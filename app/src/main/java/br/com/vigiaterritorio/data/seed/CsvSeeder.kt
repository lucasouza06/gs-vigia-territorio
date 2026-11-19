package br.com.vigiaterritorio.data.seed

import android.content.res.AssetManager
import br.com.vigiaterritorio.data.local.ReadingEntity
import br.com.vigiaterritorio.data.local.TerritoryEntity
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Lê a base local (CSV em assets) e converte para entidades do Room.
 * Única classe que conhece o formato dos arquivos: trocar por dados abertos reais do INMET
 * significa mudar só este arquivo.
 */
class CsvSeeder(private val assets: AssetManager) {

    data class SeedData(val territories: List<TerritoryEntity>, val readings: List<ReadingEntity>)

    fun read(anchor: Instant): SeedData {
        val territories = lines(TERRITORIES).map { f ->
            TerritoryEntity(
                id = f[0],
                name = f[1],
                type = f[2],
                latitude = f[3].toDouble(),
                longitude = f[4].toDouble(),
            )
        }
        val readings = lines(READINGS).map { f ->
            ReadingEntity(
                territoryId = f[0],
                indicator = f[1],
                kind = f[2],
                timestamp = anchor.plus(Duration.ofHours(f[3].toLong())).toEpochMilli(),
                value = f[4].toDouble(),
                origin = f[5],
            )
        }
        require(territories.isNotEmpty() && readings.isNotEmpty()) { "Base local vazia ou corrompida." }
        return SeedData(territories, readings)
    }

    /** Lê o CSV ignorando o cabeçalho. Os arquivos não têm vírgulas dentro dos campos. */
    private fun lines(file: String): List<List<String>> =
        assets.open(file).bufferedReader(Charsets.UTF_8).useLines { seq ->
            seq.drop(1).filter { it.isNotBlank() }.map { it.split(',').map(String::trim) }.toList()
        }

    companion object {
        /** Aumente ao trocar os CSV: força uma nova carga na próxima abertura. */
        const val SEED_VERSION = 1
        private const val TERRITORIES = "territories.csv"
        private const val READINGS = "readings.csv"

        /** A base sintética é ancorada na hora cheia da carga (offset 0 = última observação). */
        fun anchorFor(now: Instant): Instant = now.truncatedTo(ChronoUnit.HOURS)
    }
}
