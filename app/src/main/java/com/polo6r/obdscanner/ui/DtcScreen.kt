package com.polo6r.obdscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polo6r.obdscanner.ConnState
import com.polo6r.obdscanner.MainViewModel
import com.polo6r.obdscanner.obd.VagDtcDatabase

@Composable
fun DtcScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val dtc by vm.dtc.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val connected = conn.state == ConnState.CONNECTED

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.scanDtcs() },
                enabled = connected && !dtc.busy,
                modifier = Modifier.weight(1f)
            ) { Text("Scan fault codes") }
            OutlinedButton(
                onClick = { confirm = true },
                enabled = connected && !dtc.busy,
                modifier = Modifier.weight(1f)
            ) { Text("Clear codes") }
        }
        if (dtc.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!connected) {
            Text("Connect to the adapter first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        dtc.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dtc.scanned) {
                item { SectionTitle("Stored codes (Mode 03) – ${dtc.stored.size}") }
                if (dtc.stored.isEmpty()) item { Text("None", color = OkGreen) }
                items(dtc.stored) { DtcCard(it) }
                item { SectionTitle("Pending codes (Mode 07) – ${dtc.pending.size}") }
                if (dtc.pending.isEmpty()) item { Text("None", color = OkGreen) }
                items(dtc.pending) { DtcCard(it) }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear fault codes?") },
            text = {
                Text(
                    "This sends Mode 04. It will turn OFF the Check Engine Light and RESET all " +
                        "emissions readiness monitors to \"not complete\". The car may fail an emissions " +
                        "inspection until it finishes several drive cycles. Faults that still exist will " +
                        "come back.\n\nWrite down the codes first. Use ignition ON, engine OFF."
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirm = false; vm.clearDtcs() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear codes") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun DtcCard(code: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 14.dp)
            )
            Text(VagDtcDatabase.describe(code), modifier = Modifier.weight(1f))
        }
    }
}
