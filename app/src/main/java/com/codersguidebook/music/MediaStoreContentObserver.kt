package com.codersguidebook.music

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler

class MediaStoreContentObserver(handler: Handler, private val activity: MainActivity): ContentObserver(handler) {

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)

        uri?.let {
            activity.handleChangeToContentUri(uri)
        }
    }
}