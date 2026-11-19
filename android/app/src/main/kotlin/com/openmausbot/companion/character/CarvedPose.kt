package com.openmausbot.companion.character

import com.openmausbot.companion.core.SeiraLookCatalog
import com.openmausbot.companion.ui.CharacterMood
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Where the face looks and how it feels. */
internal data class CarvedPose(
    /** Gaze, -1..1 each way. */
    val gx: Double = 0.0,
    val gy: Double = 0.0,
    /** Startle, 0..1: round eyes, open mouth, stretched body. */
    val wide: Double = 0.0,
    /** Eyelids, 0 open .. 1 shut. */
    val shut: Double = 0.0,
    /** Smile curvature: 1 is the resting smile, below 0 a frown. */
    val smile: Double = 1.0,
)

internal val REST_POSE = CarvedPose()

internal class CarvedEyeRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val radius: Double,
    val tilt: Double,
    val pivotX: Double,
    val pivotY: Double,
)

/** Body transform, in the 240-unit square: translate(120, 150), rotate(lean), scale(scaleX, scaleY), translate(-120, -150). */
internal class CarvedGeometry(
    val lean: Double,
    val scaleX: Double,
    val scaleY: Double,
    val eyes: List<CarvedEyeRect>,
    val smile: CarvedSmile,
    val open: CarvedOpenMouth,
)

internal class CarvedSmile(
    val startX: Double,
    val startY: Double,
    val controlX: Double,
    val controlY: Double,
    val endX: Double,
    val endY: Double,
    val tilt: Double,
    val pivotX: Double,
    val pivotY: Double,
    val opacity: Double,
)

internal class CarvedOpenMouth(val cx: Double, val cy: Double, val rx: Double, val ry: Double, val opacity: Double)

internal const val BODY_PIVOT_X = 120.0
internal const val BODY_PIVOT_Y = 150.0

private fun r2(n: Double): Double = floor(n * 100.0 + 0.5) / 100.0

/** Every number the face needs for a pose, rounded to two places like the desktop's SVG attributes. */
internal fun carvedGeometry(body: SeiraBody, p: CarvedPose): CarvedGeometry {
    val e = body.eyes
    val ecx = e.cx.toDouble()
    val ecy = e.cy.toDouble()
    val edx = e.dx.toDouble()
    val ew = e.w.toDouble()
    val eh = e.h.toDouble()
    val eround = e.round.toDouble()
    val ax = abs(p.gx)
    val lean = -p.gx * 9
    val sx = 1 - 0.06 * p.wide
    val sy = 1 + 0.09 * p.wide
    val spread = edx * (1 - 0.1 * ax) * (1 + 0.14 * p.wide)
    val cx = ecx + p.gx * edx * 1.05
    val cy = ecy + p.gy * 16 - p.wide * eh * 0.42
    var w = ew * (1 + 0.05 * ax)
    var h = eh * (1 - 0.3 * ax)
    w += (eround - w) * p.wide
    h += (eround * 1.08 - h) * p.wide
    h *= 1 - 0.9 * p.shut
    val tilt = -p.gx * 18 * (1 - p.wide)
    fun eye(side: Int): CarvedEyeRect {
        val k = 1 - 0.1 * max(0.0, side * -p.gx)
        val kw = w * k
        val kh = max(h * k, 0.5)
        val x = cx + side * spread
        return CarvedEyeRect(
            x = r2(x - kw / 2), y = r2(cy - kh / 2), width = r2(kw), height = r2(kh),
            radius = r2(min(kw, kh) / 2), tilt = r2(tilt), pivotX = r2(x), pivotY = r2(cy),
        )
    }
    val mw = edx * 0.46
    val half = mw * (1 - 0.25 * ax)
    val depth = mw * 0.5 * p.smile
    val my = (e.mouthY?.toDouble() ?: (ecy + eh * 0.5 + edx * 0.42)) + p.gy * 10
    val gape = max(0.0, p.wide)
    return CarvedGeometry(
        lean = r2(lean),
        scaleX = r2(sx * 1000) / 1000,
        scaleY = r2(sy * 1000) / 1000,
        eyes = listOf(eye(-1), eye(1)),
        smile = CarvedSmile(
            startX = r2(cx - half), startY = r2(my - depth * 0.35),
            controlX = r2(cx), controlY = r2(my + depth),
            endX = r2(cx + half), endY = r2(my - depth * 0.35),
            tilt = r2(tilt * 0.6), pivotX = r2(cx), pivotY = r2(my),
            opacity = r2(1 - p.wide),
        ),
        open = CarvedOpenMouth(
            cx = r2(cx), cy = r2(my + mw * 0.1), rx = r2(mw * 0.5 * gape), ry = r2(mw * 0.62 * gape),
            opacity = r2(gape),
        ),
    )
}

/** What a mood asks of the face. A null field leaves the resting value. */
internal class CarvedMoodTarget(
    val gx: Double? = null,
    val gy: Double? = null,
    val wide: Double? = null,
    val shut: Double? = null,
    val smile: Double? = null,
    /** Glances per resting rate: 0 holds the gaze still. */
    val glance: Double? = null,
)

internal fun moodFor(mood: CharacterMood): CarvedMoodTarget = when (mood) {
    CharacterMood.SURPRISED, CharacterMood.ALERTING, CharacterMood.SCARED, CharacterMood.EXCITED,
    CharacterMood.CELEBRATE, CharacterMood.SPAWNING, CharacterMood.WAKING,
    -> CarvedMoodTarget(wide = 1.0)
    CharacterMood.SLEEPING, CharacterMood.POWERING_DOWN -> CarvedMoodTarget(shut = 0.92, smile = 0.4, glance = 0.0)
    CharacterMood.DROWSY, CharacterMood.BORED -> CarvedMoodTarget(shut = 0.55, smile = 0.6, glance = 0.3)
    CharacterMood.HAPPY, CharacterMood.LAUGHING, CharacterMood.PROUD, CharacterMood.PLAYFUL -> CarvedMoodTarget(smile = 1.5)
    CharacterMood.SAD -> CarvedMoodTarget(smile = -0.8, gy = 0.6, glance = 0.3)
    CharacterMood.THINKING -> CarvedMoodTarget(gx = 0.6, gy = -0.6, glance = 0.0)
    CharacterMood.SEARCHING, CharacterMood.RADAR, CharacterMood.ORBIT, CharacterMood.CURIOUS -> CarvedMoodTarget(glance = 2.2)
    CharacterMood.WORKING, CharacterMood.WRITING, CharacterMood.LOADING -> CarvedMoodTarget(gy = 0.5, glance = 0.6)
    else -> CarvedMoodTarget()
}

internal fun moodPose(mood: CharacterMood): CarvedPose {
    val m = moodFor(mood)
    return CarvedPose(gx = m.gx ?: 0.0, gy = m.gy ?: 0.0, wide = m.wide ?: 0.0, shut = m.shut ?: 0.0, smile = m.smile ?: 1.0)
}

internal const val BLINK_MS = 200.0

/** How shut the lids are [sinceMs] after a blink began: closing over 45% of the time, opening over the rest. */
internal fun blinkLid(sinceMs: Double): Double {
    val since = sinceMs / BLINK_MS
    return when {
        since >= 1 -> 0.0
        since < 0.45 -> since / 0.45
        else -> 1 - (since - 0.45) / 0.55
    }
}

/** Where a sticker lands, in the 240-unit square. */
internal class CarvedSprite(val sticker: SeiraSticker, val x: Double, val y: Double, val width: Double, val height: Double)

/** The body, the stickers it wears and the square view box that shows them. */
internal class CarvedLook(val body: SeiraBody, val sprites: List<CarvedSprite>) {
    /** The square, as left, top and side, that holds the avatar box and every sticker. */
    val viewBox: DoubleArray = spritesViewBox(sprites)

    companion object {
        private const val BOX_X = -14.0
        private const val BOX_Y = -14.0
        private const val BOX_SIDE = 268.0

        /** The body of a hair id; an unknown or retired id is Seira. */
        fun bodyOf(hair: String?): SeiraBody = SeiraBodies.all.getValue(SeiraLookCatalog.hair(hair))

        /** Known ids only, the first one in each slot, at most three. */
        fun worn(ids: List<String>): List<SeiraSticker> {
            return SeiraLookCatalog.accessories(ids).mapNotNull { SeiraBodies.stickers[it] }
        }

        fun of(hair: String?, accessories: List<String>): CarvedLook {
            val body = bodyOf(hair)
            val sprites = worn(accessories).mapNotNull { sticker ->
                val point = if (sticker.anchor == "eyes") SeiraPoint(body.eyes.cx, body.eyes.cy) else body.anchors[sticker.anchor]
                    ?: return@mapNotNull null
                val scale = sticker.width.toDouble() * body.headRadius / sticker.pictureWidth
                CarvedSprite(
                    sticker = sticker,
                    x = r2(point.x - sticker.pivotX * scale),
                    y = r2(point.y - sticker.pivotY * scale),
                    width = r2(sticker.pictureWidth * scale),
                    height = r2(sticker.pictureHeight * scale),
                )
            }
            return CarvedLook(body, sprites)
        }

        private fun spritesViewBox(sprites: List<CarvedSprite>): DoubleArray {
            if (sprites.isEmpty()) return doubleArrayOf(BOX_X, BOX_Y, BOX_SIDE)
            val centre = BOX_X + BOX_SIDE / 2
            val minX = min(BOX_X, sprites.minOf { it.x })
            val maxX = max(BOX_X + BOX_SIDE, sprites.maxOf { it.x + it.width })
            val minY = min(BOX_Y, sprites.minOf { it.y })
            val maxY = max(BOX_Y + BOX_SIDE, sprites.maxOf { it.y + it.height })
            val width = 2 * max(centre - minX, maxX - centre)
            val fit = max(width, maxY - minY)
            return doubleArrayOf(r2(centre - fit / 2), r2(minY - (fit - (maxY - minY)) / 2), r2(fit))
        }
    }
}

/**
 * The idle life of a face: glances, blinks and the ease from one mood to the next.
 * [step] takes a clock in milliseconds and says when the next frame is due.
 */
internal class CarvedAnimator(var mood: CharacterMood, private val random: Random = Random.Default) {
    var pose: CarvedPose = moodPose(mood)
        private set

    private var started = false
    private var last = 0L
    private var glanceAt = 0L
    private var blinkAt = 0L
    private var blinkBegan = -1L
    private var targetX = 0.0
    private var targetY = 0.0

    fun step(now: Long): Long {
        if (!started) {
            started = true
            last = now
            glanceAt = now + 900 + (random.nextDouble() * 2600).toLong()
            blinkAt = now + 1500 + (random.nextDouble() * 3500).toLong()
        }
        val dt = min(0.05, (now - last) / 1000.0)
        last = now
        val target = moodFor(mood)
        val rate = target.glance ?: 1.0
        if (rate > 0 && now > glanceAt) {
            val glance = GLANCES[random.nextInt(GLANCES.size)]
            targetX = glance.first
            targetY = glance.second
            glanceAt = now + ((1400 + random.nextDouble() * 3200) / rate).toLong()
        }
        if (now > blinkAt) {
            blinkBegan = now
            blinkAt = now + 2600 + (random.nextDouble() * 4200).toLong()
        }
        val wantGx = (target.gx ?: if (rate > 0) targetX else 0.0).coerceIn(-1.0, 1.0)
        val wantGy = (target.gy ?: if (rate > 0) targetY else 0.0).coerceIn(-1.0, 1.0)
        val wantWide = target.wide ?: 0.0
        val wantShut = target.shut ?: 0.0
        val wantSmile = target.smile ?: 1.0
        fun ease(speed: Double) = 1 - exp(-dt * speed)
        val p = pose
        var gx = p.gx + (wantGx - p.gx) * ease(9.0)
        var gy = p.gy + (wantGy - p.gy) * ease(9.0)
        var wide = p.wide + (wantWide - p.wide) * ease(if (wantWide > p.wide) 18.0 else 7.0)
        var smile = p.smile + (wantSmile - p.smile) * ease(8.0)
        val sinceMs = if (blinkBegan < 0) BLINK_MS else (now - blinkBegan).toDouble()
        val lid = blinkLid(sinceMs)
        var shut = p.shut + (max(wantShut, lid) - p.shut) * (if (lid > 0) 1.0 else ease(10.0))
        val still = sinceMs >= BLINK_MS &&
            settled(gx, wantGx) && settled(gy, wantGy) && settled(wide, wantWide) &&
            settled(smile, wantSmile) && settled(shut, wantShut)
        if (still) {
            gx = wantGx
            gy = wantGy
            wide = wantWide
            smile = wantSmile
            shut = wantShut
        }
        pose = CarvedPose(gx, gy, wide, shut, smile)
        return if (still) min(if (rate > 0) glanceAt else Long.MAX_VALUE, blinkAt) else now + FRAME_MS
    }

    private fun settled(a: Double, b: Double) = abs(a - b) < SETTLED

    private companion object {
        const val SETTLED = 0.001
        const val FRAME_MS = 16L
        val GLANCES: List<Pair<Double, Double>> = listOf(
            0.0 to 0.0, 0.0 to 0.0, -1.0 to 0.25, 1.0 to 0.25, -0.8 to 0.55, 0.8 to 0.55,
            -0.7 to -0.4, 0.7 to -0.4, 0.0 to 0.5,
        )
    }
}
