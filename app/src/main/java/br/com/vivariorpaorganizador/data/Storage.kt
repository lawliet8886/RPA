package br.com.vivariorpaorganizador.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

object Storage {
    private const val FILE_NAME = "app_state.json"

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    fun load(context: Context): AppState {
        return try {
            val f = File(context.filesDir, FILE_NAME)
            if (!f.exists()) return AppState()
            val txt = f.readText(Charsets.UTF_8)
            gson.fromJson(txt, AppState::class.java) ?: AppState()
        } catch (e: Exception) {
            AppState()
        }
    }

    fun save(context: Context, state: AppState) {
        try {
            val f = File(context.filesDir, FILE_NAME)
            f.writeText(gson.toJson(state), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
}
