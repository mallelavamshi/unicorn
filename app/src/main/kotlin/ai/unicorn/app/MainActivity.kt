/*
 * Copyright (C) 2026 Unicorn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.unicorn.app

import ai.unicorn.Unicorn
import ai.unicorn.memory.MemoryInsights
import ai.unicorn.memory.MemoryInsightsEngine
import ai.unicorn.memory.MemoryRepository
import ai.unicorn.ui.LocalUnicorn
import ai.unicorn.ui.UnicornTheme
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("today", "Today", Icons.Outlined.WbSunny),
    Tab("insights", "Insights", Icons.Outlined.AutoGraph),
    Tab("library", "Library", Icons.Outlined.LibraryBooks),
    Tab("explore", "Near you", Icons.Outlined.Explore),
    Tab("you", "You", Icons.Outlined.Person),
)

class MainActivity : FragmentActivity() {
    /** Starts locked when the app lock is on; locks again after the chosen time in the background. */
    private var locked by mutableStateOf(false)
    private var backgroundSince = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Unicorn.get(this).prefs
        locked = prefs.appLock && AppLock.isAvailable(this)
        val destination = intent?.getStringExtra("destination")
        setContent {
            UnicornTheme {
                var onboarded by remember { mutableStateOf(prefs.onboardingDone) }
                var revealed by remember { mutableStateOf(prefs.revealSeen) }
                when {
                    locked -> LockScreen(onUnlocked = { locked = false })
                    !onboarded -> Onboarding(onDone = { onboarded = true })
                    // Once, right after setup (and for people updating from an earlier version)
                    !revealed -> Reveal(onDone = { revealed = true })
                    else -> AppShell(startDestination = destination)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val prefs = Unicorn.get(this).prefs
        // The recents preview would show prompts, so it is blank while the lock is on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!prefs.appLock)
        if (prefs.appLock && backgroundSince != 0L && !locked && AppLock.isAvailable(this) &&
            SystemClock.elapsedRealtime() - backgroundSince >= prefs.lockAfterSeconds * 1000L
        ) {
            locked = true
        }
        backgroundSince = 0L
    }

    override fun onStop() {
        super.onStop()
        // The biometric prompt itself pauses the activity but doesn't stop it, so it never re-locks mid-unlock
        if (!isChangingConfigurations) backgroundSince = SystemClock.elapsedRealtime()
    }
}

@Composable
private fun AppShell(startDestination: String?) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val core = remember { Unicorn.get(context) }
    LaunchedEffect(Unit) {
        runCatching { core.trends.contributeIfDue() }
        if (startDestination == "privacy") nav.navigate("you")
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val colors = LocalUnicorn.current
    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            if (Tabs.any { it.route == route }) {
                NavigationBar(containerColor = colors.surface) {
                    Tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo("today") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.ink,
                                selectedTextColor = colors.ink,
                                indicatorColor = colors.ink.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(colors.background).padding(padding)) {
            AppNavHost(nav)
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    NavHost(nav, startDestination = "today") {
        composable("today") { TodayScreen(nav) }
        composable("insights") { InsightsScreen() }
        composable("library") { LibraryScreen(nav) }
        composable("explore") { ExploreScreen() }
        composable("you") { YouScreen(nav) }
        composable(
            "conversation/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry -> ConversationScreen(nav, entry.arguments?.getLong("id") ?: 0L) }
        composable("apps") { AppsScreen(nav) }
        composable("import") { ImportScreen(nav) }
        composable("diagnostics") { DiagnosticsScreen(nav) }
        composable("words") { WordsScreen(nav) }
        if (ai.unicorn.keyboard.KeyArea.ENABLED) composable("size") { SizeScreen(nav) }
    }
}

/** The prompt memory, or null before the phone was unlocked after boot (the app only runs after unlock). */
@Composable
fun rememberMemory(): MemoryRepository? {
    val context = LocalContext.current
    return remember { Unicorn.get(context).memory }
}

/** Insights recomputed whenever the memory changes. */
@Composable
fun rememberInsights(memory: MemoryRepository?): MemoryInsights? {
    val revision by (memory?.revision ?: MutableStateFlow(0L)).collectAsState()
    val state by (memory?.state ?: flowOf(MemoryRepository.State.FAILED)).collectAsState(MemoryRepository.State.LOADING)
    val insights by produceState<MemoryInsights?>(null, revision, state) {
        value = if (memory != null && state == MemoryRepository.State.READY) {
            runCatching { MemoryInsightsEngine(memory).compute() }.getOrNull()
        } else if (memory == null || state == MemoryRepository.State.FAILED) {
            MemoryInsights.Empty
        } else {
            value
        }
    }
    return insights
}
