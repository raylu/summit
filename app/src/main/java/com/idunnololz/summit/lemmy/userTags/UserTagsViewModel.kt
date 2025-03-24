package com.idunnololz.summit.lemmy.userTags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idunnololz.summit.util.StatefulLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class UserTagsViewModel @Inject constructor(
    private val userTagsManager: UserTagsManager,
) : ViewModel() {

    data class Model(
        val userTags: List<UserTag>,
    )

    val model = StatefulLiveData<Model>()

    init {
        viewModelScope.launch {
            userTagsManager.onChangedFlow.collect {
                refresh(force = true)
            }
        }

        viewModelScope.launch {
            refresh()
        }
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            refresh(force)
        }
    }

    private suspend fun refresh(force: Boolean = false) {
        if (model.valueOrNull != null && !force) {
            return
        }

        model.postIsLoading()

        withContext(Dispatchers.Default) {
            model.postValue(
                Model(
                    userTagsManager.getAllUserTags()
                        .map {
                            UserTag(
                                it.actorId,
                                it.tag,
                            )
                        },
                ),
            )
        }
    }

    fun deleteTag(personName: String?) {
        personName ?: return

        userTagsManager.deleteTag(personName)
    }
}
