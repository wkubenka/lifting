package com.astute.body.data.repository

import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.entity.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dao: UserPreferencesDao
) {
    val preferences: Flow<UserPreferencesEntity> = dao.get().map { it ?: UserPreferencesEntity() }
}
