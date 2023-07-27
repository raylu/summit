package com.idunnololz.summit.lemmy.communityInfo

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.idunnololz.summit.account.info.AccountInfoManager
import com.idunnololz.summit.api.AccountAwareLemmyClient
import com.idunnololz.summit.api.dto.CommunityView
import com.idunnololz.summit.api.dto.GetCommunityResponse
import com.idunnololz.summit.api.dto.GetSiteResponse
import com.idunnololz.summit.lemmy.CommunityRef
import com.idunnololz.summit.lemmy.toCommunityRef
import com.idunnololz.summit.util.Event
import com.idunnololz.summit.util.StatefulLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommunityInfoViewModel @Inject constructor(
    private val apiClient: AccountAwareLemmyClient,
    private val accountInfoManager: AccountInfoManager,
) : ViewModel() {

    companion object {
        private const val TAG = "CommunityInfoViewModel"
    }

    private var communityRef: CommunityRef? = null

    val siteOrCommunity = StatefulLiveData<Either<GetSiteResponse, GetCommunityResponse>>()
    private val subscribeEvent = MutableLiveData<Event<Result<CommunityView>>>()

    val instance
        get() = apiClient.instance

    private fun fetchCommunityOrSiteInfo(communityRef: CommunityRef, force: Boolean = false) {
        siteOrCommunity.setIsLoading()
        viewModelScope.launch {
            val result = when (communityRef) {
                is CommunityRef.All -> {
                    Either.Left(apiClient.fetchSiteWithRetry(force))
                }
                is CommunityRef.CommunityRefByName -> {
                    Either.Right(apiClient.fetchCommunityWithRetry(Either.Right(communityRef.getServerId()), force))
                }
                is CommunityRef.Local -> {
                    Either.Left(apiClient.fetchSiteWithRetry(force))
                }
                is CommunityRef.Subscribed -> {
                    Either.Left(apiClient.fetchSiteWithRetry(force))
                }
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
                            siteOrCommunity.postValue(Either.Right(it))
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
                        siteOrCommunity.valueOrNull?.getOrNull()?.copy(
                            community_view = it,
                        )?.let {
                            siteOrCommunity.postValue(Either.Right(it))
                        }
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
