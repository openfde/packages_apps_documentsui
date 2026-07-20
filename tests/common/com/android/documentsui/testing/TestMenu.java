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

package com.android.documentsui.testing;

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import android.util.SparseArray;
import android.view.Menu;

import androidx.appcompat.widget.SearchView;

import com.android.documentsui.R;

import org.mockito.Mockito;

/**
 * Test copy of {@link android.view.Menu}.
 *
 * We use abstract so we don't have to implement all the necessary methods from the interface,
 * and we use Mockito to just mock out the methods we need.
 * To get an instance, use {@link #create(int...)}.
 */
public abstract class TestMenu implements Menu {

    private SparseArray<TestMenuItem> items = new SparseArray<>();

    public static TestMenu create() {
        // We just blindly add all menu items here regardless of flags, the flag based menu
        // show/hide will be validated in the actual tests. The default visibility of the menu items
        // are not related to the XML file, the visibility is defined in the create() below.
        return create(
                R.id.dir_menu_share,
                R.id.dir_menu_open,
                R.id.dir_menu_open_with,
                R.id.dir_menu_cut_to_clipboard,
                R.id.dir_menu_copy_to_clipboard,
                R.id.dir_menu_compress,
                R.id.dir_menu_paste_from_clipboard,
                R.id.dir_menu_create_dir,
                R.id.dir_menu_select_all,
                R.id.dir_menu_deselect_all,
                R.id.dir_menu_rename,
                R.id.dir_menu_delete,
                R.id.dir_menu_view_in_owner,
                R.id.dir_menu_paste_into_folder,
                R.id.dir_menu_inspect,
                R.id.dir_menu_open_in_new_window,
                R.id.dir_menu_extract_here,
                R.id.dir_menu_browse,
                R.id.dir_menu_move_to_trash,
                R.id.dir_menu_restore_from_trash,
                R.id.root_menu_eject_root,
                R.id.root_menu_open_in_new_window,
                R.id.root_menu_paste_into_folder,
                R.id.root_menu_settings,
                R.id.root_menu_manage_device,
                R.id.root_menu_inspect,
                R.id.action_menu_open,
                R.id.action_menu_open_with,
                R.id.action_menu_share,
                R.id.action_menu_delete,
                R.id.action_menu_select,
                R.id.action_menu_select_all,
                R.id.action_menu_deselect_all,
                R.id.action_menu_copy_to,
                R.id.action_menu_extract_to,
                R.id.action_menu_move_to,
                R.id.action_menu_compress,
                R.id.action_menu_rename,
                R.id.action_menu_inspect,
                R.id.action_menu_view_in_owner,
                R.id.action_menu_sort,
                R.id.action_menu_extract_here,
                R.id.action_menu_browse,
                R.id.action_menu_move_to_trash,
                R.id.action_menu_restore_from_trash,
                R.id.action_menu_open_in_new_window,
                R.id.action_menu_cut_to_clipboard,
                R.id.action_menu_copy_to_clipboard,
                R.id.action_menu_paste_into_folder,
                R.id.option_menu_search,
                R.id.option_menu_debug,
                R.id.option_menu_new_window,
                R.id.option_menu_create_dir,
                R.id.option_menu_extract_all,
                R.id.option_menu_select_all,
                R.id.option_menu_settings,
                R.id.option_menu_manage_device,
                R.id.option_menu_inspect,
                R.id.option_menu_sort,
                R.id.option_menu_show_hidden_files,
                R.id.option_menu_launcher,
                R.id.option_menu_paste_from_clipboard,
                R.id.sub_menu_grid,
                R.id.sub_menu_list);
    }


    public static TestMenu create(int... ids) {
        final TestMenu menu = Mockito.mock(TestMenu.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        menu.items = new SparseArray<>();
        for (int id : ids) {
            TestMenuItem item = TestMenuItem.create(id);
            menu.addMenuItem(id, item);

            // Used by SearchViewManager
            if (id == R.id.option_menu_search) {
                item.setActionView(Mockito.mock(SearchView.class));
            }

            if (id == R.id.option_menu_extract_all
                    || id == R.id.dir_menu_extract_here
                    || id == R.id.dir_menu_browse
                    || id == R.id.action_menu_extract_here
                    || id == R.id.action_menu_browse
                    || id == R.id.action_menu_move_to_trash
                    || id == R.id.action_menu_restore_from_trash
                    || id == R.id.dir_menu_move_to_trash
                    || id == R.id.dir_menu_restore_from_trash) {
                item.setEnabled(false);
                item.setVisible(false);
            }

            if (isUseMaterial3FlagEnabled()) {
                if (id == R.id.action_menu_select
                        || id == R.id.action_menu_select_all
                        || id == R.id.action_menu_deselect_all
                        || id == R.id.action_menu_sort) {
                    item.setEnabled(false);
                    item.setVisible(false);
                }
            }
        }
        return menu;
    }

    public void addMenuItem(int id, TestMenuItem item) {
        items.put(id, item);
    }

    /** Creates and add the menu item with the given id. */
    public TestMenuItem createMenuItem(int id) {
        TestMenuItem item = TestMenuItem.create(id);
        addMenuItem(id, item);
        return item;
    }

    @Override
    public TestMenuItem findItem(int id) {
        return items.get(id);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public TestMenuItem getItem(int index) {
        return items.valueAt(index);
    }

    @Override
    public TestMenuItem add(int groupId, int itemId, int order, CharSequence title) {
        TestMenuItem item = TestMenuItem.create(groupId, itemId);
        addMenuItem(itemId, item.setTitle(title));
        return item;
    }

    @Override
    public void removeItem(int id) {
        items.remove(id);
    }
}
