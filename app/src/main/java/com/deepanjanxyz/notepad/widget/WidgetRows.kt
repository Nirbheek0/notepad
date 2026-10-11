package com.deepanjanxyz.notepad.widget

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.deepanjanxyz.notepad.R
import com.deepanjanxyz.notepad.domain.model.ChecklistItem
import com.deepanjanxyz.notepad.domain.model.Note
import com.deepanjanxyz.notepad.domain.repository.NoteRepository
import com.deepanjanxyz.notepad.ui.theme.NoteColorOptions

/** A checklist note together with the items parsed out of it. */
internal data class WidgetTaskGroup(val note: Note, val items: List<ChecklistItem>)

/** One row of the widget list: either a note header or one of its items. */
internal sealed interface WidgetRow {
    data class Group(val note: Note) : WidgetRow
    data class Item(val note: Note, val index: Int, val item: ChecklistItem) : WidgetRow
}

/** Checklist line prefixes, matching the format the editor writes and reads. */
private const val CHECKLIST_UNCHECKED_PREFIX = "[ ] "
private const val CHECKLIST_CHECKED_PREFIX = "[x] "
private const val UNTITLED_NOTE = "Untitled Note"

/**
 * True when the note actually holds checklist lines - the same test the editor
 * uses before it treats a note as a checklist. Without it a plain text note
 * would be listed as a task list, and ticking an item would rewrite its lines.
 */
internal fun Note.hasChecklist(): Boolean = content.lines().any { line ->
    line.startsWith(CHECKLIST_UNCHECKED_PREFIX) ||
        line.startsWith(CHECKLIST_CHECKED_PREFIX, ignoreCase = true)
}

/**
 * The pinned checklist notes, newest first, each with its parsed items.
 *
 * The repository hands back only the rows that can match, so a widget refresh
 * reads a handful of notes instead of the whole database.
 */
internal suspend fun loadTaskGroups(repository: NoteRepository): List<WidgetTaskGroup> =
    repository.getPinnedChecklistNotes()
        .filter { it.hasChecklist() }
        .map { note -> WidgetTaskGroup(note, ChecklistItem.parseList(note.content)) }

/** Flattens the groups into the flat row list the widget's ListView renders. */
internal fun flattenRows(groups: List<WidgetTaskGroup>): List<WidgetRow> = buildList {
    for (group in groups) {
        add(WidgetRow.Group(group.note))
        group.items.forEachIndexed { index, item ->
            add(WidgetRow.Item(group.note, index, item))
        }
    }
}

/** Header row: a dot in the note's own colour plus its title. */
internal fun buildGroupViews(context: Context, note: Note): RemoteViews {
    val header = RemoteViews(context.packageName, R.layout.widget_task_group)
    header.setTextViewText(R.id.widget_group_title, note.title.ifBlank { UNTITLED_NOTE })
    header.setInt(R.id.widget_group_dot, "setColorFilter", noteAccentColor(context, note))
    return header
}

/** One task row: the check indicator plus the item text. */
internal fun buildItemViews(
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
    row.setTextViewText(R.id.widget_item_text, itemText(item))
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
    row.setOnClickFillInIntent(
        R.id.widget_item_root,
        TaskWidgetProvider.toggleFillInIntent(note.id, itemIndex)
    )
    return row
}

/** Completed items are struck through, the way a checklist reads at a glance. */
private fun itemText(item: ChecklistItem): CharSequence =
    if (item.isChecked) {
        SpannableString(item.text).apply {
            setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    } else {
        item.text
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
