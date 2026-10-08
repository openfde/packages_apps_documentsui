/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.sorting;

import static com.android.documentsui.ui.Views.setWeight;
import static com.android.documentsui.util.FlagUtils.isUseFileSummaryEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.view.MotionEvent;
import android.view.View;
import android.view.PointerIcon;

import com.android.documentsui.R;

import javax.annotation.Nullable;

/** View controller for table header that associates header cells in table header and columns. */
public final class TableHeaderController implements SortController.WidgetController {
    // Width (in dp) of the hit area on the right edge of a header cell used to resize the column.
    private static final float RESIZE_EDGE_DP = 12f;
    private static final float MIN_COLUMN_WEIGHT = 0.02f;
    private static final float MAX_COLUMN_WEIGHT = 0.75f;

    private final HeaderCell mTitleCell;
    // The 4 cells below will be null in compact/medium screen sizes when use_material3 flag is ON.
    private final @Nullable HeaderCell mSummaryCell;
    private final @Nullable HeaderCell mSizeCell;
    private final @Nullable HeaderCell mFileTypeCell;
    private final @Nullable HeaderCell mDateCell;
    private final SortModel mModel;
    // We assign this here porque each method reference creates a new object
    // instance (which is wasteful).
    private final View.OnClickListener mOnCellClickListener = this::onCellClicked;
    private final SortModel.UpdateListener mModelListener = this::onModelUpdate;
    private final View mTableHeader;
    private final float mResizeEdgePx;

    // The cells that can be resized by dragging, in the order of the columns they represent.
    private final @Nullable HeaderCell[] mResizableCells;
    private final int[] mResizableColumns;
    // The weighted cells whose widths are re-distributed when resizing.
    private final @Nullable HeaderCell[] mWeightedCells;
    private final View.OnTouchListener mResizeTouchListener = this::onResizeTouch;
    private final View.OnHoverListener mResizeHoverListener = this::onResizeHover;

    private HeaderCell mResizingCell;
    private float mDownRawX;
    private float mAccumulatedDx;
    private boolean mResizing;

    private TableHeaderController(SortModel sortModel, View tableHeader) {
        assert (sortModel != null);
        assert (tableHeader != null);

        mModel = sortModel;
        mTableHeader = tableHeader;

        mResizeEdgePx = dpToPx(tableHeader.getContext(), RESIZE_EDGE_DP);

        mTitleCell = tableHeader.findViewById(android.R.id.title);
        mSummaryCell = tableHeader.findViewById(android.R.id.summary);
        mSizeCell = tableHeader.findViewById(getRes(R.id.size));
        mFileTypeCell = tableHeader.findViewById(getRes(R.id.file_type));
        mDateCell = tableHeader.findViewById(getRes(R.id.date));
        mWeightedCells =
                new HeaderCell[] {mTitleCell, mSummaryCell, mFileTypeCell, mSizeCell, mDateCell};
        mResizableCells = new HeaderCell[] {mTitleCell, mFileTypeCell, mSizeCell, mDateCell};
        mResizableColumns =
                new int[] {
                    ColumnWidths.COLUMN_TITLE,
                    ColumnWidths.COLUMN_FILE_TYPE,
                    ColumnWidths.COLUMN_SIZE,
                    ColumnWidths.COLUMN_DATE
                };
        ColumnWidths.ensureDefaults();
        setupResizeHandlers();
        adjustColumnWidthForSummary();
        onModelUpdate(mModel, SortModel.UPDATE_TYPE_UNSPECIFIED);

        mModel.addListener(mModelListener);
    }

    /**
     * If summary column needs to be displayed or hidden, adjust the width of all columns.
     *
     * <p>NOTE: These values are matched in {@link
     * com.android.documentsui.dirlist.ListDocumentHolder#adjustColumnWidthForSummary()}
     */
    private void adjustColumnWidthForSummary() {
        ColumnWidths.ensureDefaults();
        setWeight(mTitleCell, ColumnWidths.getWeight(ColumnWidths.COLUMN_TITLE));
        setWeight(mSummaryCell, getSummaryWeight());
        setWeight(mDateCell, ColumnWidths.getWeight(ColumnWidths.COLUMN_DATE));
        setWeight(mSizeCell, ColumnWidths.getWeight(ColumnWidths.COLUMN_SIZE));
        setWeight(mFileTypeCell, ColumnWidths.getWeight(ColumnWidths.COLUMN_FILE_TYPE));
    }

    private void setupResizeHandlers() {
        for (int i = 0; i < mResizableCells.length; i++) {
            HeaderCell cell = mResizableCells[i];
            if (cell == null) {
                continue;
            }
            cell.setOnTouchListener(mResizeTouchListener);
            cell.setOnHoverListener(mResizeHoverListener);
        }
    }

    private boolean isAtResizeEdge(HeaderCell cell, float x) {
        return cell != null && cell.getWidth() - x <= mResizeEdgePx;
    }

    private boolean onResizeTouch(View v, MotionEvent event) {
        // Only a touch started inside the right-edge resize zone starts a resize. Touches anywhere
        // else are left to the default click/sort handling.
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isAtResizeEdge((HeaderCell) v, event.getX())) {
                    return false;
                }
                mResizingCell = (HeaderCell) v;
                mDownRawX = event.getRawX();
                mAccumulatedDx = 0f;
                mResizing = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mResizing) {
                    return false;
                }
                float rawX = event.getRawX();
                // Apply only the incremental movement so the resize feels direct under the cursor.
                resizeColumn(mResizingCell, rawX - mDownRawX - mAccumulatedDx);
                mAccumulatedDx = rawX - mDownRawX;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mResizing = false;
                mResizingCell = null;
                return true;
            default:
                return mResizing;
        }
    }

    private boolean onResizeHover(View v, MotionEvent event) {
        HeaderCell cell = (HeaderCell) v;
        PointerIcon icon;
        if (isAtResizeEdge(cell, event.getX())) {
            icon =
                    PointerIcon.getSystemIcon(
                            cell.getContext(), PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW);
        } else {
            icon = null;
        }
        v.setPointerIcon(icon);
        return false;
    }

    /**
     * Adjusts the weight of the dragged column by the given horizontal delta (in pixels) and
     * shrinks the other resizable columns proportionally so the columns keep summing to the same
     * weight. The new weights are written to {@link ColumnWidths}, which keeps the table header and
     * the file rows aligned.
     */
    private void resizeColumn(@Nullable HeaderCell cell, float dxPx) {
        if (cell == null) {
            return;
        }
        int column = columnOf(cell);
        if (column < 0) {
            return;
        }

        float totalWidth = 0f;
        for (HeaderCell weightedCell : mWeightedCells) {
            if (weightedCell != null) {
                totalWidth += weightedCell.getMeasuredWidth();
            }
        }
        if (totalWidth <= 0f) {
            return;
        }

        float[] weights = new float[4];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = ColumnWidths.getWeight(mResizableColumns[i]);
        }

        float totalWeight = 0f;
        for (HeaderCell weightedCell : mWeightedCells) {
            if (weightedCell != null) {
                totalWeight += getColumnWeight(weightedCell, weights);
            }
        }
        if (totalWeight <= 0f) {
            return;
        }

        // A change of weight dw translates to exactly dxPx pixels of width, since
        // width = availableWidth * weight / totalWeight for every weighted child.
        float dw = dxPx * totalWeight / totalWidth;

        float oldTotal = 0f;
        for (float w : weights) {
            oldTotal += w;
        }
        float oldColumnWeight = weights[column];
        float newColumnWeight = clampWeight(oldColumnWeight + dw);
        float othersOld = oldTotal - oldColumnWeight;
        float othersNew = oldTotal - newColumnWeight;
        if (othersOld <= 0f || othersNew <= 0f) {
            return;
        }

        float scale = othersNew / othersOld;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = clampWeight(i == column ? newColumnWeight : weights[i] * scale);
        }

        ColumnWidths.updateWeights(weights);
        adjustColumnWidthForSummary();
    }

    private int columnOf(HeaderCell cell) {
        for (int i = 0; i < mResizableCells.length; i++) {
            if (mResizableCells[i] == cell) {
                return i;
            }
        }
        return -1;
    }

    private float getColumnWeight(HeaderCell cell, float[] weights) {
        int i = columnOf(cell);
        if (i >= 0) {
            return weights[i];
        }
        // The summary cell isn't user-resizable; it keeps its fixed weight.
        return getSummaryWeight();
    }

    private float getSummaryWeight() {
        return isUseFileSummaryEnabled() ? 0.25f : 0f;
    }

    private float clampWeight(float value) {
        return Math.max(MIN_COLUMN_WEIGHT, Math.min(MAX_COLUMN_WEIGHT, value));
    }

    private static float dpToPx(android.content.Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    /** Creates a TableHeaderController. */
    public static @Nullable TableHeaderController create(
            SortModel sortModel, @Nullable View tableHeader) {
        return (tableHeader == null) ? null : new TableHeaderController(sortModel, tableHeader);
    }

    private void onModelUpdate(SortModel model, int updateTypeUnspecified) {
        bindCell(mTitleCell, SortModel.SORT_DIMENSION_ID_TITLE);
        if (mSummaryCell != null) {
            bindCell(mSummaryCell, SortModel.SORT_DIMENSION_ID_SUMMARY);
        }
        if (mSizeCell != null) {
            bindCell(mSizeCell, SortModel.SORT_DIMENSION_ID_SIZE);
        }
        if (mFileTypeCell != null) {
            bindCell(mFileTypeCell, SortModel.SORT_DIMENSION_ID_FILE_TYPE);
        }
        if (mDateCell != null) {
            bindCell(mDateCell, SortModel.SORT_DIMENSION_ID_DATE);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        mTableHeader.setVisibility(visibility);
    }

    @Override
    public void destroy() {
        mModel.removeListener(mModelListener);
    }

    private void bindCell(HeaderCell cell, int id) {
        assert (cell != null);
        SortDimension dimension = mModel.getDimensionById(id);

        cell.setTag(dimension);

        cell.onBind(dimension);
        if (dimension.getVisibility() == View.VISIBLE
                && dimension.getSortCapability() != SortDimension.SORT_CAPABILITY_NONE) {
            cell.setOnClickListener(mOnCellClickListener);
            if (isUseMaterial3FlagEnabled()) {
                cell.setSortArrowTag(dimension);
            }
        } else {
            cell.setOnClickListener(null);
            if (isUseMaterial3FlagEnabled()) {
                cell.setSortArrowTag(null);
            }
        }
    }

    private void onCellClicked(View v) {
        SortDimension dimension = (SortDimension) v.getTag();

        mModel.sortByUser(dimension.getId(), dimension.getNextDirection());
    }
}
