package com.deepanjanxyz.notepad.domain.model

data class Note(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val date: String = "",
    val isPinned: Boolean = false,
    val colorIndex: Int = 0,
    val tags: List<String> = emptyList(),
    val inTrash: Boolean = false,
    val inArchive: Boolean = false,
    val reminderTime: Long? = null,
    // Epoch millis. createdAt is fixed when the note is first stored and is kept
    // across edits and restores; updatedAt moves on every save. They back the
    // "Date created" and "Last modified" sort orders - a row id cannot, because
    // editing an old note never changes its id.
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    // Epoch millis of when the note was moved to the trash; 0 when it has never
    // been trashed. It backs the trash retention window, and is kept separate
    // from updatedAt so a note's edit history never decides its fate in the
    // trash.
    val trashedAt: Long = 0L
)
