package com.beatmixer.dj

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object C {
    // RB Pro Audio theme: black, red and white
    val Bg = Color(0xFF000000)
    val Panel = Color(0xFF0D0D0D)
    val Panel2 = Color(0xFF191919)
    val Border = Color(0xFF2F2F2F)
    val Orange = Color(0xFFF50000)   // deck A / primary accent (logo red)
    val Blue = Color(0xFFFFFFFF)     // deck B accent (logo white)
    val Green = Color(0xFFE9E9E9)
    val Red = Color(0xFFC40000)
    val Yellow = Color(0xFFFF5252)
    val Text = Color(0xFFFFFFFF)
    val Dim = Color(0xFF9A9A9A)
}

@Composable
fun BeatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Orange,
            background = C.Bg,
            surface = C.Panel,
            onSurface = C.Text,
            onBackground = C.Text,
        ),
        content = content,
    )
}

/** Ticks ~30 times per second; read it inside a Canvas draw block to animate without recomposing. */
class UiClock {
    var tick by mutableLongStateOf(0L)
}

@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = C.Text,
    size: TextUnit = 13.sp,
    weight: FontWeight = FontWeight.Bold,
    maxLines: Int = 1,
) {
    Text(text, modifier = modifier, color = color, fontSize = size, fontWeight = weight, maxLines = maxLines)
}

@Composable
fun PadButton(
    modifier: Modifier = Modifier,
    accent: Color = C.Orange,
    active: Boolean = false,
    activeFill: Color = accent,
    fill: Color = C.Panel2,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val longClick by rememberUpdatedState(onLongClick)
    val click by rememberUpdatedState(onClick)
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (active) activeFill else fill)
            .border(1.dp, accent.copy(alpha = 0.75f), shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        press?.invoke()
                        tryAwaitRelease()
                        release?.invoke()
                    },
                    onLongPress = { longClick?.invoke() },
                    onTap = { click?.invoke() },
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun Knob(value: Float, onChange: (Float) -> Unit, accent: Color, modifier: Modifier = Modifier) {
    val cur by rememberUpdatedState(onChange)
    val v by rememberUpdatedState(value)
    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { cur(0f) }) }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dy ->
                    change.consume()
                    cur((v - dy / 160f).coerceIn(-1f, 1f))
                }
            },
    ) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(C.Panel2, r)
        drawCircle(if (abs(value) > 0.02f) accent else C.Border, r, style = Stroke(2.dp.toPx()))
        val ang = Math.toRadians(value * 135.0 - 90.0)
        val ca = cos(ang).toFloat()
        val sa = sin(ang).toFloat()
        drawLine(
            Color.White,
            Offset(cx + ca * r * 0.30f, cy + sa * r * 0.30f),
            Offset(cx + ca * r * 0.82f, cy + sa * r * 0.82f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun FlatSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    accent: Color = C.Orange,
    fromCenter: Boolean = false,
    onReset: (() -> Unit)? = null,
) {
    val cur by rememberUpdatedState(onValueChange)
    val reset by rememberUpdatedState(onReset)
    val span = range.endInclusive - range.start
    Canvas(
        modifier
            .height(28.dp)
            .pointerInput(range) {
                val tw = 10.dp.toPx()
                detectTapGestures(
                    onDoubleTap = { reset?.invoke() },
                    onTap = { o ->
                        val f = ((o.x - tw / 2f) / (size.width - tw)).coerceIn(0f, 1f)
                        cur(range.start + f * span)
                    },
                )
            }
            .pointerInput(range) {
                val tw = 10.dp.toPx()
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val f = ((change.position.x - tw / 2f) / (size.width - tw)).coerceIn(0f, 1f)
                    cur(range.start + f * span)
                }
            },
    ) {
        val f = ((value - range.start) / span).coerceIn(0f, 1f)
        val cy = size.height / 2f
        val th = 4.dp.toPx()
        val tw = 10.dp.toPx()
        val tx = tw / 2f + f * (size.width - tw)
        drawRect(C.Border, Offset(0f, cy - th / 2f), Size(size.width, th))
        val x0 = if (fromCenter) size.width / 2f else 0f
        drawRect(accent.copy(alpha = 0.55f), Offset(min(x0, tx), cy - th / 2f), Size(abs(tx - x0), th))
        if (fromCenter) {
            drawRect(C.Dim, Offset(size.width / 2f - 1f, cy - 7.dp.toPx()), Size(2f, 14.dp.toPx()))
        }
        drawRoundRect(
            accent,
            Offset(tx - tw / 2f, cy - 12.dp.toPx()),
            Size(tw, 24.dp.toPx()),
            androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        )
    }
}

@Composable
fun Vinyl(deck: Deck, accent: Color, clock: UiClock, modifier: Modifier = Modifier) {
    val logo = ImageBitmap.imageResource(R.drawable.rb_logo)
    Canvas(modifier.aspectRatio(1f)) {
        clock.tick
        val r = size.minDimension / 2f
        val c = center
        val angle = ((deck.posFrames / SR * 200.0) % 360.0).toFloat()
        drawCircle(Color(0xFF0A0A0A), r)
        drawCircle(accent, r - 2.dp.toPx(), style = Stroke(3.dp.toPx()))
        drawCircle(Color(0xFF222222), r * 0.86f, style = Stroke(1.dp.toPx()))
        drawCircle(Color(0xFF222222), r * 0.72f, style = Stroke(1.dp.toPx()))
        val lr = r * 0.60f
        rotate(angle, c) {
            drawCircle(Color.Black, lr)
            drawCircle(accent, lr, style = Stroke(2.dp.toPx()))
            val lw = lr * 2f * 0.86f
            val lh = lw * logo.height / logo.width
            drawImage(
                logo,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(logo.width, logo.height),
                dstOffset = IntOffset((c.x - lw / 2f).toInt(), (c.y - lh / 2f).toInt()),
                dstSize = IntSize(lw.toInt(), lh.toInt()),
                filterQuality = FilterQuality.Medium,
            )
            drawLine(accent, Offset(c.x, c.y - r * 0.93f), Offset(c.x, c.y - r * 0.76f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
fun OverviewWave(
    deck: Deck,
    track: Track?,
    accent: Color,
    clock: UiClock,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seek by rememberUpdatedState(onSeek)
    Canvas(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(C.Panel2)
            .pointerInput(Unit) { detectTapGestures { o -> seek(o.x / size.width) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    seek(change.position.x / size.width)
                }
            },
    ) {
        clock.tick
        if (track == null) return@Canvas
        val pk = track.peaks
        val n = pk.size
        val frac = (deck.posFrames / max(1, track.frames)).toFloat().coerceIn(0f, 1f)
        val bw = size.width / n
        for (i in 0 until n) {
            val h = max(2f, pk[i] * size.height * 0.95f)
            val x = i * bw + bw / 2f
            val col = if (i / n.toFloat() < frac) accent.copy(alpha = 0.35f) else accent.copy(alpha = 0.95f)
            drawLine(col, Offset(x, (size.height - h) / 2f), Offset(x, (size.height + h) / 2f), strokeWidth = max(1f, bw))
        }
        val px = frac * size.width
        drawLine(Color.White, Offset(px, 0f), Offset(px, size.height), strokeWidth = 2.dp.toPx())
    }
}

@Composable
fun ScrollWave(deck: Deck, track: Track?, accent: Color, clock: UiClock, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(6.dp)).background(C.Panel2)) {
        clock.tick
        if (track == null) return@Canvas
        val pcm = track.pcm
        val total = track.frames
        val pos = deck.posFrames
        val colPx = 3.dp.toPx()
        val cols = max(1, (size.width / colPx).toInt())
        val headX = size.width * 0.4f
        val spanFrames = 8.0 * SR
        for (cIdx in 0 until cols) {
            val x = cIdx * colPx
            val startF = pos + ((x - headX) / size.width) * spanFrames
            val endF = startF + (colPx / size.width) * spanFrames
            val s = startF.toInt()
            val e = endF.toInt()
            if (e < 0 || s >= total) continue
            val s0 = max(0, s)
            val e0 = min(total - 1, e)
            val stride = max(1, (e0 - s0) / 24)
            var m = 0
            var i = s0
            while (i <= e0) {
                val v = abs(pcm[i * 2].toInt())
                if (v > m) m = v
                i += stride
            }
            val h = max(2f, min(1f, m / 32768f * 1.3f) * size.height * 0.95f)
            val col = if (x < headX) accent.copy(alpha = 0.40f) else accent.copy(alpha = 0.95f)
            drawLine(
                col,
                Offset(x + colPx / 2f, (size.height - h) / 2f),
                Offset(x + colPx / 2f, (size.height + h) / 2f),
                strokeWidth = colPx - 1f,
            )
        }
        drawLine(C.Red, Offset(headX, 0f), Offset(headX, size.height), strokeWidth = 2.dp.toPx())
    }
}

@Composable
fun VuMeter(engine: MixEngine, clock: UiClock, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        clock.tick
        val segs = 10
        val gap = 2.dp.toPx()
        val sh = (size.height - gap * (segs - 1)) / segs
        val bw = (size.width - 3.dp.toPx()) / 2f
        for (ch in 0..1) {
            val lvl = if (ch == 0) engine.peakL else engine.peakR
            for (s in 0 until segs) {
                val lit = lvl >= (s + 1) / segs.toFloat() * 0.95f
                val base = when {
                    s >= 9 -> C.Red
                    s >= 7 -> C.Yellow
                    else -> C.Green
                }
                val y = size.height - (s + 1) * sh - s * gap
                drawRect(
                    if (lit) base else base.copy(alpha = 0.18f),
                    Offset(ch * (bw + 3.dp.toPx()), y),
                    Size(bw, sh),
                )
            }
        }
    }
}

@Composable
fun LogoMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.rb_logo),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
