package com.billbox.app.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device text recognition with ML Kit. Nothing leaves the phone. */
class OcrEngine(private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Reads every image and joins the text, page by page. */
    suspend fun read(imagePaths: List<String>): String = buildString {
        for (path in imagePaths) {
            val image = InputImage.fromFilePath(context, Uri.fromFile(File(path)))
            appendLine(recognizer.process(image).await().text)
        }
    }.trim()
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
