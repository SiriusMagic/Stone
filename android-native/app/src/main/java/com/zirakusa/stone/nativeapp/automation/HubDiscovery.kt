package com.zirakusa.stone.nativeapp.automation

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How the Gateway Hub app finds Stone: MainActivity declares [ACTION] with the module prefix in
 * [META_PREFIX] (AndroidManifest.xml). Being found turns nothing on: when the hub app opens Stone
 * with [ACTION], Stone only shows Ajustes › Modo API, where the user decides.
 */
object HubDiscovery {
    const val ACTION = "com.zirakusa.gatewayhub.action.APP"
    const val META_PREFIX = "com.zirakusa.gatewayhub.module"

    /** true until the UI has opened Ajustes › Modo API for a request from the hub app. */
    var openApiSettings by mutableStateOf(false)
        private set

    /** Reads only the action; no extra or data from the calling app is used. */
    fun handle(intent: Intent?) { if (intent?.action == ACTION) openApiSettings = true }
    fun consumed() { openApiSettings = false }
}
