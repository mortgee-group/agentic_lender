package com.mortgee.al_mobile.data.session

import android.content.SharedPreferences
import com.mortgee.al_mobile.data.model.BackendRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserPrivateStoreTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var fakeSessionOwner: FakeSessionOwner
    private lateinit var fakePartitionManager: FakeUserPartitionManager
    private lateinit var store: DefaultUserPrivateStore

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        fakeSessionOwner = FakeSessionOwner()
        fakePartitionManager = FakeUserPartitionManager()
        store = DefaultUserPrivateStore(fakePrefs, fakeSessionOwner, fakePartitionManager)
    }

    @Test
    fun `getString uses captured UID for reads and returns partitioned data`() = runTest {
        val sessionKeyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(sessionKeyA, "user_A@example.com", BackendRole.BORROWER)
        fakePartitionManager.readyUid = "user_A"

        val putSuccess = store.putString(sessionKeyA, "theme_preference", "dark")
        assertTrue(putSuccess)

        val valueA = store.getString("user_A", "theme_preference")
        assertEquals("dark", valueA)
    }

    @Test
    fun `putString guards private writes with full session key matching active session`() = runTest {
        val sessionKey = SessionKey("user_123", 1L)
        fakeSessionOwner.setAuthenticated(sessionKey, "user_123@example.com", BackendRole.BORROWER)
        fakePartitionManager.readyUid = "user_123"

        val result = store.putString(sessionKey, "draft_note", "Hello World")
        assertTrue(result)
        assertEquals("Hello World", store.getString("user_123", "draft_note"))
    }

    @Test
    fun `putString rejects write when session key generation is stale (A to A fresh generation)`() = runTest {
        // User A signs in first with generation 1
        val gen1Key = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(gen1Key, "user_A@example.com", BackendRole.BORROWER)
        fakePartitionManager.readyUid = "user_A"

        // Write with gen 1 key succeeds
        assertTrue(store.putString(gen1Key, "draft_1", "Initial draft"))

        // User A re-authenticates resulting in generation 2
        val gen2Key = SessionKey("user_A", 2L)
        fakeSessionOwner.setAuthenticated(gen2Key, "user_A@example.com", BackendRole.BORROWER)

        // Delayed write attempt using stale gen 1 key is rejected
        val staleWriteResult = store.putString(gen1Key, "draft_1", "Stale update")
        assertFalse(staleWriteResult)

        // Write using fresh gen 2 key succeeds
        val freshWriteResult = store.putString(gen2Key, "draft_1", "Fresh update")
        assertTrue(freshWriteResult)

        // Reads for UID user_A capture user_A and return latest data
        assertEquals("Fresh update", store.getString("user_A", "draft_1"))
    }

    @Test
    fun `putString rejects write when active user changes (A to B)`() = runTest {
        // User A active session key
        val sessionKeyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(sessionKeyA, "user_A@example.com", BackendRole.BORROWER)
        fakePartitionManager.readyUid = "user_A"

        store.putString(sessionKeyA, "user_data", "Data A")

        // User B signs in
        val sessionKeyB = SessionKey("user_B", 2L)
        fakeSessionOwner.setAuthenticated(sessionKeyB, "user_B@example.com", BackendRole.BROKER)
        fakePartitionManager.readyUid = "user_B"

        // Delayed write from User A with sessionKeyA while B is active is rejected
        val delayedWriteA = store.putString(sessionKeyA, "user_data", "Delayed Data A")
        assertFalse(delayedWriteA)

        // User B reads user_data for user_B -> returns null (User B cannot read User A's data)
        assertNull(store.getString("user_B", "user_data"))

        // User B writes user_data for user_B
        assertTrue(store.putString(sessionKeyB, "user_data", "Data B"))

        // User A's data remains isolated
        assertEquals("Data A", store.getString("user_A", "user_data"))
        assertEquals("Data B", store.getString("user_B", "user_data"))
    }

    @Test
    fun `putString rejects write when session is signed out`() = runTest {
        fakeSessionOwner.setSignedOut()
        fakePartitionManager.readyUid = null

        val key = SessionKey("user_123", 1L)
        val result = store.putString(key, "some_key", "some_val")

        assertFalse(result)
        assertNull(store.getString("user_123", "some_key"))
    }

    @Test
    fun `putString rejects write when partition is not ready in UserPartitionManager`() = runTest {
        val sessionKey = SessionKey("user_123", 1L)
        fakeSessionOwner.setAuthenticated(sessionKey, "user_123@example.com", BackendRole.BORROWER)
        // Partition is NOT ready
        fakePartitionManager.readyUid = null

        val result = store.putString(sessionKey, "unready_key", "value")

        assertFalse(result)
        assertNull(store.getString("user_123", "unready_key"))
    }

    @Test
    fun `getString ignores legacy unpartitioned private data whose owner cannot be verified`() = runTest {
        // Put raw unpartitioned legacy record directly into preferences without partition prefix
        fakePrefs.putRawUnpartitioned("legacy_key", "legacy_value")

        val sessionKeyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(sessionKeyA, "user_A@example.com", BackendRole.BORROWER)
        fakePartitionManager.readyUid = "user_A"

        // Calling getString for user_A or user_B ignores legacy unpartitioned record
        assertNull(store.getString("user_A", "legacy_key"))
        assertNull(store.getString("user_B", "legacy_key"))
        assertFalse(store.containsKey("user_A", "legacy_key"))
    }

    @Test
    fun `removeString and clearUserPartition guard writes with session key and partition readiness`() = runTest {
        val sessionKeyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(sessionKeyA, "user_A@example.com", BackendRole.BORROWER)
        fakePartitionManager.readyUid = "user_A"

        store.putString(sessionKeyA, "item1", "val1")
        store.putString(sessionKeyA, "item2", "val2")

        // Stale key removal fails
        val staleKey = SessionKey("user_A", 99L)
        assertFalse(store.removeString(staleKey, "item1"))
        assertFalse(store.clearUserPartition(staleKey))

        // Removal with valid key succeeds
        assertTrue(store.removeString(sessionKeyA, "item1"))
        assertNull(store.getString("user_A", "item1"))

        // Clear partition with valid key succeeds
        assertTrue(store.clearUserPartition(sessionKeyA))
        assertNull(store.getString("user_A", "item2"))
    }

    @Test
    fun `clearUserPartition removes only target user partition records without affecting another user partition`() = runTest {
        val sessionKeyA = SessionKey("user_A", 1L)
        fakeSessionOwner.setAuthenticated(sessionKeyA, "user_A@example.com", BackendRole.BORROWER)
        fakePartitionManager.readyUid = "user_A"
        store.putString(sessionKeyA, "shared_key", "Val A")

        val sessionKeyB = SessionKey("user_B", 2L)
        fakeSessionOwner.setAuthenticated(sessionKeyB, "user_B@example.com", BackendRole.BROKER)
        fakePartitionManager.readyUid = "user_B"
        store.putString(sessionKeyB, "shared_key", "Val B")

        // Clear User B partition
        assertTrue(store.clearUserPartition(sessionKeyB))

        // User B partition is cleared
        assertNull(store.getString("user_B", "shared_key"))

        // User A partition remains intact
        assertEquals("Val A", store.getString("user_A", "shared_key"))
    }

    @Test
    fun `handles blank or null input parameters safely`() = runTest {
        assertNull(store.getString("   ", "key"))
        assertNull(store.getString("user_123", "   "))
        assertFalse(store.putString(SessionKey("user_123", 1L), "  ", "val"))
        assertFalse(store.putString(SessionKey("  ", 1L), "key", "val"))
        assertFalse(store.containsKey("  ", "key"))
    }

    private class FakeSessionOwner : SessionOwner {
        private val _state = MutableStateFlow<SessionState>(SessionState.SignedOut)
        override val sessionState: StateFlow<SessionState> = _state.asStateFlow()

        fun setAuthenticated(key: SessionKey, email: String, role: BackendRole) {
            _state.value = SessionState.Authenticated(
                key = key,
                uid = key.uid,
                email = email,
                role = role
            )
        }

        fun setSignedOut() {
            _state.value = SessionState.SignedOut
        }

        override suspend fun startSignInAttempt(): SessionAttempt? = null
        override suspend fun startRestoreAttempt(): SessionAttempt? = null
        override suspend fun commitSession(attempt: SessionAttempt, uid: String, email: String, role: BackendRole, displayName: String?): Boolean = false
        override suspend fun invalidateSession(): Boolean = true
        override suspend fun signOut(): Boolean = true
        override suspend fun cancelAttempt(attempt: SessionAttempt) {}
    }

    private class FakeUserPartitionManager : UserPartitionManager {
        var readyUid: String? = null

        override val currentPartitionUid: String?
            get() = readyUid

        override suspend fun preparePartition(uid: String): Boolean {
            readyUid = uid
            return true
        }

        override fun isPartitionReady(uid: String): Boolean = readyUid == uid

        override suspend fun clearPartition() {
            readyUid = null
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        fun putRawUnpartitioned(key: String, value: String) {
            data[key] = value
        }

        override fun getAll(): Map<String, *> = data.toMap()

        override fun getString(key: String, defValue: String?): String? {
            return (data[key] as? String) ?: defValue
        }

        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = defValues
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
        override fun contains(key: String): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class FakeEditor : SharedPreferences.Editor {
            private val uncommittedModifications = mutableMapOf<String, Any?>()
            private val uncommittedRemovals = mutableSetOf<String>()

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                if (value == null) {
                    uncommittedRemovals.add(key)
                    uncommittedModifications.remove(key)
                } else {
                    uncommittedModifications[key] = value
                    uncommittedRemovals.remove(key)
                }
                return this
            }

            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this

            override fun remove(key: String): SharedPreferences.Editor {
                uncommittedRemovals.add(key)
                uncommittedModifications.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                data.keys.forEach { uncommittedRemovals.add(it) }
                uncommittedModifications.clear()
                return this
            }

            override fun commit(): Boolean {
                uncommittedRemovals.forEach { data.remove(it) }
                data.putAll(uncommittedModifications)
                uncommittedModifications.clear()
                uncommittedRemovals.clear()
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
