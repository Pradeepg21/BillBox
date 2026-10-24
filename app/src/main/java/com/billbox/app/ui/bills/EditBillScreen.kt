package com.billbox.app.ui.bills

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.billbox.app.PendingShare
import com.billbox.app.data.BillSource
import com.billbox.app.data.PaymentMethod
import com.billbox.app.data.TaxSection
import com.billbox.app.ui.CaptureMode
import com.billbox.app.ui.LocalContainer
import com.billbox.app.ui.Routes
import com.billbox.app.ui.components.AppScaffold
import com.billbox.app.ui.components.BackTopBar
import com.billbox.app.ui.components.DateField
import com.billbox.app.ui.components.Dropdown
import com.billbox.app.ui.components.MoneyField
import com.billbox.app.ui.components.SectionTitle
import com.billbox.app.util.Dates
import com.billbox.app.util.Money
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditBillScreen(nav: NavController, billId: Long, mode: CaptureMode) {
    val container = LocalContainer.current
    val vm: EditBillViewModel = viewModel { EditBillViewModel(container, billId) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty()
            vm.addUris(pages.map { it.imageUri }, BillSource.CAMERA)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        vm.addUris(uris, BillSource.GALLERY)
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addUris(listOf(uri), BillSource.PDF)
    }
    val startScan: () -> Unit = {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(5)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        if (activity != null) {
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { vm.message = "Scanner unavailable on this phone. Use the gallery instead." }
        }
    }
    val pickGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val pickPdf = { pdfLauncher.launch(arrayOf("application/pdf")) }

    // Open the chosen capture flow once, when the screen first appears.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (started) return@LaunchedEffect
        started = true
        when (mode) {
            CaptureMode.SCAN -> startScan()
            CaptureMode.GALLERY -> pickGallery()
            CaptureMode.PDF -> pickPdf()
            CaptureMode.SHARE -> {
                vm.addUris(PendingShare.uris.value, BillSource.SHARE)
                PendingShare.uris.value = emptyList()
            }
            else -> Unit
        }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    fun afterSave(id: Long) {
        val track = vm.trackWarranty && vm.isNew
        nav.popBackStack()
        when {
            track -> nav.navigate(Routes.editProduct(billId = id))
            vm.isNew -> nav.navigate(Routes.bill(id))
        }
    }
    val save: () -> Unit = { scope.launch { vm.save()?.let(::afterSave) } }

    AppScaffold(
        topBar = {
            BackTopBar(if (vm.isNew) "Add bill" else "Edit bill", onBack = { nav.popBackStack() }) {
                TextButton(onClick = save, enabled = vm.busy == null) { Text("Save") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!vm.loaded) return@AppScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Pages
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                items(vm.files.filter { it.isImage }, key = { it.path }) { f ->
                    Box {
                        AsyncImage(
                            model = File(f.path), contentDescription = "Bill page",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 84.dp, height = 112.dp).clip(RoundedCornerShape(10.dp)),
                        )
                        Icon(
                            Icons.Filled.Close, contentDescription = "Remove page",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp)
                                .clickable { vm.removeFile(f) },
                        )
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AddPageButton(Icons.Filled.CameraAlt, "Scan", startScan)
                        AddPageButton(Icons.Filled.PhotoLibrary, "Gallery", pickGallery)
                        AddPageButton(Icons.Filled.PictureAsPdf, "PDF", pickPdf)
                    }
                }
            }
            vm.busy?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            if (vm.ocrFilled) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Filled from the bill — check before saving", style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionTitle("Details")
            OutlinedTextField(
                vm.merchant, { vm.merchant = it }, label = { Text("Store or merchant") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(vm.total, { vm.total = it }, "Total", Modifier.weight(1f), isError = vm.totalError)
                DateField("Bill date", vm.date, { it?.let { d -> vm.date = d } }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Dropdown(
                label = "Category", options = categories, selected = categories.firstOrNull { it.id == vm.categoryId },
                optionLabel = { it.name }, onSelect = { vm.categoryId = it.id },
                noneLabel = "No category", onNone = { vm.categoryId = null },
            )
            Text("Paid by", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.entries.forEach { m ->
                    FilterChip(selected = vm.payment == m, onClick = { vm.payment = m }, label = { Text(m.label) })
                }
            }
            if (vm.isNew) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Checkbox(vm.trackWarranty, { vm.trackWarranty = it })
                    Text("Track warranty for this purchase")
                }
            }

            SectionTitle("Tax and GST")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(vm.tax, { vm.tax = it }, "GST amount", Modifier.weight(1f))
                OutlinedTextField(
                    vm.invoiceNo, { vm.invoiceNo = it }, label = { Text("Invoice no.") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                vm.gstin, { vm.gstin = it.uppercase().take(15) }, label = { Text("Seller GSTIN") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Dropdown(
                label = "Income-tax section", options = TaxSection.entries, selected = vm.taxSection,
                optionLabel = { it.label }, onSelect = { vm.taxSection = it },
                noneLabel = "Not for tax saving", onNone = { vm.taxSection = null },
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Business expense")
                    Text("Shown separately in reports", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(vm.isBusiness, { vm.isBusiness = it })
            }

            SectionTitle("More")
            DateField("Return window ends (optional)", vm.returnBy, { vm.returnBy = it }, clearable = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                vm.tags, { vm.tags = it }, label = { Text("Tags, comma separated") }, singleLine = true,
                placeholder = { Text("home renovation, office") }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(vm.notes, { vm.notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            Button(onClick = save, enabled = vm.busy == null, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                Text(if (vm.isNew) "Save bill" else "Save changes")
            }
        }
    }

    vm.duplicateOf?.let { dup ->
        AlertDialog(
            onDismissRequest = { vm.duplicateOf = null },
            title = { Text("Looks like a duplicate") },
            text = { Text("You already saved ${dup.merchant} for ${Money.format(dup.totalPaise)} on ${Dates.format(dup.billDate)}. Save this one too?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.duplicateOf = null
                    scope.launch { vm.save(ignoreDuplicate = true)?.let(::afterSave) }
                }) { Text("Save anyway") }
            },
            dismissButton = { TextButton(onClick = { vm.duplicateOf = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddPageButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.width(96.dp).height(33.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
