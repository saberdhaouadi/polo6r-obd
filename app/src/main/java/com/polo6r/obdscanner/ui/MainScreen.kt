package com.polo6r.obdscanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.polo6r.obdscanner.MainViewModel

private val tabs = listOf(
    "🔌" to "Connect",
    "📊" to "Live",
    "⚠️" to "Faults",
    "✅" to "Readiness"
)

@Composable
fun MainScreen(vm: MainViewModel, onRequestPermissions: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Poll live PIDs only while the dashboard is visible.
    LaunchedEffect(tab) { vm.setLiveActive(tab == 1) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(icon) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> ConnectionScreen(vm, onRequestPermissions)
                1 -> DashboardScreen(vm)
                2 -> DtcScreen(vm)
                else -> ReadinessScreen(vm)
            }
        }
    }
}
