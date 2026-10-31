package com.routeprice.core.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Orçamento persistido. O conteúdo completo fica em [payload] (JSON do TripQuote, autocontido);
 * as demais colunas existem para ordenação, busca e listagem rápidas.
 */
@Entity(tableName = "quotes", indices = [Index("createdAt"), Index("status")])
data class QuoteEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    /** Texto normalizado (sem acentos, minúsculo) com origem, paradas, destino e passageiro. */
    val searchText: String,
    val finalPriceCents: Long,
    val payload: String,
    val schemaVersion: Int = 1,
)

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val payload: String,
    val updatedAt: Long,
)

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE searchText LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE id = :id")
    fun observe(id: String): Flow<QuoteEntity?>

    @Query("SELECT * FROM quotes WHERE id = :id")
    suspend fun get(id: String): QuoteEntity?

    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    suspend fun all(): List<QuoteEntity>

    @Upsert
    suspend fun upsert(entity: QuoteEntity)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun get(id: String): VehicleEntity?

    @Upsert
    suspend fun upsert(entity: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [QuoteEntity::class, VehicleEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao
    abstract fun vehicleDao(): VehicleDao

    companion object {
        const val NAME = "route-price.db"
    }
}
