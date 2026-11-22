package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val storyPrompt: String,
    val styleName: String,
    val aspectRatioName: String,
    val targetDurationMinutes: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "characters",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val visualDescription: String,
    val outfit: String,
    val voiceStyle: String = "स्पष्ट हिंदी संवाद (Clear Hindi)",
    val referenceImageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "scenes",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class SceneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val sceneNumber: Int,
    val videoPrompt: String,
    val characterDescription: String,
    val linkedCharacterIdsCsv: String = "",
    val hindiDialogue: String,
    val cameraAngle: String,
    val background: String,
    val durationSeconds: Int = 8,
    val generationMode: String = "TEXT_TO_VIDEO",
    val referenceImageUri: String? = null,
    val jobStatus: String = "IDLE",
    val providerJobId: String? = null,
    val statusMessage: String = "",
    val videoUri: String? = null,
    val voiceAudioUri: String? = null,
    val errorMessage: String? = null,
    val isAiPlaceholder: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
