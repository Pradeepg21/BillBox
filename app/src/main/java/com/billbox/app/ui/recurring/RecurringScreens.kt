package com.billbox.app.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.billbox.app.data.Frequency
import com.billbox.app.data.RecurringBill
import com.billbox.app.data.RecurringType
import com.billbox.app.ui.LocalContainer
import com.billbox.app.ui.Routes
import com.billbox.app.ui.components.AppScaffold
import com.billbox.app.ui.components.BackTopBar
import com.billbox.app.ui.components.CardBox
import com.billbox.app.ui.components.DateField
import com.billbox.app.ui.components.Dropdown
import com.billbox.app.ui.components.EmptyState
import com.billbox.app.ui.components.MoneyField
import com.billbox.app.ui.components.StatusPill
import com.billbox.app.ui.theme.StatusColors
import com.billbox.app.util.Dates
import com.billbox.app.util.Money
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What a recurring bill costs per month, for the subscriptions summary. */
fun RecurringBill.monthlyPaise(): Long = (amountPaise ?: 0) / frequency.months

@Composable
fun RecurringScreen(nav: NavController) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val items by c.repository.recurring.collectAsStateWithLifecycle(null)
    var paying by remember { mutableStateOf<RecurringBill?>(null) }

    AppScaffold(
        topBar = { BackTopBar("Recurring bills", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.editRecurring()) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add") })
        },
    ) { padding ->
        val list = items ?: return@AppScaffold
        if (list.isEmpty()) {
            Column(Modifier.padding(padding)) {
                EmptyState(Icons.Filled.EventRepeat, "No recurring bills", "Add electricity, rent, EMIs, insurance and subscriptions to get reminders before they're due.")
            }
            return@AppScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                val active = list.filter { it.isActive }
                val subs = active.filter { it.type == RecurringType.SUBSCRIPTION }
                CardBox(Modifier.padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Fixed costs per month", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(active.sumOf { it.monthlyPaise() }), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (subs.isNotEmpty()) {
                            val m = subs.sumOf { it.monthlyPaise() }
                            Text("Subscriptions: ${Money.format(m)} a month · ${Money.formatShort(m * 12)} a year", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            items(list, key = { it.id }) { r ->
                val days = Dates.daysUntil(r.nextDueDate)
                ListItem(
                    headlineContent = { Text(r.name, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Text(listOfNotNull(r.type.label, r.frequency.label, r.amountPaise?.let(Money::format), if (r.autopay) "Autopay" else null).joinToString(" · "))
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            if (!r.isActive) StatusPill("Paused", MaterialTheme.colorScheme.outline)
                            else StatusPill(
                                if (days < 0) "Overdue" else "Due ${Dates.relative(r.nextDueDate)}",
                                when { days < 0 -> StatusColors.critical; days <= 7 -> StatusColors.warning; else -> StatusColors.good },
                            )
                            if (r.isActive) TextButton(onClick = { paying = r }) { Text("Mark paid") }
                        }
                    },
                    modifier = Modifier.clickable { nav.navigate(Routes.editRecurring(r.id)) },
                )
            }
        }
    }

    paying?.let { r ->
        var amount by remember(r.id) { mutableStateOf(r.amountPaise?.let(Money::toInput) ?: "") }
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("Mark ${r.name} paid") },
            text = {
                Column {
                    Text("A bill is saved for today and the next due date moves to ${Dates.format(r.nextDueDate.plusMonths(r.frequency.months))}.")
                    MoneyField(amount, { amount = it }, "Amount paid", Modifier.padding(top = 12.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = Money.parse(amount) != null, onClick = {
                    val paise = Money.parse(amount) ?: return@TextButton
                    scope.launch { c.repository.markRecurringPaid(r, paise) }
                    paying = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditRecurringScreen(nav: NavController, id: Long) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val categories by c.repository.categories.collectAsStateWithLifecycle(emptyList())
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(RecurringType.UTILITY) }
    var amount by rememberSaveable { mutableStateOf("") }
    var frequency by rememberSaveable { mutableStateOf(Frequency.MONTHLY) }
    var due by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(7).toEpochDay()) }
    var remind by rememberSaveable { mutableStateOf("3") }
    var autopay by rememberSaveable { mutableStateOf(false) }
    var active by rememberSaveable { mutableStateOf(true) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        c.repository.getRecurring(id)?.let { r ->
            name = r.name; type = r.type; amount = r.amountPaise?.let(Money::toInput) ?: ""; frequency = r.frequency
            due = r.nextDueDate.toEpochDay(); remind = r.remindDaysBefore.toString(); autopay = r.autopay
            active = r.isActive; categoryId = r.categoryId
        }
        loaded = true
    }

    val save: () -> Unit = {
        scope.launch {
            c.repository.saveRecurring(RecurringBill(
                id = id, name = name.trim(), type = type, amountPaise = Money.parse(amount), frequency = frequency,
                nextDueDate = LocalDate.ofEpochDay(due), remindDaysBefore = remind.toIntOrNull() ?: 3,
                autopay = autopay, categoryId = categoryId, isActive = active,
            ))
            nav.popBackStack()
        }
    }

    AppScaffold(topBar = {
        BackTopBar(if (id == 0L) "Add recurring bill" else "Edit recurring bill", onBack = { nav.popBackStack() }) {
            if (id != 0L) TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
        }
    }) { padding ->
        if (!loaded) return@AppScaffold
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name *") }, placeholder = { Text("Electricity – BESCOM") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Type", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurringType.entries.forEach { t -> FilterChip(type == t, { type = t }, label = { Text(t.label) }) }
            }
            MoneyField(amount, { amount = it }, "Amount (leave empty if it varies)")
            Dropdown("How often", Frequency.entries, frequency, { it.label }, { frequency = it })
            DateField("Next due date", LocalDate.ofEpochDay(due), { it?.let { d -> due = d.toEpochDay() } })
            OutlinedTextField(remind, { remind = it.filter(Char::isDigit).take(2) }, label = { Text("Remind me this many days before") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Dropdown("Category", categories, categories.firstOrNull { it.id == categoryId }, { it.name }, { categoryId = it.id }, noneLabel = "No category", onNone = { categoryId = null })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Paid by autopay", modifier = Modifier.weight(1f)); Switch(autopay, { autopay = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active", modifier = Modifier.weight(1f)); Switch(active, { active = it })
            }
            Button(onClick = save, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $name?") },
            text = { Text("Reminders stop. Bills already saved for it are kept.") },
            confirmButton = { FilledTonalButton(onClick = { scope.launch { c.repository.deleteRecurring(id); nav.popBackStack() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
