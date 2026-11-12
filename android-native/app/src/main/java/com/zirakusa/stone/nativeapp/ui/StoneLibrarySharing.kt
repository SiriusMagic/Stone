package com.zirakusa.stone.nativeapp.ui

import android.content.Intent
import androidx.core.content.FileProvider
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.StoneBackup
import com.zirakusa.stone.nativeapp.data.transfer.StonePackage
import com.zirakusa.stone.nativeapp.data.transfer.LocalTransferUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun shareStoneBackup(context: android.content.Context) {
    val text = runCatching { StoneBackup.exportText(context) }.getOrNull() ?: return
    context.startActivity(android.content.Intent.createChooser(
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Stone - respaldo de playlists")
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }, "Compartir respaldo de Stone"))
}

internal fun shareStoneLibrary(context: android.content.Context,
    songs: List<com.zirakusa.stone.core.Song>, scope: kotlinx.coroutines.CoroutineScope,
    onState: (LocalTransferUiState) -> Unit): kotlinx.coroutines.Job {
    return scope.launch {
        val packageFile = File(context.cacheDir, "stone-bluetooth.musp")
        onState(LocalTransferUiState(busy = true,
            message = context.getString(R.string.transfer_packaging_indeterminate)))
        var lastProgressAt = 0L
        val result = try {
            Result.success(withContext(Dispatchers.IO) {
                StonePackage.exportToFile(context, packageFile, songs.distinctBy { it.id }) { done, total ->
                    if (total > 0L) {
                        val now = System.currentTimeMillis()
                        if (now - lastProgressAt >= 100L || done >= total) {
                            lastProgressAt = now
                            val progress = (done.toDouble() / total).toFloat().coerceIn(0f, 1f)
                            scope.launch {
                                onState(LocalTransferUiState(
                                    busy = true,
                                    progress = progress,
                                    message = context.getString(
                                        R.string.transfer_packaging,
                                        (progress * 100).toInt()
                                    )
                                ))
                            }
                        }
                    }
                }
            })
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            packageFile.delete()
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
        result.onSuccess {
            onState(LocalTransferUiState(
                progress = 1f,
                message = context.getString(R.string.transfer_package_ready,
                    packageFile.length() / 1024 / 1024)
            ))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", packageFile)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = StonePackage.MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, context.getString(R.string.bluetooth_transfer)))
        }.onFailure {
            onState(LocalTransferUiState(message = it.message ?: context.getString(R.string.musp_failed)))
            android.widget.Toast.makeText(context, it.message ?: context.getString(R.string.musp_failed),
                android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
