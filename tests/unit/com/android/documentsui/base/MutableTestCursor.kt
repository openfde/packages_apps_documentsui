/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.documentsui.base

import android.database.AbstractCursor

/** A callback that is invoked before the cursor moves from the old position to the new position. */
fun interface OnMoveCallback {
    fun onMove(from: Int, to: Int)
}

/**
 * A trivial implementation of a Cursor that allows us to add and remove rows. This class is similar
 * to the MatrixCursor, minus all checks, plus ability to remove rows.
 */
class MutableTestCursor(private val columnNames: Array<String>) : AbstractCursor() {

    private val rows = mutableListOf<Array<Object>>()
    private var moveCallback: OnMoveCallback? = null

    /** Adds a new row to the end of the cursor. */
    fun addRow(columnValues: Array<Object>) {
        rows.add(columnValues)
    }

    /** Removes the row at the specified zero-based position. */
    fun removeRow(rowIndex: Int) {
        rows.removeAt(rowIndex)
        if (position == rowIndex) {
            moveToPosition(rowIndex - 1)
        } else if (position > rowIndex) {
            moveToPosition(position - 1)
        }
    }

    override fun getColumnNames() = columnNames

    override fun getCount(): Int = rows.size

    override fun getLong(column: Int): Long {
        checkPosition()
        return rows[position][column] as Long
    }

    override fun getDouble(column: Int): Double {
        checkPosition()
        return rows[position][column] as Double
    }

    override fun getString(column: Int): String {
        checkPosition()
        return rows[position][column].toString()
    }

    override fun getFloat(column: Int) = getDouble(column).toFloat()

    override fun getShort(column: Int): Short = getLong(column).toShort()

    override fun getInt(column: Int): Int = getLong(column).toInt()

    override fun isNull(column: Int): Boolean {
        checkPosition()
        return false
    }

    fun setMoveCallback(callback: OnMoveCallback) {
        moveCallback = callback
    }

    override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
        moveCallback?.onMove(oldPosition, newPosition)
        return super.onMove(oldPosition, newPosition)
    }
}
