package com.billbox.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.billbox.app.data.BillFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Copies bill images and PDFs into private app storage. Images are downscaled to
 * [MAX_SIDE] px and saved as JPEG so a typical receipt is 150–400 KB.
 */
class FileStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "bills").apply { mkdirs() }

    suspend fun importImage(uri: Uri, pageNo: Int = 0): BillFile = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(uri) ?: error("Could not read image")
        val out = File(dir, "${UUID.randomUUID()}.jpg")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle()
        BillFile(billId = 0, path = out.absolutePath, mimeType = "image/jpeg", pageNo = pageNo)
    }

    /** Saves the original PDF plus a JPEG of each of its first [maxPages] pages for preview and OCR. */
    suspend fun importPdf(uri: Uri, maxPages: Int = 5): List<BillFile> = withContext(Dispatchers.IO) {
        val pdf = File(dir, "${UUID.randomUUID()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input -> pdf.outputStream().use { input.copyTo(it) } }
            ?: error("Could not read PDF")
        val result = mutableListOf(BillFile(billId = 0, path = pdf.absolutePath, mimeType = "application/pdf", pageNo = 999))
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until minOf(renderer.pageCount, maxPages)) {
                    renderer.openPage(i).use { page ->
                        val scale = MAX_SIDE.toFloat() / max(page.width, page.height)
                        val bmp = Bitmap.createBitmap(
                            (page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888,
                        )
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val out = File(dir, "${UUID.randomUUID()}.jpg")
                        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                        bmp.recycle()
                        result += BillFile(billId = 0, path = out.absolutePath, mimeType = "image/jpeg", pageNo = i)
                    }
                }
            }
        }
        result
    }

    suspend fun import(uri: Uri, pageNo: Int = 0): List<BillFile> {
        val type = context.contentResolver.getType(uri) ?: ""
        return if (type == "application/pdf" || uri.toString().endsWith(".pdf", true)) importPdf(uri)
        else listOf(importImage(uri, pageNo))
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun openIntent(file: BillFile): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(File(file.path)), file.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun shareIntent(files: List<File>, mime: String, subject: String): Intent {
        val uris = ArrayList(files.map(::uriFor))
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.setType(mime).putExtra(Intent.EXTRA_SUBJECT, subject).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, subject)
    }

    fun deleteAll() {
        dir.deleteRecursively()
    }

    private fun decodeScaled(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
        if (rotation == 0f && scale == 1f) return decoded
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also {
            if (it != decoded) decoded.recycle()
        }
    }

    companion object {
        const val MAX_SIDE = 2000
    }
}
