package com.example.localllm.data.db.dao

import androidx.room.*
import com.example.localllm.data.db.entity.BenchmarkResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BenchmarkDao {

    @Query("SELECT * FROM benchmark_results ORDER BY runAt DESC")
    fun getAllResults(): Flow<List<BenchmarkResultEntity>>

    @Query("SELECT * FROM benchmark_results WHERE modelId = :modelId ORDER BY runAt DESC LIMIT 10")
    fun getResultsForModel(modelId: String): Flow<List<BenchmarkResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: BenchmarkResultEntity): Long

    @Query("DELETE FROM benchmark_results WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM benchmark_results WHERE modelId = :modelId")
    suspend fun deleteAllForModel(modelId: String)

    @Query("SELECT AVG(tokensPerSecond) FROM benchmark_results WHERE modelId = :modelId")
    suspend fun getAverageTokensPerSecond(modelId: String): Double?
}
