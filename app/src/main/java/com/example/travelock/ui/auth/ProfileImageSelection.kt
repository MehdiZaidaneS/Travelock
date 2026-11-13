package com.example.travelock.ui.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ProfileImageSelection {
    suspend fun readJpeg(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = minOf(1.0, 1024.0 / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()),
                    maxOf(1, (info.size.height * scale).toInt()))
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a readable image." }
            val options = BitmapFactory.Options().apply {
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                inSampleSize = sample
            }
            checkNotNull(context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) })
        }
        try {
            // Flatten transparency onto white before writing the JPEG-only bucket format.
            val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            try {
                android.graphics.Canvas(flattened).apply {
                    drawColor(android.graphics.Color.WHITE)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
                ByteArrayOutputStream().use { output ->
                    check(flattened.compress(Bitmap.CompressFormat.JPEG, 85, output))
                    output.toByteArray().also { require(it.isNotEmpty() && it.size <= 10 * 1024 * 1024) }
                }
            } finally { flattened.recycle() }
        } finally { bitmap.recycle() }
    }
}
