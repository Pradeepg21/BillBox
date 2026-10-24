package com.billbox.app.ui.warranty

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.billbox.app.data.Product
import com.billbox.app.ui.LocalContainer
import com.billbox.app.ui.Routes
import com.billbox.app.ui.components.EmptyState
import com.billbox.app.ui.components.StatusPill
import com.billbox.app.ui.theme.StatusColors
import com.billbox.app.util.Dates
import java.time.LocalDate

enum class WarrantyStatus(val label: String, val color: Color) {
    ACTIVE("Active", StatusColors.good),
    EXPIRING("Expiring soon", StatusColors.warning),
    EXPIRED("Expired", StatusColors.critical),
}

fun Product.status(today: LocalDate = LocalDate.now()): WarrantyStatus {
    val days = Dates.daysUntil(warrantyExpiry, today)
    return when {
        days < 0 -> WarrantyStatus.EXPIRED
        days <= 30 -> WarrantyStatus.EXPIRING
        else -> WarrantyStatus.ACTIVE
    }
}

@Composable
fun ProductListScreen(nav: NavController) {
    val products by LocalContainer.current.repository.products.collectAsStateWithLifecycle(null)
    var filter by rememberSaveable { mutableStateOf<WarrantyStatus?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.editProduct()) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add product") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            Text("Warranty", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
            val list = products ?: return@Column
            if (list.isEmpty()) {
                EmptyState(
                    Icons.Filled.VerifiedUser, "No products yet",
                    "Add your appliances and gadgets. BillBox reminds you 30, 7 and 1 day before each warranty ends.",
                )
                return@Column
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == null, { filter = null }, label = { Text("All ${list.size}") })
                WarrantyStatus.entries.forEach { s ->
                    FilterChip(filter == s, { filter = s }, label = { Text("${s.label} ${list.count { it.status() == s }}") })
                }
            }
            val shown = list.filter { filter == null || it.status() == filter }.sortedBy { it.warrantyExpiry }
            LazyColumn {
                items(shown, key = { it.id }) { p -> ProductRow(p) { nav.navigate(Routes.product(p.id)) } }
            }
        }
    }
}

@Composable
fun ProductRow(p: Product, onClick: () -> Unit) {
    val status = p.status()
    ListItem(
        headlineContent = { Text(p.name, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(
                listOf(p.brand, p.modelNo).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "Bought ${Dates.format(p.purchaseDate)}" },
            )
        },
        leadingContent = { Icon(Icons.Filled.VerifiedUser, null, tint = status.color) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(status.label, status.color)
                Text(
                    if (status == WarrantyStatus.EXPIRED) "ended ${Dates.format(p.warrantyExpiry)}" else "until ${Dates.format(p.warrantyExpiry)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
