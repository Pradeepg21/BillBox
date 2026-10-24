package com.billbox.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.billbox.app.ui.AppRoot
import com.billbox.app.ui.theme.BillBoxTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Files shared into the app from another app, waiting to become a bill. */
object PendingShare {
    val uris = MutableStateFlow<List<Uri>>(emptyList())
}

class MainActivity : FragmentActivity() {

    /** Null until the app-lock setting is read, so bills never flash on screen before the lock. */
    private val locked = MutableStateFlow<Boolean?>(null)
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BillBoxApp).container
        if (savedInstanceState == null) {
            lifecycleScope.launch { locked.value = container.settings.appLock.first() }
            handleShare(intent)
        } else {
            // Rotation or process restore keeps the lock state instead of silently unlocking.
            locked.value = savedInstanceState.getBoolean(KEY_LOCKED, true)
            backgroundedAt = savedInstanceState.getLong(KEY_BACKGROUNDED_AT)
        }
        setContent {
            BillBoxTheme {
                AppRoot(container = container, lockedFlow = locked, onUnlock = ::authenticate)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked.value != false)
        outState.putLong(KEY_BACKGROUNDED_AT, backgroundedAt)
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after a minute in the background.
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > 60_000) {
            lifecycleScope.launch {
                if ((application as BillBoxApp).container.settings.appLock.first()) locked.value = true
            }
        }
    }

    fun authenticate() {
        val settings = (application as BillBoxApp).container.settings
        // The phone's screen lock was removed after app lock was turned on: don't lock the user out of their bills.
        if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
            lifecycleScope.launch { settings.setAppLock(false) }
            locked.value = false
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                locked.value = false
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock BillBox")
            .setSubtitle("Use your fingerprint, face or screen lock")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    private companion object {
        const val KEY_LOCKED = "locked"
        const val KEY_BACKGROUNDED_AT = "backgrounded_at"
    }

    @Suppress("DEPRECATION")
    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_STREAM)
            )
            Intent.ACTION_SEND_MULTIPLE ->
                (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) PendingShare.uris.value = uris
    }
}
