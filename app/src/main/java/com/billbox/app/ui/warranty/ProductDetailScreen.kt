package com.billbox.app.ui.warranty

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.billbox.app.data.ServiceRecord
import com.billbox.app.ui.LocalContainer
import com.billbox.app.ui.Routes
import com.billbox.app.ui.components.AppScaffold
import com.billbox.app.ui.components.BackTopBar
import com.billbox.app.ui.components.CardBox
import com.billbox.app.ui.components.DateField
import com.billbox.app.ui.components.MoneyField
import com.billbox.app.ui.components.SectionTitle
import com.billbox.app.ui.components.StatusPill
import com.billbox.app.util.Dates
import com.billbox.app.util.Money
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

@Composable
fun ProductDetailScreen(nav: NavController, productId: Long) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val product by remember { c.repository.observeProduct(productId) }.collectAsStateWithLifecycle(null)
    val services by remember { c.repository.serviceRecords(productId) }.collectAsStateWithLifecycle(emptyList())
    val billId = product?.billId
    val bill by remember(billId) { billId?.let(c.repository::observeBill) ?: flowOf(null) }.collectAsStateWithLifecycle(null)
    var addService by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val p = product

    AppScaffold(topBar = {
        BackTopBar(p?.name ?: "Product", onBack = { nav.popBackStack() }) {
            IconButton(onClick = { nav.navigate(Routes.editProduct(productId)) }) { Icon(Icons.Filled.Edit, "Edit") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
        }
    }) { padding ->
        if (p == null) return@AppScaffold
        val status = p.status()
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Claim card: everything a service centre asks for, on one screen.
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text("Warranty claim card", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        StatusPill(status.label, status.color)
                    }
                    Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (p.brand.isNotBlank() || p.modelNo.isNotBlank()) Text(listOf(p.brand, p.modelNo).filter { it.isNotBlank() }.joinToString(" · "))
                    if (p.serialNo.isNotBlank()) {
                        Text("Serial number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(p.serialNo, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                    }
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Purchased", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Dates.format(p.purchaseDate))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Warranty until", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${Dates.format(p.warrantyExpiry)} (${Dates.relative(p.warrantyExpiry)})")
                        }
                    }
                    Text(
                        "${p.warrantyMonths} months standard" + if (p.extendedWarrantyMonths > 0) " + ${p.extendedWarrantyMonths} months extended/AMC" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (p.serviceCenterPhone.isNotBlank()) {
                            Button(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${p.serviceCenterPhone}".toUri())) }) {
                                Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text("Call service")
                            }
                        }
                        OutlinedButton(onClick = {
                            val files = listOfNotNull(p.warrantyCardPath?.let(::File)) +
                                bill?.files.orEmpty().filter { it.isImage }.map { File(it.path) }
                            val text = buildString {
                                append("${p.name}")
                                if (p.serialNo.isNotBlank()) append(", serial ${p.serialNo}")
                                append(". Purchased ${Dates.format(p.purchaseDate)}, warranty until ${Dates.format(p.warrantyExpiry)}.")
                            }
                            val intent = if (files.isEmpty()) {
                                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share claim details")
                            } else c.files.shareIntent(files, "image/jpeg", text).apply { putExtra(Intent.EXTRA_TEXT, text) }
                            context.startActivity(intent)
                        }) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Share for claim") }
                    }
                }
            }

            p.warrantyCardPath?.let { path ->
                SectionTitle("Warranty card")
                AsyncImage(
                    File(path), contentDescription = "Warranty card", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW).setDataAndType(c.files.uriFor(File(path)), "image/jpeg")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        )
                    },
                )
            }

            bill?.let { x ->
                SectionTitle("Purchase bill")
                ListItem(
                    headlineContent = { Text(x.bill.merchant) },
                    supportingContent = { Text("${Dates.format(x.bill.billDate)} · ${Money.format(x.bill.totalPaise)}") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null) },
                    modifier = Modifier.clickable { nav.navigate(Routes.bill(x.bill.id)) },
                )
            }

            SectionTitle("Service history") {
                TextButton(onClick = { addService = true }) { Icon(Icons.Filled.Add, null); Text("Add") }
            }
            if (services.isEmpty()) {
                Text("No repairs or services recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Total spent on service: ${Money.format(services.sumOf { it.costPaise })}", style = MaterialTheme.typography.bodySmall)
            }
            services.forEach { s ->
                ListItem(
                    headlineContent = { Text(s.issue) },
                    supportingContent = { Text(listOf(Dates.format(s.date), s.serviceCenter).filter { it.isNotBlank() }.joinToString(" · ")) },
                    trailingContent = {
                        Row {
                            Text(if (s.costPaise > 0) Money.format(s.costPaise) else "Free")
                            Icon(Icons.Filled.Delete, "Delete record", modifier = Modifier.padding(start = 8.dp).clickable {
                                scope.launch { c.repository.deleteService(s) }
                            })
                        }
                    },
                )
                HorizontalDivider()
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (addService) AddServiceDialog(onDismiss = { addService = false }) { date, issue, cost, center ->
        scope.launch { c.repository.addService(ServiceRecord(productId = productId, date = date, issue = issue, costPaise = cost, serviceCenter = center)) }
        addService = false
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this product?") },
            text = { Text("Its warranty reminders and service history are removed. The purchase bill is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { c.repository.deleteProduct(productId); nav.popBackStack() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddServiceDialog(onDismiss: () -> Unit, onSave: (LocalDate, String, Long, String) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var issue by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var center by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add service record") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(issue, { issue = it }, label = { Text("What was done") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DateField("Date", date, { it?.let { d -> date = d } })
                MoneyField(cost, { cost = it }, "Cost (0 if free)")
                OutlinedTextField(center, { center = it }, label = { Text("Service centre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = issue.isNotBlank(), onClick = { onSave(date, issue.trim(), Money.parse(cost) ?: 0, center.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
