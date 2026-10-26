package app.aurio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import app.aurio.ui.theme.AurioSpacing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aurio.data.AurioStore
import app.aurio.domain.AppCategory
import app.aurio.ui.apps.AppPicker
import app.aurio.ui.onboarding.OnboardingFlow
import app.aurio.ui.focus.FocusRunningScreen
import app.aurio.ui.focus.FocusSetupScreen
import app.aurio.ui.focus.SessionCompleteScreen
import app.aurio.ui.home.HomeScreen
import app.aurio.ui.tabs.InsightsScreen
import app.aurio.ui.tabs.LimitsScreen
import app.aurio.ui.tabs.ProfileScreen
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioType

private enum class Tab(val route: String, val label: String, val path: String) {
    HOME("home", "Home", "M4 10l8-6 8 6v10h-5v-6H9v6H4z"),
    FOCUS("focus", "Focus", "M12 21a8 8 0 1 0 0-16a8 8 0 0 0 0 16zM12 9v4l2 2"),
    LIMITS("limits", "Limits", "M4 6h16M4 12h10M4 18h6"),
    INSIGHTS("insights", "Insights", "M6 20V12M12 20V5M18 20v-6"),
    PROFILE("profile", "Profile", "M12 12a4 4 0 1 0 0-8a4 4 0 0 0 0 8zM4 21c1-4 4-6 8-6s7 2 8 6"),
}

/** Stroke icons built from the same paths as the design prototype (no icon library needed). */
private fun strokeIcon(path: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(AurioColors.Ink), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()

@Composable
fun AurioApp(onOpenUsageSettings: () -> Unit) {
    val state by AurioStore.state.collectAsStateWithLifecycle()
    if (!state.loaded) {
        Box(Modifier.fillMaxSize().background(AurioColors.Paper))
        return
    }
    if (!state.settings.onboardingDone) {
        OnboardingFlow(state.onboardingStep, state.usageAccess, onOpenUsageSettings)
        return
    }
    MainNav(onOpenUsageSettings)
}

@Composable
private fun MainNav(onOpenUsageSettings: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    fun goTab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = AurioColors.Paper,
        bottomBar = {
            if (showBar) NavigationBar(containerColor = AurioColors.Surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { goTab(tab.route) },
                        icon = { Icon(strokeIcon(tab.path), contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = { Text(tab.label, style = AurioType.caption) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AurioColors.Ink, selectedTextColor = AurioColors.Ink,
                            unselectedIconColor = AurioColors.InkSubtle, unselectedTextColor = AurioColors.InkSubtle,
                            indicatorColor = AurioColors.SurfaceSunken,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            NavHost(nav, startDestination = Tab.HOME.route) {
                composable(Tab.HOME.route) {
                    HomeScreen(
                        onStartFocus = { goTab(Tab.FOCUS.route) },
                        onEditLimits = { goTab(Tab.LIMITS.route) },
                        onProfile = { goTab(Tab.PROFILE.route) },
                        onTurnOnAccess = onOpenUsageSettings,
                    )
                }
                composable(Tab.FOCUS.route) { FocusSetupScreen(onStarted = { nav.navigate("focus/run") }) }
                composable(Tab.LIMITS.route) { LimitsScreen(onManageApps = { nav.navigate("apps/edit") }) }
                composable("apps/edit") {
                    val st by AurioStore.state.collectAsStateWithLifecycle()
                    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = AurioSpacing.xl, vertical = AurioSpacing.xxl)) {
                        Text("Manage apps", style = AurioType.display)
                        Spacer(Modifier.height(8.dp))
                        Text("Limited apps are scored against a daily limit. Tap a chosen option again to stop tracking an app.", style = AurioType.body, color = AurioColors.InkMuted)
                        Spacer(Modifier.height(AurioSpacing.lg))
                        AppPicker(
                            initial = st.tracked.mapValues { (_, t) -> t.label to t.category },
                            confirmText = "Save",
                            onConfirm = { chosen -> AurioStore.saveApps(chosen); nav.popBackStack() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                composable(Tab.INSIGHTS.route) { InsightsScreen() }
                composable(Tab.PROFILE.route) { ProfileScreen(onOpenUsageSettings, onDeveloperTools = { nav.navigate("debug") }) }
                composable("debug") { app.aurio.ui.debug.DebugScreen(onBack = { nav.popBackStack() }) }
                composable("focus/run") {
                    FocusRunningScreen(
                        onComplete = { earned -> nav.navigate("focus/done/$earned") { popUpTo(Tab.FOCUS.route) } },
                        onEndEarly = { nav.popBackStack() },
                    )
                }
                composable("focus/done/{earned}", arguments = listOf(navArgument("earned") { type = NavType.IntType })) { e ->
                    SessionCompleteScreen(
                        earned = e.arguments?.getInt("earned") ?: 0,
                        onHome = { goTab(Tab.HOME.route) },
                        onAgain = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
