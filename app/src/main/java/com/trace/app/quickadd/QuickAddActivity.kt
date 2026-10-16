package com.trace.app.quickadd

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.trace.app.MainActivity
import com.trace.app.data.Categories
import com.trace.app.data.Category
import com.trace.app.data.Money
import com.trace.app.traceApp
import com.trace.app.ui.components.Keypad
import com.trace.app.ui.components.TIcon
import com.trace.app.ui.components.TraceIcons
import com.trace.app.ui.components.tap
import com.trace.app.ui.theme.SpringEasing
import com.trace.app.ui.theme.TraceTheme
import com.trace.app.ui.theme.ts
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The lock-screen quick-add from Main.dc.html. It is shown over the keyguard (showWhenLocked),
 * so it can be opened from the lock-screen notification without unlocking.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))

        // Reaching here while the keyguard is up proves lock-screen add works on this phone.
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) traceApp.prefs.lockScreenVerified = true

        val initial = intent.getStringExtra(QuickAdd.EXTRA_CATEGORY)?.let(Category::of)
        setContent { TraceTheme { QuickAddScreen(initial, onClose = ::finish, onOpenApp = ::openApp) } }
    }

    /** Opening the full app needs the device unlocked; ask the keyguard first. */
    private fun openApp() {
        val km = getSystemService(KeyguardManager::class.java)
        val launch = {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = launch()
            })
        } else launch()
    }
}

private val CardBg = Color(0xFF000000)
private val Raised = Color(0xFF1E2128)
private val Sub = Color(0xFFA1A6B3)
private val Dim = Color(0xFF4A4F5C)
private val Inactive = Color(0xFF8A8F9C)

@Composable
private fun QuickAddScreen(initial: Category?, onClose: () -> Unit, onOpenApp: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.traceApp
    val currency = app.prefs.currency.value
    val scope = rememberCoroutineScope()
    var amount by rememberSaveable { mutableStateOf("") }
    // Saved by id: categories are data now, and the id survives rotation/process death.
    var catId by rememberSaveable { mutableStateOf((initial ?: app.prefs.quickCategory).id) }
    val categories by Categories.all.collectAsState()
    val cat = categories.firstOrNull { it.id == catId } ?: Category.of(catId)
    var done by rememberSaveable { mutableStateOf(false) }
    var savedMinor by rememberSaveable { mutableLongStateOf(0L) }
    var todayTotal by rememberSaveable { mutableLongStateOf(0L) }
    val minor = Money.parseMinor(amount)

    LaunchedEffect(done) { todayTotal = app.repo.todayTotal() }

    fun save() {
        if (minor <= 0) return
        scope.launch {
            app.prefs.quickCategory = cat
            app.repo.add(minor, cat, "", System.currentTimeMillis())
            savedMinor = minor
            todayTotal = app.repo.todayTotal()
            done = true
            QuickAdd.refresh(app) // the card itself confirms; keep the shortcut's text unchanged
        }
    }

    // Auto-dismiss a few seconds after saving, back to the real lock screen.
    LaunchedEffect(done) {
        if (done) {
            kotlinx.coroutines.delay(4000)
            onClose()
        }
    }

    // Transparent window: the real lock screen stays visible; only the box floats on top.
    // Tapping anywhere outside the box closes it.
    Box(
        Modifier.fillMaxSize()
            .clickable(interactionSource = null, indication = null, onClick = onClose)
            .statusBarsPadding(),
    ) {
        AnimatedContent(
            targetState = done,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(150)) },
            label = "card",
        ) { isDone ->
            if (isDone) {
                DoneCard(cat, savedMinor, todayTotal, currency, onOpenApp) {
                    amount = ""; done = false
                }
            } else {
                EditCard(
                    amount, cat, currency, minor > 0,
                    onPick = { catId = it.id },
                    categories = categories,
                    onKey = { amount = Money.press(amount, it).take(7) },
                    onSave = ::save, onClose = onClose,
                )
            }
        }
    }
}

@Composable
private fun EditCard(
    amount: String,
    cat: Category,
    currency: String,
    hasAmount: Boolean,
    onPick: (Category) -> Unit,
    categories: List<Category>,
    onKey: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.padding(12.dp).fillMaxWidth()
            .shadow(34.dp, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp)).background(CardBg)
            .clickable(interactionSource = null, indication = null) { } // taps inside never close it
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight(800))) { append("Trace") }
                    append(" · ADD EXPENSE")
                },
                style = ts(12, 600, Sub, 0.3f),
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape).tap(label = "Cancel", onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Raised), contentAlignment = Alignment.Center) {
                    TIcon(TraceIcons.Close, Sub, 12.dp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    currency + Money.keypadText(amount, currency),
                    style = ts(40, 800, if (hasAmount) Color.White else Dim, -1.5f, tight = true),
                    maxLines = 1,
                )
                Spacer(Modifier.width(3.dp))
                Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(cat.color))
            }
            val saveBg by animateColorAsState(if (hasAmount) cat.color else Color(0xFF2A2D35), label = "save")
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(saveBg).tap(label = "Save expense", onClick = onSave),
                contentAlignment = Alignment.Center,
            ) { TIcon(TraceIcons.Check, Color.White, 22.dp, contentDescription = "Save expense") }
        }
        // Scrolls sideways once there are more categories than fit.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { c ->
                val on = c.id == cat.id
                val bg by animateColorAsState(if (on) Raised else Color.Transparent, label = "bg")
                val scale by animateFloatAsState(if (on) 1.08f else 1f, tween(300, easing = SpringEasing), label = "scale")
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                        .tap(role = Role.RadioButton, label = c.label) { onPick(c) }
                        .padding(top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.size(38.dp).scale(scale).clip(CircleShape)
                            .background(if (on) c.color else c.color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) { TIcon(TraceIcons.category(c), if (on) Color.White else c.color, 18.dp) }
                    Text(c.label, style = ts(11, 600, if (on) Color.White else Inactive), maxLines = 1)
                }
            }
        }
        Keypad(
            onKey = onKey,
            keyColor = Color.White.copy(alpha = 0.1f),
            textColor = Color.White,
            keyHeight = 50, fontSize = 22, fontWeight = 600,
        )
    }
}

@Composable
private fun DoneCard(cat: Category, minor: Long, today: Long, currency: String, onOpen: () -> Unit, onAnother: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth().shadow(30.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp)).background(CardBg)
                .tap(label = "Expense saved — open app", onClick = onOpen)
                .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(cat.color), contentAlignment = Alignment.Center) {
                TIcon(TraceIcons.Check, Color.White, 20.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Added to ${cat.label}", style = ts(15, 700, Color.White))
                Text("Today ${Money.whole(today, currency)} · tap to open", style = ts(12, 400, Sub), maxLines = 1)
            }
            Text("−" + Money.compact(minor, currency), style = ts(22, 800, Color.White, -0.5f), maxLines = 1)
        }
        Box(
            Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f))
                .tap(label = "Add another", onClick = onAnother).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Add another", style = ts(12, 600, Color(0xFFD5D8E0))) }
    }
}
