package io.github.tomsadeh.puki.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Whether the parent has let Puki use the microphone. */
fun hasMicPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Whether the microphone was refused since the game opened. Android stops asking after two
 * refusals, so asking again would do nothing at all: Puki answers the tap with a reaction instead.
 */
var micRefused = false

/** 16-bit mono PCM ([pcm], little-endian) at [rate] Hz, wrapped as a WAV file. */
fun wavBytes(pcm: ByteArray, rate: Int): ByteArray {
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
        putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(pcm.size)
    }
    return header.array() + pcm
}
