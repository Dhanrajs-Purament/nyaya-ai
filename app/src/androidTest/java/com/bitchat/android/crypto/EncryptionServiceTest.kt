package com.bitchat.android.crypto

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Arrays

/**
 * Instrumentation test for panic-mode identity rotation.
 *
 * This must run on a device or emulator, not on the JVM. [EncryptionService]
 * stores the Ed25519 signing key in `EncryptedSharedPreferences`, and
 * `NoiseEncryptionService` reaches `SecureIdentityStateManager`, which does the
 * same. Both build an `androidx.security.crypto.MasterKey`, which requires the
 * hardware-backed `AndroidKeyStore` JCA provider. Robolectric does not
 * implement `AndroidKeyStore` on any host platform, so as a unit test this could
 * only ever fail with `KeyStoreException: AndroidKeyStore not found` — it was
 * previously in `src/test` and was the sole reason the CI unit-test job was red.
 *
 * Run with:  ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class EncryptionServiceTest {

    private lateinit var encryptionService: EncryptionService

    @Before
    fun setup() {
        encryptionService = EncryptionService(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun clearPersistentIdentity_rotatesKeysAndFingerprint() {
        val initialStaticKey = encryptionService.getStaticPublicKey()
        val initialSigningKey = encryptionService.getSigningPublicKey()
        val initialFingerprint = encryptionService.getIdentityFingerprint()

        assertNotNull("Initial static key should not be null", initialStaticKey)
        assertNotNull("Initial signing key should not be null", initialSigningKey)

        // Panic mode: wipe and regenerate the persistent identity.
        encryptionService.clearPersistentIdentity()

        val afterStaticKey = encryptionService.getStaticPublicKey()
        val afterSigningKey = encryptionService.getSigningPublicKey()
        val afterFingerprint = encryptionService.getIdentityFingerprint()

        assertNotEquals(
            "Static key should change after panic",
            Arrays.toString(initialStaticKey), Arrays.toString(afterStaticKey)
        )
        assertNotEquals(
            "Signing key should change after panic",
            Arrays.toString(initialSigningKey), Arrays.toString(afterSigningKey)
        )
        assertNotEquals(
            "Fingerprint should change after panic",
            initialFingerprint, afterFingerprint
        )
    }
}
