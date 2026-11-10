package com.mortgee.al_mobile.data.session

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UserPartitionManagerTest {

    @Test
    fun `preparePartition succeeds for valid UID and sets in-memory namespace`() = runTest {
        val manager = DefaultUserPartitionManager()

        val result = manager.preparePartition("user_123")

        assertTrue(result)
        assertEquals("user_123", manager.currentPartitionUid)
        assertTrue(manager.isPartitionReady("user_123"))
        assertFalse(manager.isPartitionReady("user_456"))
    }

    @Test
    fun `preparePartition rejects blank UID`() = runTest {
        val manager = DefaultUserPartitionManager()

        val result = manager.preparePartition("   ")

        assertFalse(result)
        assertNull(manager.currentPartitionUid)
        assertFalse(manager.isPartitionReady("   "))
    }

    @Test
    fun `preparePartition handles IO failure safely and fails closed`() = runTest {
        val failingInitializer = PartitionIoInitializer { uid ->
            if (uid == "failing_user") throw IOException("Disk failure")
            true
        }
        val manager = DefaultUserPartitionManager(failingInitializer)

        val result = manager.preparePartition("failing_user")

        assertFalse(result)
        assertNull(manager.currentPartitionUid)
        assertFalse(manager.isPartitionReady("failing_user"))
    }

    @Test
    fun `preparePartition handles IO returning false safely`() = runTest {
        val unreadyInitializer = PartitionIoInitializer { false }
        val manager = DefaultUserPartitionManager(unreadyInitializer)

        val result = manager.preparePartition("user_789")

        assertFalse(result)
        assertNull(manager.currentPartitionUid)
        assertFalse(manager.isPartitionReady("user_789"))
    }

    @Test
    fun `clearPartition resets active in-memory namespace`() = runTest {
        val manager = DefaultUserPartitionManager()

        manager.preparePartition("user_abc")
        assertTrue(manager.isPartitionReady("user_abc"))

        manager.clearPartition()
        assertNull(manager.currentPartitionUid)
        assertFalse(manager.isPartitionReady("user_abc"))
    }

    @Test
    fun `switching partition UIDs updates in-memory namespace`() = runTest {
        val manager = DefaultUserPartitionManager()

        assertTrue(manager.preparePartition("user_A"))
        assertTrue(manager.isPartitionReady("user_A"))
        assertFalse(manager.isPartitionReady("user_B"))

        assertTrue(manager.preparePartition("user_B"))
        assertFalse(manager.isPartitionReady("user_A"))
        assertTrue(manager.isPartitionReady("user_B"))
        assertEquals("user_B", manager.currentPartitionUid)
    }
}
