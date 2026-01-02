package br.com.vivariorpaorganizador

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.vivariorpaorganizador.data.*
import br.com.vivariorpaorganizador.ocr.Extractors
import br.com.vivariorpaorganizador.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = OcrEngine(app.applicationContext)

    private val _state = MutableStateFlow(Storage.load(app.applicationContext))
    val state: StateFlow<AppState> = _state

    // Mensagens rápidas (snackbar/toast) para a UI.
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage

    fun clearUiMessage() { _uiMessage.value = null }

    private fun persist() {
        Storage.save(getApplication<Application>().applicationContext, _state.value)
    }

    fun addProfessional(nome: String, funcao: String) {
        val p = Professional(id = newId(), nome = nome.trim(), funcao = funcao)
        _state.update { it.copy(professionals = (it.professionals + p).toMutableList()) }
        persist()
    }

    fun updateProfessional(prof: Professional) {
        _state.update { st ->
            val list = st.professionals.map { if (it.id == prof.id) prof else it }.toMutableList()
            st.copy(professionals = list)
        }
        persist()
    }

    fun deleteProfessional(profId: String) {
        _state.update { st ->
            st.copy(
                professionals = st.professionals.filterNot { it.id == profId }.toMutableList(),
                attachments = st.attachments.filterNot { it.professionalId == profId }.toMutableList(),
                shifts = st.shifts.filterNot { it.professionalId == profId }.toMutableList()
            )
        }
        persist()
    }

    fun addShift(profId: String, dateIso: String, hours: Int, type: ShiftType) {
        val s = Shift(id = newId(), professionalId = profId, dateIso = dateIso, hours = hours, type = type)
        _state.update { st -> st.copy(shifts = (st.shifts + s).toMutableList()) }
        persist()
    }

    fun removeShift(shiftId: String) {
        _state.update { st -> st.copy(shifts = st.shifts.filterNot { it.id == shiftId }.toMutableList()) }
        persist()
    }

    fun upsertPriceRule(funcao: String, type: ShiftType, hours: Int, value: Double) {
        _state.update { st ->
            val existing = st.priceRules.find { it.funcao == funcao && it.type == type && it.hours == hours }
            val rules = st.priceRules.toMutableList()
            if (existing == null) {
                rules.add(PriceRule(id = newId(), funcao = funcao, type = type, hours = hours, value = value))
            } else {
                existing.value = value
            }
            st.copy(priceRules = rules)
        }
        persist()
    }

    /** Importa a tabela oficial que vai embutida no APK (asset). */
    fun importOfficialPriceTableFromAssets(replaceExisting: Boolean = true) {
        val ctx = getApplication<Application>().applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = ctx.assets.open("tabela_rpa_oficial.xlsx").use { it.readBytes() }
                val imported = XlsxPriceTableImporter.importBytes(bytes)
                if (imported.isEmpty()) {
                    _uiMessage.value = "Tabela oficial não retornou regras."
                    return@launch
                }
                applyImportedRules(imported, replaceExisting)
                _uiMessage.value = "Tabela oficial importada: ${imported.size} regras."
            } catch (e: Exception) {
                _uiMessage.value = "Falha ao importar tabela oficial: ${e.message ?: "erro"}"
            }
        }
    }

    /** Importa um XLSX escolhido pelo usuário. */
    fun importPriceTableFromFile(uri: Uri, replaceExisting: Boolean = true) {
        val ctx = getApplication<Application>().applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val imported = XlsxPriceTableImporter.import(ctx, uri)
                if (imported.isEmpty()) {
                    _uiMessage.value = "Não achei valores válidos nessa planilha."
                    return@launch
                }
                applyImportedRules(imported, replaceExisting)
                _uiMessage.value = "Tabela importada: ${imported.size} regras."
            } catch (e: Exception) {
                _uiMessage.value = "Falha ao importar XLSX: ${e.message ?: "erro"}"
            }
        }
    }

    private fun applyImportedRules(imported: List<ImportedPriceRule>, replaceExisting: Boolean) {
        _state.update { st ->
            val rules = if (replaceExisting) mutableListOf() else st.priceRules.toMutableList()

            fun upsert(funcao: String, type: ShiftType, hours: Int, value: Double) {
                val existing = rules.find { it.funcao == funcao && it.type == type && it.hours == hours }
                if (existing == null) {
                    rules.add(PriceRule(id = newId(), funcao = funcao, type = type, hours = hours, value = value))
                } else {
                    existing.value = value
                }
            }

            imported.forEach { r ->
                upsert(r.funcao, r.type, r.hours, r.value)
            }

            st.copy(priceRules = rules)
        }
        persist()
    }

    fun attachDocument(profId: String, category: DocCategory, uri: Uri, displayName: String, mimeType: String) {
        val att = Attachment(
            id = newId(),
            professionalId = profId,
            category = category,
            uri = uri.toString(),
            displayName = displayName,
            mimeType = mimeType
        )

        _state.update { st -> st.copy(attachments = (st.attachments + att).toMutableList()) }
        persist()

        // OCR async: atualiza texto do anexo e tenta preencher campos do profissional.
        viewModelScope.launch {
            val text = engine.recognizeText(uri)
            if (text.isBlank()) return@launch

            _state.update { st ->
                val atts = st.attachments.map {
                    if (it.id == att.id) it.copy(ocrText = text) else it
                }.toMutableList()

                val prof = st.professionals.find { it.id == profId }
                val profs = st.professionals.toMutableList()
                if (prof != null) {
                    val updated = prof.copy()
                    updated.extracted = Extractors.applyOcrTextToExtracted(updated.extracted, text, updated.nome)
                    val idx = profs.indexOfFirst { it.id == profId }
                    if (idx >= 0) profs[idx] = updated
                }

                st.copy(attachments = atts, professionals = profs)
            }
            persist()
        }
    }

    fun removeAttachment(attId: String) {
        _state.update { st -> st.copy(attachments = st.attachments.filterNot { it.id == attId }.toMutableList()) }
        persist()
    }
}
