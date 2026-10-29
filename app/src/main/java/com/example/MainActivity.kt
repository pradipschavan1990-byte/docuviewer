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
            MyApplicationTheme {
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
        val action = intent.action
        val type = intent.type

        val uri: Uri? = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            else -> intent.data
        }

        if (uri != null) {
            viewModel.openDocument(uri, type)
        }
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
                onToggleFavorite = { uri, isFav -> viewModel.toggleFavorite(uri, isFav) },
                onDeleteRecent = { uri -> viewModel.deleteFromRecents(uri) }
            )
        }
        is Screen.PdfViewer -> {
            val pageCount by viewModel.pdfPageCount.collectAsStateWithLifecycle()
            val currentPage by viewModel.currentPdfPage.collectAsStateWithLifecycle()
            val bitmap by viewModel.currentPdfBitmap.collectAsStateWithLifecycle()
            val isNightMode by viewModel.pdfNightMode.collectAsStateWithLifecycle()

            PdfViewerScreen(
                title = screen.title,
                uri = screen.uri,
                language = language,
                pageCount = pageCount,
                currentPage = currentPage,
                bitmap = bitmap,
                isNightMode = isNightMode,
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
