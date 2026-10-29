package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BatchPink

/** Use the same Firebase signup as the website; local Room rows are not cloud accounts. */
@Composable
fun WebsiteRegistrationScreen(onBack: () -> Unit, onSignIn: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Create your BatchBoss account", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Complete your secure bakery signup on the BatchBoss website, then return here and sign in with the same email and password.")
        Spacer(Modifier.height(12.dp))
        Text("If a sales partner referred you, use their signup link. Payments are managed through the website.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            error = null
            try { uriHandler.openUri("https://app.batchboss.co.za/?signup=1") }
            catch (_: Exception) { error = "Unable to open the website. Visit app.batchboss.co.za to create your account." }
        }, colors = ButtonDefaults.buttonColors(containerColor = BatchPink)) {
            Text("Continue to secure signup")
        }
        TextButton(onClick = onSignIn) { Text("I have an account — sign in") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
