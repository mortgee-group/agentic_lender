package com.mortgee.al_mobile.data.network.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CreateProfileRequestTest {

    private lateinit var moshi: Moshi

    @Before
    fun setUp() {
        moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Test
    fun `serializes full UserProfileCreate payload with first_name, last_name, phone_number, and fixed borrower role`() {
        val request = CreateProfileRequest.forBorrower(
            firstName = "Test",
            lastName = "Borrower",
            phoneNumber = "+353871234567",
        )
        val adapter = moshi.adapter(CreateProfileRequest::class.java)
        val json = adapter.toJson(request)

        assertEquals(
            """{"first_name":"Test","last_name":"Borrower","phone_number":"+353871234567","role":"borrower"}""",
            json,
        )
        assertEquals("borrower", request.role)
    }

    @Test
    fun `serializes payload without optional phone_number and converts blank phone to null`() {
        val absentPhoneRequest = CreateProfileRequest.forBorrower(
            firstName = "Test",
            lastName = "Borrower",
        )
        val blankPhoneRequest = CreateProfileRequest.forBorrower(
            firstName = "Test",
            lastName = "Borrower",
            phoneNumber = "   ",
        )
        val adapter = moshi.adapter(CreateProfileRequest::class.java)

        assertNull(absentPhoneRequest.phoneNumber)
        assertNull(blankPhoneRequest.phoneNumber)
        assertEquals(
            """{"first_name":"Test","last_name":"Borrower","role":"borrower"}""",
            adapter.toJson(absentPhoneRequest),
        )
        assertEquals(
            """{"first_name":"Test","last_name":"Borrower","role":"borrower"}""",
            adapter.toJson(blankPhoneRequest),
        )
    }

    @Test
    fun `deserializes UserProfileCreate payload from JSON`() {
        val json = """
            {
                "first_name": "Jane",
                "last_name": "Smith",
                "phone_number": "+353871234567",
                "role": "borrower"
            }
        """.trimIndent()
        val adapter = moshi.adapter(CreateProfileRequest::class.java)
        val request = adapter.fromJson(json)

        requireNotNull(request)
        assertEquals("Jane", request.firstName)
        assertEquals("Smith", request.lastName)
        assertEquals("+353871234567", request.phoneNumber)
        assertEquals("borrower", request.role)
    }

    @Test
    fun `deserializes payload with optional phone_number omitted`() {
        val json = """{"first_name":"Test","last_name":"Borrower","role":"borrower"}"""
        val adapter = moshi.adapter(CreateProfileRequest::class.java)
        val request = adapter.fromJson(json)

        requireNotNull(request)
        assertEquals("Test", request.firstName)
        assertEquals("Borrower", request.lastName)
        assertNull(request.phoneNumber)
        assertEquals("borrower", request.role)
    }
}
