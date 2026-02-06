package com.example.simpleshell.data.repository

import com.example.simpleshell.data.local.database.CommandHistoryDao
import com.example.simpleshell.data.local.database.entity.CommandHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandHistoryRepository @Inject constructor(
    private val commandHistoryDao: CommandHistoryDao
) {
    fun observeAll(): Flow<List<CommandHistoryEntity>> = commandHistoryDao.observeAll()

    suspend fun getAll(): List<CommandHistoryEntity> = commandHistoryDao.getAll()

    suspend fun replaceAll(history: List<CommandHistoryEntity>) {
        commandHistoryDao.clearAll()
        if (history.isNotEmpty()) {
            commandHistoryDao.upsertAll(history)
        }
    }

    suspend fun clearAll() {
        commandHistoryDao.clearAll()
    }
}
