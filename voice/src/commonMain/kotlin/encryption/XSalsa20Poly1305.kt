package dev.kord.voice.encryption

import dev.kord.voice.encryption.strategies.LiteNonceStrategy
import dev.kord.voice.encryption.strategies.NonceStrategy

/**
 * An [encryption method][VoiceEncryption] that uses the XSalsa20 stream cipher and Poly1035 hash function.
 */
public expect fun XSalsa20Poly1305(nonceStrategyFactory: NonceStrategy.Factory = LiteNonceStrategy): VoiceEncryption