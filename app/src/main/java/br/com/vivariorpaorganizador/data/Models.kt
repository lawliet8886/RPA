package br.com.vivariorpaorganizador.data

import java.time.LocalDate

/**
 * AppState é salvo em JSON (Gson). Mantemos tudo simples e auditável.
 */
data class AppState(
    val version: String = "0.2.0",
    val professionals: MutableList<Professional> = mutableListOf(),
    val attachments: MutableList<Attachment> = mutableListOf(),
    val shifts: MutableList<Shift> = mutableListOf(),
    val priceRules: MutableList<PriceRule> = mutableListOf()
)

data class Professional(
    val id: String,
    var nome: String = "",
    var funcao: String = "Enfermeiro (plantonista) 40h",
    var telefone: String = "",
    var email: String = "",
    var endereco: String = "",
    var estadoCivil: String = "",
    var extracted: ExtractedData = ExtractedData(),
    var manual: ManualOverrides = ManualOverrides()
)

data class ExtractedData(
    var cpf: String = "",
    var pisPasep: String = "",
    var bancoAgencia: String = "",
    var bancoConta: String = "",
    var bancoTitular: String = "",
    var comprovanteDataEmissao: String = "", // dd/MM/yyyy
    var comprovanteEhNominal: Boolean? = null,
    var corenNumero: String = "",
    var corenValidade: String = "", // dd/MM/yyyy
    var corenNadaConstaMesAno: String = "" // MM/yyyy
)

data class ManualOverrides(
    var cpfConfirmado: Boolean = false,
    var pisConfirmado: Boolean = false,
    var bancoConfirmado: Boolean = false,
    var comprovanteConfirmado: Boolean = false,
    var corenConfirmado: Boolean = false,
    var nadaConstaConfirmado: Boolean = false
)

enum class DocCategory {
    CPF,
    PIS_PASEP,
    COMPROVANTE_RESIDENCIA,
    CARTA_RESIDENCIA_TERCEIROS,
    DOC_TITULAR_COMPROVANTE,
    DOC_PROFISSIONAL,
    BANCO,
    COREN,
    NADA_CONSTA_COREN
}

data class Attachment(
    val id: String,
    val professionalId: String,
    val category: DocCategory,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    var ocrText: String = "",
    var createdAtEpochMs: Long = System.currentTimeMillis()
)

enum class ShiftType { DIA, NOITE }

data class Shift(
    val id: String,
    val professionalId: String,
    val dateIso: String, // yyyy-MM-dd
    val hours: Int,
    val type: ShiftType
)

data class PriceRule(
    val id: String,
    var funcao: String,
    var type: ShiftType,
    var hours: Int,
    var value: Double // valor do plantão (ex: 12h noite)
)

data class OsGroupKey(
    val professionalId: String,
    val year: Int,
    val month: Int,
    val period: Int // 1 = dias 1-15, 2 = 16-31
)

fun Shift.localDate(): LocalDate = LocalDate.parse(dateIso)
