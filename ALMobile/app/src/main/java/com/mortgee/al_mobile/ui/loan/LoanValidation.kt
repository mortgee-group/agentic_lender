
package com.mortgee.al_mobile.ui.loan

import com.mortgee.al_mobile.data.model.LoanField
import java.math.BigDecimal

private val LOAN_MONEY_REGEX = Regex("[0-9]+(?:[.][0-9]{1,2})?")
private val LOAN_DIGITS_REGEX = Regex("[0-9]+")

private const val MONEY_ERROR = "Enter a non-negative amount in euros."
private const val DEBT_CHOICE_ERROR = "Choose Yes or No."
private const val TERM_ERROR = "Enter a whole number from 1 to 40."
private const val PRICE_ERROR = "Enter an asking price greater than zero."
private const val DEPOSIT_PRICE_ERROR = "Deposit must be less than the asking price."

data class Checked<T>(
    val value: T?,
    val errors: Map<LoanField, String>,
)

data class LoanFinances(
    val income: Double,
    val debt: Double,
    val deposit: Double,
    val term: Int,
    val price: Double,
)

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

fun validateFinances(d: LoanDraft): Checked<LoanFinances> {
    val errors = linkedMapOf<LoanField, String>()

    val income = loanMoney(d[LoanField.INCOME])
    if (income == null) {
        errors[LoanField.INCOME] = MONEY_ERROR
    }

    val debt: Double? = when (d.hasDebt) {
        null -> {
            errors[LoanField.DEBT] = DEBT_CHOICE_ERROR
            null
        }
        false -> 0.0
        true -> {
            val parsedDebt = loanMoney(d[LoanField.DEBT])
            if (parsedDebt == null) {
                errors[LoanField.DEBT] = MONEY_ERROR
            }
            parsedDebt
        }
    }

    val deposit = loanMoney(d[LoanField.DEPOSIT])
    if (deposit == null) {
        errors[LoanField.DEPOSIT] = MONEY_ERROR
    }

    val termRaw = d[LoanField.TERM].trim()
    val term = if (LOAN_DIGITS_REGEX.matches(termRaw)) {
        termRaw.toIntOrNull()?.takeIf { it in 1..40 }
    } else {
        null
    }
    if (term == null) {
        errors[LoanField.TERM] = TERM_ERROR
    }

    val price = loanMoney(d[LoanField.PRICE])?.takeIf { it > 0.0 }
    if (price == null) {
        errors[LoanField.PRICE] = PRICE_ERROR
    }

    if (deposit != null && price != null && deposit >= price) {
        errors[LoanField.DEPOSIT] = DEPOSIT_PRICE_ERROR
    }

    if (errors.isNotEmpty() || income == null || debt == null || deposit == null || term == null || price == null) {
        return Checked(value = null, errors = errors)
    }

    return Checked(
        value = LoanFinances(
            income = income,
            debt = debt,
            deposit = deposit,
            term = term,
            price = price,
        ),
        errors = emptyMap(),
    )
}

