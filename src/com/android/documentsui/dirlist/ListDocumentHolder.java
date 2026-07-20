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
import static com.android.documentsui.ui.Views.setWeight;
import static com.android.documentsui.util.FlagUtils.isSingleClickToSelectEnabled;
import static com.android.documentsui.util.FlagUtils.isUseFileSummaryEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;

import com.android.documentsui.ConfigStore;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.ui.Views;
import com.android.modules.utils.build.SdkLevel;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

final class ListDocumentHolder extends DocumentHolder {
    private static final String TAG = "ListDocumentHolder";

    private final TextView mTitle;

    /** Wrapper for the title column which contains title, icon, etc. */
    private final @Nullable View mTitleContainer;

    private final @Nullable TextView mDate; // Non-null for tablets/sw720dp, null for other devices.
    private final @Nullable TextView mSize; // Non-null for tablets/sw720dp, null for other devices.
    private final @Nullable TextView mType; // Non-null for tablets/sw720dp, null for other devices.

    // Summary is only displayed for Material3.
    private final @Nullable TextView mSummary;
    // Container for date + size + summary, null only for tablets/sw720dp
    private final @Nullable LinearLayout mDetails;
    // TextView for date + size + summary, null only for tablets/sw720dp
    private final @Nullable TextView mMetadataView;
    private final ImageView mIconMime;
    private final ImageView mIconThumb;
    private final @Nullable ImageView mIconCheck;
    private final ImageView mIconBadge;
    private final View mIconLayout;
    final View mPreviewIcon;

    private final IconHelper mIconHelper;
    private final Lookup<String, String> mFileTypeLookup;
    // This is used in as a convenience in our bind method.
    private DocumentInfo mDoc;
    private final DocumentsAdapter.Environment mEnv;

    ListDocumentHolder(
            Context context,
            ViewGroup parent,
            IconHelper iconHelper,
            Lookup<String, String> fileTypeLookup,
            ConfigStore configStore,
            DocumentsAdapter.Environment environment) {
        super(context, parent, getRes(R.layout.item_doc_list), configStore);

        boolean showSelectionCheckmark =
                !isSingleClickToSelectEnabled()
                        || itemView.getResources().getBoolean(R.bool.show_selection_checkmark);

        mEnv = environment;
        mIconLayout = itemView.findViewById(getRes(R.id.icon));
        mIconMime = (ImageView) itemView.findViewById(getRes(R.id.icon_mime));
        mIconThumb = (ImageView) itemView.findViewById(getRes(R.id.icon_thumb));
        mIconCheck =
                (ImageView)
                        conditionalView(
                                showSelectionCheckmark,
                                itemView.findViewById(getRes(R.id.icon_check)));
        mIconBadge = (ImageView) itemView.findViewById(getRes(R.id.icon_profile_badge));
        mTitle = (TextView) itemView.findViewById(android.R.id.title);
        mTitleContainer = (View) itemView.findViewById(R.id.title_container);
        mSummary = (TextView) itemView.findViewById(getRes(R.id.file_summary));
        mSize = (TextView) itemView.findViewById(getRes(R.id.size));
        mDate = (TextView) itemView.findViewById(getRes(R.id.date));
        mType = (TextView) itemView.findViewById(getRes(R.id.file_type));
        mMetadataView = (TextView) itemView.findViewById(getRes(R.id.metadata));
        // Warning: mDetails view doesn't exists in layout-sw720dp-land layout
        mDetails = (LinearLayout) itemView.findViewById(getRes(R.id.line2));
        mPreviewIcon = itemView.findViewById(getRes(R.id.preview_icon));

        mIconHelper = iconHelper;
        mFileTypeLookup = fileTypeLookup;
        mDoc = new DocumentInfo();

        if (!showSelectionCheckmark) {
            // Override android:pointerIcon="hand" in the res/**/*.xml layout.
            mIconLayout.setPointerIcon(null);
        }

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
        boolean showSelectionCheckmark = mIconCheck != null;

        // We always want to make sure our check box disappears if we're not selected,
        // even if the item is disabled. But it should be an error (see assert below)
        // to be set to selected && be disabled.
        float checkAlpha = selected ? 1f : 0f;
        if (!showSelectionCheckmark) {
            // No-op.
        } else if (animate) {
            fade(mIconCheck, checkAlpha).start();
        } else {
            mIconCheck.setAlpha(checkAlpha);
        }

        if (!itemView.isEnabled()) {
            assert (!selected);
        }

        super.setSelected(selected, animate);

        if (!showSelectionCheckmark) {
            // No-op.
        } else if (animate) {
            fade(mIconMime, 1f - checkAlpha).start();
            fade(mIconThumb, 1f - checkAlpha).start();
        } else {
            mIconMime.setAlpha(1f - checkAlpha);
            mIconThumb.setAlpha(1f - checkAlpha);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (isUseMaterial3FlagEnabled()) {
            itemView.setAlpha(enabled ? 1f : DISABLED_ALPHA);
        } else {
            // Text colors enabled/disabled is handle via a color set.
            final float imgAlpha = enabled ? 1f : DISABLED_ALPHA;
            mIconMime.setAlpha(imgAlpha);
            mIconThumb.setAlpha(imgAlpha);
        }

        if (!enabled) {
            // Hide the sync state when the user can't do anything to fix it.
            hideSyncIcons();
        }
    }

    @Override
    public void bindPreviewIcon(boolean show, Function<View, Boolean> clickCallback) {
        if (mDoc.isDirectory() || !show) {
            mPreviewIcon.setVisibility(View.GONE);
        } else {
            mPreviewIcon.setVisibility(View.VISIBLE);
            mPreviewIcon.setContentDescription(
                    getPreviewIconContentDescription(
                            mIconHelper.shouldShowBadge(mDoc.userId.getIdentifier()),
                            mDoc.displayName,
                            mDoc.userId));
            mPreviewIcon.setAccessibilityDelegate(new PreviewAccessibilityDelegate(clickCallback));
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
        // If itemView is activated = selected, then whole region is interactive
        if (itemView.isActivated()) {
            return true;
        }

        // Do everything in global coordinates - it makes things simpler.
        int[] coords = new int[2];
        mIconLayout.getLocationOnScreen(coords);

        Rect textBounds = new Rect();
        mTitle.getPaint().getTextBounds(
                mTitle.getText().toString(), 0, mTitle.getText().length(), textBounds);

        Rect rect = new Rect(
                coords[0],
                coords[1],
                coords[0] + mIconLayout.getWidth() + textBounds.width(),
                coords[1] + Math.max(mIconLayout.getHeight(), textBounds.height()));

        // If the tap occurred inside icon or the text, these are interactive spots.
        return rect.contains((int) event.getRawX(), (int) event.getRawY());
    }

    @Override
    public int classifySelectionHotspot(MotionEvent event) {
        boolean showSelectionCheckmark = mIconCheck != null;

        if (mDoc.isDirectory() && (mAction != State.ACTION_BROWSE)) {
            // No-op.

        } else if (showSelectionCheckmark
                && Views.isEventOver(event, itemView.getParent(), mIconLayout)) {
            return ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI;

        } else if (Events.isMousyEvent(event) && isSingleClickToSelectEnabled()) {
            return ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO;
        }

        return ItemDetails.SELECTION_HOTSPOT_OUTSIDE;
    }

    @Override
    public boolean inPreviewIconRegion(MotionEvent event) {
        return Views.isEventOver(event, itemView.getParent(), mPreviewIcon);
    }

    /**
     * If summary column needs to be displayed or hidden, adjust the width of all columns. *
     *
     * <p>NOTE: These values are matched in {@link
     * com.android.documentsui.sorting.TableHeaderController#adjustColumnWidthForSummary()}
     */
    private void adjustColumnWidthForSummary() {
        if (isUseFileSummaryEnabled()) {
            setWeight(mTitleContainer, 0.35f);
            if (mSummary != null) {
                setWeight(mSummary, 0.25f);
            }
            setWeight(mDate, 0.15f);
            setWeight(mSize, 0.15f);
            setWeight(mType, 0.15f);
        } else {
            setWeight(mTitleContainer, 0.4f);
            if (mSummary != null) {
                setWeight(mSummary, 0f);
            }
            setWeight(mDate, 0.2f);
            setWeight(mSize, 0.2f);
            setWeight(mType, 0.2f);
        }
    }

    private void bindSummary(@Nullable String summary) {
        if (mSummary == null) {
            return;
        }

        if (useSummary()) {
            if (TextUtils.isEmpty(summary)) {
                mSummary.setText("—");
                mSummary.setTooltipText(null);
                mSummary.setCompoundDrawables(null, null, null, null);
            } else {
                mSummary.setText(summary, TextView.BufferType.SPANNABLE);
                mSummary.setTooltipText(summary);
                mSummary.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_summary, 0, 0, 0);
            }
            mSummary.setVisibility(View.VISIBLE);
        } else {
            mSummary.setVisibility(View.GONE);
        }
    }

    private boolean useSummary() {
        return mEnv.shouldDisplaySummary();
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

        mIconHelper.stopLoading(mIconThumb);

        mIconMime.animate().cancel();
        mIconMime.setAlpha(1f);
        mIconThumb.animate().cancel();
        mIconThumb.setAlpha(0f);

        mIconHelper.load(mDoc, mIconThumb, mIconMime, /* subIconMime= */ null);

        if (isUseMaterial3FlagEnabled()) {
            // Only Normal type work with ellipsize=middle.
            mTitle.setText(mDoc.displayName, TextView.BufferType.NORMAL);
            mTitle.setTooltipText(mDoc.displayName);
        } else {
            mTitle.setText(mDoc.displayName, TextView.BufferType.SPANNABLE);
        }

        adjustColumnWidthForSummary();
        bindSummary(summary);

        mTitle.setVisibility(View.VISIBLE);

        bindSyncIcons(mDoc, justFinishedSync);

        if (mDoc.isDirectory() && !isUseMaterial3FlagEnabled()) {
            // Note, we don't show any details for any directory...ever.
            if (mDetails != null) {
                // Non-tablets
                mDetails.setVisibility(View.GONE);
            }
        } else {
            if (mMetadataView != null) {
                // In narrow list view, mMetadataView consolidates the metadata info.
                boolean hasDetails = false;

                if (!mDoc.isDirectory()) {
                    ArrayList<String> metadataList = new ArrayList<>(4);

                    if (useSummary() && !TextUtils.isEmpty(summary)) {
                        metadataList.add(summary);
                        mMetadataView.setCompoundDrawablesWithIntrinsicBounds(
                                R.drawable.ic_summary, 0, 0, 0);
                        mMetadataView.setTooltipText(summary);
                    } else {
                        mMetadataView.setCompoundDrawables(null, null, null, null);
                        mMetadataView.setTooltipText(null);
                    }

                    if (isUseMaterial3FlagEnabled()) {
                        if (mDoc.size >= 0) {
                            metadataList.add(Formatter.formatFileSize(mContext, mDoc.size));
                        }

                        if (mDoc.lastModified > 0) {
                            metadataList.add(Shared.formatTime(mContext, mDoc.lastModified));
                        }

                        hasDetails = !metadataList.isEmpty();
                        mMetadataView.setText(TextUtils.join(" • ", metadataList));
                    } else {
                        if (mDoc.lastModified > 0) {
                            metadataList.add(Shared.formatTime(mContext, mDoc.lastModified));
                        }

                        if (mDoc.size >= 0) {
                            metadataList.add(Formatter.formatFileSize(mContext, mDoc.size));
                        }

                        hasDetails = !metadataList.isEmpty();
                        metadataList.add(mFileTypeLookup.lookup(mDoc.mimeType));

                        mMetadataView.setText(TextUtils.join(", ", metadataList));
                    }
                }

                if (mDetails != null) {
                    mDetails.setVisibility(hasDetails ? View.VISIBLE : View.GONE);
                } else {
                    Log.w(TAG, "mDetails is unexpectedly null for non-tablet devices!");
                }
            } else {
                // In wide list view, metadata is provided in columns mDate, mSize, mType.
                assert mDate != null;
                assert mSize != null;
                assert mType != null;

                if (mDoc.lastModified > 0) {
                    mDate.setVisibility(View.VISIBLE);
                    mDate.setText(Shared.formatTime(mContext, mDoc.lastModified));
                } else if (isUseMaterial3FlagEnabled()) {
                    mDate.setVisibility(View.VISIBLE);
                    mDate.setText("—");
                } else {
                    mDate.setVisibility(View.INVISIBLE);
                }

                if (!mDoc.isDirectory() && mDoc.size >= 0) {
                    mSize.setVisibility(View.VISIBLE);
                    mSize.setText(Formatter.formatFileSize(mContext, mDoc.size));
                } else if (isUseMaterial3FlagEnabled()) {
                    mSize.setVisibility(View.VISIBLE);
                    mSize.setText("—");
                } else {
                    mSize.setVisibility(View.INVISIBLE);
                }

                mType.setText(mFileTypeLookup.lookup(mDoc.mimeType));
            }
        }

        // TODO: Add document debug info
        // Call includeDebugInfo
    }
}
