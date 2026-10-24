package com.billbox.app.ui.bills

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.billbox.app.ui.CaptureMode
import com.billbox.app.ui.LocalContainer
import com.billbox.app.ui.Routes
import com.billbox.app.ui.components.AppScaffold
import com.billbox.app.ui.components.BackTopBar
import com.billbox.app.ui.components.CardBox
import com.billbox.app.ui.components.CategoryBadge
import com.billbox.app.ui.components.SectionTitle
import com.billbox.app.ui.warranty.ProductRow
import com.billbox.app.util.Dates
import com.billbox.app.util.Money
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun BillDetailScreen(nav: NavController, billId: Long) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by remember { c.repository.observeBill(billId) }.collectAsStateWithLifecycle(null)
    val products by remember { c.repository.productsForBill(billId) }.collectAsStateWithLifecycle(emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    val x = data

    AppScaffold(topBar = {
        BackTopBar("Bill", onBack = { nav.popBackStack() }) {
            if (x != null) {
                IconButton(onClick = {
                    val images = x.files.filter { it.isImage }.map { File(it.path) }
                    val text = "${x.bill.merchant} · ${Money.format(x.bill.totalPaise)} · ${Dates.format(x.bill.billDate)}"
                    val intent = if (images.isEmpty()) {
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share bill")
                    } else c.files.shareIntent(images, "image/jpeg", text).apply { putExtra(Intent.EXTRA_TEXT, text) }
                    context.startActivity(intent)
                }) { Icon(Icons.Filled.Share, "Share") }
                IconButton(onClick = { nav.navigate(Routes.editBill(billId, CaptureMode.NONE)) }) { Icon(Icons.Filled.Edit, "Edit") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        }
    }) { padding ->
        if (x == null) return@AppScaffold
        val b = x.bill
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val images = x.files.filter { it.isImage }.sortedBy { it.pageNo }
            if (images.isNotEmpty()) {
                val pager = rememberPagerState { images.size }
                HorizontalPager(pager, pageSpacing = 8.dp, modifier = Modifier.fillMaxWidth().height(320.dp)) { i ->
                    AsyncImage(
                        File(images[i].path), contentDescription = "Bill page ${i + 1}", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { context.startActivity(c.files.openIntent(images[i])) },
                    )
                }
                if (images.size > 1) {
                    Text("Page ${pager.currentPage + 1} of ${images.size} · tap to zoom", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
            x.files.firstOrNull { it.mimeType == "application/pdf" }?.let { pdf ->
                OutlinedButton(onClick = { context.startActivity(c.files.openIntent(pdf)) }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Open original PDF")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                CategoryBadge(x.category, 48)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.merchant, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${Dates.format(b.billDate)} · ${x.category?.name ?: "No category"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(Money.format(b.totalPaise), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))

            CardBox(Modifier.padding(top = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Info("Paid by", b.paymentMethod.label)
                    if (b.taxPaise > 0) Info("GST", Money.format(b.taxPaise))
                    b.gstin?.let { Info("Seller GSTIN", it) }
                    b.invoiceNo?.let { Info("Invoice no.", it) }
                    b.taxSection?.let { Info("Tax section", it.label) }
                    if (b.isBusiness) Info("Type", "Business expense")
                    b.returnByDate?.let { Info("Return by", "${Dates.format(it)} (${Dates.relative(it)})") }
                    if (b.tagList.isNotEmpty()) Info("Tags", b.tagList.joinToString(", "))
                    if (b.notes.isNotBlank()) Info("Notes", b.notes)
                }
            }

            SectionTitle("Products and warranty")
            products.forEach { p ->
                ProductRow(p) { nav.navigate(Routes.product(p.id)) }
                HorizontalDivider()
            }
            OutlinedButton(onClick = { nav.navigate(Routes.editProduct(billId = billId)) }, modifier = Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add product and warranty")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this bill?") },
            text = { Text("The bill and its photos are removed from this phone. Products linked to it are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { c.repository.deleteBill(billId); nav.popBackStack() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, modifier = Modifier.weight(1f))
    }
}
