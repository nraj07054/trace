@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.homeboundlabs.trace.ui.people

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.data.Person
import com.homeboundlabs.trace.data.Upi
import com.homeboundlabs.trace.share.Cards
import com.homeboundlabs.trace.share.PaidBackCard
import com.homeboundlabs.trace.share.QrArt
import com.homeboundlabs.trace.share.RequestCard
import com.homeboundlabs.trace.share.RequestTone
import com.homeboundlabs.trace.share.Share
import com.homeboundlabs.trace.ui.components.ChoiceChip
import com.homeboundlabs.trace.ui.components.Keypad
import com.homeboundlabs.trace.ui.components.PrimaryButton
import com.homeboundlabs.trace.ui.components.SecondaryButton
import com.homeboundlabs.trace.ui.components.TextInput
import com.homeboundlabs.trace.ui.components.dayLabel
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** QR-code glyph for the "Show QR" buttons. */
val QrIcon: ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).addPath(
    pathData = addPathNodes("M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2v2h-2zM18 14h2v2M14 18v2h2M18 18h2v2h-2z"),
    stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
).build()

private enum class Due(val label: String, val days: Long?) { None("No date", null), Week("In a week", 7), TwoWeeks("2 weeks", 14), Month("A month", 30) }

/** "I gave" / "I got": amount, note and an optional reminder date. */
@Composable
internal fun EntrySheet(
    gave: Boolean,
    name: String,
    balance: Long,
    currency: String,
    onSave: (amount: Long, gave: Boolean, note: String, dueAt: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var isGave by rememberSaveable { mutableStateOf(gave) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf(Due.None) }
    val minor = Money.parseMinor(amount)
    // A reminder only makes sense when this entry leaves someone owing: lending, or borrowing.
    val makesDebt = if (isGave) balance >= 0 else balance <= 0
    val color = if (isGave) TraceColors.Negative else TraceColors.Positive

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TraceColors.Sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("I gave $name", isGave) { isGave = true }
                ChoiceChip("I got from $name", !isGave) { isGave = false }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(currency + Money.keypadText(amount, currency), style = ts(44, 800, letterSpacing = -1.5f, tight = true), maxLines = 1)
                    Box(Modifier.padding(start = 3.dp).width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(color))
                }
            }
            TextInput(note, { note = it.take(60) }, "What for? e.g. Movie tickets", cursor = color)
            if (makesDebt) {
                Text(if (isGave) "Remind me to ask for it back" else "Remind me to pay it back", style = ts(13, 600, TraceColors.Muted))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Due.entries.forEach { d -> ChoiceChip(d.label, d == due) { due = d } }
                }
            }
            Keypad(onKey = { amount = Money.press(amount, it) }, keyColor = TraceColors.Key, textColor = TraceColors.Ink, keyHeight = 48)
            PrimaryButton("Save", enabled = minor > 0) {
                val dueAt = due.days?.takeIf { makesDebt }?.let {
                    LocalDate.now().plusDays(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                onSave(minor, isGave, note, dueAt)
            }
        }
    }
}

/** Inline "add your UPI ID" field, shown the first time a QR is needed. */
@Composable
private fun UpiSetup(onSave: (String) -> Unit) {
    var id by rememberSaveable { mutableStateOf("") }
    val valid = Upi.isValidId(id)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TraceColors.InfoBg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Add your UPI ID to put a pay QR on the card", style = ts(14, 700, TraceColors.Info))
        Text("It's saved only on this phone. People scan the QR and their UPI app opens with the amount filled in.", style = ts(12, 500, TraceColors.Muted))
        TextInput(id, { id = it.trim().take(60) }, "yourname@okaxis", capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Email, error = id.isNotEmpty() && !valid)
        PrimaryButton("Save UPI ID", enabled = valid) { onSave(id) }
    }
}

private enum class AmountPick { Full, Half, Custom }

@Composable
internal fun RequestSheet(
    ui: PersonUi,
    person: Person,
    initialTone: String,
    onTone: (String) -> Unit,
    onSaveUpi: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val full = ui.balance.coerceAtLeast(0)
    var tone by rememberSaveable { mutableStateOf(RequestTone.from(initialTone)) }
    var pick by rememberSaveable { mutableStateOf(AmountPick.Full) }
    var custom by rememberSaveable { mutableStateOf(Money.keypadInput(full)) }
    var note by rememberSaveable { mutableStateOf(ui.entries.firstOrNull { it.gave && it.note.isNotBlank() }?.note?.take(Upi.MAX_NOTE) ?: "") }
    val canQr = ui.currency == "₹"
    var withQr by rememberSaveable { mutableStateOf(true) }
    val amount = when (pick) {
        AmountPick.Full -> full
        AmountPick.Half -> full / 2
        AmountPick.Custom -> Money.parseMinor(custom)
    }
    val upi = ui.upiId.takeIf { canQr && withQr && Upi.isValidId(it) }
    val link = upi?.let { Upi.payLink(it, ui.myName, amount, note) }

    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(tone, amount, note, link, ui.entries) {
        bmp = withContext(Dispatchers.Default) {
            Cards(context).request(
                RequestCard(
                    personName = person.name, amountMinor = amount, note = note, dateLabel = dayLabel(LocalDate.now()),
                    tone = tone, upiLink = link, upiId = upi,
                    history = ui.entries.reversed().map { it.signedMinor }, lentMinor = ui.lent, returnedMinor = ui.returned,
                    currency = ui.currency,
                ),
            )
        }
    }

    fun send(whatsApp: Boolean) {
        val image = bmp ?: return
        onTone(tone.id)
        scope.launch {
            val file = Share.save(context, image, "trace-request")
            Share.send(context, file, tone.message(person.name, Money.compact(amount, ui.currency), note, upi), whatsApp)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TraceColors.Sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Request from ${person.name}", style = ts(18, 800))
            CardPreview(bmp, fallbackRatio = if (link != null) 1080f / 1640f else 1080f / 1000f)

            Text("Amount", style = ts(13, 600, TraceColors.Muted))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("All ${Money.compact(full, ui.currency)}", pick == AmountPick.Full) { pick = AmountPick.Full }
                if (full >= 200) ChoiceChip("Half ${Money.compact(full / 2, ui.currency)}", pick == AmountPick.Half) { pick = AmountPick.Half }
                ChoiceChip("Other", pick == AmountPick.Custom) { pick = AmountPick.Custom }
            }
            if (pick == AmountPick.Custom) {
                Text(ui.currency + Money.keypadText(custom, ui.currency), style = ts(32, 800, letterSpacing = -1f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Keypad(onKey = { custom = Money.press(custom, it) }, keyColor = TraceColors.Key, textColor = TraceColors.Ink, keyHeight = 44)
            }

            Text("Message style", style = ts(13, 600, TraceColors.Muted))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { RequestTone.entries.forEach { t -> ChoiceChip(t.label, t == tone) { tone = t } } }

            TextInput(note, { note = it.take(Upi.MAX_NOTE) }, "What for? (shown on the card)")

            if (canQr) {
                if (Upi.isValidId(ui.upiId)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, TraceColors.Border, RoundedCornerShape(16.dp))
                            .tap(label = "Include pay QR") { withQr = !withQr }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Include pay QR", style = ts(15, 700))
                            Text("Pays to ${ui.upiId}", style = ts(12, 500, TraceColors.Muted))
                        }
                        androidx.compose.material3.Switch(
                            checked = withQr, onCheckedChange = null,
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedTrackColor = TraceColors.SwitchOn, checkedThumbColor = TraceColors.SwitchThumb,
                                uncheckedTrackColor = TraceColors.Fill, uncheckedBorderColor = TraceColors.Border, uncheckedThumbColor = TraceColors.Faint,
                            ),
                        )
                    }
                } else {
                    UpiSetup(onSaveUpi)
                }
            }

            PrimaryButton("Share on WhatsApp", enabled = bmp != null && amount > 0, bg = WhatsAppGreen) { send(whatsApp = true) }
            SecondaryButton("Other apps", Modifier.fillMaxWidth()) { send(whatsApp = false) }
        }
    }
}

private val WhatsAppGreen = Color(0xFF1FA855)

@Composable
private fun CardPreview(bmp: Bitmap?, fallbackRatio: Float) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).aspectRatio(bmp?.let { it.width.toFloat() / it.height } ?: fallbackRatio).clip(RoundedCornerShape(22.dp))
            .border(1.dp, TraceColors.Border, RoundedCornerShape(22.dp)).background(TraceColors.Field),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let { Image(it.asImageBitmap(), contentDescription = "Card preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}

/** Full-screen QR at full brightness, for paying in person: the friend scans it straight off this phone. */
@Composable
internal fun QrDialog(ui: PersonUi, person: Person, onSaveUpi: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val amount = ui.balance.coerceAtLeast(0)
    val upi = ui.upiId.takeIf { Upi.isValidId(it) }
    val note = ui.entries.firstOrNull { it.gave && it.note.isNotBlank() }?.note ?: ""
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(upi, amount) {
        qr = upi?.let { id ->
            withContext(Dispatchers.Default) {
                val size = 1000
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { b ->
                    val c = android.graphics.Canvas(b)
                    c.drawColor(android.graphics.Color.WHITE)
                    QrArt.draw(context, c, Upi.payLink(id, ui.myName, amount, note), 0f, 0f, size.toFloat())
                }
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            onDispose { }
        }
        // Always white, whatever the theme: a QR scans best dark-on-light.
        Column(
            Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Text("Pay ${ui.myName.ifBlank { "me" }}", style = ts(16, 600, Color(0xFF6B7080)))
            Text(Money.compact(amount, ui.currency), style = ts(44, 800, Color(0xFF0E0F12), letterSpacing = -1.5f))
            if (qr != null) {
                Image(qr!!.asImageBitmap(), "Payment QR code", Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f))
                Text("Scan with any UPI app", style = ts(15, 700, Color(0xFF0E0F12)))
                Text(upi ?: "", style = ts(13, 600, Color(0xFF6B7080)))
            } else if (upi == null) {
                Box(Modifier.widthIn(max = 420.dp)) { UpiSetup(onSaveUpi) }
            }
            SecondaryButton("Done", Modifier.widthIn(min = 160.dp), color = Color(0xFF0E0F12)) { onDismiss() }
        }
    }
}

/** After paying someone back: a cheerful "paid you back" card to send them. */
@Composable
internal fun PaidBackSheet(name: String, amount: Long, currency: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by rememberSaveable { mutableStateOf("") }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(note) {
        bmp = withContext(Dispatchers.Default) { Cards(context).paidBack(PaidBackCard(name, amount, note, settled = true, currency = currency)) }
    }
    fun send(whatsApp: Boolean) {
        val image = bmp ?: return
        scope.launch {
            val file = Share.save(context, image, "trace-paid-back")
            Share.send(context, file, "Hey $name, paid you back ${Money.compact(amount, currency)} ✅ Thanks!", whatsApp)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TraceColors.Sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Marked as paid back ✅", style = ts(18, 800))
            Text("You're all settled with $name. Let them know?", style = ts(14, 500, TraceColors.Muted))
            CardPreview(bmp, 1080f / 1000f)
            TextInput(note, { note = it.take(40) }, "Add a note (optional)")
            PrimaryButton("Share on WhatsApp", enabled = bmp != null, bg = WhatsAppGreen) { send(true) }
            SecondaryButton("Other apps", Modifier.fillMaxWidth()) { send(false) }
        }
    }
}
