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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import com.example.util.PrintHelper
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
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SpreadsheetData
import com.example.util.AppLanguage
import com.example.util.StringsManager
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.launch

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
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    onSelectSheet: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleCellSelection: (Int, Int) -> Unit,
    onClearSelection: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val density = LocalDensity.current

    // Zoom scale from 35% (0.35f) to 250% (2.5f)
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var isScreenshotMode by remember { mutableStateOf(false) }
    var isControlsOverlayVisible by remember { mutableStateOf(true) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var fontScale by remember { mutableFloatStateOf(13f) }

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

    // Calculate all matching cell coordinates
    val matchingCells = remember(searchQuery, currentSheet) {
        if (searchQuery.isBlank() || currentSheet == null) emptyList<Pair<Int, Int>>()
        else {
            val list = mutableListOf<Pair<Int, Int>>()
            currentSheet.rows.forEachIndexed { rIndex, rowCells ->
                rowCells.forEachIndexed { cIndex, cellVal ->
                    if (cellVal.contains(searchQuery, ignoreCase = true)) {
                        list.add(rIndex to cIndex)
                    }
                }
            }
            list
        }
    }
    var currentMatchIndex by remember(matchingCells) { mutableIntStateOf(0) }

    fun scrollToMatch(matchIdx: Int) {
        if (matchIdx in matchingCells.indices) {
            val (targetRow, targetCol) = matchingCells[matchIdx]
            coroutineScope.launch {
                lazyListState.animateScrollToItem(targetRow)
                val colWidthPx = with(density) { cellWidth.toPx() }
                val targetScrollX = (targetCol * colWidthPx).toInt()
                horizontalScrollState.animateScrollTo(targetScrollX)
            }
        }
    }

    // Intercept back button when in screenshot mode or search mode
    BackHandler {
        if (isScreenshotMode) {
            isScreenshotMode = false
            isControlsOverlayVisible = true
        } else if (isSearchVisible) {
            isSearchVisible = false
            onSearchQueryChange("")
        } else {
            onClose()
        }
    }
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

                        // Theme Toggle Button (Light / Dark mode)
                        IconButton(
                            onClick = onToggleDarkMode,
                            modifier = Modifier.testTag("spreadsheet_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = StringsManager.get("theme_toggle", language),
                                tint = if (isDarkMode) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Print Spreadsheet via Android Print Manager
                        IconButton(
                            onClick = {
                                currentSheet?.let { PrintHelper.printSpreadsheet(context, it, title) }
                            },
                            modifier = Modifier.testTag("spreadsheet_print_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = StringsManager.get("print", language)
                            )
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
            if (!isScreenshotMode) {
                Surface(
                    color = if (isDarkMode) Color(0xFF1E293B) else MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        // 1. Selection Stats Row (if cells are selected)
                        if (selectionStats != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val (count, sum, avg) = selectionStats!!
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "${StringsManager.get("count", language)}: $count",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (sum > 0.0) {
                                            Text(
                                                text = "${StringsManager.get("sum", language)}: ${"%,.2f".format(sum)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "${StringsManager.get("avg", language)}: ${"%,.2f".format(avg)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                    IconButton(onClick = onClearSelection, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear Selection", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // 2. Continuous Zoom Slide Bar ("खालचा स्लाईड बार")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Zoom Out (-) Button
                            IconButton(
                                onClick = { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.35f) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("spreadsheet_zoom_out_slider")
                            ) {
                                Icon(
                                    Icons.Default.ZoomOut,
                                    contentDescription = StringsManager.get("zoom_out", language),
                                    tint = if (isDarkMode) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary
                                )
                            }

                            // Continuous Zoom Slider
                            Slider(
                                value = zoomScale,
                                onValueChange = { zoomScale = it },
                                valueRange = 0.35f..2.50f,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("spreadsheet_zoom_slider")
                            )

                            // Zoom In (+) Button
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.15f).coerceAtMost(2.50f) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("spreadsheet_zoom_in_slider")
                            ) {
                                Icon(
                                    Icons.Default.ZoomIn,
                                    contentDescription = StringsManager.get("zoom_in", language),
                                    tint = if (isDarkMode) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary
                                )
                            }

                            // Zoom Percentage badge (Tap to reset to 100%)
                            Card(
                                onClick = { zoomScale = 1.0f },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkMode) Color(0xFF334155) else MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(
                                    text = "${(zoomScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkMode) Color(0xFFF1F5F9) else MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // 3. Navigation Controls & Presets Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quick Presets: 50% (fit), 100%, 150%
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistChip(
                                    onClick = { zoomScale = 0.5f },
                                    label = { Text("50%", fontSize = 10.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if ((zoomScale * 100).toInt() == 50) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    ),
                                    modifier = Modifier.height(26.dp)
                                )
                                AssistChip(
                                    onClick = { zoomScale = 1.0f },
                                    label = { Text("100%", fontSize = 10.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if ((zoomScale * 100).toInt() == 100) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    ),
                                    modifier = Modifier.height(26.dp)
                                )
                                AssistChip(
                                    onClick = { zoomScale = 1.5f },
                                    label = { Text("150%", fontSize = 10.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if ((zoomScale * 100).toInt() == 150) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    ),
                                    modifier = Modifier.height(26.dp)
                                )
                            }

                            // Slide / Navigation Buttons (Scroll to top and bottom)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { coroutineScope.launch { lazyListState.animateScrollToItem(0) } },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = StringsManager.get("scroll_to_top", language),
                                        tint = if (isDarkMode) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val lastIdx = ((currentSheet?.rows?.size ?: 1) - 1).coerceAtLeast(0)
                                        coroutineScope.launch { lazyListState.animateScrollToItem(lastIdx) }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = StringsManager.get("scroll_to_bottom", language),
                                        tint = if (isDarkMode) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // 4. Subtle Watermark strictly at the bottom-right corner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Making by Pradeep Chavan",
                                fontSize = 8.sp,
                                color = (if (isDarkMode) Color.White else Color.Black).copy(alpha = 0.35f)
                            )
                        }
                    }
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
                // Enhanced Search Bar with match count and previous/next navigation
                if (!isScreenshotMode && isSearchVisible) {
                    Surface(
                        color = if (isDarkMode) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { newQ ->
                                        onSearchQueryChange(newQ)
                                        currentMatchIndex = 0
                                        if (newQ.isNotBlank()) {
                                            scrollToMatch(0)
                                        }
                                    },
                                    placeholder = { Text(StringsManager.get("search_in_sheet", language)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    onSearchQueryChange("")
                                                    currentMatchIndex = 0
                                                }
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("spreadsheet_search_input")
                                )

                                IconButton(
                                    onClick = {
                                        isSearchVisible = false
                                        onSearchQueryChange("")
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Search")
                                }
                            }

                            // Match Counter and Navigation Row
                            if (searchQuery.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, start = 4.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (matchingCells.isNotEmpty()) {
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isDarkMode) Color(0xFF334155) else MaterialTheme.colorScheme.primaryContainer
                                            )
                                        ) {
                                            Text(
                                                text = "${currentMatchIndex + 1} / ${matchingCells.size} ${StringsManager.get("matches_found", language).substringAfter(" ")}",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDarkMode) Color(0xFFF1F5F9) else MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    val newIdx = if (currentMatchIndex <= 0) matchingCells.size - 1 else currentMatchIndex - 1
                                                    currentMatchIndex = newIdx
                                                    scrollToMatch(newIdx)
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("sheet_prev_match")
                                            ) {
                                                Icon(
                                                    Icons.Default.KeyboardArrowUp,
                                                    contentDescription = StringsManager.get("prev_match", language),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    val newIdx = (currentMatchIndex + 1) % matchingCells.size
                                                    currentMatchIndex = newIdx
                                                    scrollToMatch(newIdx)
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("sheet_next_match")
                                            ) {
                                                Icon(
                                                    Icons.Default.KeyboardArrowDown,
                                                    contentDescription = StringsManager.get("next_match", language),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    } else {
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

                // Spreadsheet Grid View with 2D Scrolling and Two-Finger Pinch-to-Zoom
                if (currentSheet != null) {
                    val colCount = currentSheet.maxColumns.coerceAtLeast(1)

                    var isPinching by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isDarkMode) Color(0xFF0F172A) else Color(0xFFFFFFFF))
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val activePointers = mutableMapOf<PointerId, Offset>()
                                    var previousDistance = -1f

                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)

                                        // Track all currently pressed pointer positions
                                        for (change in event.changes) {
                                            if (change.pressed) {
                                                activePointers[change.id] = change.position
                                            } else {
                                                activePointers.remove(change.id)
                                            }
                                        }

                                        if (activePointers.size >= 2) {
                                            isPinching = true
                                            val points = activePointers.values.toList()
                                            val currentDistance = hypot(points[0].x - points[1].x, points[0].y - points[1].y)

                                            if (previousDistance > 0f && currentDistance > 0f) {
                                                val factor = currentDistance / previousDistance
                                                if (factor in 0.4f..2.5f && abs(factor - 1f) > 0.001f) {
                                                    zoomScale = (zoomScale * factor).coerceIn(0.35f, 2.50f)
                                                }
                                            }
                                            previousDistance = currentDistance

                                            // Consume multi-touch to avoid click triggers or unwanted drags
                                            event.changes.forEach { it.consume() }
                                        } else {
                                            isPinching = false
                                            previousDistance = -1f
                                            if (event.changes.none { it.pressed }) {
                                                activePointers.clear()
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header Column Letters (A, B, C...)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isDarkMode) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant)
                                    .horizontalScroll(horizontalScrollState)
                            ) {
                                // Top-left blank corner for row number column
                                Box(
                                    modifier = Modifier
                                        .width(rowHeaderWidth)
                                        .height(headerHeight)
                                        .border(
                                            0.5.dp,
                                            if (isDarkMode) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#",
                                        fontSize = effectiveHeaderFontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkMode) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary
                                    )
                                }

                                for (c in 0 until colCount) {
                                    val colLetter = getColumnLetter(c)
                                    Box(
                                        modifier = Modifier
                                            .width(cellWidth)
                                            .height(headerHeight)
                                            .border(
                                                0.5.dp,
                                                if (isDarkMode) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = colLetter,
                                            fontSize = effectiveHeaderFontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkMode) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Rows List - Smooth vertical sliding with LazyColumn
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(horizontalScrollState)
                            ) {
                                itemsIndexed(currentSheet.rows) { rowIndex, rowCells ->
                                    val isHeaderRow = rowIndex == 0
                                    val rowBg = when {
                                        isHeaderRow -> if (isDarkMode) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        rowIndex % 2 == 1 -> if (isDarkMode) Color(0xFF16202E) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                        else -> if (isDarkMode) Color(0xFF0F172A) else MaterialTheme.colorScheme.surface
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
                                                .background(if (isDarkMode) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .border(
                                                    0.5.dp,
                                                    if (isDarkMode) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${rowIndex + 1}",
                                                fontSize = effectiveRowNumberFontSize,
                                                color = if (isDarkMode) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Cells in current row
                                        for (colIndex in 0 until colCount) {
                                            val cellText = rowCells.getOrNull(colIndex) ?: ""
                                            val isSelected = selectedCells.contains(rowIndex to colIndex)
                                            val isMatch = searchQuery.isNotBlank() &&
                                                    cellText.contains(searchQuery, ignoreCase = true)
                                            val isActiveMatch = isMatch && matchingCells.getOrNull(currentMatchIndex) == (rowIndex to colIndex)

                                            val cellBg = when {
                                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                isActiveMatch -> if (isDarkMode) Color(0xFFD97706) else Color(0xFFF59E0B) // Bold Amber for currently focused match
                                                isMatch -> if (isDarkMode) Color(0xFF854D0E) else Color(0xFFFEF08A) // Yellow highlight for other matches
                                                else -> Color.Transparent
                                            }

                                            val borderStroke = when {
                                                isActiveMatch -> androidx.compose.foundation.BorderStroke(2.dp, if (isDarkMode) Color(0xFFFDE047) else Color(0xFFB45309))
                                                else -> androidx.compose.foundation.BorderStroke(
                                                    0.5.dp,
                                                    if (isDarkMode) Color(0xFF334155).copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .width(cellWidth)
                                                    .height(cellHeight)
                                                    .background(cellBg)
                                                    .border(borderStroke)
                                                    .clickable { onToggleCellSelection(rowIndex, colIndex) }
                                                    .padding(horizontal = cellPaddingHorizontal, vertical = cellPaddingVertical),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Text(
                                                    text = cellText,
                                                    fontSize = effectiveFontSize,
                                                    fontWeight = when {
                                                        isActiveMatch -> FontWeight.Bold
                                                        isHeaderRow -> FontWeight.Bold
                                                        else -> FontWeight.Normal
                                                    },
                                                    color = when {
                                                        isActiveMatch -> Color.White
                                                        isMatch -> if (isDarkMode) Color(0xFFFEF08A) else Color.Black
                                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                        isHeaderRow -> if (isDarkMode) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary
                                                        else -> if (isDarkMode) Color(0xFFF1F5F9) else MaterialTheme.colorScheme.onSurface
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

                        // Real-time floating zoom indicator badge while two-finger pinching
                        if (isPinching) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                                shadowElevation = 10.dp,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ZoomIn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "${(zoomScale * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
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

                // Tiny watermark strictly at bottom-right corner
                Text(
                    text = "Making by Pradeep Chavan",
                    fontSize = 8.sp,
                    color = (if (isDarkMode) Color.White else Color.Black).copy(alpha = 0.25f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 4.dp)
                )
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
