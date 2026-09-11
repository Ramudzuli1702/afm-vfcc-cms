package com.afmvfcc.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afmvfcc.app.data.model.Session
import com.afmvfcc.app.ui.AppViewModel
import com.afmvfcc.app.ui.theme.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: AppViewModel,
    onSessionSelected: (Session) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val sessions       by viewModel.sessions.collectAsState()
    val isLoading      by viewModel.sessionsLoading.collectAsState()
    val error          by viewModel.sessionsError.collectAsState()
    val loggedInUser   by viewModel.loggedInUser.collectAsState()

    var showNewDialog  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadSessions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sessions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Welcome, $loggedInUser",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy, titleContentColor = White),
                actions = {
                    IconButton(onClick = { viewModel.loadSessions() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = White)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = White)
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Default.Logout, "Logout", tint = White)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("New Session", color = White) },
                icon = { Icon(Icons.Default.Add, null, tint = White) },
                onClick = { showNewDialog = true },
                containerColor = Navy
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Navy)
                }
                error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.WifiOff, null, tint = TextGrey,
                            modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(error!!, color = TextGrey, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadSessions() },
                            colors = ButtonDefaults.buttonColors(containerColor = Navy)) {
                            Text("Retry")
                        }
                    }
                }
                sessions.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.EventNote, null, tint = TextMuted,
                            modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No sessions found.", color = TextGrey, fontSize = 15.sp)
                        Text("Create one or check the CMS desktop app.",
                            color = TextMuted, fontSize = 13.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sessions) { session ->
                            SessionCard(session) { onSessionSelected(session) }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        NewSessionDialog(
            onDismiss = { showNewDialog = false },
            onConfirm  = { name, date ->
                viewModel.createSession(name, date) { session ->
                    showNewDialog = false
                    onSessionSelected(session)
                }
            }
        )
    }
}

@Composable
private fun SessionCard(session: Session, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = LightBlue,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.EventAvailable, null,
                    tint = Navy, modifier = Modifier
                        .padding(10.dp)
                        .fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(session.name, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = TextDark)
                Spacer(Modifier.height(2.dp))
                Text(session.date, fontSize = 12.sp, color = TextGrey)
                if (session.ministry.isNotEmpty() && session.ministry != "All")
                    Text(session.ministry, fontSize = 11.sp, color = TextMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${session.presentCount}", fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, color = Navy)
                Text("present", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun NewSessionDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, date) },
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("New Attendance Session", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Session Name") },
                    placeholder = { Text("e.g. Sunday Service 16 Mar") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = date, onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    )
}
