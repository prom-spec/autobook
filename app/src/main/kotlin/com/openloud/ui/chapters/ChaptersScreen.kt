package com.openloud.ui.chapters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openloud.data.db.ChapterEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersScreen(
    viewModel: ChaptersViewModel,
    bookId: String,
    onBackClick: () -> Unit,
    onChapterClick: (Int) -> Unit
) {
    val chapters by viewModel.chapters.collectAsState()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chapters") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(chapters) { index, chapter ->
                ChapterItem(
                    chapter = chapter,
                    isCurrentChapter = index == currentChapterIndex,
                    onClick = {
                        viewModel.selectChapter(bookId, index)
                        onChapterClick(index)
                    }
                )
                if (index < chapters.size - 1) {
                    Divider()
                }
            }
        }
    }
}

@Composable
fun ChapterItem(
    chapter: ChapterEntity,
    isCurrentChapter: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isCurrentChapter) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val durationMinutes = chapter.estimatedDuration / 60000
            Text(
                text = "${durationMinutes} min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isCurrentChapter) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Current",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
