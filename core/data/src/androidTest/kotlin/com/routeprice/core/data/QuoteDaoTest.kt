package com.routeprice.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.routeprice.core.data.local.AppDatabase
import com.routeprice.core.data.repository.RoomQuoteRepository
import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.QuoteStatus
import com.routeprice.core.domain.model.RouteSummary
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.pricing.PricingEngine
import com.routeprice.core.domain.usecase.BuildQuoteUseCase
import com.routeprice.core.domain.usecase.QuoteRequest
import com.routeprice.core.domain.usecase.SystemTimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Testes do Room em aparelho/emulador (rodam no workflow de testes instrumentados). */
@RunWith(AndroidJUnit4::class)
class QuoteDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomQuoteRepository
    private val build = BuildQuoteUseCase(PricingEngine(), SystemTimeProvider())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = RoomQuoteRepository(db.quoteDao())
    }

    @After
    fun tearDown() = db.close()

    private fun quote(o: String, d: String) = build(
        QuoteRequest(PlaceRef(o), PlaceRef(d), emptyList(), RouteSummary(10_000, 900, source = ValueSource.EXTERNAL), null),
        PricingSettings(),
        DriverProfile(),
    )

    @Test
    fun persisteBuscaAtualizaEExclui() = runTest {
        val a = quote("Sorocaba", "São Paulo")
        val b = quote("Campinas", "Jundiaí")
        repo.upsert(a)
        repo.upsert(b)
        assertEquals(a, repo.get(a.id))
        assertEquals(listOf(a.id), repo.search("SAO PAULO").first().map { it.id })
        repo.upsert(a.copy(status = QuoteStatus.ACCEPTED))
        assertEquals(QuoteStatus.ACCEPTED, repo.get(a.id)!!.status)
        repo.delete(a.id)
        assertNull(repo.get(a.id))
        assertEquals(1, repo.observeRecent(10).first().size)
    }
}
