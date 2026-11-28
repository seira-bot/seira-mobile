package bot.seira.companion.tile

import android.content.Context

/** The chief of staff's name, kept so the tile can show it before the roster has loaded. */
class TalkTileState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var chiefName: String?
        get() = prefs.getString(KEY_CHIEF_NAME, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_CHIEF_NAME) else putString(KEY_CHIEF_NAME, value)
            }.apply()
        }

    private companion object {
        const val NAME = "talk_tile"
        const val KEY_CHIEF_NAME = "chiefName"
    }
}
