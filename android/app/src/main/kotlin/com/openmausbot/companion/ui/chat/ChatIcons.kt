package com.openmausbot.companion.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun chatIcon(name: String, vararg paths: String): ImageVector =
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
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** Stroke icons the chat needs and the shared set does not carry. */
internal object ChatIcons {
    val Copy = chatIcon(
        "Copy",
        "M9 9h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2Z",
        "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1",
    )
    val Reply = chatIcon("Reply", "M9 14L4 9l5-5", "M4 9h10a6 6 0 0 1 6 6v3")
    val ChevronUp = chatIcon("ChevronUp", "M5 15l7-7 7 7")
    val Share = chatIcon(
        "Share",
        "M12 3v12",
        "M7 8l5-5 5 5",
        "M5 13v5a3 3 0 0 0 3 3h8a3 3 0 0 0 3-3v-5",
    )
    val Code = chatIcon("Code", "M8 7l-5 5 5 5", "M16 7l5 5-5 5", "M14 4l-4 16")
    val Layers = chatIcon(
        "Layers",
        "M12 3l9 5-9 5-9-5Z",
        "M3 13l9 5 9-5",
    )
    val TextCursor = chatIcon("TextCursor", "M9 4h6", "M9 20h6", "M12 4v16")
}
