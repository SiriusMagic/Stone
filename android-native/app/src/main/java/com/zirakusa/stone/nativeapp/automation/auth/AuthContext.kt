package com.zirakusa.stone.nativeapp.automation.auth

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Who is making the request being executed: an immutable value carried in the coroutine of that
 * one request (and of everything it starts), never in a shared variable. Two agents running at the
 * same time therefore can never see or use each other's mode or tool list.
 */
data class AuthContext(val tokenId: String, val tokenName: String, val access: AccessMode,
    val blocked: Set<String>) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AuthContext> {
        /** Calls made from inside Stone itself (tests, the app) when no agent is involved. */
        val LOCAL = AuthContext("", "", AccessMode.FULL, emptySet())
    }
}

/** The authorization of the request this coroutine belongs to. */
suspend fun currentAuth(): AuthContext = coroutineContext[AuthContext] ?: AuthContext.LOCAL
