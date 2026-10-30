package com.example.model

import android.net.Uri

enum class DocType {
    PDF,
    EXCEL,
    CSV,
    UNKNOWN;

    companion object {
        fun detectType(name: String?, mime: String?, headerBytes: ByteArray? = null): DocType {
            // 1. Check Magic Bytes first (most accurate!)
            if (headerBytes != null && headerBytes.isNotEmpty()) {
                val headerStr = String(headerBytes, java.nio.charset.StandardCharsets.ISO_8859_1)
                // Any PDF file header: %PDF anywhere in the first bytes
                if (headerStr.contains("%PDF")) {
                    return PDF
                }
                // ZIP / XLSX magic bytes: PK\x03\x04 (0x50, 0x4B, 0x03, 0x04)
                if (headerBytes.size >= 4 &&
                    headerBytes[0] == 0x50.toByte() &&
                    headerBytes[1] == 0x4B.toByte() &&
                    headerBytes[2] == 0x03.toByte() &&
                    headerBytes[3] == 0x04.toByte()
                ) {
                    return EXCEL
                }
                // OLE2 / XLS magic bytes: 0xD0, 0xCF, 0x11, 0xE0
                if (headerBytes.size >= 4 &&
                    headerBytes[0] == 0xD0.toByte() &&
                    headerBytes[1] == 0xCF.toByte() &&
                    headerBytes[2] == 0x11.toByte() &&
                    headerBytes[3] == 0xE0.toByte()
                ) {
                    return EXCEL
                }
            }

            // 2. Check Display Name
            val lowerName = name?.lowercase() ?: ""
            if (lowerName.endsWith(".pdf")) return PDF
            if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || lowerName.endsWith(".xlsm")) return EXCEL
            if (lowerName.endsWith(".csv") || lowerName.endsWith(".tsv") || lowerName.endsWith(".txt") || lowerName.endsWith(".log")) return CSV

            // 3. Check Mime
            val lowerMime = mime?.lowercase() ?: ""
            if (lowerMime.contains("pdf")) return PDF
            if (lowerMime.contains("spreadsheet") || lowerMime.contains("ms-excel") || lowerMime.contains("excel")) return EXCEL
            if (lowerMime.contains("csv") || lowerMime.contains("tab-separated") || lowerMime.contains("text/plain")) return CSV

            return UNKNOWN
        }

        fun fromFileNameOrMime(name: String?, mime: String?): DocType {
            return detectType(name, mime, null)
        }
    }
}

data class DocumentItem(
    val uri: Uri,
    val name: String,
    val type: DocType,
    val sizeBytes: Long = 0,
    val lastModified: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val pageCount: Int = 0
)

data class SpreadsheetSheet(
    val name: String,
    val rows: List<List<String>>,
    val maxColumns: Int
)

data class SpreadsheetData(
    val fileName: String,
    val sheets: List<SpreadsheetSheet>
)
