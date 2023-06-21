package com.idunnololz.summit.api

import android.content.Context
import android.util.Log
import arrow.core.Either
import com.idunnololz.summit.account.Account
import com.idunnololz.summit.api.dto.CommentSortType
import com.idunnololz.summit.api.dto.CommentView
import com.idunnololz.summit.api.dto.CommunityView
import com.idunnololz.summit.api.dto.CreateCommentLike
import com.idunnololz.summit.api.dto.CreatePostLike
import com.idunnololz.summit.api.dto.GetComments
import com.idunnololz.summit.api.dto.GetCommunity
import com.idunnololz.summit.api.dto.GetPost
import com.idunnololz.summit.api.dto.GetPosts
import com.idunnololz.summit.api.dto.GetSite
import com.idunnololz.summit.api.dto.GetSiteResponse
import com.idunnololz.summit.api.dto.ListCommunities
import com.idunnololz.summit.api.dto.ListingType
import com.idunnololz.summit.api.dto.Login
import com.idunnololz.summit.api.dto.PostView
import com.idunnololz.summit.api.dto.Search
import com.idunnololz.summit.api.dto.SearchResponse
import com.idunnololz.summit.api.dto.SearchType
import com.idunnololz.summit.api.dto.SortType
import com.idunnololz.summit.util.Utils.serializeToMap
import com.idunnololz.summit.util.retry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Call
import javax.inject.Inject

typealias PostId = Int
typealias CommentId = Int

const val COMMENTS_DEPTH_MAX = 6

class LemmyApiClient @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "LemmyApiClient"

        const val API_VERSION = "v3"
        val DEFAULT_INSTANCE = CommonLemmyInstance.LemmyMl.site

        val DEFAULT_LEMMY_INSTANCES = listOf(
            CommonLemmyInstance.Beehaw.site,
            "feddit.de",
            "feddit.it",
            "lemmy.ca",
            CommonLemmyInstance.LemmyMl.site,
            "lemmy.one",
            CommonLemmyInstance.LemmyWorld.site,
            "lemmygrad.ml",
            "midwest.social",
            "mujico.org",
            "sh.itjust.works",
            "slrpnk.net",
            "sopuli.xyz",
            "szmer.info",
        )
    }

    private var api = LemmyApi.getInstance(context)
    private val okHttpClient = LemmyApi.okHttpClient(context)

    fun changeInstance(newInstance: String) {
        api = LemmyApi.getInstance(context, newInstance)
    }

    fun defaultInstance() {
        api = LemmyApi.getInstance(context)
    }

    fun clearCache() {
        okHttpClient.cache?.evictAll()
    }

    suspend fun fetchPosts(
        account: Account?,
        communityIdOrName: Either<Int, String>? = null,
        sortType: SortType,
        listingType: ListingType,
        page: Int,
        limit: Int? = null,
    ): Result<List<PostView>> {
        val communityId = communityIdOrName?.fold({ it }, { null })
        val communityName = communityIdOrName?.fold({ null }, { it })

        val form = try {
            GetPosts(
                community_id = communityId,
                community_name = communityName,
                sort = sortType.toString(),
                type_ = listingType.toString(),
                page = page,
                limit = limit,
                auth = account?.jwt,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching posts", e)
            false
        }

        return retrofitErrorHandler {
            api.getPosts(form = form.serializeToMap())
        }.fold(
            onSuccess = {
                Result.success(it.posts)
            },
            onFailure = {
                Result.failure(it)
            }
        )
    }

    suspend fun fetchPost(
        account: Account?,
        id: Either<PostId, CommentId>,
        force: Boolean,
    ): Result<PostView> {
        val postForm = id.fold({
            GetPost(id = it, auth = account?.jwt)
        }, {
            GetPost(comment_id = it, auth = account?.jwt)
        })

        return retrofitErrorHandler {
            if (force) {
                api.getPostNoCache(form = postForm.serializeToMap())
            } else {
                api.getPost(form = postForm.serializeToMap())
            }
        }.fold(
            onSuccess = {
                Result.success(it.post_view)
            },
            onFailure = {
                Result.failure(it)
            }
        )
    }

    suspend fun fetchComments(
        account: Account?,
        id: Either<PostId, CommentId>,
        sort: CommentSortType,
        force: Boolean,
    ): Result<List<CommentView>> {

        val commentsForm = id.fold({
            GetComments(
                max_depth = COMMENTS_DEPTH_MAX,
                type_ = ListingType.All,
                post_id = it,
                sort = sort,
                auth = account?.jwt,
            )
        }, {
            GetComments(
                max_depth = COMMENTS_DEPTH_MAX,
                type_ = ListingType.All,
                parent_id = it,
                sort = sort,
                auth = account?.jwt,
            )
        })

        return retrofitErrorHandler {
            if (force) {
                api.getCommentsNoCache(
                    commentsForm
                        .serializeToMap(),
                )
            } else {
                api.getComments(
                    commentsForm
                        .serializeToMap(),
                )
            }
        }.fold(
            onSuccess = {
                Result.success(it.comments)
            },
            onFailure = {
                Result.failure(it)
            }
        )
    }

    suspend fun getCommunity(
        account: Account?,
        idOrName: Either<Int, String>,
    ): Result<CommunityView> {
        val form = idOrName.fold({ id ->
            GetCommunity(id = id, auth = account?.jwt)
        }, { name ->
            GetCommunity(name = name, auth = account?.jwt)
        })

        return retrofitErrorHandler {
            api.getCommunity(form = form.serializeToMap())
        }.fold(
            onSuccess = {
                Result.success(it.community_view)
            },
            onFailure = {
                Result.failure(it)
            }
        )
    }

    suspend fun search(
        account: Account?,
        communityId: Int? = null,
        communityName: String? = null,
        sortType: SortType,
        listingType: ListingType,
        searchType: SearchType,
        page: Int? = null,
        query: String,
        creatorId: Int? = null,
    ): Result<SearchResponse> {
        val form = Search(
            q = query,
            type_ = searchType.toString(),
            creator_id = creatorId,
            community_id = communityId,
            community_name = communityName,
            sort = sortType.toString(),
            listing_type = listingType.toString(),
            page = page,
            auth = account?.jwt,
        )

        return retrofitErrorHandler { api.search(form = form.serializeToMap()) }.fold(
            onSuccess = {
                Result.success(it)
            },
            onFailure = {
                Result.failure(it)
            }
        )
    }

    suspend fun fetchCommunities(
        account: Account?,
        sortType: SortType,
        listingType: ListingType,
        page: Int = 1,
        limit: Int = 50,
    ): Result<List<CommunityView>> {
        val form = ListCommunities(
            type_ = listingType.toString(),
            sort = sortType.toString(),
            page = page,
            limit = limit,
            auth = account?.jwt,
        )
        return retrofitErrorHandler {
            api.getCommunityList(form = form.serializeToMap())
        }.fold(
            onSuccess = {
                Result.success(it.communities)
            },
            onFailure = {
                Result.failure(it)
            }
        )
    }

    suspend fun login(
        instance: String,
        username: String,
        password: String,
    ): Result<String?> {
        val originalInstance = this.instance
        changeInstance(instance)

        val form = Login(username, password)

        return retrofitErrorHandler { api.login(form = form) }
            .fold(
                onSuccess = {
                    Result.success(it.jwt)
                },
                onFailure = {
                    changeInstance(originalInstance)
                    Result.failure(it)
                }
            )
    }

    suspend fun fetchSiteWithRetry(
        auth: String?,
    ): Result<GetSiteResponse> = retry {
        val form = GetSite(auth = auth)

        retrofitErrorHandler { api.getSite(form = form.serializeToMap()) }
            .fold(
                onSuccess = {
                    Result.success(it)
                },
                onFailure = {
                    Result.failure(it)
                }
            )
    }

    suspend fun likePostWithRetry(
        postId: Int,
        score: Int,
        account: Account,
    ): Result<PostView> = retry {

        val form = CreatePostLike(
            post_id = postId,
            score = score,
            auth = account.jwt,
        )

        retrofitErrorHandler { api.likePost(form) }
            .fold(
                onSuccess = {
                    Result.success(it.post_view)
                },
                onFailure = {
                    Result.failure(it)
                }
            )
    }

    suspend fun likeCommentWithRetry(
        commentId: Int,
        score: Int,
        account: Account,
    ): Result<CommentView> = retry {

        val form = CreateCommentLike(
            comment_id = commentId,
            score = score,
            auth = account.jwt,
        )

        retrofitErrorHandler { api.likeComment(form) }
            .fold(
                onSuccess = {
                    Result.success(it.comment_view)
                },
                onFailure = {
                    Result.failure(it)
                }
            )
    }

    val instance: String
        get() = api.instance

    private suspend fun <T> retrofitErrorHandler(
        call: suspend () -> Call<T>
    ): Result<T> {
        val res = try {
            withContext(Dispatchers.IO) {
                call().execute()
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (res.isSuccessful) {
            return Result.success(requireNotNull(res.body()))
        } else {
            val errorCode = res.code()

            if (errorCode >= 500) {
                return Result.failure(ServerApiException(errorCode))
            }

            val errorBody = res.errorBody()?.string()
            val errMsg = try {
                errorBody?.let {
                    JSONObject(it).getString("error")
                } ?: run {
                    res.code().toString()
                }
            } catch (e: Exception) {
                errorBody
            }
            Log.e("ApiError", "Error message: ${errMsg}", RuntimeException())

            if (errMsg?.contains("timeout", ignoreCase = true) == true) {
                return Result.failure(ServerTimeoutException(errorCode))
            }

            return Result.failure(ClientApiException(errMsg, errorCode))
        }
    }
}