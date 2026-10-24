package com.billbox.app.ui.warranty

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.billbox.app.data.Product
import com.billbox.app.ui.LocalContainer
import com.billbox.app.ui.Routes
import com.billbox.app.ui.components.AppScaffold
import com.billbox.app.ui.components.BackTopBar
import com.billbox.app.ui.components.DateField
import com.billbox.app.ui.components.SectionTitle
import com.billbox.app.util.Dates
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProductScreen(nav: NavController, productId: Long, billId: Long) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable { mutableStateOf(false) }
    var original by remember { mutableStateOf<Product?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var serial by rememberSaveable { mutableStateOf("") }
    var purchase by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var months by rememberSaveable { mutableStateOf("12") }
    var extMonths by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var cardPath by rememberSaveable { mutableStateOf<String?>(null) }
    var linkedBill by rememberSaveable { mutableStateOf(billId.takeIf { it != 0L }) }

    LaunchedEffect(Unit) {
        if (productId != 0L) original = c.repository.getProduct(productId)
        if (loaded) return@LaunchedEffect // fields were restored after a configuration change
        if (productId != 0L) original?.let { p ->
            name = p.name; brand = p.brand; model = p.modelNo; serial = p.serialNo
            purchase = p.purchaseDate.toEpochDay(); months = p.warrantyMonths.toString()
            extMonths = if (p.extendedWarrantyMonths > 0) p.extendedWarrantyMonths.toString() else ""
            phone = p.serviceCenterPhone; notes = p.notes; cardPath = p.warrantyCardPath; linkedBill = p.billId
        } else if (billId != 0L) c.repository.getBill(billId)?.let { purchase = it.bill.billDate.toEpochDay() }
        loaded = true
    }

    val cardPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val f = c.files.importImage(uri)
            cardPath?.takeIf { it != original?.warrantyCardPath }?.let { File(it).delete() }
            cardPath = f.path
        }
    }

    val valid = name.isNotBlank() && months.toIntOrNull() != null
    val save: () -> Unit = {
        scope.launch {
            val p = Product(
                id = productId, billId = linkedBill, name = name.trim(), brand = brand.trim(), modelNo = model.trim(), serialNo = serial.trim(),
                purchaseDate = LocalDate.ofEpochDay(purchase), warrantyMonths = months.toIntOrNull() ?: 0,
                extendedWarrantyMonths = extMonths.toIntOrNull() ?: 0, warrantyCardPath = cardPath,
                serviceCenterPhone = phone.trim(), notes = notes.trim(),
            )
            if (original?.warrantyCardPath != null && original?.warrantyCardPath != cardPath) File(original!!.warrantyCardPath!!).delete()
            val id = c.repository.saveProduct(p)
            nav.popBackStack()
            if (productId == 0L) nav.navigate(Routes.product(id))
        }
    }

    AppScaffold(topBar = {
        BackTopBar(if (productId == 0L) "Add product" else "Edit product", onBack = { nav.popBackStack() }) {
            TextButton(onClick = save, enabled = valid) { Text("Save") }
        }
    }) { padding ->
        if (!loaded) return@AppScaffold
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            OutlinedTextField(name, { name = it }, label = { Text("Product name *") }, placeholder = { Text("Refrigerator") }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(brand, { brand = it }, label = { Text("Brand") }, singleLine = true, keyboardOptions = words, modifier = Modifier.weight(1f))
                OutlinedTextField(model, { model = it }, label = { Text("Model no.") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(serial, { serial = it }, label = { Text("Serial number") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            SectionTitle("Warranty")
            DateField("Purchase date", LocalDate.ofEpochDay(purchase), { it?.let { d -> purchase = d.toEpochDay() } })
            Text("Standard warranty", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6, 12, 24, 36, 60).forEach { m ->
                    FilterChip(months == m.toString(), { months = m.toString() }, label = { Text(if (m % 12 == 0) "${m / 12} yr" else "$m mo") })
                }
            }
            val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(months, { months = it.filter(Char::isDigit).take(3) }, label = { Text("Months") }, singleLine = true, keyboardOptions = numbers, modifier = Modifier.weight(1f))
                OutlinedTextField(extMonths, { extMonths = it.filter(Char::isDigit).take(3) }, label = { Text("Extended / AMC months") }, singleLine = true, keyboardOptions = numbers, modifier = Modifier.weight(1f))
            }
            months.toIntOrNull()?.let { m ->
                val expiry = LocalDate.ofEpochDay(purchase).plusMonths((m + (extMonths.toIntOrNull() ?: 0)).toLong())
                Text("Covered until ${Dates.format(expiry)}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }

            SectionTitle("Warranty card")
            cardPath?.let {
                AsyncImage(File(it), "Warranty card", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = { cardPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Filled.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp))
                Text(if (cardPath == null) "Add warranty card photo" else "Replace photo")
            }

            SectionTitle("Service")
            OutlinedTextField(phone, { phone = it }, label = { Text("Service centre / customer care number") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Button(onClick = save, enabled = valid, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) { Text("Save product") }
        }
    }
}
