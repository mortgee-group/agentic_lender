package com.mortgee.al_mobile.data.network.model

import com.mortgee.al_mobile.di.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Test

class LoanApplicationDtosTest {

    @Test
    fun propertyKeys() {
        val dto = LoanPropertyDto(
            county = "Kilkenny",
            floorArea = 115.0,
            bedrooms = 3,
            bathrooms = 2,
            yearBuilt = 2005,
            propertyType = "SEMI_DETACHED",
            berRating = "B2",
        )
        val jsonMap = NetworkModule.provideMoshi()
            .adapter(LoanPropertyDto::class.java)
            .toJsonValue(dto) as Map<*, *>

        assertEquals(
            setOf(
                "county",
                "floor_area",
                "bedrooms",
                "bathrooms",
                "year_built",
                "property_type",
                "ber_rating",
            ),
            jsonMap.keys,
        )
        assertEquals("Kilkenny", jsonMap["county"])
        assertEquals(115.0, (jsonMap["floor_area"] as Number).toDouble(), 0.0)
        assertEquals(3, (jsonMap["bedrooms"] as Number).toInt())
        assertEquals(2, (jsonMap["bathrooms"] as Number).toInt())
        assertEquals(2005, (jsonMap["year_built"] as Number).toInt())
        assertEquals("SEMI_DETACHED", jsonMap["property_type"])
        assertEquals("B2", jsonMap["ber_rating"])
    }
}
