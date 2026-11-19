package br.com.vigiaterritorio.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Entidades do Room ficam na camada de dados. O domínio nunca as enxerga (há mapeadores).

@Entity(tableName = "territories")
data class TerritoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
)

@Entity(
    tableName = "readings",
    indices = [Index(value = ["territoryId", "indicator", "timestamp"])],
)
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val territoryId: String,
    val indicator: String,
    val kind: String,
    val timestamp: Long,
    val value: Double,
    val origin: String,
)

/** Retrato do alerta no momento da decisão (o que o gestor viu). */
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val territoryId: String,
    val territoryName: String,
    val level: String,
    val confidence: Double,
    val title: String,
    val recommendation: String,
    /** Linhas da justificativa serializadas (ver [AlertFactorCodec]). */
    val justification: String,
    val periodStart: Long,
    val periodEnd: Long,
    val validUntil: Long,
    val origin: String,
    val dataUpdatedAt: Long,
    val coverage24h: Double,
)

@Entity(tableName = "decisions", indices = [Index("alertId")])
data class DecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alertId: String,
    val territoryId: String,
    val action: String,
    val responsible: String,
    val note: String,
    val decidedAt: Long,
)

data class DecisionWithAlert(
    @Embedded val decision: DecisionEntity,
    @Relation(parentColumn = "alertId", entityColumn = "id")
    val alert: AlertEntity,
)

@Dao
interface ReadingDao {
    @Query("SELECT * FROM territories ORDER BY name")
    fun observeTerritories(): Flow<List<TerritoryEntity>>

    @Query("SELECT * FROM readings WHERE territoryId = :territoryId ORDER BY timestamp")
    fun observeReadings(territoryId: String): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM readings WHERE territoryId = :territoryId ORDER BY timestamp")
    suspend fun readings(territoryId: String): List<ReadingEntity>

    @Query("SELECT COUNT(*) FROM readings")
    suspend fun countReadings(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTerritories(items: List<TerritoryEntity>)

    @Insert
    suspend fun insertReadings(items: List<ReadingEntity>)

    @Query("DELETE FROM readings")
    suspend fun clearReadings()

    @Query("DELETE FROM territories")
    suspend fun clearTerritories()

    /** Carga atômica: ou entra tudo, ou nada (protege contra gravação parcial). */
    @Transaction
    suspend fun replaceAll(territories: List<TerritoryEntity>, readings: List<ReadingEntity>) {
        clearReadings()
        clearTerritories()
        insertTerritories(territories)
        insertReadings(readings)
    }
}

@Dao
interface DecisionDao {
    @Transaction
    @Query("SELECT * FROM decisions ORDER BY decidedAt DESC")
    fun observeHistory(): Flow<List<DecisionWithAlert>>

    @Query("SELECT * FROM decisions WHERE alertId = :alertId ORDER BY decidedAt DESC LIMIT 1")
    fun observeLatestFor(alertId: String): Flow<DecisionEntity?>

    @Upsert
    suspend fun upsertAlert(alert: AlertEntity)

    @Insert
    suspend fun insertDecision(decision: DecisionEntity): Long

    @Transaction
    suspend fun saveDecision(alert: AlertEntity, decision: DecisionEntity) {
        upsertAlert(alert)
        insertDecision(decision)
    }

    @Query("DELETE FROM decisions")
    suspend fun clearDecisions()

    @Query("DELETE FROM alerts")
    suspend fun clearAlerts()

    @Transaction
    suspend fun clearAll() {
        clearDecisions()
        clearAlerts()
    }
}

@Database(
    entities = [TerritoryEntity::class, ReadingEntity::class, AlertEntity::class, DecisionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class VigiaDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao
    abstract fun decisionDao(): DecisionDao
}
