package com.ivanherboch.chessduck.feature

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.ivanherboch.chessduck.util.runCatchingSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sortie des images de [ShareCard] : fichier temporaire + FileProvider + feuille de partage Android,
 * ou enregistrement dans la galerie (MediaStore). Aucun rendu ici.
 */
internal object ShareExporter {
    /** Durée de vie des PNG temporaires de partage (le temps que l'application destinataire les lise). */
    private const val SHARE_CACHE_TTL_MS = 60 * 60 * 1000L

    /** Supprime les PNG de partage périmés pour éviter que le cache ne grossisse indéfiniment. */
    private fun purgeShareCache(dir: File, now: Long = System.currentTimeMillis()) {
        dir.listFiles()?.forEach { f -> if (now - f.lastModified() > SHARE_CACHE_TTL_MS) f.delete() }
    }

    /**
     * Compression PNG et écriture sur Dispatchers.IO, puis ouverture du sélecteur de partage sur le fil principal.
     * Renvoie faux si l'image n'a pas pu être préparée.
     */
    suspend fun send(context: Context, bmp: Bitmap, name: String, text: String, chooser: String): Boolean {
        val uri = withContext(Dispatchers.IO) {
            runCatchingSuspend {
                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                purgeShareCache(dir)
                val file = File(dir, name)
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            }.getOrNull()
        } ?: return false
        return withContext(Dispatchers.Main) {
            runCatching {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, chooser).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        }
    }

    /**
     * Enregistre l'image dans la galerie (Images/ChessDuck), sur Dispatchers.IO.
     * Renvoie un message de confirmation, ou null en cas d'échec.
     * Android 10+ : MediaStore, sans aucune permission. Versions antérieures : dossier d'images de l'application.
     */
    suspend fun save(context: Context, bmp: Bitmap, name: String): String? = withContext(Dispatchers.IO) {
        runCatchingSuspend {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ChessDuck")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatchingSuspend null
                resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return@runCatchingSuspend null
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Image enregistrée dans la galerie (Images/ChessDuck)."
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ChessDuck").apply { mkdirs() }
                val file = File(dir, name)
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                "Image enregistrée : ${file.absolutePath}"
            }
        }.getOrNull()
    }
}
