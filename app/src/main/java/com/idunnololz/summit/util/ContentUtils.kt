package com.idunnololz.summit.util

object ContentUtils {
    fun isUrlImage(url: String) =
        url.endsWith(".jpg") ||
            url.endsWith(".jpeg") ||
            url.endsWith(".png") ||
            url.endsWith(".webp") ||
            url.endsWith(".gif") ||
            url.endsWith(".svg")

    fun isUrlVideo(url: String) =
        url.endsWith(".mp4") ||
            url.endsWith(".webm")

    fun isUrlMp4(url: String) =
        url.endsWith(".mp4")

    fun isUrlHls(url: String) =
        url.endsWith(".m3u8")
}
