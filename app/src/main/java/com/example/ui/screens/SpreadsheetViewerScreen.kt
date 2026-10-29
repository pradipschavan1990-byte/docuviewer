package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
    BackHandler { onClose() }

    var isSearchVisible by remember { mutableStateOf(false) }
    var fontScale by remember { mutableFloatStateOf(13f) }

    val currentSheet = remember(data, selectedSheetIndex) {
        if (selectedSheetIndex in data.sheets.indices) {
            data.sheets[selectedSheetIndex]
        } else {
            data.sheets.firstOrNull()
        }
    }

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
                    // Search in Sheet
                    IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }

                    // Font Size toggle
                    IconButton(
                        onClick = {
                            fontScale = if (fontScale >= 16f) 11f else fontScale + 2.5f
                        }
                    ) {
                        Icon(Icons.Default.FormatSize, contentDescription = "Font size")
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
        },
        bottomBar = {
            if (selectionStats != null) {
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
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            if (isSearchVisible) {
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
            if (data.sheets.size > 1) {
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
                                .width(44.dp)
                                .height(32.dp)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("#", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        for (c in 0 until colCount) {
                            val colLetter = getColumnLetter(c)
                            Box(
                                modifier = Modifier
                                    .width(130.dp)
                                    .height(32.dp)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = colLetter,
                                    fontSize = 12.sp,
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
                                        .width(44.dp)
                                        .height((fontScale * 2.8).dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${rowIndex + 1}",
                                        fontSize = 11.sp,
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
                                            .width(130.dp)
                                            .height((fontScale * 2.8).dp)
                                            .background(cellBg)
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                            .clickable { onToggleCellSelection(rowIndex, colIndex) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = cellText,
                                            fontSize = fontScale.sp,
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
