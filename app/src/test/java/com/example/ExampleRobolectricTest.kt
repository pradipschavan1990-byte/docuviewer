package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ExcelEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DocuViewer", appName)
  }

  @Test
  fun `test csv engine parsing`() {
    val csv = "Name,Amount\nRecharge,299\nGrocery,1850".toByteArray()
    val data = ExcelEngine.parseCsv("test.csv", csv)
    assertEquals(1, data.sheets.size)
    assertEquals(3, data.sheets[0].rows.size)
    assertEquals("Recharge", data.sheets[0].rows[1][0])
    assertEquals("299", data.sheets[0].rows[1][1])
  }

  @Test
  fun `test marathi csv engine parsing`() {
    val csv = "तारीख,तपशील,रक्कम\n०१/१०/२०२६,सोयाबीन,४५००\n०२/१०/२०२६,कांदा,२१००".toByteArray(Charsets.UTF_8)
    val data = ExcelEngine.parseCsv("मराठी_चाचणी.csv", csv)
    assertEquals(1, data.sheets.size)
    assertEquals(3, data.sheets[0].rows.size)
    assertEquals("तारीख", data.sheets[0].rows[0][0])
    assertEquals("सोयाबीन", data.sheets[0].rows[1][1])
    assertEquals("२१००", data.sheets[0].rows[2][2])
  }
}
