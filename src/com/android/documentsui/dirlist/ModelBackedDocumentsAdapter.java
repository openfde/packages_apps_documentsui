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

import static com.android.documentsui.base.State.MODE_GRID;
import static com.android.documentsui.base.State.MODE_LIST;
import static com.android.documentsui.util.FlagUtils.isDesktopUxPhase2FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isSyncStateEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;

import com.android.documentsui.ConfigStore;
import com.android.documentsui.Model;
import com.android.documentsui.Model.Update;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.NetworkMonitor;
import com.android.documentsui.base.State;
import com.android.modules.utils.build.SdkLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Adapts from dirlist.Model to something RecyclerView understands. */
final class ModelBackedDocumentsAdapter extends DocumentsAdapter {

    private static final String TAG = "ModelBackedDocuments";

    // Provides access to information needed when creating and view holders. This
    // isn't an ideal pattern (more transitive dependency stuff) but good enough for now.
    private final Environment mEnv;
    private final IconHelper mIconHelper;  // a transitive dependency of the holders.
    private final Lookup<String, String> mFileTypeLookup;
    private final ConfigStore mConfigStore;
    private final Handler mHandler;

    /**
     * An ordered list of model IDs. This is the data structure that determines what shows up in
     * the UI, and where.
     */
    private List<String> mModelIds = new ArrayList<>();

    /** A lazily instantiated HashMap from mModelIds elements to mModelIds indexes. */
    private HashMap<String, Integer> mModelIdsAsHashMap;

    private Set<String> mJustFinishedSyncingModelIds = new HashSet<>();
    private Set<String> mPreviousSyncInProgressModelIds = new HashSet<>();
    private EventListener<Model.Update> mModelUpdateListener;
    private NetworkMonitor.NetworkListener mNetworkListener = isOnline -> {
        // Do nothing. Logic is handled in DirectoryAddonsAdapter.java.
    };

    @VisibleForTesting
    public HashMap<String, Runnable> mJustFinishedSyncingRemovalTasks = new HashMap<>();

    public ModelBackedDocumentsAdapter(
            Environment env, IconHelper iconHelper, Lookup<String, String> fileTypeLookup,
            ConfigStore configStore) {
        this(env, iconHelper, fileTypeLookup, configStore, new Handler(Looper.getMainLooper()));
    }

    ModelBackedDocumentsAdapter(
            Environment env,
            IconHelper iconHelper,
            Lookup<String, String> fileTypeLookup,
            ConfigStore configStore,
            Handler handler) {
        mEnv = env;
        mIconHelper = iconHelper;
        mFileTypeLookup = fileTypeLookup;
        mConfigStore = configStore;
        mHandler = handler;

        mModelUpdateListener = new EventListener<Model.Update>() {
            @Override
            public void accept(Update event) {
                if (event.hasException()) {
                    onModelUpdateFailed(event.getException());
                } else {
                    onModelUpdate(mEnv.getModel());
                }
            }
        };
    }

    @Override
    EventListener<Update> getModelUpdateListener() {
        return mModelUpdateListener;
    }

    @Override
    NetworkMonitor.NetworkListener getNetworkListener() {
        return mNetworkListener;
    }

    @Override
    public DocumentHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        DocumentHolder holder = null;
        final State state = mEnv.getDisplayState();
        switch (state.derivedMode) {
            case MODE_GRID:
                switch (viewType) {
                    case ITEM_TYPE_DIRECTORY:
                        // Under the Material3 flag, the GridDocumentHolder is the holder for all
                        // grid items.
                        holder =
                                isUseMaterial3FlagEnabled()
                                        ? new GridDocumentHolder(
                                                mEnv.getContext(),
                                                parent,
                                                mIconHelper,
                                                mConfigStore,
                                                mEnv)
                                        : new GridDirectoryHolder(
                                                mEnv.getContext(),
                                                parent,
                                                mIconHelper,
                                                mConfigStore);
                        break;
                    case ITEM_TYPE_DOCUMENT:
                        holder =
                                (!isUseMaterial3FlagEnabled() && state.isPhotoPicking())
                                        ? new GridPhotoHolder(
                                                mEnv.getContext(),
                                                parent,
                                                mIconHelper,
                                                mConfigStore)
                                        : new GridDocumentHolder(
                                                mEnv.getContext(),
                                                parent,
                                                mIconHelper,
                                                mConfigStore,
                                                mEnv);
                        break;
                    default:
                        throw new IllegalStateException("Unsupported layout type.");
                }
                break;
            case MODE_LIST:
                holder =
                        new ListDocumentHolder(
                                mEnv.getContext(),
                                parent,
                                mIconHelper,
                                mFileTypeLookup,
                                mConfigStore,
                                mEnv);
                break;
            default:
                throw new IllegalStateException("Unsupported layout mode.");
        }

        mEnv.initDocumentHolder(holder);
        return holder;
    }

    @Override
    public void onBindViewHolder(DocumentHolder holder, int position, List<Object> payload) {
        if (payload.contains(SelectionTracker.SELECTION_CHANGED_MARKER)) {
            final boolean selected = mEnv.isSelected(mModelIds.get(position));
            holder.setSelected(selected, true);
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public void onBindViewHolder(DocumentHolder holder, int position) {
        String modelId = mModelIds.get(position);
        Cursor cursor = mEnv.getModel().getItem(modelId);
        DocumentInfo doc = DocumentInfo.fromDirectoryCursor(cursor);
        String summary = doc.summary;

        if (mEnv.shouldDisplaySummary()) {
            // For Download provider, the summary is set to the display name.
            if (summary == null || summary.equals(doc.displayName)) {
                summary = mEnv.getModel().getSummary(modelId);
            }
        }
        if (isUseMaterial3FlagEnabled()) {
            // Need the action to be set for bind().
            holder.setAction(mEnv.getDisplayState().action);
        }
        boolean justFinishedSync =
                isSyncStateEnabled() ? mJustFinishedSyncingModelIds.contains(modelId) : false;
        holder.bind(doc, modelId, summary, justFinishedSync);

        boolean enabled = mEnv.isDocumentEnabled(doc);
        boolean selected = mEnv.isSelected(modelId);
        if (!enabled) {
            assert (!selected);
        }
        holder.setEnabled(enabled);
        holder.setSelected(mEnv.isSelected(modelId), false);
        if (!isUseMaterial3FlagEnabled()) {
            holder.setAction(mEnv.getDisplayState().action);
        }
        boolean showPreview =
                enabled
                        && mEnv.getDisplayState()
                                .shouldShowPreview(
                                        !isDesktopUxPhase2FlagEnabled()
                                                || mEnv.getContext()
                                                        .getResources()
                                                        .getBoolean(
                                                                com.android.documentsui.R.bool
                                                                        .show_preview_icon));
        holder.bindPreviewIcon(
                showPreview, view -> mEnv.getActionHandler().previewItem(holder.getItemDetails()));
        int userId = doc.userId.getIdentifier();
        if (mConfigStore.isPrivateSpaceInDocsUIEnabled() && SdkLevel.isAtLeastS()) {
            holder.bindProfileIcon(mIconHelper.shouldShowBadge(userId), userId);
        } else {
            holder.bindBriefcaseIcon(mIconHelper.shouldShowBadge(userId));
        }

        mEnv.onBindDocumentHolder(holder, cursor);
    }

    @Override
    public int getItemCount() {
        return mModelIds.size();
    }

    private void onModelUpdate(Model model) {
        String[] modelIds = model.getModelIds();
        mModelIdsAsHashMap = null;
        mModelIds = new ArrayList<>(modelIds.length);
        for (String id : modelIds) {
            mModelIds.add(id);
        }

        if (isSyncStateEnabled()) {
            for (String id : mPreviousSyncInProgressModelIds) {
                if (!model.getSyncInProgressModelIds().contains(id)) {
                    // The item just finished syncing.
                    handleJustFinishedSync(id);
                }
            }

            // Update mPreviousSyncInProgressModelIds with the current state.
            mPreviousSyncInProgressModelIds.clear();
            mPreviousSyncInProgressModelIds.addAll(model.getSyncInProgressModelIds());
        }
    }

    /**
     * Adds the `docId` to the `mJustFinishedSyncingModelIds` and schedules a task to remove it from
     * that set after `TICK_VISIBLE_DURATION_MS`. If the `docId` is already in
     * `mJustFinishedSyncingModelIds` then remove the existing task first.
     */
    private void handleJustFinishedSync(String modelId) {
        if (!isSyncStateEnabled()) {
            return;
        }
        // Add the `docId` to this set so that the tick icon is shown.
        mJustFinishedSyncingModelIds.add(modelId);

        // Remove any existing tasks for this `docId`.
        Runnable existingTask = mJustFinishedSyncingRemovalTasks.get(modelId);
        if (existingTask != null) {
            mHandler.removeCallbacks(existingTask);
        }

        // Schedule a new task to remove the `docId` from the `mJustFinishedSyncingModelIds` after
        // `TICK_VISIBLE_DURATION_MS`.
        Runnable removalTask =
                () -> {
                    mJustFinishedSyncingModelIds.remove(modelId);
                    mJustFinishedSyncingRemovalTasks.remove(modelId);

                    int position = getAdapterPosition(modelId);
                    if (position != RecyclerView.NO_POSITION) {
                        // Trigger a directory list refresh so that the tick icon disappears.
                        notifyItemChanged(position);
                    }
                };
        mJustFinishedSyncingRemovalTasks.put(modelId, removalTask);
        mHandler.postDelayed(removalTask, mEnv.getTickDuration());
    }

    private void onModelUpdateFailed(Exception e) {
        Log.w(TAG, "Model update failed.", e);
        mModelIds.clear();
        mModelIdsAsHashMap = null;
    }

    /**
     * Equivalent to mModelIds.indexOf(modelId) but optimized, since mModelIds is an ArrayList and
     * ArrayList.indexOf has O(N) algorithmic complexity.
     *
     * <p>If mModelIds.size() is 'large' (for some arbitrary threshold) then this lazily creates a
     * HashMap of the indexOf results. HashMap.get has O(log(N)) algorithmic complexity.
     *
     * <p>When hitting Ctrl-A (select all) in a directory holding N files, recyclerview-selection's
     * EventBridge's onItemStateChanged can trigger on all N files, each calling this object's
     * getPosition method, synchronously, on the UI thread. This lazy-HashMap optimization reduces
     * the overall complexity from O(N²) to O(N×log(N)).
     */
    private int getIndexOf(String modelId) {
        if ((mModelIds.size() < 32) || !isUseMaterial3FlagEnabled()) {
            return mModelIds.indexOf(modelId);
        } else if (mModelIdsAsHashMap == null) {
            mModelIdsAsHashMap = new HashMap<String, Integer>();
            for (int i = 0; i < mModelIds.size(); i++) {
                mModelIdsAsHashMap.put(mModelIds.get(i), i);
            }
        }

        Integer i = mModelIdsAsHashMap.get(modelId);
        return (i != null) ? i.intValue() : -1;
    }

    @Override
    public String getStableId(int adapterPosition) {
        return mModelIds.get(adapterPosition);
    }

    @Override
    public int getAdapterPosition(String modelId) {
        return getIndexOf(modelId);
    }

    @Override
    public List<String> getStableIds() {
        return mModelIds;
    }

    @Override
    public int getPosition(String id) {
        int position = getIndexOf(id);
        return position >= 0 ? position : RecyclerView.NO_POSITION;
    }

    @Override
    public int getItemViewType(int position) {
        return isDirectory(mEnv.getModel(), position)
                ? ITEM_TYPE_DIRECTORY
                : ITEM_TYPE_DOCUMENT;
    }

    /**
     * @return true if the item type is either document or directory, false for all other
     * possible types.
     */
    public static boolean isContentType(int type) {
        switch (type) {
            case ModelBackedDocumentsAdapter.ITEM_TYPE_DOCUMENT:
            case ModelBackedDocumentsAdapter.ITEM_TYPE_DIRECTORY:
                return true;
        }
        return false;
    }

    @Override
    public void onSummariesUpdated(List<Integer> updatedIndices) {
        for (int index : updatedIndices) {
            if (index >= 0 && index < mModelIds.size()) {
                notifyItemChanged(index);
            }
        }
    }
}
