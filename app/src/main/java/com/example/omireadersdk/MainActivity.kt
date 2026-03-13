package com.example.omireadersdk

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.omireadersdk.ui.theme.OmiReaderSDKTheme
import com.example.omireadersdk.viewmodel.MainUiState
import com.example.omireadersdk.viewmodel.MainViewModel
import com.example.omireadersdk.sdk.model.EpubBook
import com.example.omireadersdk.sdk.model.EpubVersion
import com.example.omireadersdk.sdk.model.TocEntry
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmiReaderSDKTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadEpub(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OmiReader") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Open EPUB") },
                icon = { Icon(Icons.Outlined.MenuBook, contentDescription = null) },
                onClick = { launcher.launch(arrayOf("application/epub+zip")) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is MainUiState.Idle    -> EmptyState()
                is MainUiState.Loading -> LoadingState()
                is MainUiState.Loaded  -> BookInfoScreen(book = state.book)
                is MainUiState.Error   -> ErrorState(message = state.message)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                "Tap 'Open EPUB' to get started",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun BookInfoScreen(book: EpubBook) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        book.metadata.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (book.metadata.authors.isNotEmpty()) {
                        Text(
                            book.metadata.authors.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = { Text("EPUB ${if (book.version == EpubVersion.EPUB2) "2" else "3"}") }
                        )
                        AssistChip(onClick = {}, label = { Text("${book.spine.size} chapters") })
                        AssistChip(onClick = {}, label = { Text(book.metadata.language.uppercase()) })
                    }
                    val overlayCount = book.manifest.values.count { it.isSmil }
                    if (overlayCount > 0) {
                        AssistChip(onClick = {}, label = { Text("$overlayCount media overlays") })
                    }
                }
            }
        }

        item {
            Text(
                "Table of Contents",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (book.toc.isEmpty()) {
            item {
                Text(
                    "No table of contents found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(book.toc) { entry -> TocRow(entry = entry) }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun TocRow(entry: TocEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (entry.depth * 16).dp, top = 2.dp, end = 0.dp, bottom = 2.dp),
    ) {
        if (entry.depth > 0) {
            Text("·  ", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)
        }
        Text(
            text = entry.title,
            style = if (entry.depth == 0) MaterialTheme.typography.bodyMedium
            else MaterialTheme.typography.bodySmall,
            color = if (entry.depth == 0) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}