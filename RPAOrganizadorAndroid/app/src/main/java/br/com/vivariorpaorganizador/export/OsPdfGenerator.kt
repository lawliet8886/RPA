package br.com.vivariorpaorganizador.export

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument

/**
 * Gera a OS em PDF usando as imagens do template como fundo e sobrepondo os valores.
 *
 * Por quê assim? No Android, converter DOCX->PDF “de verdade” é um rolê (bibliotecas enormes/instáveis).
 * Com a imagem do seu template, o PDF sai visualmente idêntico e a gente só “apaga” o placeholder
 * com um retângulo branco e escreve o texto em cima.
 */
object OsPdfGenerator {

    private const val PAGE_W = 595  // A4 em pontos (72dpi)
    private const val PAGE_H = 842

    private val pages = listOf(
        "os_template/template_os-1.png",
        "os_template/template_os-2.png",
        "os_template/template_os-3.png"
    )

    /**
     * Layout padrão dos campos (coordenadas em pontos A4).
     *
     * Observação importante: isso é “bom o suficiente” para a maioria dos casos,
     * mas você pode ajustar fino depois (o próximo passo do projeto é uma tela de calibração).
     */
    private val fields: List<Field> = listOf(
        // Página 1
        Field(page = 0, key = "nome_profissional", x = 205f, y = 273f, w = 340f, textSize = 10.5f),
        Field(page = 0, key = "funcao", x = 88f, y = 287f, w = 260f, textSize = 10.5f),
        Field(page = 0, key = "cpf", x = 395f, y = 287f, w = 170f, textSize = 10.5f),
        Field(page = 0, key = "pis", x = 125f, y = 300f, w = 220f, textSize = 10.5f),
        Field(page = 0, key = "endereco", x = 120f, y = 313f, w = 430f, textSize = 10.0f),

        // Página 3
        Field(page = 2, key = "funcao", x = 108f, y = 300f, w = 340f, textSize = 10.5f),
        Field(page = 2, key = "datas_prestacao_servico", x = 88f, y = 386f, w = 460f, textSize = 10.5f),
        Field(page = 2, key = "data_envio_os", x = 355f, y = 720f, w = 160f, textSize = 10.5f)
    )

    fun writeFilledOsPdf(context: Context, placeholders: Map<String, String>, out: java.io.OutputStream) {
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { idx, assetPath ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, idx + 1).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas

                drawTemplatePage(context, canvas, assetPath)

                // sobrepõe campos desta página
                fields.filter { it.page == idx }.forEach { f ->
                    val value = placeholders[f.key].orEmpty()
                    if (value.isNotBlank()) drawField(canvas, f, value)
                }

                doc.finishPage(page)
            }

            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun drawTemplatePage(context: Context, canvas: Canvas, assetPath: String) {
        val bmp = context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
        val dest = Rect(0, 0, PAGE_W, PAGE_H)
        canvas.drawBitmap(bmp, null, dest, null)
        bmp.recycle()
    }

    private fun drawField(canvas: Canvas, f: Field, value: String) {
        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = f.textSize
        }
        val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }

        val lines = wrapToWidth(value, paintText, f.w)
        var y = f.y
        val lineH = paintText.fontSpacing

        lines.forEach { line ->
            val textW = paintText.measureText(line).coerceAtLeast(1f)
            val rect = RectF(
                f.x - 2f,
                y - lineH + 3f,
                (f.x + minOf(f.w, textW)) + 4f,
                y + 4f
            )
            canvas.drawRoundRect(rect, 2f, 2f, paintBg)
            canvas.drawText(line, f.x, y, paintText)
            y += lineH
        }
    }

    private fun wrapToWidth(text: String, paint: Paint, maxWidth: Float): List<String> {
        val cleaned = text.replace("\r", "").trim()
        if (cleaned.isBlank()) return emptyList()

        val paragraphs = cleaned.split("\n")
        val out = mutableListOf<String>()

        for (p in paragraphs) {
            val words = p.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) {
                out.add("")
                continue
            }
            var line = StringBuilder()
            for (w in words) {
                val candidate = if (line.isEmpty()) w else "${line} $w"
                if (paint.measureText(candidate) <= maxWidth) {
                    if (line.isNotEmpty()) line.append(' ')
                    line.append(w)
                } else {
                    if (line.isNotEmpty()) out.add(line.toString())
                    line = StringBuilder(w)
                }
            }
            if (line.isNotEmpty()) out.add(line.toString())
        }

        return out
    }

    private data class Field(
        val page: Int,
        val key: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val textSize: Float = 10f
    )
}
