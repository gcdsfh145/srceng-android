package me.nillerusr

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
                    onCommandLineChange = { commandLine = it },
                    onEnvironmentChange = { environment = it },
                    onGamePathChange = { gamePath = it },
                    onChooseDirectory = {
                        directoryPicker.launch(Intent(this, DirchActivity::class.java))
                    },
                    onAbout = { showAbout = true },
                    onDismissAbout = { showAbout = false },
                    onLaunch = ::startSource
                )
            }
        }

        ensureStorageAccess()
    }

    override fun onResume() {
        super.onResume()
        // Request notification access after the legacy storage permission
        // dialog has completed, so two permission dialogs never overlap.
        if (hasStorageAccess()) requestNotificationPermission()
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

    private fun startSource() {
        if (!hasStorageAccess() || !hasRuntimePermissions()) {
            ensureStorageAccess()
            Toast.makeText(this, "Storage and microphone permissions are required before launching the engine", Toast.LENGTH_LONG).show()
            return
        }
        saveSettings()
        startActivity(Intent(this, SDLActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun hasStorageAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
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
        requestRuntimePermissions()
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val permissions = buildList {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
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
        if (requestCode == REQUEST_PERMISSIONS && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, com.valvesoftware.source.R.string.srceng_launcher_error_no_permission, Toast.LENGTH_LONG).show()
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
    onCommandLineChange: (String) -> Unit,
    onEnvironmentChange: (String) -> Unit,
    onGamePathChange: (String) -> Unit,
    onChooseDirectory: () -> Unit,
    onAbout: () -> Unit,
    onDismissAbout: () -> Unit,
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
        LauncherScreen("-console", "LIBGL_USEVBO=0", "/sdcard/srceng", false, {}, {}, {}, {}, {}, {}, {})
    }
}
