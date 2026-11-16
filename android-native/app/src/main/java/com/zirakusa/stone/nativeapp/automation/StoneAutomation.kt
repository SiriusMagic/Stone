package com.zirakusa.stone.nativeapp.automation

import android.content.IntentSender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** A CRITICAL action waiting for the user's answer on screen. */
class PendingConfirmation(val action: String, val summary: String, val answer: CompletableDeferred<Boolean>)

/** Android's own write dialog for MediaStore files, asked once for a whole batch. */
class WriteAccessRequest(val count: Int, val sender: IntentSender, val answer: CompletableDeferred<Boolean>)

/** Progress of a batch tool, shown in Ajustes › Modo API while it runs. */
data class BatchProgress(val action: String, val done: Int, val total: Int, val failed: Int = 0)

/**
 * Single gateway between agents and Stone. It validates parameters, enforces each token's
 * [AccessMode] and the per-tool switches, and runs actions on the main thread through the
 * [host] the UI registers while it is alive.
 */
object StoneAutomation {
    @Volatile var host: AutomationHost? = null
    /** Mode of the request being executed; only used to filter stone_help results. */
    @Volatile internal var currentAccess: AccessMode = AccessMode.WRITE

    private val mutableConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val confirmation = mutableConfirmation.asStateFlow()
    private val mutableWriteRequest = MutableStateFlow<WriteAccessRequest?>(null)
    val writeRequest = mutableWriteRequest.asStateFlow()
    private val mutableProgress = MutableStateFlow<BatchProgress?>(null)
    val progress = mutableProgress.asStateFlow()
    private val mutableHistory = MutableStateFlow<List<ActionRecord>>(emptyList())
    val history = mutableHistory.asStateFlow()

    val actions: List<StoneAction> by lazy { StoneActions.all() }

    /** Actions a token with [access] may use (its mode and Ajustes › Modo API › Herramientas). */
    fun available(access: AccessMode): List<StoneAction> = actions.filter { ApiMode.permits(it, access) }

    fun find(toolName: String) = actions.firstOrNull { it.toolName == toolName || it.name == toolName }

    /** Runs [toolName] with [arguments]; throws [ActionException] with a code and message for the agent. */
    suspend fun execute(toolName: String, arguments: JSONObject, access: AccessMode = AccessMode.WRITE, token: String = ""): Any {
        val action = find(toolName) ?: throw ActionException("Unknown tool: $toolName", "UNKNOWN_TOOL",
            "Call tools/list or stone_help.")
        return try {
            if (action.toolName in ApiMode.disabledTools) throw ActionException("The user turned off '${action.toolName}' for agents.",
                "TOOL_DISABLED", "The user can enable it in Stone > Ajustes > Modo API > Herramientas.")
            if (!access.permits(action.risk)) throw ActionException("This token is read-only.", "READ_ONLY_TOKEN",
                "The user can change the token's mode in Stone > Ajustes > Modo API > Seguridad.")
            validate(action, arguments)
            val dryRun = arguments.optBoolean("dryRun", false)
            if (!dryRun && access.needsApproval(action.risk) && !confirm(action, arguments))
                throw ActionException("The user did not approve this change on the phone.", "USER_DENIED",
                    "Ask the user, or use a token with 'Acceso completo'.")
            currentAccess = access
            val result = withContext(Dispatchers.Main) { action.run(arguments) }
            record(action, RecordStatus.OK, arguments.toString(), token, "")
            result
        } catch (error: Exception) {
            val failure = ActionException.of(error)
            val status = if (failure.code in setOf("USER_DENIED", "READ_ONLY_TOKEN", "TOOL_DISABLED", "ANDROID_CONSENT"))
                RecordStatus.DENIED else RecordStatus.FAILED
            record(action, status, failure.message ?: failure.code, token, failure.code)
            throw failure
        }
    }

    private fun validate(action: StoneAction, arguments: JSONObject) {
        val known = action.params.associateBy { it.name }
        arguments.keys().forEach { key ->
            if (key !in known) throw ActionException("Unknown parameter '$key'", "INVALID_PARAM",
                "Valid parameters: ${known.keys.joinToString()}")
        }
        action.params.forEach { param ->
            if (!arguments.has(param.name) || arguments.isNull(param.name)) {
                if (param.required) throw ActionException("Missing required parameter '${param.name}'", "INVALID_PARAM")
                return@forEach
            }
            val value = arguments.get(param.name)
            val valid = when (param.type) {
                "string" -> value is String
                "integer" -> value is Int || value is Long
                "number" -> value is Number
                "boolean" -> value is Boolean
                else -> true
            }
            if (!valid) throw ActionException("Parameter '${param.name}' must be ${param.type}", "INVALID_PARAM")
            param.options?.let { if (value !in it) throw ActionException("'${param.name}' must be one of $it", "INVALID_PARAM") }
        }
    }

    private suspend fun confirm(action: StoneAction, arguments: JSONObject): Boolean {
        if (host == null) throw ActionException("Open Stone on the phone to approve this change.", "STONE_CLOSED")
        val pending = PendingConfirmation(action.name, action.summary(arguments), CompletableDeferred())
        mutableConfirmation.value = pending
        return try {
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { pending.answer.await() } ?: false
        } finally {
            mutableConfirmation.update { if (it === pending) null else it }
        }
    }

    fun answer(pending: PendingConfirmation, allow: Boolean) {
        pending.answer.complete(allow)
        mutableConfirmation.update { if (it === pending) null else it }
    }

    /**
     * Shows Android's write dialog once for all [sender]'s files. False when the user declines,
     * does not answer in time or no screen is available.
     */
    suspend fun requestWriteAccess(count: Int, sender: IntentSender): Boolean {
        if (host == null) return false
        val request = WriteAccessRequest(count, sender, CompletableDeferred())
        mutableWriteRequest.value = request
        return try {
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { request.answer.await() } ?: false
        } finally {
            mutableWriteRequest.update { if (it === request) null else it }
        }
    }

    fun answerWriteAccess(request: WriteAccessRequest, granted: Boolean) {
        request.answer.complete(granted)
        mutableWriteRequest.update { if (it === request) null else it }
    }

    fun reportProgress(progress: BatchProgress?) { mutableProgress.value = progress }

    private fun record(action: StoneAction, status: RecordStatus, detail: String, token: String, code: String) {
        val entry = ActionRecord(System.currentTimeMillis(), action.name, status == RecordStatus.OK, detail.take(240),
            status, action.risk, token, code)
        val history = mutableHistory.updateAndGet { (listOf(entry) + it).take(HISTORY_SIZE) }
        ActivityLog.recorded(history, status != RecordStatus.OK)
    }

    /** Activity saved by [ActivityLog] in a previous run. */
    internal fun loadHistory(saved: List<ActionRecord>) {
        mutableHistory.update { current -> (current + saved).distinct().sortedByDescending { it.timeMs }.take(HISTORY_SIZE) }
    }

    internal fun requireHost(): AutomationHost =
        host ?: throw ActionException("Stone is not open. Open the app on the phone and try again.", "STONE_CLOSED")

    private const val CONFIRM_TIMEOUT_MS = 60_000L
    private const val HISTORY_SIZE = 200
}
