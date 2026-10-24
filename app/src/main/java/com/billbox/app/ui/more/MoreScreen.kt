package com.billbox.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.billbox.app.ui.Routes

@Composable
fun MoreScreen(nav: NavController) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
        Entry(Icons.Filled.EventRepeat, "Recurring bills", "Utilities, rent, EMIs, subscriptions") { nav.navigate(Routes.RECURRING) }
        Entry(Icons.Filled.Insights, "Insights", "Where your money goes, month by month") { nav.navigate(Routes.INSIGHTS) }
        Entry(Icons.Filled.Category, "Categories and budgets", "Monthly limits with alerts at 80% and 100%") { nav.navigate(Routes.CATEGORIES) }
        Entry(Icons.Filled.FileDownload, "Export and tax pack", "PDF or Excel (CSV) for reimbursement and ITR") { nav.navigate(Routes.EXPORT) }
        Entry(Icons.Filled.Settings, "Settings", "App lock, reminders, your data") { nav.navigate(Routes.SETTINGS) }
    }
}

@Composable
private fun Entry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
