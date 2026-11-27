
package com.mortgee.al_mobile.ui.loan

import com.mortgee.al_mobile.data.model.LoanField
import com.mortgee.al_mobile.data.network.model.CreateLoanRequest
import com.mortgee.al_mobile.data.network.model.LoanPropertyDto
import com.mortgee.al_mobile.ui.form.PropertyFormData
import com.mortgee.al_mobile.ui.form.PropertyFormValidator
import java.math.BigDecimal

private val LOAN_MONEY_REGEX = Regex("[0-9]+(?:[.][0-9]{1,2})?")
private val LOAN_DIGITS_REGEX = Regex("[0-9]+")

private const val MONEY_ERROR = "Enter a non-negative amount in euros."
private const val DEBT_CHOICE_ERROR = "Choose Yes or No."
private const val TERM_ERROR = "Enter a whole number from 1 to 40."
private const val PRICE_ERROR = "Enter an asking price greater than zero."
private const val DEPOSIT_PRICE_ERROR = "Deposit must be less than the asking price."
private const val COUNTY_ERROR = "Enter the county."
private const val BATHS_ERROR = "Enter zero or more whole bathrooms."
private const val YEAR_ERROR = "Enter a build year from 1800 to 2030."
private const val BER_REQUIRED_ERROR = "Choose a BER rating."

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

fun validateProperty(d: LoanDraft): Checked<LoanPropertyDto> {
    val base = PropertyFormValidator.validate(
        PropertyFormData(
            locationInput = d[LoanField.COUNTY],
            sizeSqmInput = d[LoanField.AREA],
            bedroomsInput = d[LoanField.BEDS],
            propertyTypeInput = d[LoanField.TYPE],
            berRatingInput = d[LoanField.BER],
        ),
    )

    val errors = linkedMapOf<LoanField, String>()

    val county = base.parsedLocation
    if (!base.isLocationValid || county == null) {
        errors[LoanField.COUNTY] = COUNTY_ERROR
    }

    val area = base.parsedSizeSqm
    if (!base.isSizeValid || area == null) {
        errors[LoanField.AREA] = base.sizeError ?: "Property size is required"
    }

    val bedrooms = base.parsedBedrooms
    if (!base.isBedroomsValid || bedrooms == null) {
        errors[LoanField.BEDS] = base.bedroomsError ?: "Number of bedrooms is required"
    }

    val bathrooms = d[LoanField.BATHS].trim().toIntOrNull()?.takeIf { it >= 0 }
    if (bathrooms == null) {
        errors[LoanField.BATHS] = BATHS_ERROR
    }

    val yearBuilt = d[LoanField.YEAR].trim().toIntOrNull()?.takeIf { it in 1800..2030 }
    if (yearBuilt == null) {
        errors[LoanField.YEAR] = YEAR_ERROR
    }

    val propertyType = base.parsedPropertyType
    if (!base.isPropertyTypeValid || propertyType == null) {
        errors[LoanField.TYPE] = base.propertyTypeError ?: "Property type is required"
    }

    val berRating = base.parsedBerRating
    val berError = base.berRatingError ?: if (berRating == null) BER_REQUIRED_ERROR else null
    if (berError != null) {
        errors[LoanField.BER] = berError
    }

    if (
        errors.isNotEmpty() ||
        county == null ||
        area == null ||
        bedrooms == null ||
        bathrooms == null ||
        yearBuilt == null ||
        propertyType == null ||
        berRating == null
    ) {
        return Checked(value = null, errors = errors)
    }

    return Checked(
        value = LoanPropertyDto(
            county = county,
            floorArea = area,
            bedrooms = bedrooms,
            bathrooms = bathrooms,
            yearBuilt = yearBuilt,
            propertyType = propertyType,
            berRating = berRating,
        ),
        errors = emptyMap(),
    )
}

fun prepareLoan(d: LoanDraft): Checked<CreateLoanRequest> {
    val finances = validateFinances(d)
    val property = validateProperty(d)
    val combined = linkedMapOf<LoanField, String>().apply {
        putAll(finances.errors)
        putAll(property.errors)
    }

    val f = finances.value
    val p = property.value
    if (combined.isNotEmpty() || f == null || p == null) {
        return Checked(value = null, errors = combined)
    }

    return Checked(
        value = CreateLoanRequest(
            householdAnnualGrossIncome = f.income,
            existingDebtLiabilities = f.debt,
            deposit = f.deposit,
            requestedMortgageTerm = f.term,
            sellerAskingPrice = f.price,
            property = p,
        ),
        errors = emptyMap(),
    )
}


