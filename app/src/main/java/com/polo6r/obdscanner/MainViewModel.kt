package com.polo6r.obdscanner

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.polo6r.obdscanner.obd.BluetoothObdManager
import com.polo6r.obdscanner.obd.ObdParser
import com.polo6r.obdscanner.obd.ObdTimeoutException
import com.polo6r.obdscanner.obd.Readiness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class ConnState { DISCONNECTED, CONNECTING, INITIALIZING, CONNECTED }

data class ConnectionUi(
    val state: ConnState = ConnState.DISCONNECTED,
    val deviceName: String = "",
    val elmVersion: String = "",
    val voltage: String = "",
    val protocol: String = "",
    val ecuLinked: Boolean = false,
    val message: String? = null
)

data class PairedDevice(val name: String, val address: String, val likelyObd: Boolean)

data class LiveData(
    val rpm: Int? = null,
    val speed: Int? = null,
    val coolant: Int? = null,
    val iat: Int? = null,
    val load: Float? = null,
    val throttle: Float? = null,
    val voltage: Float? = null,
    val stft: Float? = null,
    val ltft: Float? = null,
    val fuelStatus: String? = null
)

data class DtcState(
    val busy: Boolean = false,
    val scanned: Boolean = false,
    val stored: List<String> = emptyList(),
    val pending: List<String> = emptyList(),
    val message: String? = null
)

data class ReadinessState(
    val busy: Boolean = false,
    val readiness: Readiness? = null,
    val message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val btAdapter: BluetoothAdapter? =
        (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val obd = BluetoothObdManager(btAdapter)

    private val _conn = MutableStateFlow(ConnectionUi())
    val conn: StateFlow<ConnectionUi> = _conn.asStateFlow()

    private val _devices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _btMessage = MutableStateFlow<String?>(null)
    val btMessage: StateFlow<String?> = _btMessage.asStateFlow()

    private val _console = MutableStateFlow<List<String>>(emptyList())
    val console: StateFlow<List<String>> = _console.asStateFlow()

    private val _live = MutableStateFlow(LiveData())
    val live: StateFlow<LiveData> = _live.asStateFlow()

    private val _dtc = MutableStateFlow(DtcState())
    val dtc: StateFlow<DtcState> = _dtc.asStateFlow()

    private val _readiness = MutableStateFlow(ReadinessState())
    val readiness: StateFlow<ReadinessState> = _readiness.asStateFlow()

    private var connectJob: Job? = null
    private var liveJob: Job? = null
    @Volatile private var liveActive = false
    @Volatile private var opBusy = false
    @Volatile private var isCan = true

    private val fastPids = listOf("010C", "010D", "0104", "0111")
    private val slowPids = listOf("0105", "010F", "0106", "0107", "0103", "ATRV")

    init {
        obd.onTraffic = { tx, rx ->
            appendConsole("> $tx")
            rx.replace("\r", "\n").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { appendConsole(it) }
        }
    }

    // ---------------------------------------------------------------- permissions / devices

    fun onPermissionsChanged(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) refreshPairedDevices()
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        val a = btAdapter
        if (a == null) {
            _btMessage.value = "This device has no Bluetooth adapter."
            return
        }
        try {
            if (!a.isEnabled) {
                _btMessage.value = "Bluetooth is turned off. Enable it and tap Refresh."
                _devices.value = emptyList()
                return
            }
            _btMessage.value = null
            _devices.value = a.bondedDevices.map {
                val n = it.name ?: "Unknown"
                PairedDevice(n, it.address, looksLikeObd(n))
            }.sortedWith(compareByDescending<PairedDevice> { it.likelyObd }.thenBy { it.name })
            if (_devices.value.isEmpty()) {
                _btMessage.value = "No paired devices. Pair the ELM327 in Android Bluetooth settings first (PIN usually 1234 or 0000)."
            }
        } catch (e: SecurityException) {
            _btMessage.value = "Bluetooth permission missing."
        }
    }

    private fun looksLikeObd(name: String): Boolean {
        val n = name.uppercase()
        return listOf("OBD", "V-LINK", "VLINK", "ELM", "VGATE", "ICAR", "VEEPEAK", "KONNWEI", "OBDLINK")
            .any { n.contains(it) }
    }

    // -------------------------------------------------------------------- connection

    fun connect(device: PairedDevice) {
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            liveJob?.cancel()
            _live.value = LiveData()
            _conn.value = ConnectionUi(ConnState.CONNECTING, deviceName = device.name)
            try {
                obd.connect(device.address)
                _conn.value = ConnectionUi(ConnState.INITIALIZING, deviceName = device.name)
                val init = obd.initialize()
                if (!init.adapterOk) {
                    withContext(Dispatchers.IO) { obd.close() }
                    _conn.value = ConnectionUi(message = init.message)
                    return@launch
                }
                isCan = init.protocol.contains("CAN", ignoreCase = true)
                _conn.value = ConnectionUi(
                    state = ConnState.CONNECTED,
                    deviceName = device.name,
                    elmVersion = init.version,
                    voltage = init.voltage,
                    protocol = init.protocol,
                    ecuLinked = init.ecuLinked,
                    message = init.message
                )
                startPolling()
            } catch (e: CancellationException) {
                withContext(Dispatchers.IO) { obd.close() }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { obd.close() }
                _conn.value = ConnectionUi(message = "Connection failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectJob?.cancel()
            liveJob?.cancel()
            withContext(Dispatchers.IO) { obd.close() }
            _conn.value = ConnectionUi()
            _live.value = LiveData()
        }
    }

    private suspend fun handleLost(reason: String) {
        withContext(Dispatchers.IO) { obd.close() }
        _conn.value = ConnectionUi(message = reason)
        _live.value = LiveData()
        liveJob?.cancel() // keep last: may cancel the calling coroutine itself
    }

    // ------------------------------------------------------------------- raw console

    private fun appendConsole(line: String) {
        _console.update { (it + line).takeLast(300) }
    }

    fun clearConsole() { _console.value = emptyList() }

    fun sendConsole(command: String) {
        val c = command.trim().uppercase()
        if (c.isEmpty()) return
        if (!obd.isConnected) {
            appendConsole("! Not connected")
            return
        }
        viewModelScope.launch {
            opBusy = true
            try {
                obd.send(c, 6000, log = true)
            } catch (e: ObdTimeoutException) {
                // already logged by the manager
            } catch (e: IOException) {
                handleLost("Link lost: ${e.message}")
            } finally {
                opBusy = false
            }
        }
    }

    // ----------------------------------------------------------------------- live data

    fun setLiveActive(active: Boolean) { liveActive = active }

    private fun startPolling() {
        liveJob?.cancel()
        liveJob = viewModelScope.launch { pollLoop() }
    }

    private suspend fun pollLoop() {
        var cycle = 0
        var timeouts = 0
        while (currentCoroutineContext().isActive) {
            if (!liveActive || opBusy) { delay(250); continue }
            try {
                val sequence = fastPids + slowPids[cycle % slowPids.size]
                for (cmd in sequence) {
                    if (!liveActive || opBusy) break
                    pollOne(cmd)
                    delay(30)
                }
                timeouts = 0
            } catch (e: ObdTimeoutException) {
                timeouts++
                if (timeouts >= 4) {
                    handleLost("Adapter stopped responding (4 consecutive timeouts).")
                    return
                }
            } catch (e: IOException) {
                handleLost("Link lost: ${e.message ?: "Bluetooth disconnected"}")
                return
            }
            cycle++
        }
    }

    private suspend fun pollOne(cmd: String) {
        if (cmd == "ATRV") {
            val raw = obd.send(cmd, 2500, log = false)
            val v = ObdParser.parseVoltage(raw)?.toFloat()
            _live.update { it.copy(voltage = v) }
            return
        }
        val raw = obd.send(cmd, 3000, log = false)
        val pid = cmd.substring(2)
        if (pid == "03") {
            _live.update { it.copy(fuelStatus = ObdParser.decodeFuelStatus(raw)) }
            return
        }
        val v = ObdParser.decodePid(pid, raw)
        _live.update {
            when (pid) {
                "0C" -> it.copy(rpm = v?.toInt())
                "0D" -> it.copy(speed = v?.toInt())
                "05" -> it.copy(coolant = v?.toInt())
                "0F" -> it.copy(iat = v?.toInt())
                "04" -> it.copy(load = v?.toFloat())
                "11" -> it.copy(throttle = v?.toFloat())
                "06" -> it.copy(stft = v?.toFloat())
                "07" -> it.copy(ltft = v?.toFloat())
                else -> it
            }
        }
    }

    // ------------------------------------------------------------------------- DTCs

    fun scanDtcs() {
        if (_dtc.value.busy || !obd.isConnected) return
        viewModelScope.launch {
            opBusy = true
            _dtc.value = DtcState(busy = true, message = "Reading stored codes (03)…")
            try {
                val rawStored = obd.send("03", 8000)
                _dtc.update { it.copy(message = "Reading pending codes (07)…") }
                delay(150)
                val rawPending = obd.send("07", 8000)

                val err = ObdParser.hardError(rawStored) ?: ObdParser.hardError(rawPending)
                if (err != null) {
                    _dtc.value = DtcState(message = err)
                } else {
                    val stored = ObdParser.parseDtcs(rawStored, "43", isCan)
                    val pending = ObdParser.parseDtcs(rawPending, "47", isCan)
                    val msg = if (stored.isEmpty() && pending.isEmpty()) {
                        "No fault codes reported."
                    } else {
                        "${stored.size} stored, ${pending.size} pending."
                    }
                    _dtc.value = DtcState(scanned = true, stored = stored, pending = pending, message = msg)
                }
            } catch (e: ObdTimeoutException) {
                _dtc.value = DtcState(message = "Timed out while reading codes. Ignition ON? Try again.")
            } catch (e: IOException) {
                _dtc.value = DtcState(message = "Link lost during scan.")
                handleLost("Link lost: ${e.message ?: "Bluetooth disconnected"}")
            } finally {
                opBusy = false
            }
        }
    }

    fun clearDtcs() {
        if (_dtc.value.busy || !obd.isConnected) return
        viewModelScope.launch {
            opBusy = true
            _dtc.value = _dtc.value.copy(busy = true, message = "Clearing codes (04)…")
            try {
                val raw = obd.send("04", 8000)
                if (ObdParser.clearSucceeded(raw)) {
                    _dtc.value = DtcState(message = "Codes cleared. MIL should go off; readiness monitors were reset.")
                } else {
                    _dtc.value = _dtc.value.copy(
                        busy = false,
                        message = ObdParser.hardError(raw)
                            ?: "ECU did not confirm the clear (needs ignition ON, engine off)."
                    )
                }
            } catch (e: ObdTimeoutException) {
                _dtc.value = _dtc.value.copy(busy = false, message = "Timed out while clearing codes.")
            } catch (e: IOException) {
                _dtc.value = DtcState(message = "Link lost during clear.")
                handleLost("Link lost: ${e.message ?: "Bluetooth disconnected"}")
            } finally {
                opBusy = false
            }
        }
    }

    // -------------------------------------------------------------------- readiness

    fun readReadiness() {
        if (_readiness.value.busy || !obd.isConnected) return
        viewModelScope.launch {
            opBusy = true
            _readiness.value = _readiness.value.copy(busy = true, message = "Reading 0101…")
            try {
                val raw = obd.send("0101", 6000)
                val r = ObdParser.parseReadiness(raw)
                _readiness.value = if (r != null) {
                    ReadinessState(readiness = r)
                } else {
                    ReadinessState(message = ObdParser.hardError(raw) ?: "ECU returned no readiness data.")
                }
            } catch (e: ObdTimeoutException) {
                _readiness.value = ReadinessState(message = "Timed out reading readiness.")
            } catch (e: IOException) {
                _readiness.value = ReadinessState(message = "Link lost.")
                handleLost("Link lost: ${e.message ?: "Bluetooth disconnected"}")
            } finally {
                opBusy = false
            }
        }
    }

    override fun onCleared() {
        obd.close()
        super.onCleared()
    }
}
