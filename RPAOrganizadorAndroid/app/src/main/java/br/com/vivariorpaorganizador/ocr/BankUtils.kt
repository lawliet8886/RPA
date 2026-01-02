package br.com.vivariorpaorganizador.ocr

import java.util.Locale

object BankUtils {
    data class BankExtract(val agencia: String = "", val conta: String = "", val titular: String = "")

    private val agRegex = Regex("""(?i)\b(ag[êe]?ncia|ag)\s*[:\-]?\s*(\d{2,5})\b""")
    private val contaRegex = Regex("""(?i)\b(conta|c/c|cc)\s*[:\-]?\s*([0-9]{3,15}[\-\.]?[0-9xX]?)\b""")
    private val titularRegex = Regex("""(?i)\b(titular|nome)\s*[:\-]?\s*([A-ZÀ-Ü][A-ZÀ-Ü ]{5,})\b""")

    fun extract(text: String): BankExtract {
        val t = text.replace("\n", " ").replace("\t", " ").replace("  ", " ")
        val agencia = agRegex.find(t)?.groupValues?.getOrNull(2) ?: ""
        val conta = contaRegex.find(t)?.groupValues?.getOrNull(2) ?: ""
        val titular = titularRegex.find(t)?.groupValues?.getOrNull(2)?.trim()?.take(60) ?: ""

        // Heurística extra: se não achou titular, tenta pegar a linha mais “nome próprio” do topo
        val titular2 = if (titular.isBlank()) guessName(text) else titular

        return BankExtract(
            agencia = agencia,
            conta = conta,
            titular = titular2
        )
    }

    private fun guessName(raw: String): String {
        val lines = raw.lines().map { it.trim() }.filter { it.length in 8..60 }
        val candidates = lines
            .filter { it.count { c -> c.isLetter() } >= 6 }
            .filter { it.uppercase(Locale.getDefault()) == it } // OCR de banco costuma vir em CAIXA ALTA
            .filterNot { it.contains("BANCO", true) || it.contains("AG", true) || it.contains("CONTA", true) }

        return candidates.firstOrNull()?.take(60) ?: ""
    }
}
