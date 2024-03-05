package com.idunnololz.summit

import com.idunnololz.summit.notifications.NotificationsManager
import com.idunnololz.summit.notifications.NotificationsUpdater
import com.idunnololz.summit.preferences.Preferences
import com.idunnololz.summit.preferences.ThemeManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun themeManager(): ThemeManager
    fun preferences(): Preferences
    fun notificationsManager(): NotificationsManager
    fun notificationsUpdaterFactory(): NotificationsUpdater.Factory
}
