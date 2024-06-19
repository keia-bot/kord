package dev.kord.voice.udp

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.io.ByteArrayView
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import io.ktor.network.sockets.Datagram as KtorDatagram
