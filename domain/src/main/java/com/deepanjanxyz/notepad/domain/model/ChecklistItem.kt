package com.deepanjanxyz.notepad.domain.model

import java.util.UUID

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isChecked: Boolean = false
) {
    companion object {
        fun parseList(raw: String): List<ChecklistItem> {
            if (raw.isBlank()) return emptyList()
            return raw.lines().filter { it.isNotBlank() }.map { line ->
                when {
                    line.startsWith("[x] ", ignoreCase = true) ->
                        ChecklistItem(text = line.substring(4), isChecked = true)
                    line.startsWith("[ ] ") ->
                        ChecklistItem(text = line.substring(4), isChecked = false)
                    else ->
                        ChecklistItem(text = line, isChecked = false)
                }
            }
        }

        fun serializeList(items: List<ChecklistItem>): String {
            return items.joinToString("\n") { item ->
                val prefix = if (item.isChecked) "[x] " else "[ ] "
                prefix + item.text
            }
        }
    }
}

fun parseChecklist(raw: String): List<ChecklistItem> = ChecklistItem.parseList(raw)
fun serializeChecklist(items: List<ChecklistItem>): String = ChecklistItem.serializeList(items)

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
