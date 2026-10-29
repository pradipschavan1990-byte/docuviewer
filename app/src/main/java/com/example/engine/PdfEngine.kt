package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfEngine(private val context: Context) {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var cachedFile: File? = null

    // Cache up to 10 rendered high-resolution pages in memory
    private val pageBitmapCache = object : LruCache<String, Bitmap>(10) {}

    var fileName: String = "document.pdf"
        private set
    var fileSize: Long = 0
        private set
    var pageCount: Int = 0
        private set

    suspend fun open(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            close()

            // Resolve file name and size
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: "document.pdf"
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
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

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1080, nightMode: Boolean = false): Bitmap? =
        withContext(Dispatchers.IO) {
            val renderer = pdfRenderer ?: return@withContext null
            if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null

            val cacheKey = "${pageIndex}_${nightMode}_$targetWidth"
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
                    page.close()

                    val finalBitmap = if (nightMode) {
                        applyNightModeInversion(bitmap)
                    } else {
                        bitmap
                    }

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
