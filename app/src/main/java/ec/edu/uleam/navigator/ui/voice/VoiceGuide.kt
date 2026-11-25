package ec.edu.uleam.navigator.ui.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Voz del sistema (TextToSpeech de Android, sin librerías extra y sin internet si el teléfono tiene
 * la voz en español descargada). Si el motor no está disponible, simplemente no habla.
 */
class VoiceGuide(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val es = listOf(Locale("es", "EC"), Locale("es", "419"), Locale("es", "ES"), Locale("es"))
        ready = es.any { tts.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE }
        if (ready) pending?.let { speak(it) }
        pending = null
    }

    /** Dice [text] interrumpiendo lo anterior (el aviso más reciente es el que importa). */
    fun speak(text: String) {
        if (!ready) { pending = text; return }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav-${text.hashCode()}")
    }

    fun stop() { if (ready) tts.stop() }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
