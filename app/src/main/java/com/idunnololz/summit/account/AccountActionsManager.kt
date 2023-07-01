package com.idunnololz.summit.account

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.idunnololz.summit.R
import com.idunnololz.summit.api.AccountInstanceMismatchException
import com.idunnololz.summit.api.NotAuthenticatedException
import com.idunnololz.summit.coroutine.CoroutineScopeFactory
import com.idunnololz.summit.actions.PendingActionsManager
import com.idunnololz.summit.actions.PendingCommentView
import com.idunnololz.summit.actions.PendingCommentsManager
import com.idunnololz.summit.actions.VotesManager
import com.idunnololz.summit.api.dto.CommentId
import com.idunnololz.summit.lemmy.PostRef
import com.idunnololz.summit.lemmy.actions.ActionInfo
import com.idunnololz.summit.lemmy.actions.LemmyAction
import com.idunnololz.summit.lemmy.actions.LemmyActionFailureException
import com.idunnololz.summit.lemmy.actions.LemmyActionFailureReason
import com.idunnololz.summit.lemmy.actions.LemmyActionResult
import com.idunnololz.summit.lemmy.utils.VotableRef
import com.idunnololz.summit.lemmy.utils.VoteUiHandler
import com.idunnololz.summit.reddit.LemmyUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountActionsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountManager: AccountManager,
    private val pendingActionsManager: PendingActionsManager,
    private val coroutineScopeFactory: CoroutineScopeFactory,
) {

    companion object {
        private const val TAG = "AccountActionsManager"
    }

    private val votesManager = VotesManager(context)
    private val pendingCommentsManager = PendingCommentsManager()
    private var nextId: Long = 1

    interface Registration {
        fun voteCurrent(score: Int, totalScore: Int)
        fun voteSuccess(newScore: Int, totalScore: Int)
        fun votePending(pendingScore: Int, totalScore: Int)
        fun voteFailed(score: Int, totalScore: Int, e: Throwable)
    }

    class VoteHandlerRegistration(
        val ref: VotableRef,
        val registration: Registration,
    )

    private val regIdToRegistration = mutableMapOf<Long, VoteHandlerRegistration>()
    private val voteRefToRegistrations = mutableMapOf<VotableRef, MutableList<Registration>>()
    private val coroutineScope = coroutineScopeFactory.create()

    val onCommentActionChanged = MutableSharedFlow<Unit>()

    val voteUiHandler = object : VoteUiHandler {
        override fun bindVoteUi(
            lifecycleOwner: LifecycleOwner,
            currentVote: Int,
            currentScore: Int,
            instance: String,
            ref: VotableRef,
            upVoteView: View?,
            downVoteView: View?,
            scoreView: TextView,
            registration: Registration,
        ) {
            votesManager.setVoteIfNoneSet(ref, currentVote)
            votesManager.setScoreIfNoneSet(ref, currentScore)

            val existingRegId = scoreView.getTag(R.id.account_actions_manager_reg_id)
            if (existingRegId != null) {
                unregisterVoteHandler(existingRegId as Long)
            }

            val account = accountManager.currentAccount.value

            upVoteView?.setOnClickListener {
                val curVote = votesManager.getVote(ref) ?: 0
                val curScore = votesManager.getScore(ref) ?: 0
                val newScore = if (curVote == 1) {
                    0
                } else {
                    1
                }

                voteOn(instance, ref, newScore, account)
                    .onFailure {
                        registration.voteFailed(curVote, curScore, it)
                    }
            }
            downVoteView?.setOnClickListener {
                val curVote = votesManager.getVote(ref) ?: 0
                val curScore = votesManager.getScore(ref) ?: 0
                val newScore = if (curVote == -1) {
                    0
                } else {
                    -1
                }

                voteOn(instance, ref, newScore, account)
                    .onFailure {
                        registration.voteFailed(curVote, curScore, it)
                    }
            }

            val regId = nextId
            scoreView.setTag(R.id.account_actions_manager_reg_id, regId)
            nextId++

            registerVoteHandler(regId, ref, registration)

            registration.voteCurrent(
                score = votesManager.getVote(ref) ?: 0,
                totalScore = votesManager.getScore(ref) ?: 0
            )

            Log.d(TAG, "Binding vote handler - ${ref}")

            scoreView.text = LemmyUtils.abbrevNumber(
                votesManager.getScore(ref)?.toLong() ?: currentScore.toLong())

            lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    Log.d(TAG, "Lifecycle onDestroy. Unbinding - ${ref}")

                    lifecycleOwner.lifecycle.removeObserver(this)

                    unbindVoteUi(scoreView)
                    upVoteView?.setOnClickListener(null)
                    downVoteView?.setOnClickListener(null)
                }
            })
        }

        override fun unbindVoteUi(scoreView: View) {
            val existingRegId = scoreView.getTag(R.id.account_actions_manager_reg_id)
            if (existingRegId != null) {
                unregisterVoteHandler(existingRegId as Long)
            }
        }
    }

    private val onActionChangedListener = object : PendingActionsManager.OnActionChangedListener {
        override fun onActionAdded(action: LemmyAction) {
            when (action.info) {
                is ActionInfo.VoteActionInfo -> {
                    votesManager.setPendingVote(action.info.ref, action.info.dir)
                    voteRefToRegistrations[action.info.ref]?.forEach {
                        it.votePending(
                            pendingScore = action.info.dir,
                            totalScore = votesManager.getScore(action.info.ref) ?: 0
                        )
                    }
                }
                is ActionInfo.CommentActionInfo -> {
                    coroutineScope.launch {
                        pendingCommentsManager.onCommentActionAdded(action.id, action.info)
                        onCommentActionChanged.emit(Unit)
                    }
                }
                is ActionInfo.DeleteCommentActionInfo -> {
                    coroutineScope.launch {
                        pendingCommentsManager.onDeleteCommentActionAdded(action.id, action.info)
                        onCommentActionChanged.emit(Unit)
                    }
                }
                is ActionInfo.EditActionInfo -> {
                    coroutineScope.launch {
                        pendingCommentsManager.onEditCommentActionAdded(action.id, action.info)
                        onCommentActionChanged.emit(Unit)
                    }
                }
                null -> {}
            }
        }

        override fun onActionFailed(action: LemmyAction, reason: LemmyActionFailureReason) {
            Log.d(TAG, "onActionComplete(): $action, reason: $reason")
            when (action.info) {
                is ActionInfo.VoteActionInfo -> {
                    votesManager.clearPendingVotes(action.info.ref)
                    voteRefToRegistrations[action.info.ref]?.forEach {
                        it.voteFailed(
                            score = votesManager.getVote(action.info.ref) ?: 0,
                            totalScore = votesManager.getScore(action.info.ref) ?: 0,
                            e = LemmyActionFailureException(reason)
                        )
                    }
                }
                is ActionInfo.CommentActionInfo -> {
                    coroutineScope.launch {
                        pendingCommentsManager.onCommentActionFailed(action.id, action.info, reason)
                        onCommentActionChanged.emit(Unit)
                    }
                }
                is ActionInfo.DeleteCommentActionInfo -> {
                    coroutineScope.launch {
                        pendingCommentsManager.onDeleteCommentActionFailed(action.id, action.info, reason)
                        onCommentActionChanged.emit(Unit)
                    }
                }
                is ActionInfo.EditActionInfo -> {
                    coroutineScope.launch {
                        pendingCommentsManager.onEditCommentActionFailed(action.id, action.info, reason)
                        onCommentActionChanged.emit(Unit)
                    }
                }
                null -> {}
            }
        }

        override fun onActionComplete(action: LemmyAction, result: LemmyActionResult<*, *>) {
            Log.d(TAG, "onActionComplete(): $action")
            when (action.info) {
                is ActionInfo.VoteActionInfo -> {
                    val score = (result as LemmyActionResult.VoteLemmyActionResult).result
                        .fold(
                            {
                                val voteRef = VotableRef.PostRef(it.post.id)

                                votesManager.setScore(voteRef, it.counts.score)
                                it.counts.score
                            },
                            {
                                val voteRef = VotableRef.CommentRef(it.comment.id)

                                votesManager.setScore(voteRef, it.counts.score)
                                it.counts.score
                            }
                        )

                    votesManager.setVote(action.info.ref, action.info.dir)
                    voteRefToRegistrations[action.info.ref]?.forEach {
                        it.voteSuccess(action.info.dir, score)
                    }
                }
                is ActionInfo.CommentActionInfo -> {
                    coroutineScope.launch {
                        pendingCommentsManager.onCommentActionComplete(action.id, action.info)
                        onCommentActionChanged.emit(Unit)
                    }
                }
                is ActionInfo.DeleteCommentActionInfo -> {
                    coroutineScope.launch {
                        pendingCommentsManager.onDeleteCommentActionComplete(action.id, action.info)
                        onCommentActionChanged.emit(Unit)
                    }
                }
                is ActionInfo.EditActionInfo -> {
                    coroutineScope.launch {
                        pendingCommentsManager.onEditCommentActionComplete(action.id, action.info)
                        onCommentActionChanged.emit(Unit)
                    }
                }
                null -> {}
            }
        }
    }

    init {
        accountManager.addOnAccountChangedListener(object : AccountManager.OnAccountChangedListener {
            override suspend fun onAccountChanged(newAccount: Account?) {
                votesManager.reset()
            }
        })

        pendingActionsManager.addActionCompleteListener(onActionChangedListener)
    }

    suspend fun createComment(
        postRef: PostRef,
        parentId: CommentId?,
        content: String,
    ) {
        val account = accountManager.currentAccount.value ?: return
        pendingActionsManager.comment(
            postRef,
            parentId,
            content,
            account.id,
        )
    }

    suspend fun editComment(
        postRef: PostRef,
        commentId: CommentId,
        content: String,
    ) {
        val account = accountManager.currentAccount.value ?: return
        pendingActionsManager.editComment(
            postRef,
            commentId,
            content,
            account.id,
        )
    }

    suspend fun deleteComment(
        postRef: PostRef,
        commentId: CommentId,
    ) {
        val account = accountManager.currentAccount.value ?: return
        pendingActionsManager.deleteComment(
            postRef,
            commentId,
            account.id,
        )
    }

    private fun registerVoteHandler(existingRegId: Long, ref: VotableRef, registration: Registration) {
        regIdToRegistration[existingRegId] = VoteHandlerRegistration(ref, registration)
        val list = voteRefToRegistrations.getOrPut(ref) { mutableListOf() }
        list.add(registration)

        Log.d(TAG, "Total registrations: ${regIdToRegistration.size}")
    }

    private fun unregisterVoteHandler(existingRegId: Long) {
        val regData = regIdToRegistration.remove(existingRegId) ?: return
        val list = voteRefToRegistrations.getOrPut(regData.ref) { mutableListOf() }
        list.remove(regData.registration)
    }

    private fun voteOn(
        instance: String,
        ref: VotableRef,
        dir: Int,
        account: Account?,
    ): Result<Unit> {
        if (account == null) {
            return Result.failure(NotAuthenticatedException())
        }
        if (account.instance != instance) {
            return Result.failure(AccountInstanceMismatchException(account.instance, instance))
        }

        pendingActionsManager.voteOn(instance, ref, dir, account.id)

        return Result.success(Unit)
    }

    fun getPendingComments(postRef: PostRef) =
        pendingCommentsManager.getPendingComments(postRef)

    fun removePendingComment(pendingComment: PendingCommentView) {
        pendingCommentsManager.removePendingComment(pendingComment)
    }
}