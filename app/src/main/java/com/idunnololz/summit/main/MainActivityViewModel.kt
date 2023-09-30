package com.idunnololz.summit.main

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.idunnololz.summit.account.AccountManager
import com.idunnololz.summit.account.AccountView
import com.idunnololz.summit.account.info.AccountInfoManager
import com.idunnololz.summit.api.AccountAwareLemmyClient
import com.idunnololz.summit.api.dto.CommunityView
import com.idunnololz.summit.api.dto.GetSiteResponse
import com.idunnololz.summit.api.dto.ListingType
import com.idunnololz.summit.api.dto.SortType
import com.idunnololz.summit.lemmy.CommunityRef
import com.idunnololz.summit.lemmy.toCommunityRef
import com.idunnololz.summit.offline.OfflineManager
import com.idunnololz.summit.user.UserCommunitiesManager
import com.idunnololz.summit.util.Event
import com.idunnololz.summit.util.StatefulLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val apiClient: AccountAwareLemmyClient,
    private val accountManager: AccountManager,
    private val offlineManager: OfflineManager,
    private val accountInfoManager: AccountInfoManager,
    val communitySelectorControllerFactory: CommunitySelectorController.Factory,
    val userCommunitiesManager: UserCommunitiesManager,
) : ViewModel() {

    companion object {
        private val TAG = "MainActivityViewModel"
    }
    private val readyCount = MutableStateFlow<Int>(0)

    val communities = StatefulLiveData<List<CommunityView>>()
    val currentAccount = MutableLiveData<AccountView?>(null)
    val unreadCount = accountInfoManager.unreadCount.asLiveData()

    private var communityRef: CommunityRef? = null

    val siteOrCommunity = StatefulLiveData<Either<GetSiteResponse, CommunityView>>()
    private val subscribeEvent = MutableLiveData<Event<Result<CommunityView>>>()

    val isReady = MutableLiveData<Boolean>(false)

    val currentInstance: String
        get() = apiClient.instance

    fun Flow<Int>.completeWhenDone(): Flow<Int> =
        transformWhile { readyCount ->
            emit(readyCount) // always emit progress

            readyCount < 2
        }

    init {
        viewModelScope.launch {
            launch {
                readyCount
                    .completeWhenDone()
                    .collect {}

                Log.d(TAG, "All ready!")
                isReady.postValue(true)
            }

            launch {
                accountManager.currentAccount.collect {
                    val accountView = if (it != null) {
                        accountInfoManager.getAccountViewForAccount(it)
                    } else {
                        null
                    }

                    currentAccount.postValue(accountView)

                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "'accountManager' ready!")
                        readyCount.emit(readyCount.value + 1)
                    }
                }
            }

            launch {
                while (true) {
                    Log.d(TAG, "Waiting for 'userCommunitiesManager'")
                    val mainTab = userCommunitiesManager.waitForTab(
                        UserCommunitiesManager.FIRST_FRAGMENT_TAB_ID,
                    )

                    if (mainTab != null) {
                        break
                    }
                }

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "'userCommunitiesManager' ready!")
                    readyCount.emit(readyCount.value + 1)
                }
            }

            launch {
                accountManager.currentAccount.collect {
                    loadCommunities()
                }
            }

            launch {
                withContext(Dispatchers.IO) {
                    offlineManager.cleanup()
                }
            }
        }
    }

    fun loadCommunities(force: Boolean = false) {
        communities.setIsLoading()
        viewModelScope.launch {
            apiClient.fetchCommunitiesWithRetry(
                sortType = SortType.TopAll,
                listingType = ListingType.All,
            ).onSuccess {
                communities.postValue(it)
            }.onFailure {
                communities.postError(it)
            }
        }
    }

    fun updateUnreadCount() {
        accountInfoManager.updateUnreadCount()
    }

    private fun fetchCommunityOrSiteInfo(communityRef: CommunityRef, force: Boolean = false) {
        siteOrCommunity.setIsLoading()
        viewModelScope.launch {
            val result = when (communityRef) {
                is CommunityRef.All -> {
                    Either.Left(apiClient.fetchSiteWithRetry(force))
                }
                is CommunityRef.CommunityRefByName -> {
                    Either.Right(
                        apiClient.fetchCommunityWithRetry(
                            Either.Right(communityRef.getServerId(currentInstance)),
                            force,
                        ),
                    )
                }
                is CommunityRef.Local -> {
                    Either.Left(apiClient.fetchSiteWithRetry(force))
                }
                is CommunityRef.Subscribed -> {
                    Either.Left(apiClient.fetchSiteWithRetry(force))
                }
                is CommunityRef.MultiCommunity ->
                    Either.Left(Result.failure(RuntimeException()))
                is CommunityRef.ModeratedCommunities ->
                    Either.Left(Result.failure(RuntimeException()))
            }

            result
                .onLeft {
                    it
                        .onSuccess {
                            siteOrCommunity.postValue(Either.Left(it))
                        }
                        .onFailure {
                            siteOrCommunity.postError(it)
                        }
                }
                .onRight {
                    it
                        .onSuccess {
                            siteOrCommunity.postValue(Either.Right(it.community_view))
                        }
                        .onFailure {
                            siteOrCommunity.postError(it)
                        }
                }
        }
    }

    fun updateSubscriptionStatus(communityId: Int, subscribe: Boolean) {
        viewModelScope.launch {
            val result = apiClient.followCommunityWithRetry(communityId, subscribe)

            subscribeEvent.postValue(Event(result))
            result
                .onSuccess {
                    if (communityRef == it.community.toCommunityRef()) {
                        siteOrCommunity.postValue(Either.Right(it))
                    }

                    delay(1000)

                    refetchCommunityOrSite(force = true)
                    accountInfoManager.refreshAccountInfo()

                    Log.d(TAG, "subscription status: " + it.subscribed)
                }
        }
    }

    fun onCommunityChanged(communityRef: CommunityRef) {
        this.communityRef = communityRef

        fetchCommunityOrSiteInfo(communityRef)
    }

    fun refetchCommunityOrSite(force: Boolean) {
        val communityRef = communityRef ?: return
        fetchCommunityOrSiteInfo(communityRef, force)
    }
}
