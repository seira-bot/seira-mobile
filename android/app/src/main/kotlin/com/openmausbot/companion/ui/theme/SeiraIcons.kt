package com.openmausbot.companion.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val STROKE_WIDTH = 1.85f

private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        for (path in paths) {
            addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private const val PHONE =
    "M5 4h3l2 5-2.5 1.5a11 11 0 0 0 6 6L15 14l5 2v3a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2Z"
private const val MIC =
    "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3Z M5 11a7 7 0 0 0 14 0 M12 18v3"
private const val FLASHLIGHT =
    "M8 3h8v4l-2 2.5V20a1.5 1.5 0 0 1-1.5 1.5h-1A1.5 1.5 0 0 1 10 20V9.5L8 7Z M12 13v2"
private const val SLASH = "M3 3l18 18"

/** Stroke-only 24 dp icons. Tint them with `Icon(tint = ...)`. */
object SeiraIcons {
    val AudioLines = strokeIcon("AudioLines", "M3 10v4 M7 7v10 M12 4v16 M17 8v8 M21 11v2")
    val PenSquare = strokeIcon(
        "PenSquare",
        "M12 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-6",
        "M17.5 3.5a2.1 2.1 0 0 1 3 3L12 15l-4 1 1-4Z",
    )
    val Search = strokeIcon("Search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14Z", "M20 20l-4.2-4.2")
    val Plus = strokeIcon("Plus", "M12 5v14 M5 12h14")
    val X = strokeIcon("X", "M6 6l12 12 M18 6L6 18")
    val ChevronLeft = strokeIcon("ChevronLeft", "M15 5l-7 7 7 7")
    val ChevronRight = strokeIcon("ChevronRight", "M9 5l7 7-7 7")
    val ChevronDown = strokeIcon("ChevronDown", "M5 9l7 7 7-7")
    val Phone = strokeIcon("Phone", PHONE)
    val PhoneOff = strokeIcon("PhoneOff", PHONE, SLASH)
    val Mic = strokeIcon("Mic", MIC)
    val MicOff = strokeIcon("MicOff", MIC, SLASH)
    val Volume2 = strokeIcon(
        "Volume2",
        "M4 9v6h4l5 4V5L8 9Z",
        "M16.5 8.5a5 5 0 0 1 0 7 M19 6a8.5 8.5 0 0 1 0 12",
    )
    val Captions = strokeIcon(
        "Captions",
        "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z",
        "M10.5 10a2 2 0 1 0 0 4 M17 10a2 2 0 1 0 0 4",
    )
    val Camera = strokeIcon(
        "Camera",
        "M4 8h3l1.5-2h7L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1Z",
        "M12 10.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 1 0 0-7Z",
    )
    val Image = strokeIcon(
        "Image",
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z",
        "M8.5 7.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 1 0 0-3Z",
        "M21 15l-5-5L5 21",
    )
    val File = strokeIcon("File", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z", "M14 3v5h5")
    val CalendarClock = strokeIcon(
        "CalendarClock",
        "M8 3v3 M16 3v3 M4 9h16 M20 11V7a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h5",
        "M17 14a4 4 0 1 0 0 8 4 4 0 1 0 0-8Z",
        "M17 16v2l1.2 1",
    )
    val Monitor = strokeIcon(
        "Monitor",
        "M4 4h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z",
        "M8 20h8 M12 16v4",
    )
    val Flashlight = strokeIcon("Flashlight", FLASHLIGHT)
    val FlashlightOff = strokeIcon("FlashlightOff", FLASHLIGHT, SLASH)
    val Check = strokeIcon("Check", "M5 12.5l4.5 4.5L19 7.5")
    val Sun = strokeIcon(
        "Sun",
        "M12 8a4 4 0 1 0 0 8 4 4 0 1 0 0-8Z",
        "M12 2.5v2 M12 19.5v2 M2.5 12h2 M19.5 12h2 M5.3 5.3l1.4 1.4 M17.3 17.3l1.4 1.4 M5.3 18.7l1.4-1.4 M17.3 6.7l1.4-1.4",
    )
    val Moon = strokeIcon("Moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5Z")
    val Smartphone = strokeIcon(
        "Smartphone",
        "M8 2.5h8a2 2 0 0 1 2 2v15a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2v-15a2 2 0 0 1 2-2Z",
        "M11 18.5h2",
    )
    val User = strokeIcon("User", "M12 4a4 4 0 1 0 0 8 4 4 0 1 0 0-8Z", "M4.5 20.5a7.5 7.5 0 0 1 15 0")
    val LogOut = strokeIcon("LogOut", "M9 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h3", "M16 8l4 4-4 4 M20 12H9")
    val Bell = strokeIcon("Bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15Z", "M10 21h4")
    val Shield = strokeIcon("Shield", "M12 3l7.5 3v5.5c0 4.5-3 7.8-7.5 9.5-4.5-1.7-7.5-5-7.5-9.5V6Z")
    val Download = strokeIcon("Download", "M12 4v11 M7 10.5l5 5 5-5 M5 20h14")
    val Laptop = strokeIcon(
        "Laptop",
        "M5 5h14a1 1 0 0 1 1 1v9H4V6a1 1 0 0 1 1-1Z",
        "M2.5 19h19",
    )

    val all: List<ImageVector> by lazy {
        listOf(
            AudioLines, PenSquare, Search, Plus, X, ChevronLeft, ChevronRight, ChevronDown,
            Phone, PhoneOff, Mic, MicOff, Volume2, Captions, Camera, Image, File, CalendarClock,
            Monitor, Flashlight, FlashlightOff, Check, Sun, Moon, Smartphone, User, LogOut,
            Bell, Shield, Download, Laptop,
        )
    }
}
