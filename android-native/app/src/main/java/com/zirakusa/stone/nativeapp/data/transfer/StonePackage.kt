package com.zirakusa.stone.nativeapp.data.transfer

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import com.zirakusa.stone.core.LyricsParser
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.ImportedLibraryStore
import com.zirakusa.stone.nativeapp.data.library.LibraryUserStore
import com.zirakusa.stone.nativeapp.data.library.SongJson
import com.zirakusa.stone.nativeapp.data.persistence.StoneDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class StonePackageResult(val imported: Int, val skipped: Int, val message: String)

/** Portable Stone container. It packages bytes and metadata without inventing an audio codec. */
object StonePackage {
    const val MIME = "application/vnd.stone.musp"
    private const val FORMAT = "stone-musp"
    private const val VERSION = 1
    private const val MAX_ENTRIES = 10_000
    private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024
    private const val MAX_METADATA_BYTES = 16L * 1024 * 1024

    fun export(context: Context, target: Uri, songs: List<Song>) {
        context.contentResolver.openOutputStream(target, "wt")?.use { output ->
            exportStream(context, output, songs)
        } ?: error("No se pudo crear el paquete Stone")
    }

    fun exportToFile(context: Context, target: File, songs: List<Song>) {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { exportStream(context, it, songs) }
    }

    private fun exportStream(context: Context, output: OutputStream, songs: List<Song>) {
        ZipOutputStream(output.buffered()).use { zip ->
            val entries = JSONArray()
            songs.distinctBy { it.id }.forEachIndexed { index, song ->
                val extension = song.uri.substringBefore('?').substringAfterLast('.', "bin")
                    .lowercase().replace(Regex("[^a-z0-9]"), "").take(8).ifBlank { "bin" }
                val audioEntry = "audio/%05d-%s.%s".format(index, safe(song.title), extension)
                val source = openInput(context, song.uri) ?: error("No se pudo leer ${song.title}")
                val checksum = source.use { input ->
                    zip.putNextEntry(ZipEntry(audioEntry))
                    val value = copyDigest(input, zip)
                    zip.closeEntry()
                    value
                }
                var artworkEntry: String? = null
                embeddedArtwork(context, song.uri)?.let { bytes ->
                    artworkEntry = "artwork/%05d.jpg".format(index)
                    zip.putNextEntry(ZipEntry(artworkEntry)); zip.write(bytes); zip.closeEntry()
                }
                var lyricsEntry: String? = null
                if (song.syncedLyrics.isNotEmpty() || song.lyrics.isNotEmpty()) {
                    lyricsEntry = "lyrics/%05d.lrc".format(index)
                    zip.putNextEntry(ZipEntry(lyricsEntry))
                    zip.write(SongJson.lrc(song).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                entries.put(SongJson.toJson(song).put("audioEntry", audioEntry)
                    .put("checksum", checksum)
                    .put("artworkEntry", artworkEntry ?: JSONObject.NULL)
                    .put("lyricsEntry", lyricsEntry ?: JSONObject.NULL))
            }
            val library = JSONObject().put("format", FORMAT).put("version", VERSION)
                .put("createdAt", System.currentTimeMillis()).put("songs", entries)
            writeText(zip, "library.json", library.toString())
            writeText(zip, "user.json", StoneDatabase.get(context).exportJson().toString())
            writeText(zip, "README.txt", "Stone portable music package v$VERSION\n")
        }
    }

    fun import(context: Context, source: Uri): StonePackageResult {
        val input = context.contentResolver.openInputStream(source) ?: error("No se pudo abrir el paquete")
        return input.use { importStream(context, it) }
    }

    fun importFile(context: Context, source: File): StonePackageResult =
        FileInputStream(source).use { importStream(context, it) }

    private fun importStream(context: Context, input: InputStream): StonePackageResult {
        val temporary = File(context.cacheDir, "musp-${System.currentTimeMillis()}").apply { mkdirs() }
        return try {
            extract(input, temporary)
            val libraryFile = File(temporary, "library.json")
            val library = JSONObject(libraryFile.readText())
            require(library.optString("format") == FORMAT && library.optInt("version") == VERSION) {
                "Formato .musp no compatible"
            }
            val userFile = File(temporary, "user.json")
            if (userFile.exists()) StoneDatabase.get(context).importJson(JSONObject(userFile.readText()))
            val imported = mutableListOf<Song>()
            val remap = linkedMapOf<String, String>()
            val songs = library.getJSONArray("songs")
            val mediaDirectory = File(context.filesDir, "imported-media").apply { mkdirs() }
            repeat(songs.length()) { index ->
                val item = songs.getJSONObject(index)
                val sourceAudio = safeChild(temporary, item.getString("audioEntry"))
                if (!sourceAudio.exists()) return@repeat
                val digest = digest(sourceAudio)
                item.optString("checksum").takeIf { it.isNotBlank() }?.let { expected ->
                    require(expected.equals(digest, ignoreCase = true)) { "Checksum inválido para ${sourceAudio.name}" }
                }
                val extension = sourceAudio.extension.lowercase().ifBlank { "bin" }
                val targetAudio = File(mediaDirectory, "$digest.$extension")
                if (!targetAudio.exists()) sourceAudio.copyTo(targetAudio)
                val artworkEntry = item.optString("artworkEntry", "")
                val targetArtwork = if (artworkEntry.isNotBlank() && artworkEntry != "null") {
                    val sourceArtwork = safeChild(temporary, artworkEntry)
                    val target = File(mediaDirectory, "$digest-artwork.jpg")
                    if (sourceArtwork.exists() && !target.exists()) sourceArtwork.copyTo(target)
                    target.takeIf(File::exists)
                } else null
                val sourceSong = SongJson.fromJson(item)
                val importedSong = sourceSong.copy(uri = Uri.fromFile(targetAudio).toString(),
                    artworkUri = targetArtwork?.let(Uri::fromFile)?.toString().orEmpty())
                imported += importedSong
                remap[sourceSong.id] = importedSong.id
            }
            ImportedLibraryStore(context).merge(imported)
            LibraryUserStore(context).remapSongIds(remap)
            StonePackageResult(imported.size, songs.length() - imported.size,
                "${imported.size} canciones importadas")
        } finally { temporary.deleteRecursively() }
    }

    private fun extract(input: InputStream, destination: File) {
        var entries = 0
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= MAX_ENTRIES) { "El paquete contiene demasiados archivos" }
                val name = entry.name.replace('\\', '/')
                require(name.isNotBlank() && !name.startsWith('/') && !name.contains("..")) {
                    "Ruta inválida en el paquete"
                }
                require(name == "library.json" || name == "user.json" || name == "README.txt" ||
                    name.startsWith("audio/") || name.startsWith("artwork/") || name.startsWith("lyrics/")) {
                    "Contenido no permitido en el paquete"
                }
                if (!entry.isDirectory) {
                    val target = safeChild(destination, name)
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var count: Int
                        while (zip.read(buffer).also { count = it } > 0) {
                            total += count
                            require(total <= MAX_TOTAL_BYTES) { "El paquete supera el límite de tamaño" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        require(File(destination, "library.json").length() <= MAX_METADATA_BYTES) { "Metadatos demasiado grandes" }
    }

    private fun safeChild(root: File, relative: String): File {
        val target = File(root, relative)
        require(target.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Ruta insegura" }
        return target
    }

    private fun writeText(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }

    private fun openInput(context: Context, value: String): InputStream? {
        val uri = Uri.parse(value)
        return if (uri.scheme == "file") uri.path?.let(::File)?.inputStream()
        else context.contentResolver.openInputStream(uri)
    }

    private fun embeddedArtwork(context: Context, value: String): ByteArray? = runCatching {
        val retriever = MediaMetadataRetriever()
        try { retriever.setDataSource(context, Uri.parse(value)); retriever.embeddedPicture }
        finally { retriever.release() }
    }.getOrNull()

    private fun digest(file: File): String {
        val sha = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) { val count = input.read(buffer); if (count <= 0) break; sha.update(buffer, 0, count) }
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyDigest(input: InputStream, output: OutputStream): String {
        val sha = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            sha.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9 _-]"), "")
        .trim().take(50).ifBlank { "stone" }
}
