package com.dev.anhnd.waveformviewbar

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

fun File.getMediaDuration(context: Context): Long {
    if (!exists()) return 0
    var duration = -1L
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.fromFile(this))
        duration =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()!!
        retriever.release()
    } catch (e: Exception) {
        e.printStackTrace()
        return duration
    }
    return duration
}
