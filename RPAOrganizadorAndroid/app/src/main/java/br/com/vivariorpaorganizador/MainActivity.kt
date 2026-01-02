package br.com.vivariorpaorganizador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.vivariorpaorganizador.ui.AppRoot
import br.com.vivariorpaorganizador.ui.theme.RPATheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RPATheme {
                val vm: MainViewModel = viewModel()
                AppRoot(vm)
            }
        }
    }
}
