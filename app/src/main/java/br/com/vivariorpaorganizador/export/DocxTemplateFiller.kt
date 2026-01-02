package br.com.vivariorpaorganizador.export

import android.content.Context
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Preenche o template_os.docx (asset) substituindo placeholders do tipo {{ chave }}.
 *
 * O Word costuma "quebrar" o texto em vários <w:t> (runs), e aí um placeholder vira algo como:
 *   <w:t>{{ </w:t> ... <w:t>nome_profissional</w:t> ... <w:t> }}</w:t>
 *
 * Esta implementação detecta placeholders quebrados, "cola" tudo dentro do primeiro <w:t>
 * e limpa os seguintes, preservando a formatação (tanto quanto dá) sem precisar de biblioteca pesada.
 */
object DocxTemplateFiller {

    private const val ASSET_NAME = "template_os.docx"

    // Captura cada nó de texto do Word: <w:t ...>TEXTO</w:t>
    private val WT_PATTERN: Pattern = Pattern.compile("<w:t[^>]*>(.*?)</w:t>", Pattern.DOTALL)

    fun writeFilledOsDocx(context: Context, placeholders: Map<String, String>, out: java.io.OutputStream) {
        val templateBytes = context.assets.open(ASSET_NAME).use { it.readBytes() }

        ZipInputStream(templateBytes.inputStream()).use { zin ->
            ZipOutputStream(out).use { zout ->
                var entry = zin.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val bytes = zin.readBytes()

                    val newBytes = if (name.endsWith(".xml")) {
                        val xml = bytes.toString(Charsets.UTF_8)
                        val replaced = replacePlaceholdersRobust(xml, placeholders)
                        replaced.toByteArray(Charsets.UTF_8)
                    } else {
                        bytes
                    }

                    zout.putNextEntry(ZipEntry(name))
                    zout.write(newBytes)
                    zout.closeEntry()

                    entry = zin.nextEntry
                }
            }
        }
    }

    private fun replacePlaceholdersRobust(xml: String, placeholders: Map<String, String>): String {
        // 1) Extrai todos os w:t e seus conteúdos
        val matcher = WT_PATTERN.matcher(xml)
        val nodes = mutableListOf<TextNode>()
        while (matcher.find()) {
            nodes.add(
                TextNode(
                    contentStart = matcher.start(1),
                    contentEnd = matcher.end(1),
                    content = matcher.group(1) ?: ""
                )
            )
        }
        if (nodes.isEmpty()) return replaceAllInString(xml, placeholders)

        // 2) Processa colando placeholders quebrados entre w:t
        val newContents = nodes.map { it.content }.toMutableList()

        var i = 0
        while (i < newContents.size) {
            val cur = newContents[i]
            val startIdx = cur.indexOf("{{")
            if (startIdx < 0) {
                // Sem início de placeholder, só substitui o que estiver inteiro aqui.
                newContents[i] = replaceAllInString(cur, placeholders)
                i++
                continue
            }

            val endIdx = cur.indexOf("}}", startIdx + 2)
            if (endIdx >= 0) {
                // Placeholder inteiro dentro do mesmo nó
                newContents[i] = replaceAllInString(cur, placeholders)
                i++
                continue
            }

            // Placeholder começou aqui, mas não terminou: junta com os próximos nós até achar "}}".
            val prefix = cur.substring(0, startIdx)
            val sb = StringBuilder()
            sb.append(cur.substring(startIdx))

            var j = i + 1
            var foundEnd = false
            while (j < newContents.size) {
                sb.append(newContents[j])
                if (sb.indexOf("}}") >= 0) {
                    foundEnd = true
                    break
                }
                j++
            }

            if (!foundEnd) {
                // Não achou o fim (documento zoado) -> faz o melhor possível no nó atual
                newContents[i] = replaceAllInString(cur, placeholders)
                i++
                continue
            }

            val merged = sb.toString()
            val replacedMerged = replaceAllInString(merged, placeholders)
            newContents[i] = prefix + replacedMerged

            // Zera os nós consumidos (i+1..j)
            for (k in i + 1..j) {
                newContents[k] = ""
            }

            i = j + 1
        }

        // 3) Reconstrói o XML substituindo apenas o conteúdo interno de cada <w:t>
        val out = StringBuilder(xml.length + 128)
        var lastPos = 0
        matcher.reset()
        var idxNode = 0
        while (matcher.find()) {
            out.append(xml, lastPos, matcher.start(1))
            out.append(newContents.getOrNull(idxNode) ?: (matcher.group(1) ?: ""))
            out.append(xml, matcher.end(1), matcher.end())
            lastPos = matcher.end()
            idxNode++
        }
        out.append(xml, lastPos, xml.length)
        return out.toString()
    }

    private fun replaceAllInString(text: String, placeholders: Map<String, String>): String {
        var out = text
        placeholders.forEach { (k, v) ->
            val safe = escapeXml(v)

            // cobre variações comuns
            out = out.replace("{{ $k }}", safe)
            out = out.replace("{{$k}}", safe)
            out = out.replace("{{${k}}}", safe)

            // também cobre "{{\nchave\n}}" e tabs
            out = out.replace(Regex("\\{\\{\\s*" + Regex.escape(k) + "\\s*\\}\\}"), safe)
        }
        return out
    }

    private fun escapeXml(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private data class TextNode(
        val contentStart: Int,
        val contentEnd: Int,
        val content: String
    )
}
