package br.com.vivariorpaorganizador.util

import br.com.vivariorpaorganizador.data.ExtractedFields
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object TextExtractors {

    private val digitsOnly = Regex("\\D+")
    private val cpfRegex = Regex("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b")
    private val pisRegex = Regex("\\b\\d{3}\\.?\\d{5}\\.?\\d{2}-?\\d\\b")

    // Datas comuns em documentos BR
    private val dateRegex = Regex("\\b(\\d{2})[\\/\\-](\\d{2})[\\/\\-](\\d{4})\\b")

    fun extractCpf(text: String): String? {
        val m = cpfRegex.find(text) ?: return null
        val cpf = m.value.replace(digitsOnly, "")
        return if (cpf.length == 11 && isValidCPF(cpf)) cpf else null
    }

    fun extractPis(text: String): String? {
        val m = pisRegex.find(text) ?: return null
        val pis = m.value.replace(digitsOnly, "")
        return if (pis.length == 11 && isValidPIS(pis)) pis else null
    }

    fun extractNomeProvavel(text: String): String? {
        // Heurística bem simples: pega a linha mais “gritante” em caixa alta.
        val lines = text.lines().map { it.trim() }.filter { it.length >= 6 }
        val candidates = lines
            .filter { it == it.uppercase(Locale("pt", "BR")) }
            .filter { it.count { ch -> ch == ' ' } >= 1 }
            .filter { it.none { ch -> ch.isDigit() } }
        return candidates.firstOrNull()
    }

    fun extractAgenciaContaTitular(text: String): Triple<String?, String?, String?> {
        val t = text.replace("\n", " ").replace("\r", " ")
        val ag = Regex("(ag\\.?|agência|agencia)\\s*[:\\-]?\\s*(\\d{3,5})", RegexOption.IGNORE_CASE)
            .find(t)?.groupValues?.getOrNull(2)

        val conta = Regex("(conta|c\\/c|cc)\\s*[:\\-]?\\s*([0-9]{3,12}[-\\.]?[0-9xX]?)", RegexOption.IGNORE_CASE)
            .find(t)?.groupValues?.getOrNull(2)

        // Nome do titular costuma aparecer como “Titular: NOME” ou “Nome: NOME”
        val titular = Regex("(titular|nome)\\s*[:\\-]?\\s*([A-ZÁÉÍÓÚÂÊÔÃÕÇ ]{6,})", RegexOption.IGNORE_CASE)
            .find(t)?.groupValues?.getOrNull(2)?.trim()

        return Triple(ag, conta, titular)
    }

    fun extractDateIso(text: String): String? {
        val m = dateRegex.find(text) ?: return null
        val (dd, mm, yyyy) = m.destructured
        return try {
            LocalDate.of(yyyy.toInt(), mm.toInt(), dd.toInt()).toString()
        } catch (_: Exception) { null }
    }

    fun extractYearMonth(text: String): String? {
        // tenta achar MM/YYYY ou mês escrito (bem best-effort)
        val m = Regex("\\b(0[1-9]|1[0-2])\\/(20\\d{2})\\b").find(text) ?: return null
        val (mm, yyyy) = m.destructured
        return "${yyyy}-${mm}"
    }

    fun merge(base: ExtractedFields, add: ExtractedFields): ExtractedFields =
        ExtractedFields(
            cpf = add.cpf ?: base.cpf,
            pis = add.pis ?: base.pis,
            nome = add.nome ?: base.nome,
            agencia = add.agencia ?: base.agencia,
            conta = add.conta ?: base.conta,
            titularBanco = add.titularBanco ?: base.titularBanco,
            dataEmissaoResidenciaIso = add.dataEmissaoResidenciaIso ?: base.dataEmissaoResidenciaIso,
            dataValidadeCorenIso = add.dataValidadeCorenIso ?: base.dataValidadeCorenIso,
            mesAnoNadaConsta = add.mesAnoNadaConsta ?: base.mesAnoNadaConsta
        )

    // -------------------------
    // Validações “de verdade”
    // -------------------------
    // CPF: valida dígitos verificadores
    fun isValidCPF(cpf: String): Boolean {
        if (cpf.length != 11) return false
        if ((0..9).any { d -> cpf.all { it == ('0' + d) } }) return false

        fun calcDigit(base: String, factorStart: Int): Int {
            var sum = 0
            var factor = factorStart
            for (c in base) {
                sum += (c - '0') * factor
                factor -= 1
            }
            val mod = sum % 11
            return if (mod < 2) 0 else 11 - mod
        }

        val d1 = calcDigit(cpf.substring(0, 9), 10)
        val d2 = calcDigit(cpf.substring(0, 9) + d1.toString(), 11)
        return cpf.endsWith("${d1}${d2}")
    }

    // PIS/PASEP: dígito verificador (módulo 11 com pesos)
    fun isValidPIS(pis: String): Boolean {
        if (pis.length != 11) return false
        val weights = intArrayOf(3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val nums = pis.map { it - '0' }
        val sum = weights.indices.sumOf { i -> nums[i] * weights[i] }
        val mod = sum % 11
        val dv = if (mod < 2) 0 else 11 - mod
        return dv == nums[10]
    }

    fun isWithinLastDays(dateIso: String, days: Long, today: LocalDate = LocalDate.now()): Boolean {
        return try {
            val d = LocalDate.parse(dateIso)
            val diff = java.time.temporal.ChronoUnit.DAYS.between(d, today)
            diff in 0..days
        } catch (_: Exception) { false }
    }

    fun isYearMonthOnOrAfter(ymIso: String, ref: YearMonth = YearMonth.now()): Boolean {
        return try {
            val ym = YearMonth.parse(ymIso)
            !ym.isBefore(ref)
        } catch (_: Exception) { false }
    }
}
