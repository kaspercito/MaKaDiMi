package com.kaspercito.domain.repository

import com.kaspercito.domain.data.model.update.UpdateData
import com.kaspercito.domain.utils.Resource
import kotlinx.coroutines.flow.Flow

interface UpdateRepository {
    fun checkForGithubReleaseUpdate(): Flow<Resource<UpdateData>>
    fun checkForFdroidUpdate(): Flow<Resource<UpdateData>>

    fun getFdroidSigningKeys(): Flow<Resource<List<String>>>
}