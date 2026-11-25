package com.polo6r.obdscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polo6r.obdscanner.ConnState
import com.polo6r.obdscanner.MainViewModel

private data class Gauge(val title: String, val value: String, val unit: String, val fraction: Float?, val color: Color)

@Composable
fun DashboardScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val d by vm.live.collectAsStateWithLifecycle()

    if (conn.state != ConnState.CONNECTED) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Not connected.\nOpen the Connect tab and pick your ELM327 adapter.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val primary = MaterialTheme.colorScheme.primary
    val amber = WarnAmber
    val gauges = listOf(
        Gauge("Engine RPM", d.rpm?.toString() ?: "--", "rpm", d.rpm?.let { it / 7000f }, primary),
        Gauge("Speed", d.speed?.toString() ?: "--", "km/h", d.speed?.let { it / 220f }, primary),
        Gauge("Coolant", d.coolant?.toString() ?: "--", "°C", d.coolant?.let { (it + 40) / 170f },
            if ((d.coolant ?: 0) > 105) MaterialTheme.colorScheme.error else primary),
        Gauge("Intake air", d.iat?.toString() ?: "--", "°C", d.iat?.let { (it + 40) / 170f }, primary),
        Gauge("Engine load", d.load?.let { "%.0f".format(it) } ?: "--", "%", d.load?.let { it / 100f }, amber),
        Gauge("Throttle", d.throttle?.let { "%.0f".format(it) } ?: "--", "%", d.throttle?.let { it / 100f }, amber),
        Gauge("Battery", d.voltage?.let { "%.1f".format(it) } ?: "--", "V", d.voltage?.let { (it - 8f) / 8f },
            if ((d.voltage ?: 13f) < 11.8f) MaterialTheme.colorScheme.error else OkGreen),
        Gauge("Short fuel trim", d.stft?.let { "%+.1f".format(it) } ?: "--", "%", d.stft?.let { (it + 25f) / 50f }, amber),
        Gauge("Long fuel trim", d.ltft?.let { "%+.1f".format(it) } ?: "--", "%", d.ltft?.let { (it + 25f) / 50f }, amber)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(gauges) { g -> GaugeCard(g) }
        item(span = { GridItemSpan(2) }) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Fuel system status", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(d.fuelStatus ?: "--", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GaugeCard(g: Gauge) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (g.fraction ?: 0f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(120.dp),
                    color = g.color,
                    trackColor = MaterialTheme.colorScheme.background,
                    strokeWidth = 9.dp,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(g.value, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(g.unit, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                g.title,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}
