package com.example.simpleshell.data.repository

import com.example.simpleshell.data.local.database.SnippetDao
import com.example.simpleshell.data.local.database.entity.SnippetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnippetRepository @Inject constructor(
    private val snippetDao: SnippetDao
) {
    fun getAllSnippets(): Flow<List<SnippetEntity>> {
        return snippetDao.getAllSnippets()
    }

    suspend fun insertSnippet(snippet: SnippetEntity): Long {
        return snippetDao.insertSnippet(snippet)
    }

    suspend fun updateSnippet(snippet: SnippetEntity) {
        snippetDao.updateSnippet(snippet)
    }

    suspend fun deleteSnippet(snippet: SnippetEntity) {
        snippetDao.deleteSnippet(snippet)
    }
}
