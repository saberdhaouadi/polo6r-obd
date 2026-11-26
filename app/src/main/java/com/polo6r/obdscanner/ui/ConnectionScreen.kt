package com.polo6r.obdscanner.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polo6r.obdscanner.ConnState
import com.polo6r.obdscanner.ConnectionUi
import com.polo6r.obdscanner.MainViewModel
import com.polo6r.obdscanner.PairedDevice
import com.polo6r.obdscanner.R

@Composable
fun ConnectionScreen(vm: MainViewModel, onRequestPermissions: () -> Unit, modifier: Modifier = Modifier) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val hasPerm by vm.hasPermission.collectAsStateWithLifecycle()
    val btMsg by vm.btMessage.collectAsStateWithLifecycle()
    val console by vm.console.collectAsStateWithLifecycle()
    var consoleOpen by rememberSaveable { mutableStateOf(false) }
    var cmd by rememberSaveable { mutableStateOf("") }
    val idle = conn.state == ConnState.DISCONNECTED

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Polo",
                modifier = Modifier.size(84.dp).clip(MaterialTheme.shapes.medium).background(Color(0xFF0B0F14))
            )
            Column {
                Text("Polo 6R Scanner", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("VW Polo 6R · ELM327 Bluetooth", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        StatusCard(conn, onDisconnect = { vm.disconnect() })

        if (!hasPerm) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bluetooth permission needed", fontWeight = FontWeight.Bold)
                    Text(
                        "Android requires Nearby devices / Bluetooth permission to list paired adapters and open a connection.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onRequestPermissions) { Text("Grant permission") }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Paired devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.refreshPairedDevices() }) { Text("Refresh") }
            }
            btMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            devices.forEach { d ->
                DeviceRow(d, enabled = idle) { vm.connect(d) }
            }
        }

        // ---- Collapsible raw console
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { consoleOpen = !consoleOpen }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Raw AT Console", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(if (consoleOpen) "▲" else "▼")
                }
                if (consoleOpen) {
                    val scroll = rememberScrollState()
                    LaunchedEffect(console.size) { scroll.animateScrollTo(scroll.maxValue) }
                    Box(
                        Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 12.dp)
                            .clip(MaterialTheme.shapes.small).background(Color.Black)
                    ) {
                        Column(Modifier.verticalScroll(scroll).padding(8.dp)) {
                            if (console.isEmpty()) {
                                Text("Type an AT or OBD command (e.g. ATRV, 0100, 010C).",
                                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.Gray)
                            }
                            console.forEach { line ->
                                Text(
                                    line,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = when {
                                        line.startsWith(">") -> MaterialTheme.colorScheme.primary
                                        line.startsWith("!") || line.startsWith("<") -> MaterialTheme.colorScheme.error
                                        else -> OkGreen
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = cmd,
                            onValueChange = { cmd = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("ATRV") },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(onSend = {
                                vm.sendConsole(cmd); cmd = ""
                            })
                        )
                        Button(
                            onClick = { vm.sendConsole(cmd); cmd = "" },
                            enabled = conn.state == ConnState.CONNECTED
                        ) { Text("Send") }
                    }
                    TextButton(onClick = { vm.clearConsole() }, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                        Text("Clear console")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(conn: ConnectionUi, onDisconnect: () -> Unit) {
    val (label, color) = when (conn.state) {
        ConnState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.error
        ConnState.CONNECTING -> "Connecting…" to WarnAmber
        ConnState.INITIALIZING -> "Initialising ELM327…" to WarnAmber
        ConnState.CONNECTED -> (if (conn.ecuLinked) "Connected – ECU linked" else "Connected – no ECU reply") to
            (if (conn.ecuLinked) OkGreen else WarnAmber)
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (conn.state == ConnState.CONNECTING || conn.state == ConnState.INITIALIZING) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            if (conn.deviceName.isNotEmpty()) InfoRow("Device", conn.deviceName)
            if (conn.elmVersion.isNotEmpty()) InfoRow("Adapter (ATI)", conn.elmVersion)
            if (conn.voltage.isNotEmpty()) InfoRow("Battery (ATRV)", conn.voltage)
            if (conn.protocol.isNotEmpty()) InfoRow("Protocol (ATDP)", conn.protocol)
            conn.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            if (conn.state != ConnState.DISCONNECTED) {
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            }
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(v, modifier = Modifier.weight(0.55f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DeviceRow(d: PairedDevice, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (d.likelyObd) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                if (d.likelyObd) Text("OBD adapter", color = OkGreen, fontSize = 12.sp)
            }
            Text(d.address, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}
