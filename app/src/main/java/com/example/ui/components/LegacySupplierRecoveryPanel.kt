package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.example.data.local.LegacySupplierRecoveryState
import kotlinx.coroutines.launch

@Composable
fun LegacySupplierRecoveryPanel(onInspect: suspend () -> Result<LegacySupplierRecoveryState>, onRecover: suspend (Boolean) -> Result<Int>) {
    var state by remember { mutableStateOf<LegacySupplierRecoveryState?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(Unit) { state = onInspect().getOrNull() }
    val current = state ?: return
    if (current.unassignedCount == 0 && message.isBlank()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Older saved suppliers", style = MaterialTheme.typography.titleMedium)
            if (current.unassignedCount > 0) {
                Text("${current.unassignedCount} older records are retained on this device without a saved account owner.")
                if (current.canRecover) {
                    Text("Recover them only if they belong to your bakery. No records are assigned automatically.")
                    OutlinedButton(onClick = { confirmed = false; showDialog = true }) { Text("Recover my older suppliers") }
                } else {
                    Text("This device's account history needs an ownership review. Older records remain retained and hidden; they have not been deleted or uploaded.")
                    TextButton(onClick = { runCatching { uriHandler.openUri("mailto:info@batchboss.co.za?subject=Older%20supplier%20recovery") } }) { Text("Contact support") }
                }
            }
            if (message.isNotBlank()) Text(message)
        }
    }
    if (showDialog) AlertDialog(
        onDismissRequest = { if (!busy) showDialog = false },
        title = { Text("Recover older suppliers?") },
        text = { Column {
            Text("Confirm the older suppliers on this device belong to this bakery. Recovery is local; refresh suppliers afterwards to sync them to your bakery workspace.")
            Row { Checkbox(checked = confirmed, onCheckedChange = { if (!busy) confirmed = it }); Text("These older suppliers belong to my bakery.") }
            if (busy) Text("Recovering…")
        } },
        confirmButton = { TextButton(enabled = confirmed && !busy, onClick = { scope.launch {
            busy = true
            try {
                onRecover(confirmed).onSuccess { count ->
                    message = "Recovered $count suppliers locally. Refresh suppliers to sync your bakery workspace."
                    state = onInspect().getOrNull(); showDialog = false
                }.onFailure { message = "Recovery could not be completed. Your older records are still retained."; showDialog = false }
            } finally { busy = false }
        } }) { Text("Recover locally") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { showDialog = false }) { Text("Cancel") } }
    )
}
