package com.example.spendsync.data.planify

/**
 * Helps turn a plan's buckets into real categories (so spends can be filed under them). Nothing is created unless
 * the user asks, either with the toggle while building the plan or with the button under a bucket.
 */
object SmartCategories {
    /** Iconify (Material Design Icons) ids by keyword; only well-known names, so a missing icon never happens. */
    private val icons = listOf(
        listOf("eating", "dining", "restaurant", "food", "cafe", "lunch", "dinner") to "mdi:silverware-fork-knife",
        listOf("grocer", "vegetable", "supermarket") to "mdi:cart",
        listOf("rent", "housing", "home", "maintenance") to "mdi:home",
        listOf("emergency", "insurance") to "mdi:shield-check",
        listOf("saving", "invest", "sip", "fund", "goal") to "mdi:piggy-bank",
        listOf("travel", "trip", "flight", "holiday") to "mdi:airplane",
        listOf("gift", "donation", "charity") to "mdi:gift",
        listOf("transport", "fuel", "petrol", "cab", "commute") to "mdi:car",
        listOf("health", "medical", "medicine", "doctor") to "mdi:medical-bag",
        listOf("movie", "entertainment", "netflix", "subscription") to "mdi:movie",
        listOf("phone", "mobile", "recharge") to "mdi:cellphone",
        listOf("school", "education", "course", "tuition") to "mdi:school",
        listOf("shopping", "clothes", "fashion") to "mdi:shopping",
        listOf("bill", "electric", "internet", "wifi", "utilit") to "mdi:lightning-bolt",
        listOf("loan", "emi", "debt") to "mdi:bank",
        listOf("gym", "fitness", "yoga") to "mdi:dumbbell",
    )

    fun iconFor(name: String): String? {
        val n = name.lowercase()
        return icons.firstOrNull { (words, _) -> words.any { n.contains(it) } }?.second
    }

    /** Bucket categories that are not categories yet. "Other" is the app's catch-all and is never created. */
    fun missing(bucketCategories: Collection<String>, known: Collection<String>): List<String> =
        bucketCategories.map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("Other", true) && known.none { k -> k.equals(it, true) } }
            .distinctBy { it.lowercase() }
}
