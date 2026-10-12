package com.deepanjanxyz.notepad.ui.feature_editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepanjanxyz.notepad.domain.model.ChecklistSegment
import com.deepanjanxyz.notepad.ui.components.ActionTooltip

/**
 * Checklist mode for the note editor.
 *
 * Every line is rendered where it was written: an ordinary text line stays a
 * plain line and a checklist line gets a checkbox. Because the underlying model
 * keeps text and tasks as separate segments, editing one task never reformats -
 * or converts - the rest of the note.
 */
@Composable
fun EditorChecklistContent(
    segments: List<ChecklistSegment>,
    newItemText: String,
    onNewItemTextChange: (String) -> Unit,
    onAddNewItem: () -> Unit,
    onItemCheckChange: (itemId: String, isChecked: Boolean) -> Unit,
    onItemTextChange: (itemId: String, newText: String) -> Unit,
    onItemDelete: (itemId: String) -> Unit,
    textPrimary: Color = Color(0xFFE2E2E6),
    textPlaceholder: Color = Color(0xFF6E7179),
    amberAccent: Color = Color(0xFFFFB300),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        segments.forEach { segment ->
            when (segment) {
                is ChecklistSegment.Text -> {
                    if (segment.text.isBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        Text(
                            text = segment.text,
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal,
                                color = textPrimary,
                                lineHeight = 24.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }

                is ChecklistSegment.Task -> ChecklistTaskRow(
                    itemId = segment.item.id,
                    text = segment.item.text,
                    isChecked = segment.item.isChecked,
                    textPrimary = textPrimary,
                    amberAccent = amberAccent,
                    onCheckChange = onItemCheckChange,
                    onTextChange = onItemTextChange,
                    onDelete = onItemDelete
                )
            }
        }

        // New Item input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color(0xFF8E9099),
                modifier = Modifier.size(22.dp)
            )
            BasicTextField(
                value = newItemText,
                onValueChange = onNewItemTextChange,
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    color = textPrimary,
                    lineHeight = 24.sp
                ),
                cursorBrush = SolidColor(amberAccent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAddNewItem() }),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (newItemText.isEmpty()) {
                            Text(
                                text = "List item",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = textPlaceholder
                                )
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
                    .testTag("new_checklist_item_input")
            )
            ActionTooltip("Add list item") {
                IconButton(
                    onClick = onAddNewItem,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("add_checklist_item_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add list item",
                        tint = amberAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/** One checklist line: its checkbox, its editable text and its remove button. */
@Composable
private fun ChecklistTaskRow(
    itemId: String,
    text: String,
    isChecked: Boolean,
    textPrimary: Color,
    amberAccent: Color,
    onCheckChange: (String, Boolean) -> Unit,
    onTextChange: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChecklistCheckbox(
            itemId = itemId,
            isChecked = isChecked,
            amberAccent = amberAccent,
            onCheckChange = onCheckChange
        )
        ChecklistItemText(
            itemId = itemId,
            text = text,
            isChecked = isChecked,
            textPrimary = textPrimary,
            amberAccent = amberAccent,
            onTextChange = onTextChange
        )
        ChecklistRemoveButton(
            itemId = itemId,
            isChecked = isChecked,
            onDelete = onDelete
        )
    }
}

/**
 * A checklist item is a single line: a newline typed or pasted into the field
 * would split the note into an extra item on the next save, so it becomes a
 * space instead.
 */
@Composable
private fun RowScope.ChecklistItemText(
    itemId: String,
    text: String,
    isChecked: Boolean,
    textPrimary: Color,
    amberAccent: Color,
    onTextChange: (String, String) -> Unit
) {
    BasicTextField(
        value = text,
        onValueChange = { onTextChange(itemId, it.replace('\n', ' ')) },
        textStyle = TextStyle(
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            color = if (isChecked) Color(0xFF6E7179) else textPrimary,
            textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
            lineHeight = 24.sp
        ),
        cursorBrush = SolidColor(amberAccent),
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 14.dp)
            .testTag(
                if (isChecked) {
                    "completed_checklist_text_$itemId"
                } else {
                    "checklist_text_$itemId"
                }
            )
    )
}

/**
 * The tap target is larger than the box it draws: a 20dp hit area is easy to
 * miss, which made ticking an item feel unreliable.
 */
@Composable
private fun ChecklistCheckbox(
    itemId: String,
    isChecked: Boolean,
    amberAccent: Color,
    onCheckChange: (String, Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable { onCheckChange(itemId, !isChecked) }
            .testTag(if (isChecked) "checkbox_completed_$itemId" else "checkbox_$itemId"),
        contentAlignment = Alignment.Center
    ) {
        ChecklistCheckboxMark(isChecked = isChecked, amberAccent = amberAccent)
    }
}

@Composable
private fun ChecklistCheckboxMark(isChecked: Boolean, amberAccent: Color) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(3.dp))
            .then(
                if (isChecked) {
                    Modifier.background(amberAccent)
                } else {
                    Modifier.border(1.8.dp, Color(0xFF8E9099), RoundedCornerShape(3.dp))
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isChecked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Completed",
                tint = Color(0xFF1B1B1F),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun ChecklistRemoveButton(
    itemId: String,
    isChecked: Boolean,
    onDelete: (String) -> Unit
) {
    ActionTooltip("Remove item") {
        IconButton(
            onClick = { onDelete(itemId) },
            modifier = Modifier
                .size(36.dp)
                .testTag(
                    if (isChecked) {
                        "remove_completed_item_$itemId"
                    } else {
                        "remove_item_$itemId"
                    }
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove item",
                tint = Color(0xFF6E7179),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
