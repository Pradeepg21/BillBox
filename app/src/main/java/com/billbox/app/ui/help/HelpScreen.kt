package com.billbox.app.ui.help

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.billbox.app.ui.Routes
import com.billbox.app.ui.components.AppScaffold
import com.billbox.app.ui.components.BackTopBar
import com.billbox.app.ui.components.CardBox

private data class HelpTopic(val key: String, val icon: ImageVector, val title: String, val steps: List<String>)

private val topics = listOf(
    HelpTopic(
        "start", Icons.Filled.Home, "Getting started", listOf(
            "Home shows what you spent this month, anything that needs attention soon, and your latest bills.",
            "Tap + (or Scan, Gallery, PDF or Manual on Home) to save a bill.",
            "Use the tabs to move between Home, Bills, Warranty and More.",
            "Tap ? on any screen to open help for that screen.",
        ),
    ),
    HelpTopic(
        "add", Icons.Filled.AddAPhoto, "Adding a bill", listOf(
            "Scan with camera: hold the bill flat. Edges are found and cropped for you, and you can add more pages.",
            "Choose from gallery or Import PDF for screenshots and e-bills. You can also share a bill to BillBox from WhatsApp, Gmail or Files.",
            "BillBox fills in the store, date, total, GST, GSTIN and invoice number. Check them before you save.",
            "Pick a category and how you paid. Add a return-by date, tax section (80C, 80D, 80G, HRA, LTA), tags or notes if you need them.",
            "Turn on Business expense to keep it separate for reimbursement, or Track warranty to add the product right away.",
        ),
    ),
    HelpTopic(
        "bills", Icons.AutoMirrored.Filled.ReceiptLong, "Finding bills", listOf(
            "Search by store, item, notes, invoice number or any text on the bill.",
            "Use the chips to filter by period, category or business bills.",
            "Open a bill to see its photos, share it, edit it or delete it.",
        ),
    ),
    HelpTopic(
        "warranty", Icons.Filled.VerifiedUser, "Warranties and claims", listOf(
            "Tap Add product and enter the warranty period. BillBox works out the expiry date.",
            "You get reminders 30, 7 and 1 day before a warranty ends.",
            "Add extended warranty or AMC, a photo of the warranty card, and the service-centre number.",
            "The claim card shows the serial number, purchase bill and service number on one screen. Tap Call service or Share for claim.",
            "Record repairs under Service history to keep track of costs.",
        ),
    ),
    HelpTopic(
        "recurring", Icons.Filled.EventRepeat, "Recurring bills and reminders", listOf(
            "Go to More, then Recurring bills, to add rent, EMIs, electricity, broadband, insurance, subscriptions or renewals.",
            "Choose how often it repeats and how many days before the due date to remind you.",
            "Tap Mark paid to save the bill and move the due date forward.",
            "Reminders arrive once a day around 9 am. Allow notifications for BillBox so you do not miss them.",
        ),
    ),
    HelpTopic(
        "insights", Icons.Filled.Insights, "Insights and budgets", listOf(
            "Insights (under More) shows spending by category and a 12-month trend.",
            "Categories and budgets (under More) lets you set a monthly limit per category. You are alerted at 80% and 100%.",
        ),
    ),
    HelpTopic(
        "export", Icons.Filled.FileDownload, "Export and tax pack", listOf(
            "Export (under More) lets you choose a period, including the Indian financial year (April to March).",
            "Export PDF includes bill photos. Export Excel (CSV) opens in Excel or Google Sheets.",
            "Save the file or share it with your CA, your employer or yourself.",
        ),
    ),
    HelpTopic(
        "privacy", Icons.Filled.Lock, "Privacy, app lock and your data", listOf(
            "No account is needed. Bills and photos are stored only on this phone, and text is read on-device.",
            "Settings, App lock asks for your fingerprint, face or screen lock when BillBox opens.",
            "Uninstalling BillBox or using Delete all data removes everything. Export first if you want a copy.",
        ),
    ),
)

/** Maps a navigation route to the help topic for that screen. */
fun helpTopicFor(route: String?): String = when {
    route == null -> "start"
    route.startsWith("bill/edit") -> "add"
    route.startsWith("bill") -> "bills"
    route.startsWith("product") || route == Routes.WARRANTY -> "warranty"
    route.startsWith("recurring") -> "recurring"
    route == Routes.INSIGHTS || route == Routes.CATEGORIES -> "insights"
    route == Routes.EXPORT -> "export"
    route == Routes.SETTINGS -> "privacy"
    else -> "start"
}

@Composable
fun HelpScreen(nav: NavController, topic: String) {
    val start = topics.indexOfFirst { it.key == topic }.coerceAtLeast(0)
    var open by remember { mutableStateOf(setOf(topics[start].key)) }
    // Item 0 is the intro line, so topic i is at index i + 1.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = if (start > 0) start + 1 else 0)
    val uri = LocalUriHandler.current

    AppScaffold(topBar = { BackTopBar("How to use BillBox", onBack = { nav.popBackStack() }, showHelp = false) }) { padding ->
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Tap a topic to see the steps. Open this guide any time with the ? button.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(topics, key = { it.key }) { t ->
                val expanded = t.key in open
                CardBox(Modifier.animateContentSize(), onClick = { open = if (expanded) open - t.key else open + t.key }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(t.icon, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                t.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).padding(start = 16.dp),
                            )
                            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
                        }
                        if (expanded) {
                            t.steps.forEachIndexed { i, step ->
                                Row(Modifier.padding(top = 12.dp)) {
                                    Text("${i + 1}.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(24.dp))
                                    Text(step, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { nav.navigate(Routes.WELCOME) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("View the welcome tour again")
                }
                TextButton(onClick = { uri.openUri("mailto:sabitri.gorai@gmail.com?subject=BillBox%20help") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Still stuck? Email us")
                }
            }
        }
    }
}
