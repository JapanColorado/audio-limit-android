package dev.russell.audiolimit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.russell.audiolimit.ui.home.HomeScreen
import dev.russell.audiolimit.ui.rules.EditRuleScreen
import dev.russell.audiolimit.ui.rules.RulesScreen
import dev.russell.audiolimit.ui.settings.SettingsScreen
import dev.russell.audiolimit.ui.stats.StatsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Today", Icons.Default.Headphones),
    Tab("rules", "Rules", Icons.Default.Lock),
    Tab("stats", "History", Icons.Default.BarChart),
    Tab("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    for (tab in tabs) {
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(onAddRule = { nav.navigate("rule/0?pkg=$it") }) }
            composable("rules") { RulesScreen(onOpen = { nav.navigate("rule/$it") }) }
            composable(
                "rule/{id}?pkg={pkg}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("pkg") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { backStack ->
                EditRuleScreen(
                    ruleId = backStack.arguments!!.getLong("id"),
                    presetPackage = backStack.arguments!!.getString("pkg"),
                    onDone = { nav.popBackStack() },
                )
            }
            composable("stats") { StatsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
