package dev.kord.voice.encryption

import dev.kord.voice.EncryptionMode

public actual val AeadAes256Gcm: VoiceEncryption get() = AeadAes256GcmImpl

private data object AeadAes256GcmImpl : VoiceEncryption {
    override val nonceLength: Int get() = 4

    override val mode: EncryptionMode
        get() = EncryptionMode.AeadAes256Gcm

    override fun createBox(key: ByteArray): VoiceEncryption.Box {
        TODO("Not yet implemented")
    }

    override fun createUnbox(key: ByteArray): VoiceEncryption.Unbox {
        TODO("Not yet implemented")
    }
}
