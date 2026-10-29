package com.example.engine

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SampleDocumentsGenerator {

    suspend fun getOrCreateSamplePdf(context: Context): Uri = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "मोटोरोला_G67_चाचणी_दस्तऐवज.pdf")
        if (!file.exists() || file.length() == 0L) {
            createSamplePdf(file)
        }
        Uri.fromFile(file)
    }

    suspend fun getOrCreateSampleExcel(context: Context): Uri = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "हिशोब_आणि_खर्च_पत्रक.csv")
        if (!file.exists() || file.length() == 0L) {
            createSampleCsv(file)
        }
        Uri.fromFile(file)
    }

    private fun createSamplePdf(file: File) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        // --- PAGE 1 ---
        val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page1 = document.startPage(pageInfo1)
        val canvas1 = page1.canvas

        // Header Background
        val headerPaint = Paint().apply {
            color = Color.rgb(30, 58, 138) // Deep Blue
            style = Paint.Style.FILL
        }
        canvas1.drawRect(0f, 0f, pageWidth.toFloat(), 130f, headerPaint)

        // Title
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas1.drawText("DocuViewer - मोटोरोला G67 पॉवर टेस्ट", 30f, 50f, titlePaint)

        val subTitlePaint = Paint().apply {
            color = Color.rgb(219, 234, 254)
            textSize = 13f
            isAntiAlias = true
        }
        canvas1.drawText("Motorola G67 Power & Modern Android Direct PDF Test", 30f, 80f, subTitlePaint)
        canvas1.drawText("तारीख: 29 ऑक्टोबर 2026 | आवृत्ती 1.0", 30f, 105f, subTitlePaint)

        // Body Text
        val textPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 14f
            isAntiAlias = true
        }

        val boldPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        var y = 170f
        canvas1.drawText("१. मोटोरोला फोनसाठी विशेष वैशिष्ट्ये (Features):", 30f, y, boldPaint)
        y += 28f

        val features = listOf(
            "✔ 100% Direct Open: WhatsApp व Email मधून थेट उघडणे",
            "✔ Zero Crash Architecture: Scoped Storage सुरक्षित हाताळणी",
            "✔ Hardware Acceleration: वेगवान व क्रिस्टल क्लिअर फॉन्ट",
            "✔ Night Mode: रात्री डोळ्यांच्या सुरक्षेसाठी इनव्हर्टेड डार्क मोड",
            "✔ Smooth Pinch Zoom: हवे तेवढे मोठे करून वाचण्याची सोय"
        )

        for (feat in features) {
            canvas1.drawText(feat, 45f, y, textPaint)
            y += 25f
        }

        y += 20f
        canvas1.drawText("२. नमुना पावती तपशील (Sample Receipt Table):", 30f, y, boldPaint)
        y += 20f

        // Draw Table
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val thPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }

        canvas1.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 30f, thPaint)
        canvas1.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 30f, borderPaint)

        val tableHeaderPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas1.drawText("अनु.", 40f, y + 20f, tableHeaderPaint)
        canvas1.drawText("तपशील (Item)", 80f, y + 20f, tableHeaderPaint)
        canvas1.drawText("संख्या", 330f, y + 20f, tableHeaderPaint)
        canvas1.drawText("रक्कम (₹)", 440f, y + 20f, tableHeaderPaint)

        y += 30f
        val items = listOf(
            Triple("1", "मोटोरोला G67 5G ॲक्सिसरीज", "1 नग"),
            Triple("2", "68W टर्बोपॉवर चार्जर केबल", "1 नग"),
            Triple("3", "स्क्रीन गार्ड टेम्पर्ड ग्लास", "2 नग"),
            Triple("4", "बॅक कव्हर सिलिकॉन केस", "1 नग")
        )
        val amounts = listOf("₹ 1,499", "₹ 899", "₹ 350", "₹ 299")

        for (i in items.indices) {
            val item = items[i]
            canvas1.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 28f, borderPaint)
            canvas1.drawText(item.first, 45f, y + 19f, textPaint)
            canvas1.drawText(item.second, 80f, y + 19f, textPaint)
            canvas1.drawText(item.third, 330f, y + 19f, textPaint)
            canvas1.drawText(amounts[i], 440f, y + 19f, textPaint)
            y += 28f
        }

        // Total
        val totalBg = Paint().apply {
            color = Color.rgb(254, 243, 199)
            style = Paint.Style.FILL
        }
        canvas1.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 30f, totalBg)
        canvas1.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 30f, borderPaint)
        canvas1.drawText("एकूण देय रक्कम (Total):", 80f, y + 20f, boldPaint)
        canvas1.drawText("₹ 3,047.00", 440f, y + 20f, boldPaint)

        // Footer
        val footerPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 11f
            isAntiAlias = true
        }
        canvas1.drawText("पान १ / २ | DocuViewer Android Ready", 30f, pageHeight - 30f, footerPaint)

        document.finishPage(page1)

        // --- PAGE 2 ---
        val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        val page2 = document.startPage(pageInfo2)
        val canvas2 = page2.canvas

        canvas2.drawRect(0f, 0f, pageWidth.toFloat(), 70f, headerPaint)
        canvas2.drawText("मोटोरोला जी ६७ पॉवर वापरकर्त्यांसाठी सूचना (Tips)", 30f, 45f, titlePaint)

        var y2 = 110f
        val tips = listOf(
            "१. व्हॉट्सॲपवरून उघडणे:",
            "जेव्हा कोणी WhatsApp वर PDF पाठवेल, त्यावर टॅप करा.",
            "DocuViewer निवडून Always वर क्लिक करा.",
            "",
            "२. इतर ॲप्स का फेल होतात?",
            "काही ॲप्स अँड्रॉइड १४/१५ च्या नवीन सेक्युरिटीमुळे",
            "क्रॅश होतात किंवा 'File not found' असा मेसेज दाखवतात.",
            "DocuViewer थेट सिस्टीम स्ट्रीम वापरत असल्याने हा प्रश्न सुटतो.",
            "",
            "३. एक्सेल फाईल सपोर्ट:",
            "हे ॲप .xlsx, .xls आणि .csv फाईल्स सहजपणे टेबल स्वरूपात",
            "दाखवते, ज्यामध्ये तुम्ही कॉलम, रो आणि सर्च करू शकता.",
            "",
            "४. डोळ्यांची काळजी (Night Mode):",
            "वर उजव्या कोपऱ्यातील चंद्र चिन्हावर टॅप करून",
            "तुम्ही पांढरा कागद काळा करून रात्री सहज वाचू शकता."
        )

        for (line in tips) {
            if (line.startsWith("१") || line.startsWith("२") || line.startsWith("३") || line.startsWith("४")) {
                canvas2.drawText(line, 30f, y2, boldPaint)
            } else if (line.isNotBlank()) {
                canvas2.drawText(line, 45f, y2, textPaint)
            }
            y2 += 24f
        }

        canvas2.drawText("पान २ / २ | यशस्वीरित्या तयार केलेले नमुना दस्तऐवज", 30f, pageHeight - 30f, footerPaint)
        document.finishPage(page2)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    private fun createSampleCsv(file: File) {
        val csvContent = """
दिनांक,तपशील,श्रेणी,रक्कम (₹),भरणा पद्धत,स्थिती
01/10/2026,मोबाईल रिचार्ज,इलेक्ट्रॉनिक्स,299,GPay / UPI,यशस्वी
02/10/2026,किराणा माल खरेदी,घरगुती,1850,रोख / Cash,यशस्वी
05/10/2026,महावितरण लाईट बिल,युटिलिटी,740,PhonePe,यशस्वी
08/10/2026,दूध व भाजीपाला,घरगुती,620,रोख / Cash,यशस्वी
12/10/2026,पेट्रोल भरले,प्रवास,500,UPI,यशस्वी
15/10/2026,शेती औषधे व खते,शेती,3200,बँक ट्रान्सफर,यशस्वी
18/10/2026,गॅस सिलिंडर रिफिल,घरगुती,860,GPay,यशस्वी
22/10/2026,डॉक्टर फी व औषधे,आरोग्य,450,UPI,यशस्वी
25/10/2026,इंटरनेट वायफाय बिल,ऑफिस,499,PhonePe,यशस्वी
28/10/2026,गाडी सर्व्हिसिंग,प्रवास,1250,रोख,यशस्वी
30/10/2026,बचत ठेव ट्रान्सफर,बचत,5000,NetBanking,यशस्वी
        """.trimIndent()

        file.writeText(csvContent, Charsets.UTF_8)
    }
}
