package com.bitchat.android.nyaya.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitchat.android.nyaya.ai.LegalLibrary
import com.bitchat.android.nyaya.ui.theme.NyayaPill
import com.bitchat.android.nyaya.ui.theme.NyayaTheme

/**
 * The offline legal library.
 *
 * The complete text of 25 Indian Acts already ships inside the app for the AI to
 * retrieve from; this lets the user read it directly. For a legal-help app that
 * matters more than it sounds: an answer the user can check against the statute
 * is worth far more than one they have to take on trust, and it keeps working when
 * no model is downloaded at all.
 *
 * Plain-language guides come first, then the full Acts.
 */
@Composable
fun LegalLibraryScreen(
    state: NyayaViewModel.UiState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onOpenDocument: (LegalLibrary.Document) -> Unit,
    onCloseDocument: () -> Unit,
    onAsk: (String) -> Unit
) {
    LaunchedEffect(Unit) { onLoad() }

    val document = state.openDocument
    if (document != null) {
        DocumentReader(
            document = document,
            sections = state.openDocumentSections,
            loading = state.libraryLoading,
            onBack = onCloseDocument,
            onAsk = onAsk
        )
        return
    }

    var query by remember { mutableStateOf("") }
    val visible = remember(state.libraryDocuments, query) {
        if (query.isBlank()) state.libraryDocuments
        else state.libraryDocuments.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    val guides = visible.filter { it.kind == LegalLibrary.Kind.GUIDE }
    val acts = visible.filter { it.kind == LegalLibrary.Kind.FULL_ACT }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NyayaTheme.gradients.canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            LibraryTopBar(title = "Legal library", onBack = onBack)

            Text(
                text = "The full text of ${state.libraryDocuments.size} documents, bundled " +
                    "inside the app. Works with no internet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            SearchRow(query = query, onQueryChange = { query = it })

            if (state.libraryLoading && state.libraryDocuments.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (guides.isNotEmpty()) {
                    item { SectionHeading("Start here \u00B7 plain language") }
                    items(guides, key = { it.assetName }) { doc ->
                        DocumentRow(doc, onClick = { onOpenDocument(doc) })
                    }
                }
                if (acts.isNotEmpty()) {
                    item { SectionHeading("Complete Acts") }
                    items(acts, key = { it.assetName }) { doc ->
                        DocumentRow(doc, onClick = { onOpenDocument(doc) })
                    }
                }
                if (visible.isEmpty()) {
                    item {
                        Text(
                            text = "Nothing matches \u201C$query\u201D.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

/**
 * Reads one document, one section at a time.
 *
 * The Income-tax Acts are over 4 MB each, so the sections are laid out lazily and
 * the body of a section is only measured when it scrolls into view. Rendering a
 * whole Act as a single Text would freeze the UI on any phone.
 */
@Composable
private fun DocumentReader(
    document: LegalLibrary.Document,
    sections: List<LegalLibrary.Section>,
    loading: Boolean,
    onBack: () -> Unit,
    onAsk: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NyayaTheme.gradients.canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            LibraryTopBar(title = document.shortTitle, onBack = onBack)

            if (loading && sections.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(
                            text = document.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (document.statusNote != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            StatusBadge(document.statusNote)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        AskAboutThis(
                            title = document.shortTitle,
                            onAsk = onAsk
                        )
                    }
                }

                items(sections.size) { index ->
                    val section = sections[index]
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        if (section.heading.isNotEmpty()) {
                            Text(
                                text = section.heading,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (section.body.isNotEmpty()) {
                            Text(
                                text = section.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun LibraryTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchRow(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), NyayaPill)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search the law",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun DocumentRow(document: LegalLibrary.Document, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = if (document.kind == LegalLibrary.Kind.GUIDE) {
                Icons.AutoMirrored.Outlined.Article
            } else {
                Icons.Outlined.Gavel
            },
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.shortTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (document.statusNote != null) {
                Spacer(modifier = Modifier.height(4.dp))
                StatusBadge(document.statusNote)
            }
        }
    }
}

/**
 * Flags an Act that is not current law. The repealed Income-tax Act, 1961 is
 * bundled on purpose — it still governs earlier assessment years — so the user
 * has to be able to see at a glance which one they are reading.
 */
@Composable
private fun StatusBadge(status: String) {
    val repealed = status.contains("REPEAL", ignoreCase = true) ||
        status.contains("OLD", ignoreCase = true)
    Text(
        text = status,
        style = MaterialTheme.typography.labelSmall,
        color = if (repealed) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(
                if (repealed) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer,
                NyayaPill
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/**
 * Bridges reading to asking, which is the point of having both in one app: a user
 * who opens an Act and finds it impenetrable — which most people do, because bare
 * acts are not written for them — can hand it straight to the AI.
 */
@Composable
private fun AskAboutThis(title: String, onAsk: (String) -> Unit) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, NyayaPill)
            .clickable { onAsk("Explain $title in simple words, and tell me when it applies to me.") }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.QuestionAnswer,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = "Ask Nyaya to explain this",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
