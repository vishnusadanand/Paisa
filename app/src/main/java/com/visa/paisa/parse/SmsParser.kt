package com.visa.paisa.parse

import java.util.Locale

enum class Direction { DEBIT, CREDIT }

enum class Channel { UPI, CREDIT_CARD, DEBIT_CARD, ATM, NET_BANKING, WALLET, BANK }

data class ParsedTxn(
    val amount: Double,
    val direction: Direction,
    val merchant: String?,
    val merchantKey: String?,
    val accountTail: String?,
    val refNo: String?,
    val channel: Channel,
    val bank: String?,
)

/**
 * Turns an Indian bank / card / wallet SMS alert into a structured transaction.
 *
 * Design notes:
 *  - Everything is content-driven, not sender-driven. Sender IDs (AD-HDFCBK, JM-ICICIB…)
 *    vary by operator and circle, so they are used only as a hint for the bank name.
 *  - The parser is deliberately conservative: if it cannot find BOTH an amount and a
 *    direction it returns null and the message is ignored entirely. A missed transaction
 *    you can add by hand; a phantom one poisons the charts.
 *  - Balance and credit-limit figures are stripped before the amount is read, otherwise
 *    "Avl Bal Rs.42,310" gets logged as a Rs.42,310 expense.
 */
object SmsParser {

    // ---------------------------------------------------------------- rejection filters

    private val OTP_RX = Regex(
        """\b(otp|o\.t\.p|one[ -]?time\s*(?:password|passcode|pin)|verification code|do not share|never share|is your (?:code|pin))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Statements, reminders, failures, mandates — real messages, but not a spend event. */
    private val NON_EVENT_RX = Regex(
        """\b(total amount due|min(?:imum)?\s*(?:amt|amount)\s*due|payment is due|due on|due date|statement (?:is|for)|e-?statement|will be debited|is scheduled|scheduled for|has been declined|declined|failed|unsuccessful|not processed|insufficient (?:balance|funds)|has been blocked|request (?:has been )?(?:received|registered)|mandate (?:registered|created)|standing instruction|converted (?:in)?to emi)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Marketing copy. Kept narrow on purpose — these phrases never appear in a real
     * transaction alert, whereas words like "cashback" and "offer" genuinely do.
     */
    private val PROMO_RX = Regex(
        """\b(shop now|order now|book now|apply now|click here|hurry|limited (?:period|time)|t&c apply|terms and conditions apply|lucky draw|refer a friend|download the app|pre-?approved|instant loan|% ?off\b)""",
        RegexOption.IGNORE_CASE,
    )

    // ------------------------------------------------------------------- noise stripping

    /** Removes "Avl Bal Rs.X", "Available limit INR X", "Clear bal 1,234.00" and friends. */
    private val BALANCE_RX = Regex(
        """(?:avl\.?\s*bal(?:ance)?|avail(?:able)?\.?\s*bal(?:ance)?|a/?c\s*bal(?:ance)?|clear\s*bal(?:ance)?|clg\s*bal|closing\s*bal(?:ance)?|bal(?:ance)?\s*(?:is|:)|avl\.?\s*(?:credit\s*)?lmt|avail(?:able)?\s*(?:credit\s*)?limit|total\s*limit|cash\s*limit)\s*(?:is|of|:)?\s*(?:rs\.?|inr|₹)?\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?""",
        RegexOption.IGNORE_CASE,
    )

    /** "credit card" / "credit limit" must not be read as a credit (money-in) event. */
    private val CREDIT_WORD_NOISE_RX = Regex(
        """credit\s*(?:card|limit|lmt)""",
        RegexOption.IGNORE_CASE,
    )

    // ----------------------------------------------------------------------- amount

    // Two alternatives, lakh-grouped first. The comma group is "+" not "*" on purpose:
    // with "*" the engine matches "125" out of "1250.00" and logs a wrong amount.
    private const val NUM = """[0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?"""

    private val AMOUNT_PREFIX_RX = Regex("""(?:rs\.?|inr|₹)\s*\.?\s*($NUM)""", RegexOption.IGNORE_CASE)
    private val AMOUNT_SUFFIX_RX = Regex("""($NUM)\s*(?:rs\.?|inr|rupees)\b""", RegexOption.IGNORE_CASE)

    /** SBI-style "debited by 150.0" carries no currency marker at all. */
    private val AMOUNT_BARE_RX = Regex(
        """\b(?:debited|credited|spent|paid|sent|withdrawn)\s*(?:by|for|of|with)\s*($NUM)\b""",
        RegexOption.IGNORE_CASE,
    )

    // --------------------------------------------------------------------- direction

    private val DEBIT_RX = Regex(
        """\b(debited|debit|spent|sent|paid|payment of|withdrawn|withdrawal|purchase|deducted|charged|utilised|used at|transferred to|trf to)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val CREDIT_RX = Regex(
        """\b(credited|received|refund(?:ed)?|reversed|reversal|cashback|deposited|added to)\b""",
        RegexOption.IGNORE_CASE,
    )

    // ------------------------------------------------------------------- identifiers

    private val TAIL_RX = Regex(
        """(?:a/?c|acct|account|card|ac no)\s*(?:no\.?|number)?\s*(?:ending\s*(?:with|in)?)?\s*(?:[xX*]{1,}|xx)?\s*(\d{3,6})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val REF_RX = Regex(
        """\b(?:ref(?:erence)?(?:\s*(?:no|number|id))?|rrn|utr|txn(?:\s*(?:no|id))?|transaction id)\.?\s*[:#-]?\s*([A-Za-z0-9]{6,25})\b""",
        RegexOption.IGNORE_CASE,
    )

    // -------------------------------------------------------------------- merchant

    private val UPI_PATH_RX = Regex("""\bUPI[/:-]([A-Za-z0-9@._/\- ]{2,60})""", RegexOption.IGNORE_CASE)
    private val VPA_RX = Regex("""\b([A-Za-z0-9][A-Za-z0-9._\-]{1,40})@([A-Za-z]{2,15})\b""")

    private const val STOP = """on|dated|ref|refno|ref no|upi|via|using|thru|through|avl|a/?c|not you|txn|info|for|with|rs|inr|dt|date|your|call|sms|to block|-"""

    private val MERCHANT_RX = listOf(
        Regex("""\b(?:info|remarks?|narration|desc(?:ription)?)\s*[:\-]\s*(.{2,45}?)(?=[.;|]|${'$'})""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:trf|transferred|sent|paid|payment|transfer)\s+to\s+(.{2,45}?)(?=\s+(?:$STOP)\b|[.;,|]|${'$'})""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+(.{2,45}?)(?=\s+(?:$STOP)\b|[.;,|]|${'$'})""", RegexOption.IGNORE_CASE),
        Regex("""\btowards\s+(.{2,45}?)(?=\s+(?:$STOP)\b|[.;,|]|${'$'})""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+(.{2,45}?)(?=\s+(?:$STOP)\b|[.;,|]|${'$'})""", RegexOption.IGNORE_CASE),
    )

    /** Words that are never a merchant, however they show up in the sentence. */
    private val MERCHANT_BLOCKLIST = setOf(
        "your", "you", "a", "an", "the", "vpa", "upi", "bank", "account", "acct", "card",
        "credit card", "debit card", "your account", "your a/c", "your card", "merchant",
        "beneficiary", "payee", "self", "customer", "user", "avl bal", "linked account",
    )

    private val BANK_HINTS = listOf(
        "HDFC" to listOf("hdfc"),
        "ICICI" to listOf("icici"),
        "SBI" to listOf("sbi", "state bank"),
        "Axis" to listOf("axis"),
        "Kotak" to listOf("kotak"),
        "Federal" to listOf("federal", "fedbnk"),
        "IDFC" to listOf("idfc"),
        "IndusInd" to listOf("indusind", "indusb"),
        "Yes Bank" to listOf("yesbnk", "yes bank"),
        "Canara" to listOf("canara", "canbnk"),
        "PNB" to listOf("pnb", "punjab national"),
        "BoB" to listOf("bob", "bank of baroda"),
        "Union" to listOf("unionbk", "union bank"),
        "South Indian Bank" to listOf("sib", "south indian"),
        "CSB" to listOf("csbbnk", "catholic syrian"),
        "Paytm" to listOf("paytm"),
        "PhonePe" to listOf("phonepe"),
        "Google Pay" to listOf("gpay", "google pay"),
        "Amazon Pay" to listOf("amazon pay", "amzpay"),
        "AmEx" to listOf("amex", "american express"),
        "RBL" to listOf("rbl"),
        "AU Bank" to listOf("au bank", "aubank"),
    )

    // ------------------------------------------------------------------------- API

    /**
     * @return a [ParsedTxn], or null if this message is not a completed money movement.
     */
    fun parse(body: String, sender: String?): ParsedTxn? {
        if (body.isBlank()) return null
        if (OTP_RX.containsMatchIn(body)) return null
        if (NON_EVENT_RX.containsMatchIn(body)) return null
        if (PROMO_RX.containsMatchIn(body)) return null

        val cleaned = BALANCE_RX.replace(body, " ")

        val amount = extractAmount(cleaned) ?: return null
        if (amount <= 0.0) return null

        val direction = extractDirection(cleaned) ?: return null

        val channel = extractChannel(body)
        val accountTail = TAIL_RX.find(cleaned)?.groupValues?.get(1)
        val refNo = REF_RX.find(body)?.groupValues?.get(1)

        // A genuine alert always identifies what it is about: an account or card tail, a
        // reference number, or a UPI handle. Marketing SMS never do — and "Get Rs.500
        // cashback on your first order!" otherwise parses as ₹500 of income.
        val identified = accountTail != null || refNo != null ||
            channel == Channel.UPI || channel == Channel.WALLET || channel == Channel.ATM
        if (!identified) return null

        val merchant = if (channel == Channel.ATM) null else extractMerchant(cleaned)

        return ParsedTxn(
            amount = amount,
            direction = direction,
            merchant = merchant,
            merchantKey = merchant?.let { normalizeKey(it) },
            accountTail = accountTail,
            refNo = refNo,
            channel = channel,
            bank = extractBank(body, sender),
        )
    }

    // -------------------------------------------------------------------- internals

    private fun extractAmount(text: String): Double? {
        val raw = AMOUNT_PREFIX_RX.find(text)?.groupValues?.get(1)
            ?: AMOUNT_SUFFIX_RX.find(text)?.groupValues?.get(1)
            ?: AMOUNT_BARE_RX.find(text)?.groupValues?.get(1)
            ?: return null
        return raw.replace(",", "").toDoubleOrNull()
    }

    private fun extractDirection(text: String): Direction? {
        // "credit card" is not a credit event.
        val masked = CREDIT_WORD_NOISE_RX.replace(text, "cardnoise")
        val debit = DEBIT_RX.find(masked)
        val credit = CREDIT_RX.find(masked)
        return when {
            debit != null && credit != null -> if (debit.range.first <= credit.range.first) Direction.DEBIT else Direction.CREDIT
            debit != null -> Direction.DEBIT
            credit != null -> Direction.CREDIT
            else -> null
        }
    }

    private fun extractChannel(body: String): Channel {
        val t = body.lowercase(Locale.ROOT)
        return when {
            t.contains("atm") || t.contains("cash wdl") || t.contains("cash withdrawal") -> Channel.ATM
            t.contains("credit card") || t.contains("cc ending") -> Channel.CREDIT_CARD
            t.contains("upi") || VPA_RX.containsMatchIn(body) -> Channel.UPI
            t.contains("debit card") -> Channel.DEBIT_CARD
            t.contains("neft") || t.contains("imps") || t.contains("rtgs") -> Channel.NET_BANKING
            t.contains("wallet") || t.contains("paytm") || t.contains("phonepe") -> Channel.WALLET
            else -> Channel.BANK
        }
    }

    private fun extractBank(body: String, sender: String?): String? {
        val haystack = ((sender ?: "") + " " + body).lowercase(Locale.ROOT)
        return BANK_HINTS.firstOrNull { (_, keys) -> keys.any { haystack.contains(it) } }?.first
    }

    private fun extractMerchant(text: String): String? {
        // 1. A UPI path like UPI/P2M/431203/BLINKIT — the payee is the last named segment.
        UPI_PATH_RX.find(text)?.groupValues?.get(1)?.let { path ->
            val segment = path.split("/", " ")
                .map { it.trim() }
                .lastOrNull { it.length >= 3 && !it.all { c -> c.isDigit() } && it.lowercase(Locale.ROOT) !in setOf("p2m", "p2a", "p2p") }
            clean(segment)?.let { return it }
        }
        // 2. A VPA like swiggy@axl or 9846xxxxx@ybl.
        VPA_RX.find(text)?.groupValues?.get(1)?.let { local ->
            if (!local.all { it.isDigit() }) clean(local)?.let { return it }
        }
        // 3. Positional phrases.
        for (rx in MERCHANT_RX) {
            val candidate = rx.find(text)?.groupValues?.get(1)
            clean(candidate)?.let { return it }
        }
        return null
    }

    /** "A/c XX9988", "Card xx1234" — an account reference, never a payee name. */
    private val ACCOUNT_REF_RX = Regex("""^[a-zA-Z]{0,4}[\s./]*[xX*]*\s*\d{3,}$""")

    private fun clean(raw: String?): String? {
        if (raw == null) return null
        var s = raw.trim()
        if (ACCOUNT_REF_RX.matches(s)) return null
        if (s.contains("@")) s = s.substringBefore("@")
        if (s.contains("/")) s = s.split("/").lastOrNull { seg -> seg.any { it.isLetter() } } ?: s
        s = s.trim().trim('.', ',', ':', ';', '-', '_', '*', '"', '\'', '(', ')')
        s = s.replace(Regex("""\s+"""), " ").trim()
        // Drop trailing dangling words the lookahead could not catch.
        s = s.removeSuffix(" on").removeSuffix(" ref").removeSuffix(" via").trim()
        if (s.length < 2 || s.length > 45) return null
        if (s.none { it.isLetter() }) return null
        if (s.lowercase(Locale.ROOT) in MERCHANT_BLOCKLIST) return null
        // Reject fragments that are mostly digits (account numbers, reference ids).
        val letters = s.count { it.isLetter() }
        if (letters < 2) return null
        if (ACCOUNT_REF_RX.matches(s)) return null
        return prettify(s)
    }

    /** ALLCAPS merchant names read badly in a list; Title Case them, leave mixed case alone. */
    private fun prettify(s: String): String {
        if (s != s.uppercase(Locale.ROOT)) return s
        return s.split(" ").joinToString(" ") { w ->
            if (w.length <= 2) w else w.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
        }
    }

    /** Stable key used for dedupe and for remembering the user's category overrides. */
    fun normalizeKey(merchant: String): String =
        merchant.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "").take(40)
}
