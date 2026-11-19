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
    fun `serializes full UserProfileCreate payload with role, names, and phone`() {
        val request = CreateProfileRequest(
            role = "borrower",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1234567890"
        )
        val adapter = moshi.adapter(CreateProfileRequest::class.java)
        val json = adapter.toJson(request)

        assertEquals(
            """{"role":"borrower","first_name":"John","last_name":"Doe","phone_number":"+1234567890"}""",
            json
        )
    }

    @Test
    fun `serializes payload with display_name only`() {
        val request = CreateProfileRequest(displayName = "Test Borrower")
        val adapter = moshi.adapter(CreateProfileRequest::class.java)
        val json = adapter.toJson(request)

        assertEquals("""{"display_name":"Test Borrower"}""", json)
    }

    @Test
    fun `deserializes UserProfileCreate payload from JSON`() {
        val json = """
            {
                "role": "borrower",
                "first_name": "Jane",
                "last_name": "Smith",
                "phone_number": "+353871234567",
                "display_name": "Jane Smith"
            }
        """.trimIndent()
        val adapter = moshi.adapter(CreateProfileRequest::class.java)
        val request = adapter.fromJson(json)

        requireNotNull(request)
        assertEquals("borrower", request.role)
        assertEquals("Jane", request.firstName)
        assertEquals("Smith", request.lastName)
        assertEquals("+353871234567", request.phoneNumber)
        assertEquals("Jane Smith", request.displayName)
    }

    @Test
    fun `deserializes payload with optional fields omitted`() {
        val json = """{"display_name":"Test Borrower"}"""
        val adapter = moshi.adapter(CreateProfileRequest::class.java)
        val request = adapter.fromJson(json)

        requireNotNull(request)
        assertEquals("Test Borrower", request.displayName)
        assertNull(request.role)
        assertNull(request.firstName)
        assertNull(request.lastName)
        assertNull(request.phoneNumber)
    }
}
