package com.idunnololz.summit.lemmy.comment

import android.app.Activity
import android.os.Bundle
import android.os.Parcelable
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import arrow.core.Either
import com.github.drjacky.imagepicker.ImagePicker
import com.idunnololz.summit.R
import com.idunnololz.summit.account.Account
import com.idunnololz.summit.accountUi.PreAuthDialogFragment
import com.idunnololz.summit.accountUi.SignInNavigator
import com.idunnololz.summit.alert.AlertDialogFragment
import com.idunnololz.summit.api.dto.CommentView
import com.idunnololz.summit.api.dto.PostView
import com.idunnololz.summit.databinding.ErrorMessageOldReplyTargetBinding
import com.idunnololz.summit.databinding.FragmentAddOrEditCommentBinding
import com.idunnololz.summit.drafts.DraftData
import com.idunnololz.summit.drafts.DraftEntry
import com.idunnololz.summit.drafts.DraftTypes
import com.idunnololz.summit.drafts.DraftsDialogFragment
import com.idunnololz.summit.drafts.OriginalCommentData
import com.idunnololz.summit.error.ErrorDialogFragment
import com.idunnololz.summit.lemmy.PostRef
import com.idunnololz.summit.lemmy.utils.TextFormatterHelper
import com.idunnololz.summit.preferences.GlobalSettings
import com.idunnololz.summit.util.BackPressHandler
import com.idunnololz.summit.util.BaseDialogFragment
import com.idunnololz.summit.util.BottomMenu
import com.idunnololz.summit.util.FullscreenDialogFragment
import com.idunnololz.summit.util.StatefulData
import com.idunnololz.summit.util.dateStringToPretty
import com.idunnololz.summit.util.dateStringToTs
import com.idunnololz.summit.util.ext.getColorFromAttribute
import com.idunnololz.summit.util.ext.getSelectedText
import com.idunnololz.summit.util.ext.showAllowingStateLoss
import com.idunnololz.summit.util.getParcelableCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.parcelize.Parcelize

@AndroidEntryPoint
class AddOrEditCommentFragment :
    BaseDialogFragment<FragmentAddOrEditCommentBinding>(),
    FullscreenDialogFragment,
    SignInNavigator,
    BackPressHandler {

    companion object {
        const val REQUEST_KEY = "AddOrEditCommentFragment_req_key"
        const val REQUEST_KEY_RESULT = "result"

        fun showReplyDialog(
            instance: String,
            postOrCommentView: Either<PostView, CommentView>,
            fragmentManager: FragmentManager,
        ) {
            AddOrEditCommentFragment().apply {
                arguments = AddOrEditCommentFragmentArgs(
                    instance = instance,
                    commentView = postOrCommentView.getOrNull(),
                    postView = postOrCommentView.leftOrNull(),
                    editCommentView = null,
                ).toBundle()
            }.showAllowingStateLoss(fragmentManager, "AddOrEditCommentFragment")
        }
    }

    private val args by navArgs<AddOrEditCommentFragmentArgs>()

    private val viewModel: AddOrEditCommentViewModel by viewModels()

    private val textFormatterHelper = TextFormatterHelper()

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val uri = it.data?.data

                if (uri != null) {
                    viewModel.uploadImage(args.instance, uri)
                }
            }
        }

    @Parcelize
    enum class Result : Parcelable {
        CommentSent,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setStyle(STYLE_NO_TITLE, R.style.Theme_App_DialogFullscreen)

        childFragmentManager.setFragmentResultListener(
            AddLinkDialogFragment.REQUEST_KEY,
            this,
        ) { key, bundle ->
            val result = bundle.getParcelableCompat<AddLinkDialogFragment.AddLinkResult>(
                AddLinkDialogFragment.REQUEST_KEY_RESULT,
            )
            if (result != null) {
                textFormatterHelper.onLinkAdded(result.text, result.url)
            }
        }
        childFragmentManager.setFragmentResultListener(
            DraftsDialogFragment.REQUEST_KEY,
            this,
        ) { key, bundle ->
            val result = bundle.getParcelableCompat<DraftEntry>(
                DraftsDialogFragment.REQUEST_KEY_RESULT,
            )
            if (result != null) {
                viewModel.currentDraftEntry.value = result
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            dialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        setBinding(FragmentAddOrEditCommentBinding.inflate(inflater, container, false))

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        requireMainActivity().apply {
            insetViewExceptBottomAutomaticallyByMargins(viewLifecycleOwner, binding.toolbar)
            insetViewExceptTopAutomaticallyByPadding(viewLifecycleOwner, binding.bottomBar)
        }

        viewModel.currentAccount.observe(viewLifecycleOwner) {
            if (it != null) {
                setup(it, savedInstanceState)

                requireActivity().invalidateOptionsMenu()
            }
        }

        viewModel.commentSentEvent.observe(viewLifecycleOwner) {
            val result = it.contentIfNotHandled ?: return@observe

            result
                .onSuccess {
                    setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY_RESULT to Result.CommentSent))

                    dismiss()
                }
                .onFailure {
                    ErrorDialogFragment.show(
                        getString(R.string.error_unable_to_send_message),
                        it,
                        childFragmentManager,
                    )
                }
        }

        viewModel.messages.observe(viewLifecycleOwner) {
            refreshMessage()
        }

        binding.toolbar.title = getString(R.string.comment)
        if (isEdit()) {
            binding.toolbar.inflateMenu(R.menu.menu_edit_comment)
        } else {
            binding.toolbar.inflateMenu(R.menu.menu_add_comment)
        }
        binding.toolbar.setNavigationIcon(R.drawable.baseline_close_24)
        binding.toolbar.setNavigationIconTint(
            context.getColorFromAttribute(io.noties.markwon.R.attr.colorControlNormal),
        )
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.send_comment -> {
                    val account = viewModel.currentAccount.value
                    if (account == null) {
                        PreAuthDialogFragment()
                            .showAllowingStateLoss(childFragmentManager, "AS")
                        return@setOnMenuItemClickListener true
                    }

                    val inboxItem = args.inboxItem
                    val personId = args.personId
                    if (personId != 0L) {
                        viewModel.sendComment(
                            personId,
                            binding.commentEditor.editText?.text.toString(),
                        )
                    } else if (inboxItem != null) {
                        viewModel.sendComment(
                            account,
                            args.instance,
                            inboxItem,
                            binding.commentEditor.editText?.text.toString(),
                        )
                    } else {
                        viewModel.sendComment(
                            account,
                            PostRef(
                                args.instance,
                                requireNotNull(
                                    args.postView?.post?.id ?: args.commentView?.post?.id,
                                ) {
                                    "Both postView and commentView were null!"
                                },
                            ),
                            args.commentView?.comment?.id,
                            binding.commentEditor.editText?.text.toString(),
                        )
                    }
                    true
                }
                R.id.update_comment -> {
                    if (viewModel.currentAccount.value == null) {
                        PreAuthDialogFragment()
                            .showAllowingStateLoss(childFragmentManager, "DF")
                        return@setOnMenuItemClickListener true
                    }

                    viewModel.updateComment(
                        PostRef(
                            args.instance,
                            requireNotNull(args.editCommentView?.post?.id) {
                                "editCommentView were null!"
                            },
                        ),
                        requireNotNull(args.editCommentView?.comment?.id),
                        binding.commentEditor.editText?.text.toString(),
                    )
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            val replyTargetTs =
                args.commentView?.let {
                    dateStringToTs(it.comment.updated ?: it.comment.published)
                } ?: args.postView?.let {
                    dateStringToTs(it.post.updated ?: it.post.published)
                }
            val thresholdMs = GlobalSettings.warnReplyToOldContentThresholdMs ?: Long.MAX_VALUE

            if (replyTargetTs != null &&
                (System.currentTimeMillis() - replyTargetTs) > thresholdMs
            ) {
                viewModel.addMessage(AddOrEditCommentViewModel.Message.ReplyTargetTooOld(replyTargetTs))
            }
        }
    }

    private fun refreshMessage() {
        if (!isBindingAvailable()) {
            return
        }

        TransitionManager.beginDelayedTransition(binding.root)

        val context = requireContext()
        val messageView = when (val message = viewModel.messages.value?.firstOrNull()) {
            is AddOrEditCommentViewModel.Message.ReplyTargetTooOld -> {
                val b = ErrorMessageOldReplyTargetBinding.inflate(
                    LayoutInflater.from(requireContext()), binding.messageContainer, false)
                b.message.text = getString(
                    R.string.error_retry_target_too_old_format,
                    dateStringToPretty(context, message.replyTargetTs))
                b.close.setOnClickListener {
                    viewModel.dismissMessage(message)
                }
                b.root
            }
            null -> null
        }
        binding.messageContainer.removeAllViews()

        if (messageView != null) {
            binding.messageContainer.addView(messageView)
        }
    }

    private fun setup(currentAccount: Account, savedInstanceState: Bundle?) {
        if (!isBindingAvailable()) {
            return
        }

        val context = requireContext()

        val postView = args.postView
        val commentView = args.commentView
        val inboxItem = args.inboxItem
        val personId = args.personId

        val commentEditor = binding.commentEditor
        if (isEdit()) {
            val commentToEdit = requireNotNull(args.editCommentView)
            binding.scrollView.visibility = View.GONE
            binding.divider.visibility = View.GONE

            if (savedInstanceState == null) {
                commentEditor.editText?.setText(commentToEdit.comment.content)
            }
        } else if (commentView != null) {
            binding.replyingTo.text = commentView.comment.content
        } else if (postView != null) {
            binding.replyingTo.text = postView.post.body
        } else if (inboxItem != null) {
            binding.replyingTo.text = inboxItem.content
        } else if (personId != 0L) {
            binding.scrollView.visibility = View.GONE
            binding.divider.visibility = View.GONE
        } else {
            dismiss()
            return
        }

        textFormatterHelper.setupTextFormatterToolbar(
            binding.textFormatToolbar,
            requireNotNull(commentEditor.editText),
            onChooseImageClick = {
                val bottomMenu = BottomMenu(context).apply {
                    setTitle(R.string.insert_image)
                    addItemWithIcon(R.id.from_camera, R.string.take_a_photo, R.drawable.baseline_photo_camera_24)
                    addItemWithIcon(R.id.from_gallery, R.string.choose_from_gallery, R.drawable.baseline_image_24)
                    addItemWithIcon(R.id.from_camera_with_editor, R.string.take_a_photo_with_editor, R.drawable.baseline_photo_camera_24)
                    addItemWithIcon(R.id.from_gallery_with_editor, R.string.choose_from_gallery_with_editor, R.drawable.baseline_image_24)

                    setOnMenuItemClickListener {
                        when (it.id) {
                            R.id.from_camera -> {
                                val intent = ImagePicker.with(requireActivity())
                                    .cameraOnly()
                                    .createIntent()
                                launcher.launch(intent)
                            }
                            R.id.from_gallery -> {
                                val intent = ImagePicker.with(requireActivity())
                                    .galleryOnly()
                                    .createIntent()
                                launcher.launch(intent)
                            }
                            R.id.from_camera_with_editor -> {
                                val intent = ImagePicker.with(requireActivity())
                                    .cameraOnly()
                                    .crop()
                                    .cropFreeStyle()
                                    .createIntent()
                                launcher.launch(intent)
                            }
                            R.id.from_gallery_with_editor -> {
                                val intent = ImagePicker.with(requireActivity())
                                    .galleryOnly()
                                    .crop()
                                    .cropFreeStyle()
                                    .createIntent()
                                launcher.launch(intent)
                            }
                        }
                    }
                }

                bottomMenu.show(
                    mainActivity = requireMainActivity(),
                    viewGroup = binding.coordinatorLayout,
                    expandFully = true,
                    handleBackPress = false,
                )
            },
            onAddLinkClick = {
                AddLinkDialogFragment.show(
                    binding.commentEditText.getSelectedText(),
                    childFragmentManager,
                )
            },
            onPreviewClick = {
                PreviewCommentDialogFragment()
                    .apply {
                        arguments = PreviewCommentDialogFragmentArgs(
                            args.instance,
                            commentEditor.editText?.text.toString(),
                        ).toBundle()
                    }
                    .showAllowingStateLoss(childFragmentManager, "AA")
            },
            onDraftsClick = {
                DraftsDialogFragment.show(childFragmentManager, DraftTypes.Comment)
            },
        )
        viewModel.uploadImageEvent.observe(viewLifecycleOwner) {
            when (it) {
                is StatefulData.Error -> {
                    binding.loadingView.hideAll()
                    AlertDialogFragment.Builder()
                        .setMessage(
                            getString(
                                R.string.error_unable_to_send_post,
                                it.error::class.qualifiedName,
                                it.error.message,
                            ),
                        )
                        .createAndShow(childFragmentManager, "ASDS")
                }
                is StatefulData.Loading -> {
                    binding.loadingView.showProgressBar()
                }
                is StatefulData.NotStarted -> {}
                is StatefulData.Success -> {
                    binding.loadingView.hideAll()
                    viewModel.uploadImageEvent.clear()

                    textFormatterHelper.onImageUploaded(it.data.url)
                }
            }
        }
        viewModel.currentDraftEntry.observe(viewLifecycleOwner) {
            if (it == null) return@observe

            val data = it.data
            if (data is DraftData.CommentDraftData) {
                binding.commentEditText.setText(data.content)
            }
        }
    }

    private fun isEdit(): Boolean {
        return args.editCommentView != null
    }

    override fun navigateToSignInScreen() {
        (parentFragment as? SignInNavigator)?.navigateToSignInScreen()
        dismiss()
    }

    override fun proceedAnyways(tag: Int) {
    }

    override fun onBackPressed(): Boolean {
        if (isBindingAvailable()) {
            if (binding.coordinatorLayout.childCount > 0) {
                binding.coordinatorLayout.removeAllViews()
                return true
            }
        }

        try {
            saveDraft()

            dismiss()
        } catch (e: IllegalStateException) {
            // do nothing... very rare
        }
        return true
    }

    private fun saveDraft() {
        val content = binding.commentEditText.text?.toString()

        val currentDraftEntry = viewModel.currentDraftEntry.value
        if (!content.isNullOrBlank()) {
            if (currentDraftEntry?.data != null &&
                currentDraftEntry.data is DraftData.CommentDraftData
            ) {
                viewModel.draftsManager.updateDraftAsync(
                    currentDraftEntry.id,
                    currentDraftEntry.data.copy(
                        content = content,
                    ),
                    showToast = true,
                )
            } else {
                viewModel.draftsManager.saveDraftAsync(
                    DraftData.CommentDraftData(
                        args.editCommentView?.toOriginalCommentData(),
                        PostRef(
                            args.instance,
                            args.postView?.post?.id
                                ?: args.commentView?.post?.id
                                ?: args.editCommentView?.post?.id ?: 0,
                        ),
                        args.commentView?.comment?.id,
                        content,
                        viewModel.currentAccount.value?.id ?: 0L,
                        viewModel.currentAccount.value?.instance ?: "",
                    ),
                    showToast = true,
                )
            }
        }
    }

    private fun CommentView.toOriginalCommentData(): OriginalCommentData =
        OriginalCommentData(
            postRef = PostRef(args.instance, this.post.id),
            commentId = this.comment.id,
            content = this.comment.content,
            parentCommentId = args.commentView?.comment?.id,
        )
}
