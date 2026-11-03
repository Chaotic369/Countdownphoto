package com.countdown.widgets.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Copies picked photos into app storage (downscaled, rotation baked in) so widgets can read them later. */
object PhotoStorage {
    private const val MAX_SIDE = 1600

    private fun root(ctx: Context) = File(ctx.filesDir, "photos")

    fun dir(ctx: Context, widgetId: Int): File = File(root(ctx), widgetId.toString()).also { it.mkdirs() }

    fun save(ctx: Context, widgetId: Int, bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

        val degrees = try {
            when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }
        val longest = max(bmp.width, bmp.height)
        val scale = if (longest > MAX_SIDE) MAX_SIDE.toFloat() / longest else 1f
        if (degrees != 0f || scale != 1f) {
            val m = Matrix()
            m.postScale(scale, scale)
            m.postRotate(degrees)
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (out !== bmp) bmp.recycle()
            bmp = out
        }
        val f = File(dir(ctx, widgetId), "p_" + System.nanoTime() + ".jpg")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return f.absolutePath
    }

    fun isOurs(ctx: Context, path: String): Boolean =
        try { File(path).canonicalPath.startsWith(root(ctx).canonicalPath) } catch (e: Exception) { false }

    fun delete(ctx: Context, path: String) {
        if (path.isNotEmpty() && isOurs(ctx, path)) File(path).delete()
    }

    fun deleteAll(ctx: Context, widgetId: Int) {
        File(root(ctx), widgetId.toString()).deleteRecursively()
    }

    /** Decode with subsampling so big photos do not blow the widget memory budget. */
    fun load(path: String, targetW: Int, targetH: Int): Bitmap? {
        if (path.isEmpty()) return null
        return try {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, b)
            if (b.outWidth <= 0) return null
            var s = 1
            while (b.outWidth / (s * 2) >= targetW && b.outHeight / (s * 2) >= targetH) s *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
        } catch (e: Throwable) {
            null
        }
    }

}
