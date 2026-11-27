package com.mortgee.al_mobile.ui.loan

import com.mortgee.al_mobile.data.model.LoanField

data class LoanDraft(
    val values: Map<LoanField, String> = emptyMap(),
    val hasDebt: Boolean? = null,
) {
    operator fun get(f: LoanField): String = values[f].orEmpty()

    fun edit(f: LoanField, text: String): LoanDraft {
        if (f == LoanField.DEBT && hasDebt != true) {
            return this
        }
        return copy(values = values + (f to text))
    }

    fun chooseDebt(yes: Boolean): LoanDraft {
        return if (yes) {
            copy(hasDebt = true)
        } else {
            copy(values = values - LoanField.DEBT, hasDebt = false)
        }
    }
}
