package com.idunnololz.summit.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.core.app.ActivityCompat.recreate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import com.idunnololz.summit.R
import com.idunnololz.summit.coroutine.CoroutineScopeFactory
import com.idunnololz.summit.util.BaseActivity
import com.idunnololz.summit.util.isLightTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences,
    private val coroutineScopeFactory: CoroutineScopeFactory,
) {

    private val coroutineScope = coroutineScopeFactory.create()

    val useMaterialYou = MutableStateFlow<Boolean>(preferences.isUseMaterialYou())
    val themeOverlayChanged = MutableSharedFlow<Unit>()

    var isLightTheme =
        when (preferences.getBaseTheme()) {
            BaseTheme.UseSystem -> context.isLightTheme()
            BaseTheme.Light -> true
            BaseTheme.Dark -> false
        }
        private set

    fun updateTextConfig() {
        isLightTheme =
            when (preferences.getBaseTheme()) {
                BaseTheme.UseSystem -> context.isLightTheme()
                BaseTheme.Light -> true
                BaseTheme.Dark -> false
            }
    }

    fun applyThemeFromPreferences() {
        val themeValue = when (preferences.getBaseTheme()) {
            BaseTheme.UseSystem -> MODE_NIGHT_FOLLOW_SYSTEM
            BaseTheme.Light -> MODE_NIGHT_NO
            BaseTheme.Dark -> MODE_NIGHT_YES
        }

        if (AppCompatDelegate.getDefaultNightMode() != themeValue) {
            AppCompatDelegate.setDefaultNightMode(themeValue)
        }

        coroutineScope.launch {
            useMaterialYou.emit(preferences.isUseMaterialYou())
        }
    }

    fun onThemeOverlayChanged() {
        coroutineScope.launch {
            themeOverlayChanged.emit(Unit)
            updateTextConfig()
        }
    }

    fun applyThemeForActivity(activity: BaseActivity) {
        if (preferences.isBlackTheme()) {
            if (activity.isLightTheme()) {
                activity.theme.applyStyle(R.style.OverlayThemeRegular, true)
            }
        } else {
            activity.theme.applyStyle(R.style.OverlayThemeRegular, true)
        }

        when (preferences.globalFontSize) {
            GlobalFontSizeId.Small ->
                activity.theme.applyStyle(R.style.TextStyle_Small, true)
            GlobalFontSizeId.Normal ->
                activity.theme.applyStyle(R.style.TextStyle, true)
            GlobalFontSizeId.Large ->
                activity.theme.applyStyle(R.style.TextStyle_Large, true)
            GlobalFontSizeId.ExtraLarge ->
                activity.theme.applyStyle(R.style.TextStyle_ExtraLarge, true)
        }

        activity.isMaterialYou = useMaterialYou.value
        if (activity.isMaterialYou) {
            DynamicColors.applyToActivityIfAvailable(activity)
        } else {
            // do nothing
        }

        when (preferences.colorScheme) {
            ColorSchemes.Blue -> {
                activity.theme.applyStyle(R.style.ThemeBlueOverlay, true)
            }
            ColorSchemes.Red -> {
                activity.theme.applyStyle(R.style.ThemeRedOverlay, true)
            }
            ColorSchemes.TalhaPurple -> {
                activity.theme.applyStyle(R.style.ThemeTalhaEPurpleOverlay, true)
            }
            ColorSchemes.TalhaGreen -> {
                activity.theme.applyStyle(R.style.ThemeTalhaEGreenOverlay, true)
            }
            else -> { /* do nothing */ }
        }

        when (preferences.globalFontColor) {
            GlobalFontColorId.Calm ->
                activity.theme.applyStyle(R.style.TextColor, true)
            GlobalFontColorId.HighContrast ->
                activity.theme.applyStyle(R.style.TextColor_HighContrast, true)
        }

        activity.lifecycleScope.launch(Dispatchers.Default) {
            useMaterialYou.collect {
                withContext(Dispatchers.Main) {
                    if (it != activity.isMaterialYou) {
                        recreate(activity)
                    }
                }
            }
        }
    }
}
