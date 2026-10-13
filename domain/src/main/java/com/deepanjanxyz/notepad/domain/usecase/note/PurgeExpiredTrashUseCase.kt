package com.deepanjanxyz.notepad.domain.usecase.note

import com.deepanjanxyz.notepad.domain.repository.NoteRepository

/** Drops trashed notes once the retention window has passed. */
class PurgeExpiredTrashUseCase(private val repository: NoteRepository) {
    suspend operator fun invoke() {
        repository.purgeExpiredTrash()
    }
}
