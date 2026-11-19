package br.com.vigiaterritorio.domain

import br.com.vigiaterritorio.domain.model.Alert
import br.com.vigiaterritorio.domain.model.AlertLevel
import br.com.vigiaterritorio.domain.model.DataOrigin
import br.com.vigiaterritorio.domain.model.Decision
import br.com.vigiaterritorio.domain.model.DecisionAction
import br.com.vigiaterritorio.domain.model.DecisionRecord
import br.com.vigiaterritorio.domain.repository.DecisionRepository
import br.com.vigiaterritorio.domain.repository.TimeProvider
import br.com.vigiaterritorio.domain.usecase.DecisionValidationException
import br.com.vigiaterritorio.domain.usecase.RegisterDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RegisterDecisionTest {

    private class FakeDecisions : DecisionRepository {
        val saved = MutableStateFlow<List<DecisionRecord>>(emptyList())
        override fun observeHistory(): Flow<List<DecisionRecord>> = saved
        override fun observeDecisionFor(alertId: String): Flow<Decision?> =
            saved.map { list -> list.lastOrNull { it.decision.alertId == alertId }?.decision }
        override suspend fun save(alert: Alert, decision: Decision) {
            saved.value = saved.value + DecisionRecord(decision, alert)
        }
        override suspend fun clearAll() {
            saved.value = emptyList()
        }
    }

    private val now = Instant.parse("2026-11-18T20:15:00Z")
    private val repo = FakeDecisions()
    private val register = RegisterDecision(repo, TimeProvider { now })

    private fun alert(level: AlertLevel) = Alert(
        id = "t1-${level.name}-1", territoryId = "t1", territoryName = "Jardim das Águas", level = level,
        confidence = 0.8, title = "x", recommendation = "y", factors = emptyList(),
        periodStart = now, periodEnd = now, validUntil = now, origin = DataOrigin.SINTETICO,
        dataUpdatedAt = now, coverage24h = 1.0,
    )

    @Test
    fun `acatar alerta critico grava com responsavel e data`() = runTest {
        val result = register(alert(AlertLevel.CRITICO), DecisionAction.ACATAR, "  Ana Souza ", "")
        assertTrue(result.isSuccess)
        val saved = repo.saved.value.single().decision
        assertEquals("Ana Souza", saved.responsible)
        assertEquals(now, saved.decidedAt)
        assertEquals(DecisionAction.ACATAR, saved.action)
    }

    @Test
    fun `descartar alerta critico sem observacao e recusado`() = runTest {
        val result = register(alert(AlertLevel.CRITICO), DecisionAction.DESCARTAR, "Ana", " ")
        assertTrue(result.exceptionOrNull() is DecisionValidationException)
        assertTrue(repo.saved.value.isEmpty())
    }

    @Test
    fun `adiar alerta de atencao sem observacao e aceito`() = runTest {
        assertTrue(register(alert(AlertLevel.ATENCAO), DecisionAction.ADIAR, "Ana", "").isSuccess)
    }

    @Test
    fun `responsavel em branco e recusado`() = runTest {
        val result = register(alert(AlertLevel.INFORMATIVO), DecisionAction.ACATAR, "", "ok")
        assertTrue(result.exceptionOrNull() is DecisionValidationException)
    }
}
