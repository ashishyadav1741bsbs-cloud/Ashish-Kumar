package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeProject(projectId: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    @Query("SELECT * FROM characters WHERE projectId = :projectId ORDER BY id ASC")
    fun observeCharacters(projectId: Long): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE projectId = :projectId ORDER BY id ASC")
    suspend fun getCharactersOnce(projectId: Long): List<CharacterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CharacterEntity): Long

    @Update
    suspend fun updateCharacter(character: CharacterEntity)

    @Query("DELETE FROM characters WHERE id = :characterId")
    suspend fun deleteCharacterById(characterId: Long)

    @Query("SELECT * FROM scenes WHERE projectId = :projectId ORDER BY sceneNumber ASC, id ASC")
    fun observeScenes(projectId: Long): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes WHERE projectId = :projectId ORDER BY sceneNumber ASC, id ASC")
    suspend fun getScenesOnce(projectId: Long): List<SceneEntity>

    @Query("SELECT * FROM scenes WHERE id = :sceneId")
    suspend fun getSceneById(sceneId: Long): SceneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScene(scene: SceneEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScenes(scenes: List<SceneEntity>)

    @Update
    suspend fun updateScene(scene: SceneEntity)

    @Query(
        "UPDATE scenes SET jobStatus = :jobStatus, providerJobId = COALESCE(:providerJobId, providerJobId), " +
            "statusMessage = :statusMessage, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :sceneId"
    )
    suspend fun updateSceneJobStatus(
        sceneId: Long,
        jobStatus: String,
        providerJobId: String?,
        statusMessage: String,
        errorMessage: String?,
        updatedAt: Long
    )

    @Query(
        "UPDATE scenes SET jobStatus = :jobStatus, providerJobId = COALESCE(:providerJobId, providerJobId), " +
            "statusMessage = :statusMessage, videoUri = :videoUri, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :sceneId"
    )
    suspend fun updateSceneVideoResult(
        sceneId: Long,
        jobStatus: String,
        providerJobId: String?,
        statusMessage: String,
        videoUri: String,
        errorMessage: String?,
        updatedAt: Long
    )

    @Query("UPDATE scenes SET voiceAudioUri = :voiceAudioUri, updatedAt = :updatedAt WHERE id = :sceneId")
    suspend fun updateSceneVoiceResult(sceneId: Long, voiceAudioUri: String, updatedAt: Long)

    @Query("UPDATE scenes SET referenceImageUri = :referenceImageUri, updatedAt = :updatedAt WHERE id = :sceneId")
    suspend fun updateSceneReferenceImage(sceneId: Long, referenceImageUri: String, updatedAt: Long)

    @Query("DELETE FROM scenes WHERE id = :sceneId")
    suspend fun deleteSceneById(sceneId: Long)

    @Query("DELETE FROM scenes WHERE projectId = :projectId")
    suspend fun deleteScenesForProject(projectId: Long)

    @Transaction
    suspend fun replaceProjectScenes(projectId: Long, newScenes: List<SceneEntity>) {
        deleteScenesForProject(projectId)
        insertScenes(newScenes)
    }
}
