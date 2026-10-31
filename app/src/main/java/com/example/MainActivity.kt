package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DocViewModel
import com.example.ui.Screen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.SpreadsheetViewerScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DocViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle direct incoming intent (e.g. from WhatsApp, Files, Gmail, Downloads)
        handleIntent(intent)

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DocuViewerApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri = extractUri(intent) ?: return

        // Take persistable permission if granted, ignore if not supported by provider (e.g. WhatsApp/Gmail)
        try {
            if ((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (_: Exception) {
            // Transient permission from WhatsApp or Gmail; fine as we copy stream directly
        }

        val resolvedMime = intent.type ?: try {
            contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }

        viewModel.openDocument(uri, resolvedMime)
    }

    private fun extractUri(intent: Intent): Uri? {
        // 1. Direct data URI (ACTION_VIEW from Files, Chrome downloads, etc.)
        intent.data?.let { return it }

        // 2. EXTRA_STREAM from ACTION_SEND (WhatsApp, Gmail, Telegram "Share")
        if (intent.action == Intent.ACTION_SEND) {
            val streamUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            if (streamUri != null) return streamUri
        }

        // 3. EXTRA_STREAM from ACTION_SEND_MULTIPLE
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val list = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            list?.firstOrNull()?.let { return it }
        }

        // 4. ClipData fallback (used by WhatsApp, Gmail, and various share sheets)
        val clipData = intent.clipData
        if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0)?.uri?.let { return it }
        }

        return null
    }
}

@Composable
fun DocuViewerApp(viewModel: DocViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

    when (val screen = currentScreen) {
        is Screen.Home -> {
            HomeScreen(
                language = language,
                recents = recents,
                favorites = favorites,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onOpenDocument = { uri, mime -> viewModel.openDocument(uri, mime) },
                onOpenSamplePdf = { viewModel.openSamplePdf() },
                onOpenSatbaraPdf = { viewModel.openSatbaraPdf() },
                onOpenSampleExcel = { viewModel.openSampleExcel() },
                onOpenShetkariExcel = { viewModel.openShetkariExcel() },
                onToggleLanguage = { viewModel.toggleLanguage() },
                isDarkMode = isDarkMode,
                onToggleDarkMode = { viewModel.toggleDarkMode() },
                onToggleFavorite = { uri, isFav -> viewModel.toggleFavorite(uri, isFav) },
                onDeleteRecent = { uri -> viewModel.deleteFromRecents(uri) }
            )
        }
        is Screen.PdfViewer -> {
            val pageCount by viewModel.pdfPageCount.collectAsStateWithLifecycle()
            val currentPage by viewModel.currentPdfPage.collectAsStateWithLifecycle()
            val bitmap by viewModel.currentPdfBitmap.collectAsStateWithLifecycle()
            val isNightMode by viewModel.pdfNightMode.collectAsStateWithLifecycle()
            val pdfSearchQuery by viewModel.pdfSearchQuery.collectAsStateWithLifecycle()
            val pdfMatches by viewModel.pdfMatches.collectAsStateWithLifecycle()
            val currentPdfMatchIndex by viewModel.currentPdfMatchIndex.collectAsStateWithLifecycle()
            val isPdfSearching by viewModel.isPdfSearching.collectAsStateWithLifecycle()

            PdfViewerScreen(
                title = screen.title,
                uri = screen.uri,
                language = language,
                pageCount = pageCount,
                currentPage = currentPage,
                bitmap = bitmap,
                isNightMode = isNightMode,
                searchQuery = pdfSearchQuery,
                searchResultsCount = pdfMatches.size,
                currentMatchIndex = currentPdfMatchIndex,
                isSearching = isPdfSearching,
                matchingPages = pdfMatches.map { it.pageIndex },
                onSearchQueryChange = { viewModel.setPdfSearchQuery(it) },
                onNextMatch = { viewModel.nextPdfMatch() },
                onPrevMatch = { viewModel.prevPdfMatch() },
                onClearSearch = { viewModel.clearPdfSearch() },
                onNextPage = { viewModel.nextPdfPage() },
                onPrevPage = { viewModel.prevPdfPage() },
                onJumpToPage = { viewModel.jumpToPdfPage(it) },
                onToggleNightMode = { viewModel.toggleNightMode() },
                onClose = { viewModel.closeDocument() }
            )
        }
        is Screen.SpreadsheetViewer -> {
            val spreadsheetData by viewModel.spreadsheetData.collectAsStateWithLifecycle()
            val selectedSheetIndex by viewModel.selectedSheetIndex.collectAsStateWithLifecycle()
            val searchQuery by viewModel.sheetSearchQuery.collectAsStateWithLifecycle()
            val selectedCells by viewModel.selectedCells.collectAsStateWithLifecycle()

            spreadsheetData?.let { data ->
                SpreadsheetViewerScreen(
                    title = screen.title,
                    uri = screen.uri,
                    language = language,
                    data = data,
                    selectedSheetIndex = selectedSheetIndex,
                    searchQuery = searchQuery,
                    selectedCells = selectedCells,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = { viewModel.toggleDarkMode() },
                    onSelectSheet = { viewModel.selectSheet(it) },
                    onSearchQueryChange = { viewModel.setSheetSearchQuery(it) },
                    onToggleCellSelection = { r, c -> viewModel.toggleCellSelection(r, c) },
                    onClearSelection = { viewModel.clearCellSelection() },
                    onClose = { viewModel.closeDocument() }
                )
            }
        }
    }
}
