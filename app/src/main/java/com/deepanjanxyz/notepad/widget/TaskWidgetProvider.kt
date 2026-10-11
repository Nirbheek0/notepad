package com.deepanjanxyz.notepad.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.deepanjanxyz.notepad.MainActivity
import com.deepanjanxyz.notepad.R
import com.deepanjanxyz.notepad.data.NoteRepositoryProvider
import com.deepanjanxyz.notepad.domain.model.ChecklistItem
import com.deepanjanxyz.notepad.domain.model.Note
import com.deepanjanxyz.notepad.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A checklist note together with the items parsed out of it. */
private data class WidgetTaskGroup(val note: Note, val items: List<ChecklistItem>)

/**
 * Home-screen widget that surfaces the checklist items of every pinned note, so
 * pending tasks can be seen and ticked without opening the app.
 *
 * The pin is the control: pinning a checklist note puts it on the widget and
 * unpinning removes it, which reuses the gesture users already know instead of
 * adding a second place to manage the same thing.
 */
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_ITEM -> {
                val noteId = intent.getLongExtra(EXTRA_NOTE_ID, INVALID_ID)
                val itemIndex = intent.getIntExtra(EXTRA_ITEM_INDEX, INVALID_INDEX)
                if (noteId != INVALID_ID && itemIndex >= 0) {
                    toggleItem(context, noteId, itemIndex)
                }
            }
            ACTION_REFRESH -> refresh(context)
            else -> super.onReceive(context, intent)
        }
    }

    private fun toggleItem(context: Context, noteId: Long, itemIndex: Int) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val repository = NoteRepositoryProvider.create(appContext)
            val note = repository.getNoteById(noteId)
            if (note != null) {
                val items = ChecklistItem.parseList(note.content).toMutableList()
                if (itemIndex in items.indices) {
                    val item = items[itemIndex]
                    items[itemIndex] = item.copy(isChecked = !item.isChecked)
                    val content = ChecklistItem.serializeList(items)
                    repository.insertOrUpdate(note.copy(content = content))
                }
            }
            renderAll(appContext)
        }
    }

    companion object {
        private const val ACTION_TOGGLE_ITEM = "com.deepanjanxyz.notepad.widget.ACTION_TOGGLE_ITEM"
        private const val ACTION_REFRESH = "com.deepanjanxyz.notepad.widget.ACTION_REFRESH"
        private const val EXTRA_NOTE_ID = "extra_note_id"
        private const val EXTRA_ITEM_INDEX = "extra_item_index"
        private const val INVALID_ID = -1L
        private const val INVALID_INDEX = -1
        private const val REQUEST_CODE_NOTE_STRIDE = 1000
        private const val REQUEST_CODE_REFRESH = 1
        private const val REQUEST_CODE_OPEN_APP = 0

        private val pendingIntentFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        /** Rebuilds every placed widget from the current notes. */
        fun refresh(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch { renderAll(appContext) }
        }

        private suspend fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TaskWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            val groups = taskGroups(NoteRepositoryProvider.create(context))
            for (id in ids) {
                manager.updateAppWidget(id, buildViews(context, groups))
            }
        }

        private suspend fun taskGroups(repository: NoteRepository): List<WidgetTaskGroup> =
            repository.getAllNotes().first()
                .filter { it.isPinned }
                .map { note -> WidgetTaskGroup(note, ChecklistItem.parseList(note.content)) }
                .filter { it.items.isNotEmpty() }

        private fun buildViews(context: Context, groups: List<WidgetTaskGroup>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tasks)
            views.removeAllViews(R.id.widget_items)

            if (groups.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                for (group in groups) {
                    group.items.forEachIndexed { index, item ->
                        views.addView(
                            R.id.widget_items,
                            buildItemViews(context, group.note, index, item)
                        )
                    }
                }
            }

            views.setOnClickPendingIntent(R.id.widget_title, openAppIntent(context))
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))
            return views
        }

        private fun buildItemViews(
            context: Context,
            note: Note,
            itemIndex: Int,
            item: ChecklistItem
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_task_item)
            row.setImageViewResource(
                R.id.widget_item_check,
                if (item.isChecked) R.drawable.ic_widget_check_done else R.drawable.ic_widget_check_todo
            )
            row.setTextViewText(R.id.widget_item_text, item.text)
            row.setTextColor(
                R.id.widget_item_text,
                context.getColor(
                    if (item.isChecked) {
                        R.color.widget_on_surface_variant
                    } else {
                        R.color.widget_on_surface
                    }
                )
            )
            row.setOnClickPendingIntent(
                R.id.widget_item_root,
                toggleIntent(context, note.id, itemIndex)
            )
            return row
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(context, REQUEST_CODE_OPEN_APP, intent, pendingIntentFlags)
        }

        private fun toggleIntent(context: Context, noteId: Long, itemIndex: Int): PendingIntent {
            val intent = Intent(context, TaskWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_ITEM
                putExtra(EXTRA_NOTE_ID, noteId)
                putExtra(EXTRA_ITEM_INDEX, itemIndex)
            }
            val requestCode = noteId.toInt() * REQUEST_CODE_NOTE_STRIDE + itemIndex
            return PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags)
        }

        private fun refreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, TaskWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            return PendingIntent.getBroadcast(context, REQUEST_CODE_REFRESH, intent, pendingIntentFlags)
        }
    }
}
