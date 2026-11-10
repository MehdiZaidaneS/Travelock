package com.example.travelock.data.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

class VisitPhotoFileProvider : FileProvider()

internal class VisitPhotoFiles(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.cacheDir, "visit-photos")

    fun create(): String {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create the camera output directory.")
        }
        val name = "${UUID.randomUUID()}.jpg"
        if (!file(name).createNewFile()) throw IOException("Could not create camera output.")
        return name
    }

    fun uri(name: String): Uri = FileProvider.getUriForFile(
        appContext,
        "${appContext.packageName}.visitphotos",
        file(name),
    )

    fun readJpeg(name: String): ByteArray {
        // Bound the read rather than trusting only the file size before opening it.
        val bytes = file(name).inputStream().use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > 10 * 1024 * 1024) {
                        throw IOException("The visit photo exceeds 10 MiB.")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outMimeType != "image/jpeg" || options.outWidth <= 0 || options.outHeight <= 0) {
            throw IOException("The camera did not save a valid JPEG.")
        }
        return bytes
    }

    fun delete(name: String) {
        file(name).delete()
    }

    private fun file(name: String): File {
        val id = name.removeSuffix(".jpg")
        require(name.endsWith(".jpg") && UUID.fromString(id).toString() == id) {
            "Invalid camera output filename."
        }
        return File(directory, name)
    }
}
