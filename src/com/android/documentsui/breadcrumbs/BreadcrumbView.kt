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
package com.android.documentsui.breadcrumbs

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isEmpty
import com.android.documentsui.R
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.util.Material3Config.Companion.getRes
import java.util.function.IntConsumer
import kotlin.math.max

/**
 * A programmatic wrapper around the horizontal scroll view. The view hides the details of updating
 * the visual representation of the path set on this view.
 */
class BreadcrumbView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    FrameLayout(context, attrs, defStyleAttr) {

    constructor(context: Context) : this(context, null, 0)

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    // The horizontal scroll that is used to scroll long paths.
    private val horizontalScrollView: HorizontalScrollView

    // The inner linear layout that actually holds views and separators representing a path.
    private val pathContainer: LinearLayout

    // The consumer of path clicks. Gets notified that the ith element of the path was clicked.
    private var clickConsumer: IntConsumer? = null

    // The views representing each path part.
    private val pathItems = mutableListOf<TextView>()

    init {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.breadcrumb_view_v2, this, true)
        horizontalScrollView = findViewById(getRes(R.id.breadcrumb_horizontal_scroll_view))
        pathContainer = findViewById(getRes(R.id.breadcrumb_path_holder))
        // The view starts as hidden, and only takes over when search is active.
        visibility = GONE
    }

    /**
     * Registers a click consumer with this view. The clicks on the path items are forwarded to the
     * consumer, passing the index of the path as the sole argument.
     */
    fun setClickConsumer(consumer: IntConsumer?) {
        clickConsumer = consumer
    }

    /** Updates the view with the new path. */
    fun setPath(path: Array<String>) {
        if (DEBUG) {
            Log.d(TAG, "Updating path to ${path.contentToString()}")
        }

        if (path.isEmpty()) {
            clear()
            return
        }

        // Remove excess elements if any. We have to remove 2 elements: a text view and an image.
        while (path.size < pathItems.size) {
            val viewIndex = 2 * (pathItems.size - 1)
            pathItems.removeLast().setOnClickListener(null)
            pathContainer.removeViewAt(viewIndex)
            pathContainer.removeViewAt(viewIndex - 1)
        }

        // Add any elements missing.
        if (path.size > pathItems.size) {
            val inflater = LayoutInflater.from(context)
            for (index in pathItems.size until path.size) {
                if (index > 0) {
                    inflater.inflate(getRes(R.layout.breadcrumb_separator), pathContainer)
                }
                val pathItemView =
                    inflater.inflate(getRes(R.layout.breadcrumb_path_item), pathContainer, false)
                        as TextView
                pathItemView.setOnClickListener { clickConsumer?.accept(index) }
                pathItems.add(pathItemView)
                pathContainer.addView(pathItemView)
            }
        }
        // Update the text and enabled state.
        for ((text, widget) in path.zip(pathItems)) {
            widget.text = text
            widget.setEnabled(true)
        }

        // The last path item is not clickable; disable it.
        pathItems.last().setEnabled(false)

        // Scroll to the end where the new elements should be.
        scrollToEnd()
    }

    /**
     * Scrolls to the end of horizontal scroll view without using fullScroll. The reason for having
     * this method is that fullScroll can scroll and grab focus. We cannot have breadcrumb view take
     * focus away from other elements of the DocumentsUI.
     */
    private fun scrollToEnd() {
        if (horizontalScrollView.isEmpty()) {
            return
        }
        horizontalScrollView.post {
            val horizontalPadding =
                horizontalScrollView.paddingLeft + horizontalScrollView.paddingRight
            val visibleWidth = horizontalScrollView.width - horizontalPadding
            val child = horizontalScrollView.getChildAt(horizontalScrollView.childCount - 1)
            val maxScrollX = max(0, child.width - visibleWidth)
            horizontalScrollView.smoothScrollTo(maxScrollX, 0)
        }
    }

    /** Returns the current length of the path shown by this view. */
    fun getPathLength() = pathItems.size

    /** Clears the view. */
    fun clear() {
        // Clean up and discard old path.
        for (widget in pathItems) {
            widget.setOnClickListener(null)
        }
        pathContainer.removeAllViews()
        pathItems.clear()
        if (DEBUG) {
            Log.d(TAG, "BreadcrumbView cleared")
        }
    }

    /** Performs a programmatic click on the path item with the given `index`. */
    fun performPathItemClick(index: Int): Boolean {
        // The path is (0) textview (1) imageview (2) textview (3) imageview ... the index of each
        // text view is always 0, 2, 4, etc., hence the following computation.
        val itemIndex = index * 2
        if (itemIndex >= pathContainer.childCount) {
            return false
        }
        val child = pathContainer.getChildAt(itemIndex)
        return child?.performClick() ?: false
    }
}
