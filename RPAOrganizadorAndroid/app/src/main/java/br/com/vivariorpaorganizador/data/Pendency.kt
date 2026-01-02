package br.com.vivariorpaorganizador.data

import br.com.vivariorpaorganizador.ocr.CpfUtils
import br.com.vivariorpaorganizador.ocr.PisUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class PendencyAction {
    ATTACH_CPF,
    ATTACH_PIS,
    ATTACH_RESIDENCIA,
    ATTACH_CARTA_TERCEIROS,
    ATTACH_DOC_TITULAR,
    ATTACH_DOC_PROF,
    ATTACH_BANCO,
    ATTACH_COREN,
    ATTACH_NADA_CONSTA,
    EDIT_FIELDS
}

data class Pendency(
    val title: String,
    val description: String,
    val action: PendencyAction
)

object PendencyEngine {

    fun compute(state: AppState, prof: Professional): List<Pendency> {
        val out = mutableListOf<Pendency>()
        val atts = state.attachments.filter { it.professionalId == prof.id }
        val e = prof.extracted
        val m = prof.manual
        val today = LocalDate.now()

        // CPF obrigatório
        val cpfOk = (m.cpfConfirmado && e.cpf.isNotBlank()) || (e.cpf.isNotBlank() && CpfUtils.isValid(e.cpf))
        if (!cpfOk) {
            out.add(Pendency(
                title = "CPF pendente",
                description = "CPF é obrigatório. Anexe o documento ou corrija manualmente.",
                action = PendencyAction.ATTACH_CPF
            ))
        }

        // PIS/PASEP obrigatório
        val pisDigits = e.pisPasep.filter { it.isDigit() }
        val pisOk = (m.pisConfirmado && pisDigits.isNotBlank()) || (pisDigits.isNotBlank() && PisUtils.isValid(pisDigits))
        if (!pisOk) {
            out.add(Pendency(
                title = "PIS/PASEP pendente",
                description = "PIS/PASEP é obrigatório. Anexe o documento ou corrija manualmente.",
                action = PendencyAction.ATTACH_PIS
            ))
        }

        // Comprovante de residência
        val comp = atts.any { it.category == DocCategory.COMPROVANTE_RESIDENCIA }
        if (!comp) {
            out.add(Pendency(
                title = "Comprovante de residência pendente",
                description = "Anexe o comprovante de residência.",
                action = PendencyAction.ATTACH_RESIDENCIA
            ))
        } else {
            val dateOk = if (m.comprovanteConfirmado) {
                true
            } else {
                val d = parseDateOrNull(e.comprovanteDataEmissao)
                d != null && !d.isAfter(today) && d.isAfter(today.minusDays(90))
            }
            if (!dateOk) {
                out.add(Pendency(
                    title = "Comprovante fora do prazo",
                    description = "A data de emissão precisa estar dentro de 90 dias (ou estar legível).",
                    action = PendencyAction.EDIT_FIELDS
                ))
            }

            val nominal = e.comprovanteEhNominal
            if (nominal == false) {
                // Regra de terceiros: carta + docs dos dois
                val carta = atts.any { it.category == DocCategory.CARTA_RESIDENCIA_TERCEIROS }
                val docTitular = atts.any { it.category == DocCategory.DOC_TITULAR_COMPROVANTE }
                val docProf = atts.any { it.category == DocCategory.DOC_PROFISSIONAL }

                if (!carta) out.add(Pendency(
                    title = "Carta do titular pendente",
                    description = "Comprovante em nome de terceiros: precisa de carta de próprio punho do titular (nome+CPF) confirmando que o profissional (nome+CPF) reside no endereço.",
                    action = PendencyAction.ATTACH_CARTA_TERCEIROS
                ))
                if (!docTitular) out.add(Pendency(
                    title = "Documento do titular pendente",
                    description = "Anexe a foto do documento do titular do comprovante.",
                    action = PendencyAction.ATTACH_DOC_TITULAR
                ))
                if (!docProf) out.add(Pendency(
                    title = "Documento do profissional pendente",
                    description = "Anexe a foto do documento do profissional (junto à carta).",
                    action = PendencyAction.ATTACH_DOC_PROF
                ))
            }
        }

        // Banco
        val bancoAtt = atts.any { it.category == DocCategory.BANCO }
        if (!bancoAtt) {
            out.add(Pendency(
                title = "Dados bancários pendentes",
                description = "Anexe print do app ou foto frente/verso do cartão. Precisa mostrar agência, conta e nome do titular.",
                action = PendencyAction.ATTACH_BANCO
            ))
        } else {
            if (!m.bancoConfirmado) {
                if (e.bancoAgencia.isBlank() || e.bancoConta.isBlank() || e.bancoTitular.isBlank()) {
                    out.add(Pendency(
                        title = "Banco incompleto",
                        description = "Precisa ter agência, conta e nome do titular (legíveis).",
                        action = PendencyAction.EDIT_FIELDS
                    ))
                }
            }
        }

        // COREN
        val corenAtt = atts.any { it.category == DocCategory.COREN }
        if (!corenAtt) {
            out.add(Pendency(
                title = "COREN pendente",
                description = "Anexe o documento do COREN e garanta que a validade esteja legível.",
                action = PendencyAction.ATTACH_COREN
            ))
        } else {
            if (!m.corenConfirmado) {
                val valDate = parseDateOrNull(e.corenValidade)
                if (valDate == null || valDate.isBefore(today)) {
                    out.add(Pendency(
                        title = "COREN inválido/expirado",
                        description = "COREN precisa estar na validade (ou a validade precisa estar legível).",
                        action = PendencyAction.EDIT_FIELDS
                    ))
                }
            }
        }

        // Nada Consta COREN
        val nadaAtt = atts.any { it.category == DocCategory.NADA_CONSTA_COREN }
        if (!nadaAtt) {
            out.add(Pendency(
                title = "Certidão (Nada Consta) pendente",
                description = "Anexe a certidão negativa do COREN do mês atual.",
                action = PendencyAction.ATTACH_NADA_CONSTA
            ))
        } else {
            if (!m.nadaConstaConfirmado) {
                val ym = parseYearMonthOrNull(e.corenNadaConstaMesAno)
                val nowYm = YearMonth.from(today)
                if (ym == null || ym.isBefore(nowYm)) {
                    out.add(Pendency(
                        title = "Nada Consta fora do mês",
                        description = "A certidão precisa ser do mês atual (ex.: 12/2025) ou posterior.",
                        action = PendencyAction.EDIT_FIELDS
                    ))
                }
            }
        }

        return out
    }

    private fun parseDateOrNull(s: String): LocalDate? {
        return try {
            if (s.isBlank()) return null
            LocalDate.parse(s.trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseYearMonthOrNull(s: String): YearMonth? {
        return try {
            if (s.isBlank()) return null
            val parts = s.replace('-', '/').split('/')
            if (parts.size != 2) return null
            val mm = parts[0].toInt()
            val yy = parts[1].toInt()
            YearMonth.of(yy, mm)
        } catch (_: Exception) {
            null
        }
    }
}
