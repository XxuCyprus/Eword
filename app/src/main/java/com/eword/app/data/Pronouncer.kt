package com.eword.app.data

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.io.File
import java.util.Locale

/**
 * 发音器。
 * 优先播放词包内随包导入的美式发音音频；若无音频则回退到系统 TTS（美式）。
 */
class Pronouncer(private val ctx: Context) {

    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingTts: String? = null

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
                pendingTts?.let { speak(it) }
                pendingTts = null
            }
        }
    }

    /** 播放某个词的发音 */
    fun pronounce(packId: String, wordId: String, word: String) {
        val afd = openAudio(packId, wordId)
        if (afd != null) {
            playFrom(afd)
        } else {
            if (ttsReady) speak(word) else pendingTts = word
        }
    }

    private fun openAudio(packId: String, wordId: String): AssetFileDescriptor? {
        // 1) 导入到内部存储的音频
        val f = File(ctx.filesDir, "audio/$packId/$wordId.mp3")
        if (f.exists()) {
            return try {
                android.content.res.AssetFileDescriptor(
                    android.os.ParcelFileDescriptor.open(
                        f, android.os.ParcelFileDescriptor.MODE_READ_ONLY
                    ), 0, f.length()
                )
            } catch (t: Throwable) {
                null
            }
        }
        // 2) 内置在 assets 里的音频
        return try {
            ctx.assets.openFd("audio/$packId/$wordId.mp3")
        } catch (t: Throwable) {
            null
        }
    }

    private fun playFrom(afd: AssetFileDescriptor) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setOnCompletionListener { it.release() }
                prepare()
                start()
            }
        } catch (t: Throwable) {
            // 播放失败就静默跳过：发音是辅助功能，不该把界面拖崩
        } finally {
            // MediaPlayer 在 setDataSource 里已经把 fd 复制了一份，官方文档写明
            // 「关闭是调用方的责任，而且这次调用一返回就可以关」。不关的话每个词
            // 漏一个文件描述符，开着自动发音滑一个单元就上百个，只能等 GC 来收。
            runCatching { afd.close() }
        }
    }

    private fun speak(text: String) {
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "eword") }
    }

    fun release() {
        runCatching { player?.release() }
        runCatching { tts?.shutdown() }
        player = null
        tts = null
    }
}
