package bot.seira.companion.audio

import android.media.AudioDeviceInfo
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ProximityRulesTest {
    @Test
    fun holdsOnlyForALiveCallOnTheEarpieceWithoutAHeadset() {
        assertTrue(ProximityRules.shouldHold(live = true, speaker = false, headsetConnected = false))
        assertFalse(ProximityRules.shouldHold(live = true, speaker = true, headsetConnected = false), "speaker on")
        assertFalse(ProximityRules.shouldHold(live = true, speaker = false, headsetConnected = true), "headset")
        assertFalse(ProximityRules.shouldHold(live = false, speaker = false, headsetConnected = false), "not live")
    }

    @Test
    fun aPlugOrBluetoothOutputCountsAsAHeadsetButThePhonesOwnOutputsDoNot() {
        assertTrue(ProximityRules.headsetConnected(listOf(AudioDeviceInfo.TYPE_WIRED_HEADSET)))
        assertTrue(ProximityRules.headsetConnected(listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)))
        assertTrue(ProximityRules.headsetConnected(listOf(AudioDeviceInfo.TYPE_USB_HEADSET)))
        assertFalse(ProximityRules.headsetConnected(listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
        assertFalse(ProximityRules.headsetConnected(emptyList()))
    }
}
