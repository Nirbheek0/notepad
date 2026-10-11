package com.deepanjanxyz.notepad

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.deepanjanxyz.notepad.data.NoteRepositoryProvider
import com.deepanjanxyz.notepad.data.SettingsRepositoryProvider
import com.deepanjanxyz.notepad.domain.repository.NoteRepository
import com.deepanjanxyz.notepad.domain.repository.SettingsRepository
import com.deepanjanxyz.notepad.domain.usecase.label.AddLabelUseCase
import com.deepanjanxyz.notepad.domain.usecase.label.DeleteLabelUseCase
import com.deepanjanxyz.notepad.domain.usecase.label.GetLabelsUseCase
import com.deepanjanxyz.notepad.domain.usecase.label.LabelUseCases
import com.deepanjanxyz.notepad.domain.usecase.label.RenameLabelUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.ArchiveNoteUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.EmptyTrashUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.FilterNotesUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.GetArchiveNotesUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.GetNoteByIdUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.GetNotesUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.GetTrashNotesUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.NoteUseCases
import com.deepanjanxyz.notepad.domain.usecase.note.PermanentlyDeleteNoteUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.PurgeExpiredTrashUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.RestoreNoteUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.SaveNoteUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.SetNoteReminderUseCase
import com.deepanjanxyz.notepad.domain.usecase.note.TogglePinUseCase
import com.deepanxyz.notepad.domain.usecase.note.TrashNoteUseCase
import com.deepanjanxyz.notepad.domain.usecase.settings.GetSettingsUseCase
import com.deepanjanxyz.notepad.domain.usecase.settings.SaveSettingsUseCase
import com.deepanjanxyz.notepad.domain.usecase.settings.SettingsUseCases
import com.deepanjanxyz.notepad.widget.TaskWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotepadApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // The home-screen widget mirrors the notes, so it is rebuilt whenever the
        // app leaves the foreground - the moment a pin, unpin or edit has just
        // finished and the user is about to look at the home screen.
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStopped(activity: Activity) {
                TaskWidgetProvider.refresh(activity.applicationContext)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })

        // Deleted notes are kept for a fixed window and then dropped, so the
        // purge runs once per launch rather than on every trash action.
        CoroutineScope(Dispatchers.IO).launch { container.noteUseCases.purgeExpiredTrash() }
    }
}

/**
 * Manual dependency container for the app.
 *
 * The repositories are built once here and the domain use cases are wired on top
 * of them, so the presentation layer depends only on the domain layer and never
 * reaches into the data layer directly.
 */
class AppContainer(application: Application) {
    private val repository: NoteRepository = NoteRepositoryProvider.create(application)
    private val settingsRepository: SettingsRepository = SettingsRepositoryProvider.create(application)

    val noteUseCases: NoteUseCases = NoteUseCases(
        getNotes = GetNotesUseCase(repository),
        getArchiveNotes = GetArchiveNotesUseCase(repository),
        getTrashNotes = GetTrashNotesUseCase(repository),
        getNoteById = GetNoteByIdUseCase(repository),
        saveNote = SaveNoteUseCase(repository),
        trashNote = TrashNoteUseCase(repository),
        restoreNote = RestoreNoteUseCase(repository),
        permanentlyDeleteNote = PermanentlyDeleteNoteUseCase(repository),
        emptyTrash = EmptyTrashUseCase(repository),
        purgeExpiredTrash = PurgeExpiredTrashUseCase(repository),
        togglePin = TogglePinUseCase(repository),
        archiveNote = ArchiveNoteUseCase(repository),
        filterNotes = FilterNotesUseCase(),
        setNoteReminder = SetNoteReminderUseCase(repository)
    )

    val labelUseCases: LabelUseCases = LabelUseCases(
        getLabels = GetLabelsUseCase(repository),
        addLabel = AddLabelUseCase(repository),
        renameLabel = RenameLabelUseCase(repository),
        deleteLabel = DeleteLabelUseCase(repository)
    )

    val settingsUseCases: SettingsUseCases = SettingsUseCases(
        getSettings = GetSettingsUseCase(settingsRepository),
        saveSettings = SaveSettingsUseCase(settingsRepository)
    )
}
