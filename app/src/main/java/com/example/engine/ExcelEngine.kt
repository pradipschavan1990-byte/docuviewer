package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.example.model.SpreadsheetData
import com.example.model.SpreadsheetSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ExcelEngine {

    suspend fun parse(context: Context, uri: Uri): Result<SpreadsheetData> = withContext(Dispatchers.IO) {
        runCatching {
            var fileName = "spreadsheet"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { fileName = it }
                    }
                }
            } catch (_: Exception) {}

            val lower = fileName.lowercase()
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open input stream for: $uri")

            val bytes = inputStream.use { it.readBytes() }

            // Guard: If this is a PDF file, do NOT parse as CSV!
            if (bytes.size >= 4 &&
                bytes[0] == 0x25.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x44.toByte() &&
                bytes[3] == 0x46.toByte()
            ) {
                throw IllegalArgumentException("This is a PDF document, not a spreadsheet!")
            }

            if (lower.endsWith(".xlsx")) {
                parseXlsx(fileName, bytes)
            } else {
                // Parse as CSV / Delimited text
                parseCsv(fileName, bytes)
            }
        }
    }

    fun parseCsv(fileName: String, bytes: ByteArray): SpreadsheetData {
        val cleanBytes = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
        val reader = BufferedReader(InputStreamReader(ByteArrayInputStream(cleanBytes), java.nio.charset.StandardCharsets.UTF_8))
        val rows = mutableListOf<List<String>>()
        var maxCols = 0

        var line = reader.readLine()
        // Detect delimiter (comma vs semicolon vs tab)
        val delimiter = if (line != null) {
            when {
                line.count { it == '\t' } > line.count { it == ',' } -> '\t'
                line.count { it == ';' } > line.count { it == ',' } -> ';'
                else -> ','
            }
        } else ','

        while (line != null) {
            if (line.isNotBlank()) {
                val row = parseCsvLine(line, delimiter)
                rows.add(row)
                if (row.size > maxCols) maxCols = row.size
            }
            line = reader.readLine()
        }

        if (rows.isEmpty()) {
            rows.add(listOf("रिकामी फाईल / Empty File"))
            maxCols = 1
        }

        val sheet = SpreadsheetSheet(name = "Sheet 1", rows = rows, maxColumns = maxCols)
        return SpreadsheetData(fileName = fileName, sheets = listOf(sheet))
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (i in line.indices) {
            val c = line[i]
            when {
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(c)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    private fun parseXlsx(fileName: String, bytes: ByteArray): SpreadsheetData {
        val sharedStrings = mutableListOf<String>()
        val sheetNames = mutableListOf<Pair<String, String>>() // name to sheetPath/id
        val sheetDataMap = mutableMapOf<String, ByteArray>()

        // 1st pass: extract shared strings and worksheet XML entries
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                when {
                    entryName.equals("xl/sharedStrings.xml", ignoreCase = true) -> {
                        sharedStrings.addAll(parseSharedStrings(zis.readBytes()))
                    }
                    entryName.equals("xl/workbook.xml", ignoreCase = true) -> {
                        sheetNames.addAll(parseWorkbookSheets(zis.readBytes()))
                    }
                    entryName.startsWith("xl/worksheets/sheet", ignoreCase = true) && entryName.endsWith(".xml", ignoreCase = true) -> {
                        sheetDataMap[entryName] = zis.readBytes()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sheets = mutableListOf<SpreadsheetSheet>()

        // Fallback if workbook.xml didn't list explicit sheets
        if (sheetNames.isEmpty() && sheetDataMap.isNotEmpty()) {
            sheetDataMap.keys.sorted().forEachIndexed { index, path ->
                sheetNames.add("Sheet ${index + 1}" to path)
            }
        }

        for (sheetPair in sheetNames) {
            val sName = sheetPair.first
            // Locate entry in sheetDataMap
            val entryData = sheetDataMap.entries.firstOrNull {
                it.key.contains(sheetPair.second, ignoreCase = true) ||
                        it.key.endsWith("sheet1.xml", ignoreCase = true)
            }?.value ?: sheetDataMap.values.firstOrNull()

            if (entryData != null) {
                val parsedSheet = parseWorksheet(sName, entryData, sharedStrings)
                sheets.add(parsedSheet)
            }
        }

        if (sheets.isEmpty()) {
            // Fallback: parse as CSV if not a standard zipped xlsx
            return parseCsv(fileName, bytes)
        }

        return SpreadsheetData(fileName = fileName, sheets = sheets)
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var eventType = parser.eventType
        val textBuilder = StringBuilder()
        var insideT = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        textBuilder.clear()
                    } else if (parser.name == "t") {
                        insideT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideT) {
                        textBuilder.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") {
                        insideT = false
                    } else if (parser.name == "si") {
                        result.add(textBuilder.toString())
                    }
                }
            }
            eventType = parser.next()
        }
        return result
    }

    private fun parseWorkbookSheets(xmlBytes: ByteArray): List<Pair<String, String>> {
        val sheets = mutableListOf<Pair<String, String>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                val sheetId = parser.getAttributeValue(null, "sheetId") ?: "1"
                sheets.add(name to "sheet$sheetId")
            }
            eventType = parser.next()
        }
        return sheets
    }

    private fun parseWorksheet(name: String, xmlBytes: ByteArray, sharedStrings: List<String>): SpreadsheetSheet {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var eventType = parser.eventType
        var currentRow = mutableListOf<String>()
        var currentCellRef = ""
        var currentCellType = ""
        var cellValue = StringBuilder()
        var insideV = false
        var maxCols = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRow = mutableListOf()
                        }
                        "c" -> {
                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            cellValue.clear()
                        }
                        "v" -> {
                            insideV = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideV) {
                        cellValue.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> {
                            insideV = false
                        }
                        "c" -> {
                            val raw = cellValue.toString().trim()
                            val resolvedText = if (currentCellType == "s") {
                                val sIndex = raw.toIntOrNull()
                                if (sIndex != null && sIndex in sharedStrings.indices) {
                                    sharedStrings[sIndex]
                                } else raw
                            } else {
                                raw
                            }

                            // Calculate column index from cell reference like 'B4' -> column 1 (0-indexed)
                            val colIdx = getColumnIndexFromRef(currentCellRef)
                            while (currentRow.size < colIdx) {
                                currentRow.add("")
                            }
                            currentRow.add(resolvedText)
                        }
                        "row" -> {
                            rows.add(currentRow)
                            if (currentRow.size > maxCols) maxCols = currentRow.size
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (rows.isEmpty()) {
            rows.add(listOf("रिकामी शीट / Empty Sheet"))
            maxCols = 1
        }

        return SpreadsheetSheet(name = name, rows = rows, maxColumns = maxCols)
    }

    private fun getColumnIndexFromRef(cellRef: String): Int {
        var col = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else break
        }
        return (col - 1).coerceAtLeast(0)
    }
}
