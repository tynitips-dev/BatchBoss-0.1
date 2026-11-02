package com.example.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudWorkspaceGateTest {
    @Test fun onlyVerifiedFirebaseIdentityUnlocksScreens() {
        assertFalse(hasVerifiedFirebaseWorkspace(false, null, null))
        assertFalse(hasVerifiedFirebaseWorkspace(true, null, "uid"))
        assertFalse(hasVerifiedFirebaseWorkspace(false, "uid", "uid"))
        assertFalse(hasVerifiedFirebaseWorkspace(true, "uid-A", "uid-B"))
        assertTrue(hasVerifiedFirebaseWorkspace(true, "uid", "uid"))
    }

    @Test fun legacyBakeryIdentityMustBePreserved() {
        assertTrue(legacyBakeryIdConflicts("local-bakery", "cloud-bakery"))
        assertTrue(legacyBakeryIdConflicts("", "cloud-bakery"))
        assertFalse(legacyBakeryIdConflicts("cloud-bakery", "cloud-bakery"))
        assertFalse(legacyBakeryIdConflicts(null, "cloud-bakery"))
    }
}
