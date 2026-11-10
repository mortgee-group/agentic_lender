package com.mortgee.al_mobile.data.session

import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SharedPreferencesCompletedSessionStorageTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var storage: SharedPreferencesCompletedSessionStorage

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        storage = SharedPreferencesCompletedSessionStorage(fakePrefs)
    }

    @Test
    fun `saveCompletedUid durably stores verified UID and returns true`() = runTest {
        val result = storage.saveCompletedUid("user_borrower_123")

        assertTrue(result)
        assertEquals("user_borrower_123", storage.getCompletedUid())
    }

    @Test
    fun `saveCompletedUid rejects blank UID and returns false`() = runTest {
        val resultBlank = storage.saveCompletedUid("   ")
        val resultEmpty = storage.saveCompletedUid("")

        assertFalse(resultBlank)
        assertFalse(resultEmpty)
        assertNull(storage.getCompletedUid())
    }

    @Test
    fun `getCompletedUid returns null when no marker exists`() = runTest {
        assertNull(storage.getCompletedUid())
    }

    @Test
    fun `clearCompletedUid removes stored UID marker and returns true`() = runTest {
        storage.saveCompletedUid("user_broker_456")
        assertEquals("user_broker_456", storage.getCompletedUid())

        val clearResult = storage.clearCompletedUid()

        assertTrue(clearResult)
        assertNull(storage.getCompletedUid())
    }

    @Test
    fun `saveCompletedUid failure is observable when commit returns false`() = runTest {
        fakePrefs.shouldFailCommit = true

        val saveResult = storage.saveCompletedUid("user_underwriter_789")

        assertFalse(saveResult)
        assertNull(storage.getCompletedUid())
    }

    @Test
    fun `saveCompletedUid failure is observable when commit throws exception`() = runTest {
        fakePrefs.shouldThrowOnCommit = true

        val saveResult = storage.saveCompletedUid("user_underwriter_789")

        assertFalse(saveResult)
        assertNull(storage.getCompletedUid())
    }

    @Test
    fun `clearCompletedUid failure is observable when commit returns false`() = runTest {
        storage.saveCompletedUid("user_123")
        fakePrefs.shouldFailCommit = true

        val clearResult = storage.clearCompletedUid()

        assertFalse(clearResult)
    }

    @Test
    fun `clearCompletedUid failure is observable when commit throws exception`() = runTest {
        storage.saveCompletedUid("user_123")
        fakePrefs.shouldThrowOnCommit = true

        val clearResult = storage.clearCompletedUid()

        assertFalse(clearResult)
    }

    @Test
    fun `getCompletedUid returns null when reading throws exception`() = runTest {
        storage.saveCompletedUid("user_123")
        fakePrefs.shouldThrowOnRead = true

        assertNull(storage.getCompletedUid())
    }

    @Test
    fun `getStorageName produces isolated names for distinct environment base URLs`() {
        val nameDev = SharedPreferencesCompletedSessionStorage.getStorageName("https://example.invalid/")
        val nameProd = SharedPreferencesCompletedSessionStorage.getStorageName("https://api.example.com/")
        val nameStaging = SharedPreferencesCompletedSessionStorage.getStorageName("https://staging.example.com/api/")

        assertEquals("al_mobile_session_https___example_invalid", nameDev)
        assertEquals("al_mobile_session_https___api_example_com", nameProd)
        assertEquals("al_mobile_session_https___staging_example_com_api", nameStaging)
        assertTrue(nameDev != nameProd)
        assertTrue(nameProd != nameStaging)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        var shouldFailCommit = false
        var shouldThrowOnCommit = false
        var shouldThrowOnRead = false

        override fun getAll(): Map<String, *> = data.toMap()

        override fun getString(key: String, defValue: String?): String? {
            if (shouldThrowOnRead) throw RuntimeException("IO Read Error")
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
                if (shouldThrowOnCommit) throw RuntimeException("Disk Write Failure")
                if (shouldFailCommit) return false
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
