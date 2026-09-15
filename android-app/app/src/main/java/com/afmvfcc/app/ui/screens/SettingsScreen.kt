package com.afmvfcc.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afmvfcc.app.ui.AppViewModel
import com.afmvfcc.app.ui.theme.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
    var scanError  by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // Parses the CMS's connect QR payload: afmvfcc://connect?ip=192.168.1.5&port=8080
    fun applyScannedContent(content: String?) {
        if (content.isNullOrBlank()) return
        try {
            val uri = Uri.parse(content)
            val scannedIp   = uri.getQueryParameter("ip")
            val scannedPort = uri.getQueryParameter("port")
            if (uri.scheme == "afmvfcc" && !scannedIp.isNullOrBlank()) {
                ip = scannedIp
                port = scannedPort?.takeIf { it.isNotBlank() } ?: "8080"
                saved = false
                testResult = null
                scanError = null
            } else {
                scanError = "That QR code isn't an AFM VFCC connect code."
            }
        } catch (e: Exception) {
            scanError = "Couldn't read that QR code. Please try again."
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        applyScannedContent(result.contents)
    }

    fun launchScanner() {
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan the QR code from the CMS's Settings or Attendance screen")
                .setBeepEnabled(false)
                .setOrientationLocked(true)
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchScanner()
        else scanError = "Camera permission is needed to scan the QR code."
    }

    fun handleScanClick() {
        scanError = null
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) launchScanner()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

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
                        Text("1. On the CMS computer, open Settings or Attendance - a QR code is shown next to the server address.",
                            fontSize = 12.sp, color = TextGrey)
                        Text("2. Tap \"Scan QR Code\" below and point the camera at it - the address fills in automatically.",
                            fontSize = 12.sp, color = TextGrey)
                        Text("3. No camera handy? Enter the IP address shown on the CMS manually instead, then tap Save Settings.",
                            fontSize = 12.sp, color = TextGrey)
                        Text("4. If Mobile Hotspot is on for the CMS computer, connect your phone's WiFi to that hotspot first.",
                            fontSize = 12.sp, color = TextGrey)
                    }
                }
            }

            // Scan QR button
            Button(
                onClick = { handleScanClick() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold)
            ) {
                Icon(Icons.Default.QrCodeScanner, null, tint = Navy)
                Spacer(Modifier.width(8.dp))
                Text("Scan QR Code", fontWeight = FontWeight.Bold, color = Navy)
            }

            scanError?.let { err ->
                Surface(
                    color = DangerRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(err, fontSize = 13.sp, color = DangerRed)
                    }
                }
            }

            Text("Or enter it manually", fontWeight = FontWeight.Bold,
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
