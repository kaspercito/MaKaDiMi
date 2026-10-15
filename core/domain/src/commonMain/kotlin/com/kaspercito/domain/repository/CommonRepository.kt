package com.kaspercito.domain.repository

import com.kaspercito.domain.data.entities.NotificationEntity
import com.kaspercito.domain.data.model.cookie.CookieItem
import com.kaspercito.domain.data.model.library.LibraryOverview
import com.kaspercito.domain.data.type.RecentlyType
import com.kaspercito.domain.manager.DataStoreManager
import kotlinx.coroutines.flow.Flow

interface CommonRepository {
    fun init(cookiePath: String, dataStoreManager: DataStoreManager, isDevBuild: Boolean = false)

    // Database
    fun closeDatabase()

    fun getDatabasePath(): String?

    suspend fun databaseDaoCheckpoint()

    // Recently data
    fun getAllRecentData(): Flow<List<RecentlyType>>

    /** Counts and newest artwork for the Your library cards; re-emits whenever any of them changes. */
    fun getLibraryOverview(): Flow<LibraryOverview>

    // Notifications
    suspend fun insertNotification(notificationEntity: NotificationEntity)

    suspend fun getAllNotifications(): Flow<List<NotificationEntity>?>

    suspend fun isNotificationExists(link: String): Boolean

    suspend fun deleteNotification(id: Long)

    suspend fun writeTextToFile(text: String, filePath: String): Boolean

    suspend fun getCookiesFromInternalDatabase(url: String, packageName: String): CookieItem
}