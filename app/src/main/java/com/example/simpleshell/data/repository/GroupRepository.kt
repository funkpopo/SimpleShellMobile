package com.example.simpleshell.data.repository

import com.example.simpleshell.data.local.database.GroupDao
import com.example.simpleshell.data.local.database.entity.GroupEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val groupDao: GroupDao
) {
    fun getAllGroups(): Flow<List<GroupEntity>> = groupDao.getAllGroups()

    suspend fun getGroupById(id: Long): GroupEntity? = groupDao.getGroupById(id)

    /**
     * Returns an existing group's id if present, otherwise creates it.
     *
     * @return Pair(groupId, createdNew)
     */
    suspend fun getOrCreateGroupId(name: String): Pair<Long, Boolean> {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Group name is blank" }

        val existingId = groupDao.getGroupIdByName(trimmed)
        if (existingId != null) return existingId to false

        val id = groupDao.insertGroup(
            GroupEntity(name = trimmed)
        )
        return id to true
    }

    suspend fun createGroup(name: String): Long {
        return groupDao.insertGroup(
            GroupEntity(name = name.trim())
        )
    }

    suspend fun renameGroup(groupId: Long, newName: String) {
        val group = groupDao.getGroupById(groupId) ?: return
        groupDao.updateGroup(group.copy(name = newName.trim()))
    }

    suspend fun deleteGroup(group: GroupEntity) {
        groupDao.deleteGroup(group)
    }

    suspend fun groupNameExists(name: String): Boolean {
        return groupDao.countByName(name.trim()) > 0
    }
}
