package com.billbox.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.billbox.app.ui.CaptureMode
import com.billbox.app.ui.LocalContainer
import com.billbox.app.ui.Routes
import com.billbox.app.ui.bills.BillRow
import com.billbox.app.ui.components.CardBox
import com.billbox.app.ui.components.SectionTitle
import com.billbox.app.ui.theme.StatusColors
import com.billbox.app.util.Dates
import com.billbox.app.util.Money
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

private data class Attention(val icon: ImageVector, val color: Color, val title: String, val subtitle: String, val days: Long, val route: String)

@Composable
fun HomeScreen(nav: NavController, onAdd: (CaptureMode) -> Unit) {
    val repo = LocalContainer.current.repository
    val bills by repo.allBills.collectAsStateWithLifecycle(emptyList())
    val products by repo.products.collectAsStateWithLifecycle(emptyList())
    val recurring by repo.recurring.collectAsStateWithLifecycle(emptyList())
    val recent by remember { repo.recentBills(5) }.collectAsStateWithLifecycle(emptyList())

    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val thisMonth = bills.filter { YearMonth.from(it.billDate) == month }.sumOf { it.totalPaise }
    val lastMonth = bills.filter { YearMonth.from(it.billDate) == month.minusMonths(1) }.sumOf { it.totalPaise }

    val attention = remember(products, recurring, bills) {
        buildList {
            products.forEach { p ->
                val d = Dates.daysUntil(p.warrantyExpiry, today)
                if (d in 0..30) add(Attention(Icons.Filled.VerifiedUser, StatusColors.warning, "${p.name} warranty ends ${Dates.relative(p.warrantyExpiry, today)}", Dates.format(p.warrantyExpiry), d, Routes.product(p.id)))
            }
            recurring.filter { it.isActive }.forEach { r ->
                val d = Dates.daysUntil(r.nextDueDate, today)
                if (d <= 7) add(Attention(Icons.Filled.EventRepeat, if (d < 0) StatusColors.critical else StatusColors.warning,
                    if (d < 0) "${r.name} is overdue" else "${r.name} due ${Dates.relative(r.nextDueDate, today)}",
                    r.amountPaise?.let(Money::format) ?: Dates.format(r.nextDueDate), d, Routes.RECURRING))
            }
            bills.forEach { b ->
                val by = b.returnByDate ?: return@forEach
                val d = Dates.daysUntil(by, today)
                if (d in 0..7) add(Attention(Icons.AutoMirrored.Filled.KeyboardReturn, StatusColors.warning, "Return ${b.merchant} ${Dates.relative(by, today)}", Money.format(b.totalPaise), d, Routes.bill(b.id)))
            }
        }.sortedBy { it.days }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        val hour = LocalTime.now().hour
        Text(
            when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" },
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp),
        )
        Text("Your bills, warranties and spending in one place", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Surface(
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clickable { nav.navigate(Routes.INSIGHTS) },
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Spent in ${Dates.format(month)}", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                Text(Money.format(thisMonth), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                val change = if (lastMonth > 0) ((thisMonth - lastMonth) * 100 / lastMonth) else null
                Text(
                    change?.let { "${if (it >= 0) "▲" else "▼"} ${kotlin.math.abs(it)}% vs last month (${Money.formatShort(lastMonth)})" } ?: "Tap for insights",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
            QuickAction(Icons.Filled.CameraAlt, "Scan", Modifier.weight(1f)) { onAdd(CaptureMode.SCAN) }
            QuickAction(Icons.Filled.PhotoLibrary, "Gallery", Modifier.weight(1f)) { onAdd(CaptureMode.GALLERY) }
            QuickAction(Icons.Filled.PictureAsPdf, "PDF", Modifier.weight(1f)) { onAdd(CaptureMode.PDF) }
            QuickAction(Icons.Filled.EditNote, "Manual", Modifier.weight(1f)) { onAdd(CaptureMode.MANUAL) }
        }

        if (attention.isNotEmpty()) {
            SectionTitle("Needs attention")
            CardBox {
                Column {
                    attention.take(6).forEach { a ->
                        ListItem(
                            headlineContent = { Text(a.title) },
                            supportingContent = { Text(a.subtitle) },
                            leadingContent = { Icon(a.icon, null, tint = a.color) },
                            modifier = Modifier.clickable { nav.navigate(a.route) },
                        )
                    }
                }
            }
        }

        SectionTitle("Recent bills") {
            if (recent.isNotEmpty()) TextButton(onClick = { nav.navigate(Routes.BILLS) }) { Text("See all") }
        }
        if (recent.isEmpty()) {
            CardBox(onClick = { onAdd(CaptureMode.SCAN) }) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("Save your first bill", fontWeight = FontWeight.SemiBold)
                        Text("Scan it and BillBox fills in the store, date and amount.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            CardBox { Column { recent.forEach { BillRow(it) { nav.navigate(Routes.bill(it.bill.id)) } } } }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
