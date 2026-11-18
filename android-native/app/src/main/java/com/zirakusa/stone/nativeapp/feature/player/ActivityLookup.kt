package com.zirakusa.stone.nativeapp.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The Activity behind a Compose context (rotation, brightness, picture-in-picture). */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
