package com.mortgee.al_mobile.ui.form

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyFormValidatorTest {

    @Test
    fun `validate returns valid result when all fields are correct`() {
        val data = PropertyFormData(
            locationInput = "Ballsbridge, Dublin 4",
            sizeSqmInput = "95.5",
            bedroomsInput = "3",
            propertyTypeInput = "APARTMENT",
            berRatingInput = "A2"
        )

        val result = PropertyFormValidator.validate(data)

        assertTrue(result.isValid)
        assertTrue(result.isLocationValid)
        assertTrue(result.isSizeValid)
        assertTrue(result.isBedroomsValid)
        assertTrue(result.isPropertyTypeValid)
        assertTrue(result.isBerRatingValid)

        assertEquals("Ballsbridge, Dublin 4", result.parsedLocation)
        assertEquals(95.5, result.parsedSizeSqm!!, 0.01)
        assertEquals(3, result.parsedBedrooms)
        assertEquals("APARTMENT", result.parsedPropertyType)
        assertEquals("A2", result.parsedBerRating)
    }

    @Test
    fun `validate supports zero-bedroom studios and EXEMPT BER rating`() {
        val data = PropertyFormData(
            locationInput = "Grand Canal Dock",
            sizeSqmInput = "40.0",
            bedroomsInput = "0",
            propertyTypeInput = "APARTMENT",
            berRatingInput = "EXEMPT"
        )

        val result = PropertyFormValidator.validate(data)

        assertTrue(result.isValid)
        assertEquals(0, result.parsedBedrooms)
        assertEquals("EXEMPT", result.parsedBerRating)
    }

    @Test
    fun `validate rejects blank location, negative or invalid size, negative bedrooms, invalid codes`() {
        val data = PropertyFormData(
            locationInput = "   ",
            sizeSqmInput = "-12.0",
            bedroomsInput = "not_a_number",
            propertyTypeInput = "INVALID_TYPE",
            berRatingInput = "Z99"
        )

        val result = PropertyFormValidator.validate(data)

        assertFalse(result.isValid)
        assertFalse(result.isLocationValid)
        assertEquals("Location is required", result.locationError)

        assertFalse(result.isSizeValid)
        assertEquals("Size must be a positive number in square metres", result.sizeError)

        assertFalse(result.isBedroomsValid)
        assertEquals("Bedrooms must be 0 (for Studio) or a positive whole number", result.bedroomsError)

        assertFalse(result.isPropertyTypeValid)
        assertEquals("Unsupported property type code", result.propertyTypeError)

        assertFalse(result.isBerRatingValid)
        assertEquals("Unsupported BER rating code", result.berRatingError)
    }

    @Test
    fun `validate treats blank BER rating as optional valid null`() {
        val data = PropertyFormData(
            locationInput = "Ranelagh",
            sizeSqmInput = "110",
            bedroomsInput = "2",
            propertyTypeInput = "TERRACED",
            berRatingInput = ""
        )

        val result = PropertyFormValidator.validate(data)

        assertTrue(result.isValid)
        assertTrue(result.isBerRatingValid)
        assertNull(result.parsedBerRating)
    }
}
