package com.mortgee.al_mobile.data.network.model

import com.mortgee.al_mobile.data.model.BackendRole
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class UserProfileDtoTest {

    private lateinit var moshi: Moshi

    @Before
    fun setUp() {
        moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Test
    fun `parses valid documented payload including display_name`() {
        val json = """
            {
                "uid": "user_123",
                "email": "jane.doe@example.com",
                "role": "borrower",
                "display_name": "Jane Doe"
            }
        """.trimIndent()

        val adapter = moshi.adapter(UserProfileDto::class.java)
        val dto = adapter.fromJson(json)

        requireNotNull(dto)
        assertEquals("user_123", dto.uid)
        assertEquals("jane.doe@example.com", dto.email)
        assertEquals("borrower", dto.role)
        assertEquals("Jane Doe", dto.displayName)
        assertEquals(BackendRole.BORROWER, dto.decodeRole())
    }

    @Test
    fun `parses valid payload without optional display_name`() {
        val json = """
            {
                "uid": "user_456",
                "email": "broker@example.com",
                "role": "broker"
            }
        """.trimIndent()

        val adapter = moshi.adapter(UserProfileDto::class.java)
        val dto = adapter.fromJson(json)

        requireNotNull(dto)
        assertEquals("user_456", dto.uid)
        assertEquals("broker@example.com", dto.email)
        assertEquals("broker", dto.role)
        assertNull(dto.displayName)
        assertEquals(BackendRole.BROKER, dto.decodeRole())
    }

    @Test
    fun `decodes all supported backend roles from raw wire strings`() {
        val borrowerDto = UserProfileDto(uid = "u1", email = "e1@test.com", role = "borrower")
        val brokerDto = UserProfileDto(uid = "u2", email = "e2@test.com", role = "broker")
        val underwriterDto = UserProfileDto(uid = "u3", email = "e3@test.com", role = "underwriter")

        assertEquals(BackendRole.BORROWER, borrowerDto.decodeRole())
        assertEquals(BackendRole.BROKER, brokerDto.decodeRole())
        assertEquals(BackendRole.UNDERWRITER, underwriterDto.decodeRole())
    }

    @Test
    fun `unknown or unsupported roles parse wire string but decode to null preventing session establishment`() {
        val json = """
            {
                "uid": "user_789",
                "email": "admin@example.com",
                "role": "admin",
                "display_name": "System Administrator"
            }
        """.trimIndent()

        val adapter = moshi.adapter(UserProfileDto::class.java)
        val dto = adapter.fromJson(json)

        requireNotNull(dto)
        assertEquals("admin", dto.role)
        assertNull("Unknown role must decode to null to prevent session establishment", dto.decodeRole())
    }

    @Test
    fun `unsupported role values such as owner and provider decode to null`() {
        val ownerDto = UserProfileDto(uid = "u1", email = "owner@test.com", role = "owner")
        val providerDto = UserProfileDto(uid = "u2", email = "provider@test.com", role = "provider")
        val uppercaseDto = UserProfileDto(uid = "u3", email = "borrower@test.com", role = "BORROWER")

        assertNull(ownerDto.decodeRole())
        assertNull(providerDto.decodeRole())
        assertNull(uppercaseDto.decodeRole())
    }

    @Test
    fun `missing or null role parses DTO with null role and decodes to null`() {
        val json = """
            {
                "uid": "user_999",
                "email": "norole@example.com"
            }
        """.trimIndent()

        val adapter = moshi.adapter(UserProfileDto::class.java)
        val dto = adapter.fromJson(json)

        requireNotNull(dto)
        assertNull(dto.role)
        assertNull(dto.decodeRole())
    }

    @Test
    fun `malformed payload with missing required uid throws JsonDataException`() {
        val json = """
            {
                "email": "jane@example.com",
                "role": "borrower"
            }
        """.trimIndent()

        val adapter = moshi.adapter(UserProfileDto::class.java)
        assertThrows(JsonDataException::class.java) {
            adapter.fromJson(json)
        }
    }

    @Test
    fun `malformed payload with missing required email throws JsonDataException`() {
        val json = """
            {
                "uid": "user_123",
                "role": "borrower"
            }
        """.trimIndent()

        val adapter = moshi.adapter(UserProfileDto::class.java)
        assertThrows(JsonDataException::class.java) {
            adapter.fromJson(json)
        }
    }
}
