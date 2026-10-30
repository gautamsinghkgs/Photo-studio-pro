package com.photostudio.pro.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * All bitmap loading and geometry. Everything here runs on-device (SPEC.md C1).
 *
 * MEMORY DISCIPLINE (SPEC.md architectural rule #4): OOM on mid-range devices
 * is the #1 uninstall driver for photo apps. Every loader here downsamples to a
 * working size instead of decoding full resolution, and callers must recycle.
 */
object BitmapOps {

    /** Decode a Uri, downsampled so the longest edge is at most [maxEdgePx]. */
    fun loadSampled(context: Context, uri: Uri, maxEdgePx: Int = 2048): Bitmap? {
        // Pass 1: bounds only, no pixel allocation.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdgePx) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        // Pass 2: EXIF rotation. Skipping this is why so many apps output
        // sideways photos. SPEC.md M3 acceptance criteria.
        val rotation = readExifRotation(context, uri)
        return if (rotation == 0) raw else rotate(raw, rotation, recycleSource = true)
    }

    fun readExifRotation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            when (ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    fun rotate(src: Bitmap, degrees: Int, recycleSource: Boolean): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (recycleSource && out != src) src.recycle()
        return out
    }

    /** Scale to an exact longest edge, preserving aspect ratio. */
    fun scaleToLongestEdge(src: Bitmap, edgePx: Int, recycleSource: Boolean = false): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= 0 || longest == edgePx) return src
        val ratio = edgePx.toFloat() / longest
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        val out = Bitmap.createScaledBitmap(src, w, h, /* filter = */ true)
        if (recycleSource && out != src) src.recycle()
        return out
    }

    /** Scale to exact pixel dimensions. Used by the ID photo tool. */
    fun scaleExact(src: Bitmap, widthPx: Int, heightPx: Int, recycleSource: Boolean = false): Bitmap {
        val out = Bitmap.createScaledBitmap(src, widthPx, heightPx, true)
        if (recycleSource && out != src) src.recycle()
        return out
    }

    /** Crop a region, clamped to bounds. Returns null if the region is empty. */
    fun crop(src: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap? {
        val cx = x.coerceIn(0, src.width - 1)
        val cy = y.coerceIn(0, src.height - 1)
        val cw = w.coerceIn(1, src.width - cx)
        val ch = h.coerceIn(1, src.height - cy)
        return Bitmap.createBitmap(src, cx, cy, cw, ch)
    }

    /** Composite [fg] over a solid colour, producing an opaque bitmap. */
    fun compositeOverColor(fg: Bitmap, argb: Int): Bitmap {
        val out = Bitmap.createBitmap(fg.width, fg.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(argb)
        c.drawBitmap(fg, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        c.setBitmap(null)
        return out
    }

    /** Composite [fg] over a vertical gradient. */
    fun compositeOverGradient(fg: Bitmap, topArgb: Int, bottomArgb: Int): Bitmap {
        val out = Bitmap.createBitmap(fg.width, fg.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawPaint(Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, out.height.toFloat(), topArgb, bottomArgb,
                android.graphics.Shader.TileMode.CLAMP
            )
        })
        c.drawBitmap(fg, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        c.setBitmap(null)
        return out
    }

    /** Checkerboard so transparency is visible in the preview. */
    fun checkerboard(w: Int, h: Int, cell: Int = 16): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val a = Paint().apply { color = Color.WHITE }
        val b = Paint().apply { color = 0xFFE2E6EC.toInt() }
        var y = 0; var row = 0
        while (y < h) {
            var x = 0; var col = 0
            while (x < w) {
                c.drawRect(Rect(x, y, x + cell, y + cell), if ((row + col) % 2 == 0) a else b)
                x += cell; col++
            }
            y += cell; row++
        }
        c.setBitmap(null)
        return out
    }

    /** Safe recycle helper. */
    fun recycle(vararg bitmaps: Bitmap?) = bitmaps.forEach { runCatching { if (!it?.isRecycled!!) it.recycle() } }
}
