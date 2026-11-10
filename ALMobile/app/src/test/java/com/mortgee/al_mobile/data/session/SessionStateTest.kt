package com.mortgee.al_mobile.data.session

import com.mortgee.al_mobile.data.model.BackendRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateTest {

    @Test
    fun `SignedOut state is closed to private content`() {
        val state: SessionState = SessionState.SignedOut

        assertFalse(state.isAuthenticated)
        assertNull(state.sessionKey)
        assertNull(state.currentUid)
    }

    @Test
    fun `Establishing state is closed to private content and contains attemptId`() {
        val state: SessionState = SessionState.Establishing(attemptId = "attempt_123")

        assertFalse(state.isAuthenticated)
        assertNull(state.sessionKey)
        assertNull(state.currentUid)
        assertEquals("attempt_123", (state as SessionState.Establishing).attemptId)
    }

    @Test
    fun `Restoring state is closed to private content and contains attemptId`() {
        val state: SessionState = SessionState.Restoring(attemptId = "attempt_456")

        assertFalse(state.isAuthenticated)
        assertNull(state.sessionKey)
        assertNull(state.currentUid)
        assertEquals("attempt_456", (state as SessionState.Restoring).attemptId)
    }

    @Test
    fun `Authenticated state is open to private content and exposes key and uid`() {
        val key = SessionKey(uid = "user_789", generation = 1L)
        val state: SessionState = SessionState.Authenticated(
            key = key,
            uid = "user_789",
            email = "borrower@example.com",
            role = BackendRole.BORROWER,
            displayName = "Jane Borrower",
        )

        assertTrue(state.isAuthenticated)
        assertEquals(key, state.sessionKey)
        assertEquals("user_789", state.currentUid)
        assertEquals("borrower@example.com", (state as SessionState.Authenticated).email)
        assertEquals(BackendRole.BORROWER, state.role)
        assertEquals("Jane Borrower", state.displayName)
    }

    @Test
    fun `SessionKey equality and generation properties behave correctly`() {
        val key1 = SessionKey(uid = "uid_1", generation = 100L)
        val key2 = SessionKey(uid = "uid_1", generation = 100L)
        val key3 = SessionKey(uid = "uid_1", generation = 101L)

        assertEquals(key1, key2)
        assertTrue(key1 != key3)
        assertEquals("uid_1", key1.uid)
        assertEquals(100L, key1.generation)
    }
}
