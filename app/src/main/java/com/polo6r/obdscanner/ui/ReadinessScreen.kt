package com.polo6r.obdscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polo6r.obdscanner.ConnState
import com.polo6r.obdscanner.MainViewModel
import com.polo6r.obdscanner.obd.Monitor

@Composable
fun ReadinessScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val st by vm.readiness.collectAsStateWithLifecycle()
    val connected = conn.state == ConnState.CONNECTED

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { vm.readReadiness() },
            enabled = connected && !st.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Read readiness monitors (0101)") }
        if (st.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!connected) Text("Connect to the adapter first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        st.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        st.readiness?.let { r ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (r.milOn) "Check Engine Light: ON" else "Check Engine Light: OFF",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (r.milOn) MaterialTheme.colorScheme.error else OkGreen
                    )
                    Text("Stored DTC count: ${r.dtcCount}")
                    Text(
                        if (r.compressionIgnition) "Engine type: compression ignition (diesel)"
                        else "Engine type: spark ignition (petrol)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val (supported, unsupported) = r.monitors.partition { it.supported }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(supported) { MonitorRow(it) }
                if (unsupported.isNotEmpty()) {
                    item {
                        Text(
                            "Not supported: " + unsupported.joinToString { it.name },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorRow(m: Monitor) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, modifier = Modifier.weight(1f), fontSize = 16.sp)
            Text(
                if (m.complete) "Complete" else "Not complete",
                color = if (m.complete) OkGreen else WarnAmber,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
