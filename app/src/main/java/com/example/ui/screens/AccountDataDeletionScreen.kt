package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.UserAccountEntity
import com.example.ui.components.BatchBossBrandLogo
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDataDeletionScreen(
    userAccount: UserAccountEntity?,
    onBack: () -> Unit,
    onSubmitRequest: (reason: String) -> Unit,
    onInstantDelete: () -> Unit
) {
    val context = LocalContext.current
    val deletionUrl = "https://batchboss.co.za/delete-account"

    var selectedReason by remember { mutableStateOf("Data privacy & account removal") }
    var customNotes by remember { mutableStateOf("") }
    var showInstantDeleteDialog by remember { mutableStateOf(false) }
    var requestSubmitted by remember { mutableStateOf(false) }

    val reasons = listOf(
        "Data privacy & account removal",
        "Closing or pausing bakery operations",
        "Switching to another system",
        "No longer need the application",
        "Other reason"
    )

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("BatchBoss Deletion Link", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Google Play Deletion Link copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun openBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Visit: $url", Toast.LENGTH_LONG).show()
        }
    }

    fun openEmail() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:info@batchboss.co.za")
                putExtra(Intent.EXTRA_SUBJECT, "Account and Data Deletion Request - BatchBoss")
                putExtra(Intent.EXTRA_TEXT, "Account Email: ${userAccount?.email ?: ""}\nBakery: ${userAccount?.bakeryName ?: ""}\nPlease delete my account and associated data.")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Email: info@batchboss.co.za", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Account & Data Deletion",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Google Play Data Safety & Privacy",
                            fontSize = 11.sp,
                            color = DarkTextMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_deletion")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceWhite
                )
            )
        },
        containerColor = SoftBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BatchBossBrandLogo(size = 36, showVersionBadge = true)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Account & Data Management",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You maintain complete ownership of your data. You can delete your account and all associated bakery records directly inside the app or via our Google Play compliant web page.",
                            fontSize = 13.sp,
                            color = DarkTextMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Google Play Official Deletion Link Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(BatchPink.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Link, contentDescription = null, tint = BatchPink, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Google Play Deletion Link",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = DarkText
                                )
                                Text(
                                    text = "Required URL for Google Play Store Data Safety",
                                    fontSize = 12.sp,
                                    color = DarkTextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // URL container
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = SoftBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Web Deletion URL:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkTextMuted
                                    )
                                    Text(
                                        text = deletionUrl,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BatchPink
                                    )
                                }
                                IconButton(
                                    onClick = { copyToClipboard(deletionUrl) },
                                    modifier = Modifier.testTag("btn_copy_deletion_url")
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Link", tint = DarkText)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { copyToClipboard(deletionUrl) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Link", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { openBrowser(deletionUrl) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
                            ) {
                                Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Page", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Scope of Data Subject to Deletion Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Data Scope & Retention Policy",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = DarkText
                                )
                                Text(
                                    text = "What happens when you request deletion",
                                    fontSize = 12.sp,
                                    color = DarkTextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val dataItems = listOf(
                            "Bakery Account & Profile: Name, email, phone, location, password." to Icons.Outlined.Person,
                            "Recipes & Ingredients: Formulas, unit costs, pricing margins, batches." to Icons.Outlined.RestaurantMenu,
                            "Inventory & Suppliers: Stock on hand, supplier details, alert thresholds." to Icons.Outlined.Inventory2,
                            "Invoices & Quotes: Customer details, bespoke line items, estimates." to Icons.Outlined.ReceiptLong,
                            "Session & Activity Logs: Login histories, task records, preferences." to Icons.Outlined.History
                        )

                        dataItems.forEach { (text, icon) ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(icon, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = text, fontSize = 13.sp, color = DarkText, lineHeight = 18.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = "Notice: In accordance with statutory financial regulations, tax-compliant records (e.g. issued fiscal invoices) may be retained for the minimum period mandated by law.",
                                modifier = Modifier.padding(10.dp),
                                fontSize = 11.sp,
                                color = Color(0xFF92400E),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Request Deletion Form Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Send, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Submit Deletion Request",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = DarkText
                                )
                                Text(
                                    text = "Processed by the BatchBoss administrative team",
                                    fontSize = 12.sp,
                                    color = DarkTextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (requestSubmitted) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF0FDF4),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Deletion Request Received",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF166534)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Your request has been logged. Our administrative team will process the complete deletion of your account within 48 hours.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF166534),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Current Account: ${userAccount?.email ?: "Active User"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Select Reason:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkTextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            reasons.forEach { reason ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedReason = reason }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedReason == reason,
                                        onClick = { selectedReason = reason },
                                        colors = RadioButtonDefaults.colors(selectedColor = BatchPink)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = reason, fontSize = 13.sp, color = DarkText)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = customNotes,
                                onValueChange = { customNotes = it },
                                label = { Text("Additional notes (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                minLines = 2,
                                maxLines = 4
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val fullReason = if (customNotes.isNotBlank()) "$selectedReason - $customNotes" else selectedReason
                                    onSubmitRequest(fullReason)
                                    requestSubmitted = true
                                    Toast.makeText(context, "Deletion request submitted successfully", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.fillMaxWidth().testTag("btn_submit_deletion_request"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7280))
                            ) {
                                Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Submit Deletion Request", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Immediate In-App Wipe (Guarded)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Instant In-App Deletion",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF991B1B)
                                )
                                Text(
                                    text = "Immediate and permanent purge from this device",
                                    fontSize = 12.sp,
                                    color = Color(0xFFB91C1C)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Tapping this will instantly erase all your recipes, ingredient costs, inventory, customers, quotes, and invoices immediately without waiting for administrative review. This action cannot be reversed.",
                            fontSize = 12.sp,
                            color = Color(0xFF7F1D1D),
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { showInstantDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth().testTag("btn_instant_delete_account"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete My Account & Data Immediately", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Direct Support Contact
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Questions & Privacy Inquiries",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You can also contact our Data Protection team directly:",
                            fontSize = 13.sp,
                            color = DarkTextMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openEmail() }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Email, contentDescription = null, tint = BatchPink, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("info@batchboss.co.za", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DarkText)
                        }
                    }
                }
            }

            // Copyright footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "© 2026 Tada Innovations (Pty) Ltd. All rights reserved. BatchBoss™",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = DarkTextMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "BatchBoss™ is a trademark of Tada Innovations (Pty) Ltd.",
                        fontSize = 10.sp,
                        color = DarkTextMuted.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Confirmation Dialog for Instant Deletion
    if (showInstantDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showInstantDeleteDialog = false },
            icon = {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(36.dp))
            },
            title = {
                Text(
                    text = "Permanently Delete Account?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Are you completely sure you want to delete your BatchBoss account?\n\nThis will permanently destroy all your saved recipes, ingredient costs, inventory records, quotes, invoices, and customer contacts. This cannot be undone.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = DarkText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showInstantDeleteDialog = false
                        onInstantDelete()
                        Toast.makeText(context, "Account and all data permanently deleted", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("btn_confirm_instant_delete")
                ) {
                    Text("Yes, Delete Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showInstantDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
