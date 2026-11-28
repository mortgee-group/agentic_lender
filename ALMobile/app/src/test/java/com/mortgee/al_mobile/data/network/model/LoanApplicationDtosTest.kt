package com.mortgee.al_mobile.data.network.model

import com.mortgee.al_mobile.di.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun createContract() {
        val moshi = NetworkModule.provideMoshi()
        val requestAdapter = moshi.adapter(CreateLoanRequest::class.java)
        val request = CreateLoanRequest(
            householdAnnualGrossIncome = 75000.0,
            existingDebtLiabilities = 10000.0,
            deposit = 70000.0,
            requestedMortgageTerm = 30,
            sellerAskingPrice = 350000.0,
            property = LoanPropertyDto(
                county = "Kilkenny",
                floorArea = 115.0,
                bedrooms = 3,
                bathrooms = 2,
                yearBuilt = 2005,
                propertyType = "SEMI_DETACHED",
                berRating = "B2",
            ),
        )

        val json = requestAdapter.toJson(request)
        assertTrue(json.contains("\"requestedMortgageTerm\":30"))
        assertFalse(json.contains("\"requestedMortgageTerm\":30.0"))

        val jsonMap = moshi.adapter(Map::class.java).fromJson(json) as Map<*, *>
        assertEquals(
            setOf(
                "householdAnnualGrossIncome",
                "existingDebtLiabilities",
                "deposit",
                "requestedMortgageTerm",
                "sellerAskingPrice",
                "property",
            ),
            jsonMap.keys,
        )
        assertFalse(jsonMap.containsKey("borrowerId"))
        assertFalse(jsonMap.containsKey("propertyId"))
        assertFalse(jsonMap.containsKey("requestedLoanAmount"))
        assertFalse(jsonMap.containsKey("currency"))

        assertEquals(75000.0, (jsonMap["householdAnnualGrossIncome"] as Number).toDouble(), 0.0)
        assertEquals(10000.0, (jsonMap["existingDebtLiabilities"] as Number).toDouble(), 0.0)
        assertEquals(70000.0, (jsonMap["deposit"] as Number).toDouble(), 0.0)
        assertEquals(30, (jsonMap["requestedMortgageTerm"] as Number).toInt())
        assertEquals(350000.0, (jsonMap["sellerAskingPrice"] as Number).toDouble(), 0.0)

        val propertyMap = jsonMap["property"] as Map<*, *>
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
            propertyMap.keys,
        )

        val receiptAdapter = moshi.adapter(CreateLoanReceipt::class.java)
        val receipt = receiptAdapter.fromJson(
            """{"applicationId":"application-001","message":"Mortgage application created successfully"}""",
        )
        assertEquals(
            CreateLoanReceipt(
                applicationId = "application-001",
                message = "Mortgage application created successfully",
            ),
            receipt,
        )

        val receiptWithUnknown = receiptAdapter.fromJson(
            """{"applicationId":"application-001","message":"Mortgage application created successfully","unexpectedField":"ignored"}""",
        )
        assertEquals(
            CreateLoanReceipt(
                applicationId = "application-001",
                message = "Mortgage application created successfully",
            ),
            receiptWithUnknown,
        )
    }
}

