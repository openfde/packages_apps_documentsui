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
package com.android.documentsui.base

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.provider.DocumentsContract
import com.android.documentsui.IconUtils
import com.android.documentsui.R
import com.android.documentsui.base.Shared.compareToIgnoreCaseNullable
import com.android.documentsui.roots.ShortcutResourceValues
import com.android.documentsui.util.Material3Config.Companion.getRes
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Objects

class ShortcutInfo() : SidebarEntryItemInfo, Durable, Parcelable {
    constructor(
        root: RootInfo,
        parentDirDocumentId: String?,
        folderTitle: String?,
        localizedDisplayTitle: String?,
        icon: Int,
    ) : this() {
        this.root = root
        this.parentDirDocumentId = parentDirDocumentId
        this.folderTitle = folderTitle
        this.localizedDisplayTitle = localizedDisplayTitle
        this.icon = icon
    }

    override var root: RootInfo = RootInfo()
    var parentDirDocumentId: String? = null
    var folderTitle: String? = null
    var localizedDisplayTitle: String? = null
    override var title: String?
        get() = localizedDisplayTitle
        set(title) {
            localizedDisplayTitle = title
        }

    var icon: Int = ShortcutResourceValues.INVALID_ICON_REF
    override var documentId: String? = null
    override val uri: Uri?
        get() = DocumentsContract.buildDocumentUri(root.authority, documentId)

    @SidebarEntryItemInfo.SidebarEntryItemType
    override val derivedType: Int
        get() {
            if (
                folderTitle.equals(Providers.HOME_SCREEN_SHORTCUT_TITLE) &&
                    parentDirDocumentId.equals("primary:") &&
                    root.authority.equals(Providers.AUTHORITY_STORAGE)
            ) {
                return SidebarEntryItemInfo.TYPE_HOME_SCREEN
            } else if (
                folderTitle.equals(Providers.DOWNLOAD_SHORTCUT_TITLE) &&
                    parentDirDocumentId.equals("primary:") &&
                    root.authority.equals(Providers.AUTHORITY_STORAGE)
            ) {
                return SidebarEntryItemInfo.TYPE_DOWNLOADS
            } else {
                return SidebarEntryItemInfo.TYPE_SHORTCUT_OTHER
            }
        }

    override fun loadDrawerIcon(context: Context, maybeShowBadge: Boolean): Drawable? {
        if (icon == RootInfo.LOAD_FROM_CONTENT_RESOLVER) {
            return IconUtils.applyTintColor(
                context,
                loadMimeTypeIcon(context),
                getRes(R.color.item_root_icon),
            )
        } else if (icon != 0) {
            return IconUtils.applyTintColor(context, icon, getRes(R.color.item_root_icon))
        } else {
            return IconUtils.loadPackageIcon(
                context,
                root.userId,
                root.authority,
                root.icon,
                maybeShowBadge,
            )
        }
    }

    private fun loadMimeTypeIcon(context: Context): Drawable? {
        return when (root.derivedType) {
            SidebarEntryItemInfo.TYPE_IMAGES ->
                IconUtils.loadMimeIcon(context, MimeTypes.IMAGE_MIME)
            SidebarEntryItemInfo.TYPE_AUDIO -> IconUtils.loadMimeIcon(context, MimeTypes.AUDIO_MIME)
            SidebarEntryItemInfo.TYPE_VIDEO -> IconUtils.loadMimeIcon(context, MimeTypes.VIDEO_MIME)
            else -> IconUtils.loadMimeIcon(context, MimeTypes.GENERIC_TYPE)
        }
    }

    /**
     * Gets the URI of the parent directory. The parent directory may not be a real root directory.
     */
    fun getParentDirectoryUri(): Uri {
        return DocumentsContract.buildDocumentUri(root.authority, parentDirDocumentId)
    }

    override fun supportsCreate(): Boolean {
        return root.supportsCreate()
    }

    override fun supportsEject(): Boolean {
        return false
    }

    override fun isEjecting(): Boolean {
        return false
    }

    override fun supportsInspect(): Boolean {
        return documentId != null
    }

    override fun hasSettings(): Boolean {
        return root.hasSettings()
    }

    override fun isValidDropTarget(): Boolean {
        return supportsCreate() || root.isTrash()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }

        if (other is ShortcutInfo) {
            val o = other

            return Objects.equals(folderTitle, o.folderTitle) &&
                Objects.equals(localizedDisplayTitle, o.localizedDisplayTitle) &&
                Objects.equals(documentId, o.documentId) &&
                Objects.equals(root, o.root) &&
                Objects.equals(parentDirDocumentId, o.parentDirDocumentId)
        }
        return false
    }

    override fun toString(): String {
        return ("ShortcutInfo{" +
            "title=" +
            title +
            ", folderTitle=" +
            folderTitle +
            ", documentId=" +
            documentId +
            ", root=" +
            root +
            "} @ " +
            uri)
    }

    override fun compareTo(other: SidebarEntryItemInfo): Int {
        val score: Int = derivedType - other.derivedType
        if (score != 0) {
            return score
        }
        // If comparing a shortcut info with a root info, the score should have already been
        // returned since the derived types would be different.
        val o: ShortcutInfo = other as ShortcutInfo
        // Compare against the folder title rather than the localized title.
        return compareToIgnoreCaseNullable(folderTitle, o.folderTitle)
    }

    override fun reset() {
        localizedDisplayTitle = null
        folderTitle = null
        documentId = null
        root = RootInfo()
    }

    override fun read(input: DataInputStream?) {
        icon = input!!.readInt()
        folderTitle = DurableUtils.readNullableString(input)
        localizedDisplayTitle = DurableUtils.readNullableString(input)
        root = RootInfo()
        root.read(input)
        parentDirDocumentId = DurableUtils.readNullableString(input)
        documentId = DurableUtils.readNullableString(input)
    }

    override fun write(out: DataOutputStream?) {
        out!!.writeInt(icon)
        DurableUtils.writeNullableString(out, folderTitle)
        DurableUtils.writeNullableString(out, localizedDisplayTitle)
        root.write(out)
        DurableUtils.writeNullableString(out, parentDirDocumentId)
        DurableUtils.writeNullableString(out, documentId)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        DurableUtils.writeToParcel(dest, this)
    }

    companion object CREATOR : Parcelable.Creator<ShortcutInfo> {
        override fun createFromParcel(input: Parcel): ShortcutInfo {
            val shortcut = ShortcutInfo()
            DurableUtils.readFromParcel(input, shortcut)
            return shortcut
        }

        override fun newArray(size: Int): Array<ShortcutInfo?> {
            return kotlin.arrayOfNulls<ShortcutInfo?>(size)
        }
    }

    /** Returns a new shortcut info copied from the existing ShortcutInfo instance. */
    fun copyShortcutInfo(): ShortcutInfo {
        val newShortcut = ShortcutInfo()
        newShortcut.icon = this.icon
        newShortcut.folderTitle = this.folderTitle
        newShortcut.localizedDisplayTitle = this.localizedDisplayTitle
        newShortcut.root = RootInfo.copyRootInfo(this.root)
        newShortcut.parentDirDocumentId = this.parentDirDocumentId
        newShortcut.documentId = this.documentId
        return newShortcut
    }
}
