/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.documentsui.testing;

import android.content.ClipData;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.DragAndDropManager;
import com.android.documentsui.DragAndDropManager.Permissions;
import com.android.documentsui.MenuManager.SelectionDetails;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.SidebarEntryItemInfo;
import com.android.documentsui.dirlist.IconHelper;
import com.android.documentsui.services.FileOperations;

import kotlin.Triple;

import java.util.List;

public class TestDragAndDropManager implements DragAndDropManager {

    public boolean mLastCanDragAndDrop = true;

    public final TestEventListener<List<DocumentInfo>> startDragHandler = new TestEventListener<>();
    public final TestEventHandler<Triple<Permissions, ClipData, SidebarEntryItemInfo>>
            dropOnRootHandler = new TestEventHandler<>();
    public final TestEventHandler<Triple<Permissions, ClipData, DocumentStack>>
            dropOnDocumentHandler = new TestEventHandler<>();
    public final TestEventHandler<Void> isDragFromSameAppHandler = new TestEventHandler<>();

    public TestDragAndDropManager() {
        isDragFromSameAppHandler.nextReturn(true);
    }

    @Override
    public void onKeyEvent(KeyEvent event) {}

    @Override
    public void startDrag(
            View v,
            List<DocumentInfo> srcs,
            SidebarEntryItemInfo itemInfo,
            List<Uri> invalidDest,
            SelectionDetails details,
            IconHelper iconHelper,
            @Nullable DocumentInfo parent,
            boolean canDragAndDrop,
            DocumentsAccess docsAccess) {
        startDragHandler.accept(srcs);
        mLastCanDragAndDrop = canDragAndDrop;
    }

    @Override
    public boolean canSpringOpen(RootInfo root, DocumentInfo doc) {
        return false;
    }

    @Override
    public void updateStateToNotAllowed(View v) {}

    @Override
    public int updateState(
            View v, @Nullable SidebarEntryItemInfo destItemInfo, @Nullable DocumentInfo destDoc) {
        return 0;
    }

    @Override
    public void resetState(View v) {}

    @Override
    public boolean isDragFromSameApp() {
        return isDragFromSameAppHandler.accept(null);
    }

    @Override
    public boolean drop(
            @Nullable Permissions permissions,
            ClipData clipData,
            Object localState,
            SidebarEntryItemInfo root,
            ActionHandler actions,
            DocumentsAccess docs,
            FileOperations.Callback callback,
            List<Uri> invalidDest) {
        return dropOnRootHandler.accept(new Triple(permissions, clipData, root));
    }

    @Override
    public boolean drop(
            @Nullable Permissions permissions,
            ClipData clipData,
            Object localState,
            DocumentStack dstStack,
            ActionHandler actions,
            DocumentsAccess docs,
            FileOperations.Callback callback) {
        return dropOnDocumentHandler.accept(new Triple(permissions, clipData, dstStack));
    }

    @Override
    public void dragEnded() {}

    @Override
    public List<Uri> getInvalidDestinations() {
        return List.of();
    }
}
