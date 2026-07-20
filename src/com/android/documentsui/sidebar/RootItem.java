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

package com.android.documentsui.sidebar;

import static com.android.documentsui.util.Material3Config.getRes;

import android.content.Context;
import android.provider.DocumentsProvider;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.DragEvent;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.R;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.SidebarEntryItemInfo;
import com.android.documentsui.base.UserId;

import java.util.Objects;

/** An {@link Item} for each root provided by {@link DocumentsProvider}s. */
public class RootItem extends BaseSidebarEntryItem {
    private static final String TAG = "RootItem";
    private static final String STRING_ID_FORMAT = "RootItem{%s/%s}";

    public final RootInfo root;

    public RootItem(RootInfo root, ActionHandler actionHandler, boolean maybeShowBadge) {
        this(root, actionHandler, "" /* packageName */, maybeShowBadge);
    }

    public RootItem(RootInfo root, ActionHandler actionHandler, String packageName,
            boolean maybeShowBadge) {
        this(getRes(R.layout.item_root), root, actionHandler, packageName, maybeShowBadge);
    }

    public RootItem(
            @LayoutRes int layoutId,
            RootInfo root,
            ActionHandler actionHandler,
            String packageName,
            boolean maybeShowBadge) {
        super(
                layoutId,
                root.title,
                getStringId(root),
                root.userId,
                actionHandler,
                packageName,
                maybeShowBadge);
        this.root = root;
    }

    private static String getStringId(RootInfo root) {
        // Empty URI authority is invalid, so we can use empty string if root.authority is null.
        // Directly passing null to String.format() will write "null" which can be a valid URI
        // authority.
        String authority = (root.authority == null ? "" : root.authority);
        return String.format(STRING_ID_FORMAT, authority, root.rootId);
    }

    @Override
    public void bindView(View convertView) {
        final Context context = convertView.getContext();
        if (root.supportsEject()) {
            bindAction(
                    convertView,
                    View.VISIBLE,
                    getRes(R.drawable.ic_eject),
                    context.getResources().getString(R.string.menu_eject_root));
        } else {
            bindAction(convertView, View.GONE, -1 /* iconResource */, null /* description */);
        }
        // Show available space if no summary
        String summaryText = root.summary;
        if (TextUtils.isEmpty(summaryText) && root.availableBytes >= 0) {
            summaryText =
                    context.getString(
                            getRes(R.string.root_available_bytes),
                            Formatter.formatFileSize(context, root.availableBytes));
        }

        bindIconAndTitle(convertView);
        bindSummary(convertView, summaryText);
    }

    protected void onActionClick(View view) {
        RootsFragment.ejectClicked(view, root, getActionHandler());
    }

    @Override
    boolean isRoot() {
        return true;
    }

    @Override
    void open() {
        getActionHandler().openRoot(root);
    }

    @Override
    public String getSummary() {
        return root.summary;
    }

    @Override
    public boolean isDropTarget() {
        return root.isValidDropTarget();
    }

    @Override
    boolean dropOn(DragEvent event) {
        return getActionHandler().dropOn(event, root);
    }

    @Override
    public String toString() {
        return "RootItem{"
                + "id="
                + stringId
                + ", userId="
                + userId
                + ", root="
                + root
                + ", docInfo="
                + getDocInfo()
                + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (this == o) {
            return true;
        }

        if (o instanceof RootItem) {
            RootItem other = (RootItem) o;
            return Objects.equals(root, other.root)
                    && Objects.equals(getDocInfo(), other.getDocInfo())
                    && Objects.equals(getActionHandler(), other.getActionHandler())
                    && Objects.equals(getPackageName(), other.getPackageName());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(root, getDocInfo(), getActionHandler(), getPackageName());
    }

    /**
     * Creates a stub root item for a user. A stub root item is used as a place holder when
     * there is no such root available. We can therefore show the item on the UI.
     */
    public static RootItem createStubItem(RootItem item, UserId targetUser) {
        RootInfo stubRootInfo = RootInfo.copyRootInfo(item.root);
        stubRootInfo.userId = targetUser;
        RootItem stub =
                new RootItem(stubRootInfo, item.getActionHandler(), item.getMaybeShowBadge());
        return stub;
    }

    @Override
    public @NonNull SidebarEntryItemInfo getItemInfo() {
        return root;
    }
}
