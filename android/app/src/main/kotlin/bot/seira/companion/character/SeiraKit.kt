package bot.seira.companion.character

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val KIT_ROOT = "seira-character"
private const val ACCESSORY_ROOT = "seira-mascot/accessory"
private const val CACHE_BYTES = 48 * 1024 * 1024

internal const val SEIRA_DEFAULT_HAIR = "seira"

internal class SeiraViewSpec(
    val name: String,
    val file: String,
    val pivotX: Float,
    val pivotY: Float,
    val radius: Float,
    val yaw: Float,
    val anchors: Map<String, Pair<Float, Float>>,
    val hairFile: String?,
    val visorFile: String?,
)

internal class SeiraStyleSpec(val id: String, val recolor: Boolean, val views: Map<String, SeiraViewSpec>)

internal class SeiraAccessorySpec(
    val id: String,
    val slot: String,
    val file: String,
    val pivotX: Float,
    val pivotY: Float,
    val anchor: String,
    val width: Float,
    val views: List<String>,
)

internal class SeiraKitData(
    val styles: Map<String, SeiraStyleSpec>,
    val accessories: Map<String, SeiraAccessorySpec>,
) {
    companion object {
        fun parse(kitJson: String, placementJson: String): SeiraKitData {
            val json = Json.parseToJsonElement(kitJson).jsonObject["styles"]!!.jsonObject
            val styles = json.mapValues { (id, style) ->
                val obj = style.jsonObject
                val views = obj["views"]!!.jsonObject.mapValues { (name, view) -> parseView(name, view.jsonObject) }
                SeiraStyleSpec(id, obj["recolor"]?.jsonPrimitive?.booleanOrNull ?: false, views)
            }
            val placement = Json.parseToJsonElement(placementJson).jsonObject["accessories"]!!.jsonObject
            val accessories = placement.mapValues { (id, value) ->
                val obj = value.jsonObject
                val pivot = obj["pivot"]!!.jsonArray
                SeiraAccessorySpec(
                    id = id,
                    slot = obj["slot"]?.jsonPrimitive?.contentOrNull ?: id,
                    file = obj["file"]!!.jsonPrimitive.content,
                    pivotX = pivot[0].jsonPrimitive.float,
                    pivotY = pivot[1].jsonPrimitive.float,
                    anchor = obj["anchor"]?.jsonPrimitive?.contentOrNull ?: "top",
                    width = obj["width"]?.jsonPrimitive?.floatOrNull ?: 1f,
                    views = (obj["views"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: listOf("front"),
                )
            }
            return SeiraKitData(styles, accessories)
        }

        private fun parseView(name: String, obj: JsonObject): SeiraViewSpec {
            val pivot = obj["pivot"]!!.jsonArray
            val anchors = (obj["anchors"] as? JsonObject).orEmpty().mapValues { (_, point) ->
                val a = point.jsonArray
                a[0].jsonPrimitive.float to a[1].jsonPrimitive.float
            }
            return SeiraViewSpec(
                name = name,
                file = obj["file"]!!.jsonPrimitive.content,
                pivotX = pivot[0].jsonPrimitive.float,
                pivotY = pivot[1].jsonPrimitive.float,
                radius = obj["r"]!!.jsonPrimitive.float,
                yaw = obj["measuredYaw"]?.jsonPrimitive?.floatOrNull ?: 0f,
                anchors = anchors,
                hairFile = obj["hair"]?.jsonPrimitive?.contentOrNull,
                visorFile = obj["visor"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }
}

/** A decoded picture. [maskBox] is set for a visor mask: x, y, width and height of the part above half. */
internal class SeiraImage(val bitmap: Bitmap, val maskBox: IntArray? = null)

/** Reads the kit from the app assets and keeps the decoded pictures in a byte-limited cache. */
internal class SeiraAssets(private val assets: AssetManager) {
    val kit: SeiraKitData = SeiraKitData.parse(readText("$KIT_ROOT/kit.json"), readText("$KIT_ROOT/placement.json"))

    private val cache = object : LruCache<String, SeiraImage>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: SeiraImage) = value.bitmap.allocationByteCount
    }

    fun styleFor(hair: String?): SeiraStyleSpec = kit.styles[hair ?: SEIRA_DEFAULT_HAIR] ?: kit.styles.getValue(SEIRA_DEFAULT_HAIR)

    fun castJson(): String = readText("$KIT_ROOT/cast.json")

    fun accessory(id: String): SeiraAccessorySpec? = kit.accessories[id]

    fun accessoryImage(spec: SeiraAccessorySpec): SeiraImage? = cached("acc:${spec.file}") {
        decode("$ACCESSORY_ROOT/${spec.file}")
    }

    fun frame(view: SeiraViewSpec): SeiraImage? = cached("frame:${view.file}") { decode("$KIT_ROOT/${view.file}") }

    /** The base frame with its hair recoloured to [tint], or the plain frame when [tint] is null. */
    fun frame(style: SeiraStyleSpec, view: SeiraViewSpec, tint: Int?): SeiraImage? {
        val base = frame(view) ?: return null
        val hairFile = view.hairFile
        if (tint == null || hairFile == null) return base
        return cached("hair:${style.id}:${view.name}:${tint.toUInt().toString(16)}") {
            val mask = decode("$KIT_ROOT/$hairFile") ?: return@cached base
            val pixels = IntArray(base.bitmap.width * base.bitmap.height)
            base.bitmap.getPixels(pixels, 0, base.bitmap.width, 0, 0, base.bitmap.width, base.bitmap.height)
            val maskPixels = IntArray(mask.bitmap.width * mask.bitmap.height)
            mask.bitmap.getPixels(maskPixels, 0, mask.bitmap.width, 0, 0, mask.bitmap.width, mask.bitmap.height)
            SeiraRecolor.recolor(pixels, maskPixels, tint)
            SeiraImage(Bitmap.createBitmap(pixels, base.bitmap.width, base.bitmap.height, Bitmap.Config.ARGB_8888))
        }
    }

    /** The visor mask as white pixels whose alpha is the mask value. */
    fun visor(view: SeiraViewSpec): SeiraImage? {
        val file = view.visorFile ?: return null
        return cached("visor:$file") {
            val raw = decode("$KIT_ROOT/$file") ?: return@cached null
            val w = raw.bitmap.width
            val h = raw.bitmap.height
            val pixels = IntArray(w * h)
            raw.bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            var x0 = w
            var y0 = h
            var x1 = -1
            var y1 = -1
            for (i in pixels.indices) {
                val p = pixels[i]
                val a = p ushr 24
                val lum = Math.round((((p shr 16) and 255) + ((p shr 8) and 255) + (p and 255)) / 3f * (a / 255f))
                pixels[i] = (lum shl 24) or 0xFFFFFF
                if (lum > 127) {
                    val px = i % w
                    val py = i / w
                    if (px < x0) x0 = px
                    if (px > x1) x1 = px
                    if (py < y0) y0 = py
                    if (py > y1) y1 = py
                }
            }
            val box = if (x1 >= 0) intArrayOf(x0, y0, x1 - x0 + 1, y1 - y0 + 1) else null
            SeiraImage(Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888), box)
        }
    }

    /** Decodes every picture that a look needs, so the first drawn frame does not wait for the disk. */
    fun preload(hair: String?, tint: Int?, accessories: List<String>) {
        val style = styleFor(hair)
        for (view in style.views.values) {
            frame(style, view, tint)
            visor(view)
        }
        for (id in accessories) accessory(id)?.let(::accessoryImage)
    }

    private fun cached(key: String, load: () -> SeiraImage?): SeiraImage? {
        synchronized(cache) { cache.get(key) }?.let { return it }
        val value = load() ?: return null
        synchronized(cache) { cache.put(key, value) }
        return value
    }

    private fun decode(path: String): SeiraImage? = runCatching {
        assets.open(path).use { stream ->
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, options)?.let { SeiraImage(it) }
        }
    }.getOrNull()

    private fun readText(path: String): String = assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }

    companion object {
        @Volatile
        private var shared: SeiraAssets? = null

        fun forContext(context: Context): SeiraAssets {
            val manager = context.applicationContext.assets
            shared?.takeIf { it.assets === manager }?.let { return it }
            return synchronized(this) {
                shared?.takeIf { it.assets === manager } ?: SeiraAssets(manager).also { shared = it }
            }
        }
    }
}
