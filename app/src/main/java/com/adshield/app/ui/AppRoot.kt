package com.adshield.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.adshield.app.R

private enum class Screen(val labelRes: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Filled.Home),
    Browser(R.string.tab_browser, Icons.Filled.Language),
    Apps(R.string.tab_apps, Icons.Filled.Apps),
    Filters(R.string.tab_filters, Icons.Filled.FilterAlt),
    Settings(R.string.tab_settings, Icons.Filled.Settings)
}

@Composable
fun AppRoot(onRequestVpn: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(Screen.Home.name) }
    val screen = runCatching { Screen.valueOf(current) }.getOrDefault(Screen.Home)

    BackHandler(enabled = screen != Screen.Home) { current = Screen.Home.name }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { current = item.name },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.Home -> DashboardScreen(onRequestVpn = onRequestVpn)
                Screen.Browser -> BrowserScreen()
                Screen.Apps -> AppsScreen()
                Screen.Filters -> FiltersScreen()
                Screen.Settings -> SettingsScreen()
            }
        }
    }
}
