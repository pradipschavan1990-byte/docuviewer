package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import com.example.model.SpreadsheetSheet
import java.io.FileOutputStream

object PrintHelper {

    fun printPdf(context: Context, uri: Uri, documentName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val cleanName = documentName.ifBlank { "document" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")

        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }

                val pdi = PrintDocumentInfo.Builder(cleanName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(pdi, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("Destination is null")
                    return
                }

                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            val buffer = ByteArray(16384)
                            var read: Int
                            while (input.read(buffer).also { read = it } >= 0) {
                                if (cancellationSignal?.isCanceled == true) {
                                    callback?.onWriteCancelled()
                                    return
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    } ?: throw IllegalStateException("Cannot open input stream for $uri")

                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }

        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()

        printManager.print(cleanName, printAdapter, attributes)
    }

    fun printSpreadsheet(context: Context, sheet: SpreadsheetSheet, fileName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val cleanName = "${fileName}_${sheet.name}".replace(Regex("[^a-zA-Z0-9._-]"), "_")

        val printAdapter = object : PrintDocumentAdapter() {
            private var totalPages = 1
            private val pageWidth = 595 // Standard A4 points
            private val pageHeight = 842
            private val marginHorizontal = 30f
            private val marginTop = 40f
            private val marginBottom = 40f
            private val rowHeight = 22f
            private val headerRowHeight = 26f
            private val topHeaderHeight = 44f
            private val footerHeight = 24f

            private val availableHeight = pageHeight - marginTop - marginBottom - topHeaderHeight - headerRowHeight - footerHeight
            private val rowsPerPage = (availableHeight / rowHeight).toInt().coerceAtLeast(1)

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }

                // If rows empty, 1 page. Else page count based on rowsPerPage
                val dataRowsCount = (sheet.rows.size - 1).coerceAtLeast(0)
                totalPages = if (dataRowsCount == 0) 1 else ((dataRowsCount + rowsPerPage - 1) / rowsPerPage).coerceAtLeast(1)

                val pdi = PrintDocumentInfo.Builder(cleanName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(totalPages)
                    .build()

                callback?.onLayoutFinished(pdi, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("Destination is null")
                    return
                }

                val pdfDocument = PdfDocument()

                try {
                    val tableWidth = pageWidth - (marginHorizontal * 2)
                    val colCount = sheet.maxColumns.coerceAtLeast(1)
                    val rowHeaderWidth = 32f
                    val availableColWidth = tableWidth - rowHeaderWidth
                    val colWidth = availableColWidth / colCount

                    val headerBgPaint = Paint().apply {
                        color = Color.rgb(241, 245, 249) // Slate-100
                        style = Paint.Style.FILL
                    }
                    val altRowBgPaint = Paint().apply {
                        color = Color.rgb(248, 250, 252) // Slate-50
                        style = Paint.Style.FILL
                    }
                    val borderPaint = Paint().apply {
                        color = Color.rgb(203, 213, 225) // Slate-300
                        style = Paint.Style.STROKE
                        strokeWidth = 0.75f
                    }
                    val titlePaint = Paint().apply {
                        color = Color.rgb(15, 23, 42)
                        textSize = 14f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        isAntiAlias = true
                    }
                    val subTitlePaint = Paint().apply {
                        color = Color.rgb(100, 116, 139)
                        textSize = 9f
                        isAntiAlias = true
                    }
                    val cellHeaderPaint = Paint().apply {
                        color = Color.rgb(30, 41, 59)
                        textSize = 9.5f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        isAntiAlias = true
                    }
                    val cellTextPaint = Paint().apply {
                        color = Color.rgb(51, 65, 85)
                        textSize = 9f
                        isAntiAlias = true
                    }
                    val watermarkPaint = Paint().apply {
                        color = Color.argb(80, 100, 116, 139)
                        textSize = 8f
                        isAntiAlias = true
                    }

                    val firstRow = sheet.rows.firstOrNull() ?: emptyList()
                    val dataRows = if (sheet.rows.size > 1) sheet.rows.subList(1, sheet.rows.size) else emptyList()

                    for (pageIndex in 0 until totalPages) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onWriteCancelled()
                            pdfDocument.close()
                            return
                        }

                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        val canvas = page.canvas

                        // Document Top Title & Sheet Name
                        var curY = marginTop
                        canvas.drawText("$fileName  •  ${sheet.name}", marginHorizontal, curY + 12f, titlePaint)
                        canvas.drawText("Total Rows: ${sheet.rows.size}  |  Columns: ${sheet.maxColumns}", marginHorizontal, curY + 28f, subTitlePaint)
                        curY += topHeaderHeight

                        // Column Headers (Row 0 of sheet or column letters)
                        canvas.drawRect(marginHorizontal, curY, marginHorizontal + tableWidth, curY + headerRowHeight, headerBgPaint)
                        canvas.drawRect(marginHorizontal, curY, marginHorizontal + tableWidth, curY + headerRowHeight, borderPaint)

                        // Row index column header
                        canvas.drawRect(marginHorizontal, curY, marginHorizontal + rowHeaderWidth, curY + headerRowHeight, borderPaint)
                        canvas.drawText("#", marginHorizontal + 10f, curY + 17f, cellHeaderPaint)

                        for (c in 0 until colCount) {
                            val colLeft = marginHorizontal + rowHeaderWidth + (c * colWidth)
                            canvas.drawRect(colLeft, curY, colLeft + colWidth, curY + headerRowHeight, borderPaint)
                            val headerVal = firstRow.getOrNull(c) ?: ('A' + (c % 26)).toString()
                            val clipped = if (headerVal.length > 14) headerVal.take(12) + ".." else headerVal
                            canvas.drawText(clipped, colLeft + 4f, curY + 17f, cellHeaderPaint)
                        }
                        curY += headerRowHeight

                        // Data rows for this page
                        val startRow = pageIndex * rowsPerPage
                        val endRow = (startRow + rowsPerPage).coerceAtMost(dataRows.size)

                        for (r in startRow until endRow) {
                            val rowCells = dataRows[r]
                            val isAlt = r % 2 == 1
                            if (isAlt) {
                                canvas.drawRect(marginHorizontal, curY, marginHorizontal + tableWidth, curY + rowHeight, altRowBgPaint)
                            }
                            canvas.drawRect(marginHorizontal, curY, marginHorizontal + tableWidth, curY + rowHeight, borderPaint)

                            // Row number
                            canvas.drawRect(marginHorizontal, curY, marginHorizontal + rowHeaderWidth, curY + rowHeight, borderPaint)
                            canvas.drawText("${r + 2}", marginHorizontal + 6f, curY + 15f, subTitlePaint)

                            for (c in 0 until colCount) {
                                val colLeft = marginHorizontal + rowHeaderWidth + (c * colWidth)
                                canvas.drawRect(colLeft, curY, colLeft + colWidth, curY + rowHeight, borderPaint)
                                val cellVal = rowCells.getOrNull(c) ?: ""
                                val clippedVal = if (cellVal.length > 16) cellVal.take(14) + ".." else cellVal
                                canvas.drawText(clippedVal, colLeft + 4f, curY + 15f, cellTextPaint)
                            }
                            curY += rowHeight
                        }

                        // Bottom Footer: Page indicator and Watermark
                        val footerY = pageHeight - marginBottom + 16f
                        val pageText = "Page ${pageIndex + 1} of $totalPages"
                        canvas.drawText(pageText, marginHorizontal, footerY, subTitlePaint)

                        val wm = "Making by Pradeep Chavan"
                        val wmWidth = watermarkPaint.measureText(wm)
                        canvas.drawText(wm, pageWidth - marginHorizontal - wmWidth, footerY, watermarkPaint)

                        pdfDocument.finishPage(page)
                    }

                    FileOutputStream(destination.fileDescriptor).use { output ->
                        pdfDocument.writeTo(output)
                    }

                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                } finally {
                    pdfDocument.close()
                }
            }
        }

        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()

        printManager.print(cleanName, printAdapter, attributes)
    }
}
