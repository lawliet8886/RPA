package br.com.vivariorpaorganizador.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import br.com.vivariorpaorganizador.data.ExtractedData
import br.com.vivariorpaorganizador.data.Parsing
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

class OcrManager {

    private val canceled = AtomicBoolean(false)

    fun cancelCurrent() {
        canceled.set(true)
    }

    data class OcrResult(
        val rawText: String,
        val extracted: ExtractedData,
    )

    suspend fun extractAll(context: Context, uri: Uri): OcrResult {
        canceled.set(false)
        val mime = context.contentResolver.getType(uri) ?: ""

        val fullText = if (mime.contains("pdf") || uri.toString().lowercase().endsWith(".pdf")) {
            ocrPdfAllPages(context, uri)
        } else {
            ocrImage(context, uri)
        }

        val ex = ExtractedData(
            cpf = Parsing.findCpf(fullText),
            pis = Parsing.findPis(fullText),
            bancoAgencia = Parsing.findAgencia(fullText),
            bancoConta = Parsing.findConta(fullText),
            bancoTitular = Parsing.findTitular(fullText),
            corenNumero = Parsing.findCorenNumero(fullText),
            corenValidadeIso = Parsing.findCorenValidadeIso(fullText),
            nadaConstaMesAno = Parsing.findYearMonth(fullText),
        )

        return OcrResult(rawText = fullText, extracted = ex)
    }

    private suspend fun ocrImage(context: Context, uri: Uri): String {
        val bmp = decodeBitmap(context, uri)
        return recognizeBitmap(bmp)
    }

    private suspend fun ocrPdfAllPages(context: Context, uri: Uri): String {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Não consegui abrir o PDF")

        pfd.use {
            PdfRenderer(it).use { renderer ->
                val sb = StringBuilder()
                for (i in 0 until renderer.pageCount) {
                    if (canceled.get()) throw CancellationException("OCR cancelado")
                    val page = renderer.openPage(i)
                    page.use {
                        val width = (page.width * 2)
                        val height = (page.height * 2)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val text = recognizeBitmap(bitmap)
                        sb.append("\n\n--- PÁGINA ${i + 1} ---\n")
                        sb.append(text)
                    }
                }
                return sb.toString().trim()
            }
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Não consegui ler a imagem")
        input.use {
            return BitmapFactory.decodeStream(it)
                ?: throw IllegalArgumentException("Imagem inválida")
        }
    }

    private suspend fun recognizeBitmap(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return result.text ?: ""
    }
}
