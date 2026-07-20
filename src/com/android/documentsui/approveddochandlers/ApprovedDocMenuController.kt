/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.documentsui.approveddochandlers

import android.content.ComponentName
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.android.documentsui.Injector
import com.android.documentsui.MenuManager
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Controller to manage the populating and updating of the approved document handlers menu items.
 *
 * It uses a reactive approach to observe the current selection and updates the provided [Menu] with
 * the available handlers. It ensures that the UI is only updated with the most current data.
 */
class ApprovedDocMenuController(
    private val scope: CoroutineScope,
    private val viewModel: ApprovedDocHandlers,
    private val injector: Injector<*>,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    @Volatile private var menuRef: WeakReference<Menu>? = null
    private val selectionFlow =
        MutableSharedFlow<MenuManager.SelectionDetails>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val isListening = AtomicBoolean(false)

    companion object {
        // A unique ID used to group menu items created for approved document handlers.
        internal val HANDLER_GROUP_ID = View.generateViewId()

        // The maximum number of buttons to show in the action bar as agreed in the design.
        public const val NUMBER_OF_BUTTONS = 1
        // The maximum number of menu items to show in the menu as agreed in the design.
        public const val NUMBER_OF_MENU_ITEMS = 2
    }

    /**
     * Updates the provided menu with actions from approved document handlers. This method
     * dynamically adds or removes menu items based on the available approved document handlers and
     * the current selection.
     *
     * @param menu The menu to be updated.
     * @param selectionDetails Details about the current selection of documents.
     */
    @ExperimentalCoroutinesApi
    fun updateApprovedDocHandlerMenus(menu: Menu, selectionDetails: MenuManager.SelectionDetails) {
        menuRef = WeakReference(menu)
        if (isListening.compareAndSet(false, true)) {
            scope.launch(mainDispatcher) {
                combine(
                        selectionFlow.flatMapLatest { selection ->
                            viewModel.getApprovedDocHandlersFlow(selection)
                        },
                        viewModel.updateEvents.onStart { emit(Unit) },
                    ) { handlers, _ ->
                        handlers
                    }
                    .collectLatest { handlers ->
                        val currentMenu = menuRef?.get() ?: return@collectLatest
                        updateMenu(currentMenu, handlers)
                    }
            }
        }
        selectionFlow.tryEmit(selectionDetails)
    }

    private fun updateMenu(menu: Menu, approvedDocHandlersList: List<ApprovedDocHandler>) {
        // The handlers set to be buttons with an icon will attempt to be populated as action
        // buttons first, if more button handlers exist than available button slots or if the button
        // handlers do not have an icon, then the remaining button handlers will fall back to be
        // populated as menu items, however still be prioritized over the rest of the handlers.
        val (potentialButtons, others) =
            approvedDocHandlersList.partition { it.isButton && it.icon != null }
        val (prioritizedHandlers, regularHandlers) = others.partition { it.isButton }
        val actionButtonHandlers = potentialButtons.take(NUMBER_OF_BUTTONS)
        // Priority of menu items:
        // 1. Action buttons that don't fit in the button slots.
        // 2. Handlers registered as buttons but don't have an icon.
        // 3. Handlers registered as menu items.
        val menuHandlers =
            (potentialButtons.drop(NUMBER_OF_BUTTONS) + prioritizedHandlers + regularHandlers).take(
                NUMBER_OF_MENU_ITEMS
            )

        // The handlers to be shown in the menu.
        val targetHandlersMap =
            (actionButtonHandlers + menuHandlers).associateBy { it.componentName }
        // The handlers to be removed from the menu.
        val toRemove = mutableListOf<Int>()
        // The handlers that are already in the menu.
        val foundComponents = mutableSetOf<ComponentName>()

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.groupId != HANDLER_GROUP_ID) {
                continue
            }
            val component = item.intent?.component
            val handler = targetHandlersMap[component]

            if (component == null || handler == null) {
                toRemove.add(item.itemId)
                continue
            }

            val newIntent = injector.actions.createApprovedHandlerIntent(component)
            if (newIntent != null) {
                item.intent = newIntent
                item.isEnabled = handler.isEnabled
                // Update the label in case it has changed due to package changes.
                item.title = handler.label

                // If the handler is an action button handler, set the icon and show as action
                // if room. Otherwise make sure it's shown as a menu item.
                val isActionButton = handler in actionButtonHandlers
                item.icon = if (isActionButton) handler.icon else null
                item.setShowAsAction(
                    if (isActionButton) MenuItem.SHOW_AS_ACTION_IF_ROOM
                    else MenuItem.SHOW_AS_ACTION_NEVER
                )
                foundComponents.add(component)
            } else {
                // If the intent is null, the handler is not valid.
                // This can happen when selection is cleared before the menu is updated.
                toRemove.add(item.itemId)
            }
        }

        toRemove.forEach { menu.removeItem(it) }

        // Add new handlers.
        for (handlerComponent in targetHandlersMap.keys) {
            if (handlerComponent in foundComponents) {
                continue
            }
            val intent = injector.actions.createApprovedHandlerIntent(handlerComponent)
            if (intent == null) {
                // If the intent is null, the handler is not valid.
                // This can happen when selection is cleared before the menu is updated.
                continue
            }
            val handler = targetHandlersMap[handlerComponent]!!
            val item = menu.add(HANDLER_GROUP_ID, View.generateViewId(), Menu.NONE, handler.label)
            item.intent = intent
            item.isEnabled = handler.isEnabled

            val isActionButton = handler in actionButtonHandlers
            item.icon = if (isActionButton) handler.icon else null
            item.setShowAsAction(
                if (isActionButton) MenuItem.SHOW_AS_ACTION_IF_ROOM
                else MenuItem.SHOW_AS_ACTION_NEVER
            )
        }
    }
}
