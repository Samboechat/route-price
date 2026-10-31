package com.routeprice.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.routeprice.core.data.local.EntityMapper
import com.routeprice.core.data.repository.DataStoreSettingsRepository
import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.FuelType
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PricingModel
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.Vehicle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal

/** Persistência testada na JVM: serialização do histórico e DataStore real em arquivo temporário. */
class LocalStorageTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `orcamento completo sobrevive a serializacao`() {
        val q = TestQuotes.quote("Sorocaba", "São Paulo", "Maria")
        val entity = EntityMapper.toEntity(q)
        assertEquals(q, EntityMapper.fromEntity(entity))
        assertEquals(q.finalPrice.cents, entity.finalPriceCents)
        assertTrue(entity.searchText.contains("sao paulo"))
        assertTrue(entity.searchText.contains("maria"))
        assertTrue(entity.searchText.contains("parada unica"))
    }

    @Test
    fun `payload corrompido e ignorado sem derrubar o app`() {
        val entity = EntityMapper.toEntity(TestQuotes.quote("A", "B")).copy(payload = "{quebrado")
        assertNull(EntityMapper.fromEntity(entity))
    }

    @Test
    fun `veiculo ida e volta`() {
        val v = Vehicle("v1", "Corolla", fuelType = FuelType.ETHANOL, consumptionKmPerUnit = BigDecimal("8.5"))
        assertEquals(v, EntityMapper.fromEntity(EntityMapper.toEntity(v, 0L)))
    }

    @Test
    fun `configuracoes em DataStore`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("s.preferences_pb") }
        val repo = DataStoreSettingsRepository(store)
        assertEquals(PricingSettings(), repo.pricingSettings.first())
        repo.updatePricingSettings {
            it.copy(
                model = PricingModel.OPERATING_COST,
                reviewedByDriver = true,
                cost = it.cost.copy(energyPrices = mapOf(FuelType.GASOLINE to BigDecimal("6.19"))),
            )
        }
        val saved = repo.pricingSettings.first()
        assertEquals(PricingModel.OPERATING_COST, saved.model)
        assertTrue(saved.reviewedByDriver)
        assertEquals(BigDecimal("6.19"), saved.cost.energyPrices[FuelType.GASOLINE])
        repo.updateDriverProfile { it.copy(name = "Samuel") }
        assertEquals(DriverProfile(name = "Samuel"), repo.driverProfile.first())
    }

    @Test
    fun `normalizacao para busca`() {
        assertEquals("sao paulo jundiai", EntityMapper.normalize(listOf("São Paulo", "JUNDIAÍ")))
        assertEquals(Money.ZERO, Money.sum(emptyList()))
    }
}
