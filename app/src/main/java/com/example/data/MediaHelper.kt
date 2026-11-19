package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

object MediaHelper {
    private const val TAG = "MediaHelper"
    private const val MAX_BASE64_MEDIA_CHARS = 400_000
    private const val MAX_BASE64_THUMB_CHARS = 160_000
    private const val MAX_RAW_SYNC_BYTES = 260_000L

    fun createCameraCaptureUri(context: Context): Pair<Uri, File> {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    suspend fun prepareAvatarBase64(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val localFile = copyUriToAppMedia(context, uri, "avatar_${System.currentTimeMillis()}.jpg")
                val sampled = decodeSampledBitmap(localFile, maxDimension = 320)
                    ?: throw IllegalStateException("Gagal membaca gambar profil.")
                val side = minOf(sampled.width, sampled.height)
                val xOffset = (sampled.width - side) / 2
                val yOffset = (sampled.height - side) / 2
                val square = Bitmap.createBitmap(sampled, xOffset, yOffset, side, side)
                val scaled = scaleBitmapIfNeeded(square, 256)
                val base64 = compressBitmapToBase64(scaled, 100_000)
                if (scaled !== square) scaled.recycle()
                if (square !== sampled) square.recycle()
                sampled.recycle()
                runCatching { localFile.delete() }
                base64
            }
        }

    suspend fun prepareImageAttachment(context: Context, uri: Uri): Result<PreparedAttachment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (displayName, rawSize) = queryFileMeta(context, uri, "foto_${System.currentTimeMillis()}.jpg")
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val localFile = copyUriToAppMedia(context, uri, displayName)

                val sampledBitmap = decodeSampledBitmap(localFile, maxDimension = 1080)
                    ?: throw IllegalStateException("Gagal membaca file gambar.")
                val base64 = compressBitmapToBase64(sampledBitmap, MAX_BASE64_MEDIA_CHARS)
                sampledBitmap.recycle()

                PreparedAttachment(
                    messageType = MessageType.IMAGE,
                    fileName = displayName.take(240),
                    mimeType = mime.take(120),
                    fileSizeBytes = if (rawSize > 0L) rawSize else localFile.length(),
                    mediaData = base64,
                    localFilePath = localFile.absolutePath.take(480)
                )
            }
        }

    suspend fun prepareImageForStorage(context: Context, uri: Uri): Result<PreparedStorageImage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (displayName, _) = queryFileMeta(context, uri, "foto_${System.currentTimeMillis()}.jpg")
                val localFile = copyUriToAppMedia(context, uri, displayName)

                val uploadBitmap = decodeSampledBitmap(localFile, maxDimension = 1600)
                    ?: throw IllegalStateException("Gagal membaca gambar dari galeri.")
                val out = ByteArrayOutputStream()
                uploadBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                val jpegBytes = out.toByteArray()

                val fallbackScaled = scaleBitmapIfNeeded(uploadBitmap, 960)
                val fallbackBase64 = compressBitmapToBase64(fallbackScaled, MAX_BASE64_MEDIA_CHARS)
                if (fallbackScaled !== uploadBitmap) fallbackScaled.recycle()
                uploadBitmap.recycle()

                val finalName = if (displayName.endsWith(".jpg", ignoreCase = true) ||
                    displayName.endsWith(".jpeg", ignoreCase = true)
                ) {
                    displayName
                } else {
                    "${displayName.substringBeforeLast(".")}.jpg"
                }

                PreparedStorageImage(
                    fileName = finalName.take(240),
                    mimeType = "image/jpeg",
                    jpegBytes = jpegBytes,
                    fileSizeBytes = jpegBytes.size.toLong(),
                    localFilePath = localFile.absolutePath.take(480),
                    fallbackBase64 = fallbackBase64
                )
            }
        }

    suspend fun prepareVideoAttachment(context: Context, uri: Uri): Result<PreparedAttachment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (displayName, rawSize) = queryFileMeta(context, uri, "video_${System.currentTimeMillis()}.mp4")
                val mime = context.contentResolver.getType(uri) ?: "video/mp4"
                val localFile = copyUriToAppMedia(context, uri, displayName)

                var durationMs: Long? = null
                var thumbBase64: String? = null
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(localFile.absolutePath)
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                    if (frame != null) {
                        val scaled = scaleBitmapIfNeeded(frame, 640)
                        thumbBase64 = compressBitmapToBase64(scaled, MAX_BASE64_THUMB_CHARS)
                        if (scaled !== frame) scaled.recycle()
                        frame.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Video metadata extraction warning: ${e.message}")
                } finally {
                    runCatching { retriever.release() }
                }

                val actualSize = if (rawSize > 0L) rawSize else localFile.length()
                val syncedClipBase64 = if (localFile.length() <= MAX_RAW_SYNC_BYTES) {
                    val encoded = Base64.encodeToString(localFile.readBytes(), Base64.NO_WRAP)
                    encoded.takeIf { it.length <= MAX_BASE64_MEDIA_CHARS }
                } else {
                    null
                }

                PreparedAttachment(
                    messageType = MessageType.VIDEO,
                    fileName = displayName.take(240),
                    mimeType = mime.take(120),
                    fileSizeBytes = actualSize,
                    durationMs = durationMs?.coerceIn(0L, 86_400_000L),
                    mediaData = syncedClipBase64,
                    thumbnailData = thumbBase64,
                    localFilePath = localFile.absolutePath.take(480)
                )
            }
        }

    suspend fun prepareDocumentAttachment(context: Context, uri: Uri): Result<PreparedAttachment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (displayName, rawSize) = queryFileMeta(context, uri, "dokumen_${System.currentTimeMillis()}")
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val localFile = copyUriToAppMedia(context, uri, displayName)
                val actualSize = if (rawSize > 0L) rawSize else localFile.length()

                val syncedBase64 = if (localFile.length() <= MAX_RAW_SYNC_BYTES) {
                    val encoded = Base64.encodeToString(localFile.readBytes(), Base64.NO_WRAP)
                    encoded.takeIf { it.length <= MAX_BASE64_MEDIA_CHARS }
                } else {
                    null
                }

                PreparedAttachment(
                    messageType = MessageType.FILE,
                    fileName = displayName.take(240),
                    mimeType = mime.take(120),
                    fileSizeBytes = actualSize,
                    mediaData = syncedBase64,
                    localFilePath = localFile.absolutePath.take(480)
                )
            }
        }

    suspend fun prepareVoiceClipForStorage(
        audioFile: File,
        recordedDurationMs: Long
    ): Result<PreparedStorageAudio> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(audioFile.exists() && audioFile.length() > 0L) {
                    "File rekaman suara tidak ditemukan."
                }
                var resolvedDuration = recordedDurationMs.coerceAtLeast(500L)
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(audioFile.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.takeIf { it > 0L }
                        ?.let { resolvedDuration = it }
                } catch (e: Exception) {
                    Log.w(TAG, "Audio duration extraction warning: ${e.message}")
                } finally {
                    runCatching { retriever.release() }
                }

                val rawBytes = audioFile.readBytes()
                val fallbackBase64 = if (rawBytes.size <= 320_000) {
                    Base64.encodeToString(rawBytes, Base64.NO_WRAP)
                        .takeIf { it.length <= MAX_BASE64_MEDIA_CHARS }
                } else {
                    null
                }

                PreparedStorageAudio(
                    fileName = audioFile.name.take(240),
                    mimeType = "audio/mp4",
                    audioBytes = rawBytes,
                    fileSizeBytes = rawBytes.size.toLong(),
                    durationMs = resolvedDuration.coerceIn(500L, 86_400_000L),
                    localFilePath = audioFile.absolutePath.take(480),
                    fallbackBase64 = fallbackBase64
                )
            }
        }

    fun decodeBase64ToBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveLocalOrSyncedFile(context: Context, message: ChatMessage): File? =
        withContext(Dispatchers.IO) {
            val existingLocal = message.localFilePath?.let { File(it) }
            if (existingLocal != null && existingLocal.exists() && existingLocal.length() > 0L) {
                return@withContext existingLocal
            }
            val data = message.mediaData ?: return@withContext null
            return@withContext try {
                val safeName = (message.fileName ?: "lampiran_${message.id}")
                    .replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
                val dir = File(context.filesDir, "media").apply { mkdirs() }
                val target = File(dir, "${message.id}_$safeName")
                if (!target.exists() || target.length() == 0L) {
                    val bytes = Base64.decode(data, Base64.DEFAULT)
                    FileOutputStream(target).use { it.write(bytes) }
                }
                target
            } catch (e: Exception) {
                Log.w(TAG, "Failed materializing synced file: ${e.message}")
                null
            }
        }

    fun openFileExternally(context: Context, file: File, mimeType: String?): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Buka dengan…").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open file externally: ${e.message}")
            false
        }
    }

    fun formatFileSize(bytes: Long?): String {
        val value = bytes ?: return "Ukuran tidak diketahui"
        return when {
            value < 1024L -> "$value B"
            value < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", value / 1024.0)
            else -> String.format(Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
        }
    }

    fun formatDurationMs(durationMs: Long?): String {
        val totalSeconds = ((durationMs ?: 0L) / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun formatCallSeconds(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hrs = safe / 3600L
        val mins = (safe % 3600L) / 60L
        val secs = safe % 60L
        return if (hrs > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    private fun queryFileMeta(context: Context, uri: Uri, fallbackName: String): Pair<String, Long> {
        var name = fallbackName
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    private fun copyUriToAppMedia(context: Context, uri: Uri, originalName: String): File {
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val sanitized = originalName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val dest = File(dir, "${UUID.randomUUID().toString().take(8)}_$sanitized")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Tidak dapat membuka file yang dipilih.")
        return dest
    }

    private fun decodeSampledBitmap(file: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension * 2 ||
            bounds.outHeight / sampleSize > maxDimension * 2
        ) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val scaled = scaleBitmapIfNeeded(decoded, maxDimension)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun compressBitmapToBase64(bitmap: Bitmap, maxBase64Chars: Int): String {
        var quality = 78
        var encoded = ""
        while (quality >= 28) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (encoded.length <= maxBase64Chars) return encoded
            quality -= 12
        }
        val smaller = scaleBitmapIfNeeded(bitmap, 480)
        val out = ByteArrayOutputStream()
        smaller.compress(Bitmap.CompressFormat.JPEG, 55, out)
        if (smaller !== bitmap) smaller.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP).take(maxBase64Chars)
    }
}
