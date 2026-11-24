/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.SOLID_COLORED;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_ICON;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.DocumentsContract;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;

import com.android.documentsui.ConfigStore;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.IconUtils;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.modules.utils.build.SdkLevel;

import java.util.Map;

// TODO(b/379776735): remove this file after use_material3 flag is launched.
final class GridDirectoryHolder extends DocumentHolder {
    final TextView mTitle;

    private final ImageView mIconMime;
    private final ImageView mIconBadge;

    private final IconHelper mIconHelper;

    GridDirectoryHolder(
            Context context, ViewGroup parent, IconHelper iconHelper, ConfigStore configStore) {
        super(context, parent, getRes(R.layout.item_dir_grid), configStore);

        mTitle = (TextView) itemView.findViewById(android.R.id.title);
        mIconMime = (ImageView) itemView.findViewById(getRes(R.id.icon_mime_sm));
        mIconBadge = (ImageView) itemView.findViewById(getRes(R.id.icon_profile_badge));
        mIconMime.setImageDrawable(
                IconUtils.loadMimeIcon(context, DocumentsContract.Document.MIME_TYPE_DIR));
        mIconHelper = iconHelper;

        if (SdkLevel.isAtLeastT() && !mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
            setUpdatableWorkProfileIcon(context);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void setUpdatableWorkProfileIcon(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        Drawable drawable =
                dpm.getResources()
                        .getDrawable(
                                WORK_PROFILE_ICON,
                                SOLID_COLORED,
                                () -> context.getDrawable(getRes(R.drawable.ic_briefcase)));
        mIconBadge.setImageDrawable(drawable);
    }

    @Override
    public void setSelected(boolean selected, boolean animate) {
        // There is no checkbox in grid mode; selection is shown by the whole item
        // being boxed. The folder icon is always visible.
        super.setSelected(selected, animate);
    }

    @Override
    public void bindBriefcaseIcon(boolean show) {
        mIconBadge.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.S)
    public void bindProfileIcon(boolean show, int userIdIdentifier) {
        Map<UserId, Drawable> userIdToBadgeMap = DocumentsApplication.getUserManagerState(
                mContext).getUserIdToBadgeMap();
        Drawable drawable = userIdToBadgeMap.get(UserId.of(userIdIdentifier));
        mIconBadge.setImageDrawable(drawable);
        mIconBadge.setVisibility(show ? View.VISIBLE : View.GONE);
        mIconBadge.setContentDescription(mIconHelper.getProfileLabel(userIdIdentifier));
    }

    @Override
    public boolean inDragRegion(MotionEvent event) {
        // Entire grid box should be draggable
        return true;
    }

    @Override
    public int classifySelectionHotspot(MotionEvent event) {
        if (mAction != State.ACTION_BROWSE) {
            return ItemDetails.SELECTION_HOTSPOT_OUTSIDE;
        }

        // Whole item is the selectable box: clicking anywhere on the grid item toggles
        // its selection. Touch stays as-is to preserve the open/picker behavior.
        return Events.isMousyEvent(event)
                ? ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI
                : ItemDetails.SELECTION_HOTSPOT_OUTSIDE;
    }

    /**
     * Bind this view to the given document for display.
     *
     * @param doc The document to be bound.
     * @param modelId The model ID of the item.
     */
    @Override
    public void bind(
            DocumentInfo doc, String modelId, @Nullable String summary, boolean justFinishedSync) {
        this.mModelId = modelId;
        mTitle.setText(doc.displayName, TextView.BufferType.SPANNABLE);
    }
}
