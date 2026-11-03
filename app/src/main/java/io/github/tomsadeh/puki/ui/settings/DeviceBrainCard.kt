package io.github.tomsadeh.puki.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.data.models.BrainLanguages
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.theme.CloudWhite
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.theme.GrassGreen
import io.github.tomsadeh.puki.ui.common.LocalAppSettings
import io.github.tomsadeh.puki.ui.common.LocalLanguage

/** "Puki on this device": pick his languages, download the models once, then think here. */
@Composable
fun DeviceBrainCard(viewModel: SettingsViewModel) {
    val language = LocalLanguage.current
    val settings = LocalAppSettings.current
    val models = viewModel.deviceModels
    val progress by models.progress.collectAsState()
    val langs = models.languagesFor(settings.brainLanguages, settings.language)
    val ready = progress == null && models.ready(langs)
    val size = sizeText(models.totalBytes(langs))
    val missing = sizeText(models.missingBytes(langs))

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CloudWhite)
            .padding(24.dp)
            .width(400.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(BilingualText("פוקי במכשיר הזה", "Puki on this device").resolve(language),
            style = MaterialTheme.typography.titleLarge, color = EarthBrown)
        Text(
            BilingualText(
                "פוקי יכול לחשוב, לדבר ולהקשיב כאן במכשיר, בלי אינטרנט: גם מחוץ לבית. מורידים פעם אחת (עדיף ב-Wi-Fi), " +
                    "בשפה שבוחרים:",
                "Puki can think, talk and listen right here on this device, with no internet: even away from home. " +
                    "It's a one-time download (Wi-Fi is best), in the languages you pick:"
            ).resolve(language),
            style = MaterialTheme.typography.bodyMedium, color = EarthBrown
        )
        BrainLanguagePicker(langs, language, enabled = progress == null) { viewModel.setBrainLanguages(it) }
        val p = progress
        when {
            p != null -> {
                LinearProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth(), color = GrassGreen)
                Text(BilingualText("מוריד… ${(p * 100).toInt()}%", "Downloading… ${(p * 100).toInt()}%").resolve(language),
                    style = MaterialTheme.typography.bodyMedium, color = EarthBrown)
            }
            else -> {
                // What the pick still needs (a whole brain, or one more language's reader)
                if (!ready) Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GrassGreen)
                        .clickable { viewModel.downloadDeviceBrain(langs) }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(BilingualText("הורדה ($missing)", "Download ($missing)").resolve(language),
                        style = MaterialTheme.typography.titleMedium, color = CloudWhite)
                }
                // The rest whenever there's a brain on the device (or memory kept, so it can always be turned off)
                if (models.ready() || settings.rememberBetween) BrainControls(viewModel, size)
            }
        }
        viewModel.downloadError?.let {
            Text(BilingualText("ההורדה נעצרה: $it. אפשר ללחוץ שוב כדי להמשיך.", "The download stopped: $it. Tap again to carry on.").resolve(language),
                style = MaterialTheme.typography.bodyMedium, color = EarthBrown)
        }
    }
}

/** Thinking on the device, remembering between sessions, and removing it all. */
@Composable
private fun BrainControls(viewModel: SettingsViewModel, size: String) {
    val language = LocalLanguage.current
    val settings = LocalAppSettings.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(BilingualText("לחשוב במכשיר הזה", "Think on this device").resolve(language),
                        style = MaterialTheme.typography.titleMedium, color = EarthBrown)
                    Switch(checked = settings.brainOnDevice, onCheckedChange = { viewModel.setBrainOnDevice(it) })
                }
                // Off unless the parent turns it on: the site promises nothing outlives the session
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(BilingualText("פוקי זוכר בין משחקים", "Puki remembers between sessions").resolve(language),
                            style = MaterialTheme.typography.titleMedium, color = EarthBrown)
                        Text(BilingualText(
                            "כבוי: פוקי שוכח הכל כשסוגרים את המשחק. דולק: הוא זוכר את המשחקים של היום לסיכום לפני השינה, ואת היומיים האחרונים (\"כמו הפרה אתמול!\"). רק בטאבלט, אף פעם לא מה שהילדים אמרו, ונמחק כשמכבים.",
                            "Off: Puki forgets it all when the game closes. On: he keeps today's games for the bedtime recap, and the last two days (\"like the cow yesterday!\"). Only on this tablet, never what the child said, and deleted when you turn it off."
                        ).resolve(language), style = MaterialTheme.typography.bodySmall, color = EarthBrown.copy(alpha = 0.75f))
                    }
                    Switch(checked = settings.rememberBetween, onCheckedChange = { viewModel.setRememberBetween(it) })
                }
                Text(
                    BilingualText("הסרה (מפנה $size)", "Remove (frees $size)").resolve(language),
                    style = MaterialTheme.typography.bodyMedium, color = EarthBrown.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { viewModel.removeDeviceBrain() }.padding(vertical = 4.dp)
                )
    }
}

/** Hebrew, English or both: each language has its own reader (a few MB); the rest is shared. */
@Composable
fun BrainLanguagePicker(selected: BrainLanguages, language: Language, enabled: Boolean = true, onPick: (BrainLanguages) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((option, label) in listOf(
            BrainLanguages.HEBREW to BilingualText("עברית", "Hebrew"),
            BrainLanguages.ENGLISH to BilingualText("אנגלית", "English"),
            BrainLanguages.BOTH to BilingualText("שתיהן", "Both"),
        )) {
            val on = option == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) GrassGreen else EarthBrown.copy(alpha = 0.08f))
                    .clickable(enabled = enabled && !on) { onPick(option) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(label.resolve(language), style = MaterialTheme.typography.titleMedium, color = if (on) CloudWhite else EarthBrown)
            }
        }
    }
}

/** A download's size the way a parent reads it: MB under a gigabyte, else GB. */
fun sizeText(bytes: Long): String = if (bytes < 1_000_000_000) "${(bytes + 999_999) / 1_000_000} MB" else "%.1f GB".format(bytes / 1e9)
