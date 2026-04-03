package com.autobook.ui.chapters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.data.db.ChapterEntity
import com.autobook.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChaptersViewModel(
    private val repository: BookRepository
) : ViewModel() {

    private val _chapters = MutableStateFlow<List<ChapterEntity>>(emptyList())
    val chapters: StateFlow<List<ChapterEntity>> = _chapters

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex

    fun loadChapters(bookId: String) {
        viewModelScope.launch {
            repository.getChaptersForBook(bookId).collect { chapterList ->
                _chapters.value = chapterList
            }

            val book = repository.getBook(bookId)
            _currentChapterIndex.value = book?.currentChapterIndex ?: 0
        }
    }

    fun selectChapter(bookId: String, chapterIndex: Int) {
        viewModelScope.launch {
            repository.updateReadPosition(bookId, chapterIndex, 0)
        }
    }
}
