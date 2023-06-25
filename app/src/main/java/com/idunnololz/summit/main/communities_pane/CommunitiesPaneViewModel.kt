package com.idunnololz.summit.main.communities_pane

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idunnololz.summit.account.AccountInfoManager
import com.idunnololz.summit.api.dto.Community
import com.idunnololz.summit.databinding.CommunitiesPaneBinding
import com.idunnololz.summit.lemmy.CommunityRef
import com.idunnololz.summit.user.UserCommunitiesManager
import com.idunnololz.summit.user.UserCommunityItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommunitiesPaneViewModel @Inject constructor(
    private val communitiesPaneControllerFactory: CommunitiesPaneController.Factory,
    private val accountInfoManager: AccountInfoManager,
    private val userCommunitiesManager: UserCommunitiesManager,
) : ViewModel() {

    private var subscriptionCommunities: List<Community> = listOf()
    private var userCommunities: List<UserCommunityItem> = listOf()

    val communities = MutableLiveData<CommunityData?>(null)

    init {
        viewModelScope.launch {
            accountInfoManager.subscribedCommunities.collect {
                subscriptionCommunities = it

                updateCommunities()
            }
        }

        viewModelScope.launch {
            userCommunitiesManager.userCommunitiesChangedFlow.collect {
                userCommunities = userCommunitiesManager.getAllUserCommunities()

                updateCommunities()
            }
        }
    }

    fun createController(
        binding: CommunitiesPaneBinding,
        viewLifecycleOwner: LifecycleOwner,
        onCommunitySelected: OnCommunitySelected,
    ) =
        communitiesPaneControllerFactory.create(
            this,
            binding,
            viewLifecycleOwner,
            onCommunitySelected,
        )

    fun loadCommunities() {
        userCommunities = userCommunitiesManager.getAllUserCommunities()
        accountInfoManager.fetchAccountInfo()
        updateCommunities()
    }

    fun deleteUserCommunity(id: Long) {
        viewModelScope.launch {
            userCommunitiesManager.deleteUserCommunity(id)
        }
    }

    private fun updateCommunities() {
        communities.postValue(CommunityData(
            userCommunities = userCommunities,
            subscriptionCommunities = subscriptionCommunities,
        ))
    }

    class CommunityData(
        val subscriptionCommunities: List<Community>,
        val userCommunities: List<UserCommunityItem>,
    )
}