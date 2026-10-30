package com.photostudio.pro.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.rendering.PDFRenderer

/**
 * M8 — Image ↔ PDF.
 *
 * Uses PdfBox-Android (Apache 2.0), which is already a declared dependency and
 * runs entirely on-device. No cloud conversion service, so documents never
 * leave the phone — which for a seller means invoices and for a student means
 * ID documents, both of which people are rightly unwilling to upload.
 */
object PdfOps {

    /** Page sizes in PDF points (1 pt = 1/72 inch). */
    val pageSizes = linkedMapOf(
        "A4"      to PDRectangle.A4,
        "Letter"  to PDRectangle.LETTER,
        "Legal"   to PDRectangle.LEGAL,
        "A5"      to PDRectangle.A5,
        "A3"      to PDRectangle.A3,
        "Fit to image" to null,      // page adopts the image's own aspect
    )

    enum class Fit { FIT, FILL }

    private fun ensureLoaded(ctx: Context) {
        // Idempotent; PhotoStudioApp already calls this once at startup.
        runCatching { PDFBoxResourceLoader.init(ctx.applicationContext) }
    }

    /**
     * Place one image on one page.
     *
     * PURE maths for the placement rect is in [placeRect] so it can be
     * unit-tested; this function only does the PdfBox I/O around it.
     */
    fun imagesToPdf(
        ctx: Context,
        bitmaps: List<Bitmap>,
        pageSizeName: String = "A4",
        fit: Fit = Fit.FIT,
        marginPt: Float = 24f,
        jpegQuality: Float = 0.9f,
    ): ByteArray {
        require(bitmaps.isNotEmpty()) { "no images" }
        ensureLoaded(ctx)

        PDDocument().use { doc ->
            bitmaps.forEach { bmp ->
                val explicit = pageSizes[pageSizeName]
                val mediaBox = if (explicit != null) explicit
                else PDRectangle(bmp.width.toFloat(), bmp.height.toFloat())

                val page = PDPage(mediaBox)
                doc.addPage(page)

                val m = marginPt.coerceAtLeast(0f)
                val availW = (mediaBox.width - 2 * m).coerceAtLeast(1f)
                val availH = (mediaBox.height - 2 * m).coerceAtLeast(1f)
                val (x, y, w, h) = placeRect(bmp.width, bmp.height, availW, availH, m, fit)

                // JPEG for photos (much smaller), lossless only when there is an
                // alpha channel — JPEGFactory would flatten it to black.
                val hasAlpha = bmp.hasAlpha()
                val pdImage = if (hasAlpha || jpegQuality >= 1f) LosslessFactory.createFromImage(doc, bmp)
                              else JPEGFactory.createFromImage(doc, bmp, jpegQuality)

                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImage, x, y, w, h)
                }
            }
            val out = java.io.ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    /**
     * Where to place an image inside the available box, in PDF coordinates.
     *
     * ⚠️ PDF's origin is the BOTTOM-left, not the top-left. Getting this wrong
     * puts every image upside-down or off the page, and it is the single most
     * common bug in image-to-PDF code. PURE — unit-tested.
     *
     * @return (x, y, w, h) with y measured from the bottom edge of the page.
     */
    fun placeRect(
        imgW: Int, imgH: Int,
        availW: Float, availH: Float,
        marginPt: Float,
        fit: Fit,
    ): FloatArray = LayoutMath.placeRectOnPage(
        imgW, imgH, availW, availH, marginPt, fill = fit == Fit.FILL)

    /**
     * Render each page of a PDF to a Bitmap.
     *
     * @param dpi 150 is a good default: legible, and small enough that a
     *        50-page document does not exhaust memory. Pages are rendered one
     *        at a time and returned lazily via the callback so the caller can
     *        write each to disk before the next is decoded.
     */
    fun pdfToImages(
        ctx: Context,
        pdfBytes: ByteArray,
        dpi: Int = 150,
        maxPages: Int = 100,
        onPage: (index: Int, total: Int, bmp: Bitmap) -> Unit,
    ): Int {
        ensureLoaded(ctx)
        PDDocument.load(pdfBytes).use { doc ->
            val total = doc.numberOfPages.coerceAtMost(maxPages)
            if (total == 0) return 0
            val renderer = PDFRenderer(doc)
            for (i in 0 until total) {
                val bmp = renderer.renderImageWithDPI(i, dpi.toFloat())
                onPage(i, total, bmp)
            }
            return total
        }
    }

    /**
     * Convenience: combine photos into a single-page-per-photo PDF and write it
     * through SAF so the user picks the destination. Returns the saved Uri.
     */
    fun savePdfToTree(
        ctx: Context, treeUri: android.net.Uri, name: String, bytes: ByteArray,
    ): android.net.Uri? = ExportOps.saveToTree(ctx, treeUri, name, bytes, "application/pdf")
}
