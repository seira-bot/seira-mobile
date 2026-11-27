package bot.seira.companion.core

import kotlinx.serialization.Serializable

/**
 * One finished Live call (`kind == "call"`). Port of `LiveCallRecord` in
 * `phone-protocol`, without `client`. [endReason] stays a string so a reason a
 * newer computer adds never breaks decoding.
 */
@Serializable
data class CallRecord(
    val callId: String = "",
    val botId: String = "",
    val startedAt: Double = 0.0,
    val endedAt: Double = 0.0,
    val seconds: Double = 0.0,
    val endReason: String = "",
    val title: String? = null,
)

object CallRecords {
    /** `m:ss` under an hour, `h:mm:ss` from an hour on. Anything that is not a length reads as `0:00`. */
    fun formatCallDuration(seconds: Double): String {
        val total = if (seconds.isFinite() && seconds > 0) seconds.toLong() else 0L
        val hours = total / 3_600
        val minutes = (total % 3_600) / 60
        val rest = (total % 60).toString().padStart(2, '0')
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$rest" else "$minutes:$rest"
    }

    /** What the person said on this call: their messages that carry the call's id, in transcript order. */
    fun callRecordLines(transcript: List<Message>, callId: String): List<Message> =
        if (callId.isEmpty()) {
            emptyList()
        } else {
            transcript.filter {
                it.role == Message.Role.USER && it.via == "call" && it.callId == callId && !it.text.isNullOrBlank()
            }
        }

    /** The start of the call may sit in messages that are not loaded yet. */
    fun isPartial(transcript: List<Message>, call: CallRecord, hasMore: Boolean): Boolean {
        val oldest = transcript.firstOrNull() ?: return false
        return hasMore && oldest.at > call.startedAt
    }
}
