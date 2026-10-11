package com.deepanjanxyz.notepad.domain.model

import java.util.UUID

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isChecked: Boolean = false
)

/** Checklist line prefixes, exactly as the editor writes them. */
const val CHECKLIST_UNCHECKED_PREFIX = "[ ] "
const val CHECKLIST_CHECKED_PREFIX = "[x] "

/**
 * Line-level helpers for callers that must not reformat a note - the widget
 * toggles one line and leaves every other line, including ordinary text and
 * blank lines, byte for byte as it was.
 */
fun String.isChecklistLine(): Boolean =
    startsWith(CHECKLIST_UNCHECKED_PREFIX) ||
        startsWith(CHECKLIST_CHECKED_PREFIX, ignoreCase = true)

fun String.isChecklistLineChecked(): Boolean =
    startsWith(CHECKLIST_CHECKED_PREFIX, ignoreCase = true)

/** The line's text with its checkbox prefix removed. */
fun String.checklistLineText(): String = when {
    startsWith(CHECKLIST_UNCHECKED_PREFIX) -> substring(CHECKLIST_UNCHECKED_PREFIX.length)
    startsWith(CHECKLIST_CHECKED_PREFIX, ignoreCase = true) ->
        substring(CHECKLIST_CHECKED_PREFIX.length)
    else -> this
}

/** The same line with its checkbox flipped, or null when it is not a checklist line. */
fun String.toggleChecklistLine(): String? = when {
    startsWith(CHECKLIST_UNCHECKED_PREFIX) ->
        CHECKLIST_CHECKED_PREFIX + substring(CHECKLIST_UNCHECKED_PREFIX.length)
    startsWith(CHECKLIST_CHECKED_PREFIX, ignoreCase = true) ->
        CHECKLIST_UNCHECKED_PREFIX + substring(CHECKLIST_CHECKED_PREFIX.length)
    else -> null
}

/**
 * One line of a note shown in checklist mode.
 *
 * A [Text] line is kept exactly as it was written - ordinary prose, a heading or
 * a blank line - and is never reformatted. Only [Task] lines carry a checkbox,
 * and only a task's own line is ever rewritten. Modelling the note as a list of
 * segments is what stops the editor from turning ordinary text into checklist
 * items (and dropping blank lines) when a single item is toggled.
 */
sealed interface ChecklistSegment {
    data class Text(val text: String) : ChecklistSegment
    data class Task(val item: ChecklistItem) : ChecklistSegment
}

/** Splits a note into checklist lines and the verbatim lines between them. */
fun parseChecklistSegments(raw: String): List<ChecklistSegment> {
    if (raw.isEmpty()) return emptyList()
    return raw.split("\n").map { line ->
        when {
            line.startsWith(CHECKLIST_CHECKED_PREFIX, ignoreCase = true) ->
                ChecklistSegment.Task(
                    ChecklistItem(
                        text = line.substring(CHECKLIST_CHECKED_PREFIX.length),
                        isChecked = true
                    )
                )
            line.startsWith(CHECKLIST_UNCHECKED_PREFIX) ->
                ChecklistSegment.Task(
                    ChecklistItem(
                        text = line.substring(CHECKLIST_UNCHECKED_PREFIX.length),
                        isChecked = false
                    )
                )
            else -> ChecklistSegment.Text(line)
        }
    }
}

/**
 * Rebuilds the note from its segments.
 *
 * Text segments are written back byte for byte and task segments get their
 * checkbox prefix, so a note round-trips through checklist mode unchanged
 * except for the item the user actually touched.
 */
fun serializeChecklistSegments(segments: List<ChecklistSegment>): String =
    segments.joinToString("\n") { segment ->
        when (segment) {
            is ChecklistSegment.Text -> segment.text
            is ChecklistSegment.Task ->
                (if (segment.item.isChecked) {
                    CHECKLIST_CHECKED_PREFIX
                } else {
                    CHECKLIST_UNCHECKED_PREFIX
                }) + segment.item.text
        }
    }

/** True when the note already contains at least one checklist line. */
fun String.containsChecklistLine(): Boolean = split("\n").any { it.isChecklistLine() }
