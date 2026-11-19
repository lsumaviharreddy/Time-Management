package com.example.timeblock

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var state: PlannerState

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            state.refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reminders.createChannels(this)
        state = PlannerState(applicationContext)
        state.onRequestNotif = ::requestNotifications
        state.onRequestExact = ::requestExactAlarms
        state.onRequestUsage = { startActivity(Usage.usageAccessIntent()) }
        state.onRequestOverlay = {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }

        if (Build.VERSION.SDK_INT >= 33 && !state.store.notifAsked) {
            requestNotifications()
        }

        setContent {
            TimeBlockTheme {
                AppRoot(state)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        state.refreshPermissions()
        Reminders.rescheduleAll(this) // picks up exact-alarm permission if just granted
        Monitor.sync(this) // starts/stops the app-timer service
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            val askedBefore = state.store.notifAsked
            if (askedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                openNotificationSettings()
            } else {
                state.store.notifAsked = true
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            )
        }
    }
}

@Composable
fun AppRoot(state: PlannerState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            state.tick()
            delay(1000)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("Plan") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    label = { Text("Focus") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    label = { Text("Apps") },
                )
                NavigationBarItem(
                    selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                    label = { Text("Review") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> PlanScreen(state)
                1 -> FocusScreen(state)
                2 -> AppsScreen(state)
                else -> ReviewScreen(state)
            }
        }
    }
}
