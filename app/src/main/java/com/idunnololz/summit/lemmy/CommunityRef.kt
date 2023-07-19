package com.idunnololz.summit.lemmy

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import com.idunnololz.summit.R
import com.idunnololz.summit.account.info.AccountSubscription
import com.idunnololz.summit.api.dto.Community
import com.idunnololz.summit.api.utils.instance
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import kotlinx.parcelize.Parcelize
import java.util.Locale

@JsonClass(generateAdapter = true, generator = "sealed:t")
sealed interface CommunityRef : PageRef, Parcelable {

//    @Parcelize
//    @JsonClass(generateAdapter = true)
//    @TypeLabel("1")
//    data class CommunityRefByObj(
//        val community: Community,
//        val instance: String?,
//    ) : CommunityRef {
//
//        fun getServerId(): String {
//            if (instance == null) {
//                return "${community.name}@"
//            }
//            return "${community.name}@${instance}"
//        }
//    }

    @Parcelize
    @JsonClass(generateAdapter = true)
    @TypeLabel("2")
    data class Local(
        val instance: String?
    ) : CommunityRef

    @Parcelize
    @JsonClass(generateAdapter = true)
    @TypeLabel("3")
    data class All(
        @Json(name = "site")
        val instance: String? = null
    ) : CommunityRef

    @Parcelize
    @JsonClass(generateAdapter = true)
    @TypeLabel("4")
    data class CommunityRefByName(
        val name: String,
        val instance: String?,
    ) : CommunityRef {

        val fullName: String
            get() {
                if (instance == null) {
                    return "${name}@"
                }
                return "${name}@${instance}"
            }

        fun getServerId(): String {
            if (instance == null) {
                return "${name}@"
            }
            return "${name}@${instance}"
        }
    }

    @Parcelize
    @JsonClass(generateAdapter = true)
    @TypeLabel("5")
    data class Subscribed(
        val instance: String?,
    ) : CommunityRef

    fun getName(context: Context): String =
        when (this) {
            is Local -> context.getString(R.string.local)
            is All -> context.getString(R.string.all)
            is CommunityRefByName -> this.name
            is Subscribed -> context.getString(R.string.subscribed)
        }

    fun getKey(): String =
        when (this) {
            is Local ->
                if (this.instance != null) {
                    "local@${this.instance}"
                } else {
                    "local"
                }
            is All ->
                if (this.instance != null) {
                    "all@${this.instance}"
                } else {
                    "all"
                }
            is CommunityRefByName -> this.fullName
            is Subscribed ->
                if (this.instance != null) {
                    "subscribed@${this.instance}"
                } else {
                    "subscribed"
                }
        }
}

fun Community.toCommunityRef(): CommunityRef.CommunityRefByName {
    return CommunityRef.CommunityRefByName(this.name, this.instance)
}

fun AccountSubscription.toCommunityRef(): CommunityRef.CommunityRefByName {
    val uri = Uri.parse(this.actorId)
    return CommunityRef.CommunityRefByName(this.name, uri.host)
}