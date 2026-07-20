/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.documentsui.compose.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.documentsui.compose.data.model.Root
import com.android.documentsui.compose.data.model.RootType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** A list item for the roots list. */
sealed class RootListItem {
    /** A root item in the roots list. */
    data class RootItem(val root: Root) : RootListItem()

    /** A divider item in the roots list. */
    object DividerItem : RootListItem()
}

/** A view model for the roots list. */
@HiltViewModel
open class RootsViewModel
@Inject
constructor(private val documentsProviderDataSource: DocumentsProviderDataSource) : ViewModel() {
    /** The immutable state flow that emits a list root items (ordered and divided). */
    val listItems: StateFlow<List<RootListItem>> =
        documentsProviderDataSource.rootsFlow
            .onEach(::rootsChanged)
            .map(::createRootsListItems)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    /** The private mutable state flow for the selected root. */
    protected val _selectedRoot = MutableStateFlow<Root?>(null)

    /** The immutable state flow that emits the selected root. */
    val selectedRoot: StateFlow<Root?> = _selectedRoot.asStateFlow()

    /** Updates the selected root. */
    fun onRootSelected(root: Root) {
        _selectedRoot.value = root
    }

    /** When roots change, update the selected root if necessary. */
    private fun rootsChanged(roots: List<Root>) {
        if (roots.isEmpty()) {
            _selectedRoot.value = null
            return
        }

        // TODO: When the roots are loaded incrementally, this needs to change as well.
        if (_selectedRoot.value == null || _selectedRoot.value !in roots) {
            _selectedRoot.value =
                roots.find { it.type.order == RootType.PRIMARY.order }
                    ?: roots.minByOrNull { it.type.order }
        }
    }

    /** Creates a list of root list items from a list of roots. */
    private fun createRootsListItems(roots: List<Root>): List<RootListItem> {
        val rootItems = roots.sortedBy { it.type.order }.map { root -> RootListItem.RootItem(root) }

        // Group the roots, roots in the same 100s are grouped together with a divider in between.
        return buildList {
            rootItems
                .groupBy { it.root.type.order / 100 }
                .forEach { (group, roots) ->
                    if (group != 0) {
                        add(RootListItem.DividerItem)
                    }
                    addAll(roots)
                }
        }
    }
}
