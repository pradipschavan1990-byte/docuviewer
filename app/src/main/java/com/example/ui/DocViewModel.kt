package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.RecentDocumentEntity
import com.example.engine.ExcelEngine
import com.example.engine.PdfEngine
import com.example.engine.SampleDocumentsGenerator
import com.example.model.DocType
import com.example.model.SpreadsheetData
import com.example.util.AppLanguage
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen {
    data object Home : Screen()
    data class PdfViewer(val uri: Uri, val title: String) : Screen()
    data class SpreadsheetViewer(val uri: Uri, val title: String) : Screen()
}

class DocViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.documentDao()
    private val pdfEngine = PdfEngine(application)

    val recents: StateFlow<List<RecentDocumentEntity>> = dao.getAllRecents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<RecentDocumentEntity>> = dao.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.MARATHI)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // PDF state
    private val _pdfPageCount = MutableStateFlow(0)
    val pdfPageCount: StateFlow<Int> = _pdfPageCount.asStateFlow()

    private val _currentPdfPage = MutableStateFlow(0)
    val currentPdfPage: StateFlow<Int> = _currentPdfPage.asStateFlow()

    private val _currentPdfBitmap = MutableStateFlow<Bitmap?>(null)
    val currentPdfBitmap: StateFlow<Bitmap?> = _currentPdfBitmap.asStateFlow()

    private val _pdfNightMode = MutableStateFlow(false)
    val pdfNightMode: StateFlow<Boolean> = _pdfNightMode.asStateFlow()

    // Spreadsheet state
    private val _spreadsheetData = MutableStateFlow<SpreadsheetData?>(null)
    val spreadsheetData: StateFlow<SpreadsheetData?> = _spreadsheetData.asStateFlow()

    private val _selectedSheetIndex = MutableStateFlow(0)
    val selectedSheetIndex: StateFlow<Int> = _selectedSheetIndex.asStateFlow()

    private val _sheetSearchQuery = MutableStateFlow("")
    val sheetSearchQuery: StateFlow<String> = _sheetSearchQuery.asStateFlow()

    private val _selectedCells = MutableStateFlow<Set<Pair<Int, Int>>>(emptySet())
    val selectedCells: StateFlow<Set<Pair<Int, Int>>> = _selectedCells.asStateFlow()

    fun toggleLanguage() {
        _language.value = if (_language.value == AppLanguage.MARATHI) AppLanguage.ENGLISH else AppLanguage.MARATHI
    }

    fun openDocument(uri: Uri, mimeType: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val context = getApplication<Application>()
            var resolvedName: String = uri.lastPathSegment ?: "document"

            // 1. Resolve true file name from ContentResolver
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { resolvedName = it }
                    }
                }
            } catch (_: Exception) {}

            // 2. Peek magic header bytes (first 64 bytes)
            val headerBytes = ByteArray(64)
            var bytesRead = 0
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    bytesRead = stream.read(headerBytes)
                }
            } catch (_: Exception) {}

            val effectiveMime = mimeType?.takeIf { it.isNotBlank() && it != "*/*" && it != "application/octet-stream" }
                ?: try { context.contentResolver.getType(uri) } catch (_: Exception) { null }

            val detectedType = DocType.detectType(
                name = resolvedName,
                mime = effectiveMime,
                headerBytes = if (bytesRead >= 4) headerBytes.copyOf(bytesRead) else null
            )

            // 3. If detected as PDF (by %PDF magic bytes, .pdf name or mime) -> Open as PDF!
            if (detectedType == DocType.PDF) {
                openAsPdf(uri)
            } else if (detectedType == DocType.EXCEL || detectedType == DocType.CSV) {
                // If detected as spreadsheet -> Parse spreadsheet, fallback to PDF if parse fails
                val parseResult = ExcelEngine.parse(context, uri)
                if (parseResult.isSuccess) {
                    val data = parseResult.getOrThrow()
                    _spreadsheetData.value = data
                    _selectedSheetIndex.value = 0
                    _sheetSearchQuery.value = ""
                    _selectedCells.value = emptySet()
                    _currentScreen.value = Screen.SpreadsheetViewer(uri, data.fileName)

                    dao.insertOrUpdate(
                        RecentDocumentEntity(
                            uriString = uri.toString(),
                            fileName = data.fileName,
                            fileType = if (data.fileName.endsWith(".csv", ignoreCase = true)) "CSV" else "EXCEL",
                            fileSize = 0L,
                            lastOpenedTimestamp = System.currentTimeMillis(),
                            totalCount = data.sheets.firstOrNull()?.rows?.size ?: 0
                        )
                    )
                } else {
                    // Fallback to PDF
                    openAsPdf(uri)
                }
            } else {
                // UNKNOWN: First try PDF engine (safe and strictly validated by PdfRenderer)
                val openResult = pdfEngine.open(uri)
                if (openResult.isSuccess) {
                    val count = openResult.getOrThrow()
                    _pdfPageCount.value = count
                    _currentPdfPage.value = 0
                    loadPdfPage(0)
                    _currentScreen.value = Screen.PdfViewer(uri, pdfEngine.fileName)
                    dao.insertOrUpdate(
                        RecentDocumentEntity(
                            uriString = uri.toString(),
                            fileName = pdfEngine.fileName,
                            fileType = "PDF",
                            fileSize = pdfEngine.fileSize,
                            lastOpenedTimestamp = System.currentTimeMillis(),
                            totalCount = count
                        )
                    )
                } else {
                    // Try spreadsheet as last resort
                    val parseResult = ExcelEngine.parse(context, uri)
                    if (parseResult.isSuccess) {
                        val data = parseResult.getOrThrow()
                        _spreadsheetData.value = data
                        _selectedSheetIndex.value = 0
                        _sheetSearchQuery.value = ""
                        _selectedCells.value = emptySet()
                        _currentScreen.value = Screen.SpreadsheetViewer(uri, data.fileName)
                    } else {
                        _errorMessage.value = "फाईल उघडता आली नाही. कृपया फाईल वैध आहे का ते तपासा."
                    }
                }
            }
            _isLoading.value = false
        }
    }

    private suspend fun openAsPdf(uri: Uri) {
        val openResult = pdfEngine.open(uri)
        if (openResult.isSuccess) {
            val count = openResult.getOrThrow()
            _pdfPageCount.value = count
            _currentPdfPage.value = 0
            loadPdfPage(0)
            _currentScreen.value = Screen.PdfViewer(uri, pdfEngine.fileName)

            dao.insertOrUpdate(
                RecentDocumentEntity(
                    uriString = uri.toString(),
                    fileName = pdfEngine.fileName,
                    fileType = "PDF",
                    fileSize = pdfEngine.fileSize,
                    lastOpenedTimestamp = System.currentTimeMillis(),
                    totalCount = count
                )
            )
        } else {
            _errorMessage.value = openResult.exceptionOrNull()?.message ?: "PDF उघडता आले नाही."
        }
    }

    fun openSamplePdf() {
        viewModelScope.launch {
            _isLoading.value = true
            val uri = SampleDocumentsGenerator.getOrCreateSamplePdf(getApplication())
            openDocument(uri, "application/pdf")
        }
    }

    fun openSatbaraPdf() {
        viewModelScope.launch {
            _isLoading.value = true
            val uri = SampleDocumentsGenerator.getOrCreateSatbaraPdf(getApplication())
            openDocument(uri, "application/pdf")
        }
    }

    fun openSampleExcel() {
        viewModelScope.launch {
            _isLoading.value = true
            val uri = SampleDocumentsGenerator.getOrCreateSampleExcel(getApplication())
            openDocument(uri, "text/csv")
        }
    }

    fun openShetkariExcel() {
        viewModelScope.launch {
            _isLoading.value = true
            val uri = SampleDocumentsGenerator.getOrCreateShetkariExcel(getApplication())
            openDocument(uri, "text/csv")
        }
    }

    fun loadPdfPage(index: Int) {
        viewModelScope.launch {
            val bitmap = pdfEngine.renderPage(index, 1080, _pdfNightMode.value)
            _currentPdfBitmap.value = bitmap
            _currentPdfPage.value = index
        }
    }

    fun nextPdfPage() {
        if (_currentPdfPage.value < _pdfPageCount.value - 1) {
            loadPdfPage(_currentPdfPage.value + 1)
        }
    }

    fun prevPdfPage() {
        if (_currentPdfPage.value > 0) {
            loadPdfPage(_currentPdfPage.value - 1)
        }
    }

    fun jumpToPdfPage(pageIndex: Int) {
        if (pageIndex in 0 until _pdfPageCount.value) {
            loadPdfPage(pageIndex)
        }
    }

    fun toggleNightMode() {
        _pdfNightMode.value = !_pdfNightMode.value
        loadPdfPage(_currentPdfPage.value)
    }

    fun selectSheet(index: Int) {
        _selectedSheetIndex.value = index
        _selectedCells.value = emptySet()
    }

    fun setSheetSearchQuery(query: String) {
        _sheetSearchQuery.value = query
    }

    fun toggleCellSelection(row: Int, col: Int) {
        val current = _selectedCells.value.toMutableSet()
        val pair = row to col
        if (current.contains(pair)) {
            current.remove(pair)
        } else {
            current.add(pair)
        }
        _selectedCells.value = current
    }

    fun clearCellSelection() {
        _selectedCells.value = emptySet()
    }

    fun toggleFavorite(uriString: String, currentFav: Boolean) {
        viewModelScope.launch {
            dao.updateFavorite(uriString, !currentFav)
        }
    }

    fun deleteFromRecents(uriString: String) {
        viewModelScope.launch {
            dao.deleteByUri(uriString)
        }
    }

    fun clearAllRecents() {
        viewModelScope.launch {
            dao.clearAll()
        }
    }

    fun closeDocument() {
        pdfEngine.close()
        _currentPdfBitmap.value = null
        _spreadsheetData.value = null
        _errorMessage.value = null
        _currentScreen.value = Screen.Home
    }

    override fun onCleared() {
        super.onCleared()
        pdfEngine.close()
    }
}
