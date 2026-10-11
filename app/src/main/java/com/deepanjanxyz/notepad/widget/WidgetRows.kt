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
 * The two row kinds get their own layout and their own view type.
 *
 * A widget host recycles views by type, so a header and a task sharing one type
 * would let a header inherit the click behaviour of the task it was recycled
 * from - which is what made tapping a row toggle the wrong item. Two types keep
 * a header a header and a task a task.
 */
internal const val WIDGET_ROW_TYPE_GROUP = 0
internal const val WIDGET_ROW_TYPE_ITEM = 1
internal const val WIDGET_ROW_TYPE_COUNT = 2

/** A stable id per row, so a recycled view is reused only for the same row. */
internal fun rowStableId(row: WidgetRow): Long = when (row) {
    is WidgetRow.Group -> row.note.id * ID_STRIDE
    is WidgetRow.Item -> row.note.id * ID_STRIDE + row.task.lineIndex + 1
}

internal fun rowViewType(row: WidgetRow): Int = when (row) {
    is WidgetRow.Group -> WIDGET_ROW_TYPE_GROUP
    is WidgetRow.Item -> WIDGET_ROW_TYPE_ITEM
}

/** Keeps a note's header id and its item ids in separate ranges. */
private const val ID_STRIDE = 1_000_000L

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

/**
 * Builds one row.
 *
 * Headers and tasks use separate layouts (and separate view types), so a header
 * can never end up with a task's click target and a task always carries exactly
 * one fill-in intent - the tap that flips its own line.
 */
internal fun buildRowViews(context: Context, row: WidgetRow): RemoteViews = when (row) {
    is WidgetRow.Group -> {
        val views = RemoteViews(context.packageName, R.layout.widget_group_header)
        views.setTextViewText(R.id.widget_group_title, row.note.title.ifBlank { UNTITLED_NOTE })
        views.setInt(R.id.widget_group_dot, "setColorFilter", noteAccentColor(context, row.note))
        views
    }

    is WidgetRow.Item -> {
        val views = RemoteViews(context.packageName, R.layout.widget_task_row)
        views.setImageViewResource(
            R.id.widget_item_check,
            if (row.task.isChecked) {
                R.drawable.ic_widget_check_done
            } else {
                R.drawable.ic_widget_check_todo
            }
        )
        views.setTextViewText(R.id.widget_item_text, itemText(row.task))
        views.setTextColor(
            R.id.widget_item_text,
            context.getColor(
                if (row.task.isChecked) {
                    R.color.widget_on_surface_variant
                } else {
                    R.color.widget_on_surface
                }
            )
        )
        views.setOnClickFillInIntent(
            R.id.widget_row_root,
            TaskWidgetProvider.toggleFillInIntent(row.note.id, row.task.lineIndex)
        )
        views
    }
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
