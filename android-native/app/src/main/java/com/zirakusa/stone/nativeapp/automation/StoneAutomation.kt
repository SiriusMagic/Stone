package com.zirakusa.stone.nativeapp.automation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** A PERSISTENT action waiting for the user's answer on screen. */
class PendingConfirmation(val action: String, val summary: String, val answer: CompletableDeferred<Boolean>)

/**
 * Single gateway between agents and Stone. It validates parameters, enforces the risk policy
 * and runs actions on the main thread through the [host] the UI registers while it is alive.
 * The player works exactly the same without it; this is only an extra way to control it.
 */
object StoneAutomation {
    @Volatile var host: AutomationHost? = null

    private val mutableConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val confirmation = mutableConfirmation.asStateFlow()
    private val mutableHistory = MutableStateFlow<List<ActionRecord>>(emptyList())
    val history = mutableHistory.asStateFlow()

    val actions: List<StoneAction> by lazy { StoneActions.all() }

    /** Actions the current link may use (Ajustes › Modo API › permisos). */
    val available: List<StoneAction> get() = actions.filter { ApiMode.permits(it.risk) }

    fun find(toolName: String) = actions.firstOrNull { it.toolName == toolName || it.name == toolName }

    /** Runs [toolName] with [arguments]; throws [ActionException] with a message for the agent. */
    suspend fun execute(toolName: String, arguments: JSONObject): Any {
        val action = find(toolName) ?: throw ActionException("Unknown tool: $toolName")
        return try {
            if (!ApiMode.permits(action.risk)) throw ActionException(
                if (ApiMode.readOnly) "This Stone link is read-only. The user can allow changes in Stone > Ajustes > Modo API > Opciones avanzadas."
                else "Editing files is turned off for this Stone link. The user can enable it in Stone > Ajustes > Modo API > Opciones avanzadas.")
            validate(action, arguments)
            if (action.risk == ActionRisk.PERSISTENT && !confirm(action, arguments))
                throw ActionException("The user did not approve this change on the phone.")
            val result = withContext(Dispatchers.Main) { action.run(arguments) }
            record(action.name, true, arguments.toString())
            result
        } catch (error: Exception) {
            record(action.name, false, error.message ?: error.javaClass.simpleName)
            if (error is ActionException) throw error
            throw ActionException(error.message ?: "Action failed")
        }
    }

    private fun validate(action: StoneAction, arguments: JSONObject) {
        val known = action.params.associateBy { it.name }
        arguments.keys().forEach { key -> if (key !in known) throw ActionException("Unknown parameter '$key'") }
        action.params.forEach { param ->
            if (!arguments.has(param.name) || arguments.isNull(param.name)) {
                if (param.required) throw ActionException("Missing required parameter '${param.name}'")
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
            if (!valid) throw ActionException("Parameter '${param.name}' must be ${param.type}")
            param.options?.let { if (value !in it) throw ActionException("'${param.name}' must be one of $it") }
        }
    }

    private suspend fun confirm(action: StoneAction, arguments: JSONObject): Boolean {
        if (host == null) throw ActionException("Open Stone on the phone to approve this change.")
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

    private fun record(action: String, ok: Boolean, detail: String) {
        mutableHistory.update { (listOf(ActionRecord(System.currentTimeMillis(), action, ok, detail.take(160))) + it).take(30) }
    }

    internal fun requireHost(): AutomationHost =
        host ?: throw ActionException("Stone is not open. Open the app on the phone and try again.")

    private const val CONFIRM_TIMEOUT_MS = 60_000L
}
