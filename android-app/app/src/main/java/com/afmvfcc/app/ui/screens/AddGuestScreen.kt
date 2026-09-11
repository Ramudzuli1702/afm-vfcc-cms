package com.afmvfcc.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.afmvfcc.app.data.model.GuestRecord
import com.afmvfcc.app.ui.AppViewModel
import com.afmvfcc.app.ui.theme.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGuestScreen(
    sessionId: Int,
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val subBranches by viewModel.subBranches.collectAsState()

    var name            by remember { mutableStateOf("") }
    var phone           by remember { mutableStateOf("") }
    var gender          by remember { mutableStateOf("") }
    var selectedBranch  by remember { mutableStateOf<Int?>(null) }
    var invitedBy       by remember { mutableStateOf("") }
    var wantsMembership by remember { mutableStateOf(false) }
    var prayerRequest   by remember { mutableStateOf("") }
    var genderExpanded  by remember { mutableStateOf(false) }
    var branchExpanded  by remember { mutableStateOf(false) }
    var nameError       by remember { mutableStateOf(false) }
    var saved           by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Guest", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy, titleContentColor = White),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = White)
                    }
                }
            )
        }
    ) { padding ->

        if (saved) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = SuccessGreen, modifier = Modifier.size(64.dp))
                    Text("Guest recorded!", fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, color = TextDark)
                    Text("They will be synced to the CMS when you tap Sync.",
                        color = TextGrey, fontSize = 13.sp)

                    // ── WhatsApp share card ──────────────────────────────
                    val guestSummary = buildGuestSummary(
                        name            = name,
                        phone           = phone,
                        gender          = gender,
                        invitedBy       = invitedBy,
                        wantsMembership = wantsMembership,
                        prayerRequest   = prayerRequest
                    )
                    val context = androidx.compose.ui.platform.LocalContext.current

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Share, null,
                                    tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Share guest details",
                                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Text(guestSummary,
                                fontSize = 12.sp, color = TextGrey,
                                lineHeight = 18.sp)
                            Button(
                                onClick = { shareOnWhatsApp(context, guestSummary) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF25D366))
                            ) {
                                Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share on WhatsApp", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    // ────────────────────────────────────────────────────

                    Spacer(Modifier.height(4.dp))
                    Button(onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Navy),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Back to Attendance") }

                    OutlinedButton(
                        onClick = {
                            name = ""; phone = ""; gender = ""; selectedBranch = null
                            invitedBy = ""; wantsMembership = false; prayerRequest = ""; saved = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add Another Guest") }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Guest Information", fontWeight = FontWeight.Bold,
                fontSize = 18.sp, color = TextDark)
            Text("This guest will be saved locally and synced when you tap Sync.",
                fontSize = 13.sp, color = TextGrey)

            HorizontalDivider()

            // Full name *
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Full Name *") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                isError = nameError,
                supportingText = if (nameError) {{ Text("Name is required") }} else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Phone
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                leadingIcon = { Icon(Icons.Default.Phone, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Gender
            ExposedDropdownMenuBox(
                expanded = genderExpanded,
                onExpandedChange = { genderExpanded = it }
            ) {
                OutlinedTextField(
                    value = gender,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Gender") },
                    leadingIcon = { Icon(Icons.Default.Wc, null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(genderExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(
                    expanded = genderExpanded,
                    onDismissRequest = { genderExpanded = false }
                ) {
                    listOf("Male", "Female").forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g) },
                            onClick = { gender = g; genderExpanded = false }
                        )
                    }
                }
            }

            // Sub-branch
            if (subBranches.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = branchExpanded,
                    onExpandedChange = { branchExpanded = it }
                ) {
                    OutlinedTextField(
                        value = subBranches.find { it.id == selectedBranch }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sub-Branch / Area") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(branchExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = branchExpanded,
                        onDismissRequest = { branchExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None / Unknown") },
                            onClick = { selectedBranch = null; branchExpanded = false }
                        )
                        subBranches.forEach { sb ->
                            DropdownMenuItem(
                                text = { Text(sb.name) },
                                onClick = { selectedBranch = sb.id; branchExpanded = false }
                            )
                        }
                    }
                }
            }

            // Invited by
            OutlinedTextField(
                value = invitedBy,
                onValueChange = { invitedBy = it },
                label = { Text("Invited By") },
                leadingIcon = { Icon(Icons.Default.GroupAdd, null) },
                placeholder = { Text("Name of member who invited them") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            HorizontalDivider()

            // Wants membership
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (wantsMembership) SuccessGreen.copy(alpha = 0.08f)
                                     else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (wantsMembership) Icons.Default.HowToReg else Icons.Default.PersonOutline,
                        null, tint = if (wantsMembership) SuccessGreen else TextGrey,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Interested in Full Membership",
                            fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Guest would like to join AFM VFCC",
                            fontSize = 12.sp, color = TextGrey)
                    }
                    Switch(
                        checked = wantsMembership,
                        onCheckedChange = { wantsMembership = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = White,
                            checkedTrackColor = SuccessGreen)
                    )
                }
            }

            // Prayer request
            OutlinedTextField(
                value = prayerRequest,
                onValueChange = { prayerRequest = it },
                label = { Text("Prayer Request for the Bishop") },
                leadingIcon = { Icon(Icons.Default.VolunteerActivism, null) },
                placeholder = { Text("Optional — anything the Bishop should know") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                maxLines = 5,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(Modifier.height(8.dp))

            // Save button
            Button(
                onClick = {
                    if (name.isBlank()) { nameError = true; return@Button }
                    viewModel.addGuest(
                        GuestRecord(
                            name          = name.trim(),
                            phone         = phone.trim(),
                            gender        = gender,
                            subBranchId   = selectedBranch,
                            invitedBy     = invitedBy.trim(),
                            wantsMembership = wantsMembership,
                            prayerRequest = prayerRequest.trim(),
                            sessionId     = sessionId,
                            visitDate     = LocalDate.now().toString()
                        )
                    )
                    saved = true
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Guest", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

private fun buildGuestSummary(
    name: String,
    phone: String,
    gender: String,
    invitedBy: String,
    wantsMembership: Boolean,
    prayerRequest: String
): String = buildString {
    appendLine("*AFM VFCC — Guest Record*")
    appendLine()
    appendLine("*Name:* $name")
    if (phone.isNotBlank())          appendLine("*Phone:* $phone")
    if (gender.isNotBlank())         appendLine("*Gender:* $gender")
    if (invitedBy.isNotBlank())      appendLine("*Invited by:* $invitedBy")
    if (wantsMembership)             appendLine("*Interested in membership:* Yes ✅")
    if (prayerRequest.isNotBlank()) {
        appendLine()
        appendLine("*Prayer request:*")
        appendLine(prayerRequest)
    }
}.trimEnd()

private fun shareOnWhatsApp(context: android.content.Context, text: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type    = "text/plain"
        setPackage("com.whatsapp")          // targets WhatsApp directly
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    // Fall back to the generic share sheet if WhatsApp isn't installed
    val chooser = if (intent.resolveActivity(context.packageManager) != null) {
        intent
    } else {
        android.content.Intent.createChooser(
            intent.apply { setPackage(null) }, "Share guest details"
        )
    }
    context.startActivity(chooser)
}
