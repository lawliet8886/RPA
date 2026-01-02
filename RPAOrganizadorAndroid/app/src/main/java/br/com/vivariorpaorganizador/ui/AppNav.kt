package br.com.vivariorpaorganizador.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.vivariorpaorganizador.vm.MainViewModel

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()
    val state by vm.state.collectAsState()

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                state = state,
                onOpenProfessional = { nav.navigate("prof/$it") },
                onAddProfessional = vm::addProfessional,
                onOpenPrices = { nav.navigate("prices") }
            )
        }
        composable(
            route = "prof/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("id") ?: return@composable
            ProfessionalScreen(
                state = state,
                profId = id,
                onBack = { nav.popBackStack() },
                onAttachDoc = vm::attachDoc,
                onAddShift = vm::addShift,
                onValidate = { vm.validate(id) },
                onExportZip = vm::exportZip
            )
        }
        composable("prices") {
            PricesScreen(
                state = state,
                onBack = { nav.popBackStack() },
                onSave = vm::updatePriceTable
            )
        }
    }
}
