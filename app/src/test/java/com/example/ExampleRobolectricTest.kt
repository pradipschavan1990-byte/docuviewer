package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ExcelEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
