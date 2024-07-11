package dev.kord.voice.encryption

import dev.kord.voice.EncryptionMode

/**
 * An [encryption method][VoiceEncryption] that uses the AES-256 GCM cipher.
 */
public actual val AeadAes256Gcm: VoiceEncryption = object : VoiceEncryption {
    override val nonceLength: Int
        get() = TODO("Not yet implemented")

    override val mode: EncryptionMode
        get() = TODO("Not yet implemented")

    override fun createBox(key: ByteArray): VoiceEncryption.Box {
        TODO("Not yet implemented")
    }

    override fun createUnbox(key: ByteArray): VoiceEncryption.Unbox {
        TODO("Not yet implemented")
    }
}
