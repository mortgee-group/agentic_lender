package com.mortgee.al_mobile.ui.form

data class PropertyFormData(
    val locationInput: String = "",
    val sizeSqmInput: String = "",
    val bedroomsInput: String = "",
    val propertyTypeInput: String = "",
    val berRatingInput: String = ""
)

data class PropertyFormValidationResult(
    val isLocationValid: Boolean,
    val locationError: String? = null,
    val isSizeValid: Boolean,
    val sizeError: String? = null,
    val isBedroomsValid: Boolean,
    val bedroomsError: String? = null,
    val isPropertyTypeValid: Boolean,
    val propertyTypeError: String? = null,
    val isBerRatingValid: Boolean,
    val berRatingError: String? = null,
    val parsedLocation: String? = null,
    val parsedSizeSqm: Double? = null,
    val parsedBedrooms: Int? = null,
    val parsedPropertyType: String? = null,
    val parsedBerRating: String? = null
) {
    val isValid: Boolean
        get() = isLocationValid && isSizeValid && isBedroomsValid && isPropertyTypeValid && isBerRatingValid
}

object PropertyFormValidator {

    val VALID_PROPERTY_TYPES = setOf(
        "APARTMENT", "DETACHED", "SEMI_DETACHED", "TERRACED",
        "BUNGALOW", "TOWN_HOUSE", "COMMERCIAL", "OTHER"
    )

    val VALID_BER_RATINGS = setOf(
        "A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3",
        "D1", "D2", "E1", "E2", "F", "G", "EXEMPT"
    )

    fun validate(data: PropertyFormData): PropertyFormValidationResult {
        // 1. Location
        val locationTrimmed = data.locationInput.trim()
        val isLocationValid = locationTrimmed.isNotBlank()
        val locationError = if (!isLocationValid) "Location is required" else null

        // 2. Property size (sqm)
        val sizeTrimmed = data.sizeSqmInput.trim()
        var sizeError: String? = null
        var parsedSize: Double? = null
        val isSizeValid: Boolean
        if (sizeTrimmed.isBlank()) {
            isSizeValid = false
            sizeError = "Property size is required"
        } else {
            val sizeAsDouble = sizeTrimmed.toDoubleOrNull()
            if (sizeAsDouble == null || !sizeAsDouble.isFinite() || sizeAsDouble <= 0) {
                isSizeValid = false
                sizeError = "Size must be a positive number in square metres"
            } else {
                isSizeValid = true
                parsedSize = sizeAsDouble
            }
        }

        // 3. Bedrooms (0 = Studio)
        val bedsTrimmed = data.bedroomsInput.trim()
        var bedsError: String? = null
        var parsedBeds: Int? = null
        val isBedsValid: Boolean
        if (bedsTrimmed.isBlank()) {
            isBedsValid = false
            bedsError = "Number of bedrooms is required"
        } else {
            val bedsAsInt = bedsTrimmed.toIntOrNull()
            if (bedsAsInt == null || bedsAsInt < 0) {
                isBedsValid = false
                bedsError = "Bedrooms must be 0 (for Studio) or a positive whole number"
            } else {
                isBedsValid = true
                parsedBeds = bedsAsInt
            }
        }

        // 4. Property type
        val typeTrimmed = data.propertyTypeInput.trim().uppercase()
        var typeError: String? = null
        val isTypeValid: Boolean
        if (typeTrimmed.isBlank()) {
            isTypeValid = false
            typeError = "Property type is required"
        } else if (!VALID_PROPERTY_TYPES.contains(typeTrimmed)) {
            isTypeValid = false
            typeError = "Unsupported property type code"
        } else {
            isTypeValid = true
        }

        // 5. BER Rating (Optional or selected code, distinct EXEMPT support)
        val berTrimmed = data.berRatingInput.trim().uppercase()
        var berError: String? = null
        val isBerValid: Boolean
        if (berTrimmed.isBlank()) {
            // Optional / Unspecified
            isBerValid = true
        } else if (!VALID_BER_RATINGS.contains(berTrimmed)) {
            isBerValid = false
            berError = "Unsupported BER rating code"
        } else {
            isBerValid = true
        }

        return PropertyFormValidationResult(
            isLocationValid = isLocationValid,
            locationError = locationError,
            isSizeValid = isSizeValid,
            sizeError = sizeError,
            isBedroomsValid = isBedsValid,
            bedroomsError = bedsError,
            isPropertyTypeValid = isTypeValid,
            propertyTypeError = typeError,
            isBerRatingValid = isBerValid,
            berRatingError = berError,
            parsedLocation = if (isLocationValid) locationTrimmed else null,
            parsedSizeSqm = parsedSize,
            parsedBedrooms = parsedBeds,
            parsedPropertyType = if (isTypeValid) typeTrimmed else null,
            parsedBerRating = if (isBerValid && berTrimmed.isNotBlank()) berTrimmed else null
        )
    }
}
