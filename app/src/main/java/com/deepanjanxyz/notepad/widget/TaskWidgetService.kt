package com.deepanjanxyz.notepad.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.deepanjanxyz.notepad.data.NoteRepositoryProvider
import kotlinx.coroutines.runBlocking

/**
 * Supplies the rows for the widget's scrollable list.
 *
 * [onDataSetChanged] is called on a background thread, so the notes are read
 * there once and the rows are then built from memory - the widget never touches
 * the database from the main thread.
 */
class TaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TaskWidgetFactory(applicationContext)
}

private class TaskWidgetFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    private var rows: List<WidgetRow> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        rows = flattenRows(runBlocking { loadTaskGroups(NoteRepositoryProvider.create(context)) })
    }

    override fun onDestroy() = Unit

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews = when (val row = rows[position]) {
        is WidgetRow.Group -> buildGroupViews(context, row.note)
        is WidgetRow.Item -> buildItemViews(context, row.note, row.index, row.item)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = VIEW_TYPE_COUNT

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is WidgetRow.Group) VIEW_TYPE_GROUP else VIEW_TYPE_ITEM

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    private companion object {
        const val VIEW_TYPE_GROUP = 0
        const val VIEW_TYPE_ITEM = 1
        const val VIEW_TYPE_COUNT = 2
    }
}
