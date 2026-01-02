package br.com.vivariorpaorganizador.ocr

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CorenUtils {
    data class CorenExtract(val numero: String = "", val validade: String = "")

    private val corenNumRegex = Regex("""(?i)\bCOREN\s*[- ]?\s*([A-Z]{2})\s*(\d{3,10})\b""")
    private val validadeRegex = Regex("""(?i)\b(validade|vence|vencimento)\s*[:\-]?\s*(\d{2}[/-]\d{2}[/-]\d{4})\b""")

    fun extract(text: String): CorenExtract {
        val numMatch = corenNumRegex.find(text)
        val numero = if (numMatch != null) {
            val uf = numMatch.groupValues[1].uppercase()
            val n = numMatch.groupValues[2]
            "${uf}${n}"
        } else ""

        val valMatch = validadeRegex.find(text)
        val validade = valMatch?.groupValues?.getOrNull(2)?.let { normalizeDate(it) } ?: ""

        return CorenExtract(numero = numero, validade = validade)
    }

    private fun normalizeDate(s: String): String {
        val cleaned = s.replace('-', '/').trim()
        return try {
            val d = LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (_: Exception) {
            cleaned
        }
    }
}
