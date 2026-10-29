package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_documents")
data class RecentDocumentEntity(
    @PrimaryKey
    val uriString: String,
    val fileName: String,
    val fileType: String, // PDF, EXCEL, CSV
    val fileSize: Long,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val lastPageOrSheet: Int = 0,
    val totalCount: Int = 0
)
