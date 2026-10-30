package com.example.engine

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
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

    suspend fun getOrCreateSatbaraPdf(context: Context): Uri = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "७_१२_सातबारा_उतारा_नमुना.pdf")
        if (!file.exists() || file.length() == 0L) {
            createSatbaraPdf(file)
        }
        Uri.fromFile(file)
    }

    suspend fun getOrCreateSampleExcel(context: Context): Uri = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "शेती_आणि_घरगुती_खर्च_पत्रक.csv")
        if (!file.exists() || file.length() == 0L) {
            createSampleCsv(file)
        }
        Uri.fromFile(file)
    }

    suspend fun getOrCreateShetkariExcel(context: Context): Uri = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "शेतकरी_पीक_उत्पन्न_व_बाजारभाव.csv")
        if (!file.exists() || file.length() == 0L) {
            createShetkariCsv(file)
        }
        Uri.fromFile(file)
    }

    private fun createSatbaraPdf(file: File) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // Header Background
        val headerPaint = Paint().apply {
            color = Color.rgb(180, 83, 9) // Ochre/Gold Official Maharashtra Gov style
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 95f, headerPaint)

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("महाराष्ट्र शासन - महसूल विभाग (मराठी दस्तऐवज)", 30f, 40f, titlePaint)

        val subTitlePaint = Paint().apply {
            color = Color.rgb(254, 243, 199)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("गाव नमुना सात (अधिकार अभिलेख पत्रक) व गाव नमुना १२ (पिकांची पाहणी)", 30f, 68f, subTitlePaint)

        // General Info
        val textPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 13f
            isAntiAlias = true
        }
        val boldPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        var y = 125f
        canvas.drawText("गाव: शिरपूर   |   तालुका: हवेली   |   जिल्हा: पुणे", 30f, y, boldPaint)
        y += 24f
        canvas.drawText("भूमापन क्रमांक (गट क्र.): २४५/२अ   |   एकूण खाते क्र.: १२८", 30f, y, boldPaint)
        y += 26f

        // Table Border
        val borderPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        val tableBg = Paint().apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
        }

        canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 30f, tableBg)
        canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 30f, borderPaint)

        canvas.drawText("खातेदाराचे नाव (भूधारक)", 40f, y + 20f, boldPaint)
        canvas.drawText("क्षेत्र (हे.आर.)", 260f, y + 20f, boldPaint)
        canvas.drawText("आकारणी (₹)", 370f, y + 20f, boldPaint)
        canvas.drawText("इतर अधिकार व बोजा", 460f, y + 20f, boldPaint)

        y += 30f
        val owners = listOf(
            Triple("प्रदीप शंकरराव चव्हाण (मुख्य खातेदार)", "१ हेक्टर २० आर", "₹ १८.५०"),
            Triple("सुभाष शंकरराव चव्हाण (हिस्सेदार)", "० हेक्टर ८० आर", "₹ १२.००"),
            Triple("रंजना प्रदीप चव्हाण (वारसदार)", "० हेक्टर ५० आर", "₹ ०८.२५")
        )

        for (item in owners) {
            canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 32f, borderPaint)
            canvas.drawText(item.first, 40f, y + 21f, textPaint)
            canvas.drawText(item.second, 260f, y + 21f, textPaint)
            canvas.drawText(item.third, 370f, y + 21f, textPaint)
            canvas.drawText("बँक ऑफ महाराष्ट्र कर्ज", 460f, y + 21f, textPaint)
            y += 32f
        }

        // Section 2: Crop Details (गाव नमुना १२)
        y += 25f
        canvas.drawText("पिकांची नोंद वही (गाव नमुना १२ - हंगाम खरीप/रब्बी):", 30f, y, boldPaint)
        y += 15f

        canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 28f, tableBg)
        canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 28f, borderPaint)
        canvas.drawText("वर्ष", 40f, y + 19f, boldPaint)
        canvas.drawText("हंगाम", 100f, y + 19f, boldPaint)
        canvas.drawText("पिकाचे नाव", 180f, y + 19f, boldPaint)
        canvas.drawText("लागवड क्षेत्र", 320f, y + 19f, boldPaint)
        canvas.drawText("सिंचनाचे साधन", 450f, y + 19f, boldPaint)

        y += 28f
        val crops = listOf(
            listOf("२०२५-२६", "खरीप", "सोयाबीन (फुले संगम)", "१ हे. ०० आर", "विहीर"),
            listOf("२०२५-२६", "रब्बी", "गहू (शरबती)", "० हे. ६० आर", "ठिबक सिंचन"),
            listOf("२०२५-२६", "बारमाही", "ऊस (को-८६०३२)", "० हे. ९० आर", "कॅनाल")
        )

        for (crop in crops) {
            canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 28f, borderPaint)
            canvas.drawText(crop[0], 40f, y + 19f, textPaint)
            canvas.drawText(crop[1], 100f, y + 19f, textPaint)
            canvas.drawText(crop[2], 180f, y + 19f, textPaint)
            canvas.drawText(crop[3], 320f, y + 19f, textPaint)
            canvas.drawText(crop[4], 450f, y + 19f, textPaint)
            y += 28f
        }

        // Footer Stamp
        y += 35f
        val stampBg = Paint().apply {
            color = Color.rgb(238, 242, 255)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(30f, y, (pageWidth - 30).toFloat(), y + 110f, 12f, 12f, stampBg)
        canvas.drawRoundRect(30f, y, (pageWidth - 30).toFloat(), y + 110f, 12f, 12f, borderPaint)

        val notePaint = Paint().apply {
            color = Color.rgb(30, 58, 138)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("✔ महाभूलेख (MahaBhulekh) डिजिटल स्वाक्षरीत नमुना उतारा", 45f, y + 28f, notePaint)
        canvas.drawText("मोटोरोला जी ६७ पॉवर वर DocuViewer द्वारे सुरक्षित व स्पष्ट पाहिला गेला.", 45f, y + 50f, textPaint)
        canvas.drawText("मुद्रण दिनांक: २९ ऑक्टोबर २०२६", 45f, y + 70f, textPaint)

        val tinyWatermarkPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 9f
            isAntiAlias = true
        }
        canvas.drawText("Making by Pradeep Chavan", (pageWidth - 140).toFloat(), pageHeight - 15f, tinyWatermarkPaint)

        document.finishPage(page)
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
    }

    private fun createSamplePdf(file: File) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        // --- PAGE 1 ---
        val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page1 = document.startPage(pageInfo1)
        val canvas1 = page1.canvas

        val headerPaint = Paint().apply {
            color = Color.rgb(30, 58, 138)
            style = Paint.Style.FILL
        }
        canvas1.drawRect(0f, 0f, pageWidth.toFloat(), 130f, headerPaint)

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
        canvas1.drawText("तारीख: २९ ऑक्टोबर २०२६ | आवृत्ती १.०", 30f, 105f, subTitlePaint)

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
        canvas1.drawText("१. मोटोरोला फोनसाठी विशेष वैशिष्ट्ये:", 30f, y, boldPaint)
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

        val totalBg = Paint().apply {
            color = Color.rgb(254, 243, 199)
            style = Paint.Style.FILL
        }
        canvas1.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 30f, totalBg)
        canvas1.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 30f, borderPaint)
        canvas1.drawText("एकूण देय रक्कम (Total):", 80f, y + 20f, boldPaint)
        canvas1.drawText("₹ 3,047.00", 440f, y + 20f, boldPaint)

        val tinyWatermarkPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 9f
            isAntiAlias = true
        }
        canvas1.drawText("पान १ / १ | DocuViewer Android Ready", 30f, pageHeight - 20f, tinyWatermarkPaint)
        canvas1.drawText("Making by Pradeep Chavan", (pageWidth - 140).toFloat(), pageHeight - 20f, tinyWatermarkPaint)
        document.finishPage(page1)

        FileOutputStream(file).use { out -> document.writeTo(out) }
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

    private fun createShetkariCsv(file: File) {
        val csvContent = """
तारीख,कृषी उत्पन्न बाजार समिती (APMC),शेतीमाल,प्रत/व्हरायटी,आवश्यक प्रमाण (क्विंटल),किमान भाव (₹),कमाल भाव (₹),सरासरी भाव (₹)
29/10/2026,पुणे APMC,सोयाबीन,पिवळा,450,4200,4850,4650
29/10/2026,नाशिक APMC,कांदा,उन्हाळी लाल,1200,2100,3400,2850
29/10/2026,नागपूर APMC,कापूस,मध्यम धागा,380,6800,7450,7200
29/10/2026,कोल्हापूर APMC,गुळ,एक नंबर ढेप,220,3800,4300,4100
29/10/2026,जळगाव APMC,केळी,ग्रँड नैन,850,1400,2100,1850
29/10/2026,अमरावती APMC,तूर,पांढरी,310,9200,10400,9800
29/10/2026,सोलापूर APMC,डाळिंब,भगवा,190,7500,14500,11000
29/10/2026,बारामती APMC,गहू,शरबती,520,2600,3200,2950
        """.trimIndent()

        file.writeText(csvContent, Charsets.UTF_8)
    }
}
