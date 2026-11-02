package io.github.tomsadeh.puki.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.tomsadeh.puki.data.Contact
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomsadeh.puki.audio.Companion
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.theme.CloudWhite
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.ui.common.LocalGender
import io.github.tomsadeh.puki.ui.common.LocalLanguage

/**
 * מלשין לרנינג: what Puki said this session, newest first, each with a flag for a parent who
 * heard something that wasn't right, and three short steps saying what the list is, what to flag
 * and where a report goes (to Puki's makers, and only by the parent's own email). What the child
 * said shows here, in memory only; a report carries Puki's line and the game moment, not her words.
 */
@Composable
fun ReportCard(companion: Companion) {
    val language = LocalLanguage.current
    val lines by companion.recentLines.collectAsState()
    var sharing by remember { mutableStateOf<Companion.SaidLine?>(null) }
    sharing?.let { line -> ShareDialog(companion, line) { sharing = null } }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CloudWhite)
            .padding(24.dp)
            .width(400.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🚩 " + BilingualText("מלשין לרנינג", "What Puki said").resolve(language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = EarthBrown)
        Text(
            BilingualText("פוקי אמר משהו לא בסדר? ספרו לנו!", "Did Puki say something wrong? Tell us!").resolve(language),
            style = MaterialTheme.typography.bodyLarge,
            color = EarthBrown
        )
        // What this is, what counts, and where a report goes: in three short steps
        val gender = LocalGender.current
        val canSend = Contact.REPORT_EMAIL.isNotBlank()
        val steps = listOf(
            BilingualText(
                "כאן רואים מה פוקי אמר מאז שהמשחק נפתח, מהחדש לישן, ומעל כל משפט מה קרה רגע לפניו, או מה הילד אמר לו.",
                "Here's what Puki has said since the game opened, newest first, and above each line what happened just before, or what your child said to him.",
                heGirl = "כאן רואים מה פוקי אמר מאז שהמשחק נפתח, מהחדש לישן, ומעל כל משפט מה קרה רגע לפניו, או מה הילדה אמרה לו."
            ),
            BilingualText(
                "משהו לא התאים? מפחיד, לא נכון, מבטיח משהו או סתם מוזר: לוחצים על 🚩 ליד המשפט.",
                "Something not right? Scary, untrue, a promise, or just odd: tap 🚩 next to the line."
            ),
            if (canSend) BilingualText(
                "אז מופיע ✉️: שולחים לנו את הדיווח מהמייל שלכם, ורואים בדיוק מה יישלח: המשפט של פוקי ומה קרה במשחק, עם השם מוחלף. מה שהילד אמר לא נשלח. ככה נתקן את פוקי לכל המשפחות. בטאבלט עצמו הוא לא נשמר, ושום דבר לא נשלח בלי שתבחרו.",
                "Then ✉️ appears: send the report to Puki's makers from your own email, seeing exactly what goes: Puki's line and what happened in the game, with your child's name replaced. What your child said isn't sent. That's how Puki gets fixed for every family. It isn't kept on this tablet, and nothing is sent unless you choose to.",
                heGirl = "אז מופיע ✉️: שולחים לנו את הדיווח מהמייל שלכם, ורואים בדיוק מה יישלח: המשפט של פוקי ומה קרה במשחק, עם השם מוחלף. מה שהילדה אמרה לא נשלח. ככה נתקן את פוקי לכל המשפחות. בטאבלט עצמו הוא לא נשמר, ושום דבר לא נשלח בלי שתבחרו."
            ) else BilingualText(
                "הדיווח לא נשמר בטאבלט ולא יוצא ממנו.",
                "The report isn't kept on this tablet, and nothing leaves it."
            )
        )
        steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = EarthBrown)
                Text(step.resolve(language, gender), style = MaterialTheme.typography.bodyMedium, color = EarthBrown.copy(alpha = 0.85f))
            }
        }
        if (lines.isEmpty()) {
            Text(
                BilingualText("פוקי עוד לא אמר כלום הפעם.", "Puki hasn't said anything yet this time.").resolve(language),
                style = MaterialTheme.typography.bodyMedium,
                color = EarthBrown.copy(alpha = 0.6f)
            )
        }
        for (line in lines.asReversed().take(10)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    // What came just before: her own words, or what was happening in the game
                    val (label, before) = when {
                        line.heard.isNotBlank() -> BilingualText("הילד אמר:", "Your child said:", heGirl = "הילדה אמרה:") to line.heard
                        else -> BilingualText("מה קרה:", "What happened:") to whatHappened(line.situation)
                    }
                    if (before.isNotBlank()) {
                        Text(
                            label.resolve(language, gender) + " " + before,
                            fontSize = 13.sp, lineHeight = 17.sp, color = EarthBrown.copy(alpha = 0.65f), maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Text(line.said, style = MaterialTheme.typography.bodyLarge, color = EarthBrown)
                }
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (line.reported) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                        .clickable(enabled = !line.reported) { companion.report(line) },
                    contentAlignment = Alignment.Center
                ) { Text(if (line.reported) "✓" else "🚩", fontSize = 20.sp) }
                // Reported: the parent may also send it to Puki's makers (only when there's an address)
                if (line.reported && Contact.REPORT_EMAIL.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE3F2FD))
                            .clickable { sharing = line },
                        contentAlignment = Alignment.Center
                    ) { Text("✉️", fontSize = 20.sp) }
                }
            }
        }
    }
}

/**
 * The game's description of the moment, for the parent: without the sentences that tell Puki
 * what to do ("Say something short about it."), which are for him, not for them.
 */
private fun whatHappened(situation: String): String =
    situation.split(Regex("(?<=[.!?])\\s+"))
        .filterNot { it.trimStart().startsWith("תגיד") || it.trimStart().startsWith("Say ") || it.trimStart().startsWith("Tell ") }
        .joinToString(" ")
        .trim()

/**
 * What will be sent, before anything is: the text with the child's name replaced, which the parent
 * can change or cut. "Open email" hands it to their own mail app, which sends it (Puki never does).
 */
@Composable
private fun ShareDialog(companion: Companion, line: Companion.SaidLine, onDone: () -> Unit) {
    val language = LocalLanguage.current
    val context = LocalContext.current
    var text by remember(line.id) { mutableStateOf(companion.shareText(line)) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(BilingualText("שליחה ליוצרי פוקי", "Send to Puki's makers").resolve(language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    BilingualText(
                        "זה מה שיישלח, במייל מהטלפון שלכם. השם הוחלף, ואפשר לשנות או למחוק כל דבר.",
                        "This is what will be sent, by email from your phone. The name is replaced, and you can change or cut anything."
                    ).resolve(language),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 5)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(Contact.REPORT_EMAIL))
                    putExtra(Intent.EXTRA_SUBJECT, "מלשין לרנינג: Puki report")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                try {
                    context.startActivity(mail)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        context,
                        BilingualText("אין אפליקציית מייל בטלפון", "There's no email app on this device").resolve(language),
                        Toast.LENGTH_LONG
                    ).show()
                }
                onDone()
            }) { Text(BilingualText("פתיחת המייל", "Open email").resolve(language)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(BilingualText("ביטול", "Cancel").resolve(language)) } }
    )
}
