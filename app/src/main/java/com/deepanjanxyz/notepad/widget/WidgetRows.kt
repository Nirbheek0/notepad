package com.deepanjanxyz.notepad.widget

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.deepanjanxyz.notepad.R
import com.deepanjanxyz.notepad.domain.model.Note
import com.deepanjanxyz.notepad.domain.model.checklistLineText
import com.deepanjanxyz.notepad.domain.model.isChecklistLine
import com.deepanjanxyz.notepad.domain.model.isChecklistLineChecked
import com.deepanjanxyz.notepad.domain.repository.NoteRepository
import com.deepanjanxyz.notepad.ui.theme.NoteColorOptions

/** One checklist line of a note, remembering the line it came from. */
internal data class WidgetTask(val lineIndex: Int, val text: String, val isChecked: Boolean)

/** A checklist note together with the lines it contributes to the widget. */
internal data class WidgetTaskGroup(val note: Note, val tasks: List<WidgetTask>)

/** One row of the widget list: either a note header or one of its tasks. */
internal sealed interface WidgetRow {
    data class Group(val note: Note) : WidgetRow
    data class Item(val note: Note, val task: WidgetTask) : WidgetRow
}

private const val UNTITLED_NOTE = "Untitled Note"

/**
 * The checklist lines of a note, each remembering the line it came from.
 *
 * Only lines that already carry a checkbox are listed, and a tap flips just that
 * line's checkbox - so ordinary text, blank lines and the rest of the note are
 * never rewritten by the widget.
 */
private fun checklistTasks(content: String): List<WidgetTask> =
    content.split("\n").mapIndexedNotNull { index, line ->
        if (line.isChecklistLine()) {
            WidgetTask(index, line.checklistLineText(), line.isChecklistLineChecked())
        } else {
            null
        }
    }

/**
 * The pinned checklist notes, newest first, each with the lines it contributes.
 *
 * The repository hands back only the rows that can match, so a widget refresh
 * reads a handful of notes instead of the whole database.
 */
internal suspend fun loadTaskGroups(repository: NoteRepository): List<WidgetTaskGroup> =
    repository.getPinnedChecklistNotes()
        .map { note -> WidgetTaskGroup(note, checklistTasks(note.content)) }
        .filter { it.tasks.isNotEmpty() }

/** Flattens the groups into the flat row list the widget's ListView renders. */
internal fun flattenRows(groups: List<WidgetTaskGroup>): List<WidgetRow> = buildList {
    for (group in groups) {
        add(WidgetRow.Group(group.note))
        group.tasks.forEach { task -> add(WidgetRow.Item(group.note, task)) }
    }
}

/** Header row: a dot in the note's own colour plus its title. */
internal fun buildGroupViews(context: Context, note: Note): RemoteViews {
    val header = RemoteViews(context.packageName, R.layout.widget_task_group)
    header.setTextViewText(R.id.widget_group_title, note.title.ifBlank { UNTITLED_NOTE })
    header.setInt(R.id.widget_group_dot, "setColorFilter", noteAccentColor(context, note))
    return header
}

/** One task row: the check indicator plus the line's text. */
internal fun buildItemViews(context: Context, note: Note, task: WidgetTask): RemoteViews {
    val row = RemoteViews(context.packageName, R.layout.widget_task_item)
    row.setImageViewResource(
        R.id.widget_item_check,
        if (task.isChecked) R.drawable.ic_widget_check_done else R.drawable.ic_widget_check_todo
    )
    row.setTextViewText(R.id.widget_item_text, itemText(task))
    row.setTextColor(
        R.id.widget_item_text,
        context.getColor(
            if (task.isChecked) {
                R.color.widget_on_surface_variant
            } else {
                R.color.widget_on_surface
            }
        )
    )
    row.setOnClickFillInIntent(
        R.id.widget_item_root,
        TaskWidgetProvider.toggleFillInIntent(note.id, task.lineIndex)
    )
    return row
}

/** Completed lines are struck through, the way a checklist reads at a glance. */
private fun itemText(task: WidgetTask): CharSequence =
    if (task.isChecked) {
        SpannableString(task.text).apply {
            setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    } else {
        task.text
    }

/**
 * The dot mirrors the note's own colour so a group is recognisable at a glance,
 * exactly as the note card is in the app. Notes left on the default colour fall
 * back to the widget accent.
 */
private fun noteAccentColor(context: Context, note: Note): Int =
    if (note.colorIndex in 1 until NoteColorOptions.size) {
        NoteColorOptions[note.colorIndex].toArgb()
    } else {
        context.getColor(R.color.widget_accent)
    }
