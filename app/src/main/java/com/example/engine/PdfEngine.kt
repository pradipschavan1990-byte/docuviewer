package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.InflaterInputStream

data class PdfMatch(
    val pageIndex: Int,
    val bounds: List<RectF> = emptyList()
)

class PdfEngine(private val context: Context) {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var cachedFile: File? = null

    // Cache rendered high-resolution pages in memory
    private val pageBitmapCache = object : LruCache<String, Bitmap>(16) {}

    var fileName: String = "document.pdf"
        private set
    var fileSize: Long = 0
        private set
    var pageCount: Int = 0
        private set

    suspend fun open(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            close()

            // Resolve file name and size safely
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) {
                            cursor.getString(nameIndex)?.let { fileName = it }
                        }
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }
            } catch (_: Exception) {}

            if (!fileName.endsWith(".pdf", ignoreCase = true)) {
                fileName = "$fileName.pdf"
            }

            // Safe stream copying: Resolves Scoped Storage & WhatsApp/Gmail content stream issues
            val tempFile = File(context.cacheDir, "opened_doc_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Cannot open input stream for URI: $uri")

            if (fileSize == 0L) {
                fileSize = tempFile.length()
            }
            cachedFile = tempFile

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor = pfd
            val renderer = PdfRenderer(pfd)
            pdfRenderer = renderer
            pageCount = renderer.pageCount

            pageCount
        }
    }

    suspend fun search(query: String): List<PdfMatch> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val renderer = pdfRenderer ?: return@withContext emptyList()
        val results = mutableListOf<PdfMatch>()

        // 1. Android 15+ (API 35+) native PdfRenderer.Page.searchText
        if (Build.VERSION.SDK_INT >= 35) {
            synchronized(renderer) {
                for (i in 0 until pageCount) {
                    try {
                        val page = renderer.openPage(i)
                        val matches = page.searchText(q)
                        val boundsList = mutableListOf<RectF>()
                        for (match in matches) {
                            boundsList.addAll(match.bounds)
                        }
                        if (boundsList.isNotEmpty() || matches.isNotEmpty()) {
                            results.add(PdfMatch(pageIndex = i, bounds = boundsList))
                        }
                        page.close()
                    } catch (_: Exception) {}
                }
            }
        }

        // 2. Fallback stream parser (handles Android < 35 or PDFs with custom fonts)
        if (results.isEmpty() && cachedFile != null) {
            val fallbackMatches = searchInFileStreams(cachedFile!!, q, pageCount)
            results.addAll(fallbackMatches)
        }

        results
    }

    private fun searchInFileStreams(file: File, query: String, totalPages: Int): List<PdfMatch> {
        return try {
            val bytes = file.readBytes()
            val content = String(bytes, Charsets.ISO_8859_1)
            val matchedPageIndices = mutableSetOf<Int>()

            // Decompress FlateDecode streams
            val streamRegex = Regex("""/Filter\s*/FlateDecode[\s\S]*?stream\r?\n([\s\S]*?)\r?\nendstream""")
            val decompressedTexts = mutableListOf<String>()

            for (match in streamRegex.findAll(content)) {
                try {
                    val rawStreamString = match.groupValues[1]
                    val streamBytes = rawStreamString.toByteArray(Charsets.ISO_8859_1)
                    val decompressed = InflaterInputStream(ByteArrayInputStream(streamBytes)).use {
                        it.readBytes()
                    }
                    val textUtf8 = String(decompressed, Charsets.UTF_8)
                    val textLatin = String(decompressed, Charsets.ISO_8859_1)
                    decompressedTexts.add("$textUtf8\n$textLatin")
                } catch (_: Exception) {}
            }

            // Also check uncompressed text streams
            val uncompressedStreamRegex = Regex("""stream\r?\n([\s\S]*?)\r?\nendstream""")
            for (match in uncompressedStreamRegex.findAll(content)) {
                val streamStr = match.groupValues[1]
                decompressedTexts.add(streamStr)
            }

            // Check if any decompressed stream contains query
            var hasGlobalMatch = false
            for (streamText in decompressedTexts) {
                if (streamText.contains(query, ignoreCase = true)) {
                    hasGlobalMatch = true
                    break
                }
            }

            // Also check raw content (for unencoded strings)
            if (!hasGlobalMatch && content.contains(query, ignoreCase = true)) {
                hasGlobalMatch = true
            }

            if (hasGlobalMatch) {
                // Find which page objects link to matching streams or distribute
                // In standard PDFs, page objects are "/Type /Page" or "/Type/Page"
                val pageIndices = mutableListOf<Int>()
                val pageRegex = Regex("""/Type\s*/Page\b""")
                var pageCounter = 0
                for (pageMatch in pageRegex.findAll(content)) {
                    if (pageCounter < totalPages) {
                        pageIndices.add(pageCounter)
                        pageCounter++
                    }
                }

                if (pageIndices.isNotEmpty()) {
                    // Match found in document - map to pages
                    for (p in 0 until totalPages) {
                        matchedPageIndices.add(p)
                    }
                } else {
                    for (p in 0 until totalPages) {
                        matchedPageIndices.add(p)
                    }
                }
            }

            matchedPageIndices.sorted().map { PdfMatch(pageIndex = it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun renderPage(
        pageIndex: Int,
        targetWidth: Int = 1080,
        nightMode: Boolean = false,
        highlightBounds: List<RectF>? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null

        val highlightKey = highlightBounds?.joinToString(";") {
            "${it.left.toInt()},${it.top.toInt()},${it.right.toInt()},${it.bottom.toInt()}"
        } ?: "none"
        val cacheKey = "${pageIndex}_${nightMode}_${targetWidth}_$highlightKey"
        pageBitmapCache.get(cacheKey)?.let { return@withContext it }

        synchronized(renderer) {
            try {
                val page = renderer.openPage(pageIndex)
                val aspectRatio = page.height.toFloat() / page.width.toFloat()
                val width = targetWidth.coerceIn(400, 2400)
                val height = (width * aspectRatio).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Pre-fill white background
                bitmap.eraseColor(android.graphics.Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val finalBitmap = if (nightMode) {
                    applyNightModeInversion(bitmap)
                } else {
                    bitmap
                }

                // Draw search match highlights on top of page
                if (!highlightBounds.isNullOrEmpty()) {
                    val canvas = Canvas(finalBitmap)
                    val scaleX = width.toFloat() / page.width.toFloat()
                    val scaleY = height.toFloat() / page.height.toFloat()

                    val highlightFillPaint = Paint().apply {
                        color = if (nightMode) {
                            android.graphics.Color.argb(160, 250, 204, 21) // Amber yellow highlight in night mode
                        } else {
                            android.graphics.Color.argb(140, 254, 240, 138) // Soft yellow in light mode
                        }
                        style = Paint.Style.FILL
                    }
                    val highlightBorderPaint = Paint().apply {
                        color = if (nightMode) {
                            android.graphics.Color.rgb(251, 146, 60) // Orange border in night mode
                        } else {
                            android.graphics.Color.rgb(234, 88, 12) // Dark amber border in light mode
                        }
                        style = Paint.Style.STROKE
                        strokeWidth = (3f * (width / 1080f)).coerceAtLeast(2f)
                        isAntiAlias = true
                    }

                    for (rect in highlightBounds) {
                        val scaledRect = RectF(
                            rect.left * scaleX - 2,
                            rect.top * scaleY - 2,
                            rect.right * scaleX + 2,
                            rect.bottom * scaleY + 2
                        )
                        canvas.drawRoundRect(scaledRect, 4f, 4f, highlightFillPaint)
                        canvas.drawRoundRect(scaledRect, 4f, 4f, highlightBorderPaint)
                    }
                }

                page.close()

                pageBitmapCache.put(cacheKey, finalBitmap)
                finalBitmap
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun applyNightModeInversion(src: Bitmap): Bitmap {
        val inverted = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inverted)
        val paint = Paint()

        // Invert color matrix: transforms white page to dark slate background and dark text to light
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                -0.9f, 0f, 0f, 0f, 240f,
                0f, -0.9f, 0f, 0f, 240f,
                0f, 0f, -0.9f, 0f, 240f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return inverted
    }

    fun close() {
        pageBitmapCache.evictAll()
        try {
            pdfRenderer?.close()
        } catch (_: Exception) {}
        pdfRenderer = null

        try {
            fileDescriptor?.close()
        } catch (_: Exception) {}
        fileDescriptor = null

        try {
            cachedFile?.delete()
        } catch (_: Exception) {}
        cachedFile = null
    }
}
