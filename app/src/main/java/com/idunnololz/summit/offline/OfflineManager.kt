package com.idunnololz.summit.offline

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.view.View
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.idunnololz.summit.R
import com.idunnololz.summit.reddit.*
import com.idunnololz.summit.reddit.ext.ListingItemType
import com.idunnololz.summit.reddit.ext.getPreviewUrl
import com.idunnololz.summit.reddit.ext.getType
import com.idunnololz.summit.reddit_objects.ListingItem
import com.idunnololz.summit.reddit_objects.ListingItemObject
import com.idunnololz.summit.util.*
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.*
import okhttp3.Request
import okio.BufferedSink
import okio.buffer
import okio.sink
import org.apache.commons.io.FileUtils
import java.io.File
import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap


@UnstableApi class OfflineManager(
    private val context: Context
) {

    companion object {

        private val TAG = OfflineManager::class.java.simpleName

        @SuppressLint("StaticFieldLeak") // application context
        lateinit var instance: OfflineManager
            private set

        fun initialize(context: Context) {
            instance = OfflineManager(context.applicationContext)
        }
    }

    val imagesDir = File(context.filesDir, "imgs")
    val videosDir = File(context.filesDir, "videos")
    val videoCacheDir = File(context.cacheDir, "videos")

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val downloadTasks = LinkedHashMap<String, DownloadTask>()
    private val jobMap = HashMap<String, MutableList<Job>>()
    private var currentTask: DownloadTask? = null

    private val imageSizeHints = HashMap<String, Size>()
    private val maxImageSizeHint = HashMap<File, Size>()

    private var offlineDownloadProgressListeners = ArrayList<OfflineDownloadProgressListener>()

    private val exoDatabaseProvider by lazy {
        ExoDatabaseProvider(context)
    }
    val exoCache by lazy {
        SimpleCache(videosDir, NoOpCacheEvictor(), exoDatabaseProvider)
    }

    private data class DownloadTask (
        val url: String,
        val listeners: LinkedList<TaskListener> = LinkedList(),
        val errorListeners: LinkedList<TaskFailedListener> = LinkedList()
    )

    class Registration (
        private val key: String,
        private val listener: TaskListener,
        private val errorListener: TaskFailedListener?
    ) {
        fun cancel(offlineManager: OfflineManager) {
            offlineManager.cancelFetch(key, listener)
        }
    }

    fun fetchImage(rootView: View, url: String?, listener: TaskListener) {
        fetchImageWithError(rootView, url, listener, null)
    }

    fun fetchImageWithError(rootView: View, url: String?, listener: TaskListener, errorListener: TaskFailedListener?) {
        url ?: return
        val registrations: MutableList<Registration> = (rootView.getTag(
            R.id.offline_manager_registrations
        ) as? MutableList<Registration>) ?: arrayListOf<Registration>().also {
            rootView.setTag(R.id.offline_manager_registrations, it)
        }
        registrations.add(
            fetchImage(url, listener, errorListener)
        )
    }

    fun cancelFetch(url: String, listener: TaskListener, errorListener: TaskFailedListener? = null) {
        synchronized(this) {
            val task = downloadTasks[url]

            if (task != null) {
                task.listeners.remove(listener)
                task.errorListeners.remove(errorListener)

                if (task.listeners.isEmpty() && task.errorListeners.isEmpty()) {
                    jobMap[url]?.forEach {
                        it.cancel()
                    }
                    jobMap.remove(url)
                    downloadTasks.remove(url)
                }
            }
        }
    }

    fun cancelFetch(rootView: View) {
        (rootView.getTag(
            R.id.offline_manager_registrations
        ) as? MutableList<Registration>)?.forEach {
            it.cancel(this)
        }
    }

    fun deleteOfflineImages() {
        FileUtils.deleteDirectory(imagesDir)
    }

    fun setImageSizeHint(url: String, w: Int, h: Int) {
        imageSizeHints.getOrPut(url) { Size() }.apply {
            width = w
            height = h
        }
    }

    fun hasImageSizeHint(url: String): Boolean = imageSizeHints.containsKey(url)

    fun getImageSizeHint(url: String, size: Size): Size = size.apply {
        val size = imageSizeHints.get(url)
        if (size != null) {
            width = size.width
            height = size.height
        } else {
            width = 0
            height = 0
        }
    }

    fun setMaxImageSizeHint(file: File, w: Int, h: Int) {
        maxImageSizeHint.getOrPut(file) { Size() }.apply {
            width = w
            height = h
        }
    }

    fun hasMaxImageSizeHint(file: File): Boolean = maxImageSizeHint.containsKey(file)

    fun getMaxImageSizeHint(file: File, size: Size): Size = size.apply {
        val size = maxImageSizeHint.get(file)
        if (size != null) {
            width = size.width
            height = size.height
        } else {
            width = 0
            height = 0
        }
    }

    private fun getFilenameForUrl(url: String): String {
        val extension = if (url.lastIndexOf(".") != -1) {
            url.substring(url.lastIndexOf("."))
        } else {
            ""
        }
        return Utils.hashSha256(url) + extension
    }

    private fun fetchGeneric(
        url: String,
        destDir: File,
        force: Boolean = false,
        saveToFileFn: (File) -> Throwable?,
        listener: TaskListener,
        errorListener: TaskFailedListener?
    ): Registration {
        check(!destDir.exists() || destDir.isDirectory)

        val job = coroutineScope.launch {
            val file = try {
                downloadFileIfNeeded(url, destDir, force, saveToFileFn, listener, errorListener)
            } catch (e: Exception) {
                Log.e(TAG, "", e)

                // Delete downloaded file if there is an error in case the file is corrupt due to
                // network issue, etc.
                val downloadedFile = File(destDir, getFilenameForUrl(url))
                downloadedFile.delete()

                synchronized(this@OfflineManager) {
                    currentTask = null

                    downloadTasks.remove(url)?.let {
                        it.errorListeners.forEach {
                            launch(Dispatchers.Main) {
                                it(e)
                            }
                        }
                    }
                }
                null
            }

            file ?: return@launch

            synchronized(this@OfflineManager) {
                currentTask = null

                downloadTasks.remove(url)?.listeners?.forEach {
                    launch(Dispatchers.Main) {
                        it(file)
                    }
                }
            }
        }

        jobMap.getOrPut(url) { arrayListOf() }.add(job)

        return Registration(url, listener, errorListener)
    }

    private fun downloadFileIfNeeded(
        url: String,
        destDir: File,
        force: Boolean,
        saveToFileFn: (File) -> Throwable?,
        listener: (File) -> Unit,
        errorListener: ((e: Throwable) -> Unit)?
    ): File? {
        synchronized(this@OfflineManager) {
            val task = downloadTasks[url]

            // This task is already scheduled... abort
            if (task != null) {
                task.listeners += listener
                if (errorListener != null) {
                    task.errorListeners += errorListener
                }
                return null
            }

            downloadTasks[url] = DownloadTask(url).apply {
                listeners += listener
                if (errorListener != null) {
                    errorListeners += errorListener
                }
            }

            currentTask = task
        }

        val downloadedFile = File(destDir, getFilenameForUrl(url))
        Log.d(TAG, "dl file: " + downloadedFile.absolutePath)
        1
        if (!force && downloadedFile.exists()) {
            return downloadedFile
        }

        downloadedFile.parentFile?.mkdirs()

        val error = saveToFileFn(downloadedFile)
        if (error != null) {
            throw error
        } else {
            return downloadedFile
        }
    }

    private fun fetchImage(
        url: String,
        listener: TaskListener,
        errorListener: TaskFailedListener? = null
    ): Registration = fetchGeneric(url, imagesDir, false, { destFile ->
        val req = Request.Builder()
            .url(url)
            .build()

        val response = Client.get().newCall(req).execute()
        val sink: BufferedSink = destFile.sink().buffer()
        response.body?.source()?.let {
            sink.writeAll(it)
        }
        sink.close()

        Log.d(TAG, "Downloaded image from $url")

        null
    }, listener, errorListener)

    fun calculateImageMaxSizeIfNeeded(file: File) {
        if (hasMaxImageSizeHint(file)) {
            return
        }

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true

        //Returns null, sizes are in the options variable
        BitmapFactory.decodeFile(file.path, options)
        val width = options.outWidth
        val height = options.outHeight

        if (width > Utils.getScreenWidth(context)
            || height > Utils.getScreenHeight(context)) {
            val size =
                RedditUtils.calculateMaxImagePreviewSize(
                    context,
                    width,
                    height
                )
            setMaxImageSizeHint(file, size.width, size.height)
        } else {
            setMaxImageSizeHint(file, -1, -1)
        }
    }

    fun doOfflineBlocking(config: OfflineTaskConfig) {
        postProgressUpdate("Downloading pages...", 0.0)

        val parts = 3.0
        var progress = 0.0

        val redditPageLoader = RedditPageLoader()

        val seenListingObjects = hashSetOf<String>()
        val listingItems = mutableListOf<ListingItem>()
        while (true) {
            val countDownLatch = CountDownLatch(1)
            redditPageLoader.onListingPageLoadedListener = { url, pageIndex, adapter ->
                if (adapter.isSuccess()) {
                    val o = adapter.get()
                    val children =
                        o.data?.getChildrenAs<ListingItemObject>()?.mapNotNull { it.data }
                            ?: listOf()

                    for (c in children) {
                        val notSeen = seenListingObjects.add(c.name)
                        if (notSeen) {
                            listingItems.add(c)
                        }

                        if (!config.roundPostsToNearestPage && seenListingObjects.size >= config.minPosts) {
                            break
                        }
                    }
                } else {
                    Log.e(TAG, "Adapter failed due to ${adapter.error}")
                    throw RuntimeException("Oh noz!")
                }
                countDownLatch.countDown()
            }
            redditPageLoader.fetchCurrentPage(force = true)

            countDownLatch.await()

            if (seenListingObjects.size >= config.minPosts) {
                break
            }

            redditPageLoader.moveToNextPage()
        }

        progress += 1.0 / parts
        postProgressUpdate("Downloading each post...", progress)

        // Download each post...
        run {
            val countDownLatch = CountDownLatch(listingItems.size)
            redditPageLoader.onPostLoadedListener = { adapter ->
                if (adapter.isSuccess()) {
                    //val postData = adapter.get()
                } else {
                    Log.e(TAG, "Adapter failed due to ${adapter.error}")
                    throw RuntimeException("Oh noz!")
                }
                countDownLatch.countDown()

                postProgressUpdate(
                    "Downloading post ${countDownLatch.count}/${listingItems.size}...",
                    progress + (1.0 - countDownLatch.count.toDouble() / listingItems.size) / parts)
            }
            redditPageLoader.fetchPostsFromListingItems(listingItems)

            countDownLatch.await()
        }

        progress += 1.0 / parts
        postProgressUpdate("Downloading post content...", progress)

        // Download post content
        run {
            val totalCount = listingItems.size * 3
            val countDownLatch = CountDownLatch(totalCount)
            val registrations = mutableListOf<Registration>()

            fun doneOne() {
                countDownLatch.countDown()

                postProgressUpdate(
                    "Downloading content ${countDownLatch.count}/${listingItems.size}...",
                    progress + (1.0 - countDownLatch.count.toDouble() / totalCount) / parts)
            }

            for (listingItem in listingItems) {
                val type = listingItem.getType()

                if (type != ListingItemType.DEFAULT_SELF) {
                    val thumbUrl = listingItem.getThumbnailUrl(false)
                    val revealedUrl = listingItem.getThumbnailUrl(true)
                    if (revealedUrl != thumbUrl && revealedUrl != null) {
                        registrations += fetchImage(revealedUrl, {
                            doneOne()
                        }, {
                            doneOne()
                            throw RuntimeException("Oh noz!")
                        })
                    } else {
                        doneOne()
                    }
                    if (thumbUrl != null) {
                        registrations += fetchImage(thumbUrl, {
                            doneOne()
                        }, {
                            doneOne()
                            throw RuntimeException("Oh noz!")
                        })
                    } else {
                        doneOne()
                    }
                } else {
                    doneOne()
                    doneOne()
                }

                when (type) {
                    ListingItemType.DEFAULT_SELF -> {
                        doneOne()
                    }
                    ListingItemType.REDDIT_IMAGE -> {
                        registrations += fetchImage(listingItem.url, b@{
                            doneOne()
                        }, {
                            doneOne()
                            throw RuntimeException("Oh noz!")
                        })
                    }
                    ListingItemType.REDDIT_VIDEO -> {
                        doneOne()
                    }
                    ListingItemType.UNKNOWN -> {
                        val previewUrl = listingItem.getPreviewUrl()

                        if (previewUrl != null) {
                            registrations += fetchImage(previewUrl, b@{
                                doneOne()
                            }, {
                                doneOne()
                                throw RuntimeException("Oh noz!")
                            })
                        } else {
                            doneOne()
                        }
                    }
                    ListingItemType.REDDIT_GALLERY -> {}
                }
            }

            countDownLatch.await()
        }

        postProgressUpdate("Done!", 1.0)

        PreferenceUtil.preferences.edit()
            .putLong(PreferenceUtil.KEY_LAST_SUCCESSFUL_OFFLINE_DOWNLOAD, System.currentTimeMillis())
            .apply()
    }

    fun getLastSuccessfulOfflineDownloadTime(): Long =
        PreferenceUtil.preferences.getLong(PreferenceUtil.KEY_LAST_SUCCESSFUL_OFFLINE_DOWNLOAD, -1)

    fun postProgressUpdate(message: String, progress: Double) {
        val copy = offlineDownloadProgressListeners.toList()
        AndroidSchedulers.mainThread().scheduleDirect {
            for (l in copy) {
                l(message, progress)
            }
        }
    }

    fun addOfflineDownloadProgressListener(listener: OfflineDownloadProgressListener): OfflineDownloadProgressListener {
        offlineDownloadProgressListeners.add(listener)
        return listener
    }

    fun removeOfflineDownloadProgressListener(listener: OfflineDownloadProgressListener?) {
        offlineDownloadProgressListeners.remove(listener)
    }

    fun clearOfflineData() {
        Utils.deleteDir(imagesDir)
        Utils.deleteDir(videosDir)
        Utils.deleteDir(videoCacheDir)
        imagesDir.mkdirs()
        videosDir.mkdirs()
        videoCacheDir.mkdirs()
    }
}