package com.zirakusa.stone.nativeapp.ui.components

import android.animation.ValueAnimator
import android.graphics.drawable.AnimatedVectorDrawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.zirakusa.stone.nativeapp.R

/** A one-shot, low-key mascot animation used only when the report form opens. */
@Composable
internal fun PenguinReportMascot(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.penguin_mascot_description)
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                contentDescription = description
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val resource = if (animationsEnabled) R.drawable.avd_penguin_click
                else R.drawable.ic_penguin
                setImageResource(resource)
                if (animationsEnabled) {
                    (drawable as? AnimatedVectorDrawable)?.start()
                }
            }
        },
    )
}
