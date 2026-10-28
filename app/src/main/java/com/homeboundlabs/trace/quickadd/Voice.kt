package com.homeboundlabs.trace.quickadd

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import com.homeboundlabs.trace.data.Category
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Voice input for the quick add box, using only Android's *on-device* speech recogniser:
 * audio never leaves the phone and Trace still has no internet permission. Phones without an
 * on-device English model get no mic at all.
 */
object Voice {
    private val preferred = listOf("en-IN", "en-US", "en-GB")

    /**
     * The language to listen in, or null when this phone can't understand speech offline.
     * Must be called on the main thread (the recogniser is created there).
     */
    suspend fun offlineLanguage(context: Context): String? {
        if (Build.VERSION.SDK_INT < 31) return null
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return null
        // Android 12 can't report which models are installed; assume English is there.
        if (Build.VERSION.SDK_INT < 33) return preferred.first()
        return installedEnglish(context)
    }

    @RequiresApi(33)
    private suspend fun installedEnglish(context: Context): String? = suspendCancellableCoroutine { cont ->
        val sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        cont.invokeOnCancellation { sr.destroy() }
        try {
            sr.checkRecognitionSupport(intent, context.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    val installed = support.installedOnDeviceLanguages.map { it.lowercase() }
                    val pick = preferred.firstOrNull { it.lowercase() in installed }
                        ?: installed.firstOrNull { it.startsWith("en") }
                    sr.destroy()
                    if (cont.isActive) cont.resume(pick)
                }

                override fun onError(error: Int) {
                    sr.destroy()
                    if (cont.isActive) cont.resume(null)
                }
            })
        } catch (_: Exception) {
            sr.destroy()
            if (cont.isActive) cont.resume(null)
        }
    }

    /** One listening session. Callbacks arrive on the main thread. */
    class Session(
        context: Context,
        private val language: String,
        private val biasing: ArrayList<String>,
        private val onLevel: (Float) -> Unit,
        private val onPartial: (String) -> Unit,
        private val onFinal: (String) -> Unit,
        private val onError: (Int) -> Unit,
    ) {
        private val sr: SpeechRecognizer? =
            if (Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else null
        private var finished = false

        fun start() {
            val recognizer = sr ?: return onError(SpeechRecognizer.ERROR_CLIENT)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.firstText()?.let(onPartial)
                }
                override fun onResults(results: Bundle?) {
                    if (finished) return
                    finished = true
                    onFinal(results?.firstText().orEmpty())
                }
                override fun onError(error: Int) {
                    if (finished) return
                    finished = true
                    this@Session.onError(error)
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Android 13+: hint the words Trace expects so "vada pav" isn't heard as "iPad".
            if (Build.VERSION.SDK_INT >= 33 && biasing.isNotEmpty()) intent.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, biasing)
            recognizer.startListening(intent)
        }

        /** Stop listening; the final words still arrive through onFinal. */
        fun stop() = sr?.stopListening()

        /** Throw away whatever was heard. */
        fun cancel() {
            finished = true
            sr?.cancel()
            sr?.destroy()
        }

        fun release() = sr?.destroy()

        private fun Bundle.firstText(): String? =
            getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    /* ---------- understanding what was said ---------- */

    /** What Trace took from a sentence: how much, what it was for, and (if it could tell) the category. */
    data class Heard(val amountMinor: Long, val note: String, val category: Category?)

    // Words that say *what* a spend was, mapped to the built-in category they belong to.
    // Multi-word items ("vada pav", "local train") are matched as phrases first.
    val words: Map<String, List<String>> = mapOf(
        Category.Food.id to listOf(
            "food", "lunch", "dinner", "breakfast", "snack", "snacks", "coffee", "tea", "chai", "vada pav", "vada", "pav bhaji",
            "pav", "samosa", "dosa", "idli", "biryani", "momos", "maggi", "thali", "paratha", "poha", "upma", "sandwich", "pizza",
            "burger", "juice", "lassi", "pani puri", "golgappa", "chaat", "bhel", "sabzi", "vegetables", "fruits", "milk",
            "groceries", "grocery", "kirana", "swiggy", "zomato", "blinkit", "zepto", "instamart", "bigbasket", "restaurant",
            "cafe", "canteen", "mess", "tiffin", "sweets", "ice cream", "cake", "eggs", "bread",
            "khana", "nashta", "doodh", "sabji", "aaiskrim",
            "खाना", "नाश्ता", "चाय", "कॉफी", "कॉफ़ी", "समोसा", "वड़ा पाव", "वडा पाव", "पाव भाजी", "डोसा", "बिरयानी", "मोमोज",
            "मैगी", "थाली", "पराठा", "पोहा", "जूस", "लस्सी", "पानी पूरी", "गोलगप्पे", "चाट", "भेल", "सब्ज़ी", "सब्जी", "फल",
            "दूध", "राशन", "किराना", "आइसक्रीम", "आइस क्रीम", "केक", "मिठाई", "अंडे", "ब्रेड", "पिज़्ज़ा", "पिज्जा", "बर्गर",
        ),
        Category.Travel.id to listOf(
            "travel", "auto", "rickshaw", "cab", "taxi", "uber", "ola", "rapido", "metro", "local train", "train", "bus",
            "ticket", "petrol", "diesel", "fuel", "cng", "toll", "parking", "flight", "station", "airport", "bike", "scooty",
            "ऑटो", "रिक्शा", "कैब", "टैक्सी", "मेट्रो", "लोकल", "ट्रेन", "बस", "टिकट", "पेट्रोल", "डीज़ल", "डीजल", "पार्किंग",
        ),
        Category.Shopping.id to listOf(
            "shopping", "shop", "amazon", "flipkart", "myntra", "meesho", "ajio", "nykaa", "clothes", "shirt", "jeans", "kurta",
            "saree", "shoes", "chappal", "bag", "watch", "phone case", "earphones", "gift",
            "कपड़े", "शर्ट", "जींस", "कुर्ता", "साड़ी", "जूते", "चप्पल", "बैग", "घड़ी", "गिफ्ट",
        ),
        Category.Bills.id to listOf(
            "bill", "bills", "electricity", "light bill", "recharge", "mobile recharge", "wifi", "internet", "broadband", "rent",
            "emi", "gas", "cylinder", "water", "maintenance", "insurance", "dth", "fees", "school fees",
            "बिल", "बिजली", "रिचार्ज", "वाईफाई", "किराया", "गैस", "सिलेंडर", "पानी", "फीस",
        ),
        Category.Fun.id to listOf(
            "fun", "movie", "movies", "cinema", "pvr", "netflix", "hotstar", "spotify", "game", "games", "ride", "rides",
            "park", "concert", "party", "outing", "trip", "bowling",
            "मूवी", "फिल्म", "पिक्चर", "सिनेमा", "पार्टी", "घूमना",
        ),
    )

    // Words people say around an amount that never belong in the note, wherever they appear.
    private val dropAnywhere = setOf(
        "rupees", "rupee", "rs", "inr", "bucks", "paid", "spent", "spend", "pay", "i", "i've", "ive", "add", "added",
        "expense", "worth", "costs", "cost", "costed", "only", "just",
        // Hindi and Hinglish: "35 रुपीस का आइसक्रीम खाया है" → just "आइसक्रीम".
        "रुपये", "रुपए", "रुपया", "रुपीस", "रुपी", "रु", "रूपये", "मैंने", "मैं", "ने", "का", "की", "के", "को", "से",
        "में", "पर", "है", "हैं", "था", "थी", "थे", "खाया", "खाई", "खायी", "खाए", "खाये", "पिया", "पी", "लिया", "ली",
        "लिए", "लिये", "दिया", "दी", "दिए", "दिये", "किया", "गया", "गई", "गयी", "भी", "तो", "बस", "सिर्फ", "वाला",
        "वाली", "वाले", "आज", "अभी", "खर्च", "खरीदा", "खरीदी", "लगे", "लगा",
        "rupaye", "rupay", "rupiya", "maine", "mai", "ka", "ki", "ke", "ko", "se", "mein", "hai", "hain", "tha", "thi",
        "khaya", "khayi", "khaye", "piya", "liya", "liye", "diya", "diye", "kiya", "gaya", "gayi", "bhi", "toh", "bas",
        "sirf", "wala", "wali", "wale", "aaj", "abhi", "kharch", "kharida", "lage", "laga",
    )
    // Little linking words that are fine inside a note ("auto to station") but not at its ends.
    private val trimEnds = setOf("for", "on", "of", "a", "an", "the", "and", "to", "at", "in", "my", "it", "was", "is", "it's")

    /**
     * "for vada pav I paid rupees 50" → ₹50 · "Vada pav" · Food.
     * [history] maps notes you've saved before to the category you used, so Trace learns your words.
     * Null when no amount was heard.
     */
    fun understand(text: String, cats: List<Category>, history: Map<String, String> = emptyMap()): Heard? {
        val spoken = SpokenNumbers.toDigits(text.lowercase().replace("₹", " rupees ").replace(",", " "))
        val match = Regex("""\d+(?:\.\d{1,2})?""").find(spoken) ?: return null
        val minor = com.homeboundlabs.trace.data.Money.parseMinor(match.value)
        if (minor <= 0) return null

        val rest = spoken.removeRange(match.range)
        val tokens = rest.split(Regex("[\\s.!?;:।]+")).map { it.trim('\'', '"') }.filter { it.isNotBlank() }
        val kept = tokens.filterNot { it in dropAnywhere }
            .dropWhile { it in trimEnds }.dropLastWhile { it in trimEnds }
        val phrase = kept.joinToString(" ")
        val note = phrase.replaceFirstChar { it.uppercase() }

        return Heard(minor, note, categoryFor(phrase, cats, history))
    }

    private fun categoryFor(phrase: String, cats: List<Category>, history: Map<String, String>): Category? {
        if (phrase.isBlank()) return null
        fun byId(id: String) = cats.firstOrNull { it.id == id }
        // 1. You've saved this exact thing before: use the category you chose then.
        history[phrase.lowercase()]?.let { id -> byId(id)?.let { return it } }
        val padded = " ${phrase.lowercase()} "
        // 2. You named a category, including ones you created ("50 for pets").
        cats.firstOrNull { c -> " ${c.name.lowercase()} " in padded }?.let { return it }
        // 3. A known item, longest phrase first so "pav bhaji" beats "pav".
        return words.flatMap { (id, list) -> list.map { it to id } }
            .sortedByDescending { it.first.length }
            .firstOrNull { (w, _) -> " $w " in padded }
            ?.let { byId(it.second) }
    }

    /** Phrases to nudge the recogniser towards (Android 13+): amounts, categories, items and your own notes. */
    fun biasing(cats: List<Category>, history: Collection<String>): ArrayList<String> {
        val list = LinkedHashSet<String>()
        list += listOf("rupees", "rupees 50", "for vada pav", "I paid")
        cats.forEach { list += it.name }
        words.values.flatten().forEach { list += it }
        history.forEach { list += it }
        return ArrayList(list.take(200))
    }
}

/** Turns spoken numbers into digits: "fifteen hundred" → 1500, "one twenty" → 120, "two thousand five hundred" → 2500. */
object SpokenNumbers {
    private val small = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )
    private val scales = mapOf("hundred" to 100L, "thousand" to 1_000L, "lakh" to 100_000L, "lakhs" to 100_000L)

    private fun isNumberWord(w: String) = w in small || w in tens || w in scales

    fun toDigits(text: String): String {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            if (!isNumberWord(tokens[i])) { out += tokens[i]; i++; continue }
            // Collect a run of number words (allowing "and" inside: "one hundred and twenty").
            val run = mutableListOf<String>()
            while (i < tokens.size && (isNumberWord(tokens[i]) || (tokens[i] == "and" && run.isNotEmpty() && i + 1 < tokens.size && isNumberWord(tokens[i + 1])))) {
                if (tokens[i] != "and") run += tokens[i]
                i++
            }
            out += value(run).toString()
        }
        return out.joinToString(" ")
    }

    private fun value(run: List<String>): Long {
        // Indian shorthand: "one twenty" = 120, "two fifty" = 250, "four ninety nine" = 499.
        if (run.none { it in scales } && run.size >= 2 && run[0] in small && (small[run[0]] ?: 0) in 1..9) {
            val rest = groupValue(run.drop(1))
            if (rest in 10..99) return (small[run[0]]!! * 100L) + rest
        }
        var total = 0L
        var current = 0L
        for (w in run) {
            when {
                w in small -> current += small[w]!!
                w in tens -> current += tens[w]!!
                w == "hundred" -> current = (if (current == 0L) 1 else current) * 100
                w in scales -> { total += (if (current == 0L) 1 else current) * scales[w]!!; current = 0 }
            }
        }
        return total + current
    }

    private fun groupValue(words: List<String>): Long = words.sumOf { (small[it] ?: tens[it] ?: 0).toLong() }
}
