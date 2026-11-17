package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object MediaUtils {
    private val bitmapCache = object : LruCache<Int, Bitmap>(32) {}

    fun decodeBase64Bitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        val key = base64.hashCode()
        bitmapCache.get(key)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { bmp ->
                bitmapCache.put(key, bmp)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun compressImageUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 900,
        maxBytes: Int = 240_000
    ): Result<String> {
        return try {
            val resolver = context.contentResolver
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDimension || boundsOpts.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val original = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return Result.failure(IllegalArgumentException("Unable to decode selected image"))

            val scale = minOf(
                maxDimension.toFloat() / original.width.coerceAtLeast(1),
                maxDimension.toFloat() / original.height.coerceAtLeast(1),
                1f
            )
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it !== original) original.recycle() }
            } else {
                original
            }

            var quality = 84
            val baos = ByteArrayOutputStream()
            do {
                baos.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                quality -= 12
            } while (baos.size() > maxBytes && quality >= 24)

            if (scaled !== original) scaled.recycle() else original.recycle()

            val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            if (encoded.length > 720_000) {
                Result.failure(IllegalArgumentException("Compressed image exceeds maximum message size."))
            } else {
                Result.success(encoded)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class EncodedFile(
        val base64: String,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Int
    )

    fun readUriToBase64(
        context: Context,
        uri: Uri,
        maxRawBytes: Int = 520_000
    ): Result<EncodedFile> {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            var fileName = "attachment"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) fileName = cursor.getString(idx) ?: fileName
                }
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.failure(IllegalArgumentException("Cannot read selected file"))
            if (bytes.size > maxRawBytes) {
                return Result.failure(
                    IllegalArgumentException("File is ${(bytes.size / 1024)} KB. Maximum supported attachment size is ${maxRawBytes / 1024} KB.")
                )
            }
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Result.success(EncodedFile(base64, fileName, mimeType, bytes.size))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeBase64ToCacheFile(
        context: Context,
        base64: String,
        fileName: String
    ): File? {
        return try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(dir, safeName)
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            FileOutputStream(file).use { it.write(bytes) }
            file
        } catch (_: Exception) {
            null
        }
    }

    fun openBase64Attachment(
        context: Context,
        base64: String,
        fileName: String,
        mimeType: String
    ): Result<Unit> {
        return try {
            val file = writeBase64ToCacheFile(context, base64, fileName)
                ?: return Result.failure(IllegalStateException("Could not write attachment cache file"))
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open $fileName").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
