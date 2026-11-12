package android.media

/** Silent JVM stand-ins so the live procedural synth compiles; nothing is played. */
class AudioAttributes {
    class Builder {
        fun setUsage(u: Int) = this
        fun setContentType(c: Int) = this
        fun build() = AudioAttributes()
    }
    companion object { const val USAGE_GAME = 14; const val CONTENT_TYPE_SONIFICATION = 4 }
}
class AudioFormat {
    class Builder {
        fun setEncoding(e: Int) = this
        fun setSampleRate(r: Int) = this
        fun setChannelMask(m: Int) = this
        fun build() = AudioFormat()
    }
    companion object { const val ENCODING_PCM_16BIT = 2; const val CHANNEL_OUT_MONO = 4 }
}
class AudioTrack {
    val state = 0
    fun write(s: ShortArray, o: Int, n: Int, mode: Int) = 0
    fun setVolume(v: Float) = 0
    fun play() = Unit
    fun stop() = Unit
    fun release() = Unit
    class Builder {
        fun setAudioAttributes(a: AudioAttributes) = this
        fun setAudioFormat(f: AudioFormat) = this
        fun setTransferMode(m: Int) = this
        fun setBufferSizeInBytes(b: Int) = this
        fun build() = AudioTrack()
    }
    companion object { const val STATE_INITIALIZED = 1; const val WRITE_BLOCKING = 0; const val MODE_STATIC = 0 }
}
