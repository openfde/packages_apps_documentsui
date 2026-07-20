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

package com.android.documentsui.picker;

import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.State.ACTION_CREATE;
import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static com.android.documentsui.base.State.ACTION_OPEN_TREE;
import static com.android.documentsui.base.State.ACTION_PICK_COPY_DESTINATION;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.content.Context;
import android.database.Cursor;
import android.provider.DocumentsContract.Document;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.documentsui.Injector;
import com.android.documentsui.Model;
import com.android.documentsui.R;
import com.android.documentsui.approveddochandlers.ApprovedDocMenuController;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.Menus;
import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.State;
import com.android.documentsui.queries.SearchViewManager;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public final class MenuManager extends com.android.documentsui.MenuManager {

    private boolean mOnlyDirectory;

    public MenuManager(
            SearchViewManager searchManager,
            State displayState,
            DirectoryDetails dirDetails,
            IntSupplier filesCountSupplier,
            Context context,
            Features features,
            Injector<?> injector,
            @Nullable ApprovedDocMenuController approvedDocMenuController) {
        super(
                searchManager,
                displayState,
                dirDetails,
                filesCountSupplier,
                context,
                features,
                injector,
                approvedDocMenuController);
    }

    @Override
    public void updateKeyboardShortcutsMenu(
            List<KeyboardShortcutGroup> data, IntFunction<String> stringSupplier) {
        // None as of yet.
    }

    private boolean picking() {
        // picking() is used to do special logic for the 3 action modes below, e.g. show some menu
        // items on the app bar instead of under the dropdown, hide grid/list button in Recents.
        // For M3, we don't want these special logic, we treat menus items for all picker/saver
        // modes the same.
        if (isUseMaterial3FlagEnabled()) {
            return false;
        }
        return mState.action == ACTION_CREATE
                || mState.action == ACTION_OPEN_TREE
                || mState.action == ACTION_PICK_COPY_DESTINATION;
    }

    @Override
    public void updateOptionMenu(Menu menu) {
        super.updateOptionMenu(menu);
        if (picking()) {
            // May already be hidden because the root
            // doesn't support search.
            mSearchManager.showMenu(null);

            // Show on toolbar because there are only two menu items while ACTION_OPEN_TREE.
            menu.findItem(getRes(R.id.option_menu_sort))
                    .setShowAsAction(
                            mState.action == ACTION_OPEN_TREE
                                    ? MenuItem.SHOW_AS_ACTION_ALWAYS
                                    : MenuItem.SHOW_AS_ACTION_NEVER);
        }
    }

    @Override
    public void updateModel(Model model) {
        for (String id : model.getModelIds()) {
            Cursor cursor = model.getItem(id);
            String docMimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            if (!MimeTypes.mimeMatches(Document.MIME_TYPE_DIR, docMimeType)) {
                mOnlyDirectory = false;
                return;
            }
        }
        mOnlyDirectory = true;
    }

    @Override
    protected void updateModePicker(MenuItem grid, MenuItem list) {
        // No display options in recent directories
        if (picking() && mDirDetails.isInRecents()) {
            Menus.setEnabledAndVisible(grid, false);
            Menus.setEnabledAndVisible(list, false);
        } else {
            super.updateModePicker(grid, list);
        }
    }

    @Override
    protected void updateSelectAll(MenuItem selectAll) {
        boolean visible =
                mFilesCountSupplier.getAsInt() > 0 && mState.allowMultiple && !mOnlyDirectory;
        Menus.setEnabledAndVisible(selectAll, visible);
    }

    @Override
    protected void updateSelectAll(MenuItem selectAll, SelectionDetails selectionDetails) {
        final boolean visible =
                mFilesCountSupplier.getAsInt() > 0
                        && mState.allowMultiple
                        && selectionDetails.size() < mFilesCountSupplier.getAsInt()
                        && !mOnlyDirectory;
        Menus.setEnabledAndVisible(selectAll, visible);
    }

    @Override
    protected void updateDeselectAll(MenuItem deselectAll, SelectionDetails selectionDetails) {
        final boolean visible =
                mFilesCountSupplier.getAsInt() > 0
                        && mState.allowMultiple
                        && selectionDetails.size() == mFilesCountSupplier.getAsInt()
                        && !mOnlyDirectory;
        Menus.setEnabledAndVisible(deselectAll, visible);
    }

    @Override
    protected void updateCreateDir(MenuItem createDir) {
        if (isUseMaterial3FlagEnabled()) {
            super.updateCreateDir(createDir);
        } else {
            createDir.setShowAsAction(
                    picking() ? MenuItem.SHOW_AS_ACTION_ALWAYS : MenuItem.SHOW_AS_ACTION_NEVER);
            Menus.setEnabledAndVisible(createDir, picking() && mDirDetails.canCreateDirectory());
        }
    }

    @Override
    protected void updateSelect(MenuItem select, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(select,
                !isUseMaterial3FlagEnabled() && (mState.action == ACTION_GET_CONTENT
                || mState.action == ACTION_OPEN)
                && selectionDetails.size() > 0);
        select.setTitle(getRes(R.string.menu_select));
    }

    @Override
    public void showContextMenu(Fragment f, View v, float x, float y) {
        if (isUseMaterial3FlagEnabled()) {
            super.showContextMenu(f, v, x, y);
        }
    }

    @Override
    public void inflateContextMenuForContainer(
            Menu menu, MenuInflater inflater, SelectionDetails selectionDetails) {
        if (isUseMaterial3FlagEnabled()) {
            super.inflateContextMenuForContainer(menu, inflater, selectionDetails);
        } else {
            throw new UnsupportedOperationException("Pickers don't allow context menu.");
        }
    }

    @Override
    public void inflateContextMenuForDocs(
            Menu menu, MenuInflater inflater, SelectionDetails selectionDetails) {
        if (isUseMaterial3FlagEnabled()) {
            super.inflateContextMenuForDocs(menu, inflater, selectionDetails);
        } else {
            throw new UnsupportedOperationException("Pickers don't allow context menu.");
        }
    }

    @Override
    protected void updateDelete(MenuItem delete, SelectionDetails selectionDetails) {
        if (isUseMaterial3FlagEnabled()) {
            super.updateDelete(delete, selectionDetails);
        } else {
            Menus.setEnabledAndVisible(delete, false);
        }
    }

    @Override
    protected void updateCopyAndCut(
            MenuItem copy, MenuItem cut, SelectionDetails selectionDetails) {
        // We don't support copy/cut/paste inside the picker.
        if (isUseMaterial3FlagEnabled()) {
            Menus.disableAndSetVisibility(copy, false);
            Menus.disableAndSetVisibility(cut, false);
        } else {
            super.updateCopyAndCut(copy, cut, selectionDetails);
        }
    }

    @Override
    protected void updatePaste(MenuItem paste) {
        // We don't support copy/cut/paste inside the picker.
        if (isUseMaterial3FlagEnabled()) {
            Menus.setEnabledAndVisible(paste, false);
        } else {
            super.updatePaste(paste);
        }
    }

    @Override
    protected void updateRename(MenuItem rename, SelectionDetails selectionDetails) {
        if (isUseMaterial3FlagEnabled()) {
            super.updateRename(rename, selectionDetails);
        } else {
            Menus.setEnabledAndVisible(rename, false);
        }
    }

    @Override
    protected void updateInspect(MenuItem inspect) {
        if (isUseMaterial3FlagEnabled()) {
            super.updateInspect(inspect);
        } else {
            Menus.setEnabledAndVisible(inspect, false);
        }
    }

    @Override
    protected void updateInspect(MenuItem inspect, SelectionDetails selectionDetails) {
        if (isUseMaterial3FlagEnabled()) {
            super.updateInspect(inspect, selectionDetails);
        } else {
            Menus.setEnabledAndVisible(inspect, false);
        }
    }

    @Override
    protected void updateCompress(@NonNull MenuItem it, @NonNull SelectionDetails selection) {
        if (isUseMaterial3FlagEnabled()) {
            super.updateCompress(it, selection);
        } else {
            Menus.setEnabledAndVisible(it, false);
        }
    }

    @Override
    public void updateApprovedDocHandlers(Menu menu, SelectionDetails selection) {
        // We don't support approved doc handlers inside the picker.
    }
}
