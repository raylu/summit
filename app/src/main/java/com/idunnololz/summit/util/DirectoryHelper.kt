package com.idunnololz.summit.util

import android.content.Context
import android.util.Log
import com.idunnololz.summit.cache.MoshiDiskCache
import com.idunnololz.summit.fileprovider.FileProviderHelper
import com.idunnololz.summit.lemmy.community.LoadedPostsData
import com.squareup.moshi.JsonClass
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectoryHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "DirectoryHelper"
    }

    val cacheDir = context.cacheDir
    val okHttpCacheDir = File(context.cacheDir, "okhttp_cache")
    val downloadInProgressDir = File(context.filesDir, "dl")
    val imagesDir = File(context.filesDir, "imgs")
    val videosDir = File(context.filesDir, "videos")
    val videoCacheDir = File(context.cacheDir, "videos")
    val tabsDir = File(context.cacheDir, "tabs")
    val miscDir = File(context.cacheDir, "misc")
    val settingBackupsDir = File(context.filesDir, "sb")
    val saveForLaterDir = File(context.filesDir, "sfl")

    val tabsDiskCache = MoshiDiskCache
        .create(moshi, tabsDir, 1, 10L * 1024L * 1024L /* 10MB */)

    fun cleanup() {
        var purgedFiles = 0
        val thresholdTime = System.currentTimeMillis() - Duration.ofDays(1).toMillis()

        fun File.cleanupDir() {
            this.listFiles()?.forEach {
                if (it.lastModified() < thresholdTime) {
                    if (it.isDirectory) {
                        it.deleteRecursively()
                    } else {
                        it.delete()
                    }
                    purgedFiles++
                }
            }
        }

        imagesDir.cleanupDir()
        videosDir.cleanupDir()
        FileProviderHelper(context).fileProviderDir.cleanupDir()
        miscDir.cleanupDir()

        Log.d(TAG, "Deleted $purgedFiles files.")
    }

    fun deleteOfflineImages() {
        imagesDir.deleteRecursively()
    }

    @JsonClass(generateAdapter = true)
    data class PostListEngineCacheInfo(
        val totalPages: Int,
    )

    fun addPage(key: String?, secondaryKey: String?, data: LoadedPostsData, totalPages: Int) {
        key ?: return

        tabsDir.mkdirs()

        val keyPrefix = "$key|$secondaryKey"
        val infoKey = "$keyPrefix|info"

        tabsDiskCache.cacheObject(infoKey, PostListEngineCacheInfo(totalPages))

        val pageKey = "$keyPrefix|${data.pageIndex}"

        tabsDiskCache.cacheObject(pageKey, data)

        tabsDiskCache.printDebugInfo()
    }

    fun clearPages(key: String?, secondaryKey: String?) {
        key ?: return

        tabsDir.mkdirs()

        val keyPrefix = "$key|$secondaryKey"
        val infoKey = "$keyPrefix|info"

        tabsDiskCache.evict(infoKey)
    }

    fun getPages(key: String?, secondaryKey: String?): List<LoadedPostsData>? {
        key ?: return null

        tabsDir.mkdirs()

        val keyPrefix = "$key|$secondaryKey"
        val infoKey = "$keyPrefix|info"

        val pageCacheInfo = tabsDiskCache.getCachedObject<PostListEngineCacheInfo>(infoKey)

        if (pageCacheInfo == null || pageCacheInfo.totalPages == 0) {
            return null
        }

        val pages = mutableListOf<LoadedPostsData>()
        for (i in 0 until pageCacheInfo.totalPages) {
            val pageKey = "$keyPrefix|$i"
            val postData = tabsDiskCache.getCachedObject<LoadedPostsData>(pageKey)
                ?: return null
            pages.add(postData)
        }

        return pages
    }
}
