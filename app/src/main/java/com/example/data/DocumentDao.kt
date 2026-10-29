package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM recent_documents ORDER BY lastOpenedTimestamp DESC")
    fun getAllRecents(): Flow<List<RecentDocumentEntity>>

    @Query("SELECT * FROM recent_documents WHERE isFavorite = 1 ORDER BY lastOpenedTimestamp DESC")
    fun getFavorites(): Flow<List<RecentDocumentEntity>>

    @Query("SELECT * FROM recent_documents WHERE uriString = :uri LIMIT 1")
    suspend fun getByUri(uri: String): RecentDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(document: RecentDocumentEntity)

    @Update
    suspend fun update(document: RecentDocumentEntity)

    @Query("UPDATE recent_documents SET isFavorite = :isFav WHERE uriString = :uri")
    suspend fun updateFavorite(uri: String, isFav: Boolean)

    @Query("DELETE FROM recent_documents WHERE uriString = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM recent_documents")
    suspend fun clearAll()
}
