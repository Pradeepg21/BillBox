package com.billbox.app.ui.help

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class WelcomePage(val icon: ImageVector, val title: String, val body: String)

private val pages = listOf(
    WelcomePage(
        Icons.AutoMirrored.Filled.ReceiptLong, "Welcome to BillBox",
        "Every bill, warranty card and due date in one place, so you never hunt for a receipt again.",
    ),
    WelcomePage(
        Icons.Filled.CameraAlt, "Scan once, details filled",
        "Scan a paper bill, pick a photo or PDF, or share one from WhatsApp or Gmail. BillBox reads the store, date, total and GST for you.",
    ),
    WelcomePage(
        Icons.Filled.NotificationsActive, "Never miss a date",
        "Add products and recurring bills. You get a reminder before a warranty ends or a bill is due.",
    ),
    WelcomePage(
        Icons.Filled.Lock, "Private by design",
        "No account and no sign-up. Your bills stay on this phone and nothing is uploaded. Tap ? on any screen for help.",
    ),
)

/** First-run tour; also reachable from the help guide. */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(8.dp), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = onDone) { Text("Skip") }
            }
            HorizontalPager(pager, modifier = Modifier.weight(1f)) { i -> Page(pages[i]) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                pages.indices.forEach { i ->
                    val selected = i == pager.currentPage
                    Box(
                        Modifier.size(if (selected) 10.dp else 8.dp).background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape,
                        ),
                    )
                }
            }
            Button(
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp).height(52.dp),
            ) { Text(if (last) "Get started" else "Next") }
        }
    }
}

@Composable
private fun Page(page: WelcomePage) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(128.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            page.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}
