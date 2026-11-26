package com.homeboundlabs.trace.data

import java.net.URLEncoder

/** UPI payment links, the same ones any UPI app reads from a shop's QR code. No server or gateway is involved. */
object Upi {
    private val VPA = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,63}$")

    /** True for something shaped like a UPI ID, e.g. "name@okaxis". */
    fun isValidId(id: String): Boolean = VPA.matches(id.trim())

    /**
     * `upi://pay?pa=…&pn=…&am=…&cu=INR&tn=…`. The note is kept short: the less data in the QR, the
     * bigger each dot and the easier it scans.
     */
    fun payLink(upiId: String, payeeName: String, amountMinor: Long?, note: String): String = buildString {
        // "@" stays as is: it's legal in a query, and some UPI apps don't decode "%40" in the payee address.
        append("upi://pay?pa=").append(enc(upiId.trim()).replace("%40", "@"))
        val name = payeeName.trim().take(40)
        if (name.isNotEmpty()) append("&pn=").append(enc(name))
        if (amountMinor != null && amountMinor > 0) append("&am=").append("%d.%02d".format(amountMinor / 100, amountMinor % 100))
        append("&cu=INR")
        val tn = note.trim().take(MAX_NOTE)
        if (tn.isNotEmpty()) append("&tn=").append(enc(tn))
    }

    const val MAX_NOTE = 30

    // URLEncoder makes spaces "+", which some UPI apps show literally; %20 reads the same everywhere.
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
