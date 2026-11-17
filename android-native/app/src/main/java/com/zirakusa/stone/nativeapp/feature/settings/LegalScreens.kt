package com.zirakusa.stone.nativeapp.feature.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.feature.settings.api.color

/** Where "Calificarnos" sends people until Stone is on Google Play. */
const val STONE_REPOSITORY_URL = "https://github.com/SiriusMagic/Stone"

fun openStoneRepository(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, STONE_REPOSITORY_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Shows `assets/legal/<name>_<language>.md` ("privacy", "terms" or "licenses"). Spanish for
 * Spanish, English for every other language. Only `## ` headings and `- ` bullets are styled.
 */
@Composable fun LegalDocument(name: String) {
    val context = LocalContext.current
    val language = if (LocalConfiguration.current.locales[0].language == "es") "es" else "en"
    val lines = remember(name, language) {
        runCatching { context.assets.open("legal/${name}_$language.md").bufferedReader().use { it.readLines() } }
            .getOrDefault(emptyList())
    }
    SelectionContainer {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            lines.forEach { line ->
                when {
                    line.startsWith("## ") -> Text(line.removePrefix("## "), color = StonePalette.Yellow,
                        fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                    line.startsWith("- ") -> Row(Modifier.padding(bottom = 6.dp)) {
                        Text("•", color = StonePalette.Muted, modifier = Modifier.padding(end = 8.dp))
                        Text(line.removePrefix("- "), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    line.isNotBlank() -> Text(line, fontSize = 14.sp, lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}
