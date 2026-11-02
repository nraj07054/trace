package com.homeboundlabs.trace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.navigation.compose.currentBackStackEntryAsState
import com.homeboundlabs.trace.ui.components.Tab
import com.homeboundlabs.trace.ui.components.TabBar
import com.homeboundlabs.trace.ui.theme.EmphasizedEasing
import com.homeboundlabs.trace.ui.theme.Motion
import com.homeboundlabs.trace.ui.theme.axisIn
import com.homeboundlabs.trace.ui.theme.sheetIn
import com.homeboundlabs.trace.ui.theme.sheetOut
import com.homeboundlabs.trace.ui.theme.axisOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.homeboundlabs.trace.quickadd.QuickAdd
import com.homeboundlabs.trace.ui.add.AddScreen
import com.homeboundlabs.trace.ui.all.AllExpensesScreen
import com.homeboundlabs.trace.ui.categories.CategoriesScreen
import com.homeboundlabs.trace.ui.home.HomeScreen
import com.homeboundlabs.trace.ui.insights.InsightsScreen
import com.homeboundlabs.trace.ui.onboarding.OnboardingScreen
import com.homeboundlabs.trace.ui.permissions.Permissions
import com.homeboundlabs.trace.ui.permissions.PermissionsScreen
import com.homeboundlabs.trace.ui.settings.SettingsScreen
import com.homeboundlabs.trace.ui.theme.DarkPalette
import com.homeboundlabs.trace.ui.theme.LightPalette
import com.homeboundlabs.trace.ui.theme.ThemeHost
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.isDarkFor
import com.homeboundlabs.trace.ui.theme.TraceTheme

class MainActivity : ComponentActivity() {
    private val addRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Start in the right colors, so opening the app never flashes the other theme.
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        TraceColors.isDark = isDarkFor(traceApp.prefs.themeMode.value, systemDark)
        applySystemBars(TraceColors.isDark)
        if (savedInstanceState == null) handle(intent)
        setContent {
            ThemeHost(onDark = ::applySystemBars) {
                TraceTheme { TraceNav(addRequests.intValue) }
            }
        }
    }

    /** Status/navigation bar icons and the window behind the app, to match light or dark. */
    private fun applySystemBars(dark: Boolean) {
        val clear = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
        enableEdgeToEdge(style, style)
        window.setBackgroundDrawable(ColorDrawable((if (dark) DarkPalette else LightPalette).surface.toArgb()))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ADD, false) == true) addRequests.intValue++
    }

    companion object {
        const val EXTRA_OPEN_ADD = "open_add"
    }
}

@Composable
private fun TraceNav(addRequest: Int) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val app = context.traceApp

    val startOnboarding = remember { !app.prefs.onboarded }
    // Becomes true once onboarding finishes, so the start-up check below runs then too.
    var ready by remember { mutableStateOf(!startOnboarding) }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        QuickAdd.refresh(context)
        // Explain permissions in Trace's own screen first; Android's pop-ups only open from there.
        if (Permissions.shouldShow(context, app.prefs)) nav.navigate("permissions") { launchSingleTop = true }
    }
    LaunchedEffect(addRequest) {
        if (addRequest > 0 && app.prefs.onboarded) nav.navigate("add") { launchSingleTop = true }
    }

    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tab = Tab.of(route)
    // Tabs never stack: each sits right above Home, so Back from Insights or Settings lands on Home.
    fun goTo(t: Tab) {
        if (t == Tab.Home) nav.popBackStack("home", inclusive = false)
        else nav.navigate(t.route) { popUpTo("home"); launchSingleTop = true }
    }
    fun isSheet(route: String?) = route == "add" || route?.startsWith("edit") == true
    // Between tabs the screens move along a shared axis, by their order in the bar, under the fixed bar.
    fun dir(from: String?, to: String?): Int? {
        val a = Tab.of(from) ?: return null
        val b = Tab.of(to) ?: return null
        return if (b.ordinal > a.ordinal) 1 else -1
    }

    Box(Modifier.fillMaxSize()) {
    NavHost(
        nav, startDestination = if (startOnboarding) "onboarding" else "home",
        modifier = Modifier.fillMaxSize().background(TraceColors.Surface),
        // One motion for every sideways move: between tabs by their order in the bar, and into a
        // screen (forward) or back out of it. Add and edit rise over the screen like a sheet, which
        // stays in place underneath.
        enterTransition = {
            axisIn(dir(initialState.destination.route, targetState.destination.route) ?: 1)
        },
        exitTransition = {
            if (isSheet(targetState.destination.route)) ExitTransition.KeepUntilTransitionsFinished
            else axisOut(dir(initialState.destination.route, targetState.destination.route) ?: 1)
        },
        popEnterTransition = {
            if (isSheet(initialState.destination.route)) EnterTransition.None
            else axisIn(dir(initialState.destination.route, targetState.destination.route) ?: -1)
        },
        popExitTransition = {
            axisOut(dir(initialState.destination.route, targetState.destination.route) ?: -1)
        },
    ) {
        composable(
            "onboarding",
            exitTransition = { fadeOut(tween(Motion.STANDARD)) },
        ) {
            OnboardingScreen(onDone = {
                ready = true
                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
            })
        }
        composable(
            "home",
            enterTransition = {
                dir(initialState.destination.route, targetState.destination.route)?.let { d -> axisIn(d) }
                    ?: fadeIn(tween(Motion.STANDARD))
            },
        ) {
            HomeScreen(
                onSettings = { goTo(Tab.Settings) },
                onSeeAll = { nav.navigate("all") { launchSingleTop = true } },
                onEdit = { id -> nav.navigate("edit/$id") { launchSingleTop = true } },
            )
        }
        composable(
            "add",
            enterTransition = { sheetIn() },
            popExitTransition = { sheetOut() },
        ) { AddScreen(onClose = { nav.popBackStack() }) }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() }, onCategories = { nav.navigate("categories") { launchSingleTop = true } })
        }
        composable("permissions") { PermissionsScreen(onDone = { nav.popBackStack() }) }
        composable("categories") { CategoriesScreen(onBack = { nav.popBackStack() }) }
        composable("insights") { InsightsScreen() }
        composable("all") {
            AllExpensesScreen(onBack = { nav.popBackStack() }, onEdit = { id -> nav.navigate("edit/$id") { launchSingleTop = true } })
        }
        composable(
            "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
            enterTransition = { sheetIn() },
            popExitTransition = { sheetOut() },
        ) { entry ->
            AddScreen(onClose = { nav.popBackStack() }, editId = entry.arguments?.getLong("id"))
        }
    }
    // Fixed above the screens on the three tabs; it slides away for everything else.
    AnimatedVisibility(
        visible = tab != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(Motion.STANDARD, easing = EmphasizedEasing)) { it } + fadeIn(tween(Motion.QUICK)),
        exit = slideOutVertically(tween(Motion.STANDARD, easing = EmphasizedEasing)) { it } + fadeOut(tween(Motion.QUICK)),
    ) {
        TabBar(current = tab ?: Tab.Home, onTab = ::goTo, onAdd = { nav.navigate("add") { launchSingleTop = true } })
    }
    }
}

