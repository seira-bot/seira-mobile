package com.openmausbot.companion

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairingLinkBrowserTest {
    private val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://c-demo.seira.run"))

    @Test
    fun `a missing browser tells the person instead of failing silently`() {
        var explained = 0
        startOrExplain(view, { throw ActivityNotFoundException() }) { explained++ }
        assertEquals(1, explained)
    }

    @Test
    fun `a working browser shows no message`() {
        val started = mutableListOf<Intent>()
        var explained = 0
        startOrExplain(view, { started += it }) { explained++ }
        assertEquals(listOf(view), started)
        assertEquals(0, explained)
    }
}
