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
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.format.Formatter;
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
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.ui.Views;
import com.android.modules.utils.build.SdkLevel;

import com.google.android.material.card.MaterialCardView;

import java.util.Map;
import java.util.function.Function;

final class GridDocumentHolder extends DocumentHolder {

    final TextView mTitle;
    final TextView mDate;
    final TextView mDetails;
    // Non-null only when useMaterial3 flag is ON.
    final @Nullable TextView mBullet;
    final ImageView mIconMimeLg;
    // Null when useMaterial3 flag is ON.
    final @Nullable ImageView mIconMimeSm;
    final ImageView mIconThumb;
    final ImageView mIconBadge;
    final IconHelper mIconHelper;
    final View mPreviewIcon;
    boolean mHasSelectionRegion;
    final DocumentsAdapter.Environment mEnv;

    // This is used in as a convenience in our bind method.
    private DocumentInfo mDoc = new DocumentInfo();

    // Non-null only when useMaterial3 flag is ON.
    // private final @Nullable MaterialCardView mIconWrapper;

    GridDocumentHolder(
            Context context,
            ViewGroup parent,
            IconHelper iconHelper,
            ConfigStore configStore,
            DocumentsAdapter.Environment environment) {
        super(context, parent, getRes(R.layout.item_doc_grid), configStore);

        mEnv = environment;
        if (isUseMaterial3FlagEnabled()) {
            mBullet = itemView.findViewById(getRes(R.id.bullet));
            // mIconWrapper = itemView.findViewById(getRes(R.id.icon_wrapper));
            mIconMimeSm = null;
        } else {
            mBullet = null;
            // mIconWrapper = null;
            mIconMimeSm = (ImageView) itemView.findViewById(getRes(R.id.icon_mime_sm));
        }

        mTitle = (TextView) itemView.findViewById(android.R.id.title);
        mDate = (TextView) itemView.findViewById(getRes(R.id.date));
        mDetails = (TextView) itemView.findViewById(getRes(R.id.details));
        mIconMimeLg = (ImageView) itemView.findViewById(getRes(R.id.icon_mime_lg));
        mIconThumb = (ImageView) itemView.findViewById(getRes(R.id.icon_thumb));
        mIconBadge = (ImageView) itemView.findViewById(getRes(R.id.icon_profile_badge));
        mPreviewIcon = itemView.findViewById(getRes(R.id.preview_icon));
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
        // But it should be an error to be set to selected && be disabled.
        if (!itemView.isEnabled()) {
            assert (!selected);
        }

        // The checkbox is intentionally not shown. Selection is instead indicated
        // by the whole item being framed / boxed (see grid_item_selection_box_m3).
        super.setSelected(selected, animate);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        float imgAlpha = enabled ? 1f : DISABLED_ALPHA;

        if (isUseMaterial3FlagEnabled()) {
            itemView.setAlpha(imgAlpha);
        } else {
            mIconMimeLg.setAlpha(imgAlpha);
            mIconMimeSm.setAlpha(imgAlpha);
            mIconThumb.setAlpha(imgAlpha);
        }

        if (!enabled) {
            // Hide the sync state when the user can't do anything to fix it.
            hideSyncIcons();
        }
    }

    @Override
    public void bindPreviewIcon(boolean show, Function<View, Boolean> clickCallback) {
        if (isUseMaterial3FlagEnabled() && mDoc.isDirectory()) {
            mPreviewIcon.setVisibility(View.GONE);
            return;
        }
        mPreviewIcon.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            mPreviewIcon.setContentDescription(
                    getPreviewIconContentDescription(
                            mIconHelper.shouldShowBadge(mDoc.userId.getIdentifier()),
                            mDoc.displayName, mDoc.userId));
            mPreviewIcon.setAccessibilityDelegate(
                    new PreviewAccessibilityDelegate(clickCallback));
        }
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
        if (!isUseMaterial3FlagEnabled()) {
            // Entire grid box is the selection region when the user clicks with a mouse.
            return Events.isMousyEvent(event)
                    ? ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI
                    : ItemDetails.SELECTION_HOTSPOT_OUTSIDE;
        }

        if (!mHasSelectionRegion) {
            return ItemDetails.SELECTION_HOTSPOT_OUTSIDE;
        }

        // Whole item is the selectable box: clicking anywhere on the grid item toggles
        // its selection. Touch stays as-is (tap activates/opens) to preserve the
        // picker/open behavior.
        return Events.isMousyEvent(event)
                ? ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI
                : ItemDetails.SELECTION_HOTSPOT_OUTSIDE;
    }

    @Override
    public boolean inPreviewIconRegion(MotionEvent event) {
        return Views.isEventOver(event, itemView.getParent(), mPreviewIcon);
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
        mModelId = modelId;
        mDoc = doc;

        // Only have a selection region when the Material3 flag is on and if this is in non-browsing
        // mode, the item must not be a folder.
        mHasSelectionRegion =
                isUseMaterial3FlagEnabled() && (!mDoc.isDirectory()
                        || mAction == State.ACTION_BROWSE);

        mIconHelper.stopLoading(mIconThumb);

        mIconMimeLg.animate().cancel();
        mIconMimeLg.setAlpha(1f);
        mIconThumb.animate().cancel();
        mIconThumb.setAlpha(0f);

        mIconHelper.load(mDoc, mIconThumb, mIconMimeLg, mIconMimeSm);

        if (isUseMaterial3FlagEnabled()) {
            // Only Normal type works with ellipsize=middle.
            mTitle.setText(mDoc.displayName, TextView.BufferType.NORMAL);
        } else {
            mTitle.setText(mDoc.displayName, TextView.BufferType.SPANNABLE);
        }
        mTitle.setVisibility(View.VISIBLE);
        // Show the full name in a tooltip.
        itemView.setTooltipText(mDoc.displayName);

        // If file is partial, we want to show summary field as that's more relevant than
        // fileSize and date.
        if (mDoc.isPartial()) {
            final String docSummary = mDoc.summary;
            mDetails.setVisibility(View.VISIBLE);
            if (isUseMaterial3FlagEnabled()) {
                mDate.setVisibility(View.GONE);
            } else {
                mDate.setText(null);
            }
            mDetails.setText(docSummary);
        } else {
            if ((isUseMaterial3FlagEnabled() && mDoc.isDirectory()) || mDoc.lastModified <= 0) {
                if (isUseMaterial3FlagEnabled()) {
                    mDate.setVisibility(View.GONE);
                } else {
                    mDate.setText(null);
                }
            } else {
                mDate.setText(Shared.formatTime(mContext, mDoc.lastModified));
            }

            final long docSize = mDoc.size;
            if (mDoc.isDirectory() || docSize == -1) {
                mDetails.setVisibility(View.GONE);
            } else {
                mDetails.setVisibility(View.VISIBLE);
                mDetails.setText(Formatter.formatFileSize(mContext, docSize));
            }
        }

        if (mBullet != null && (mDetails.getText() == null || mDetails.getText().length() == 0
                || mDate.getText() == null || mDate.getText().length() == 0)) {
            // There is no need for the bullet separating the details and date.
            mBullet.setVisibility(View.GONE);
        }

        bindSyncIcons(mDoc, justFinishedSync);
    }
}
