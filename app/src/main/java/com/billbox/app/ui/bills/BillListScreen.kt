package com.billbox.app.ui.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ListItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.billbox.app.data.BillWithExtras
import com.billbox.app.ui.CaptureMode
import com.billbox.app.ui.LocalContainer
import com.billbox.app.ui.Routes
import com.billbox.app.ui.components.CategoryBadge
import com.billbox.app.ui.components.TabHeader
import com.billbox.app.ui.components.EmptyState
import com.billbox.app.util.Dates
import com.billbox.app.util.Money
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

enum class Period(val label: String) {
    ALL("All time"), THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_FY("This financial year"), LAST_FY("Last financial year");

    fun range(today: LocalDate = LocalDate.now()): ClosedRange<LocalDate> = when (this) {
        ALL -> LocalDate.of(2000, 1, 1)..LocalDate.of(2100, 1, 1)
        THIS_MONTH -> YearMonth.from(today).let { it.atDay(1)..it.atEndOfMonth() }
        LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1)..it.atEndOfMonth() }
        THIS_FY -> Dates.financialYear(today)
        LAST_FY -> Dates.financialYear(today.minusYears(1))
    }
}

@Composable
fun BillListScreen(nav: NavController) {
    val repo = LocalContainer.current.repository
    var query by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf(Period.ALL) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var businessOnly by rememberSaveable { mutableStateOf(false) }
    val categories by repo.categories.collectAsStateWithLifecycle(emptyList())
    val results by remember(query) { repo.searchBills(query) }.collectAsStateWithLifecycle(null)

    val filtered = remember(results, period, categoryId, businessOnly) {
        val range = period.range()
        results.orEmpty().filter {
            it.bill.billDate in range && (categoryId == null || it.bill.categoryId == categoryId) &&
                (!businessOnly || it.bill.isBusiness)
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { YearMonth.from(it.bill.billDate) } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabHeader("Bills")
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search store, item, notes, invoice…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear") } },
            singleLine = true, shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var periodMenu by remember { mutableStateOf(false) }
            FilterChip(selected = period != Period.ALL, onClick = { periodMenu = true }, label = { Text(period.label) })
            DropdownMenu(periodMenu, { periodMenu = false }) {
                Period.entries.forEach { p -> DropdownMenuItem(text = { Text(p.label) }, onClick = { period = p; periodMenu = false }) }
            }
            var catMenu by remember { mutableStateOf(false) }
            FilterChip(
                selected = categoryId != null, onClick = { catMenu = true },
                label = { Text(categories.firstOrNull { it.id == categoryId }?.name ?: "All categories") },
            )
            DropdownMenu(catMenu, { catMenu = false }) {
                DropdownMenuItem(text = { Text("All categories") }, onClick = { categoryId = null; catMenu = false })
                categories.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { categoryId = c.id; catMenu = false }) }
            }
            FilterChip(selected = businessOnly, onClick = { businessOnly = !businessOnly }, label = { Text("Business") })
        }

        when {
            results == null -> Unit
            results!!.isEmpty() && query.isEmpty() -> EmptyState(
                Icons.AutoMirrored.Filled.ReceiptLong, "No bills yet",
                "Scan your first bill. BillBox reads the amount, date and store for you.",
                "Scan a bill",
            ) { nav.navigate(Routes.editBill(mode = CaptureMode.SCAN)) }
            filtered.isEmpty() -> EmptyState(Icons.Filled.Search, "No matching bills", "Try another search or clear the filters.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "${Money.bills(filtered.size)} · ${Money.format(filtered.sumOf { it.bill.totalPaise })}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                grouped.forEach { (month, bills) ->
                    item(key = month.toString()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 4.dp)) {
                            Text(Dates.format(month), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(Money.formatShort(bills.sumOf { it.bill.totalPaise }), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    items(bills, key = { it.bill.id }) { BillRow(it) { nav.navigate(Routes.bill(it.bill.id)) } }
                }
            }
        }
    }
}

@Composable
fun BillRow(x: BillWithExtras, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(x.bill.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(Dates.format(x.bill.billDate), x.category?.name, x.bill.paymentMethod.label).joinToString(" · "),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            val thumb = x.thumbnail
            if (thumb != null) {
                AsyncImage(
                    File(thumb.path), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else CategoryBadge(x.category, 44)
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(x.bill.totalPaise), fontWeight = FontWeight.SemiBold)
                if (x.bill.isBusiness) Text("Business", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
    )
}
