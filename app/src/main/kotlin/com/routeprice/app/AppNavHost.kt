package com.routeprice.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.routeprice.feature.history.HistoryRoute
import com.routeprice.feature.history.historyScreen
import com.routeprice.feature.home.HomeNavigation
import com.routeprice.feature.home.HomeRoute
import com.routeprice.feature.home.homeScreen
import com.routeprice.feature.quote.PassengerQuoteRoute
import com.routeprice.feature.quote.QuoteNavigation
import com.routeprice.feature.quote.QuoteRoute
import com.routeprice.feature.quote.passengerQuoteScreen
import com.routeprice.feature.quote.quoteScreen
import com.routeprice.feature.settings.DriverProfileRoute
import com.routeprice.feature.settings.PricingSettingsRoute
import com.routeprice.feature.settings.SettingsRoute
import com.routeprice.feature.settings.VehiclesRoute
import com.routeprice.feature.settings.settingsScreens
import com.routeprice.feature.trip.TripPlannerRoute
import com.routeprice.feature.trip.tripPlannerScreen

private const val ANIM_MS = 260

/** Navegação do app: cada feature expõe suas rotas; aqui só ligamos uma à outra. */
@Composable
fun AppNavHost(nav: NavHostController = rememberNavController()) {
    fun openQuoteReplacingPlanner(id: String) {
        val previous = nav.previousBackStackEntry
        val editingThisQuote = previous?.destination?.hasRoute<QuoteRoute>() == true && previous.toRoute<QuoteRoute>().quoteId == id
        if (editingThisQuote) {
            // Volta ao orçamento que estava sendo editado; ele observa o banco e já mostra os novos valores.
            nav.popBackStack()
        } else {
            nav.navigate(QuoteRoute(id)) { popUpTo<TripPlannerRoute> { inclusive = true } }
        }
    }

    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = { slideInHorizontally(tween(ANIM_MS)) { it / 6 } + fadeIn(tween(ANIM_MS)) },
        exitTransition = { fadeOut(tween(ANIM_MS / 2)) },
        popEnterTransition = { fadeIn(tween(ANIM_MS)) },
        popExitTransition = { slideOutHorizontally(tween(ANIM_MS)) { it / 6 } + fadeOut(tween(ANIM_MS)) },
    ) {
        homeScreen(
            HomeNavigation(
                onNewTrip = { nav.navigate(TripPlannerRoute()) },
                onOpenQuote = { nav.navigate(QuoteRoute(it)) },
                onHistory = { nav.navigate(HistoryRoute) },
                onSettings = { nav.navigate(SettingsRoute) },
                onDriverProfile = { nav.navigate(DriverProfileRoute) },
                onVehicles = { nav.navigate(VehiclesRoute) },
                onPricing = { nav.navigate(PricingSettingsRoute) },
            ),
        )
        tripPlannerScreen(
            onBack = { nav.popBackStack() },
            onQuoteReady = ::openQuoteReplacingPlanner,
            onManageVehicles = { nav.navigate(VehiclesRoute) },
        )
        quoteScreen(
            QuoteNavigation(
                onBack = { nav.popBackStack() },
                onEditTrip = { nav.navigate(TripPlannerRoute(it)) },
                onPassengerView = { nav.navigate(PassengerQuoteRoute(it)) },
                onOpenQuote = { nav.navigate(QuoteRoute(it)) },
            ),
        )
        passengerQuoteScreen(onBack = { nav.popBackStack() })
        historyScreen(
            onBack = { nav.popBackStack() },
            onOpenQuote = { nav.navigate(QuoteRoute(it)) },
            onNewTrip = { nav.navigate(TripPlannerRoute()) },
        )
        settingsScreens(
            onBack = { nav.popBackStack() },
            onOpen = { route -> nav.navigate(route) },
        )
    }
}
