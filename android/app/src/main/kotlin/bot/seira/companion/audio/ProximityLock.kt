package bot.seira.companion.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * Turns the screen off while the phone is at the ear, as a phone call does.
 * The manager reports the two facts that decide it; the rule is in
 * [ProximityRules] so a JVM test can pin it.
 */
interface LiveCallProximity {
    /** [live]: the call is live. [speaker]: the person chose the loudspeaker. */
    fun update(live: Boolean, speaker: Boolean)

    /** Give the screen back. Safe to call at any time. */
    fun release()

    object None : LiveCallProximity {
        override fun update(live: Boolean, speaker: Boolean) = Unit

        override fun release() = Unit
    }
}

@SuppressLint("InlinedApi")
internal object ProximityRules {
    /**
     * Outputs that mean the phone is not at the ear: a plug, a hearing aid, a
     * USB or Bluetooth headset or a Bluetooth speaker. The loudspeaker and the
     * earpiece are the phone's own, so they are not on this list.
     */
    private val PRIVATE_OUTPUTS: Set<Int> = setOf(
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    )

    /** Hold the proximity lock only for a live call that plays through the earpiece. */
    fun shouldHold(live: Boolean, speaker: Boolean, headsetConnected: Boolean): Boolean =
        live && !speaker && !headsetConnected

    /** Whether [outputs], as `AudioDeviceInfo.TYPE_*` values, include a headset or another private output. */
    fun headsetConnected(outputs: List<Int>): Boolean = outputs.any { it in PRIVATE_OUTPUTS }
}

internal class AndroidProximityLock(context: Context) : LiveCallProximity {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var lock: PowerManager.WakeLock? = null
    private var live = false
    private var speaker = true
    private var watching = false

    private val deviceChanges = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = apply()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = apply()
    }

    override fun update(live: Boolean, speaker: Boolean) {
        this.live = live
        this.speaker = speaker
        if (live && !watching) {
            watching = true
            audioManager.registerAudioDeviceCallback(deviceChanges, Handler(Looper.getMainLooper()))
        } else if (!live && watching) {
            watching = false
            audioManager.unregisterAudioDeviceCallback(deviceChanges)
        }
        apply()
    }

    override fun release() = update(live = false, speaker = speaker)

    private fun apply() {
        val headset = ProximityRules.headsetConnected(
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type },
        )
        if (ProximityRules.shouldHold(live, speaker, headset)) hold() else drop()
    }

    private fun hold() {
        if (lock?.isHeld == true) return
        if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        val created = lock ?: powerManager
            .newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG)
            .apply { setReferenceCounted(false) }
        lock = created
        created.acquire(MAX_HOLD_MS)
    }

    private fun drop() {
        lock?.takeIf { it.isHeld }?.release()
    }

    private companion object {
        const val TAG = "seira:live-call-proximity"
        const val MAX_HOLD_MS = 6 * 60 * 60 * 1000L
    }
}
