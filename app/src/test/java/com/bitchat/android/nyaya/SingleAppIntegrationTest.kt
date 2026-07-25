package com.bitchat.android.nyaya

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the "one app, both features" integration.
 *
 * Nyaya AI Lawyer and bitchat's encrypted mesh messenger ship in a single APK
 * behind a single launcher icon. Earlier builds declared both activities as
 * launchers, which put two separate icons on the user's home screen and made the
 * mesh messenger look like a different app. These tests fail if that regresses,
 * and equally if bitchat's activity is ever dropped from the manifest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SingleAppIntegrationTest {

    private lateinit var packageManager: PackageManager
    private lateinit var packageName: String

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        packageManager = app.packageManager
        packageName = app.packageName
    }

    private fun launcherActivities(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        return packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.name }
    }

    @Test
    fun exactlyOneLauncherIcon_isDeclared() {
        val launchers = launcherActivities()
        assertEquals(
            "the app must present a single launcher icon, found: $launchers",
            1, launchers.size
        )
    }

    @Test
    fun theLauncherIcon_isTheAiLawyer() {
        assertEquals(
            "com.bitchat.android.nyaya.NyayaActivity",
            launcherActivities().single()
        )
    }

    @Test
    fun bitchatMeshMessenger_isStillShippedAndReachable() {
        // Declared in the manifest and enabled, just no longer a launcher entry.
        val info = packageManager.getActivityInfo(
            android.content.ComponentName(packageName, "com.bitchat.android.MainActivity"),
            0
        )
        assertTrue("bitchat's activity must remain enabled", info.enabled)
        assertTrue("bitchat's activity must be exported for deep links", info.exported)
        assertTrue(
            "bitchat's activity must not be a launcher entry",
            "com.bitchat.android.MainActivity" !in launcherActivities()
        )
    }

    @Test
    fun bitchatVerificationDeepLink_stillResolves() {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("bitchat://verify"))
            .setPackage(packageName)
        val targets = packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
        assertTrue(
            "bitchat://verify must still open bitchat, got $targets",
            targets.contains("com.bitchat.android.MainActivity")
        )
    }

    @Test
    fun meshForegroundService_isStillDeclared() {
        val services = packageManager.getPackageInfo(
            packageName, PackageManager.GET_SERVICES
        ).services.orEmpty().map { it.name }
        assertTrue(
            "the mesh foreground service must still ship, got $services",
            services.any { it.contains("MeshForegroundService") }
        )
    }
}
