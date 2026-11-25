package com.polo6r.obdscanner.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Thrown when the adapter does not return the '>' prompt in time. */
class ObdTimeoutException(message: String) : IOException(message)

data class InitResult(
    val adapterOk: Boolean,
    val ecuLinked: Boolean,
    val version: String,
    val protocol: String,
    val voltage: String,
    val message: String
)

/**
 * Bluetooth Classic (SPP / RFCOMM) transport + ELM327 clone-safe init sequence.
 * All socket I/O runs on Dispatchers.IO and is serialised by a mutex so
 * commands are NEVER sent concurrently (ELM327 buffer safety).
 */
@SuppressLint("MissingPermission")
class BluetoothObdManager(private val adapter: BluetoothAdapter?) {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val GAP_MS = 150L
    }

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var input: InputStream? = null
    @Volatile private var output: OutputStream? = null
    private val ioMutex = Mutex()

    /** Called for every logged command: (tx, rx). */
    var onTraffic: ((String, String) -> Unit)? = null

    val isConnected: Boolean
        get() = socket?.isConnected == true && input != null && output != null

    // ------------------------------------------------------------------ connect

    suspend fun connect(address: String) {
        withContext(Dispatchers.IO) {
            close()
            val bt = adapter ?: throw IOException("Bluetooth is not available on this device")
            if (!bt.isEnabled) throw IOException("Bluetooth is turned off")
            val device = try {
                bt.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                throw IOException("Invalid device address")
            }
            try { bt.cancelDiscovery() } catch (_: Exception) {}

            val s: BluetoothSocket = try {
                openSocket { device.createRfcommSocketToServiceRecord(SPP_UUID) }
            } catch (first: IOException) {
                // Many Chinese clones only accept the reflection-based RFCOMM channel 1 socket.
                try {
                    openSocket {
                        val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        m.invoke(device, 1) as BluetoothSocket
                    }
                } catch (second: Exception) {
                    throw IOException("Could not open RFCOMM socket: ${first.message ?: second.message}")
                }
            }
            socket = s
            input = s.inputStream
            output = s.outputStream
        }
    }

    /** Opens + connects a socket; a watchdog closes it if connect() hangs. */
    private suspend fun openSocket(factory: () -> BluetoothSocket): BluetoothSocket = coroutineScope {
        val s = factory()
        val watchdog = launch {
            delay(CONNECT_TIMEOUT_MS)
            runCatching { s.close() }
        }
        try {
            s.connect()
        } catch (e: IOException) {
            runCatching { s.close() }
            throw e
        } finally {
            watchdog.cancel()
        }
        s
    }

    fun close() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input = null
        output = null
        socket = null
    }

    // --------------------------------------------------------------------- send

    /**
     * Sends one command and reads until the '>' prompt. Never blocks the caller's
     * thread uninterruptibly: it polls available() with a hard timeout.
     * @throws ObdTimeoutException if no prompt within [timeoutMs]
     * @throws IOException if the link is broken (socket is closed automatically)
     */
    suspend fun send(command: String, timeoutMs: Long = 3000L, log: Boolean = true): String =
        ioMutex.withLock {
            withContext(Dispatchers.IO) {
                val ins = input ?: throw IOException("Not connected")
                val outs = output ?: throw IOException("Not connected")
                val sb = StringBuilder()
                try {
                    val trash = ByteArray(256)
                    while (ins.available() > 0) {
                        if (ins.read(trash, 0, minOf(ins.available(), trash.size)) < 0) break
                    }
                    outs.write((command + "\r").toByteArray(Charsets.US_ASCII))
                    outs.flush()

                    val buf = ByteArray(256)
                    val start = SystemClock.elapsedRealtime()
                    while (true) {
                        val n = ins.available()
                        if (n > 0) {
                            val r = ins.read(buf, 0, minOf(n, buf.size))
                            if (r < 0) throw IOException("Connection closed by adapter")
                            sb.append(String(buf, 0, r, Charsets.US_ASCII))
                            if (sb.indexOf(">") >= 0) break
                        } else {
                            if (SystemClock.elapsedRealtime() - start > timeoutMs) {
                                throw ObdTimeoutException("Timeout waiting for reply to $command")
                            }
                            delay(10)
                        }
                    }
                    val result = sb.toString().replace(">", "")
                    if (log) onTraffic?.invoke(command, result)
                    result
                } catch (e: ObdTimeoutException) {
                    if (log) onTraffic?.invoke(command, "<TIMEOUT> ${sb.toString().trim()}")
                    throw e
                } catch (e: IOException) {
                    if (log) onTraffic?.invoke(command, "<LINK ERROR> ${e.message}")
                    close()
                    throw e
                }
            }
        }

    private suspend fun trySend(cmd: String, timeoutMs: Long): String? =
        try { send(cmd, timeoutMs) } catch (e: ObdTimeoutException) { null }

    private fun unknown(raw: String): Boolean = ObdParser.clean(raw).any { it == "?" }

    // ------------------------------------------------------------------- init

    /**
     * Clone-safe init: ATZ (+1000 ms), ATE0, ATL0, ATS0, ATH0 with 150 ms gaps and a
     * single retry if '?' comes back, then ATSP6 with fallback to ATSP0.
     */
    suspend fun initialize(): InitResult {
        val notes = mutableListOf<String>()

        var z = trySend("ATZ", 5000)
        if (z == null) {
            delay(500)
            z = trySend("ATZ", 5000)
        }
        if (z == null) {
            return InitResult(false, false, "", "", "",
                "Adapter did not answer ATZ. Check that it is plugged in and not paired to another phone.")
        }
        delay(1000)

        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0")) {
            delay(GAP_MS)
            var r = trySend(cmd, 2500)
            if (r == null || unknown(r)) {
                delay(200)
                r = trySend(cmd, 2500)
            }
            if (r == null || unknown(r)) notes += "$cmd unsupported (skipped)"
        }

        delay(GAP_MS)
        trySend("ATSP6", 2500)
        delay(GAP_MS)
        var probe = trySend("0100", 15_000)
        var ecu = probe != null && ObdParser.pidData(probe, "00", 4) != null
        if (!ecu) {
            notes += "ATSP6 got no ECU reply, fell back to ATSP0 (auto)"
            delay(GAP_MS)
            trySend("ATSP0", 2500)
            delay(GAP_MS)
            probe = trySend("0100", 20_000)
            ecu = probe != null && ObdParser.pidData(probe, "00", 4) != null
        }

        delay(GAP_MS)
        val version = trySend("ATI", 2500)?.let { ObdParser.textLine(it, "ATI") } ?: ""
        delay(GAP_MS)
        val voltage = trySend("ATRV", 2500)?.let { ObdParser.textLine(it, "ATRV") } ?: ""
        delay(GAP_MS)
        val protocol = trySend("ATDP", 2500)?.let { ObdParser.textLine(it, "ATDP") } ?: ""

        val msg = if (ecu) {
            "ECU linked." + if (notes.isEmpty()) "" else " " + notes.joinToString("; ")
        } else {
            "Adapter OK but the ECU did not answer. Switch ignition ON (key position II) and reconnect."
        }
        return InitResult(true, ecu, version, protocol, voltage, msg)
    }
}
