package com.beatmixer.dj

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class Theme(
    val name: String,
    val bg: Long,
    val panel: Long,
    val panel2: Long,
    val border: Long,
    val a: Long,
    val b: Long,
    val green: Long,
    val red: Long,
    val yellow: Long,
    val dim: Long,
    val ring: Boolean,
)

val THEMES = listOf(
    Theme("PIONEER", 0xFF05070B, 0xFF0F1319, 0xFF181D26, 0xFF2A313D, 0xFF2D9CFF, 0xFFFF2B2B, 0xFF3DDC84, 0xFFE02020, 0xFFFFB300, 0xFF8C97A8, true),
    Theme("RB PRO", 0xFF000000, 0xFF0D0D0D, 0xFF191919, 0xFF2F2F2F, 0xFFF50000, 0xFFFFFFFF, 0xFFE9E9E9, 0xFFC40000, 0xFFFF5252, 0xFF9A9A9A, false),
    Theme("CYBER", 0xFF07020F, 0xFF120A20, 0xFF1C1230, 0xFF34244F, 0xFF00E5FF, 0xFFFF2BD6, 0xFF7CFF6B, 0xFFFF3860, 0xFFFFD60A, 0xFF9A8DB8, true),
    Theme("EMERALD GOLD", 0xFF020805, 0xFF0A130E, 0xFF121F18, 0xFF22382B, 0xFF00E676, 0xFFFFC400, 0xFF69F0AE, 0xFFE53935, 0xFFFFEA00, 0xFF8FA396, true),
    Theme("SUNSET", 0xFF0B0505, 0xFF170C0C, 0xFF221414, 0xFF3D2424, 0xFFFF8A00, 0xFFFF4081, 0xFFFFD54F, 0xFFD32F2F, 0xFFFFAB40, 0xFFA8918B, true),
    Theme("ICE", 0xFF03070C, 0xFF0B1118, 0xFF141C26, 0xFF263243, 0xFF7DD3FC, 0xFFE2E8F0, 0xFF5EEAD4, 0xFFF87171, 0xFFFDE68A, 0xFF8B9BB0, true),
)

/** Live palette. Every colour is Compose state, so changing the theme repaints the whole app. */
object C {
    var Bg by mutableStateOf(Color(0xFF05070B))
    var Panel by mutableStateOf(Color(0xFF0F1319))
    var Panel2 by mutableStateOf(Color(0xFF181D26))
    var Border by mutableStateOf(Color(0xFF2A313D))
    var Orange by mutableStateOf(Color(0xFF2D9CFF))   // deck A / primary accent
    var Blue by mutableStateOf(Color(0xFFFF2B2B))     // deck B accent
    var Green by mutableStateOf(Color(0xFF3DDC84))
    var Red by mutableStateOf(Color(0xFFE02020))
    var Yellow by mutableStateOf(Color(0xFFFFB300))
    var Text by mutableStateOf(Color(0xFFFFFFFF))
    var Dim by mutableStateOf(Color(0xFF8C97A8))
    var Ring by mutableStateOf(true)

    fun apply(t: Theme) {
        Bg = Color(t.bg)
        Panel = Color(t.panel)
        Panel2 = Color(t.panel2)
        Border = Color(t.border)
        Orange = Color(t.a)
        Blue = Color(t.b)
        Green = Color(t.green)
        Red = Color(t.red)
        Yellow = Color(t.yellow)
        Dim = Color(t.dim)
        Ring = t.ring
    }
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
    val tm = rememberTextMeasurer()
    Canvas(modifier.aspectRatio(1f)) {
        clock.tick
        val r = size.minDimension / 2f
        val c = center
        val angle = ((deck.posFrames / SR * 200.0) % 360.0).toFloat()
        if (C.Ring) {
            val tr = deck.track
            val total = max(1, tr?.frames ?: 1)
            val frac = if (tr == null) 0f else (deck.posFrames / total).toFloat().coerceIn(0f, 1f)
            drawCircle(Color(0xFF07090D), r)
            // soft glow
            for (k in 4 downTo 1) {
                drawCircle(accent.copy(alpha = 0.07f * (5 - k)), r - 3.dp.toPx(), style = Stroke((3 + k * 3).dp.toPx()))
            }
            drawCircle(accent.copy(alpha = 0.35f), r - 3.dp.toPx(), style = Stroke(2.dp.toPx()))
            val arcInset = 3.dp.toPx()
            drawArc(
                accent,
                -90f,
                360f * frac,
                false,
                topLeft = Offset(arcInset, arcInset),
                size = Size(size.width - arcInset * 2, size.height - arcInset * 2),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(Color(0xFF161B22), r * 0.80f, style = Stroke(1.dp.toPx()))
            rotate(angle, c) {
                drawLine(accent, Offset(c.x, c.y - r * 0.78f), Offset(c.x, c.y - r * 0.62f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            val bpm = tr?.bpm ?: 0f
            val rem = if (tr == null) 0f else ((total - deck.posFrames) / SR).toFloat().coerceAtLeast(0f)
            val l1 = tm.measure(if (bpm > 0f) "%.1f".format(bpm) else "---", TextStyle(color = accent, fontSize = (r * 0.30f / density).sp, fontWeight = FontWeight.Bold))
            val l2 = tm.measure("-%d:%02d".format(rem.toInt() / 60, rem.toInt() % 60), TextStyle(color = Color(0xFFDDDDDD), fontSize = (r * 0.20f / density).sp))
            drawText(l1, topLeft = Offset(c.x - l1.size.width / 2f, c.y - l1.size.height - 1.dp.toPx()))
            drawText(l2, topLeft = Offset(c.x - l2.size.width / 2f, c.y + 1.dp.toPx()))
            return@Canvas
        }
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
    val reveal = remember(track) { floatArrayOf(0f) }
    val tmp = remember(track) { FloatArray(track?.peaks?.size ?: 1) }
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
        val total = max(1, track.frames)
        val ready = if (track.complete) total else min(total, track.readyFrames)
        val target = if (track.complete) 1f else (ready.toFloat() / total).coerceIn(0f, 1f)
        // smooth reveal: glide toward the decoded fraction, never jump
        val cur = reveal[0]
        reveal[0] = min(target, cur + max((target - cur) * 0.10f, 0.006f))
        val shown = reveal[0] * n
        val per = max(1, total / n)
        var mx = 0f
        if (track.complete) {
            for (i in 0 until n) tmp[i] = pk[i]
            mx = 1f
        } else {
            for (i in 0 until n) {
                val s = i * per
                if (s >= ready) {
                    tmp[i] = 0f
                    continue
                }
                val e = min(ready, s + per)
                val stride = max(1, (e - s) / 48)
                var m = 0
                var k = s
                while (k < e) {
                    val q = abs(track.pcm[k * 2].toInt())
                    if (q > m) m = q
                    k += stride
                }
                tmp[i] = m / 32768f
                if (tmp[i] > mx) mx = tmp[i]
            }
            if (mx < 0.05f) mx = 0.05f
        }
        val frac = (deck.posFrames / total).toFloat().coerceIn(0f, 1f)
        val bw = size.width / n
        for (i in 0 until n) {
            val grow = ((shown - i) / 10f).coerceIn(0f, 1f)
            if (grow <= 0f) break
            val ease = grow * grow * (3f - 2f * grow)
            val h = max(2f, (tmp[i] / mx).coerceIn(0f, 1f) * size.height * 0.95f * ease)
            val x = i * bw + bw / 2f
            val played = i / n.toFloat() < frac
            val col = (if (played) accent.copy(alpha = 0.35f) else accent.copy(alpha = 0.95f)).copy(alpha = (if (played) 0.35f else 0.95f) * (0.3f + 0.7f * ease))
            drawLine(col, Offset(x, (size.height - h) / 2f), Offset(x, (size.height + h) / 2f), strokeWidth = max(1f, bw))
        }
        if (reveal[0] < 0.999f) {
            val ex = (shown * bw).coerceIn(0f, size.width)
            drawLine(accent.copy(alpha = 0.9f), Offset(ex, 0f), Offset(ex, size.height), strokeWidth = 2.dp.toPx())
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

/** A value that gently pulses between 0.35 and 1 while it is composed. */
@Composable
fun pulseValue(): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "p",
    )
    return v
}

/** Deck letter badge: glows and pulses while that deck is playing. */
@Composable
fun DeckBadge(letter: String, accent: Color, playing: Boolean, boxSize: Dp) {
    val p = if (playing) pulseValue() else 1f
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(boxSize)
            .clip(shape)
            .background(if (playing) accent.copy(alpha = 0.4f + 0.6f * p) else Color.Transparent)
            .border(1.5.dp, accent, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            color = if (playing) Color.Black else accent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
        )
    }
}
