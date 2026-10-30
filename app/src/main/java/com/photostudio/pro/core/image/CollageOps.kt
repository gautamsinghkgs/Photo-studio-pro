package com.photostudio.pro.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.graphics.Rect

/**
 * Cells come from the pure [LayoutMath.Cell]. Kept as a top-level alias so
 * existing references to `CollageOps.Cell` keep compiling.
 */
typealias Cell = LayoutMath.Cell

/**
 * M7 — Collage / Templates.
 *
 * This is the main **rewarded-ad surface** of the app: a 🔒 template unlocks for
 * 24 hours after one video. That pairing is deliberate — RewardGuard only ever
 * grants small, temporary rewards (TEMPLATE_24H is one of exactly five kinds), so
 * the thing a forger could extract by faking a callback is 24 hours of layouts
 * they could otherwise get by watching a video. There is no path from a rewarded
 * ad to Pro.
 *
 * Layouts are defined as data, not as code branches, so adding a template is a
 * one-line change and the tiling maths stays in exactly one place.
 */
object CollageOps {

    /**
     * One template = a list of cells in NORMALISED coordinates (0..1 of the
     * canvas), so the same template renders at any output size and aspect.
     */

    data class Template(
        val id: String,
        val name: String,
        val cells: List<Cell>,
        /** true = Pro-only. Free gets the first [FREE_COUNT] templates. */
        val pro: Boolean = false,
    ) {
        val count: Int get() = cells.size
    }

    /** How many templates the free tier gets. SPEC.md section 10. */
    const val FREE_COUNT = 10

    // ---- Grid helpers (PURE, unit-tested) ----

    fun grid(cols: Int, rows: Int): List<Cell> = LayoutMath.grid(cols, rows)

    /** `n` equal horizontal strips (stacked vertically). */
    fun rows(n: Int): List<Cell> = LayoutMath.rows(n)

    /** `n` equal vertical strips (side by side). */
    fun cols(n: Int): List<Cell> = LayoutMath.cols(n)

    /** One large cell on the left plus `n` stacked on the right. */
    fun heroLeft(n: Int, heroFraction: Float = 0.58f): List<Cell> =
        LayoutMath.heroLeft(n, heroFraction)

    /** Mirror of [heroLeft]. */
    fun heroRight(n: Int, heroFraction: Float = 0.58f): List<Cell> =
        LayoutMath.heroRight(n, heroFraction)

    /** Four equal quadrants. */
    fun centreFour(): List<Cell> = LayoutMath.centreFour()

    /** Used for 2/3/4-up vertical layouts, which
     * are the most-shared format on marketplace listings and Instagram stories.
     */

    /** `n` equal vertical strips. */

    /**
     * One large cell on the left and `n` stacked on the right — the "hero plus
     * details" layout sellers use for a main product shot plus angles.
     */

    /** Mirror of [heroLeft]. */

    /** Centre cell plus four corners — the classic 5-up. */

    /**
     * The shipped template list. Exactly [FREE_COUNT] non-Pro entries come
     * first, so `templates.take(FREE_COUNT)` is precisely the free tier and no
     * separate flag-filtering can drift out of sync with the paywall copy.
     */
    val templates: List<Template> = buildList {
        // ---- Free (first 10) ----
        add(Template("g1",   "Single",        grid(1, 1)))
        add(Template("g2h",  "Two across",    cols(2)))
        add(Template("g2v",  "Two stacked",   rows(2)))
        add(Template("g3h",  "Three across",  cols(3)))
        add(Template("g3v",  "Three stacked", rows(3)))
        add(Template("g4",   "2 × 2 grid",    grid(2, 2)))
        add(Template("g6",   "2 × 3 grid",    grid(2, 3)))
        add(Template("g9",   "3 × 3 grid",    grid(3, 3)))
        add(Template("hl2",  "Hero + 2",      heroLeft(2)))
        add(Template("hr2",  "Hero right + 2",heroRight(2)))
        // ---- Pro ----
        add(Template("hl3",  "Hero + 3",      heroLeft(3),  pro = true))
        add(Template("hr3",  "Hero right + 3",heroRight(3), pro = true))
        add(Template("hl4",  "Hero + 4",      heroLeft(4),  pro = true))
        add(Template("c4",   "Centre four",   centreFour(), pro = true))
        add(Template("g12",  "3 × 4 grid",    grid(3, 4),   pro = true))
        add(Template("g16",  "4 × 4 grid",    grid(4, 4),   pro = true))
        add(Template("g4v",  "Four stacked",  rows(4),      pro = true))
        add(Template("g4h",  "Four across",   cols(4),      pro = true))
        add(Template("hl5",  "Hero + 5",      heroLeft(5),  pro = true))
        add(Template("g20",  "4 × 5 grid",    grid(4, 5),   pro = true))
    }

    /** FREE_COUNT must land exactly on the free/pro boundary. Guarded by a test. */
    fun freeTemplates() = templates.take(FREE_COUNT)
    fun proTemplates() = templates.drop(FREE_COUNT)

    fun byId(id: String): Template? = templates.firstOrNull { it.id == id }

    /** Common output aspect ratios, as width:height. */
    val ratios = linkedMapOf(
        "1:1 (Instagram)"  to (1f to 1f),
        "4:5 (Portrait)"   to (4f to 5f),
        "9:16 (Story)"     to (9f to 16f),
        "3:4"              to (3f to 4f),
        "16:9 (Landscape)" to (16f to 9f),
        "4:3"              to (4f to 3f),
    )

    // ---------------- Rendering ----------------

    /**
     * Render a collage.
     *
     * @param outW/outH output size in px. Capped by the caller; 2048 on the long
     *        edge is a good default (Pro may go higher).
     * @param spacingPx gap between cells
     * @param borderPx  outer border width
     * @param cornerPx  per-cell corner radius
     */
    fun render(
        photos: List<Bitmap>,
        template: Template,
        outW: Int,
        outH: Int,
        spacingPx: Int = 12,
        borderPx: Int = 24,
        cornerPx: Int = 8,
        bgColor: Int = Color.WHITE,
    ): Bitmap {
        require(photos.isNotEmpty()) { "no photos" }
        require(outW > 0 && outH > 0) { "bad output size" }

        val canvasBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(canvasBmp)
        c.drawColor(bgColor)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val cells = template.cells
        val usableW = outW - 2 * borderPx
        val usableH = outH - 2 * borderPx
        if (usableW <= 0 || usableH <= 0) { c.setBitmap(null); return canvasBmp }

        val gap = spacingPx.coerceAtLeast(0).toFloat()
        val corner = cornerPx.coerceAtLeast(0).toFloat()

        cells.forEachIndexed { i, cell ->
            // Wrap around if the user picked fewer photos than the template has
            // cells, rather than leaving a blank white hole. A blank cell looks
            // like a bug; a repeated photo looks like a choice.
            val src = photos[i % photos.size]
            val left = borderPx + cell.x * usableW + gap / 2f
            val top  = borderPx + cell.y * usableH + gap / 2f
            val right = borderPx + (cell.x + cell.w) * usableW - gap / 2f
            val bot   = borderPx + (cell.y + cell.h) * usableH - gap / 2f
            if (right <= left || bot <= top) return@forEachIndexed

            val dst = RectF(left, top, right, bot)
            val path = Path().apply { addRoundRect(dst, corner, corner, Path.Direction.CW) }
            c.save()
            c.clipPath(path)
            // centre-crop: fill the cell, cropping the overflow, so no letterboxing
            val srcRect = centerCropRect(src.width, src.height, dst.width(), dst.height())
            c.drawBitmap(src, srcRect, dst, paint)
            c.restore()
        }

        c.setBitmap(null)
        return canvasBmp
    }

    /**
     * Centre-crop source rect for a destination of the given aspect.
     * PURE — unit-tested, because an off-by-one here shows up as a visible seam
     * or a stretched product photo.
     */
    fun centerCropRect(srcW: Int, srcH: Int, dstW: Float, dstH: Float): Rect {
        val r = LayoutMath.centerCropRect(srcW, srcH, dstW, dstH)
        return Rect(r[0], r[1], r[2], r[3])
    }
}
