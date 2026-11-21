package com.beatmixer.dj

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Internal sample rate of the whole mixer. */
const val SR = 44100

/**
 * A track decoded into interleaved stereo 16-bit PCM at [SR]. Decoding continues on a background
 * thread, so playback can start after the first few seconds are ready ([readyFrames]).
 */
class Track(
    val uri: Uri,
    val title: String,
    val artist: String,
    val pcm: ShortArray,
    val peaks: FloatArray,
    estFrames: Int,
) {
    @Volatile var frames: Int = estFrames
    @Volatile var readyFrames: Int = 0
    @Volatile var complete: Boolean = false
    @Volatile var failed: Boolean = false
    @Volatile var cancelled: Boolean = false
    @Volatile var bpm: Float = 0f
    @Volatile var beatOffset: Float = 0f
    /** How much of the overview wave has been animated in; kept here so it survives tab switches. */
    @Volatile var revealed: Float = 0f
}

private class PcmBuilder(initialCapacity: Int) {
    var data = ShortArray(max(initialCapacity, 1 shl 16))
    var size = 0

    fun addAll(sb: ShortBuffer) {
        val n = sb.remaining()
        if (size + n > data.size) {
            data = data.copyOf(max(size + n, data.size + data.size / 2))
        }
        sb.get(data, size, n)
        size += n
    }
}

object Decoder {
    const val MAX_SECONDS = 480

    /** Decodes any Android-supported audio file to interleaved stereo PCM at 44.1 kHz. */
    fun decodeStereo(ctx: Context, uri: Uri, maxSeconds: Int): ShortArray? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, uri, null)
            var trackIdx = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME)
                if (m != null && m.startsWith("audio/")) {
                    trackIdx = i
                    fmt = f
                    break
                }
            }
            if (trackIdx < 0 || fmt == null) return null
            ex.selectTrack(trackIdx)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null

            var srcRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else SR
            var channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            val durSec = if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                min(maxSeconds.toLong(), fmt.getLong(MediaFormat.KEY_DURATION) / 1_000_000L).toInt() + 2
            } else {
                60
            }
            val raw = PcmBuilder(durSec * srcRate * max(1, channels))

            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()

            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var stall = 0
            while (!outDone) {
                if (!inDone) {
                    val ib = c.dequeueInputBuffer(10_000)
                    if (ib >= 0) {
                        val buf = c.getInputBuffer(ib)
                        val n = if (buf != null) ex.readSampleData(buf, 0) else -1
                        if (n < 0) {
                            c.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            c.queueInputBuffer(ib, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val ob = c.dequeueOutputBuffer(info, 10_000)
                if (ob >= 0) {
                    stall = 0
                    val out = c.getOutputBuffer(ob)
                    if (out != null && info.size > 0) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        raw.addAll(out.order(ByteOrder.nativeOrder()).asShortBuffer())
                    }
                    c.releaseOutputBuffer(ob, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    if (raw.size.toLong() >= maxSeconds.toLong() * srcRate * max(1, channels)) outDone = true
                } else if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = c.outputFormat
                    srcRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (inDone) {
                    stall++
                    if (stall > 300) outDone = true
                }
            }

            val ch = max(1, channels)
            val srcFrames = raw.size / ch
            if (srcFrames < SR / 2) return null
            val outFrames = (srcFrames.toLong() * SR / srcRate).toInt()
            val out = ShortArray(outFrames * 2)
            val d = raw.data
            if (srcRate == SR) {
                for (i in 0 until outFrames) {
                    val b = i * ch
                    val l = d[b]
                    val r = if (ch > 1) d[b + 1] else l
                    out[i * 2] = l
                    out[i * 2 + 1] = r
                }
            } else {
                val ratio = srcRate.toDouble() / SR
                for (i in 0 until outFrames) {
                    val p = i * ratio
                    val i0 = p.toInt()
                    val fr = (p - i0).toFloat()
                    val i1 = min(i0 + 1, srcFrames - 1)
                    val l0 = d[i0 * ch].toInt()
                    val l1 = d[i1 * ch].toInt()
                    val r0 = if (ch > 1) d[i0 * ch + 1].toInt() else l0
                    val r1 = if (ch > 1) d[i1 * ch + 1].toInt() else l1
                    out[i * 2] = (l0 + (l1 - l0) * fr).toInt().toShort()
                    out[i * 2 + 1] = (r0 + (r1 - r0) * fr).toInt().toShort()
                }
            }
            return out
        } catch (e: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                ex.release()
            } catch (_: Exception) {
            }
        }
    }

    fun readMeta(ctx: Context, uri: Uri): Pair<String?, String?> {
        val m = MediaMetadataRetriever()
        return try {
            m.setDataSource(ctx, uri)
            Pair(
                m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            )
        } catch (e: Exception) {
            Pair(null, null)
        } finally {
            try {
                m.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Starts decoding [song] on a background thread and returns as soon as the first seconds are
     * playable. Peaks and BPM are filled in later; [onAnalyzed] is called when they are ready.
     */
    fun startTrack(ctx: Context, song: Song, onAnalyzed: (Track) -> Unit): Track? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, song.uri, null)
            var idx = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME)
                if (m != null && m.startsWith("audio/")) {
                    idx = i
                    fmt = f
                    break
                }
            }
            if (idx < 0 || fmt == null) {
                ex.release()
                return null
            }
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: run {
                ex.release()
                return null
            }
            ex.selectTrack(idx)
            val durSec = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) / 1_000_000.0 else 300.0
            val secs = min(durSec, MAX_SECONDS.toDouble())
            val cap = min((secs * SR * 1.03).toInt() + SR * 2, (MAX_SECONDS + 5) * SR)
            val track = Track(song.uri, song.title, song.artist, ShortArray(cap * 2), FloatArray(900), min((secs * SR).toInt(), cap))
            val f2: MediaFormat = fmt
            Thread({ decodeLoop(ex, f2, mime, track, cap, onAnalyzed) }, "track-decoder").start()

            val need = min(SR * 3, track.frames)
            val t0 = System.currentTimeMillis()
            while (!track.complete && !track.failed && track.readyFrames < need && System.currentTimeMillis() - t0 < 20000) {
                Thread.sleep(4)
            }
            if (track.failed || track.readyFrames < min(need, SR / 2)) {
                track.cancelled = true
                return null
            }
            return track
        } catch (e: Exception) {
            try {
                ex.release()
            } catch (_: Exception) {
            }
            return null
        }
    }

    private fun decodeLoop(ex: MediaExtractor, fmt: MediaFormat, mime: String, track: Track, cap: Int, onAnalyzed: (Track) -> Unit) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND)
        var codec: MediaCodec? = null
        try {
            var srcRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else SR
            var channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()
            val w = StreamWriter(track.pcm, cap, srcRate)
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var stall = 0
            while (!outDone && !track.cancelled) {
                while (!inDone) {
                    val ib = c.dequeueInputBuffer(0)
                    if (ib < 0) break
                    val buf = c.getInputBuffer(ib)
                    val n = if (buf != null) ex.readSampleData(buf, 0) else -1
                    if (n < 0) {
                        c.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inDone = true
                    } else {
                        c.queueInputBuffer(ib, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
                val ob = c.dequeueOutputBuffer(info, 4000)
                if (ob >= 0) {
                    stall = 0
                    val out = c.getOutputBuffer(ob)
                    if (out != null && info.size > 0) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        w.push(out.order(ByteOrder.nativeOrder()).asShortBuffer(), channels)
                        track.readyFrames = w.frames
                    }
                    c.releaseOutputBuffer(ob, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    if (w.full) outDone = true
                } else if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = c.outputFormat
                    srcRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    w.setRate(srcRate)
                } else if (inDone) {
                    stall++
                    if (stall > 500) outDone = true
                }
            }
            if (track.cancelled) return
            track.frames = w.frames
            track.readyFrames = w.frames
            track.complete = true
            if (w.frames < SR / 2) {
                track.failed = true
                return
            }
            computePeaks(track.pcm, w.frames, track.peaks)
            val r = BpmDetector.detect(track.pcm, w.frames)
            track.bpm = r.bpm
            track.beatOffset = r.offsetSec
            onAnalyzed(track)
        } catch (e: Throwable) {
            if (track.readyFrames > SR) {
                track.frames = track.readyFrames
                track.complete = true
            } else {
                track.failed = true
            }
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                ex.release()
            } catch (_: Exception) {
            }
        }
    }

    fun computePeaks(pcm: ShortArray, frames: Int, out: FloatArray) {
        val n = out.size
        val per = max(1, frames / n)
        for (b in 0 until n) {
            var m = 0
            val s = b * per
            val e = min(frames, s + per)
            val stride = max(1, (e - s) / 64)
            var i = s
            while (i < e) {
                val v = abs(pcm[i * 2].toInt())
                if (v > m) m = v
                i += stride
            }
            out[b] = m / 32768f
        }
        var mx = 0f
        for (v in out) if (v > mx) mx = v
        if (mx > 0f) for (i in out.indices) out[i] = out[i] / mx
    }
}

/** Writes decoded samples straight into the final 44.1 kHz stereo array, resampling if needed. */
private class StreamWriter(val dst: ShortArray, val capFrames: Int, srcRate: Int) {
    var frames = 0
    val full: Boolean get() = frames >= capFrames
    private var ratio = srcRate.toDouble() / SR
    private var same = srcRate == SR
    private var haveFirst = false
    private var prevL = 0
    private var prevR = 0
    private var gi = 0L
    private var sp = 0.0

    fun setRate(r: Int) {
        ratio = r.toDouble() / SR
        same = r == SR
    }

    private fun emit(l: Float, r: Float) {
        if (frames >= capFrames) return
        dst[frames * 2] = l.toInt().toShort()
        dst[frames * 2 + 1] = r.toInt().toShort()
        frames++
    }

    fun push(sb: ShortBuffer, ch: Int) {
        val c = max(1, ch)
        val n = sb.remaining() / c
        val start = sb.position()
        if (same) {
            val k = min(n, capFrames - frames)
            var o = frames * 2
            for (i in 0 until k) {
                val idx = start + i * c
                val l = sb.get(idx)
                dst[o] = l
                dst[o + 1] = if (c > 1) sb.get(idx + 1) else l
                o += 2
            }
            frames += k
        } else {
            for (i in 0 until n) {
                val idx = start + i * c
                val l = sb.get(idx).toInt()
                val r = if (c > 1) sb.get(idx + 1).toInt() else l
                if (!haveFirst) {
                    prevL = l
                    prevR = r
                    haveFirst = true
                    gi = 0
                    continue
                }
                gi++
                while (sp < gi) {
                    val fr = (sp - (gi - 1)).toFloat()
                    emit(prevL + (l - prevL) * fr, prevR + (r - prevR) * fr)
                    sp += ratio
                }
                prevL = l
                prevR = r
                if (frames >= capFrames) return
            }
        }
    }
}

/** Simple onset-autocorrelation tempo estimator (70-180 BPM). Returns 0 when unsure. */
class BpmResult(val bpm: Float, val offsetSec: Float)

object BpmDetector {
    fun detect(pcm: ShortArray, frames: Int): BpmResult {
        val hop = 256
        val len = min(frames, SR * 60)
        val start = if (frames > SR * 90) (frames - len) / 2 else 0
        val n = len / hop - 2
        if (n < 600) return BpmResult(0f, 0f)

        val env = FloatArray(n)
        for (h in 0 until n) {
            var s = 0.0
            val base = start + h * hop
            var k = 0
            while (k < hop) {
                val v = pcm[(base + k) * 2].toInt() / 32768.0
                s += v * v
                k += 2
            }
            env[h] = sqrt(s / (hop / 2)).toFloat()
        }
        val onset = FloatArray(n)
        for (i in 1 until n) onset[i] = max(0f, env[i] - env[i - 1])
        var mean = 0f
        for (v in onset) mean += v
        mean /= n
        for (i in 0 until n) onset[i] = onset[i] - mean

        val hopRate = SR / hop.toFloat()
        var best = 0f
        var bestScore = -1e9f
        var bpm = 70f
        while (bpm <= 180f) {
            val lag = 60f * hopRate / bpm
            var score = autoc(onset, lag) + 0.5f * autoc(onset, lag * 2f)
            val z = ln(bpm / 120f) / 0.5f
            val w = exp(-0.5f * z * z)
            score *= (0.6f + 0.4f * w)
            if (score > bestScore) {
                bestScore = score
                best = bpm
            }
            bpm += 0.25f
        }
        val bpmOut = (best * 10f).roundToInt() / 10f

        // Find where the beats fall (phase of the beat grid) with a comb filter.
        val period = 60f * hopRate / best
        var bestP = 0f
        var bestS = -1e9f
        var p = 0f
        while (p < period) {
            var s = 0f
            var cnt = 0
            var x = p
            while (x < n - 2) {
                val i0 = x.toInt()
                val fr = x - i0
                s += onset[i0] * (1f - fr) + onset[i0 + 1] * fr
                cnt++
                x += period
            }
            if (cnt > 0) s /= cnt
            if (s > bestS) {
                bestS = s
                bestP = p
            }
            p += 0.5f
        }
        val t0 = (start + bestP * hop) / SR.toFloat()
        val beatLen = 60f / bpmOut
        return BpmResult(bpmOut, t0 % beatLen)
    }

    private fun autoc(x: FloatArray, lag: Float): Float {
        val li = lag.toInt()
        val fr = lag - li
        val n = x.size - li - 2
        if (n <= 0) return 0f
        var s = 0f
        for (i in 0 until n) {
            s += x[i] * (x[i + li] * (1f - fr) + x[i + li + 1] * fr)
        }
        return s / n
    }
}

class DropDef(val name: String, val gen: () -> ShortArray)

/** Procedurally generated DJ drops / sound effects, so the app needs no bundled audio. */
object DropSynth {
    private fun tone(
        sec: Float,
        saw: Boolean = false,
        freq: (Float) -> Float,
        env: (Float) -> Float,
    ): FloatArray {
        val n = (sec * SR).toInt()
        val out = FloatArray(n)
        var ph = 0.0
        for (i in 0 until n) {
            val t = i / n.toFloat()
            ph += 2.0 * PI * freq(t) / SR
            val s = if (saw) (((ph / (2.0 * PI)) % 1.0) * 2.0 - 1.0).toFloat() else sin(ph).toFloat()
            out[i] = s * env(t)
        }
        return out
    }

    private fun ad(t: Float, a: Float, r: Float): Float = min(1f, t / a) * min(1f, (1f - t) / r)

    private fun noise(sec: Float, seed: Int, env: (Float) -> Float): FloatArray {
        val rnd = Random(seed)
        val n = (sec * SR).toInt()
        return FloatArray(n) { (rnd.nextFloat() * 2f - 1f) * env(it / n.toFloat()) }
    }

    private fun mix(vararg parts: FloatArray): FloatArray {
        var n = 0
        for (p in parts) n = max(n, p.size)
        val o = FloatArray(n)
        for (p in parts) for (i in p.indices) o[i] += p[i]
        return o
    }

    private fun concat(vararg parts: FloatArray): FloatArray {
        var n = 0
        for (p in parts) n += p.size
        val o = FloatArray(n)
        var at = 0
        for (p in parts) {
            p.copyInto(o, at)
            at += p.size
        }
        return o
    }

    private fun silence(sec: Float) = FloatArray((sec * SR).toInt())

    private fun finish(f: FloatArray): ShortArray {
        var peak = 1e-6f
        for (v in f) peak = max(peak, abs(v))
        val g = 0.85f / peak * 32767f
        return ShortArray(f.size) { (f[it] * g).toInt().coerceIn(-32767, 32767).toShort() }
    }

    val all: List<DropDef> = listOf(
        // ---- page 1
        DropDef("AIRHORN") {
            finish(
                mix(
                    tone(0.9f, true, { 440f }, { ad(it, 0.03f, 0.25f) }),
                    tone(0.9f, true, { 554f }, { ad(it, 0.03f, 0.25f) }),
                    tone(0.9f, true, { 659f }, { ad(it, 0.03f, 0.25f) }),
                ),
            )
        },
        DropDef("SIREN") {
            finish(tone(1.4f, false, { 800f + 380f * sin(2.0 * PI * 2.5 * it * 1.4).toFloat() }, { ad(it, 0.05f, 0.2f) }))
        },
        DropDef("LASER") {
            finish(tone(0.35f, true, { 200f + 1800f * (1f - it) * (1f - it) }, { 1f - it }))
        },
        DropDef("BOOM") {
            finish(tone(0.9f, false, { 35f + 110f * exp(-5f * it) }, { min(1f, it / 0.004f) * (1f - it) * (1f - it) }))
        },
        DropDef("RISER") {
            finish(tone(1.6f, true, { 150f + 1900f * it * it }, { it * min(1f, (1f - it) / 0.03f) }))
        },
        DropDef("SCRATCH") {
            finish(tone(0.45f, true, { 300f + 700f * sin(2.0 * PI * 6.0 * it * 0.45).toFloat() }, { ad(it, 0.02f, 0.3f) }))
        },
        // ---- page 2
        DropDef("CLAP") {
            finish(
                noise(0.35f, 7) { t ->
                    val ms = t * 0.35f
                    val a = exp(-ms * 55f)
                    val b = if (ms > 0.012f) exp(-(ms - 0.012f) * 55f) else 0f
                    val c = if (ms > 0.026f) exp(-(ms - 0.026f) * 30f) else 0f
                    max(a, max(b, c))
                },
            )
        },
        DropDef("COWBELL") {
            finish(
                mix(
                    tone(0.5f, false, { 540f }, { exp(-7f * it) }),
                    tone(0.5f, false, { 810f }, { exp(-7f * it) }),
                    tone(0.5f, false, { 1620f }, { 0.3f * exp(-9f * it) }),
                ),
            )
        },
        DropDef("DING") {
            finish(
                mix(
                    tone(1.0f, false, { 1320f }, { min(1f, it / 0.003f) * exp(-4f * it) }),
                    tone(1.0f, false, { 2640f }, { 0.3f * exp(-6f * it) }),
                ),
            )
        },
        DropDef("ZAP") {
            finish(tone(0.25f, true, { 150f + 3000f * exp(-8f * it) }, { 1f - it }))
        },
        DropDef("BLIP") {
            finish(concat(tone(0.08f, false, { 900f }, { ad(it, 0.1f, 0.4f) }), silence(0.03f), tone(0.1f, false, { 1400f }, { ad(it, 0.1f, 0.4f) })))
        },
        DropDef("BASSDROP") {
            finish(tone(1.5f, false, { 38f + 360f * exp(-3.5f * it) }, { min(1f, it / 0.01f) * (1f - it) }))
        },
        // ---- page 3
        DropDef("TWE3X") {
            finish(tone(0.4f, false, { 500f + 1800f * it + 150f * sin(2.0 * PI * 30.0 * it * 0.4).toFloat() }, { ad(it, 0.05f, 0.4f) }))
        },
        DropDef("WAHAHA") {
            val parts = ArrayList<FloatArray>()
            for (k in 0 until 3) {
                parts.add(tone(0.2f, true, { 430f - 55f * k - 110f * it }, { sin(PI * it).toFloat() * (0.8f + 0.2f * sin(2.0 * PI * 18.0 * it * 0.2).toFloat()) }))
                parts.add(silence(0.07f))
            }
            finish(concat(*parts.toTypedArray()))
        },
        DropDef("TRIP") {
            finish(
                tone(0.6f, true, { 200f + 320f * ((it * 8f) % 1f) }, {
                    val g = if (((it * 8f) % 1f) < 0.7f) 1f else 0.15f
                    g * (1f - it)
                }),
            )
        },
        DropDef("WEEY") {
            finish(tone(0.8f, false, { 500f + 900f * sin(PI * it).toFloat() }, { ad(it, 0.05f, 0.2f) }))
        },
        DropDef("OH NO") {
            finish(
                concat(
                    tone(0.28f, true, { 520f - 70f * it }, { ad(it, 0.05f, 0.3f) }),
                    silence(0.04f),
                    tone(0.55f, true, { 440f - 270f * it + 25f * sin(2.0 * PI * 7.0 * it * 0.55).toFloat() }, { ad(it, 0.04f, 0.4f) }),
                ),
            )
        },
        DropDef("YAHOO") {
            finish(tone(0.7f, false, { 380f + 520f * it.pow(0.7f) + 40f * sin(2.0 * PI * 9.0 * it * 0.7).toFloat() }, { ad(it, 0.1f, 0.3f) }))
        },
    )

    /** Short synth note used by the PLAY keyboard. */
    fun note(freqHz: Float): ShortArray {
        val a = tone(0.9f, true, { freqHz }, { min(1f, it / 0.004f) * exp(-3.2f * it) })
        val b = tone(0.9f, false, { freqHz * 2f }, { min(1f, it / 0.004f) * exp(-4.5f * it) })
        for (i in a.indices) a[i] = a[i] * 0.55f + b[i] * 0.35f
        return finish(a)
    }
}
