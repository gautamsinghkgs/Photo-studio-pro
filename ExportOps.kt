package com.photostudio.pro.core.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream

/**
 * Encoding and export. On-device only (SPEC.md C1).
 *
 * ⭐ The target-file-size binary search is the feature that makes Batch Resize
 * worth paying for: sellers are given a hard limit ("max 200 KB per image") by
 * Amazon, eBay or a government portal and need to hit it without eyeballing a
 * quality slider.
 */
object ExportOps {

    enum class Format(val ext: String, val mime: String) {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP_LOSSY("webp", "image/webp"),
        WEBP_LOSSLESS("webp", "image/webp"),
    }

    /** Encode to a byte array at an explicit quality (0-100). JPEG/WebP only. */
    fun encode(src: Bitmap, format: Format, quality: Int = 92): ByteArray {
        val out = ByteArrayOutputStream()
        val compressFormat = when (format) {
            Format.JPEG         -> Bitmap.CompressFormat.JPEG
            Format.PNG          -> Bitmap.CompressFormat.PNG   // quality ignored
            Format.WEBP_LOSSY,
            Format.WEBP_LOSSLESS -> webpFormat()
        }
        src.compress(compressFormat, if (format == Format.WEBP_LOSSLESS) 100 else quality.coerceIn(1, 100), out)
        return out.toByteArray()
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
        else Bitmap.CompressFormat.WEBP

    /**
     * Binary-search the JPEG/WebP quality so the encoded size lands within
     * [tolerancePct] of [targetBytes].
     *
     * Pure and testable in spirit: give it an encoder lambda and it converges.
     * Typically 5-7 iterations (log2 of the quality range).
     *
     * @return (bytes, qualityUsed). If the target is unreachable even at
     *         quality 1, returns the smallest achievable. If the target is
     *         larger than quality 100 produces, returns that.
     */
    /**
     * Delegates to the pure [QualitySearch] so the algorithm is JVM-testable.
     * Highest JPEG quality whose output lands within `tolerancePct` of the
     * target. Needed because government ID-photo portals hard-reject oversized
     * uploads, and "just set quality 70" is wrong for every image.
     *
     * Returns (encodedByteCount, qualityUsed).
     */
    fun searchQualityForTargetSize(
        targetBytes: Int,
        tolerancePct: Int = 10,
        minQuality: Int = 20,
        maxQuality: Int = 100,
        encodeAt: (Int) -> Int,
    ): Pair<Int, Int> = QualitySearch.find(
        targetBytes = targetBytes,
        encodeAt = encodeAt,
        tolerancePct = tolerancePct,
        minQuality = minQuality,
        maxQuality = maxQuality,
    )

    /** Write to the cache dir and return a FileProvider Uri for sharing. */
    fun toShareUri(context: Context, src: Bitmap, name: String, format: Format, quality: Int = 92): Uri? =
        runCatching {
            val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
            val file = java.io.File(dir, "$name.${format.ext}")
            file.outputStream().use { it.write(encode(src, format, quality)) }
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
        }.getOrNull()

    /**
     * Same destination as [toShareUri], but for bytes that are ALREADY encoded.
     *
     * ID Photo searches for the quality that lands under a government portal's KB
     * ceiling; re-encoding from the bitmap here would throw that search away and
     * could put the file back over the limit. Sharing the exact bytes also means
     * the file the user receives, the file a folder-save would write, and the size
     * reported to analytics are all the same three numbers.
     */
    fun toShareUri(context: Context, data: ByteArray, name: String, format: Format): Uri? =
        runCatching {
            val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
            val file = java.io.File(dir, "$name.${format.ext}")
            file.outputStream().use { it.write(data) }
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
        }.getOrNull()

    /**
     * Save into a user-picked folder via the Storage Access Framework.
     * We never request READ/WRITE_EXTERNAL_STORAGE or READ_MEDIA_IMAGES —
     * SAF keeps us out of the sensitive-permission review path entirely.
     */
    fun saveToTree(
        context: Context,
        treeUri: Uri,
        fileName: String,
        data: ByteArray,
        mime: String,
    ): Uri? = runCatching {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching null
        val existing = dir.findFile(fileName)
        val target = existing ?: dir.createFile(mime, fileName) ?: return@runCatching null
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(data) }
        target.uri
    }.getOrNull()

    /** Add a watermark for free-tier exports. Pro removes this. */
    fun watermark(src: Bitmap, text: String): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF
            textSize = (out.width * 0.035f).coerceAtLeast(24f)
            isFakeBoldText = true
            setShadowLayer(6f, 2f, 2f, 0x88000000.toInt())
        }
        val w = paint.measureText(text)
        c.drawText(text, (out.width - w) / 2f, out.height - out.height * 0.05f, paint)
        c.setBitmap(null)
        return out
    }
}
