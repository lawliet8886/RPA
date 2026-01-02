package br.com.vivariorpaorganizador.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object Parsing {

    private val datePatterns = listOf(
        Regex("\\b(\\d{2})[/-](\\d{2})[/-](\\d{4})\\b"),
        Regex("\\b(\\d{1})[/-](\\d{2})[/-](\\d{4})\\b"),
        Regex("\\b(\\d{2})[/-](\\d{1})[/-](\\d{4})\\b"),
    )

    fun normalizeDigits(s: String): String = s.filter { it.isDigit() }

    fun findCpf(text: String?): String? {
        val t = text ?: return null
        // formato com pontuação
        Regex("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b").find(t)?.value?.let {
            val digits = normalizeDigits(it)
            if (Cpf.isValid(digits)) return Cpf.format(digits)
        }
        // 11 dígitos
        Regex("\\b\\d{11}\\b").findAll(t).forEach { m ->
            val digits = m.value
            if (Cpf.isValid(digits)) return Cpf.format(digits)
        }
        return null
    }

    fun findPis(text: String?): String? {
        val t = text ?: return null
        Regex("\\b\\d{3}\\.\\d{5}\\.\\d{2}-\\d\\b").find(t)?.value?.let {
            val digits = normalizeDigits(it)
            if (Pis.isValid(digits)) return Pis.format(digits)
        }
        Regex("\\b\\d{11}\\b").findAll(t).forEach { m ->
            val digits = m.value
            if (Pis.isValid(digits)) return Pis.format(digits)
        }
        return null
    }

    fun findDateIso(text: String?): LocalDate? {
        val t = text ?: return null
        for (rg in datePatterns) {
            val m = rg.find(t) ?: continue
            val dd = m.groupValues[1].toIntOrNull() ?: continue
            val mm = m.groupValues[2].toIntOrNull() ?: continue
            val yy = m.groupValues[3].toIntOrNull() ?: continue
            try {
                return LocalDate.of(yy, mm, dd)
            } catch (_: Exception) {}
        }
        return null
    }

    fun findYearMonth(text: String?): String? {
        val t = text ?: return null
        // padrões MM/YYYY
        Regex("\\b(0?[1-9]|1[0-2])[/.-](\\d{4})\\b").find(t)?.let { m ->
            val mm = m.groupValues[1].padStart(2, '0')
            val yy = m.groupValues[2]
            return "$mm/$yy"
        }
        return null
    }

    fun parseYearMonth(mesAno: String?): YearMonth? {
        if (mesAno.isNullOrBlank()) return null
        return try {
            val parts = mesAno.trim().split("/", "-", ".")
            if (parts.size < 2) return null
            val mm = parts[0].padStart(2, '0').toInt()
            val yy = parts[1].toInt()
            YearMonth.of(yy, mm)
        } catch (_: Exception) { null }
    }

    fun findAgencia(text: String?): String? {
        val t = text ?: return null
        // tenta "Agência 1234" ou "Ag 1234"
        Regex("(?i)ag[êe]ncia\s*[:.-]?\s*(\\d{3,5})").find(t)?.let { return it.groupValues[1] }
        Regex("(?i)\\bag\s*[:.-]?\s*(\\d{3,5})").find(t)?.let { return it.groupValues[1] }
        return null
    }

    fun findConta(text: String?): String? {
        val t = text ?: return null
        Regex("(?i)conta\s*[:.-]?\s*([0-9]{3,12}[-.]?[0-9xXkK]?)").find(t)?.let { return it.groupValues[1] }
        Regex("(?i)cc\s*[:.-]?\s*([0-9]{3,12}[-.]?[0-9xXkK]?)").find(t)?.let { return it.groupValues[1] }
        return null
    }

    fun findTitular(text: String?): String? {
        val t = text ?: return null
        // procura linhas com "Titular" ou "Nome"
        val lines = t.lines().map { it.trim() }.filter { it.isNotBlank() }
        val hit = lines.firstOrNull { it.contains("titular", ignoreCase = true) || it.startsWith("nome", true) }
        if (hit != null) {
            val cleaned = hit.replace(Regex("(?i)(titular|nome)\s*[:.-]?\s*"), "").trim()
            if (cleaned.length >= 5) return cleaned
        }
        // fallback: melhor "linha" em caixa alta
        val upper = lines.filter { it.length in 8..60 && it == it.uppercase(Locale.getDefault()) }
        return upper.firstOrNull()
    }

    fun findCorenNumero(text: String?): String? {
        val t = text ?: return null
        // Ex: COREN-RJ 123456
        Regex("(?i)core[nm][^0-9]{0,10}([0-9]{4,8})").find(t)?.let { return it.groupValues[1] }
        return null
    }

    fun findCorenValidadeIso(text: String?): String? {
        val date = findDateIso(text) ?: return null
        return date.toString()
    }
}
