package com.idunnololz.summit.preview

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.transition.Fade
import android.transition.Transition
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.navArgs
import coil3.Image
import coil3.asDrawable
import coil3.imageLoader
import coil3.load
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Dimension
import coil3.target.Target
import com.google.android.material.snackbar.Snackbar
import com.idunnololz.summit.MainApplication
import com.idunnololz.summit.R
import com.idunnololz.summit.databinding.FragmentImageViewerBinding
import com.idunnololz.summit.error.ErrorDialogFragment
import com.idunnololz.summit.image.ImageInfoDialogFragment
import com.idunnololz.summit.lemmy.utils.actions.MoreActionsHelper
import com.idunnololz.summit.lemmy.utils.createImageOrLinkActionsHandler
import com.idunnololz.summit.lemmy.utils.showAdvancedLinkOptions
import com.idunnololz.summit.lemmy.utils.showShareSheetForImage
import com.idunnololz.summit.offline.OfflineManager
import com.idunnololz.summit.preferences.Preferences
import com.idunnololz.summit.util.BaseActivity
import com.idunnololz.summit.util.BottomMenu
import com.idunnololz.summit.util.BottomMenuContainer
import com.idunnololz.summit.util.ContentUtils
import com.idunnololz.summit.util.FileDownloadHelper
import com.idunnololz.summit.util.InsetsHelper
import com.idunnololz.summit.util.InsetsProvider
import com.idunnololz.summit.util.LinkFetcher
import com.idunnololz.summit.util.SharedElementNames
import com.idunnololz.summit.util.SharedElementTransition
import com.idunnololz.summit.util.Size
import com.idunnololz.summit.util.StatefulData
import com.idunnololz.summit.util.Utils
import com.idunnololz.summit.util.crashLogger.crashLogger
import com.idunnololz.summit.util.ext.showAboveCutout
import com.idunnololz.summit.util.imgur.ImgurPageParser
import com.idunnololz.summit.util.insetViewExceptTopAutomaticallyByPadding
import com.idunnololz.summit.view.GalleryImageView
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ImageViewerActivity :
    BaseActivity(), BottomMenuContainer, InsetsProvider by InsetsHelper(consumeInsets = false) {

    companion object {

        @Suppress("unused")
        private const val TAG = "ImageViewerActivity"

        private const val EXIT_OFFSET_DP = 60f

        private const val MAX_BITMAP_SIZE = 100 * 1024 * 1024 // 100 MB

        const val ErrorCustomDownloadLocation = 1234
    }

    private val args: ImageViewerActivityArgs by navArgs()

    private val viewModel: ImageViewerViewModel by viewModels()

    private var currentBottomMenu: BottomMenu? = null

    private var showingUi = true

    private lateinit var fileName: String
    private var mimeType: String? = null

    private lateinit var binding: FragmentImageViewerBinding

    @Inject
    lateinit var moreActionsHelper: MoreActionsHelper

    @Inject
    lateinit var offlineManager: OfflineManager

    @Inject
    lateinit var preferences: Preferences

    @Inject
    lateinit var linkFetcher: LinkFetcher

    override val context: Context
        get() = this
    override val mainApplication: MainApplication
        get() = application as MainApplication
    override val activity: BaseActivity
        get() = this

    private val urlAlt: String?
        get() {
            val urlAlt = args.urlAlt
                ?: return null

            return if (ContentUtils.isUrlImage(urlAlt)) {
                urlAlt
            } else {
                null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge(
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        postponeEnterTransition()

        super.onCreate(savedInstanceState)
        binding = FragmentImageViewerBinding.inflate(LayoutInflater.from(this))

        setContentView(binding.root)
        registerInsetsHandler(binding.root)

        showAboveCutout()

        if (urlAlt != null) {
            binding.hdButton.visibility = View.VISIBLE

            binding.hdButton.setOnClickListener {
                toggleImageQuality()
            }
        } else {
            binding.hdButton.visibility = View.GONE
        }

        viewModel.url = args.url

        onViewCreated()

        setSupportActionBar(binding.toolbar)

        val actionBar = supportActionBar
        actionBar?.setDisplayShowHomeEnabled(true)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.title = ""

        binding.dummyAppBar.transitionName = SharedElementNames.APP_BAR
        binding.bottomNavigationView.transitionName = SharedElementNames.NAV_BAR

        onInsetsChanged = { insets ->
            binding.toolbar.layoutParams = binding.toolbar.layoutParams.apply {
                (this as MarginLayoutParams).topMargin = insets.topInset
            }
        }

        window.enterTransition = Fade(Fade.IN).apply {
            duration = 200
            excludeTarget(R.id.dummy_image_view, true)
            excludeTarget(R.id.image_view, true)
        }
        window.exitTransition = Fade(Fade.OUT).apply {
            duration = 200
            excludeTarget(R.id.dummy_image_view, true)
            excludeTarget(R.id.image_view, true)
        }
        window.returnTransition = Fade(Fade.OUT).apply {
            duration = 200
            excludeTarget(R.id.dummy_image_view, true)
            excludeTarget(R.id.image_view, true)
        }

        window.sharedElementEnterTransition = SharedElementTransition()
        window.sharedElementReturnTransition = SharedElementTransition()
//        window.sharedElementsUseOverlay = false

        if (savedInstanceState != null) {
            onSharedElementEnterTransitionEnd()
        } else {
            binding.dummyImageView.visibility = View.VISIBLE
            binding.imageView.visibility = View.INVISIBLE
        }
        window.sharedElementEnterTransition.addListener(
            object : Transition.TransitionListener {
                override fun onTransitionStart(p0: Transition?) {
                }

                override fun onTransitionEnd(p0: Transition?) {
                    onSharedElementEnterTransitionEnd()

                    binding.imageView.postDelayed({
                        toggleImageQuality(useHd = true)
                    }, 50)
                }

                override fun onTransitionCancel(p0: Transition?) {
                }

                override fun onTransitionPause(p0: Transition?) {
                }

                override fun onTransitionResume(p0: Transition?) {
                }
            },
        )
        window.sharedElementExitTransition.addListener(
            object : Transition.TransitionListener {
                override fun onTransitionStart(p0: Transition?) {
                    binding.appBar.animate()
                        .translationY(-200f)
                }

                override fun onTransitionEnd(p0: Transition?) {}

                override fun onTransitionCancel(p0: Transition?) {}

                override fun onTransitionPause(p0: Transition?) {}

                override fun onTransitionResume(p0: Transition?) {}
            },
        )

        if (args.transitionName == null) {
            binding.dummyImageView.post {
                binding.dummyImageView.transitionName = null
                binding.imageView.transitionName = args.transitionName

                binding.dummyImageView.visibility = View.GONE
                binding.imageView.visibility = View.VISIBLE
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            supportFinishAfterTransition()
        }

        moreActionsHelper.downloadAndShareFile.observe(this) {
            when (it) {
                is StatefulData.Error -> {
                    ErrorDialogFragment.show(
                        getString(R.string.error_unable_to_share_file),
                        it.error,
                        supportFragmentManager,
                    )
                }
                is StatefulData.Loading -> {}
                is StatefulData.NotStarted -> {}
                is StatefulData.Success -> {
                    showShareSheetForImage(this, it.data)
                }
            }
        }
    }

    private fun toggleImageQuality(useHd: Boolean = false) {
        val hasAltUrl = !urlAlt.isNullOrBlank()
        if (hasAltUrl && (viewModel.url == args.url || useHd)) {
            viewModel.url = urlAlt
            binding.hdButton.setImageResource(R.drawable.baseline_hd_24)
        } else {
            viewModel.url = args.url
            binding.hdButton.setImageResource(R.drawable.outline_hd_24)
        }

        loadImage(viewModel.url)
    }

    private fun onSharedElementEnterTransitionEnd() {
        binding.dummyImageView.post {
            binding.dummyImageView.transitionName = null
            binding.imageView.transitionName = args.transitionName

            binding.dummyImageView.visibility = View.GONE
            binding.imageView.visibility = View.VISIBLE
        }
    }

    fun onViewCreated() {
        if (intent.extras == null) {
            finish()
            return
        }

        insets.observe(this) {
            insetViewExceptTopAutomaticallyByPadding(this, binding.bottomBar)
        }

        val context = this

        binding.dummyImageView.transitionName = args.transitionName

        binding.loadingView.showProgressBar()
        binding.loadingView.setOnRefreshClickListener {
            val url = viewModel.url ?: return@setOnRefreshClickListener
            loadImage(url)
        }

        loadImage(viewModel.url)

        binding.imageView.postDelayed({
            startPostponedEnterTransition()
        }, 50)

        if (viewModel.url?.startsWith("file:") == true) {
            binding.bottomBar.visibility = View.GONE
        } else {
            binding.bottomBar.visibility = View.VISIBLE
        }

        moreActionsHelper.downloadResult.observe(this) {
            when (it) {
                is StatefulData.NotStarted -> {}
                is StatefulData.Error -> {
                    Snackbar
                        .make(
                            getSnackbarContainer(),
                            R.string.error_downloading_image,
                            Snackbar.LENGTH_LONG,
                        )
                        .setAnchorView(binding.bottomBar)
                        .show()
                }
                is StatefulData.Loading -> {}
                is StatefulData.Success -> {
                    it.data
                        .onSuccess { downloadResult ->
                            try {
                                val uri = downloadResult.uri
                                val mimeType = downloadResult.mimeType

                                val snackbarMsg =
                                    getString(R.string.image_saved_format, downloadResult.uri)
                                Snackbar
                                    .make(
                                        getSnackbarContainer(),
                                        snackbarMsg,
                                        Snackbar.LENGTH_LONG,
                                    )
                                    .setAction(R.string.view) {
                                        Utils.safeLaunchExternalIntentWithErrorDialog(
                                            context,
                                            supportFragmentManager,
                                            Intent(Intent.ACTION_VIEW).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                setDataAndType(uri, mimeType)
                                            },
                                        )
                                    }
                                    .setAnchorView(binding.bottomBar)
                                    .show()
                            } catch (e: IOException) {
                                /* do nothing */
                            }
                        }
                        .onFailure {
                            if (it is FileDownloadHelper.CustomDownloadLocationException) {
                                Snackbar
                                    .make(
                                        getSnackbarContainer(),
                                        R.string.error_downloading_image,
                                        Snackbar.LENGTH_LONG,
                                    )
                                    .setAction(R.string.downloads_settings) {
                                        setResult(ErrorCustomDownloadLocation)
                                        finish()
                                    }
                                    .setAnchorView(binding.bottomBar)
                                    .show()
                            } else {
                                crashLogger?.recordException(it)
                                Snackbar
                                    .make(
                                        getSnackbarContainer(),
                                        R.string.error_downloading_image,
                                        Snackbar.LENGTH_LONG,
                                    )
                                    .setAnchorView(binding.bottomBar)
                                    .show()
                            }
                        }
                }
            }
        }

        binding.shareButton.setOnClickListener {
            val url = viewModel.url ?: return@setOnClickListener
            createImageOrLinkActionsHandler(
                url,
                moreActionsHelper,
                supportFragmentManager,
                args.mimeType,
            )(R.id.share_image)
        }
        binding.downloadButton.setOnClickListener {
            val url = viewModel.url ?: return@setOnClickListener
            createImageOrLinkActionsHandler(
                url,
                moreActionsHelper,
                supportFragmentManager,
                args.mimeType,
            )(R.id.download)
        }
        binding.infoButton.setOnClickListener {
            val url = viewModel.url ?: return@setOnClickListener
            ImageInfoDialogFragment.show(supportFragmentManager, url)
        }
        binding.moreButton.setOnClickListener {
            val url = viewModel.url ?: return@setOnClickListener
            showAdvancedLinkOptions(
                url,
                moreActionsHelper,
                supportFragmentManager,
                args.mimeType,
            )
        }

        if (preferences.imagePreviewHideUiByDefault) {
            binding.root.post {
                hideUi()
            }
        }
    }

    override fun onDestroy() {
        offlineManager.cancelFetch(binding.root)
        showSystemUI()
        super.onDestroy()
    }

    /**
     * @param forceLoadAsImage is true, will ignore the url format or domain and download the page
     * and pretend it's an image/gif
     */
    private fun loadImage(url: String?, forceLoadAsImage: Boolean = false) {
        url ?: return

        val uri = Uri.parse(url)
        if (uri.host == "imgur.com" && !forceLoadAsImage &&
            uri.encodedPath?.endsWith(".png") != true
        ) {
            binding.progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                val html = linkFetcher.downloadSite(url)
                val previewInfo = ImgurPageParser().parsePage(url, html)

                val rootView = binding.root

                rootView.post {
                    binding.progressBar.visibility = View.GONE

                    if (previewInfo == null) {
                        binding.loadingView.showDefaultErrorMessageFor(RuntimeException())

                        showUi()
                    } else {
                        if (previewInfo.wasUrlRawGif) {
                            loadImage(url, forceLoadAsImage = true)
                        } else {
                            loadImage(previewInfo.url)
                        }
                    }
                }
            }

            return
        }

        val imageName = args.title
        mimeType = args.mimeType

        fileName = with(imageName ?: url) {
            val s = substring(lastIndexOf('/') + 1)
            val indexOfDot = s.lastIndexOf('.')
            if (indexOfDot != -1) {
                s
            } else {
                run {
                    val urlHasExtension = url.lastIndexOf(".") != -1
                    if (urlHasExtension) {
                        s + url.substring(url.lastIndexOf("."))
                    } else {
                        s
                    }
                }
            }
        }

        binding.imageView.callback = object : GalleryImageView.Callback {
            override fun toggleUi() {
                this@ImageViewerActivity.togggleUi()
            }

            override fun showUi() {
                this@ImageViewerActivity.showUi()
            }

            override fun hideUi() {
                this@ImageViewerActivity.hideUi()
            }

            override fun zoom(curZoom: Float) {
                this@ImageViewerActivity.hideUi()
            }

            var isExiting = false
            override fun overScroll(offX: Float, offY: Float, curZoom: Float) {
                val exitOffsetScaled = Utils.convertDpToPixel(EXIT_OFFSET_DP) / curZoom
                if (offY > exitOffsetScaled ||
                    offY < -exitOffsetScaled ||
                    offX > exitOffsetScaled ||
                    offX < -exitOffsetScaled
                ) {
                    if (!isExiting) {
                        isExiting = true
                    }
                } else {
                    if (isExiting) {
                        isExiting = false
                    }
                }
            }

            override fun overScrollEnd(): Boolean {
                if (isExiting) {
                    supportFinishAfterTransition()
                    return true
                }

                return false
            }
        }

        loadImageFromUrl(url)
    }

    private fun togggleUi() {
        if (showingUi) {
            hideUi()
        } else {
            showUi()
        }
    }

    private fun showUi() {
        showingUi = true
        showActionBar()
        showSystemUI()
        binding.bottomBar.animate().translationY(0f)
    }

    private fun hideUi() {
        showingUi = false
        hideActionBar()
        hideSystemUI()
        binding.bottomBar.animate().translationY(binding.bottomBar.height.toFloat())
    }

    private fun hideSystemUI() {
        WindowInsetsControllerCompat(
            window,
            requireNotNull(window.decorView),
        ).let {
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun showSystemUI() {
        WindowInsetsControllerCompat(
            window,
            requireNotNull(window.decorView),
        ).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun loadImageFromUrl(url: String, forceSize: Size? = null) {
        Log.d(TAG, "loadImageFromUrl: $url")

        binding.progressBar.visibility = View.VISIBLE

        if (url.startsWith("file:")) {
            binding.progressBar.visibility = View.GONE
            startPostponedEnterTransition()
            binding.loadingView.hideAll()

            binding.dummyImageView.load(Uri.parse(url))
            binding.imageView.load(Uri.parse(url))
            return
        }

        offlineManager.fetchImageWithError(
            rootView = binding.root,
            url = url,
            listener = {
                val tempSize = Size()
                offlineManager.getImageSizeHint(it, tempSize)

                val request = ImageRequest.Builder(binding.imageView.context)
                    .data(it)
                    .allowHardware(false)
                    .target(
                        object : Target {
                            override fun onError(error: Image?) {
                                super.onError(error)
                                binding.progressBar.visibility = View.GONE
                                binding.loadingView.showErrorWithRetry(
                                    R.string.error_downloading_image,
                                )
                            }

                            override fun onSuccess(result: Image) {
                                super.onSuccess(result)

                                binding.progressBar.visibility = View.GONE

                                Log.d(
                                    TAG,
                                    "Image drawable size: w${result.width} h${result.height}",
                                )

                                val drawable = result.asDrawable(this@ImageViewerActivity.resources)

                                if (result is BitmapDrawable) {
                                    val bitmap = result.bitmap
                                    if (bitmap.byteCount >= MAX_BITMAP_SIZE && forceSize == null) {
                                        val maxPixels = 24000000
                                        val pixelsInBitmap = bitmap.width * bitmap.height

                                        if (pixelsInBitmap > maxPixels) {
                                            val scale = sqrt(pixelsInBitmap / maxPixels.toDouble())
                                            loadImageFromUrl(
                                                url,
                                                Size(
                                                    (bitmap.width / scale).roundToInt(),
                                                    (bitmap.height / scale).roundToInt(),
                                                ),
                                            )

                                            Snackbar
                                                .make(
                                                    getSnackbarContainer(),
                                                    R.string.warn_image_downscaled,
                                                    Snackbar.LENGTH_LONG,
                                                )
                                                .setAnchorView(binding.bottomBar)
                                                .show()
                                        } else {
                                            binding.loadingView
                                                .showErrorText(
                                                    R.string.error_image_too_large_to_preview,
                                                )
                                        }
                                        return
                                    }
                                }

                                startPostponedEnterTransition()
                                binding.loadingView.hideAll()

                                binding.dummyImageView.setImageDrawable(drawable)
                                binding.imageView.setImageDrawable(drawable)

                                if (drawable is Animatable) {
                                    drawable.start()
                                }
                            }
                        },
                    )
                    .apply {
                        if (forceSize != null) {
                            size(Dimension(forceSize.width), Dimension(forceSize.height))
                        } else {
                            size(coil3.size.Size.ORIGINAL)
                        }
                    }
                    .build()
                binding.imageView.context.imageLoader.enqueue(request)
            },
            errorListener = {
                binding.progressBar.visibility = View.GONE
                binding.loadingView.showDefaultErrorMessageFor(
                    it,
                )
            },
        )
    }

    private fun showActionBar() {
//        TransitionManager.beginDelayedTransition(binding.root, makeTransition())

        binding.appBar.animate().translationY(0f)
    }

    private fun hideActionBar() {
//        TransitionManager.beginDelayedTransition(binding.root, makeTransition())

        binding.appBar.animate().translationY((-binding.appBar.height).toFloat())
    }

    private fun getSnackbarContainer(): View = binding.contentView

    override fun showBottomMenu(bottomMenu: BottomMenu, expandFully: Boolean) {
        currentBottomMenu?.close()
        bottomMenu.show(
            bottomMenuContainer = this,
            bottomSheetContainer = binding.root,
            expandFully = expandFully,
        )
        currentBottomMenu = bottomMenu
    }
}
