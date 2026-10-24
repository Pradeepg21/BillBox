package com.billbox.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.billbox.app.AppContainer
import com.billbox.app.PendingShare
import com.billbox.app.ui.bills.BillDetailScreen
import com.billbox.app.ui.bills.BillListScreen
import com.billbox.app.ui.bills.EditBillScreen
import com.billbox.app.ui.home.HomeScreen
import com.billbox.app.ui.lock.LockScreen
import com.billbox.app.ui.more.CategoriesScreen
import com.billbox.app.ui.more.ExportScreen
import com.billbox.app.ui.more.InsightsScreen
import com.billbox.app.ui.more.MoreScreen
import com.billbox.app.ui.more.SettingsScreen
import com.billbox.app.ui.recurring.EditRecurringScreen
import com.billbox.app.ui.recurring.RecurringScreen
import com.billbox.app.ui.warranty.EditProductScreen
import com.billbox.app.ui.warranty.ProductDetailScreen
import com.billbox.app.ui.warranty.ProductListScreen
import kotlinx.coroutines.flow.StateFlow

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }

/** How the add-bill screen should start. */
enum class CaptureMode { SCAN, GALLERY, PDF, MANUAL, SHARE, NONE }

object Routes {
    const val HOME = "home"
    const val BILLS = "bills"
    const val WARRANTY = "warranty"
    const val MORE = "more"
    fun bill(id: Long) = "bill/$id"
    fun editBill(id: Long = 0, mode: CaptureMode = CaptureMode.NONE) = "bill/edit?id=$id&mode=$mode"
    fun product(id: Long) = "product/$id"
    fun editProduct(id: Long = 0, billId: Long = 0) = "product/edit?id=$id&billId=$billId"
    const val RECURRING = "recurring"
    fun editRecurring(id: Long = 0) = "recurring/edit?id=$id"
    const val INSIGHTS = "insights"
    const val CATEGORIES = "categories"
    const val EXPORT = "export"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.BILLS, "Bills", Icons.AutoMirrored.Filled.ReceiptLong),
    Tab(Routes.WARRANTY, "Warranty", Icons.Filled.VerifiedUser),
    Tab(Routes.MORE, "More", Icons.Filled.MoreHoriz),
)

@Composable
fun AppRoot(container: AppContainer, lockedFlow: StateFlow<Boolean?>, onUnlock: () -> Unit) {
    val locked by lockedFlow.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalContainer provides container) {
        when (locked) {
            null -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
            true -> LockScreen(onUnlock)
            false -> AppNav()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var showAdd by remember { mutableStateOf(false) }

    // Ask once for notification permission (Android 13+) so reminders can be shown.
    val settings = LocalContainer.current.settings
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!settings.onboarded.first()) {
            settings.setOnboarded()
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val shared by PendingShare.uris.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        if (shared.isNotEmpty()) nav.navigate(Routes.editBill(mode = CaptureMode.SHARE))
    }

    Scaffold(
        bottomBar = {
            if (route in tabs.map { it.route }) {
                NavigationBar {
                    tabs.take(2).forEach { TabItem(it, route, nav) }
                    NavigationBarItem(
                        selected = false,
                        onClick = { showAdd = true },
                        icon = {
                            FilledIconButton(onClick = { showAdd = true }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = "Add bill")
                            }
                        },
                        label = { Text("Add") },
                    )
                    tabs.drop(2).forEach { TabItem(it, route, nav) }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.HOME) { HomeScreen(nav, onAdd = { mode -> nav.navigate(Routes.editBill(mode = mode)) }) }
            composable(Routes.BILLS) { BillListScreen(nav) }
            composable(Routes.WARRANTY) { ProductListScreen(nav) }
            composable(Routes.MORE) { MoreScreen(nav) }
            composable(
                "bill/edit?id={id}&mode={mode}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("mode") { type = NavType.StringType; defaultValue = CaptureMode.NONE.name },
                ),
            ) { entry ->
                EditBillScreen(
                    nav,
                    billId = entry.arguments?.getLong("id") ?: 0L,
                    mode = CaptureMode.valueOf(entry.arguments?.getString("mode") ?: CaptureMode.NONE.name),
                )
            }
            composable("bill/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                BillDetailScreen(nav, entry.arguments!!.getLong("id"))
            }
            composable(
                "product/edit?id={id}&billId={billId}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("billId") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { entry ->
                EditProductScreen(nav, entry.arguments?.getLong("id") ?: 0L, entry.arguments?.getLong("billId") ?: 0L)
            }
            composable("product/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ProductDetailScreen(nav, entry.arguments!!.getLong("id"))
            }
            composable(Routes.RECURRING) { RecurringScreen(nav) }
            composable(
                "recurring/edit?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
            ) { entry -> EditRecurringScreen(nav, entry.arguments?.getLong("id") ?: 0L) }
            composable(Routes.INSIGHTS) { InsightsScreen(nav) }
            composable(Routes.CATEGORIES) { CategoriesScreen(nav) }
            composable(Routes.EXPORT) { ExportScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    "Add a bill", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                AddOption(Icons.Filled.CameraAlt, "Scan with camera", "Auto crop and read the bill") {
                    showAdd = false; nav.navigate(Routes.editBill(mode = CaptureMode.SCAN))
                }
                AddOption(Icons.Filled.PhotoLibrary, "Choose from gallery", "Photos or screenshots of bills") {
                    showAdd = false; nav.navigate(Routes.editBill(mode = CaptureMode.GALLERY))
                }
                AddOption(Icons.Filled.PictureAsPdf, "Import PDF", "E-bills and invoices") {
                    showAdd = false; nav.navigate(Routes.editBill(mode = CaptureMode.PDF))
                }
                AddOption(Icons.Filled.EditNote, "Enter manually", "No photo needed") {
                    showAdd = false; nav.navigate(Routes.editBill(mode = CaptureMode.MANUAL))
                }
            }
        }
    }
}

@Composable
private fun AddOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
private fun RowScope.TabItem(tab: Tab, route: String?, nav: NavHostController) {
    NavigationBarItem(
        selected = route == tab.route,
        onClick = {
            nav.navigate(tab.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(tab.icon, contentDescription = null) },
        label = { Text(tab.label) },
    )
}
