package com.afmvfcc.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afmvfcc.app.data.model.AttendanceState
import com.afmvfcc.app.data.model.GuestRecord
import com.afmvfcc.app.ui.AppViewModel
import com.afmvfcc.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    sessionId: Int,
    sessionName: String,
    viewModel: AppViewModel,
    onAddGuest: () -> Unit,
    onBack: () -> Unit
) {
    val attendanceList by viewModel.attendanceList.collectAsState()
    val isLoading      by viewModel.attendanceLoading.collectAsState()
    val isSyncing      by viewModel.isSyncing.collectAsState()
    val syncResult     by viewModel.syncResult.collectAsState()
    val pendingGuests  by viewModel.pendingGuests.collectAsState()

    var search           by remember { mutableStateOf("") }
    var selectedMinistry by remember { mutableStateOf("All") }
    var showSyncDialog   by remember { mutableStateOf(false) }
    var showMarkAllMenu  by remember { mutableStateOf(false) }
    var showGuestsSheet  by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        if (viewModel.attendanceList.value.isEmpty()) {
            viewModel.loadAttendance(sessionId)
        }
        viewModel.loadSubBranches()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(syncResult) {
        syncResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncResult()
        }
    }

    val ministries = remember(attendanceList) {
        val mins = attendanceList
            .flatMap { it.member.ministry.split(", ").filter { m -> m.isNotBlank() } }
            .distinct().sorted()
        listOf("All") + mins
    }

    val filtered = remember(attendanceList, search, selectedMinistry) {
        attendanceList.filter { state ->
            val matchesSearch = search.isBlank() ||
                    state.member.name.contains(search, ignoreCase = true) ||
                    state.member.subBranch.contains(search, ignoreCase = true)
            val matchesMin = selectedMinistry == "All" ||
                    state.member.ministry.contains(selectedMinistry, ignoreCase = true)
            matchesSearch && matchesMin
        }
    }

    // ── Guest list bottom sheet ───────────────────────────────
    if (showGuestsSheet) {
        GuestsBottomSheet(
            guests    = pendingGuests,
            onDismiss = { showGuestsSheet = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(sessionName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "${viewModel.presentCount} / ${viewModel.totalCount} present",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Navy, titleContentColor = White
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null, tint = White)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMarkAllMenu = true }) {
                                Icon(Icons.Default.MoreVert, null, tint = White)
                            }
                            DropdownMenu(
                                expanded = showMarkAllMenu,
                                onDismissRequest = { showMarkAllMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Mark All Present") },
                                    leadingIcon = {
                                        Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen)
                                    },
                                    onClick = {
                                        viewModel.markAll(true)
                                        showMarkAllMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear All") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Cancel, null, tint = DangerRed)
                                    },
                                    onClick = {
                                        viewModel.markAll(false)
                                        showMarkAllMenu = false
                                    }
                                )
                            }
                        }
                    }
                )

                // Progress bar
                if (!isLoading && attendanceList.isNotEmpty()) {
                    val progress = viewModel.presentCount.toFloat() / viewModel.totalCount
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Gold,
                        trackColor = Navy.copy(alpha = 0.3f)
                    )
                }

                // Search bar
                Surface(color = Navy) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = {
                            Text("Search members...", color = White.copy(alpha = 0.5f))
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, null, tint = White.copy(alpha = 0.7f))
                        },
                        trailingIcon = {
                            if (search.isNotEmpty()) {
                                IconButton(onClick = { search = "" }) {
                                    Icon(Icons.Default.Clear, null, tint = White.copy(alpha = 0.7f))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor     = White,
                            unfocusedTextColor   = White,
                            focusedBorderColor   = Gold,
                            unfocusedBorderColor = White.copy(alpha = 0.3f),
                            cursorColor          = Gold
                        )
                    )
                }

                // Ministry filter chips
                if (ministries.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Navy)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ministries.forEach { min ->
                            val selected = min == selectedMinistry
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (selected) Gold else White.copy(alpha = 0.15f)
                                    )
                                    .clickable { selectedMinistry = min }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = min,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color     = if (selected) Navy else White
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            BottomAppBar(containerColor = White, tonalElevation = 8.dp) {

                // ── View Guests button — only shown when guests exist ──
                if (pendingGuests.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showGuestsSheet = true },
                        shape  = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.PeopleAlt, null,
                            modifier = Modifier.size(16.dp),
                            tint = Gold
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${pendingGuests.size} Guest${if (pendingGuests.size != 1) "s" else ""}",
                            fontWeight = FontWeight.Bold,
                            color = Gold
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Add guest button
                OutlinedButton(
                    onClick = onAddGuest,
                    shape  = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Guest")
                }

                // Sync button
                Button(
                    onClick  = { showSyncDialog = true },
                    enabled  = !isSyncing,
                    colors   = ButtonDefaults.buttonColors(containerColor = Navy),
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            color       = White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSyncing) "Syncing..." else "Sync to CMS")
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Navy)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading members...", color = TextGrey)
                }
            }
        } else {
            LazyColumn(
                modifier        = Modifier.fillMaxSize().padding(padding),
                contentPadding  = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (filtered.isEmpty() && search.isNotBlank()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No members match: $search",
                                color = TextGrey, fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.member.id }) { state ->
                        MemberAttendanceRow(state) {
                            viewModel.toggleAttendance(state.member.id)
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // Sync confirmation dialog
    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            title = { Text("Sync Attendance", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will send the following to the CMS:")
                    Text(
                        "  - ${viewModel.presentCount} of ${viewModel.totalCount} members marked present",
                        color = TextGrey, fontSize = 13.sp
                    )
                    if (pendingGuests.isNotEmpty()) {
                        Text(
                            "  - ${pendingGuests.size} guest record(s)",
                            color = TextGrey, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Make sure you are connected to the church WiFi.",
                        fontSize = 12.sp, color = TextMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSyncDialog = false
                        viewModel.syncAll(sessionId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) { Text("Sync Now") }
            },
            dismissButton = {
                TextButton(onClick = { showSyncDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Guests bottom sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestsBottomSheet(
    guests: List<GuestRecord>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = White,
        dragHandle       = { BottomSheetDefaults.DragHandle() }
    ) {
        // Sheet header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PeopleAlt, null, tint = Gold, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Recorded Guests (${guests.size})",
                fontWeight = FontWeight.Bold,
                fontSize   = 17.sp,
                color      = TextDark
            )
            Spacer(Modifier.weight(1f))
            // Share ALL guests in one message
            if (guests.size > 1) {
                IconButton(onClick = {
                    val allText = guests.joinToString("\n\n─────────────\n\n") {
                        buildGuestSummary(it)
                    }
                    shareText(context, allText)
                }) {
                    Icon(Icons.Default.Share, "Share all", tint = Navy)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier            = Modifier.navigationBarsPadding()
        ) {
            items(guests, key = { it.name + it.visitDate + it.phone }) { guest ->
                GuestCard(
                    guest   = guest,
                    onShare = { shareText(context, buildGuestSummary(guest)) }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Individual guest card
// ─────────────────────────────────────────────────────────────

@Composable
private fun GuestCard(guest: GuestRecord, onShare: () -> Unit) {
    Card(
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFF5F6FA)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Initials avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Navy.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    guest.name.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    color      = Navy
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    guest.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    color      = TextDark
                )
                Spacer(Modifier.height(4.dp))

                // Only render rows for non-blank / true fields
                if (guest.phone.isNotBlank())
                    GuestDetailRow(Icons.Default.Phone, guest.phone)
                if (guest.gender.isNotBlank())
                    GuestDetailRow(Icons.Default.Wc, guest.gender)
                if (guest.invitedBy.isNotBlank())
                    GuestDetailRow(Icons.Default.GroupAdd, "Invited by ${guest.invitedBy}")
                if (guest.wantsMembership)
                    GuestDetailRow(
                        Icons.Default.HowToReg,
                        "Interested in membership",
                        tint = Gold
                    )
                if (guest.prayerRequest.isNotBlank())
                    GuestDetailRow(
                        Icons.Default.VolunteerActivism,
                        guest.prayerRequest,
                        maxLines = 3
                    )
            }

            // Per-guest share button
            IconButton(
                onClick  = onShare,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.Share, "Share",
                    tint     = TextGrey,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun GuestDetailRow(
    icon:     ImageVector,
    text:     String,
    tint:     Color = TextGrey,
    maxLines: Int   = 1
) {
    Row(
        modifier          = Modifier.padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            fontSize  = 12.sp,
            color     = tint,
            maxLines  = maxLines,
            overflow  = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Share helpers
// ─────────────────────────────────────────────────────────────

private fun buildGuestSummary(guest: GuestRecord): String = buildString {
    appendLine("*AFM VFCC — Guest Record*")
    appendLine()
    appendLine("*Name:* ${guest.name}")
    if (guest.phone.isNotBlank())          appendLine("*Phone:* ${guest.phone}")
    if (guest.gender.isNotBlank())         appendLine("*Gender:* ${guest.gender}")
    if (guest.invitedBy.isNotBlank())      appendLine("*Invited by:* ${guest.invitedBy}")
    if (guest.wantsMembership)             appendLine("*Interested in membership:* Yes ✅")
    if (guest.prayerRequest.isNotBlank()) {
        appendLine()
        appendLine("*Prayer request:*")
        appendLine(guest.prayerRequest)
    }
}.trimEnd()

private fun shareText(context: android.content.Context, text: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        setPackage("com.whatsapp")
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    val chooser = if (intent.resolveActivity(context.packageManager) != null) {
        intent
    } else {
        android.content.Intent.createChooser(intent.apply { setPackage(null) }, "Share guest")
    }
    context.startActivity(chooser)
}

// ─────────────────────────────────────────────────────────────
// Member attendance row (unchanged)
// ─────────────────────────────────────────────────────────────

@Composable
private fun MemberAttendanceRow(state: AttendanceState, onToggle: () -> Unit) {
    val bgColor = if (state.isPresent) SuccessGreen.copy(alpha = 0.08f) else White

    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape     = RoundedCornerShape(10.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(if (state.isPresent) 0.dp else 1.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (state.isPresent) SuccessGreen.copy(alpha = 0.2f) else LightBlue,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    state.member.name.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                    color      = if (state.isPresent) SuccessGreen else Navy
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.member.name,
                    fontWeight = FontWeight.Medium,
                    fontSize   = 15.sp,
                    color      = TextDark
                )
                if (state.member.subBranch.isNotEmpty())
                    Text(state.member.subBranch, fontSize = 12.sp, color = TextMuted)
                if (state.member.ministry.isNotEmpty())
                    Text(state.member.ministry, fontSize = 11.sp, color = Gold.copy(alpha = 0.8f))
            }

            Checkbox(
                checked         = state.isPresent,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor   = SuccessGreen,
                    uncheckedColor = TextMuted
                )
            )
        }
    }
}