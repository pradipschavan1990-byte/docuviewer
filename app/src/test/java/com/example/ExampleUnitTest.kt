package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testSearchInCsvGrid() {
    val csvContent = "गट क्र,शेतकरी,पीक,उत्पन्न\n२४५,रमेश पाटील,सोयाबीन,७५०००\n२४६,गणेश कदम,कांदा,८२०००\n२४७,सुरेश पाटील,सोयाबीन,६५०००"
    val data = com.example.engine.ExcelEngine.parseCsv("test.csv", csvContent.toByteArray(Charsets.UTF_8))
    val sheet = data.sheets[0]

    // Search query: "सोयाबीन"
    val query = "सोयाबीन"
    val matches = mutableListOf<Pair<Int, Int>>()
    sheet.rows.forEachIndexed { rIndex, rowCells ->
      rowCells.forEachIndexed { cIndex, cellVal ->
        if (cellVal.contains(query, ignoreCase = true)) {
          matches.add(rIndex to cIndex)
        }
      }
    }

    assertEquals(2, matches.size)
    assertEquals(1 to 2, matches[0])
    assertEquals(3 to 2, matches[1])

    // Search query: "पाटील"
    val queryPatil = "पाटील"
    val matchesPatil = mutableListOf<Pair<Int, Int>>()
    sheet.rows.forEachIndexed { rIndex, rowCells ->
      rowCells.forEachIndexed { cIndex, cellVal ->
        if (cellVal.contains(queryPatil, ignoreCase = true)) {
          matchesPatil.add(rIndex to cIndex)
        }
      }
    }

    assertEquals(2, matchesPatil.size)
    assertEquals(1 to 1, matchesPatil[0])
    assertEquals(3 to 1, matchesPatil[1])
  }
}
