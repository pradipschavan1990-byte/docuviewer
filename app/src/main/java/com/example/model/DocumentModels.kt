package com.example.model

import android.net.Uri

enum class DocType {
    PDF,
    EXCEL,
    CSV,
    UNKNOWN;

    companion object {
        fun fromFileNameOrMime(name: String?, mime: String?): DocType {
            val lowerName = name?.lowercase() ?: ""
            val lowerMime = mime?.lowercase() ?: ""

            return when {
                lowerName.endsWith(".pdf") || lowerMime.contains("pdf") -> PDF
                lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") ||
                        lowerMime.contains("spreadsheet") || lowerMime.contains("ms-excel") -> EXCEL
                lowerName.endsWith(".csv") || lowerMime.contains("csv") -> CSV
                else -> UNKNOWN
            }
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
