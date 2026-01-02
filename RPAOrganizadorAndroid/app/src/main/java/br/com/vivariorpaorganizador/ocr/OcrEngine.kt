package br.com.vivariorpaorganizador.ocr

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OcrEngine(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(uri: Uri): String = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: ""
        return@withContext try {
            when {
                mime.contains("pdf", ignoreCase = true) -> recognizePdf(cr, uri)
                mime.startsWith("image/") || mime.isBlank() -> recognizeImage(cr, uri)
                else -> recognizeImage(cr, uri)
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun recognizeImage(cr: ContentResolver, uri: Uri): String {
        cr.openInputStream(uri).use { input ->
            val bmp = BitmapFactory.decodeStream(input) ?: return ""
            val img = InputImage.fromBitmap(bmp, 0)
            val result = Tasks.await(recognizer.process(img))
            return result.text ?: ""
        }
    }

    private fun recognizePdf(cr: ContentResolver, uri: Uri): String {
        val pfd: ParcelFileDescriptor = cr.openFileDescriptor(uri, "r") ?: return ""
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val sb = StringBuilder()
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val bmp = renderPage(page)
                        val img = InputImage.fromBitmap(bmp, 0)
                        val result = Tasks.await(recognizer.process(img))
                        val text = result.text ?: ""
                        if (text.isNotBlank()) {
                            sb.append("\n\n==== PAGINA ").append(i + 1).append(" ====\n")
                            sb.append(text)
                        }
                    }
                }
                sb.toString().trim()
            }
        }
    }

    private fun renderPage(page: PdfRenderer.Page): Bitmap {
        // Renderiza numa escala moderada pra equilibrar precisão x performance.
        val scale = 2
        val width = page.width * scale
        val height = page.height * scale
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }
}
