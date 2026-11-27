package com.mortgee.al_mobile.ui.loan

import java.math.BigDecimal

private val LOAN_MONEY_REGEX = Regex("[0-9]+(?:[.][0-9]{1,2})?")

internal fun loanMoney(raw: String): Double? {
    val s = raw.trim()
    if (!LOAN_MONEY_REGEX.matches(s)) {
        return null
    }
    val original = s.toBigDecimalOrNull() ?: return null
    val d = original.toDouble()
    if (!d.isFinite()) {
        return null
    }
    if (BigDecimal.valueOf(d).compareTo(original) != 0) {
        return null
    }
    return d
}
