package cat.sergibonet.diagnosi

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Avís sonor de velocitat: un "pi-pi-pi-pi" cada 10 segons mentre la velocitat del GPS
 * sigui igual o superior al llindar dels ajustaments. Sona pel volum de multimèdia
 * del cotxe, sense aturar la ràdio ni la música.
 */
object SpeedAlert {
    private val logic = AlertLogic()
    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null

    fun onFix(elapsedMs: Long, kmh: Double) {
        if (logic.onFix(elapsedMs, kmh, Prefs.alertOn, Prefs.alertKmh)) play()
    }

    private fun createTone(): ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 85)
    } catch (e: RuntimeException) {
        null
    }

    /** Quatre pips curts. També es fa servir al botó "Prova el so". */
    fun play() {
        val t = tone ?: createTone() ?: return
        tone = t
        for (i in 0 until 4) {
            handler.postDelayed({
                try {
                    t.startTone(ToneGenerator.TONE_CDMA_PIP, 120)
                } catch (e: RuntimeException) {
                    // si el so falla, no passa res: l'avís és un extra
                }
            }, i * 260L)
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        tone?.release()
        tone = null
    }
}
