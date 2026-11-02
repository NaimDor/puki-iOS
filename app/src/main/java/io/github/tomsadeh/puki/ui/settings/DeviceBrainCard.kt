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
import io.github.tomsadeh.puki.theme.CloudWhite
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.theme.GrassGreen
import io.github.tomsadeh.puki.ui.common.LocalAppSettings
import io.github.tomsadeh.puki.ui.common.LocalLanguage

/** "Puki on this device": download the models once, then think here. */
@Composable
fun DeviceBrainCard(viewModel: SettingsViewModel) {
    val language = LocalLanguage.current
    val settings = LocalAppSettings.current
    val models = viewModel.deviceModels
    val progress by models.progress.collectAsState()
    val ready = progress == null && models.ready()
    val gb = "%.1f".format(models.totalBytes / 1e9)

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
                "פוקי יכול לחשוב, לדבר ולהקשיב כאן במכשיר, בלי אינטרנט: גם מחוץ לבית. צריך להוריד פעם אחת $gb GB " +
                    "(עדיף ב-Wi-Fi). בינתיים כאן הוא מדבר רק עברית.",
                "Puki can think, talk and listen right here on this device, with no internet: even away from home. " +
                    "It's a one-time $gb GB download (Wi-Fi is best). For now he speaks only Hebrew here."
            ).resolve(language),
            style = MaterialTheme.typography.bodyMedium, color = EarthBrown
        )
        val p = progress
        when {
            p != null -> {
                LinearProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth(), color = GrassGreen)
                Text(BilingualText("מוריד… ${(p * 100).toInt()}%", "Downloading… ${(p * 100).toInt()}%").resolve(language),
                    style = MaterialTheme.typography.bodyMedium, color = EarthBrown)
            }
            !ready -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GrassGreen)
                    .clickable { viewModel.downloadDeviceBrain() }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(BilingualText("הורדה ($gb GB)", "Download ($gb GB)").resolve(language),
                    style = MaterialTheme.typography.titleMedium, color = CloudWhite)
            }
            else -> {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(BilingualText("לחשוב במכשיר הזה", "Think on this device").resolve(language),
                        style = MaterialTheme.typography.titleMedium, color = EarthBrown)
                    Switch(checked = settings.brainOnDevice, onCheckedChange = { viewModel.setBrainOnDevice(it) })
                }
                Text(
                    BilingualText("הסרה (מפנה $gb GB)", "Remove (frees $gb GB)").resolve(language),
                    style = MaterialTheme.typography.bodyMedium, color = EarthBrown.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { viewModel.removeDeviceBrain() }.padding(vertical = 4.dp)
                )
            }
        }
        viewModel.downloadError?.let {
            Text(BilingualText("ההורדה נעצרה: $it. אפשר ללחוץ שוב כדי להמשיך.", "The download stopped: $it. Tap again to carry on.").resolve(language),
                style = MaterialTheme.typography.bodyMedium, color = EarthBrown)
        }
    }
}
