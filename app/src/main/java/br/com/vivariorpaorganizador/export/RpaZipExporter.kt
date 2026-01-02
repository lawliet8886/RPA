package br.com.vivariorpaorganizador.export

import android.content.Context
import android.net.Uri
import br.com.vivariorpaorganizador.data.*
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object RpaZipExporter {

    fun exportToCacheZip(context: Context, state: AppState): File {
        val outFile = File(context.cacheDir, "RPA_ATUALIZADO.zip")
        if (outFile.exists()) outFile.delete()

        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            // pasta raiz RPA/
            val base = "RPA/"
            putDir(zip, base)
            putDir(zip, base + "PROFISSIONAIS/")

            // Planilha de pagamento (xlsx)
            val rows = buildPagamentoRows(state)
            val xlsxBytes = java.io.ByteArrayOutputStream().use { baos ->
                XlsxMinimalWriter.write(
                    sheetName = "Pagamento",
                    rows = rows,
                    out = baos
                )
                baos.toByteArray()
            }
            putBytes(zip, base + "CONTROLE_PAGAMENTO.xlsx", xlsxBytes)

            // por profissional
            for (prof in state.professionals) {
                val folder = safeFolderName(prof)
                val profBase = base + "PROFISSIONAIS/" + folder + "/"
                putDir(zip, profBase)
                putDir(zip, profBase + "DOCUMENTOS/")

                // copia anexos
                val atts = state.attachments.filter { it.professionalId == prof.id }
                for (att in atts) {
                    val data = readUriBytes(context, Uri.parse(att.uri)) ?: continue
                    val fileName = sanitizeFileName("${att.category.name}_${att.displayName}")
                    putBytes(zip, profBase + "DOCUMENTOS/" + fileName, data)
                }

                // gera OS por mês/período (P1/P2)
                val shifts = state.shifts.filter { it.professionalId == prof.id }
                    .mapNotNull { s ->
                        try { Pair(LocalDate.parse(s.dateIso), s) } catch (_: Exception) { null }
                    }
                val groups = shifts.groupBy { (date, _) ->
                    val ym = YearMonth.from(date)
                    val p = if (date.dayOfMonth <= 15) "P1" else "P2"
                    "$ym|$p"
                }

                for ((key, items) in groups) {
                    val parts = key.split("|")
                    val ym = YearMonth.parse(parts[0])
                    val period = parts[1]
                    val shiftsIn = items.map { it.second }.sortedBy { it.dateIso }

                    val totalHours = shiftsIn.sumOf { it.hours }
                    val totalValue = shiftsIn.sumOf { shiftValue(state, prof, it) }

                    val diasRes = shiftsIn.joinToString("; ") { s ->
                        val d = LocalDate.parse(s.dateIso)
                        val ddmm = d.format(DateTimeFormatter.ofPattern("dd/MM"))
                        "$ddmm - ${s.hours}h ${s.type.name.lowercase(Locale.getDefault())}"
                    }
                    val datasPrest = shiftsIn.joinToString(", ") { s ->
                        val d = LocalDate.parse(s.dateIso)
                        d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }

                    val dataEnvio = computeDataEnvioOs(ym, period)
                    val placeholders = mapOf(
                        "nome_profissional" to prof.nome,
                        "cpf" to prof.extracted.cpf,
                        "pis" to prof.extracted.pisPasep,
                        "endereco" to prof.endereco,
                        "telefone_contato" to prof.telefone,
                        "email" to prof.email,
                        "estado_civil" to prof.estadoCivil,
                        "funcao" to prof.funcao,
                        "carga_horaria_total" to totalHours.toString(),
                        "valor_os" to String.format(Locale.getDefault(), "%.2f", totalValue),
                        "datas_prestacao_servico" to datasPrest,
                        "dias_prestacao_resumido" to diasRes,
                        "data_envio_os" to dataEnvio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        "numero_coren" to prof.extracted.corenNumero,
                        "data_emissao_coren" to "",
                        "motivo_substituicao" to "Vacância Unidade",
                        "nome_substituido" to "",
                        "cpf_substituido" to "",
                        "coren_substituido" to ""
                    )

                    // DOCX
                    val docxName = "OS_${ym.year}_${pad2(ym.monthValue)}_${period}.docx"
                    val docxBytes = java.io.ByteArrayOutputStream().use { baos ->
                        DocxTemplateFiller.writeFilledOsDocx(context, placeholders, baos)
                        baos.toByteArray()
                    }
                    putBytes(zip, profBase + docxName, docxBytes)

                    // PDF (resumo)
                    val pdfName = "OS_${ym.year}_${pad2(ym.monthValue)}_${period}.pdf"
                    val pdfBytes = java.io.ByteArrayOutputStream().use { baos ->
                        OsPdfGenerator.writeFilledOsPdf(context, placeholders, baos)
                        baos.toByteArray()
                    }
                    putBytes(zip, profBase + pdfName, pdfBytes)
                }

                // checklist
                val checklist = buildChecklist(state, prof)
                putBytes(zip, profBase + "CHECKLIST_DOCUMENTOS.txt", checklist.toByteArray(Charsets.UTF_8))
            }
        }

        return outFile
    }

    private fun shiftValue(state: AppState, prof: Professional, shift: Shift): Double {
        val rule = state.priceRules.find { it.funcao == prof.funcao && it.type == shift.type && it.hours == shift.hours }
        return rule?.value ?: 0.0
    }

    private fun buildPagamentoRows(state: AppState): List<List<String>> {
        val header = listOf("Profissional", "CPF", "Função", "Data", "Turno", "Horas", "Valor")
        val rows = mutableListOf(header)
        for (prof in state.professionals) {
            val shifts = state.shifts.filter { it.professionalId == prof.id }.sortedBy { it.dateIso }
            for (s in shifts) {
                val d = try { LocalDate.parse(s.dateIso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) } catch (_: Exception) { s.dateIso }
                val v = shiftValue(state, prof, s)
                rows.add(listOf(
                    prof.nome,
                    prof.extracted.cpf,
                    prof.funcao,
                    d,
                    s.type.name,
                    s.hours.toString(),
                    String.format(Locale.getDefault(), "%.2f", v)
                ))
            }
        }
        return rows
    }

    private fun buildChecklist(state: AppState, prof: Professional): String {
        val pend = PendencyEngine.compute(state, prof)
        val sb = StringBuilder()
        sb.appendLine("CHECKLIST – ${prof.nome}")
        sb.appendLine("Função: ${prof.funcao}")
        sb.appendLine("CPF: ${prof.extracted.cpf}")
        sb.appendLine("PIS/PASEP: ${prof.extracted.pisPasep}")
        sb.appendLine("")
        if (pend.isEmpty()) {
            sb.appendLine("✅ Sem pendências")
        } else {
            sb.appendLine("⚠ Pendências:")
            pend.forEach { sb.appendLine("- ${it.title}: ${it.description}") }
        }
        sb.appendLine("")
        sb.appendLine("Documentos anexados:")
        val atts = state.attachments.filter { it.professionalId == prof.id }
        for (cat in DocCategory.values()) {
            sb.appendLine("- ${cat.name}: ${if (atts.any { it.category == cat }) "OK" else "PENDENTE"}")
        }
        return sb.toString()
    }

    private fun computeDataEnvioOs(ym: YearMonth, period: String): LocalDate {
        return if (period == "P1") {
            nextBusinessDay(LocalDate.of(ym.year, ym.monthValue, 15).plusDays(1))
        } else {
            val nextMonth = ym.plusMonths(1)
            nextBusinessDay(LocalDate.of(nextMonth.year, nextMonth.monthValue, 1))
        }
    }

    private fun nextBusinessDay(date: LocalDate): LocalDate {
        var d = date
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) {
            d = d.plusDays(1)
        }
        return d
    }

    private fun putDir(zip: ZipOutputStream, path: String) {
        val p = if (path.endsWith("/")) path else "$path/"
        val e = ZipEntry(p)
        zip.putNextEntry(e)
        zip.closeEntry()
    }

    private fun putBytes(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        val e = ZipEntry(path)
        zip.putNextEntry(e)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun readUriBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    private fun safeFolderName(prof: Professional): String {
        val cpf = prof.extracted.cpf.filter { it.isDigit() }
        val base = prof.nome.trim().ifBlank { "PROFISSIONAL" }
        return sanitizeFileName("${base}_$cpf")
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()
}
