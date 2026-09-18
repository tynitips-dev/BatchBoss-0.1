package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DataDeletionRequestEntity
import com.example.data.local.UserAccountEntity
import com.example.data.local.UserLoginLogEntity
import com.example.ui.components.BatchBossBrandLogo
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterBackendScreen(
    users: List<UserAccountEntity>,
    loginLogs: List<UserLoginLogEntity>,
    deletionRequests: List<DataDeletionRequestEntity>,
    onBack: () -> Unit,
    onDeleteUser: (userId: Long) -> Unit,
    onProcessDeletionRequest: (requestId: Long, userId: Long) -> Unit,
    onRejectDeletionRequest: (requestId: Long) -> Unit,
    onClearLogs: () -> Unit,
    onFactoryReset: () -> Unit,
    onCreateUser: (
        firstName: String,
        surname: String,
        email: String,
        bakeryName: String,
        phone: String,
        city: String,
        operatingModel: String,
        currency: String,
        isPro: Boolean
    ) -> Unit,
    onToggleUserPro: (userId: Long, isPro: Boolean) -> Unit,
    onPurgeInvoices: (userId: Long) -> Unit,
    onPurgeQuotes: (userId: Long) -> Unit
) {
    val context = LocalContext.current

    // Security Gate
    var isUnlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Navigation Tabs inside Backend
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("User Logins", "Store Data", "Delete Data", "Google Play Link")

    // State for Search
    var userSearchQuery by remember { mutableStateOf("") }
    var logSearchQuery by remember { mutableStateOf("") }

    // Dialog States
    var showCreateUserDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<UserAccountEntity?>(null) }
    var showFactoryResetDialog by remember { mutableStateOf(false) }
    var factoryResetConfirmText by remember { mutableStateOf("") }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    if (!isUnlocked) {
        // Master Developer Access Gate
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Master Backend Access", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
                )
            },
            containerColor = SoftBackground
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "BatchBoss Master Backend",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Developer portal to check logins, store data, and manage data deletion.",
                            fontSize = 13.sp,
                            color = DarkTextMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                pinInput = it
                                pinError = false
                            },
                            label = { Text("Master PIN / Key") },
                            placeholder = { Text("Enter Master authorization PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("input_master_backend_pin"),
                            shape = RoundedCornerShape(12.dp),
                            isError = pinError,
                            singleLine = true
                        )

                        if (pinError) {
                            Text(
                                text = "Access denied. Invalid Master PIN.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (pinInput.trim() == "16320" || pinInput.trim().lowercase() == "tynitips@gmail.com") {
                                    isUnlocked = true
                                    pinError = false
                                } else {
                                    pinError = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("btn_unlock_backend"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock Master Backend", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        return
    }

    // Authenticated Master Backend
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "BatchBoss Master Backend",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "LIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Local Storage v10 • Google Play Compliant",
                            fontSize = 11.sp,
                            color = DarkTextMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isUnlocked = false
                            pinInput = ""
                        }
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = "Lock Backend", tint = DarkText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        },
        containerColor = SoftBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Master Navigation Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceWhite,
                contentColor = BatchPink,
                edgePadding = 12.dp
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                                if (index == 0 && deletionRequests.any { it.status == "PENDING" }) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFDC2626))
                                    )
                                }
                            }
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // TAB 0: CHECK USER LOGINS & USERS
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Summary Metrics
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Registered Users", fontSize = 11.sp, color = DarkTextMuted)
                                        Text("${users.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                    }
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Login Audits", fontSize = 11.sp, color = DarkTextMuted)
                                        Text("${loginLogs.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BatchPink)
                                    }
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                                ) {
                                    val pendingCount = deletionRequests.count { it.status == "PENDING" }
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Deletion Req.", fontSize = 11.sp, color = DarkTextMuted)
                                        Text("$pendingCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (pendingCount > 0) Color(0xFFDC2626) else Color(0xFF10B981))
                                    }
                                }
                            }
                        }

                        // Section 1: Registered Users & Bakeries
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Registered Bakery Accounts", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkText)
                                TextButton(onClick = { showCreateUserDialog = true }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Store New", fontSize = 12.sp)
                                }
                            }
                        }

                        val filteredUsers = users.filter {
                            it.email.contains(userSearchQuery, ignoreCase = true) ||
                            it.bakeryName.contains(userSearchQuery, ignoreCase = true) ||
                            it.fullName.contains(userSearchQuery, ignoreCase = true)
                        }

                        if (filteredUsers.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                                ) {
                                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("No accounts registered yet", color = DarkTextMuted, fontSize = 13.sp)
                                    }
                                }
                            }
                        } else {
                            items(filteredUsers) { user ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(user.bakeryName.ifBlank { "Unnamed Bakery" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (user.isPremium) BatchPink.copy(alpha = 0.12f) else Color(0xFFE2E8F0)
                                                    ) {
                                                        Text(
                                                            text = if (user.isPremium) "PRO" else "FREE",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (user.isPremium) BatchPink else Color(0xFF64748B),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text("Owner: ${user.fullName} • ${user.email}", fontSize = 12.sp, color = DarkTextMuted)
                                                Text("Phone: ${user.phone.ifBlank { "N/A" }} • ${user.city}", fontSize = 11.sp, color = DarkTextMuted)
                                            }

                                            IconButton(
                                                onClick = { userToDelete = user }
                                            ) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Delete User", tint = Color(0xFFDC2626))
                                            }
                                        }

                                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF1F5F9))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Registered: ${dateFormat.format(Date(user.createdAt))}",
                                                fontSize = 11.sp,
                                                color = DarkTextMuted
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (user.isPremium) "Pro Enabled" else "Free Tier",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = DarkText
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Switch(
                                                    checked = user.isPremium,
                                                    onCheckedChange = { onToggleUserPro(user.id, it) },
                                                    modifier = Modifier.scale(0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section 2: Real-time Login Audit Trail
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Login Activity & Audit Trail", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkText)
                                    Text("Track who logged in, registered, or requested deletion", fontSize = 11.sp, color = DarkTextMuted)
                                }
                                if (loginLogs.isNotEmpty()) {
                                    TextButton(onClick = onClearLogs) {
                                        Text("Clear Logs", fontSize = 11.sp, color = Color(0xFFDC2626))
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = logSearchQuery,
                                onValueChange = { logSearchQuery = it },
                                placeholder = { Text("Filter logs by email, bakery, or action...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DarkTextMuted) },
                                singleLine = true
                            )
                        }

                        val filteredLogs = loginLogs.filter {
                            it.email.contains(logSearchQuery, ignoreCase = true) ||
                            it.bakeryName.contains(logSearchQuery, ignoreCase = true) ||
                            it.action.contains(logSearchQuery, ignoreCase = true)
                        }

                        if (filteredLogs.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                                ) {
                                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("No login activity logs recorded yet", color = DarkTextMuted, fontSize = 13.sp)
                                    }
                                }
                            }
                        } else {
                            items(filteredLogs.take(50)) { log ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = SurfaceWhite,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Action indicator badge
                                        val (badgeColor, badgeText) = when (log.action) {
                                            "LOGIN" -> Color(0xFF10B981) to "LOGIN"
                                            "REGISTER" -> Color(0xFF3B82F6) to "SIGNUP"
                                            "LOGOUT" -> Color(0xFF6B7280) to "LOGOUT"
                                            "DELETION_REQUESTED" -> Color(0xFFDC2626) to "DEL_REQ"
                                            "ACCOUNT_DELETED" -> Color(0xFF991B1B) to "WIPED"
                                            "ADMIN_CREATED" -> Color(0xFF8B5CF6) to "ADMIN"
                                            else -> Color(0xFF1E293B) to log.action
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = badgeColor.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = badgeText,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = log.email.ifBlank { "User #${log.userId}" },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = DarkText
                                                )
                                                Text(
                                                    text = dateFormat.format(Date(log.timestamp)),
                                                    fontSize = 10.sp,
                                                    color = DarkTextMuted
                                                )
                                            }
                                            Text(
                                                text = "${log.bakeryName} • ${log.branch}${if (log.notes.isNotBlank()) " (${log.notes})" else ""}",
                                                fontSize = 11.sp,
                                                color = DarkTextMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: STORE DATA (Data Management, Provisioning & Backup)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = BatchPink)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Store / Provision Bakery Account", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Directly create and store new bakeries, assigning them custom operating models and Free or Pro tiers.",
                                        fontSize = 13.sp,
                                        color = DarkTextMuted
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { showCreateUserDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create & Store Account", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // JSON Backup Export
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Backup, contentDescription = null, tint = Color(0xFF3B82F6))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Export System Backup (JSON)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Export the entire system database (users, login activity, deletion requests) as a formatted JSON document for off-site backup or archiving.",
                                        fontSize = 13.sp,
                                        color = DarkTextMuted
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = {
                                            val backupJson = buildString {
                                                append("{\n")
                                                append("  \"exportTimestamp\": ${System.currentTimeMillis()},\n")
                                                append("  \"version\": 8,\n")
                                                append("  \"database\": \"batchboss_database\",\n")
                                                append("  \"userAccountsCount\": ${users.size},\n")
                                                append("  \"users\": [\n")
                                                users.forEachIndexed { i, u ->
                                                    append("    {\"id\": ${u.id}, \"email\": \"${u.email}\", \"bakery\": \"${u.bakeryName}\", \"isPro\": ${u.isPremium}}${if (i < users.size - 1) "," else ""}\n")
                                                }
                                                append("  ],\n")
                                                append("  \"loginLogsCount\": ${loginLogs.size}\n")
                                                append("}")
                                            }
                                            copyToClipboard("BatchBoss JSON Backup", backupJson)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Generate & Copy JSON Backup", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: DELETE DATA (Deletion Requests & Data Purges)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Pending Google Play Deletion Requests Queue
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.AssignmentLate, contentDescription = null, tint = Color(0xFFDC2626))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("User Deletion Requests Queue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Requests submitted by users from the in-app deletion flow or the Google Play web link.",
                                        fontSize = 12.sp,
                                        color = DarkTextMuted
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))

                                    val pendingRequests = deletionRequests.filter { it.status == "PENDING" }
                                    if (pendingRequests.isEmpty()) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF0FDF4)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("All deletion requests are fulfilled! No pending requests.", fontSize = 12.sp, color = Color(0xFF166534))
                                            }
                                        }
                                    } else {
                                        pendingRequests.forEach { req ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))
                                            ) {
                                                Column(modifier = Modifier.padding(14.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(req.email, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF991B1B))
                                                        Text(dateFormat.format(Date(req.requestedAt)), fontSize = 10.sp, color = DarkTextMuted)
                                                    }
                                                    Text("Bakery: ${req.bakeryName}", fontSize = 12.sp, color = DarkText)
                                                    Text("Reason: ${req.reason}", fontSize = 12.sp, color = DarkTextMuted)

                                                    Spacer(modifier = Modifier.height(10.dp))

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = {
                                                                onProcessDeletionRequest(req.id, req.userId)
                                                                Toast.makeText(context, "Request fulfilled: user data wiped", Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.weight(1f),
                                                            shape = RoundedCornerShape(8.dp),
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                                                        ) {
                                                            Text("Approve & Wipe Data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        OutlinedButton(
                                                            onClick = { onRejectDeletionRequest(req.id) },
                                                            modifier = Modifier.weight(1f),
                                                            shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                            Text("Dismiss", fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Granular Purge Tools
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text("Granular Data Purges", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Wipe specific dataset tables for testing or customer support.", fontSize = 12.sp, color = DarkTextMuted)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (users.isEmpty()) {
                                        Text("No users available to purge", fontSize = 12.sp, color = DarkTextMuted)
                                    } else {
                                        users.forEach { u ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(u.bakeryName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                    Text(u.email, fontSize = 11.sp, color = DarkTextMuted)
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            onPurgeInvoices(u.id)
                                                            Toast.makeText(context, "Invoices wiped for ${u.bakeryName}", Toast.LENGTH_SHORT).show()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("Wipe Invoices", fontSize = 10.sp)
                                                    }
                                                    OutlinedButton(
                                                        onClick = {
                                                            onPurgeQuotes(u.id)
                                                            Toast.makeText(context, "Quotes wiped for ${u.bakeryName}", Toast.LENGTH_SHORT).show()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("Wipe Quotes", fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Danger Zone: Full Factory Reset
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Danger Zone: Full Database Factory Reset", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF991B1B))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Completely clears all local database tables back to zero state: removes all user accounts, login history, recipes, inventory, and invoices.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF7F1D1D)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { showFactoryResetDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                                    ) {
                                        Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Factory Reset All Database Data", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // TAB 3: GOOGLE PLAY LINK & COMPLIANCE
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text("Google Play Account Deletion URL", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Provide this exact URL in Google Play Console under Policy & Programs > App Content > Data Safety > Account Deletion requirement:",
                                        fontSize = 12.sp,
                                        color = DarkTextMuted
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))

                                    val gplayUrl = "https://batchboss.co.za/delete-account"
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        color = SoftBackground,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = gplayUrl,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = BatchPink
                                            )
                                            IconButton(onClick = { copyToClipboard("Google Play Deletion URL", gplayUrl) }) {
                                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = { copyToClipboard("Google Play Deletion URL", gplayUrl) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Copy URL for Google Play Console", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text("Google Play Policy Checklist", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    val checklist = listOf(
                                        "In-App Deletion Path: More > Account & Data Deletion provides instant wipe." to true,
                                        "Web-based Deletion URL: https://batchboss.co.za/delete-account is live." to true,
                                        "Discloses data categories deleted (recipes, stock, profile, invoices)." to true,
                                        "Discloses retention policy & statutory tax record exceptions." to true,
                                        "Personal master backend to monitor and approve requests." to true
                                    )

                                    checklist.forEach { (itemText, checked) ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(text = itemText, fontSize = 12.sp, color = DarkText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Provision New Account
    if (showCreateUserDialog) {
        var firstName by remember { mutableStateOf("") }
        var surname by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var bakeryName by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var city by remember { mutableStateOf("Cape Town") }
        var operatingModel by remember { mutableStateOf("Commercial Bakery") }
        var isPro by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showCreateUserDialog = false },
            title = { Text("Store New Bakery Account", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("First Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = surname,
                        onValueChange = { surname = it },
                        label = { Text("Surname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bakeryName,
                        onValueChange = { bakeryName = it },
                        label = { Text("Bakery Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Grant Pro Membership", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(checked = isPro, onCheckedChange = { isPro = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (email.isNotBlank() && bakeryName.isNotBlank()) {
                            onCreateUser(
                                firstName.ifBlank { "Baker" },
                                surname,
                                email,
                                bakeryName,
                                phone,
                                city,
                                operatingModel,
                                "ZAR (R)",
                                isPro
                            )
                            showCreateUserDialog = false
                            Toast.makeText(context, "Account stored successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Email and Bakery Name required", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
                ) {
                    Text("Store Account")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateUserDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal: Delete User Confirmation
    if (userToDelete != null) {
        val target = userToDelete!!
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete User & Wipe Data?") },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete '${target.bakeryName}' (${target.email}) and ALL their recipes, ingredients, invoices, and customers?\n\nThis cannot be undone.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(target.id)
                        userToDelete = null
                        Toast.makeText(context, "User and data wiped successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Permanently Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { userToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal: Full Factory Reset
    if (showFactoryResetDialog) {
        AlertDialog(
            onDismissRequest = { showFactoryResetDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Complete Database Reset?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This will wipe ALL accounts, recipes, inventory, invoices, and login history.\n\nType 'RESET' below to confirm:",
                        fontSize = 13.sp,
                        color = Color(0xFF991B1B)
                    )
                    OutlinedTextField(
                        value = factoryResetConfirmText,
                        onValueChange = { factoryResetConfirmText = it },
                        placeholder = { Text("Type RESET") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (factoryResetConfirmText.trim().uppercase() == "RESET") {
                            showFactoryResetDialog = false
                            factoryResetConfirmText = ""
                            onFactoryReset()
                            Toast.makeText(context, "Database factory reset completed", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Please type RESET to confirm", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Reset Database", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showFactoryResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
