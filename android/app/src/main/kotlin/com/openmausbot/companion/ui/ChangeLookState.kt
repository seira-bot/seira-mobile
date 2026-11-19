package com.openmausbot.companion.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.BotLookPatch
import com.openmausbot.companion.core.SeiraCast
import com.openmausbot.companion.core.SeiraCastMember
import com.openmausbot.companion.core.SeiraLook
import com.openmausbot.companion.core.SeiraLookCatalog
import kotlin.random.Random

internal enum class ChangeLookCategory { CHARACTERS, HAIR, COLOUR, FACE, EXTRAS }

/** What the "Change look" sheet edits: one look, the choices made so far and the state of the save. */
internal class ChangeLookState(
    val original: SeiraLook,
    members: List<SeiraCastMember>,
    private val random: Random = Random.Default,
    restoredLook: SeiraLook = original,
    restoredCategory: ChangeLookCategory = ChangeLookCategory.CHARACTERS,
    restoredSaveFailed: Boolean = false,
) {
    /** Seira first, then the rest in catalog order. */
    val cast: List<SeiraCastMember> = members.filter { it.id == SEIRA_ID } + members.filter { it.id != SEIRA_ID }

    var look by mutableStateOf(restoredLook)
        private set
    var category by mutableStateOf(restoredCategory)
    var saving by mutableStateOf(false)
        private set
    var saveFailed by mutableStateOf(restoredSaveFailed)
        private set

    /** Counts taps on the preview; the screen shows the happy face for a moment after each. */
    var celebrations by mutableIntStateOf(0)
        private set

    val changed: Boolean get() = look != original

    fun canSave(connected: Boolean): Boolean = connected && changed && !saving

    fun pick(member: SeiraCastMember) = change(member.look)

    fun setHair(id: String) = change(look.copy(hair = id))

    fun setColor(name: String) = change(look.copy(color = name))

    fun isExpressionSelected(id: String): Boolean =
        if (id == CALM) look.expression == null || look.expression == CALM else look.expression == id

    fun setExpression(id: String) {
        val expression = when {
            id != CALM -> id
            original.expression == CALM -> CALM
            else -> null
        }
        change(look.copy(expression = expression))
    }

    fun toggleExtra(id: String) = change(look.copy(accessories = SeiraLookCatalog.toggle(id, look.accessories)))

    fun surprise() = change(SeiraCast.surprise(cast, look, random))

    fun celebrate() {
        celebrations++
    }

    fun patch(): BotLookPatch = BotLookPatch.between(original, look)

    /** Sends the patch through [update]; a null answer is a failure. Returns whether the save worked. */
    suspend fun save(update: suspend (BotLookPatch) -> Bot?): Boolean {
        if (saving) return false
        saving = true
        saveFailed = false
        val saved = try {
            update(patch()) != null
        } finally {
            saving = false
        }
        saveFailed = !saved
        return saved
    }

    private fun change(next: SeiraLook) {
        look = next
        saveFailed = false
    }

    companion object {
        const val CALM = "idle"
        private const val SEIRA_ID = "seira"

        private fun SeiraLook.pack(): List<Any> = listOf(
            hair, color, ArrayList(listOfNotNull(faceInk)), ArrayList(listOfNotNull(expression)), ArrayList(accessories),
        )

        @Suppress("UNCHECKED_CAST")
        private fun unpack(values: List<Any>): SeiraLook = SeiraLook(
            hair = values[0] as String,
            color = values[1] as String,
            faceInk = (values[2] as List<String>).firstOrNull(),
            expression = (values[3] as List<String>).firstOrNull(),
            accessories = (values[4] as List<String>).toList(),
        )

        /** Keeps the choices across a rotation and a process restart; a save in flight is not kept. */
        fun saver(members: List<SeiraCastMember>): Saver<ChangeLookState, Any> = listSaver(
            save = { state ->
                state.original.pack() + state.look.pack() + state.category.name + state.saveFailed
            },
            restore = { values ->
                ChangeLookState(
                    original = unpack(values.subList(0, 5)),
                    members = members,
                    restoredLook = unpack(values.subList(5, 10)),
                    restoredCategory = ChangeLookCategory.valueOf(values[10] as String),
                    restoredSaveFailed = values[11] as Boolean,
                )
            },
        )

        val faceExpressions: List<String> = listOf(CALM, "happy", "surprised", "drowsy", "thinking")
    }
}
