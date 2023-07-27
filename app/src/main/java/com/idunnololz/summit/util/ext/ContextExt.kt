package com.idunnololz.summit.util.ext

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat

fun Context.getColorFromAttribute(attribute: Int): Int {
    val attributes = obtainStyledAttributes(intArrayOf(attribute))
    val color = attributes.getColor(0, 0)
    attributes.recycle()
    return color
}

fun Context.getDimenFromAttribute(attribute: Int): Float {
    val attributes = obtainStyledAttributes(intArrayOf(attribute))
    val dimen = attributes.getDimension(0, 0f)
    attributes.recycle()
    return dimen
}

fun Context.getResIdFromAttribute(attribute: Int): Int {
    val attributes = obtainStyledAttributes(intArrayOf(attribute))
    val resourceId = attributes.getResourceId(0, 0)
    attributes.recycle()
    return resourceId
}

fun Context.getDimen(@DimenRes dimen: Int): Int {
    return resources.getDimension(dimen).toInt()
}

fun Context.getColorCompat(@ColorRes color: Int): Int =
    ContextCompat.getColor(this, color)

fun Context.getDrawableCompat(@DrawableRes drawableRes: Int): Drawable? =
    AppCompatResources.getDrawable(this, drawableRes)
