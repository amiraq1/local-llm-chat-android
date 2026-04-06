package com.example.localllm.data.db.dao

import androidx.room.*
import com.example.localllm.data.db.entity.InstalledModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    @Query("SELECT * FROM installed_models ORDER BY installedAt DESC")
    fun getAllInstalledModels(): Flow<List<InstalledModelEntity>>

    @Query("SELECT * FROM installed_models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): InstalledModelEntity?

    @Query("SELECT * FROM installed_models WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveModel(): InstalledModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: InstalledModelEntity)

    @Update
    suspend fun update(model: InstalledModelEntity)

    @Query("UPDATE installed_models SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE installed_models SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Query("UPDATE installed_models SET checksumVerified = :verified WHERE id = :id")
    suspend fun setChecksumVerified(id: String, verified: Boolean)

    @Query("DELETE FROM installed_models WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT SUM(sizeBytes) FROM installed_models")
    suspend fun getTotalInstalledSizeBytes(): Long?
}
