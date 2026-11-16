package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.LyricWord
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

private val LyricInk = Color(0xFFD8E5E3)

/**
 * One phrase on a single line, left aligned. Long phrases first shrink a little (never below a
 * readable size) and only then end in "…", so the window never changes height. With word
 * timings, words already sung are bright and the rest stay dim.
 */
@Composable internal fun LyricPhrase(text: String, muted: Boolean, modifier: Modifier = Modifier,
    words: List<LyricWord> = emptyList(), activeWord: Int = -1) {
    val ink = if (muted) StonePalette.Secondary else LyricInk
    val content = remember(text, words, activeWord, ink) {
        buildAnnotatedString {
            if (words.isEmpty()) append(text)
            else words.forEachIndexed { index, word ->
                withStyle(SpanStyle(color = if (index <= activeWord) ink else ink.copy(alpha = .45f))) {
                    append(word.text)
                }
            }
        }
    }
    BasicText(content, modifier, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start, lineHeight = 18.sp),
        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp, stepSize = .5.sp))
}
