package com.billbox.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.billbox.app.data.Category
import com.billbox.app.ui.LocalContainer
import com.billbox.app.ui.components.AppScaffold
import com.billbox.app.ui.components.BackTopBar
import com.billbox.app.ui.components.CategoryBadge
import com.billbox.app.ui.components.MoneyField
import com.billbox.app.ui.components.categoryIcon
import com.billbox.app.ui.components.categoryIconKeys
import com.billbox.app.util.Money
import kotlinx.coroutines.launch

private val palette = listOf(0xFF3B82F6, 0xFF16A34A, 0xFFF97316, 0xFFDC2626, 0xFFEAB308, 0xFF0EA5E9, 0xFFDB2777, 0xFF7C3AED, 0xFF0F766E, 0xFF64748B)

@Composable
fun CategoriesScreen(nav: NavController) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val categories by c.repository.categories.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Category?>(null) }

    AppScaffold(
        topBar = { BackTopBar("Categories and budgets", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Category(name = "", icon = "receipt", color = palette.first()) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New category") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(categories, key = { it.id }) { cat ->
                ListItem(
                    headlineContent = { Text(cat.name) },
                    supportingContent = { Text(cat.monthlyBudgetPaise?.let { "Budget ${Money.formatShort(it)} a month" } ?: "No budget") },
                    leadingContent = { CategoryBadge(cat) },
                    modifier = Modifier.clickable { editing = cat },
                )
            }
        }
    }

    editing?.let { cat ->
        var name by remember(cat) { mutableStateOf(cat.name) }
        var budget by remember(cat) { mutableStateOf(cat.monthlyBudgetPaise?.let(Money::toInput) ?: "") }
        var icon by remember(cat) { mutableStateOf(cat.icon) }
        var color by remember(cat) { mutableStateOf(cat.color) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (cat.id == 0L) "New category" else cat.name) },
            text = { CategoryForm(cat, name, { name = it }, budget, { budget = it }, icon, { icon = it }, color, { color = it }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        c.repository.saveCategory(cat.copy(name = name.trim(), monthlyBudgetPaise = Money.parse(budget)?.takeIf { it > 0 }, icon = icon, color = color))
                    }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                if (cat.id != 0L && !cat.isSystem) {
                    TextButton(onClick = { scope.launch { c.repository.deleteCategory(cat) }; editing = null }) { Text("Delete") }
                } else TextButton(onClick = { editing = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryForm(
    cat: Category, name: String, onName: (String) -> Unit, budget: String, onBudget: (String) -> Unit,
    icon: String, onIcon: (String) -> Unit, color: Long, onColor: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name, onName, label = { Text("Name") }, singleLine = true, enabled = !cat.isSystem, modifier = Modifier.fillMaxWidth())
        MoneyField(budget, onBudget, "Monthly budget (optional)")
        if (!cat.isSystem) {
            Text("Icon", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categoryIconKeys.forEach { key ->
                    Box(
                        Modifier.size(36.dp).background(if (key == icon) Color(color).copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                            .clickable { onIcon(key) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(categoryIcon(key), key, tint = Color(color)) }
                }
            }
            Text("Colour", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                palette.forEach { p ->
                    Box(
                        Modifier.size(28.dp).background(Color(p), CircleShape)
                            .border(if (p == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { onColor(p) },
                    )
                }
            }
        }
    }
}
