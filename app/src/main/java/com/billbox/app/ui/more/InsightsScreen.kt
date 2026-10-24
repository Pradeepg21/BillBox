package com.billbox.app.ui.more

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.billbox.app.data.Category
import com.billbox.app.ui.LocalContainer
import com.billbox.app.ui.components.AppScaffold
import com.billbox.app.ui.components.BackTopBar
import com.billbox.app.ui.components.CardBox
import com.billbox.app.ui.components.SectionTitle
import com.billbox.app.ui.theme.StatusColors
import com.billbox.app.util.Dates
import com.billbox.app.util.Money
import java.time.YearMonth

@Composable
fun InsightsScreen(nav: NavController) {
    val repo = LocalContainer.current.repository
    val bills by repo.allBills.collectAsStateWithLifecycle(emptyList())
    val categories by repo.categories.collectAsStateWithLifecycle(emptyList())
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)

    val inMonth = remember(bills, month) { bills.filter { YearMonth.from(it.billDate) == month } }
    val total = inMonth.sumOf { it.totalPaise }
    val prev = bills.filter { YearMonth.from(it.billDate) == month.minusMonths(1) }.sumOf { it.totalPaise }
    val byCat = remember(inMonth, categories) {
        inMonth.groupBy { it.categoryId }.map { (id, list) -> categories.firstOrNull { it.id == id } to list.sumOf { it.totalPaise } }
            .sortedByDescending { it.second }
    }
    val last12 = remember(bills, month) {
        (11 downTo 0).map { i -> month.minusMonths(i.toLong()) }.map { m -> m to bills.filter { YearMonth.from(it.billDate) == m }.sumOf { it.totalPaise } }
    }
    val merchants = remember(inMonth) {
        inMonth.groupBy { it.merchant.trim().lowercase() }.map { (_, l) -> l.first().merchant to l.sumOf { it.totalPaise } }
            .sortedByDescending { it.second }.take(5)
    }

    AppScaffold(topBar = { BackTopBar("Insights", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                Text(Dates.format(month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = month < YearMonth.now()) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                }
            }
            CardBox {
                Column(Modifier.padding(16.dp)) {
                    Text("Total spent", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (prev > 0) {
                        val pct = (total - prev) * 100 / prev
                        Text(
                            "${if (pct >= 0) "▲" else "▼"} ${kotlin.math.abs(pct)}% vs ${Dates.shortMonth(month.minusMonths(1))} (${Money.formatShort(prev)})",
                            color = if (pct > 0) StatusColors.critical else StatusColors.good, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("${Money.bills(inMonth.size)} · business ${Money.formatShort(inMonth.filter { it.isBusiness }.sumOf { it.totalPaise })}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (byCat.isNotEmpty()) {
                SectionTitle("By category")
                CardBox {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Donut(byCat.map { it.first.colorFor() to it.second }, Modifier.size(130.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            byCat.take(6).forEach { (cat, amt) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).background(cat.colorFor(), CircleShape))
                                    Text(cat?.name ?: "No category", Modifier.weight(1f).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    Text("${amt * 100 / total.coerceAtLeast(1)}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            SectionTitle("Last 12 months")
            CardBox {
                Column(Modifier.padding(16.dp)) {
                    MonthBars(last12.map { it.second }, last12.indexOfFirst { it.first == month }, Modifier.fillMaxWidth().height(140.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        last12.forEachIndexed { i, (m, _) ->
                            Text(if (i % 2 == 1) Dates.shortMonth(m) else "", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Highest: ${Money.formatShort(last12.maxOf { it.second })} · average ${Money.formatShort(last12.sumOf { it.second } / 12)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }

            val budgeted = categories.filter { (it.monthlyBudgetPaise ?: 0) > 0 }
            if (budgeted.isNotEmpty()) {
                SectionTitle("Budgets")
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        budgeted.forEach { c ->
                            val used = byCat.firstOrNull { it.first?.id == c.id }?.second ?: 0
                            val budget = c.monthlyBudgetPaise!!
                            val frac = used.toFloat() / budget
                            Column {
                                Row {
                                    Text(c.name, Modifier.weight(1f))
                                    Text("${Money.formatShort(used)} / ${Money.formatShort(budget)}", style = MaterialTheme.typography.bodySmall)
                                }
                                LinearProgressIndicator(
                                    progress = { frac.coerceIn(0f, 1f) },
                                    color = when { frac >= 1f -> StatusColors.critical; frac >= 0.8f -> StatusColors.warning; else -> StatusColors.good },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (merchants.isNotEmpty()) {
                SectionTitle("Top merchants")
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        merchants.forEach { (name, amt) ->
                            Row { Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(Money.format(amt), fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Category?.colorFor(): Color = this?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline

@Composable
private fun Donut(slices: List<Pair<Color, Long>>, modifier: Modifier) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1).toFloat()
    Canvas(modifier) {
        val stroke = size.minDimension * 0.18f
        val inset = stroke / 2
        var start = -90f
        slices.forEach { (color, value) ->
            val sweep = value / total * 360f
            drawArc(color, start, sweep - 1.5f, useCenter = false, topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            start += sweep
        }
    }
}

@Composable
private fun MonthBars(values: List<Long>, highlight: Int, modifier: Modifier) {
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val accent = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    Canvas(modifier) {
        val slot = size.width / values.size
        val barW = slot * 0.6f
        values.forEachIndexed { i, v ->
            val h = size.height * v / max
            drawRoundRect(
                if (i == highlight) accent else quiet,
                topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                size = Size(barW, h), cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
}
