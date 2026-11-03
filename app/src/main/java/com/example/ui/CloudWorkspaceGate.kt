package com.example.ui

/**
 * Only a Firebase identity verified for this app session may enter bakery screens.
 * Cached Room preferences and a non-null Firebase user alone are not sufficient.
 */
internal fun hasVerifiedFirebaseWorkspace(
    locallySignedIn: Boolean,
    verifiedFirebaseUid: String?,
    firebaseCurrentUid: String?
): Boolean = locallySignedIn &&
    !verifiedFirebaseUid.isNullOrBlank() &&
    verifiedFirebaseUid == firebaseCurrentUid

/**
 * Existing v25 Room records may still be scoped to this old bakery ID.
 * Never silently replace their owner workspace or upload them to a new ID.
 */
internal fun legacyBakeryIdConflicts(
    localBakeryId: String?,
    cloudBakeryId: String
): Boolean = localBakeryId != null && localBakeryId != cloudBakeryId
