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

package com.android.documentsui.demoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.documentsui.demoapp.components.ScreenTitle
import com.android.documentsui.demoapp.screens.DspScreen
import com.android.documentsui.demoapp.screens.EspScreen
import com.android.documentsui.demoapp.screens.GenericScreen
import com.android.documentsui.demoapp.screens.MdpScreen

private val TAB_TITLES =
    listOf(R.string.tab_general, R.string.tab_esp, R.string.tab_dsp, R.string.tab_mdp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DemoAppContent()
                }
            }
        }
    }
}

@Composable
fun DemoAppContent() {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val savableStateHolder = rememberSaveableStateHolder()

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenTitle()

        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            TAB_TITLES.forEachIndexed { index, titleResId ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(text = stringResource(titleResId)) },
                )
            }
        }

        savableStateHolder.SaveableStateProvider(selectedTabIndex) {
            when (selectedTabIndex) {
                0 -> GenericScreen(Modifier.weight(1f))
                1 -> EspScreen(Modifier.weight(1f))
                2 -> DspScreen(Modifier.weight(1f))
                3 -> MdpScreen(Modifier.weight(1f))
            }
        }
    }
}
