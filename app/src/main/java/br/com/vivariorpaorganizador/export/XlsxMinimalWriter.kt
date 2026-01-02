package br.com.vivariorpaorganizador.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Escritor mínimo de .xlsx (OpenXML) usando inlineStr.
 * Excel abre normalmente e você evita dependência pesada (Apache POI) no Android.
 */
object XlsxMinimalWriter {

    fun write(
        sheetName: String,
        rows: List<List<String>>,
        out: OutputStream
    ) {
        ZipOutputStream(out).use { zip ->
            // [Content_Types].xml
            zipEntry(zip, "[Content_Types].xml", contentTypes())
            // _rels/.rels
            zipEntry(zip, "_rels/.rels", rels())
            // xl/workbook.xml
            zipEntry(zip, "xl/workbook.xml", workbook(sheetName))
            // xl/_rels/workbook.xml.rels
            zipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            // xl/worksheets/sheet1.xml
            zipEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(rows))
        }
    }

    private fun zipEntry(zip: ZipOutputStream, path: String, xml: String) {
        val e = ZipEntry(path)
        zip.putNextEntry(e)
        zip.write(xml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes(): String = """
        <?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
        <Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">
          <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>
          <Default Extension=\"xml\" ContentType=\"application/xml\"/>
          <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>
          <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>
        </Types>
    """.trimIndent()

    private fun rels(): String = """
        <?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
        <Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">
          <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>
        </Relationships>
    """.trimIndent()

    private fun workbook(sheetName: String): String {
        val safeName = escapeXml(sheetName).take(31)
        return """
        <?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
        <workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">
          <sheets>
            <sheet name=\"$safeName\" sheetId=\"1\" r:id=\"rId1\"/>
          </sheets>
        </workbook>
        """.trimIndent()
    }

    private fun workbookRels(): String = """
        <?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
        <Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">
          <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>
        </Relationships>
    """.trimIndent()

    private fun sheetXml(rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetData>")

        rows.forEachIndexed { rIdx, row ->
            val rowNumber = rIdx + 1
            sb.append("<row r=\"$rowNumber\">")
            row.forEachIndexed { cIdx, value ->
                val cellRef = "${colName(cIdx)}$rowNumber"
                val text = escapeXml(value)
                sb.append("<c r=\"$cellRef\" t=\"inlineStr\"><is><t>")
                sb.append(text)
                sb.append("</t></is></c>")
            }
            sb.append("</row>")
        }

        sb.append("</sheetData>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun colName(index: Int): String {
        var i = index
        var name = ""
        do {
            val rem = i % 26
            name = ('A'.code + rem).toChar() + name
            i = i / 26 - 1
        } while (i >= 0)
        return name
    }

    private fun escapeXml(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
