package com.photostudio.pro.feature.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.photostudio.pro.analytics.Events
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * M9 — Text Scanner. Bundled, so truly offline from the first run.
 * Verified versions on 2026-10-29:
 *   com.google.mlkit:text-recognition:16.0.1              (~4 MB bundled)
 *   com.google.mlkit:text-recognition-devanagari:16.0.1   (~4 MB bundled)
 *
 * Hindi/Devanagari support is a genuine differentiator — most competitors in
 * this category ship Latin only.
 */
class OcrEngine(private val context: Context) {

    enum class Script { LATIN, DEVANAGARI }

    private val latin: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagari: TextRecognizer =
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

    suspend fun recognise(uri: Uri, script: Script): String {
        val image = InputImage.fromFilePath(context, uri)
        val client = if (script == Script.DEVANAGARI) devanagari else latin
        return suspendCancellableCoroutine { cont ->
            client.process(image)
                .addOnSuccessListener { text -> cont.resume(text.text) }
                .addOnFailureListener { e ->
                    Events.logMlKitError("text_recognition_${script.name.lowercase()}", e.javaClass.simpleName)
                    cont.resumeWithException(e)
                }
        }
    }

    /** Run both scripts and keep whichever found more text. */
    suspend fun recogniseAuto(uri: Uri): Pair<String, Script> {
        val l = runCatching { recognise(uri, Script.LATIN) }.getOrDefault("")
        val d = runCatching { recognise(uri, Script.DEVANAGARI) }.getOrDefault("")
        return if (d.length > l.length) d to Script.DEVANAGARI else l to Script.LATIN
    }

    fun close() { runCatching { latin.close() }; runCatching { devanagari.close() } }
}
