package com.visa.paisa.parse

import java.util.Locale

/**
 * Assigns a category to a parsed transaction.
 *
 * Three layers, in order of authority:
 *  1. A rule you created by overriding a category by hand — always wins, forever.
 *  2. The built-in merchant dictionary below (longest matching keyword wins, so
 *     "amazon prime" beats "amazon").
 *  3. Channel fallbacks (ATM withdrawals, NEFT/IMPS transfers).
 *
 * Anything else returns [Categories.UNCATEGORIZED] so it surfaces in the Review tab.
 */
object Categorizer {

    private val DICTIONARY: List<Pair<String, List<String>>> = listOf(
        "Food & Dining" to listOf(
            "swiggy", "zomato", "eatsure", "faasos", "behrouz", "ovenstory", "dominos", "pizza hut",
            "pizza", "mcdonald", "kfc", "burger king", "burger", "starbucks", "cafe coffee", "ccd",
            "costa coffee", "third wave", "blue tokai", "chaayos", "chai point", "cafe", "coffee",
            "restaurant", "biryani", "bakery", "subway", "barbeque nation", "wow momo", "haldiram",
            "thalappakatti", "paragon", "kitchen", "dhaba", "hotel ", "eatfit", "dineout", "baskin",
            "naturals ice", "ice cream", "juice", "food court", "canteen", "mess ", "tiffin",
        ),
        "Groceries" to listOf(
            "bigbasket", "blinkit", "zepto", "instamart", "dmart", "d mart", "avenue supermart",
            "jiomart", "grofers", "reliance fresh", "reliance smart", "more retail", "spencer",
            "star bazaar", "nature basket", "lulu", "nilgiris", "supermarket", "super market",
            "kirana", "provision", "licious", "freshtohome", "fresh to home", "country delight",
            "milk", "vegetable", "grocer",
        ),
        "Transport" to listOf(
            "uber", "ola ", "olacabs", "rapido", "namma yatri", "bmtc", "ksrtc", "metro rail",
            "metro", "dmrc", "kochi metro", "fastag", "netc", "toll", "parking", "yulu", "bounce",
            "indian oil", "indianoil", "bharat petroleum", "hindustan petroleum", "hpcl", "bpcl",
            "iocl", "petrol", "diesel", "fuel", "nayara", "shell ", "reliance petro", "auto rickshaw",
        ),
        "Bills & Utilities" to listOf(
            "electricity", "kseb", "bescom", "tneb", "mseb", "tata power", "adani electricity",
            "water bill", "water authority", "kwa ", "indane", "hp gas", "bharatgas", "gas agency",
            "airtel", "jio ", "reliance jio", "vodafone", "vi postpaid", "bsnl", "broadband",
            "act fibernet", "hathway", "excitel", "tata play", "dish tv", "d2h", "dth", "recharge",
            "postpaid", "bbps", "municipal", "property tax", "maintenance", "internet",
        ),
        "Entertainment" to listOf(
            "bookmyshow", "pvr", "inox", "cinepolis", "carnival cinema", "cinema", "multiplex",
            "movie", "steam games", "steampowered", "playstation", "xbox", "nintendo", "epic games",
            "google play", "bowling", "wonderla", "amusement", "theme park", "concert", "event",
            "district", "paytm insider",
        ),
        "Subscriptions" to listOf(
            "netflix", "amazon prime", "prime video", "hotstar", "jiohotstar", "disney", "sonyliv",
            "sony liv", "zee5", "spotify", "youtube premium", "google one", "icloud", "apple.com/bill",
            "apple services", "adobe", "microsoft 365", "office 365", "canva", "notion", "openai",
            "chatgpt", "anthropic", "claude.ai", "github", "figma", "dropbox", "audible", "kindle",
            "jiosaavn", "gaana", "wynk", "linkedin premium", "cred ", "subscription",
        ),
        "Shopping" to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "tatacliq",
            "snapdeal", "shoppers stop", "lifestyle store", "westside", "max fashion", "zara",
            "h&m", "uniqlo", "decathlon", "croma", "reliance digital", "vijay sales", "ikea",
            "pepperfry", "urban ladder", "firstcry", "lenskart", "titan", "tanishq", "boat ",
            "one plus", "oneplus", "samsung", "apple store", "bata", "puma", "adidas", "nike",
        ),
        "Health" to listOf(
            "apollo", "pharmeasy", "1mg", "tata 1mg", "netmeds", "medplus", "wellness forever",
            "pharmacy", "medical store", "hospital", "clinic", "diagnostic", "pathology", "lab ",
            "dental", "practo", "cult.fit", "cultfit", "gym", "fitness", "physio", "optical",
            "spectacle", "aster", "kims", "amrita",
        ),
        "Education" to listOf(
            "udemy", "coursera", "edx", "unacademy", "byju", "vedantu", "physics wallah", "testbook",
            "school fee", "college", "university", "tuition", "coaching", "exam fee", "academy",
            "institute", "book store", "book depot", "stationery", "library",
        ),
        "Travel" to listOf(
            "makemytrip", "goibibo", "cleartrip", "yatra", "ixigo", "easemytrip", "irctc", "redbus",
            "abhibus", "indigo", "air india", "spicejet", "vistara", "akasa", "airasia", "emirates",
            "oyo", "airbnb", "booking.com", "agoda", "treebo", "fabhotel", "resort", "homestay",
            "travels", "tours", "passport", "visa fee",
        ),
        "Investments" to listOf(
            "zerodha", "groww", "upstox", "angel one", "angelbroking", "icici direct", "hdfc sec",
            "kotak sec", "kuvera", "coin by zerodha", "mutual fund", "amc ", "nps ", "ppf ",
            "sukanya", "lic of india", "insurance", "policybazaar", "paytm money", "smallcase",
            "indmoney", "coindcx", "wazirx", "binance", "sip debit", "recurring deposit",
        ),
    )

    /** Flattened and sorted longest-first so the most specific keyword wins. */
    private val FLAT: List<Triple<String, String, Int>> = DICTIONARY
        .flatMap { (cat, keys) -> keys.map { Triple(it, cat, it.length) } }
        .sortedByDescending { it.third }

    /**
     * @param userRule category the user previously pinned to this merchant, if any.
     * @return the category, or [Categories.UNCATEGORIZED] when nothing matches confidently.
     */
    fun categorize(
        parsed: ParsedTxn,
        body: String,
        userRule: String? = null,
    ): String {
        if (userRule != null && Categories.isKnown(userRule)) return userRule

        if (parsed.channel == Channel.ATM) return Categories.CASH_ATM

        // Merchant name is the trustworthy signal; match against it first.
        parsed.merchant?.let { m ->
            match(m.lowercase(Locale.ROOT))?.let { return it }
        }

        // Fall back to the whole message, which often carries the merchant in a form the
        // extractor could not isolate cleanly.
        match(body.lowercase(Locale.ROOT))?.let { return it }

        if (parsed.channel == Channel.NET_BANKING) return Categories.TRANSFERS

        return Categories.UNCATEGORIZED
    }

    private fun match(haystack: String): String? =
        FLAT.firstOrNull { haystack.contains(it.first) }?.second
}
