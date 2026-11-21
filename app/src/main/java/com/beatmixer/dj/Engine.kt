package com.beatmixer.dj

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.tanh

fun dbToGain(db: Float): Float = if (db <= -24f) 0f else 10f.pow(db / 20f)

/**
 * One playback deck. All fields are written by the UI thread and read by the audio thread,
 * so they are @Volatile. [render] runs on the audio thread only.
 */
class Deck(val id: Int) {
    @Volatile var track: Track? = null
    @Volatile var posFrames: Double = 0.0
    @Volatile var playing: Boolean = false
    @Volatile var tempoPct: Float = 0f
    @Volatile var filter: Float = 0f          // -1 (low-pass) .. +1 (high-pass)
    @Volatile var echoOn: Boolean = false
    @Volatile var flangerOn: Boolean = false
    @Volatile var phaserOn: Boolean = false
    @Volatile var eqLow: Float = 0f           // dB
    @Volatile var eqMid: Float = 0f
    @Volatile var eqHigh: Float = 0f
    @Volatile var loopStart: Double = -1.0
    @Volatile var loopEnd: Double = -1.0
    @Volatile var seekReq: Double = Double.NaN
    @Volatile var cuePoint: Double = 0.0
    @Volatile var level: Float = 0f

    val rate: Double get() = 1.0 + tempoPct / 100.0

    // DSP state (audio thread only)
    private val ic1 = FloatArray(2)
    private val ic2 = FloatArray(2)
    private val lo1 = FloatArray(2)
    private val lo2 = FloatArray(2)
    private val dlyL = FloatArray(SR * 2)
    private val dlyR = FloatArray(SR * 2)
    private var dIdx = 0
    private val flL = FloatArray(1024)
    private val flR = FloatArray(1024)
    private var flIdx = 0
    private var flLfo = 0f
    private val phX = FloatArray(8)
    private val phY = FloatArray(8)
    private var phLfo = 0f
    private var phA = 0f

    fun seek(frames: Double) {
        seekReq = frames
    }

    fun render(outL: FloatArray, outR: FloatArray, n: Int) {
        val t = track
        val p: ShortArray = t?.pcm ?: EMPTY
        val total = t?.frames ?: 0
        var pos = posFrames
        val req = seekReq
        if (!req.isNaN()) {
            pos = req.coerceIn(0.0, max(0.0, total - 2.0))
            seekReq = Double.NaN
        }
        val ready = t?.readyFrames ?: 0
        var play = playing && t != null && total > 4
        val step = rate
        val ls = loopStart
        val le = loopEnd

        // --- filter coefficients (TPT state-variable filter)
        val f = filter
        val useFilter = abs(f) > 0.02f
        val isLp = f < 0f
        val kq = 1.2f
        var a1 = 0f
        var a2 = 0f
        var a3 = 0f
        if (useFilter) {
            val amt = abs(f)
            val fc = if (isLp) 18000f * (180f / 18000f).pow(amt) else 25f * (5000f / 25f).pow(amt)
            val g = tan(PI.toFloat() * fc / SR)
            a1 = 1f / (1f + g * (g + kq))
            a2 = g * a1
            a3 = g * a2
        }

        // --- 3-band EQ
        val eqOn = eqLow != 0f || eqMid != 0f || eqHigh != 0f
        val aLo = 1f - exp(-2f * PI.toFloat() * 250f / SR)
        val aHi = 1f - exp(-2f * PI.toFloat() * 2500f / SR)
        val gL = dbToGain(eqLow)
        val gM = dbToGain(eqMid)
        val gH = dbToGain(eqHigh)

        // --- echo (half-beat delay)
        val baseBpm = t?.bpm ?: 0f
        val bpmEff = if (baseBpm > 20f) baseBpm * step.toFloat() else 120f
        val dly = (60f / bpmEff * 0.5f * SR).toInt().coerceIn(SR / 8, SR * 2 - 1)
        val echo = echoOn
        val flanger = flangerOn
        val phaser = phaserOn

        var peak = 0f
        for (i in 0 until n) {
            var l = 0f
            var r = 0f
            if (play) {
                if (le > 0.0 && pos >= le) pos = ls + (pos - le)
                val ip = pos.toInt()
                if (ip >= total - 2 || ip < 0) {
                    play = false
                    playing = false
                    pos = max(0.0, total - 2.0)
                } else if (ip + 3 >= ready) {
                    // not decoded yet: hold position and stay silent
                } else {
                    val fr = (pos - ip).toFloat()
                    val b = ip * 2
                    l = (p[b] + (p[b + 2] - p[b]) * fr) * (1f / 32768f)
                    r = (p[b + 1] + (p[b + 3] - p[b + 1]) * fr) * (1f / 32768f)
                    pos += step
                }
            }

            if (useFilter) {
                var v3 = l - ic2[0]
                var v1 = a1 * ic1[0] + a2 * v3
                var v2 = ic2[0] + a2 * ic1[0] + a3 * v3
                ic1[0] = 2f * v1 - ic1[0]
                ic2[0] = 2f * v2 - ic2[0]
                l = if (isLp) v2 else l - kq * v1 - v2

                v3 = r - ic2[1]
                v1 = a1 * ic1[1] + a2 * v3
                v2 = ic2[1] + a2 * ic1[1] + a3 * v3
                ic1[1] = 2f * v1 - ic1[1]
                ic2[1] = 2f * v2 - ic2[1]
                r = if (isLp) v2 else r - kq * v1 - v2
            }

            if (eqOn) {
                lo1[0] += aLo * (l - lo1[0])
                lo2[0] += aHi * (l - lo2[0])
                l = lo1[0] * gL + (lo2[0] - lo1[0]) * gM + (l - lo2[0]) * gH
                lo1[1] += aLo * (r - lo1[1])
                lo2[1] += aHi * (r - lo2[1])
                r = lo1[1] * gL + (lo2[1] - lo1[1]) * gM + (r - lo2[1]) * gH
            }

            if (flanger) {
                flLfo += 0.25f / SR
                if (flLfo >= 1f) flLfo -= 1f
                val s = 0.5f + 0.5f * sin(2f * PI.toFloat() * flLfo)
                val dsm = 3f + s * 300f
                val rp = flIdx - dsm
                val i0 = Math.floor(rp.toDouble()).toInt()
                val fr = rp - i0
                val a0 = (i0 + 1024) and 1023
                val a1i = (i0 + 1025) and 1023
                val dl2 = flL[a0] + (flL[a1i] - flL[a0]) * fr
                val dr2 = flR[a0] + (flR[a1i] - flR[a0]) * fr
                flL[flIdx] = l + dl2 * 0.5f
                flR[flIdx] = r + dr2 * 0.5f
                flIdx = (flIdx + 1) and 1023
                l = (l + dl2) * 0.75f
                r = (r + dr2) * 0.75f
            }

            if (phaser) {
                if ((i and 31) == 0) {
                    phLfo += 32f * 0.4f / SR
                    if (phLfo >= 1f) phLfo -= 1f
                    val s = 0.5f + 0.5f * sin(2f * PI.toFloat() * phLfo)
                    val fc = 300f * 10f.pow(s)
                    val tn = tan(PI.toFloat() * fc / SR)
                    phA = (tn - 1f) / (tn + 1f)
                }
                var xl = l
                var xr = r
                for (st in 0 until 4) {
                    val yl = phA * xl + phX[st] - phA * phY[st]
                    phX[st] = xl
                    phY[st] = yl
                    xl = yl
                    val yr = phA * xr + phX[4 + st] - phA * phY[4 + st]
                    phX[4 + st] = xr
                    phY[4 + st] = yr
                    xr = yr
                }
                l = (l + xl) * 0.6f
                r = (r + xr) * 0.6f
            }

            val rd = (dIdx - dly + dlyL.size) % dlyL.size
            val dl = dlyL[rd]
            val dr = dlyR[rd]
            dlyL[dIdx] = (if (echo) l else 0f) + dl * 0.5f
            dlyR[dIdx] = (if (echo) r else 0f) + dr * 0.5f
            dIdx++
            if (dIdx >= dlyL.size) dIdx = 0
            l += dl * 0.55f
            r += dr * 0.55f

            outL[i] = l
            outR[i] = r
            val m = max(abs(l), abs(r))
            if (m > peak) peak = m
        }
        posFrames = pos
        level = peak
    }

    companion object {
        private val EMPTY = ShortArray(0)
    }
}

class DropVoice {
    @Volatile var data: ShortArray? = null
    @Volatile var pos: Int = 0
    @Volatile var gain: Float = 1f
}

/** Streams the mixed output of two decks, drops and the microphone to an AudioTrack. */
class MixEngine {
    companion object {
        const val BLOCK = 512
        private const val MIC_MASK = 32767L
        private const val HALF_PI = 1.5707964f
    }

    val decks = arrayOf(Deck(0), Deck(1))
    val voices = Array(8) { DropVoice() }

    @Volatile var crossfader = 0.5f   // 0 = deck A, 1 = deck B
    @Volatile var master = 0.85f
    @Volatile var muted = false
    @Volatile var micOn = false
    @Volatile var micGain = 1.0f
    @Volatile var mcTalk = false
    @Volatile var duck = 0.3f
    @Volatile var peakL = 0f
    @Volatile var peakR = 0f
    @Volatile var recorder: WavRecorder? = null

    private val micRing = ShortArray(1 shl 15)
    @Volatile private var micWrite = 0L
    private var micRead = 0L
    @Volatile private var micRunning = false
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "mix-engine").also { it.start() }
    }

    fun release() {
        running = false
        micRunning = false
        try {
            thread?.join(800)
        } catch (_: InterruptedException) {
        }
        thread = null
    }

    fun triggerDrop(sample: ShortArray, gain: Float = 1f) {
        val v = voices.firstOrNull { it.data == null } ?: voices[0]
        v.pos = 0
        v.gain = gain
        v.data = sample
    }

    @SuppressLint("MissingPermission")
    fun startMic(): Boolean {
        if (micRunning) return true
        val minB = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minB <= 0) return false
        val rec: AudioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SR,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minB, 4096),
            )
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        micRunning = true
        try {
            rec.startRecording()
        } catch (e: Exception) {
            micRunning = false
            rec.release()
            return false
        }
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buf = ShortArray(256)
            while (micRunning) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    var w = micWrite
                    for (i in 0 until n) {
                        micRing[(w and MIC_MASK).toInt()] = buf[i]
                        w++
                    }
                    micWrite = w
                } else if (n < 0) {
                    break
                }
            }
            try {
                rec.stop()
            } catch (_: Exception) {
            }
            rec.release()
            micRunning = false
        }, "mic-reader").start()
        return true
    }

    fun stopMic() {
        micRunning = false
    }

    private fun softClip(x: Float): Float {
        val a = abs(x)
        return if (a < 0.8f) x else sign(x) * (0.8f + 0.2f * tanh((a - 0.8f) / 0.2f))
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val bufBytes = max(minBuf, BLOCK * 4 * 4)
        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        at.play()

        val aL = FloatArray(BLOCK)
        val aR = FloatArray(BLOCK)
        val bL = FloatArray(BLOCK)
        val bR = FloatArray(BLOCK)
        val dropBuf = FloatArray(BLOCK)
        val micBuf = FloatArray(BLOCK)
        val out = ShortArray(BLOCK * 2)

        var prevA = cos(crossfader * HALF_PI)
        var prevB = sin(crossfader * HALF_PI)
        var prevDuck = 1f
        var prevMaster = if (muted) 0f else master
        var prevMic = 0f

        while (running) {
            decks[0].render(aL, aR, BLOCK)
            decks[1].render(bL, bR, BLOCK)

            // drops
            java.util.Arrays.fill(dropBuf, 0f)
            for (v in voices) {
                val d = v.data ?: continue
                var pp = v.pos
                if (pp >= d.size) {
                    v.data = null
                    continue
                }
                val g = v.gain / 32768f
                var i = 0
                while (i < BLOCK && pp < d.size) {
                    dropBuf[i] += d[pp] * g
                    i++
                    pp++
                }
                v.pos = pp
            }

            // mic
            if (micOn && micRunning) {
                var avail = micWrite - micRead
                if (avail > BLOCK * 4L) {
                    micRead = micWrite - BLOCK * 2L
                    avail = BLOCK * 2L
                }
                val take = min(avail, BLOCK.toLong()).toInt()
                for (i in 0 until take) micBuf[i] = micRing[((micRead + i) and MIC_MASK).toInt()] / 32768f
                for (i in take until BLOCK) micBuf[i] = 0f
                micRead += take
            } else {
                micRead = micWrite
                java.util.Arrays.fill(micBuf, 0f)
            }

            val cf = crossfader
            val tA = cos(cf * HALF_PI)
            val tB = sin(cf * HALF_PI)
            val tDuck = if (mcTalk) duck else 1f
            val tMaster = if (muted) 0f else master
            val tMic = if (micOn) micGain * (if (mcTalk) 1.6f else 1f) else 0f

            var pkL = 0f
            var pkR = 0f
            for (i in 0 until BLOCK) {
                val k = (i + 1) / BLOCK.toFloat()
                val gA = prevA + (tA - prevA) * k
                val gB = prevB + (tB - prevB) * k
                val gD = prevDuck + (tDuck - prevDuck) * k
                val gM = prevMaster + (tMaster - prevMaster) * k
                val gMic = prevMic + (tMic - prevMic) * k
                val m = micBuf[i] * gMic
                val sl = (aL[i] * gA + bL[i] * gB) * gD + dropBuf[i] + m
                val sr = (aR[i] * gA + bR[i] * gB) * gD + dropBuf[i] + m
                val oL = softClip(sl * gM)
                val oR = softClip(sr * gM)
                if (abs(oL) > pkL) pkL = abs(oL)
                if (abs(oR) > pkR) pkR = abs(oR)
                out[i * 2] = (oL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                out[i * 2 + 1] = (oR * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }
            prevA = tA
            prevB = tB
            prevDuck = tDuck
            prevMaster = tMaster
            prevMic = tMic

            peakL = max(pkL, peakL * 0.9f)
            peakR = max(pkR, peakR * 0.9f)

            recorder?.write(out.copyOf())
            at.write(out, 0, out.size)
        }
        try {
            at.stop()
        } catch (_: Exception) {
        }
        at.release()
    }
}

/** Records the master output to a 16-bit stereo WAV file on a background thread. */
class WavRecorder(val file: File) {
    private val queue = LinkedBlockingQueue<ShortArray>(4096)
    private val poison = ShortArray(0)
    private var thread: Thread? = null

    @Volatile
    var bytes: Long = 0L

    fun start() {
        thread = Thread({
            try {
                RandomAccessFile(file, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write(header(0L))
                    while (true) {
                        val s = queue.take()
                        if (s.isEmpty()) break
                        val bb = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                        bb.asShortBuffer().put(s)
                        raf.write(bb.array())
                        bytes += s.size * 2L
                    }
                    raf.seek(0)
                    raf.write(header(bytes))
                }
            } catch (_: Exception) {
            }
        }, "wav-writer").also { it.start() }
    }

    fun write(block: ShortArray) {
        queue.offer(block)
    }

    fun stop() {
        try {
            queue.offer(poison, 2, TimeUnit.SECONDS)
            thread?.join(4000)
        } catch (_: Exception) {
        }
    }

    private fun header(dataBytes: Long): ByteArray {
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt((36L + dataBytes).toInt())
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1.toShort())
        bb.putShort(2.toShort())
        bb.putInt(SR)
        bb.putInt(SR * 2 * 2)
        bb.putShort(4.toShort())
        bb.putShort(16.toShort())
        bb.put("data".toByteArray())
        bb.putInt(dataBytes.toInt())
        return bb.array()
    }
}
