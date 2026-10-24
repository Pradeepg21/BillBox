package com.billbox.app.ui.more

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.billbox.app.BuildConfig
import com.billbox.app.R
import com.billbox.app.reminders.ReminderWorker
import com.billbox.app.ui.LocalContainer
import com.billbox.app.ui.components.AppScaffold
import com.billbox.app.ui.components.BackTopBar
import com.billbox.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavController) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appLock by c.settings.appLock.collectAsStateWithLifecycle(false)
    var confirmWipe by remember { mutableStateOf(false) }
    fun notificationsAllowed() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var notifOk by remember { mutableStateOf(notificationsAllowed()) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }
    val canLock = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    AppScaffold(topBar = { BackTopBar("Settings", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle("Security", Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("App lock") },
                supportingContent = { Text(if (canLock) "Fingerprint, face or screen lock when opening BillBox" else "Set a screen lock on your phone first") },
                leadingContent = { Icon(Icons.Filled.Fingerprint, null) },
                trailingContent = { Switch(appLock, { on -> scope.launch { c.settings.setAppLock(on) } }, enabled = canLock || appLock) },
            )
            HorizontalDivider()
            SectionTitle("Reminders", Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("Notifications") },
                supportingContent = { Text(if (notifOk) "On — daily check at 9 am" else "Off — tap to allow reminders") },
                leadingContent = { Icon(Icons.Filled.Notifications, null) },
                modifier = Modifier.clickable(enabled = !notifOk && Build.VERSION.SDK_INT >= 33) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
            ListItem(
                headlineContent = { Text("Check reminders now") },
                supportingContent = { Text("Warranty 30/7/1 days before expiry, bills on their reminder day, returns, budgets") },
                leadingContent = { Icon(Icons.Filled.NotificationsActive, null) },
                modifier = Modifier.clickable {
                    ReminderWorker.runNow(context)
                    Toast.makeText(context, "Checking reminders…", Toast.LENGTH_SHORT).show()
                },
            )
            HorizontalDivider()
            SectionTitle("Your data", Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("Stored only on this phone") },
                supportingContent = { Text("Bills and photos stay in BillBox's private storage. Text is read on-device; nothing is uploaded. Use Export to keep a copy.") },
                leadingContent = { Icon(Icons.Filled.Info, null) },
            )
            ListItem(
                headlineContent = { Text("Delete all data", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Removes every bill, photo, product and reminder") },
                leadingContent = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { confirmWipe = true },
            )
            HorizontalDivider()
            val privacyUrl = stringResource(R.string.privacy_policy_url)
            val uriHandler = LocalUriHandler.current
            if (privacyUrl.isNotBlank()) {
                ListItem(
                    headlineContent = { Text("Privacy policy") },
                    leadingContent = { Icon(Icons.Filled.Policy, null) },
                    modifier = Modifier.clickable { uriHandler.openUri(privacyUrl) },
                )
            }
            ListItem(headlineContent = { Text("BillBox ${BuildConfig.VERSION_NAME}") }, supportingContent = { Text("Save bills · track warranties · see your spending") })
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete everything?") },
            text = { Text("This cannot be undone. Export your bills first if you want a copy.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch { c.repository.deleteEverything(); Toast.makeText(context, "All data deleted", Toast.LENGTH_SHORT).show() }
                }) { Text("Delete everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}
