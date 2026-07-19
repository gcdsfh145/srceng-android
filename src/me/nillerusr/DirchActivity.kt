package me.nillerusr

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.IOException
import java.util.Locale

class DirchActivity : ComponentActivity() {
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences("mod", MODE_PRIVATE)
        enableEdgeToEdge()
        setContent {
            SrcEngTheme {
                DirectoryScreen(
                    roots = storageRoots(),
                    onSelect = { path ->
                        preferences.edit().putString("gamepath", "$path/").apply()
                        setResult(RESULT_OK)
                        finish()
                    },
                    onCancel = ::finish
                )
            }
        }
    }

    private fun storageRoots(): List<File> {
        val roots = linkedMapOf<String, File>()
        fun add(file: File?) {
            if (file != null && file.isDirectory && file.canRead()) {
                roots[file.absolutePath.lowercase(Locale.ROOT)] = file
            }
        }
        add(Environment.getExternalStorageDirectory())
        File("/storage").listFiles()?.forEach(::add)
        return roots.values.sortedBy { it.absolutePath.lowercase(Locale.ROOT) }
    }
}

private data class DirectoryEntry(val file: File, val isParent: Boolean = false)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DirectoryScreen(
    roots: List<File>,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var currentPath by rememberSaveable { mutableStateOf<String?>(null) }
    val entries = remember(currentPath, roots) {
        if (currentPath == null) {
            roots.map { DirectoryEntry(it) }
        } else {
            val current = File(currentPath!!)
            buildList {
                current.parentFile?.let { add(DirectoryEntry(it, isParent = true)) }
                current.listFiles()
                    ?.filter { it.isDirectory && it.canRead() }
                    ?.sortedBy { it.name.lowercase(Locale.ROOT) }
                    ?.forEach { add(DirectoryEntry(it)) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Game directory", fontWeight = FontWeight.Bold)
                        Text(
                            currentPath ?: "Choose a storage location",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            Button(
                onClick = { currentPath?.let(onSelect) },
                enabled = currentPath != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Use current directory")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        "No readable directories found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            items(entries, key = { it.file.absolutePath }) { entry ->
                ListItem(
                    headlineContent = {
                        Text(
                            if (entry.isParent) ".." else entry.file.name.ifEmpty { entry.file.path },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(entry.file.absolutePath, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = { Text("📁", style = MaterialTheme.typography.titleLarge) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            currentPath = try {
                                entry.file.canonicalPath
                            } catch (_: IOException) {
                                entry.file.absolutePath
                            }
                        }
                )
                HorizontalDivider()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DirectoryPreview() {
    SrcEngTheme {
        DirectoryScreen(listOf(File("/sdcard")), {}, {})
    }
}
