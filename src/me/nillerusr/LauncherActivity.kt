package me.nillerusr

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.libsdl.app.SDLActivity

class LauncherActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_PERMISSIONS = 42
        private const val REQUEST_NOTIFICATION_PERMISSION = 43

        @JvmField
        var PKG_NAME: String = "com.valvesoftware.source"

        @JvmStatic
        fun getDefaultDir(): String {
            val dir = Environment.getExternalStorageDirectory()
            return if (dir == null || !dir.exists()) "/sdcard/" else dir.path
        }

        @JvmStatic
        fun getAndroidDataDir(): String {
            val path = "${getDefaultDir()}/Android/data/$PKG_NAME/files"
            FileCompat.mkdirs(path)
            return path
        }
    }

    private lateinit var preferences: SharedPreferences
    private var commandLine by mutableStateOf("")
    private var environment by mutableStateOf("")
    private var gamePath by mutableStateOf("")
    private var showAbout by mutableStateOf(false)
    private var showOnline by mutableStateOf(false)
    private var onlineAddress by mutableStateOf("127.0.0.1")
    private var onlinePort by mutableStateOf("27015")
    private var onlineMap by mutableStateOf("d1_trainstation_01")
    private var storageSettingsRequested = false

    private val directoryPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        gamePath = preferences.getString("gamepath", gamePath) ?: gamePath
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PKG_NAME = packageName
        preferences = getSharedPreferences("mod", MODE_PRIVATE)
        commandLine = preferences.getString("argv", "-console") ?: "-console"
        environment = preferences.getString("env", "LIBGL_USEVBO=0") ?: "LIBGL_USEVBO=0"
        gamePath = preferences.getString("gamepath", "${getDefaultDir()}/srceng")
            ?: "${getDefaultDir()}/srceng"

        enableEdgeToEdge()
        setContent {
            SrcEngTheme {
                LauncherScreen(
                    commandLine = commandLine,
                    environment = environment,
                    gamePath = gamePath,
                    showAbout = showAbout,
                    showOnline = showOnline,
                    onlineAddress = onlineAddress,
                    onlinePort = onlinePort,
                    onlineMap = onlineMap,
                    onCommandLineChange = { commandLine = it },
                    onEnvironmentChange = { environment = it },
                    onGamePathChange = { gamePath = it },
                    onOnlineAddressChange = { onlineAddress = it },
                    onOnlinePortChange = { onlinePort = it },
                    onOnlineMapChange = { onlineMap = it },
                    onChooseDirectory = {
                        directoryPicker.launch(Intent(this, DirchActivity::class.java))
                    },
                    onAbout = { showAbout = true },
                    onDismissAbout = { showAbout = false },
                    onOnline = { showOnline = true },
                    onDismissOnline = { showOnline = false },
                    onOnlineJoin = ::joinOnline,
                    onOnlineHost = ::hostOnline,
                    onLaunch = { startSource() }
                )
            }
        }

        ensureStorageAccess()
    }

    override fun onResume() {
        super.onResume()
        // Android 11+ ignores WRITE_EXTERNAL_STORAGE for apps targeting modern
        // SDKs. All-files access is the storage gate on those versions; the
        // legacy runtime permission remains active on Android 10 and below.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasModernStorageAccess()) {
            if (storageSettingsRequested) {
                storageSettingsRequested = false
            } else {
                requestModernStorageAccess()
            }
            return
        }
        if (!hasLegacyStorageAccess() || !hasRuntimePermissions()) {
            requestRuntimePermissions()
            return
        }
        requestNotificationPermission()
    }

    override fun onPause() {
        saveSettings()
        super.onPause()
    }

    private fun saveSettings() {
        if (!::preferences.isInitialized) return
        preferences.edit()
            .putString("argv", commandLine)
            .putString("gamepath", gamePath)
            .putString("env", environment)
            .putBoolean("immersive_mode", true)
            .apply()
    }

    private fun startSource(extraArgs: String = "") {
        if (!hasStorageAccess() || !hasRuntimePermissions()) {
            ensureStorageAccess()
            Toast.makeText(this, "Storage and microphone permissions are required before launching the engine", Toast.LENGTH_LONG).show()
            return
        }
        saveSettings()
        startActivity(Intent(this, SDLActivity::class.java).apply {
            if (extraArgs.isNotBlank()) putExtra("argv", "$commandLine $extraArgs")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun joinOnline(address: String, port: String) {
        val host = address.trim()
        val portNumber = port.trim().toIntOrNull()
        if (host.isEmpty() || host.any { it.isWhitespace() || it == ';' || it == '"' } ||
            portNumber == null || portNumber !in 1..65535
        ) {
            Toast.makeText(this, "Invalid server address or port", Toast.LENGTH_LONG).show()
            return
        }
        showOnline = false
        startSource("+connect $host:$portNumber")
    }

    private fun hostOnline(map: String, port: String) {
        val mapName = map.trim()
        val portNumber = port.trim().toIntOrNull()
        if (mapName.isEmpty() || mapName.any { it.isWhitespace() || it == ';' || it == '"' } ||
            portNumber == null || portNumber !in 1..65535
        ) {
            Toast.makeText(this, "Invalid map or port", Toast.LENGTH_LONG).show()
            return
        }
        showOnline = false
        startSource("-maxplayers 8 +hostport $portNumber +map $mapName")
    }

    private fun hasLegacyStorageAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasModernStorageAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    private fun hasStorageAccess(): Boolean {
        return hasLegacyStorageAccess() && hasModernStorageAccess()
    }

    private fun hasRuntimePermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasModernStorageAccess()) {
            requestModernStorageAccess()
        } else if (!hasLegacyStorageAccess() || !hasRuntimePermissions()) {
            requestRuntimePermissions()
        }
    }

    private fun requestModernStorageAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || storageSettingsRequested) return
        storageSettingsRequested = true
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val permissions = buildList {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, com.valvesoftware.source.R.string.srceng_launcher_error_no_permission, Toast.LENGTH_LONG).show()
            } else {
                ensureStorageAccess()
            }
        }
    }

    private object FileCompat {
        fun mkdirs(path: String) {
            try {
                java.io.File(path).mkdirs()
            } catch (_: SecurityException) {
                // Native code will report a more useful error if this path is unavailable.
            }
        }
    }

}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LauncherScreen(
    commandLine: String,
    environment: String,
    gamePath: String,
    showAbout: Boolean,
    showOnline: Boolean,
    onlineAddress: String,
    onlinePort: String,
    onlineMap: String,
    onCommandLineChange: (String) -> Unit,
    onEnvironmentChange: (String) -> Unit,
    onGamePathChange: (String) -> Unit,
    onOnlineAddressChange: (String) -> Unit,
    onOnlinePortChange: (String) -> Unit,
    onOnlineMapChange: (String) -> Unit,
    onChooseDirectory: () -> Unit,
    onAbout: () -> Unit,
    onDismissAbout: () -> Unit,
    onOnline: () -> Unit,
    onDismissOnline: () -> Unit,
    onOnlineJoin: (String, String) -> Unit,
    onOnlineHost: (String, String) -> Unit,
    onLaunch: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Source Engine", fontWeight = FontWeight.Bold)
                        Text("Android launcher", style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedActionButton("About", onAbout, Modifier.weight(1f))
                Button(onClick = onLaunch, modifier = Modifier.weight(1f)) {
                    Text("Launch")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Runtime configuration",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Configure the native engine before starting the game.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Online", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Generic Source connection for HL2. This is not HL2MP; the selected game DLL must provide multiplayer rules.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = onOnline, modifier = Modifier.fillMaxWidth()) {
                        Text("Open online")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ConfigField("Command-line arguments", commandLine, onCommandLineChange)
                    ConfigField("Environment", environment, onEnvironmentChange)
                    ConfigField("Game resources", gamePath, onGamePathChange)
                    OutlinedActionButton(
                        "Choose game directory",
                        onChooseDirectory,
                        Modifier.fillMaxWidth()
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tip: use -console for a visible engine console. Your settings are saved automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = onDismissAbout,
            title = { Text("About Source Engine") },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(context.getString(com.valvesoftware.source.R.string.srceng_launcher_about_text))
                }
            },
            confirmButton = { TextButton(onClick = onDismissAbout) { Text("OK") } }
        )
    }

    if (showOnline) {
        AlertDialog(
            onDismissRequest = onDismissOnline,
            title = { Text("Online") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = onlineAddress,
                        onValueChange = onOnlineAddressChange,
                        label = { Text("Server address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = onlinePort,
                        onValueChange = onOnlinePortChange,
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = onlineMap,
                        onValueChange = onOnlineMapChange,
                        label = { Text("Host map") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = { TextButton(onClick = onDismissOnline) { Text("Cancel") } },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onOnlineHost(onlineMap, onlinePort) }) { Text("Host") }
                    Button(onClick = { onOnlineJoin(onlineAddress, onlinePort) }) { Text("Join") }
                }
            }
        )
    }
}

@Composable
private fun ConfigField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
}

@Composable
private fun OutlinedActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(label)
    }
}

@Composable
fun SrcEngTheme(content: @Composable () -> Unit) {
    val colors: ColorScheme = darkColorScheme(
        primary = Color(0xFFFFA726),
        onPrimary = Color(0xFF271900),
        primaryContainer = Color(0xFF5D3A00),
        onPrimaryContainer = Color(0xFFFFDDB5),
        background = Color(0xFF101014),
        surface = Color(0xFF101014),
        surfaceContainer = Color(0xFF1D1D24),
        surfaceContainerLow = Color(0xFF18181E)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Preview(showBackground = true, backgroundColor = 0xFF101014)
@Composable
private fun LauncherPreview() {
    SrcEngTheme {
        LauncherScreen(
            "-console", "LIBGL_USEVBO=0", "/sdcard/srceng", false, false,
            "127.0.0.1", "27015", "d1_trainstation_01",
            onCommandLineChange = {},
            onEnvironmentChange = {},
            onGamePathChange = {},
            onOnlineAddressChange = {},
            onOnlinePortChange = {},
            onOnlineMapChange = {},
            onChooseDirectory = {},
            onAbout = {},
            onDismissAbout = {},
            onOnline = {},
            onDismissOnline = {},
            onOnlineJoin = { _, _ -> },
            onOnlineHost = { _, _ -> },
            onLaunch = {}
        )
    }
}
