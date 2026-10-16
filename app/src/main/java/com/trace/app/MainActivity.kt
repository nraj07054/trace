package com.trace.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trace.app.quickadd.QuickAdd
import com.trace.app.ui.add.AddScreen
import com.trace.app.ui.all.AllExpensesScreen
import com.trace.app.ui.categories.CategoriesScreen
import com.trace.app.ui.home.HomeScreen
import com.trace.app.ui.insights.InsightsScreen
import com.trace.app.ui.onboarding.OnboardingScreen
import com.trace.app.ui.settings.SettingsScreen
import com.trace.app.ui.theme.TraceColors
import com.trace.app.ui.theme.TraceTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val addRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val light = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(light, light)
        if (savedInstanceState == null) handle(intent)
        setContent {
            TraceTheme { TraceNav(addRequests.intValue) }
        }
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

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) app.prefs.setQuickAddEnabled(false)
        app.scope.launch { QuickAdd.refresh(context) }
    }
    val startOnboarding = remember { !app.prefs.onboarded }
    LaunchedEffect(Unit) {
        if (startOnboarding) return@LaunchedEffect // onboarding asks for notifications itself
        if (Build.VERSION.SDK_INT >= 33 && app.prefs.quickAddEnabled.value && !QuickAdd.canPost(context) &&
            !app.prefs.askedNotificationPermission
        ) {
            app.prefs.askedNotificationPermission = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            QuickAdd.refresh(context)
        }
    }
    LaunchedEffect(addRequest) {
        if (addRequest > 0 && app.prefs.onboarded) nav.navigate("add") { launchSingleTop = true }
    }

    NavHost(
        nav, startDestination = if (startOnboarding) "onboarding" else "home",
        modifier = Modifier.fillMaxSize().background(TraceColors.Surface),
        enterTransition = { slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(180)) },
    ) {
        composable(
            "onboarding",
            exitTransition = { fadeOut(tween(350)) },
        ) {
            OnboardingScreen(onDone = {
                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
            })
        }
        composable(
            "home",
            enterTransition = { fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 12 } },
        ) {
            HomeScreen(
                onAdd = { nav.navigate("add") { launchSingleTop = true } },
                onSettings = { nav.navigate("settings") { launchSingleTop = true } },
                onSeeAll = { nav.navigate("all") { launchSingleTop = true } },
                onEdit = { id -> nav.navigate("edit/$id") { launchSingleTop = true } },
                onInsights = { nav.navigate("insights") { launchSingleTop = true } },
            )
        }
        composable(
            "add",
            enterTransition = { slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(200)) },
            popExitTransition = { slideOutVertically(tween(260)) { it / 3 } + fadeOut(tween(200)) },
        ) { AddScreen(onClose = { nav.popBackStack() }) }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() }, onCategories = { nav.navigate("categories") { launchSingleTop = true } })
        }
        composable("categories") { CategoriesScreen(onBack = { nav.popBackStack() }) }
        composable("insights") { InsightsScreen(onBack = { nav.popBackStack() }) }
        composable("all") {
            AllExpensesScreen(onBack = { nav.popBackStack() }, onEdit = { id -> nav.navigate("edit/$id") { launchSingleTop = true } })
        }
        composable(
            "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
            enterTransition = { slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(200)) },
            popExitTransition = { slideOutVertically(tween(260)) { it / 3 } + fadeOut(tween(200)) },
        ) { entry ->
            AddScreen(onClose = { nav.popBackStack() }, editId = entry.arguments?.getLong("id"))
        }
    }
}

