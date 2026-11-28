package com.mortgee.al_mobile.ui.loan

object IrishCounties {
    val NAMES: List<String> = listOf(
        "Antrim",
        "Armagh",
        "Carlow",
        "Cavan",
        "Clare",
        "Cork",
        "Derry",
        "Donegal",
        "Down",
        "Dublin",
        "Fermanagh",
        "Galway",
        "Kerry",
        "Kildare",
        "Kilkenny",
        "Laois",
        "Leitrim",
        "Limerick",
        "Longford",
        "Louth",
        "Mayo",
        "Meath",
        "Monaghan",
        "Offaly",
        "Roscommon",
        "Sligo",
        "Tipperary",
        "Tyrone",
        "Waterford",
        "Westmeath",
        "Wexford",
        "Wicklow",
    )

    val OPTIONS: List<String> = NAMES.map { "co. $it" }

    private val OPTION_SET: Set<String> = OPTIONS.toSet()

    fun search(query: String): List<String> {
        val trimmed = query.trim()
        return NAMES
            .filter { it.contains(trimmed, ignoreCase = true) }
            .map { "co. $it" }
    }

    fun isSelection(value: String): Boolean = value in OPTION_SET
}
