package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.remote.PublicSupplier
import com.example.data.remote.PublicSupplierDirectory
import kotlinx.coroutines.launch

@Composable
fun SupplierDiscoveryPanel(savedIds: Set<String>, canSave: Boolean, onUnlock: () -> Unit,
    onSave: suspend (PublicSupplier) -> Result<Unit>, onSync: suspend () -> Result<Unit>) {
    var entries by remember { mutableStateOf<List<PublicSupplier>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun load(next: String? = null) {
        busy = true; error = ""
        try {
            val page = PublicSupplierDirectory.load(next)
            entries = ((if (next == null) emptyList() else entries) + page.suppliers).distinctBy { it.id }
            cursor = page.nextCursor
        } catch (_: Exception) { error = "We could not load suppliers. Please try again." }
        finally { busy = false }
    }
    LaunchedEffect(Unit) {
        load()
        if (canSave) onSync().onFailure { error = "Saved suppliers could not sync. Check your connection and refresh." }
    }
    val shown = entries.filter { p ->
        (category.isBlank() || p.category.equals(category.trim(), true)) &&
        (city.isBlank() || p.city.contains(city.trim(), true)) &&
        (province.isBlank() || p.province.contains(province.trim(), true)) &&
        "${p.businessName} ${p.description} ${p.delivery}".contains(search.trim(), true)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Find suppliers in your area", style = MaterialTheme.typography.titleMedium)
            Text("Browse approved public listings. Your saved suppliers belong to your bakery.")
            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Business or product") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City or town") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = province, onValueChange = { province = it }, label = { Text("Province") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category (optional)") }, supportingText = { Text("Ingredients, Packaging, Equipment or Decorating") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedButton(enabled = !busy && saving.isBlank(), onClick = { scope.launch {
                load()
                if (canSave) onSync().onFailure { error = "Saved suppliers could not sync. Please try again." }
            } }) { Text("Refresh suppliers") }
            Text("Refresh to receive changes saved on the website.")
            if (busy) Text("Loading suppliers…")
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (!busy && error.isBlank() && shown.isEmpty()) Text(if (entries.isEmpty()) "No suppliers to display yet." else "No matches in the loaded suppliers.")
            shown.forEach { supplier ->
                HorizontalDivider()
                Text(supplier.businessName, style = MaterialTheme.typography.titleSmall)
                Text("${supplier.city}, ${supplier.province} · ${supplier.category}")
                Text(supplier.description)
                if (supplier.delivery.isNotBlank()) Text("Delivery / collection: ${supplier.delivery}")
                OutlinedButton(enabled = saving.isBlank() && supplier.id !in savedIds, onClick = {
                    if (!canSave) onUnlock() else scope.launch {
                        saving = supplier.id; error = ""
                        try { onSave(supplier).onFailure { error = "We could not save this supplier. Please try again." } }
                        finally { saving = "" }
                    }
                }) { Text(if (supplier.id in savedIds) "Saved to my suppliers" else if (saving == supplier.id) "Saving…" else if (!canSave) "Unlock supplier saving" else "Save to my suppliers") }
            }
            if (cursor != null) {
                Text("Filters apply to suppliers loaded so far.")
                OutlinedButton(enabled = !busy, onClick = { scope.launch { load(cursor) } }) { Text("Load more suppliers") }
            }
        }
    }
}
