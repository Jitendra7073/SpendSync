package com.example.spendsync.ui.i18n

import com.example.spendsync.R

/** Stored theme value ("System" / "Light" / "Dark") → what the user reads. */
fun themeLabel(mode: String): String = when (mode) {
    "Light" -> tr(R.string.theme_light)
    "Dark" -> tr(R.string.theme_dark)
    else -> tr(R.string.theme_system)
}

/** Stored accent name → what the user reads. */
fun accentLabel(name: String): String = when (name) {
    "Emerald Green" -> tr(R.string.accent_emerald)
    "Kakariki Green" -> tr(R.string.accent_kakariki)
    "Crimson Red" -> tr(R.string.accent_crimson)
    "Coral Orange" -> tr(R.string.accent_coral)
    else -> tr(R.string.accent_brand_blue)
}

/**
 * Category names are saved as data (always the English default or whatever the user typed).
 * The built-in ones are shown in the current language; custom ones appear exactly as typed.
 */
fun categoryLabel(name: String): String = when (name) {
        "Salary" -> tr(R.string.cat_salary)
        "Freelance" -> tr(R.string.cat_freelance)
        "Business" -> tr(R.string.cat_business)
        "Gift" -> tr(R.string.cat_gift)
        "Investment" -> tr(R.string.cat_investment)
        "Food" -> tr(R.string.cat_food)
        "Transport" -> tr(R.string.cat_transport)
        "Shopping" -> tr(R.string.cat_shopping)
        "Housing" -> tr(R.string.cat_housing)
        "Health" -> tr(R.string.cat_health)
        "Education" -> tr(R.string.cat_education)
        "Travel" -> tr(R.string.cat_travel)
        "Bills" -> tr(R.string.cat_bills)
        "Fitness" -> tr(R.string.cat_fitness)
        "Movies" -> tr(R.string.cat_movies)
        "Groceries" -> tr(R.string.cat_groceries)
        "Dining Out" -> tr(R.string.cat_dining_out)
        "Rent" -> tr(R.string.cat_rent)
        "Entertainment" -> tr(R.string.cat_entertainment)
        "Café" -> tr(R.string.cat_caf)
        "Other" -> tr(R.string.cat_other)
    else -> name
}
