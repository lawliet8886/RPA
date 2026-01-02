package br.com.vivariorpaorganizador.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Importa uma planilha XLSX (tabela oficial) e extrai regras de valores por:
 * - Função
 * - Turno (Dia/Noite)
 * - Horas (8h / 12h)
 *
 * Sem Apache POI (muito pesado pra APK). Leitura mínima de:
 * - xl/sharedStrings.xml
 * - xl/workbook.xml + xl/_rels/workbook.xml.rels
 * - xl/worksheets/sheet*.xml
 */

data class ImportedPriceRule(
    val funcao: String,
    val type: ShiftType,
    val hours: Int,
    val value: Double
)

object XlsxPriceTableImporter {

    fun import(context: Context, uri: Uri): List<ImportedPriceRule> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Não foi possível ler o arquivo XLSX")
        return importBytes(bytes)
    }

    fun importBytes(bytes: ByteArray): List<ImportedPriceRule> {
        val entries = unzipEntries(bytes)
        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val sheetPaths = resolveSheetPaths(entries)

        // Lê todas as planilhas e escolhe a mais "parecida" com a tabela oficial.
        val candidates = sheetPaths.mapNotNull { path ->
            val xml = entries[path] ?: return@mapNotNull null
            val grid = parseSheet(xml, sharedStrings)
            val headerRow = findHeaderRow(grid)
            if (headerRow == null) return@mapNotNull null
            val cols = mapColumns(grid, headerRow)
            if (cols.funcaoCol < 0 || cols.turnoCol < 0 || cols.valor12Col < 0) return@mapNotNull null
            val rules = extractRulesFromGrid(grid, headerRow, cols)
            // score: qtde de regras 12h
            val score = rules.count { it.hours == 12 }
            SheetCandidate(score, rules)
        }

        if (candidates.isEmpty()) {
            throw IllegalArgumentException("Não consegui identificar a aba/colunas de preços nessa planilha")
        }

        return candidates.maxBy { it.score }.rules
    }

    // ------------------------- internals -------------------------

    private data class SheetCandidate(val score: Int, val rules: List<ImportedPriceRule>)

    private data class ColMap(
        val funcaoCol: Int,
        val chCol: Int,
        val turnoCol: Int,
        val valor8Col: Int,
        val valor12Col: Int
    )

    private fun unzipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    out[e.name] = zis.readBytes()
                }
                e = zis.nextEntry
            }
        }
        return out
    }

    private fun parseSharedStrings(xmlBytes: ByteArray?): List<String> {
        if (xmlBytes == null) return emptyList()
        val parser = Xml.newPullParser()
        parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        val out = mutableListOf<String>()
        var inT = false
        var buffer = StringBuilder()

        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    if (parser.name == "t") {
                        inT = true
                        buffer = StringBuilder()
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> {
                    if (inT) buffer.append(parser.text ?: "")
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    if (parser.name == "t" && inT) {
                        inT = false
                        out.add(buffer.toString())
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun resolveSheetPaths(entries: Map<String, ByteArray>): List<String> {
        // workbook.xml indica sheets e r:ids; rels mapeia r:id -> worksheets/sheetX.xml
        val wb = entries["xl/workbook.xml"]?.toString(Charsets.UTF_8)
        val rels = entries["xl/_rels/workbook.xml.rels"]?.toString(Charsets.UTF_8)

        if (wb == null || rels == null) {
            // fallback comum
            return entries.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.sorted()
        }

        val idToTarget = mutableMapOf<String, String>()
        val relParser = Xml.newPullParser()
        relParser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        relParser.setInput(rels.reader())
        var event = relParser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG && relParser.name == "Relationship") {
                val id = relParser.getAttributeValue(null, "Id")
                val target = relParser.getAttributeValue(null, "Target")
                if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                    idToTarget[id] = target
                }
            }
            event = relParser.next()
        }

        val paths = mutableListOf<String>()
        val wbParser = Xml.newPullParser()
        wbParser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        wbParser.setInput(wb.reader())
        event = wbParser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG && wbParser.name == "sheet") {
                val rid = wbParser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    ?: wbParser.getAttributeValue(null, "r:id")
                if (!rid.isNullOrBlank()) {
                    val target = idToTarget[rid]
                    if (!target.isNullOrBlank()) {
                        val full = if (target.startsWith("/")) target.drop(1) else "xl/$target"
                        paths.add(full)
                    }
                }
            }
            event = wbParser.next()
        }

        return if (paths.isNotEmpty()) paths else entries.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.sorted()
    }

    private fun parseSheet(xmlBytes: ByteArray, sharedStrings: List<String>): Map<Int, MutableMap<Int, String>> {
        val grid = mutableMapOf<Int, MutableMap<Int, String>>()

        val parser = Xml.newPullParser()
        parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var currentCellRef: String? = null
        var currentType: String? = null
        var currentValue: String? = null
        var inV = false
        var inInlineT = false

        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "c" -> {
                            currentCellRef = parser.getAttributeValue(null, "r")
                            currentType = parser.getAttributeValue(null, "t")
                            currentValue = null
                        }
                        "v" -> inV = true
                        "t" -> {
                            // inline string: <is><t>texto</t></is>
                            inInlineT = true
                            currentValue = ""
                        }
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> {
                    if (inV) currentValue = (parser.text ?: "")
                    if (inInlineT) currentValue = (parser.text ?: "")
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> inV = false
                        "t" -> inInlineT = false
                        "c" -> {
                            val ref = currentCellRef
                            if (!ref.isNullOrBlank() && !currentValue.isNullOrBlank()) {
                                val (col, row) = parseCellRef(ref)
                                val v = when (currentType) {
                                    "s" -> {
                                        val idx = currentValue!!.toIntOrNull() ?: -1
                                        if (idx in sharedStrings.indices) sharedStrings[idx] else currentValue!!
                                    }
                                    else -> currentValue!!
                                }
                                val rowMap = grid.getOrPut(row) { mutableMapOf() }
                                rowMap[col] = v
                            }
                            currentCellRef = null
                            currentType = null
                            currentValue = null
                        }
                    }
                }
            }
            event = parser.next()
        }

        return grid
    }

    private fun parseCellRef(ref: String): Pair<Int, Int> {
        // e.g. A2, AB12
        val letters = ref.takeWhile { it.isLetter() }
        val numbers = ref.dropWhile { it.isLetter() }
        val col = excelColToIndex(letters)
        val row = numbers.toIntOrNull() ?: 0
        return Pair(col, row)
    }

    private fun excelColToIndex(letters: String): Int {
        var result = 0
        for (ch in letters.uppercase(Locale.getDefault())) {
            result = result * 26 + (ch.code - 'A'.code + 1)
        }
        return result
    }

    private fun findHeaderRow(grid: Map<Int, Map<Int, String>>): Int? {
        // procura nas primeiras 12 linhas
        var bestRow: Int? = null
        var bestScore = 0

        for (row in 1..12) {
            val map = grid[row] ?: continue
            val vals = map.values.map { norm(it) }
            var score = 0
            if (vals.any { it.contains("FUNCAO") }) score += 3
            if (vals.any { it == "TURNO" || it.contains("TURNO") }) score += 2
            if (vals.any { it.contains("12H") && it.contains("VALOR") }) score += 3
            if (vals.any { it.contains("8H") && it.contains("VALOR") }) score += 1
            if (score > bestScore) {
                bestScore = score
                bestRow = row
            }
        }

        return if (bestScore >= 5) bestRow else null
    }

    private fun mapColumns(grid: Map<Int, Map<Int, String>>, headerRow: Int): ColMap {
        val header = grid[headerRow] ?: emptyMap()
        var funcao = -1
        var ch = -1
        var turno = -1
        var v8 = -1
        var v12 = -1

        header.forEach { (col, raw) ->
            val h = norm(raw)
            if (funcao < 0 && h.contains("FUNCAO")) funcao = col
            if (ch < 0 && h.contains("CH") && h.contains("SEMANAL")) ch = col
            if (turno < 0 && (h == "TURNO" || h.contains("TURNO"))) turno = col
            if (v8 < 0 && h.contains("8H") && h.contains("VALOR")) v8 = col
            if (v12 < 0 && h.contains("12H") && h.contains("VALOR")) v12 = col
        }

        return ColMap(funcao, ch, turno, v8, v12)
    }

    private fun extractRulesFromGrid(grid: Map<Int, Map<Int, String>>, headerRow: Int, cols: ColMap): List<ImportedPriceRule> {
        val out = mutableListOf<ImportedPriceRule>()

        for (row in (headerRow + 1)..(headerRow + 400)) {
            val map = grid[row] ?: continue
            val funcaoRaw = map[cols.funcaoCol]?.trim().orEmpty()
            if (funcaoRaw.isBlank()) continue

            val chRaw = map[cols.chCol]?.trim().orEmpty()
            val chInt = chRaw.toIntOrNull() ?: chRaw.replace(".0", "").toIntOrNull()

            val turnoRaw = map[cols.turnoCol]?.trim().orEmpty()
            val type = if (norm(turnoRaw).contains("NOITE") || norm(turnoRaw).contains("NOTUR")) ShiftType.NOITE else ShiftType.DIA

            val f = mapFuncaoForApp(funcaoRaw, chInt)

            val v12 = map[cols.valor12Col]?.let { parseMoneyOrNumber(it) }
            if (v12 != null && v12 > 0) out.add(ImportedPriceRule(f, type, 12, v12))

            if (cols.valor8Col > 0) {
                val v8 = map[cols.valor8Col]?.let { parseMoneyOrNumber(it) }
                if (v8 != null && v8 > 0) out.add(ImportedPriceRule(f, type, 8, v8))
            }
        }

        return out
    }

    private fun parseMoneyOrNumber(raw: String): Double? {
        val s = raw
            .replace("R$", "")
            .replace("\\u00A0", " ")
            .trim()

        val cleaned = s
            .replace(".", "")
            .replace(",", ".")
            .replace(" ", "")

        return cleaned.toDoubleOrNull()
    }

    private fun mapFuncaoForApp(funcaoRaw: String, chSemanal: Int?): String {
        val n = norm(funcaoRaw)
        return when {
            n.contains("ENFERMEIRO") && n.contains("PLANTON") -> {
                // Regra do projeto: plantonista de Enfermagem é SEMPRE considerado 40h.
                // Mesmo que o XLSX venha diferente, a gente padroniza pro app casar as regras certinho.
                "Enfermeiro (plantonista) 40h"
            }
            (n.contains("TECNICO") || n.contains("TEC")) && n.contains("ENFERMAG") && n.contains("PLANTON") -> {
                // Regra do projeto: plantonista de Técnico de Enfermagem é SEMPRE considerado 40h.
                "Téc. de Enfermagem (plantonista) 40h"
            }
            else -> funcaoRaw.trim()
        }
    }

    private fun norm(s: String): String {
        val noAcc = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return noAcc.uppercase(Locale.getDefault()).trim()
    }
}
