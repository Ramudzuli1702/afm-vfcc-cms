package com.afmvfcc.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afmvfcc.app.ui.AppViewModel
import com.afmvfcc.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val scope       = rememberCoroutineScope()
    val prefs       = viewModel.prefs
    val serverIp    by prefs.serverIp.collectAsState("")
    val serverPort  by prefs.serverPort.collectAsState("8080")

    var ip         by remember(serverIp)   { mutableStateOf(serverIp) }
    var port       by remember(serverPort) { mutableStateOf(serverPort) }
    var saved      by remember { mutableStateOf(false) }
    var testing    by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Settings", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = LightBlue)
            ) {
                Row(modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = Navy,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("How to connect",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Navy)
                        Spacer(Modifier.height(4.dp))
                        Text("1. On your phone, enable Mobile Hotspot (Settings > Hotspot).",
                            fontSize = 12.sp, color = TextGrey)
                        Text("2. On the CMS computer, connect to your phone hotspot via WiFi.",
                            fontSize = 12.sp, color = TextGrey)
                        Text("3. In the CMS, open the Attendance page - the IP address is shown next to APP SERVER IP at the top.",
                            fontSize = 12.sp, color = TextGrey)
                        Text("4. Enter that IP address below and tap Save Settings.",
                            fontSize = 12.sp, color = TextGrey)
                    }
                }
            }

            Text("CMS Server Address", fontWeight = FontWeight.Bold,
                fontSize = 16.sp, color = TextDark)

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it; saved = false; testResult = null },
                label = { Text("Server IP Address") },
                placeholder = { Text("e.g. 192.168.1.5") },
                leadingIcon = { Icon(Icons.Default.Computer, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(10.dp)
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it; saved = false; testResult = null },
                label = { Text("Port") },
                placeholder = { Text("8080") },
                leadingIcon = { Icon(Icons.Default.Settings, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(10.dp)
            )

            // Test result
            testResult?.let { result ->
                val isOk = result.startsWith("Connected")
                Surface(
                    color = if (isOk) SuccessGreen.copy(alpha = 0.1f)
                            else DangerRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if (isOk) SuccessGreen else DangerRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(result, fontSize = 13.sp,
                            color = if (isOk) SuccessGreen else DangerRed)
                    }
                }
            }

            if (saved) {
                Surface(
                    color = SuccessGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Settings saved.", fontSize = 13.sp, color = SuccessGreen)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Test connection
            OutlinedButton(
                onClick = {
                    scope.launch {
                        testing    = true
                        testResult = null
                        try {
                            prefs.saveServerAddress(ip, port)
                            val ok = com.afmvfcc.app.data.api.ApiClient(
                                "http://${ip.trim()}:${port.trim().ifEmpty { "8080" }}"
                            ).ping()
                            testResult = if (ok) "Connected successfully to the CMS!"
                                         else "Server responded but returned an error."
                        } catch (e: Exception) {
                            testResult = "Could not connect. Check the IP and make sure you are on the same WiFi."
                        } finally { testing = false }
                    }
                },
                enabled = ip.isNotBlank() && !testing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp, color = Navy)
                } else {
                    Icon(Icons.Default.NetworkCheck, null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (testing) "Testing..." else "Test Connection")
            }

            // Save button
            Button(
                onClick = {
                    scope.launch {
                        prefs.saveServerAddress(ip, port)
                        saved = true
                    }
                },
                enabled = ip.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Save Settings", fontWeight = FontWeight.Bold)
            }
        }
    }
}
