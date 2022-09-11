package com.example.myaudioplayer.data

import android.graphics.Bitmap
import java.util.*

data class Audio(
    val data: String,
    val title: String,
    val album: String,
    val artist: String,
    val albumID: String,
){
    var image: Bitmap? = null
}

fun convertMillisToString(durationInMillis: Int): String? {
    val second = durationInMillis / 1000 % 60
    val minute = durationInMillis / (1000 * 60) % 60
    return java.lang.String.format(Locale.US, "%02d:%02d", minute, second)
}