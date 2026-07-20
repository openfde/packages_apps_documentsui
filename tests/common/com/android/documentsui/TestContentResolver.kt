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
package com.android.documentsui

import android.content.ContentResolver
import android.content.Context
import android.content.IContentProvider
import android.content.UriPermission

/**
 * A simple, test implementation of a content resolver that allows us to manipulate its responses
 * during test.
 */
class TestContentResolver(val contentProvider: IContentProvider, context: Context) :
    ContentResolver(context) {

    /** Returns content provider specified at the construction time. */
    override fun acquireProvider(c: Context?, name: String?) = contentProvider

    /** Returns content provider specified at the construction time. */
    override fun acquireUnstableProvider(c: Context?, name: String?) = contentProvider

    override fun releaseProvider(icp: IContentProvider?) = true

    override fun releaseUnstableProvider(icp: IContentProvider?) = true

    override fun unstableProviderDied(icp: IContentProvider?) {}

    override fun getPersistedUriPermissions() = listOf<UriPermission>()
}
