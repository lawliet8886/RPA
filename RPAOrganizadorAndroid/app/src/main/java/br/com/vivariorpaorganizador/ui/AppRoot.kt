package br.com.vivariorpaorganizador.ui

import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.vivariorpaorganizador.MainViewModel

object Routes {
    const val HOME = "home"
    const val ADD = "add"
    const val PRICES = "prices"
    const val DETAIL = "detail"
}

@Composable
fun AppRoot(vm: MainViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(vm,
                onAdd = { nav.navigate(Routes.ADD) },
                onOpenPrices = { nav.navigate(Routes.PRICES) },
                onOpenProfessional = { id -> nav.navigate("${Routes.DETAIL}/$id") }
            )
        }
        composable(Routes.ADD) {
            AddProfessionalScreen(vm,
                onDone = { nav.popBackStack() }
            )
        }
        composable(Routes.PRICES) {
            PricesScreen(vm,
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            route = "${Routes.DETAIL}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            ProfessionalDetailScreen(vm, profId = id, onBack = { nav.popBackStack() })
        }
    }
}
