package br.com.vivariorpaorganizador.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.WorkerThread
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object DocumentAnalyzer {

    @WorkerThread
    fun ocrText(context: Context, uri: Uri): String {
        val bitmap = renderToBitmap(context.contentResolver, uri) ?: return ""
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val task = recognizer.process(image)
        val result = Tasks.await(task)
        recognizer.close()
        return result.text ?: ""
    }

    private fun renderToBitmap(resolver: ContentResolver, uri: Uri): Bitmap? {
        val type = resolver.getType(uri) ?: ""
        return try {
            when {
                type.startsWith("image/") -> BitmapUtils.decodeBitmap(resolver, uri)
                type == "application/pdf" || uri.toString().lowercase().endsWith(".pdf") -> renderPdfFirstPage(resolver, uri)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun renderPdfFirstPage(resolver: ContentResolver, uri: Uri): Bitmap? {
        val pfd: ParcelFileDescriptor = resolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }
}
