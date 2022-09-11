package com.example.myaudioplayer.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

const val STORAGE = " audioplayer.STORAGE"

class AudioStorage(context: Context) {
    private val sharedPreferences: SharedPreferences

    init {
        sharedPreferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
    }

    fun storeAudio(audioArrayList: ArrayList<Audio>) {
        sharedPreferences.edit().apply {
            val gson = Gson()
            putString("audioArrayList", gson.toJson(audioArrayList))
        }.apply()
    }

    fun loadAudio(): ArrayList<Audio> {
        val gson = Gson()

        val json = sharedPreferences.getString("audioArrayList", "")
        val type = object : TypeToken<ArrayList<Audio>>() {}.type

        return gson.fromJson(json, type)
    }

    fun storeAudioIndex(index: Int) {
        sharedPreferences.edit().apply {
            putInt("audioIndex", index)
        }.apply()
    }

    fun loadAudioIndex(): Int = sharedPreferences.getInt("audioIndex", -1)

    fun clearCachedAudioPlaylist() {
        sharedPreferences.edit().apply {
            clear()
        }.apply()
    }

}