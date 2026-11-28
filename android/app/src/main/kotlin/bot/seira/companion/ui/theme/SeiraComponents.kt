package bot.seira.companion.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.seira.companion.core.Bot
import bot.seira.companion.ui.BotAvatar
import bot.seira.companion.ui.CharacterMood

private val TouchTarget: Dp = 48.dp
private val ShadowElevation: Dp = 10.dp

/**
 * The floating shadow: a soft key shadow and a hairline ring drawn over whatever
 * the modifier chain paints inside. Spot and ambient colours apply from API 28.
 */
@Composable
fun Modifier.seiraShadow(shape: Shape, elevation: Dp = ShadowElevation): Modifier {
    val colors = Seira.colors
    val ring = colors.shadowRing
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = colors.shadowKey.copy(alpha = colors.shadowKey.alpha * 0.4f),
            spotColor = colors.shadowKey,
        )
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val stroke = Stroke(width = 0.5.dp.toPx())
            onDrawWithContent {
                drawContent()
                strokeOutline(outline, ring, stroke)
            }
        }
}

private fun DrawScope.strokeOutline(outline: Outline, color: Color, stroke: Stroke) {
    when (outline) {
        is Outline.Rectangle -> drawRect(color, outline.rect.topLeft, outline.rect.size, style = stroke)
        is Outline.Rounded -> {
            val box = outline.roundRect
            if (box.topLeftCornerRadius.x == box.topLeftCornerRadius.y) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(box.left, box.top),
                    size = androidx.compose.ui.geometry.Size(box.width, box.height),
                    cornerRadius = CornerRadius(box.topLeftCornerRadius.x, box.topLeftCornerRadius.y),
                    style = stroke,
                )
            } else {
                drawPath(Path().apply { addRoundRect(box) }, color, style = stroke)
            }
        }
        is Outline.Generic -> drawPath(outline.path, color, style = stroke)
    }
}

/** Translucent chrome. No blur: `RenderEffect` blur starts at API 31 and the app starts at 26. */
@Composable
fun Modifier.glass(shape: Shape, elevation: Dp = ShadowElevation): Modifier =
    this.seiraShadow(shape, elevation).background(Seira.colors.glass, shape)

@Composable
fun InkPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 48.dp,
    enabled: Boolean = true,
) {
    val colors = Seira.colors
    Box(
        modifier = modifier.heightIn(min = TouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = height)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
            }
            Text(text, style = SeiraType.headline, color = colors.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ControlCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = Seira.colors
    val side = if (size > TouchTarget) size else TouchTarget
    Box(
        modifier = modifier
            .size(side)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(if (selected) colors.primary else colors.control, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (selected) colors.onPrimary else colors.ink,
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(TouchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(44.dp).glass(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = Seira.colors.ink, modifier = Modifier.size(20.dp))
        }
    }
}

enum class AvatarRing { None, Working }

/**
 * A bot's face on its identity plate. The working ring and the waiting dot are
 * drawn outside [size] (up to 4.5 dp), so leave room around the avatar.
 */
@Composable
internal fun IdentityAvatar(
    bot: Bot,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: AvatarRing = AvatarRing.None,
    waitingDot: Boolean = false,
    state: CharacterMood = CharacterMood.IDLE,
    animated: Boolean = true,
    sway: Boolean = false,
    contentDescription: String? = null,
) {
    val colors = Seira.colors
    val plate = identityPlate(bot.color, colors.isDark)
    val ringColor = colors.success
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val gap = 2.5.dp.toPx()
                val outer = 4.5.dp.toPx()
                val width = outer - gap
                onDrawBehind {
                    if (ring == AvatarRing.Working) {
                        drawCircle(
                            color = ringColor,
                            radius = this.size.minDimension / 2f + gap + width / 2f,
                            style = Stroke(width = width),
                        )
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clip(CircleShape).background(plate, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BotAvatar(
                bot = bot,
                size = size,
                state = state,
                animated = animated,
                sway = sway,
                contentDescription = contentDescription,
            )
        }
        if (waitingDot) {
            val dot = if (size * 0.2f > 10.dp) size * 0.2f else 10.dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(y = (-1).dp)
                    .size(dot + 5.dp)
                    .background(colors.app, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(dot).background(colors.warning, CircleShape))
            }
        }
    }
}

@Composable
fun SeiraSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val track by animateColorAsState(
        if (checked) colors.primary else colors.switchTrackOff,
        seiraTween(SeiraMotion.BASE, reduced),
        label = "switchTrack",
    )
    val knob by animateColorAsState(
        if (checked) colors.switchKnobOn else colors.switchKnobOff,
        seiraTween(SeiraMotion.BASE, reduced),
        label = "switchKnob",
    )
    val travel by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        seiraTween(SeiraMotion.BASE + 40, reduced),
        label = "switchTravel",
    )
    Box(
        modifier = modifier
            .size(width = 51.dp, height = TouchTarget)
            .then(
                if (onCheckedChange == null) {
                    Modifier
                } else {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(width = 51.dp, height = 31.dp)
                .background(track.copy(alpha = track.alpha * if (enabled) 1f else 0.5f), CircleShape),
        )
        Box(
            modifier = Modifier
                .offset(x = 2.dp + travel)
                .size(27.dp)
                .shadow(1.5.dp, CircleShape, clip = false)
                .background(knob, CircleShape),
        )
    }
}

@Composable
fun SegmentedControl(
    options: List<Pair<String, ImageVector?>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val position by animateFloatAsState(
        selectedIndex.toFloat(),
        seiraTween(SeiraMotion.SHEET, reduced),
        label = "segmentThumb",
    )
    val inset = 4.dp
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.seg, RoundedCornerShape(16.dp))
            .selectableGroup(),
    ) {
        Box(Modifier.matchParentSize().padding(inset)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(1f / options.size)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = size.width * position * direction }
                    .shadow(1.dp, RoundedCornerShape(12.dp), clip = false)
                    .background(colors.thumb, RoundedCornerShape(12.dp)),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = inset)) {
            options.forEachIndexed { index, (label, icon) ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(
                    if (selected) colors.ink else colors.ink2,
                    seiraTween(SeiraMotion.BASE, reduced),
                    label = "segmentInk",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = TouchTarget)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (icon != null) {
                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            label,
                            style = SeiraType.bodyMedium.copy(fontSize = 14.sp),
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(min = 0.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SeiraCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.clip(shape).background(Seira.colors.tint, shape),
        content = content,
    )
}

/** The app colour stays solid down to [solidUntil] of the height, then fades out, as in the design. */
@Composable
fun TopFade(
    height: Dp,
    modifier: Modifier = Modifier,
    color: Color = Seira.colors.app,
    solidUntil: Float = 0.4f,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to color,
                    solidUntil to color,
                    1f to color.copy(alpha = 0f),
                ),
            ),
    )
}

/** The app colour fades in and is solid from [solidFrom] of the height, so text under the floating bar stays hidden. */
@Composable
fun BottomFade(
    height: Dp,
    modifier: Modifier = Modifier,
    color: Color = Seira.colors.app,
    solidFrom: Float = 0.55f,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0f),
                    solidFrom to color,
                    1f to color,
                ),
            ),
    )
}

private const val MARK_VIEWPORT = 178f
private val MarkEyes = Color(0xFFE11D48)

private object MarkPaths {
    private fun parse(data: String, evenOdd: Boolean = false): Path =
        PathParser().parsePathString(data).toPath().apply {
            if (evenOdd) fillType = PathFillType.EvenOdd
        }

    val body by lazy {
        parse("M0 178V89A89 89 0 0 1 178 89V178Z M34 55H144V89A55 55 0 0 1 34 89Z", evenOdd = true)
    }
    val eyes by lazy {
        listOf(
            parse("M55 86.5A10.5 10.5 0 0 1 76 86.5V99.5A10.5 10.5 0 0 1 55 99.5Z M63 85A4 4 0 1 0 71 85A4 4 0 1 0 63 85Z", evenOdd = true),
            parse("M60 79L55 89L47 76Z"),
            parse("M102 86.5A10.5 10.5 0 0 1 123 86.5V99.5A10.5 10.5 0 0 1 102 99.5Z M107 85A4 4 0 1 0 115 85A4 4 0 1 0 107 85Z", evenOdd = true),
            parse("M118 79L131 76L123 89Z"),
        )
    }
}

@Composable
fun SeiraMark(
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = Seira.colors.ink,
    contentDescription: String? = null,
) {
    val described = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    }
    Canvas(described.size(size)) {
        scale(scale = this.size.minDimension / MARK_VIEWPORT, pivot = Offset.Zero) {
            drawPath(MarkPaths.body, color)
            MarkPaths.eyes.forEach { drawPath(it, MarkEyes) }
        }
    }
}
