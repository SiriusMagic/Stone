package com.zirakusa.stone.nativeapp.automation

/** Tools that help agents help themselves: recent errors, the solutions dictionary and background jobs. */
internal object StoneAgentActions {
    private val jobId = ActionParam("jobId", "string", "Id returned by a tool that started a background job.", true)

    fun all(): List<StoneAction> = listOf(
        StoneAction("stone.activity", "Your own recent calls (kept after Stone closes), newest first, with " +
            "status and error code; other agents' calls are not shown. Use onlyErrors=true to see what failed and why.", ActionRisk.READ,
            listOf(ActionParam("onlyErrors", "boolean", "Only failed or denied calls."),
                ActionParam("tool", "string", "Only this tool, e.g. metadata_suggest_tags."),
                ActionParam("token", "string", "Only calls from the token with this name."),
                ActionParam("limit", "integer", "Maximum entries (default 30, max 200)."))) { args ->
            ActivityLog.query(StoneAutomation.history.value, args.optBoolean("onlyErrors"),
                args.optString("tool").ifBlank { null }, args.optString("token").ifBlank { null },
                args.optInt("limit", 30).coerceIn(1, 200), ownerTokenId = currentAuth().tokenId)
        },
        StoneAction("stone.metrics", "How the MCP server and its internet link are working since Stone started: requests " +
            "per route, HTTP status, JSON-RPC methods, calls per tool, latency and queue-wait buckets, relay reconnections, " +
            "timeouts (504), work refused because it waited too long, and repeated changes answered from the first result. " +
            "Totals for all agents; contains no tokens, addresses or arguments.", ActionRisk.READ) {
            ServerMetrics.snapshot()
        },
        StoneAction("stone.troubleshoot", "Dictionary of solutions: what an error code means and the steps that fix it, " +
            "plus recommended workflows (WORKFLOW_LYRICS, WORKFLOW_GENRES, WORKFLOW_BULK). Call it when a tool returns " +
            "an error, before retrying.", ActionRisk.READ, listOf(
                ActionParam("code", "string", "Error code, e.g. RATE_LIMITED, LOW_CONFIDENCE, ANDROID_CONSENT."),
                ActionParam("query", "string", "Words to look for when you have no code, e.g. \"lyrics genre\"."))) { args ->
            StoneSolutions.lookup(args.optString("code").ifBlank { null }, args.optString("query").ifBlank { null })
        },
        StoneAction("stone.job_status", "Progress of a background job: status (running, waiting, paused, done, " +
            "cancelled), done/total, percent, a result per song and the songs left. Without jobId lists recent jobs.",
            ActionRisk.READ, listOf(ActionParam("jobId", "string", "Job to read. Omit to list jobs."),
                ActionParam("since", "integer", "Only results from this index on (to read only what is new)."))) { args ->
            val owner = currentAuth().tokenId
            args.optString("jobId").ifBlank { null }?.let { StoneJobs.status(it, args.optInt("since", 0), owner) } ?: StoneJobs.list(owner)
        },
        StoneAction("stone.job_resume", "Continue a paused or cancelled job from the first song not done yet.",
            ActionRisk.REVERSIBLE, listOf(jobId)) { args -> StoneJobs.resume(args.getString("jobId"), currentAuth().tokenId) },
        StoneAction("stone.job_cancel", "Stop a background job; songs already done keep their changes (metadata_undo " +
            "with its batchId reverts them).", ActionRisk.REVERSIBLE, listOf(jobId)) { args -> StoneJobs.cancel(args.getString("jobId"), currentAuth().tokenId) },
    )
}
