package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SpreadsheetData
import com.example.util.AppLanguage
import com.example.util.StringsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadsheetViewerScreen(
    title: String,
    uri: Uri,
    language: AppLanguage,
    data: SpreadsheetData,
    selectedSheetIndex: Int,
    searchQuery: String,
    selectedCells: Set<Pair<Int, Int>>,
    onSelectSheet: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleCellSelection: (Int, Int) -> Unit,
    onClearSelection: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    // Zoom scale from 35% (0.35f) to 250% (2.5f)
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var isScreenshotMode by remember { mutableStateOf(false) }
    var isControlsOverlayVisible by remember { mutableStateOf(true) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var fontScale by remember { mutableFloatStateOf(13f) }

    // Intercept back button when in screenshot mode to exit screenshot mode first
    BackHandler {
        if (isScreenshotMode) {
            isScreenshotMode = false
            isControlsOverlayVisible = true
        } else {
            onClose()
        }
    }

    val currentSheet = remember(data, selectedSheetIndex) {
        if (selectedSheetIndex in data.sheets.indices) {
            data.sheets[selectedSheetIndex]
        } else {
            data.sheets.firstOrNull()
        }
    }

    // Dynamic sizing based on zoomScale
    val cellWidth = (130.dp * zoomScale).coerceAtLeast(44.dp)
    val rowHeaderWidth = (44.dp * zoomScale).coerceAtLeast(26.dp)
    val cellHeight = ((fontScale * 2.8).dp * zoomScale).coerceAtLeast(20.dp)
    val headerHeight = (32.dp * zoomScale).coerceAtLeast(22.dp)
    val effectiveFontSize = (fontScale * zoomScale).coerceIn(6.5f, 26f).sp
    val effectiveHeaderFontSize = (12f * zoomScale).coerceIn(7.5f, 20f).sp
    val effectiveRowNumberFontSize = (11f * zoomScale).coerceIn(7f, 18f).sp
    val cellPaddingHorizontal = (8.dp * zoomScale).coerceIn(2.dp, 10.dp)
    val cellPaddingVertical = (4.dp * zoomScale).coerceIn(1.dp, 6.dp)

    // Calculate selection statistics (Count, Sum, Avg)
    val selectionStats by remember(selectedCells, currentSheet) {
        derivedStateOf {
            if (selectedCells.isEmpty() || currentSheet == null) null
            else {
                var count = 0
                var numericCount = 0
                var sum = 0.0

                for ((r, c) in selectedCells) {
                    if (r in currentSheet.rows.indices && c in currentSheet.rows[r].indices) {
                        count++
                        val text = currentSheet.rows[r][c].replace(",", "").replace("₹", "").trim()
                        val num = text.toDoubleOrNull()
                        if (num != null) {
                            numericCount++
                            sum += num
                        }
                    }
                }
                val avg = if (numericCount > 0) sum / numericCount else 0.0
                Triple(count, sum, avg)
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isScreenshotMode) {
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
                                text = "${currentSheet?.rows?.size ?: 0} ${StringsManager.get("rows", language)} • ${currentSheet?.maxColumns ?: 0} ${StringsManager.get("cols", language)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose, modifier = Modifier.testTag("spreadsheet_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Zoom Out (-) button in TopBar
                        IconButton(
                            onClick = { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.35f) },
                            modifier = Modifier.testTag("spreadsheet_zoom_out_top")
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = StringsManager.get("zoom_out", language))
                        }

                        // Zoom In (+) button in TopBar
                        IconButton(
                            onClick = { zoomScale = (zoomScale + 0.15f).coerceAtMost(2.5f) },
                            modifier = Modifier.testTag("spreadsheet_zoom_in_top")
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = StringsManager.get("zoom_in", language))
                        }

                        // Screenshot Clean View Mode button
                        IconButton(
                            onClick = {
                                isScreenshotMode = true
                                isControlsOverlayVisible = true
                            },
                            modifier = Modifier.testTag("spreadsheet_screenshot_mode_top")
                        ) {
                            Icon(Icons.Default.CropFree, contentDescription = StringsManager.get("screenshot_mode", language))
                        }

                        // Search in Sheet
                        IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }

                        // Share File
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (title.endsWith(".csv", true)) "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Spreadsheet"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        bottomBar = {
            if (!isScreenshotMode && selectionStats != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val (count, sum, avg) = selectionStats!!
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "${StringsManager.get("count", language)}: $count",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                if (sum > 0.0) {
                                    Text(
                                        text = "${StringsManager.get("sum", language)}: ${"%,.2f".format(sum)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${StringsManager.get("avg", language)}: ${"%,.2f".format(avg)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        IconButton(onClick = onClearSelection, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Making by Pradeep Chavan",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Bar
                if (!isScreenshotMode && isSearchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(StringsManager.get("search_in_sheet", language)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true
                    )
                }

                // Multi-sheet tabs
                if (!isScreenshotMode && data.sheets.size > 1) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedSheetIndex,
                        edgePadding = 8.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        data.sheets.forEachIndexed { index, sheet ->
                            Tab(
                                selected = selectedSheetIndex == index,
                                onClick = { onSelectSheet(index) },
                                text = { Text(sheet.name, maxLines = 1) }
                            )
                        }
                    }
                }

                // Dedicated Zoom Toolbar for easy screenshot preparation
                if (!isScreenshotMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Zoom Out (-) Button
                            IconButton(
                                onClick = { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.35f) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("spreadsheet_zoom_out_bar")
                            ) {
                                Icon(
                                    Icons.Default.ZoomOut,
                                    contentDescription = StringsManager.get("zoom_out", language),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Zoom Percentage indicator badge (click to reset / cycle)
                            Card(
                                onClick = {
                                    zoomScale = when {
                                        zoomScale < 0.65f -> 1.0f
                                        zoomScale < 1.15f -> 0.55f
                                        else -> 1.0f
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text(
                                    text = "${(zoomScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            // Zoom In (+) Button
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.15f).coerceAtMost(2.5f) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("spreadsheet_zoom_in_bar")
                            ) {
                                Icon(
                                    Icons.Default.ZoomIn,
                                    contentDescription = StringsManager.get("zoom_in", language),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(Modifier.width(2.dp))

                            // Quick Presets: 50% (Fit/Screenshot all columns), 75%, 100%, 150%
                            AssistChip(
                                onClick = { zoomScale = 0.5f },
                                label = { Text("50% (${StringsManager.get("fit_columns", language)})", fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if ((zoomScale * 100).toInt() == 50) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.height(30.dp)
                            )

                            AssistChip(
                                onClick = { zoomScale = 1.0f },
                                label = { Text("100%", fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if ((zoomScale * 100).toInt() == 100) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.height(30.dp)
                            )

                            AssistChip(
                                onClick = { zoomScale = 1.5f },
                                label = { Text("150%", fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if ((zoomScale * 100).toInt() == 150) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.height(30.dp)
                            )

                            Spacer(Modifier.width(4.dp))

                            // Screenshot Mode Button
                            FilledTonalButton(
                                onClick = {
                                    isScreenshotMode = true
                                    isControlsOverlayVisible = true
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("spreadsheet_screenshot_mode_btn")
                            ) {
                                Icon(
                                    Icons.Default.CropFree,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = StringsManager.get("screenshot_mode", language),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Spreadsheet Grid View with 2D Scrolling
                if (currentSheet != null) {
                    val horizontalScrollState = rememberScrollState()
                    val colCount = currentSheet.maxColumns.coerceAtLeast(1)

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header Column Letters (A, B, C...)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            // Top-left blank corner for row number column
                            Box(
                                modifier = Modifier
                                    .width(rowHeaderWidth)
                                    .height(headerHeight)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("#", fontSize = effectiveHeaderFontSize, fontWeight = FontWeight.Bold)
                            }

                            for (c in 0 until colCount) {
                                val colLetter = getColumnLetter(c)
                                Box(
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .height(headerHeight)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = colLetter,
                                        fontSize = effectiveHeaderFontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Rows List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            itemsIndexed(currentSheet.rows) { rowIndex, rowCells ->
                                val isHeaderRow = rowIndex == 0
                                val rowBg = when {
                                    isHeaderRow -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    rowIndex % 2 == 1 -> MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                    else -> MaterialTheme.colorScheme.surface
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                ) {
                                    // Row Number column on left
                                    Box(
                                        modifier = Modifier
                                            .width(rowHeaderWidth)
                                            .height(cellHeight)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${rowIndex + 1}",
                                            fontSize = effectiveRowNumberFontSize,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Cells in current row
                                    for (colIndex in 0 until colCount) {
                                        val cellText = rowCells.getOrNull(colIndex) ?: ""
                                        val isSelected = selectedCells.contains(rowIndex to colIndex)
                                        val isMatch = searchQuery.isNotBlank() &&
                                                cellText.contains(searchQuery, ignoreCase = true)

                                        val cellBg = when {
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            isMatch -> Color(0xFFFEF08A) // Yellow highlight
                                            else -> Color.Transparent
                                        }

                                        Box(
                                            modifier = Modifier
                                                .width(cellWidth)
                                                .height(cellHeight)
                                                .background(cellBg)
                                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                                .clickable { onToggleCellSelection(rowIndex, colIndex) }
                                                .padding(horizontal = cellPaddingHorizontal, vertical = cellPaddingVertical),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = cellText,
                                                fontSize = effectiveFontSize,
                                                fontWeight = if (isHeaderRow) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isMatch -> Color.Black
                                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    isHeaderRow -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Minimal Zoom & Exit Controller in Screenshot Mode
            if (isScreenshotMode) {
                AnimatedVisibility(
                    visible = isControlsOverlayVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Zoom Out (-) Button
                            IconButton(
                                onClick = { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.35f) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = StringsManager.get("zoom_out", language))
                            }

                            // Zoom Percentage pill
                            Card(
                                onClick = {
                                    zoomScale = when {
                                        zoomScale < 0.65f -> 1.0f
                                        zoomScale < 1.15f -> 0.55f
                                        else -> 1.0f
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Text(
                                    text = "${(zoomScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Zoom In (+) Button
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.15f).coerceAtMost(2.5f) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = StringsManager.get("zoom_in", language))
                            }

                            // Quick 50% fit chip
                            AssistChip(
                                onClick = { zoomScale = 0.5f },
                                label = { Text("50%", fontSize = 11.sp) },
                                modifier = Modifier.height(28.dp)
                            )

                            // Exit Screenshot Mode Button
                            FilledTonalButton(
                                onClick = { isScreenshotMode = false },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.FullscreenExit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = StringsManager.get("exit_screenshot_mode", language),
                                    fontSize = 11.sp
                                )
                            }

                            // Hide overlay icon (for 100% clean screenshot, user can tap screen to bring back)
                            IconButton(
                                onClick = { isControlsOverlayVisible = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.VisibilityOff, contentDescription = "Hide controls for screenshot", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // If controls overlay is hidden, show a subtle floating button in corner to restore
                if (!isControlsOverlayVisible) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        IconButton(
                            onClick = { isControlsOverlayVisible = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = "Show controls", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}

private fun getColumnLetter(colIndex: Int): String {
    var c = colIndex
    val sb = StringBuilder()
    while (c >= 0) {
        sb.insert(0, ('A'.code + (c % 26)).toChar())
        c = (c / 26) - 1
    }
    return sb.toString()
}
