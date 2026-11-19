package com.openmausbot.companion.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

enum class SeiraLookSlot { FACE, EARS, HAIR, TOP, NECK }

sealed interface SeiraExtraToggle {
    data class Updated(val ids: List<String>) : SeiraExtraToggle
    data object Full : SeiraExtraToggle
}

/**
 * The values a Seira look may take. They must match the desktop catalog:
 * the server reads an id it does not know as the default look.
 */
object SeiraLookCatalog {
    val hairIds: List<String> = listOf(
        "seira", "seira-long", "bob", "twin-tails", "ponytail", "long-straight",
        "bun", "long-wavy", "short-neat", "center-part", "spiky", "slick-back",
        "long-tied", "long-loose", "buzz", "messy", "none",
    )

    const val DEFAULT_HAIR = "seira"

    /** An unknown or retired body id reads as [DEFAULT_HAIR], as on the desktop. */
    fun hair(id: String?): String = if (id != null && id in hairIds) id else DEFAULT_HAIR

    private val accessorySlots: List<Pair<String, SeiraLookSlot>> = listOf(
        "glasses" to SeiraLookSlot.FACE, "sunglasses" to SeiraLookSlot.FACE,
        "earring" to SeiraLookSlot.EARS, "headphones" to SeiraLookSlot.EARS,
        "headset-mic" to SeiraLookSlot.EARS,
        "ribbon" to SeiraLookSlot.HAIR, "hairpin" to SeiraLookSlot.HAIR, "tiara" to SeiraLookSlot.HAIR,
        "cap" to SeiraLookSlot.TOP, "antenna" to SeiraLookSlot.TOP,
        "scarf" to SeiraLookSlot.NECK, "bowtie" to SeiraLookSlot.NECK,
    )

    val accessoryIds: List<String> = accessorySlots.map { it.first }

    fun slot(accessory: String): SeiraLookSlot? = accessorySlots.firstOrNull { it.first == accessory }?.second

    const val MAX_ACCESSORIES = 3

    /** Keeps known ids, the first one in each slot, at most [MAX_ACCESSORIES], in input order. */
    fun accessories(ids: List<String>?): List<String> {
        val usedSlots = mutableSetOf<SeiraLookSlot>()
        val result = mutableListOf<String>()
        for (id in ids.orEmpty()) {
            if (result.size >= MAX_ACCESSORIES) continue
            val itemSlot = slot(id) ?: continue
            if (!usedSlots.add(itemSlot)) continue
            result.add(id)
        }
        return result
    }

    /**
     * An id that is on comes off. An id whose slot is taken swaps with the one
     * worn there. A new slot while the limit is reached gives [SeiraExtraToggle.Full].
     */
    fun toggle(id: String, current: List<String>): SeiraExtraToggle {
        val worn = accessories(current)
        if (id in worn) return SeiraExtraToggle.Updated(worn.filter { it != id })
        val newSlot = slot(id) ?: return SeiraExtraToggle.Updated(worn)
        val index = worn.indexOfFirst { slot(it) == newSlot }
        if (index >= 0) {
            val swapped = worn.toMutableList()
            swapped[index] = id
            return SeiraExtraToggle.Updated(swapped)
        }
        if (worn.size >= MAX_ACCESSORIES) return SeiraExtraToggle.Full
        return SeiraExtraToggle.Updated(worn + id)
    }

    val starterColors: List<String> = listOf(
        "green", "blue", "red", "orange", "purple", "cyan", "pink", "yellow", "teal", "coral",
    )

    val colorHex: Map<String, String> = mapOf(
        "green" to "#009957",
        "blue" to "#377FE6",
        "red" to "#D94B52",
        "orange" to "#E78531",
        "purple" to "#8057C8",
        "cyan" to "#0EA5C6",
        "pink" to "#D84F8B",
        "yellow" to "#D8A729",
        "teal" to "#01A492",
        "coral" to "#E5634E",
        "silver" to "#A8ADB4",
        "chrome" to "#B3BAC4",
    )

    const val DEFAULT_COLOR = "green"

    /** A known colour name, else the colour of a new or reset bot. */
    fun color(name: String?): String = if (name != null && name in colorHex) name else DEFAULT_COLOR

    val faceInks: List<String> = listOf("white", "black")

    val expressions: List<String> = listOf(
        "idle", "happy", "curious", "drowsy", "working",
        "thinking", "listening", "sleeping", "suspicious", "proud",
    )
}

data class SeiraLook(
    val hair: String,
    val color: String,
    val faceInk: String?,
    val expression: String?,
    val accessories: List<String>,
) {
    companion object {
        fun of(
            hair: String?,
            color: String?,
            faceInk: String? = null,
            expression: String? = null,
            accessories: List<String>? = null,
        ): SeiraLook = SeiraLook(
            hair = SeiraLookCatalog.hair(hair),
            color = SeiraLookCatalog.color(color),
            faceInk = faceInk?.takeIf { it in SeiraLookCatalog.faceInks },
            expression = expression?.takeIf { it in SeiraLookCatalog.expressions },
            accessories = SeiraLookCatalog.accessories(accessories),
        )

        fun from(bot: Bot): SeiraLook = of(
            hair = bot.mascotBody,
            color = bot.color,
            faceInk = bot.mascotFaceColor,
            expression = bot.mascotExpression,
            accessories = bot.mascotAccessories,
        )

        /** Red is the colour of the app palette nearest to Seira pink (#FF2A57). */
        val SEIRA: SeiraLook = of(hair = "seira", color = "red")
    }
}

@Serializable
data class SeiraCastMember(
    val id: String,
    val name: String,
    val mascotBody: String,
    val mascotAccessories: List<String> = emptyList(),
    val color: String,
) {
    val look: SeiraLook
        get() = SeiraLook.of(hair = mascotBody, color = color, accessories = mascotAccessories)
}

object SeiraCast {
    fun decode(json: String): List<SeiraCastMember> =
        CompanionJson.decodeFromString(ListSerializer(SeiraCastMember.serializer()), json)

    /**
     * The look of a random member that differs from [current]. With no other
     * member it gives the first member's look, or [current] for an empty list.
     */
    fun surprise(members: List<SeiraCastMember>, current: SeiraLook, random: Random): SeiraLook {
        val others = members.map { it.look }.filter { it != current }
        if (others.isNotEmpty()) return others[random.nextInt(others.size)]
        return members.firstOrNull()?.look ?: current
    }
}

/**
 * The body of `PATCH /api/bots/:id` for a look change. Only fields that
 * differ are sent. A cleared face ink or expression goes out as JSON null.
 */
data class BotLookPatch(
    val color: String? = null,
    val mascotBody: String? = null,
    val mascotAccessories: List<String>? = null,
    val mascotFaceColor: Change? = null,
    val mascotExpression: Change? = null,
) {
    sealed interface Change {
        data class Set(val value: String) : Change
        data object Clear : Change
    }

    val isEmpty: Boolean
        get() = color == null && mascotBody == null && mascotAccessories == null &&
            mascotFaceColor == null && mascotExpression == null

    fun toJson(): JsonObject = buildJsonObject {
        color?.let { put("color", it) }
        mascotBody?.let { put("mascotBody", it) }
        mascotAccessories?.let { ids ->
            put("mascotAccessories", kotlinx.serialization.json.JsonArray(ids.map { JsonPrimitive(it) }))
        }
        mascotFaceColor?.let { put("mascotFaceColor", it.toJsonValue()) }
        mascotExpression?.let { put("mascotExpression", it.toJsonValue()) }
    }

    private fun Change.toJsonValue() = when (this) {
        is Change.Set -> JsonPrimitive(value)
        Change.Clear -> JsonNull
    }

    companion object {
        fun between(old: SeiraLook, new: SeiraLook): BotLookPatch = BotLookPatch(
            color = new.color.takeIf { it != old.color },
            mascotBody = new.hair.takeIf { it != old.hair },
            mascotAccessories = new.accessories.takeIf { it != old.accessories },
            mascotFaceColor = change(old.faceInk, new.faceInk),
            mascotExpression = change(old.expression, new.expression),
        )

        private fun change(old: String?, new: String?): Change? = when {
            old == new -> null
            new != null -> Change.Set(new)
            else -> Change.Clear
        }
    }
}
