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

package com.android.documentsui.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LaptopChromebook
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.android.documentsui.compose.R
import com.android.documentsui.compose.data.RootListItem
import com.android.documentsui.compose.data.RootsViewModel
import com.android.documentsui.compose.data.model.Root
import com.android.documentsui.compose.data.model.RootType

/** Composable for the roots list. Displayed in the navigation drawer. */
@Composable
fun RootsList(closeDrawer: () -> Unit) {
    val rootViewModel: RootsViewModel = hiltViewModel()
    val listItems by rootViewModel.listItems.collectAsState()
    val selectedRoot by rootViewModel.selectedRoot.collectAsState()

    val RootNavigationDrawerItem =
        @Composable { label: String, iconImageVector: ImageVector, root: Root ->
            NavigationDrawerItem(
                label = { Text(label) },
                icon = { Icon(imageVector = iconImageVector, contentDescription = label) },
                selected = selectedRoot == root,
                onClick = {
                    rootViewModel.onRootSelected(root)
                    closeDrawer()
                },
                shape = RoundedCornerShape(8.dp),
            )
        }

    LazyColumn(
        modifier =
            Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                .fillMaxHeight()
                .testTag("RootsList"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(listItems) { item ->
            when (item) {
                is RootListItem.RootItem -> {
                    when (item.root.type) {
                        RootType.RECENTS -> {
                            RootNavigationDrawerItem(
                                stringResource(R.string.recents_label),
                                Icons.Outlined.Schedule,
                                item.root,
                            )
                        }
                        RootType.DOWNLOADS -> {
                            RootNavigationDrawerItem(
                                stringResource(R.string.downloads_label),
                                Icons.Outlined.Download,
                                item.root,
                            )
                        }
                        RootType.PRIMARY -> {
                            RootNavigationDrawerItem(
                                stringResource(R.string.this_laptop_label),
                                Icons.Outlined.LaptopChromebook,
                                item.root,
                            )
                        }
                        RootType.USB -> {
                            RootNavigationDrawerItem(
                                item.root.displayName,
                                Icons.Outlined.Usb,
                                item.root,
                            )
                        }
                        RootType.GENERIC,
                        RootType.TRASH -> {
                            RootNavigationDrawerItem(
                                item.root.displayName,
                                Icons.Outlined.Storage,
                                item.root,
                            )
                        }
                    }
                }
                is RootListItem.DividerItem -> {
                    HorizontalDivider()
                }
            }
        }
    }
}
