package com.zirakusa.stone.nativeapp.automation.provider

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Bound Service through which Stone provides its tools to Gateway Hub ([ProviderBridge]). Android lets only apps holding the
 * signature permission com.zirakusa.gatewayhub.permission.LINK bind; each message is then checked
 * again from its real sender uid (package + signature), never from what the message says.
 * Work runs off the main thread, a few at a time; a full queue is answered "busy" at once.
 */
class ProviderService : Service() {
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16))
    private val messenger by lazy { Messenger(Handler(Looper.getMainLooper()) { receive(it); true }) }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action != ProviderBridge.ACTION) return null
        if (!ApiMode.loaded) ApiMode.load(StoneSettingsStore(applicationContext), applicationContext)
        return messenger.binder
    }

    private fun receive(msg: Message) {
        if (msg.what != ProviderBridge.MSG_RPC) return
        val replyTo = msg.replyTo ?: return
        val requestId = msg.arg1
        val packages = packageManager.getPackagesForUid(msg.sendingUid)?.toList().orEmpty()
        val refusal = ProviderBridge.callerRefusal(packages) {
            packageManager.checkSignatures(packageName, it) == PackageManager.SIGNATURE_MATCH
        } ?: ProviderBridge.switchRefusal()
        val text = runCatching { msg.data?.getString(ProviderBridge.KEY_MESSAGE) }.getOrNull()
        try {
            workers.execute {
                val reply = if (refusal != null) ProviderBridge.error(null, -32001, refusal) else
                    runCatching { ProviderBridge.handle(text) }.getOrElse { ProviderBridge.error(null, -32603, "Stone failed: ${it.javaClass.simpleName}") }
                if (reply != null) send(replyTo, requestId, reply)
            }
        } catch (_: RejectedExecutionException) {
            send(replyTo, requestId, ProviderBridge.error(null, -32000, "BUSY: Stone is answering other requests."))
        }
    }

    private fun send(to: Messenger, requestId: Int, reply: String) {
        val answer = Message.obtain(null, ProviderBridge.MSG_RPC, requestId, 0).apply { data = Bundle().apply { putString(ProviderBridge.KEY_MESSAGE, reply) } }
        try { to.send(answer) } catch (_: RemoteException) { /* Gateway went away: nothing to answer */ }
    }

    override fun onDestroy() { workers.shutdownNow(); super.onDestroy() }
}
