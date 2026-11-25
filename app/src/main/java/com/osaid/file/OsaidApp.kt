package com.osaid.file

import android.app.Application
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class OsaidApp : Application() {
    companion object {
        @Volatile
        var isDownloaderReady: Boolean = false
            private set

        @Volatile
        var downloaderInitError: String? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Thread {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                Aria2c.getInstance().init(this)
                isDownloaderReady = true
                downloaderInitError = null
            } catch (e: Throwable) {
                Log.e("OsaidApp", "فشل تهيئة محرك التنزيل", e)
                downloaderInitError = e.message ?: "تعذر تهيئة yt-dlp / FFmpeg"
            }
        }.start()
    }
}
