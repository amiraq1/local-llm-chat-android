package com.example.localllm.data.tools

import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface ClipboardReader {
    /** Returns the current clipboard text, or null if empty / unavailable. */
    fun readText(): String?
}

/** Production implementation — reads from [ClipboardManager]. */
class SystemClipboardReader @Inject constructor(
    @ApplicationContext private val context: Context
) : ClipboardReader {
    override fun readText(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.trim()
            ?.ifEmpty { null }
    }
}
