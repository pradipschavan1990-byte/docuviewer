package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AppLanguage
import com.example.util.StringsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    title: String,
    uri: Uri,
    language: AppLanguage,
    pageCount: Int,
    currentPage: Int,
    bitmap: Bitmap?,
    isNightMode: Boolean,
    searchQuery: String = "",
    searchResultsCount: Int = 0,
    currentMatchIndex: Int = 0,
    isSearching: Boolean = false,
    matchingPages: List<Int> = emptyList(),
    onSearchQueryChange: (String) -> Unit = {},
    onNextMatch: () -> Unit = {},
    onPrevMatch: () -> Unit = {},
    onClearSearch: () -> Unit = {},
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onToggleNightMode: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isSearchVisible by remember { mutableStateOf(false) }

    // Intercept back button when search is open to close search first
    BackHandler {
        if (isSearchVisible) {
            isSearchVisible = false
            onClearSearch()
        } else {
            onClose()
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }

    val bgColor = if (isNightMode) Color(0xFF0F172A) else Color(0xFFE2E8F0)
    val isMatchOnCurrentPage = matchingPages.contains(currentPage)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${StringsManager.get("page", language)} ${currentPage + 1} ${StringsManager.get("of", language)} $pageCount",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose, modifier = Modifier.testTag("pdf_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Search Button
                        IconButton(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) onClearSearch()
                            },
                            modifier = Modifier.testTag("pdf_search_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = StringsManager.get("search_label", language),
                                tint = if (isSearchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Night Mode Toggle
                        IconButton(onClick = onToggleNightMode, modifier = Modifier.testTag("pdf_night_mode")) {
                            Icon(
                                imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = StringsManager.get("night_mode", language),
                                tint = if (isNightMode) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Share File
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Expandable Search Bar
                AnimatedVisibility(
                    visible = isSearchVisible,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 4.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    placeholder = {
                                        Text(
                                            StringsManager.get("search_in_pdf", language),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        if (isSearching) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = onClearSearch,
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = StringsManager.get("clear_search", language),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pdf_search_input")
                                )

                                // Close search bar button
                                IconButton(
                                    onClick = {
                                        isSearchVisible = false
                                        onClearSearch()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Search")
                                }
                            }

                            // Match Navigation & Counter Row
                            if (searchQuery.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (searchResultsCount > 0) {
                                        // Match indicator chip
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        ) {
                                            Text(
                                                text = "${currentMatchIndex + 1} / $searchResultsCount ${StringsManager.get("matches_found", language).substringAfter(" ")}",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        // Prev and Next Match Buttons
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = onPrevMatch,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("pdf_prev_match")
                                            ) {
                                                Icon(
                                                    Icons.Default.KeyboardArrowUp,
                                                    contentDescription = StringsManager.get("prev_match", language),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = onNextMatch,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("pdf_next_match")
                                            ) {
                                                Icon(
                                                    Icons.Default.KeyboardArrowDown,
                                                    contentDescription = StringsManager.get("next_match", language),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    } else if (!isSearching) {
                                        Text(
                                            text = StringsManager.get("no_matches_found", language),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = MaterialTheme.colorScheme.error
                                            ),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Page Navigation Slider
                    if (pageCount > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "1",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = currentPage.toFloat(),
                                onValueChange = { onJumpToPage(it.toInt()) },
                                valueRange = 0f..(pageCount - 1).toFloat(),
                                steps = (pageCount - 2).coerceAtLeast(0),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text(
                                text = "$pageCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Bottom Zoom Slider ("खालचा स्लाईड बार")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                scale = (scale - 0.25f).coerceAtLeast(0.75f)
                                if (scale <= 1f) offset = Offset.Zero
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = StringsManager.get("zoom_out", language))
                        }

                        Slider(
                            value = scale,
                            onValueChange = { newScale ->
                                scale = newScale
                                if (scale <= 1f) offset = Offset.Zero
                            },
                            valueRange = 0.75f..4.0f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        )

                        IconButton(
                            onClick = {
                                scale = (scale + 0.25f).coerceAtMost(4.0f)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = StringsManager.get("zoom_in", language))
                        }

                        Text(
                            text = "${(scale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(42.dp)
                        )
                    }

                    // Controls Row: Prev, Page Jump Pill, Next, Reset Zoom
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Page
                        IconButton(
                            onClick = onPrevPage,
                            enabled = currentPage > 0,
                            modifier = Modifier.testTag("pdf_prev_page")
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Page")
                        }

                        // Jump to Page Pill
                        Card(
                            onClick = {
                                jumpPageInput = "${currentPage + 1}"
                                showJumpDialog = true
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMatchOnCurrentPage) Color(0xFFFEF08A) else MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${StringsManager.get("page", language)} ${currentPage + 1} / $pageCount",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMatchOnCurrentPage) Color(0xFF854D0E) else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                                if (isMatchOnCurrentPage) {
                                    Text(
                                        text = " • ★",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color(0xFFD97706),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        // Next Page
                        IconButton(
                            onClick = onNextPage,
                            enabled = currentPage < pageCount - 1,
                            modifier = Modifier.testTag("pdf_next_page")
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Page")
                        }

                        // Reset zoom button
                        TextButton(
                            onClick = {
                                scale = 1.0f
                                offset = Offset.Zero
                            }
                        ) {
                            Text(
                                text = StringsManager.get("reset_zoom", language),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Watermark strictly at the bottom-right corner in 8sp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Making by Pradeep Chavan",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(bgColor)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.75f, 4f)
                        if (scale > 1f) {
                            val maxX = (size.width * (scale - 1)) / 2f
                            val maxY = (size.height * (scale - 1)) / 2f
                            offset = Offset(
                                x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1.0f
                                offset = Offset.Zero
                            } else {
                                scale = 2.0f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF Page ${currentPage + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            // Floating indicator if current page has search matches
            if (isSearchVisible && isMatchOnCurrentPage) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFEF08A),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                ) {
                    Text(
                        text = "🔍 ${String.format(StringsManager.get("match_on_page", language), currentPage + 1)}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF854D0E)
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // Jump to Page Dialog
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("पानावर जा / Go to page") },
            text = {
                Column {
                    Text("पानाचा क्रमांक टाका (1 ते $pageCount):")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jumpPageInput,
                        onValueChange = { jumpPageInput = it.filter { char -> char.isDigit() } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = jumpPageInput.toIntOrNull()
                        if (p != null && p in 1..pageCount) {
                            onJumpToPage(p - 1)
                        }
                        showJumpDialog = false
                    }
                ) {
                    Text("जा / Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("रद्द करा / Cancel")
                }
            }
        )
    }
}
