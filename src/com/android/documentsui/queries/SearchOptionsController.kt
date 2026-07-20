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
package com.android.documentsui.queries

import android.content.Context
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.MenuItem
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.iterator
import com.android.documentsui.MetricConsts
import com.android.documentsui.R
import com.android.documentsui.base.RootInfo
import com.android.documentsui.util.FlagUtils
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.chip.Chip
import java.time.LocalDate
import java.time.ZoneId

/**
 * Retrieves an enum of the given type. This wraps the deprecated API, on which we must rely due to
 * DocsUI relying on API level 30.
 */
private inline fun <reified T : Enum<T>> retrieveEnum(key: String, bundle: Bundle, fallback: T): T {
    return bundle.getSerializable(key) as T ?: fallback
}

/**
 * The controller for the search option dropdowns. This controller manages the UI interactions and
 * converts them to a state of the dropdowns. It must be created with the view that contains the
 * buttons that trigger showing or hiding of the dropdowns.
 */
class SearchOptionsController(private val container: View?) {
    // The value of currently selected options. Initialized to sensible defaults.
    private var lastModifiedOption: LastModifiedOption = DEFAULT_LAST_MODIFIED

    private var fileTypeOption: FileTypeOption = DEFAULT_FILE_TYPE
    private var locationOption: SearchLocationOption = DEFAULT_LOCATION

    // We dynamically set the name of the root folder, based on where in the directory tree
    // the user is located at the time search is opened. This does not change while the search
    // options are visible.
    private var currentRoot: RootInfo? = null

    // Keeps the name of the last seen root. If the application is restarted, and the new set
    // root is

    companion object {
        val LOCATION_KEY = "${SearchOptionsController::class.qualifiedName}:location"
        val LOCATION_TEXT_KEY = "${SearchOptionsController::class.qualifiedName}:locationText"
        val FILE_TYPE_KEY = "${SearchOptionsController::class.qualifiedName}:fileType"
        val LAST_MODIFIED_KEY = "${SearchOptionsController::class.qualifiedName}:lastModified"
        val CURRENT_ROOT_KEY = "${SearchOptionsController::class.qualifiedName}:currentRoot"

        val DEFAULT_LAST_MODIFIED = LastModifiedOption.ANY_TIME
        val DEFAULT_FILE_TYPE = FileTypeOption.ANY_TYPE
        val DEFAULT_LOCATION = SearchLocationOption.ROOT_FOLDER
    }

    // A single listener to query option change events.
    private var optionsListener: SearchOptionsListener? = null

    init {
        if (FlagUtils.isUseMaterial3FlagEnabled() && container != null) {
            makeTrigger(
                getRes(R.id.search_location_trigger),
                getRes(R.menu.search_location_menu),
                this::onLocationSelected,
            )
            makeTrigger(
                getRes(R.id.search_last_modified_trigger),
                getRes(R.menu.search_last_modified_menu),
                this::onLastModifiedSelected,
            )
            makeTrigger(
                getRes(R.id.search_file_type_trigger),
                getRes(R.menu.search_file_type_menu),
                this::onFileTypeSelected,
            )
        }
    }

    private fun getSelectedMenuOption(@MenuRes menuId: Int): Int {
        return when (menuId) {
            getRes(R.menu.search_location_menu) -> locationOption.value
            getRes(R.menu.search_last_modified_menu) -> lastModifiedOption.value
            getRes(R.menu.search_file_type_menu) -> fileTypeOption.value
            else -> throw IllegalArgumentException("Unexpected menu ID $menuId")
        }
    }

    /** Explicitly sets the file type based on a MetricConsts. */
    fun setSelectedFileType(@MetricConsts.SearchType typeId: Int) {
        fileTypeOption =
            when (typeId) {
                MetricConsts.TYPE_CHIP_AUDIOS -> FileTypeOption.AUDIO
                MetricConsts.TYPE_CHIP_DOCS -> FileTypeOption.DOCUMENTS
                MetricConsts.TYPE_CHIP_IMAGES -> FileTypeOption.IMAGES
                MetricConsts.TYPE_CHIP_VIDEOS -> FileTypeOption.VIDEO
                else -> throw IllegalArgumentException("Cannot convert $typeId to file type")
            }
    }

    /** Helper function that removes repetitive setup for each of the option triggers. */
    private fun makeTrigger(
        @IdRes triggerId: Int,
        @MenuRes menuId: Int,
        callback: (option: Int) -> Boolean,
    ) {
        val trigger = container?.findViewById<Chip>(triggerId)
        trigger?.setOnClickListener {
            showMenu(trigger, menuId) {
                if (callback(it)) {
                    notifyOptionsChangeListener()
                }
            }
        }
    }

    /**
     * Updates the location option based on the given resource ID, and updates the last modified
     * chip. Returns true, if the option has been changed. Otherwise, returns false.
     */
    fun onLocationSelected(locationId: Int): Boolean {
        val selectedOption = searchLocationOptionFor(locationId) ?: return false
        if (selectedOption == locationOption) {
            return false
        }
        locationOption = selectedOption
        updateLastModifiedChip()
        return true
    }

    /**
     * Updates the text shown on the last modified chip to correspond to the currently set
     * lastModifiedOption. This is needed as we update the last modified option each time the root
     * is changed, and not only each time the user selects a new last modified option dropdown.
     */
    private fun updateLastModifiedChip() {
        val chip = container?.findViewById<Chip>(getRes(R.id.search_last_modified_trigger))
        chip?.text = container.resources.getString(lastModifiedOption.textId)
    }

    /**
     * Updates the file type option based on the given resource ID. Returns true, if the option has
     * been changed. Otherwise, returns false.
     */
    fun onFileTypeSelected(fileTypeId: Int): Boolean {
        val selectedOption = fileTypeOptionFor(fileTypeId) ?: return false
        if (selectedOption == fileTypeOption) {
            return false
        }
        fileTypeOption = selectedOption
        return true
    }

    /**
     * Updates the last modified option based on the given resource ID. Returns true, if the option
     * has been changed. Otherwise, returns false.
     */
    fun onLastModifiedSelected(lastModifiedId: Int): Boolean {
        val selectedOption = lastModifiedOptionFor(lastModifiedId) ?: return false
        if (selectedOption == lastModifiedOption) {
            return false
        }
        lastModifiedOption = selectedOption
        return true
    }

    /** Notifies an option listener about options change, if one is registered. */
    fun notifyOptionsChangeListener() {
        optionsListener?.onOptionsChanged(
            SearchOptionsState(fileTypeOption, lastModifiedOption, locationOption)
        )
    }

    /**
     * Sets the option change listener. Currently only one listener is supported. If the listener is
     * set to null, that is equivalent to removing the listener.
     */
    fun setOptionChangeListener(listener: SearchOptionsListener) {
        optionsListener = listener
    }

    /**
     * Creates a bundle, potentially empty, that contains DocumentsContract last modified argument
     * set based on mLastModifiedOptions.
     */
    private fun getLastModifiedQueryArgs(): Bundle {
        val bundle = Bundle()
        if (lastModifiedOption != LastModifiedOption.ANY_TIME) {
            bundle.putLong(
                DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER,
                LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() -
                    lastModifiedOption.millis,
            )
        }
        return bundle
    }

    private fun getFileTypeQueryArgs(): Bundle {
        val bundle = Bundle()
        val mimeTypes =
            when (fileTypeOption) {
                FileTypeOption.AUDIO -> SearchChipViewManager.AUDIO_MIMETYPES
                FileTypeOption.DOCUMENTS -> SearchChipViewManager.DOCUMENTS_MIMETYPES
                FileTypeOption.IMAGES -> SearchChipViewManager.IMAGES_MIMETYPES
                FileTypeOption.VIDEO -> SearchChipViewManager.VIDEOS_MIMETYPES
                else -> arrayOf<String>()
            }
        if (mimeTypes.isNotEmpty()) {
            bundle.putStringArray(DocumentsContract.QUERY_ARG_MIME_TYPES, mimeTypes)
        }
        return bundle
    }

    /** Creates a bundle with query arguments compliant with DocumentsContract. */
    fun getOptionsQueryArgs(): Bundle {
        val bundle = Bundle()
        bundle.putAll(getLastModifiedQueryArgs())
        bundle.putAll(getFileTypeQueryArgs())
        return bundle
    }

    /**
     * Shows the search dropdown options bar. Before the dropdowns are made visible, their state and
     * text is adjusted based on the root set in the #setRoot() method (which must be called before
     * the show() method is called).
     */
    fun show() {
        if (container == null) {
            return
        }
        // If the locationOption is the top folder, update the location trigger text.
        val chip = container.findViewById<Chip>(getRes(R.id.search_location_trigger))
        if (chip != null && locationOption == SearchLocationOption.ROOT_FOLDER) {
            chip.text = getRootFolderFallbackText(container.context)
        }
        updateLastModifiedChip()
        val typeChip = container.findViewById<Chip>(getRes(R.id.search_file_type_trigger))
        if (typeChip != null) {
            typeChip.text = container.resources.getString(fileTypeOption.textId)
        }
        container.visibility = View.VISIBLE
    }

    /** Hides the dropdown bar by making it GONE. */
    fun hide() {
        container?.visibility = View.GONE
    }

    /**
     * Returns the text to be shown in the root folder. This method uses the current root's title,
     * if available, otherwise falls back to the hardcoded default.
     */
    private fun getRootFolderFallbackText(context: Context): String {
        return currentRoot?.title ?: context.getString(R.string.search_location_root_folder)
    }

    /**
     * Records a root on which search options act. This method does nothing if the search option
     * container is not set up (pre V2 search), or the set root is the same as the current root. The
     * latter is done to prevent resetting of the locationOption, which is set to the root folder
     * (top folder) the first time the root is changed. This method always returns the currently set
     * location option, so that the caller can keep in sync with what is known to this controller.
     *
     * @return The location option for the set root.
     */
    fun setRoot(root: RootInfo?): SearchLocationOption {
        if (container == null) {
            return locationOption
        }
        if (currentRoot == root) {
            // Copy root, as currentRoot may be a partial root, restored from a bundle.
            currentRoot = root
            // If this root was already set, do nothing. We wish to react to the first setting of
            // a new root by adjusting location and last modified options. However, if the user
            // changes the location option we get another call to setRoot(). Yet in such a case we
            // must not adjust the location, but rather rely on the user set value.
            return locationOption
        }
        currentRoot = root
        locationOption = SearchLocationOption.ROOT_FOLDER
        lastModifiedOption = getLastModifiedForRoot(root)
        return locationOption
    }

    /** Returns the starting last modified option for the given root. */
    private fun getLastModifiedForRoot(root: RootInfo?) =
        if (root == null || !root.isRecents) {
            LastModifiedOption.ANY_TIME
        } else {
            LastModifiedOption.LAST_30_DAYS
        }

    /** Returns whether or not this controller is visible. */
    fun isVisible(): Boolean {
        return container?.visibility == View.VISIBLE
    }

    fun showMenu(chip: Chip, @MenuRes menuRes: Int, callback: (option: Int) -> Unit) {
        // We prevent the chip from working as a chip. Instead it acts as a button.
        chip.isChecked = false

        // Activate the specified popup menu for the chip.
        PopupMenu(chip.context, chip).apply {
            menuInflater.inflate(menuRes, menu)
            setForceShowIcon(true)
            setOnMenuItemClickListener { menuItem: MenuItem ->
                chip.text = menuItem.title
                callback(menuItem.itemId)
                false
            }
            val selectedValue = getSelectedMenuOption(menuRes)
            for (menuItem in menu) {
                if (menuItem.itemId == R.id.root_folder_option) {
                    menuItem.title = getRootFolderFallbackText(chip.context)
                }
                if (menuItem.itemId != selectedValue) {
                    menuItem.icon = null
                }
            }
            show()
        }
    }

    /** Stores the current state of the controls. */
    fun saveState(bundle: Bundle?) {
        if (bundle == null || container == null) {
            return
        }
        bundle.putSerializable(LOCATION_KEY, locationOption)
        val locationChip = container.findViewById<Chip>(R.id.search_location_trigger)
        if (locationChip != null) {
            bundle.putString(LOCATION_TEXT_KEY, locationChip.getText().toString())
        }
        bundle.putSerializable(FILE_TYPE_KEY, fileTypeOption)
        bundle.putSerializable(LAST_MODIFIED_KEY, lastModifiedOption)
        if (currentRoot != null) {
            bundle.putParcelable(CURRENT_ROOT_KEY, currentRoot)
        }
    }

    /** Restores the values of the options from the given bundle. */
    fun restoreState(bundle: Bundle?) {
        if (bundle == null) {
            return
        }
        locationOption = retrieveEnum(LOCATION_KEY, bundle, DEFAULT_LOCATION)
        val locationText = bundle.getString(LOCATION_TEXT_KEY)
        if (locationText != null) {
            val locationChip = container?.findViewById<Chip>(R.id.search_location_trigger)
            locationChip?.text = locationText
        }
        fileTypeOption = retrieveEnum(FILE_TYPE_KEY, bundle, DEFAULT_FILE_TYPE)
        lastModifiedOption = retrieveEnum(LAST_MODIFIED_KEY, bundle, DEFAULT_LAST_MODIFIED)
        currentRoot = bundle.getParcelable(CURRENT_ROOT_KEY)
    }
}
