package com.billbox.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.billbox.app.data.BillWithExtras
import com.billbox.app.ui.LocalContainer
import com.billbox.app.ui.bills.Period
import com.billbox.app.ui.components.AppScaffold
import com.billbox.app.ui.components.BackTopBar
import com.billbox.app.ui.components.CardBox
import com.billbox.app.ui.components.Dropdown
import com.billbox.app.util.Dates
import com.billbox.app.util.Money
import kotlinx.coroutines.launch

@Composable
fun ExportScreen(nav: NavController) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by c.repository.categories.collectAsStateWithLifecycle(emptyList())
    var period by rememberSaveable { mutableStateOf(Period.THIS_MONTH) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var taxOnly by rememberSaveable { mutableStateOf(false) }
    var businessOnly by rememberSaveable { mutableStateOf(false) }
    var images by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun selection(): List<BillWithExtras> {
        val r = period.range()
        return c.repository.billsBetween(r.start, r.endInclusive).filter {
            (categoryId == null || it.bill.categoryId == categoryId) && (!taxOnly || it.bill.taxSection != null) &&
                (!businessOnly || it.bill.isBusiness)
        }
    }

    fun run(pdf: Boolean) = scope.launch {
        busy = true
        message = null
        try {
            val bills = selection()
            if (bills.isEmpty()) { message = "No bills match these filters."; return@launch }
            val title = buildString {
                append(if (taxOnly) "Tax proofs" else if (businessOnly) "Business expenses" else "Bills")
                append(" · ").append(period.label)
                categories.firstOrNull { it.id == categoryId }?.let { append(" · ").append(it.name) }
            }
            val name = "BillBox-${period.name.lowercase()}-${System.currentTimeMillis() / 1000}"
            val file = if (pdf) c.exporter.pdf(bills, title, name, images) else c.exporter.csv(bills, name)
            context.startActivity(c.exporter.share(file, title))
            message = "${Money.bills(bills.size)} · ${Money.format(bills.sumOf { it.bill.totalPaise })}"
        } catch (e: Exception) {
            message = "Export failed: ${e.message}"
        } finally {
            busy = false
        }
    }

    AppScaffold(topBar = { BackTopBar("Export and tax pack", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Dropdown("Period", Period.entries, period, { it.label }, { period = it })
            val r = period.range()
            if (period != Period.ALL) Text("${Dates.format(r.start)} – ${Dates.format(r.endInclusive)}", style = MaterialTheme.typography.bodySmall)
            Dropdown("Category", categories, categories.firstOrNull { it.id == categoryId }, { it.name }, { categoryId = it.id }, noneLabel = "All categories", onNone = { categoryId = null })
            Toggle("Only tax-saving bills", "80C, 80D, 80G, HRA, LTA — for your ITR", taxOnly) { taxOnly = it }
            Toggle("Only business expenses", "For reimbursement or GST claims", businessOnly) { businessOnly = it }
            Toggle("Attach bill photos to PDF", "One page per bill image", images) { images = it }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(onClick = { run(pdf = true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Export PDF") }
            OutlinedButton(onClick = { run(pdf = false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Export Excel (CSV)") }
            CardBox {
                Text(
                    "Tip: choose \"Last financial year\" and \"Only tax-saving bills\" in April to get every proof for your income-tax return in one PDF.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}
