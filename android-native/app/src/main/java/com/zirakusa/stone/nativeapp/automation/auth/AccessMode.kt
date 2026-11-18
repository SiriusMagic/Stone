package com.zirakusa.stone.nativeapp.automation.auth

import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk

/**
 * What a token lets an agent do. Approval is asked only where it matters:
 * - READ: queries only.
 * - WRITE: everything; only CRITICAL actions (destructive, cannot be undone) ask on the phone.
 * - FULL: everything, nothing asks. Edits are still backed up and can be undone.
 */
enum class AccessMode(val key: String) {
    READ("read"), WRITE("write"), FULL("full");

    fun permits(risk: ActionRisk): Boolean = this != READ || risk == ActionRisk.READ
    fun needsApproval(risk: ActionRisk): Boolean = this == WRITE && risk == ActionRisk.CRITICAL

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: WRITE
    }
}
