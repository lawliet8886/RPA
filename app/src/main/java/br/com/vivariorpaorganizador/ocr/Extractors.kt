package br.com.vivariorpaorganizador.ocr

import br.com.vivariorpaorganizador.data.ExtractedData
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object Extractors {

    private val cpfRegex = Regex("""\b(\d{3}\.\d{3}\.\d{3}-\d{2}|\d{11})\b""")
    private val pisRegex = Regex("""\b(\d{3}\.\d{5}\.\d{2}-\d|\d{11})\b""")
    private val dateRegex = Regex("""\b(\d{2})[/-](\d{2})[/-](\d{4})\b""")
    private val mesAnoRegex = Regex("""\b(0?[1-9]|1[0-2])[/-](\d{4})\b""")

    fun applyOcrTextToExtracted(
        current: ExtractedData,
        ocrText: String,
        professionalName: String
    ): ExtractedData {
        val cleaned = ocrText.replace("\u00A0", " ")
        val out = current.copy()

        // CPF
        if (out.cpf.isBlank()) {
            val cpf = cpfRegex.find(cleaned)?.value?.let { normalizeDigits(it) } ?: ""
            if (CpfUtils.isValid(cpf)) out.cpf = CpfUtils.format(cpf)
        }

        // PIS/PASEP
        if (out.pisPasep.isBlank()) {
            val pis = pisRegex.find(cleaned)?.value?.let { normalizeDigits(it) } ?: ""
            if (PisUtils.isValid(pis)) out.pisPasep = PisUtils.format(pis)
        }

        // Comprovante: data e nominalidade
        if (out.comprovanteDataEmissao.isBlank()) {
            val d = dateRegex.findAll(cleaned).mapNotNull { m ->
                val day = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val mon = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val yr = m.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                try { LocalDate.of(yr, mon, day) } catch (_: Exception) { null }
            }.maxOrNull()
            if (d != null) {
                out.comprovanteDataEmissao = d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            }
        }
        if (out.comprovanteEhNominal == null) {
            // Heurística simples: procura pelo nome do profissional no texto.
            val tokens = professionalName.uppercase(Locale.getDefault()).split(" ").filter { it.length >= 4 }
            val upper = cleaned.uppercase(Locale.getDefault())
            out.comprovanteEhNominal = tokens.any { upper.contains(it) }
        }

        // Banco
        val banco = BankUtils.extract(cleaned)
        if (out.bancoAgencia.isBlank() && banco.agencia.isNotBlank()) out.bancoAgencia = banco.agencia
        if (out.bancoConta.isBlank() && banco.conta.isNotBlank()) out.bancoConta = banco.conta
        if (out.bancoTitular.isBlank() && banco.titular.isNotBlank()) out.bancoTitular = banco.titular

        // COREN
        val coren = CorenUtils.extract(cleaned)
        if (out.corenNumero.isBlank() && coren.numero.isNotBlank()) out.corenNumero = coren.numero
        if (out.corenValidade.isBlank() && coren.validade.isNotBlank()) out.corenValidade = coren.validade

        // Nada Consta: tenta achar MM/yyyy
        if (out.corenNadaConstaMesAno.isBlank()) {
            val ym = mesAnoRegex.findAll(cleaned).mapNotNull { m ->
                val mm = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val yy = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                try { YearMonth.of(yy, mm) } catch (_: Exception) { null }
            }.maxOrNull()
            if (ym != null) out.corenNadaConstaMesAno = String.format(Locale.getDefault(), "%02d/%04d", ym.monthValue, ym.year)
        }

        return out
    }

    fun normalizeDigits(s: String): String = s.filter { it.isDigit() }
}
