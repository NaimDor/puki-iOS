import SwiftUI

/**
 * מלשין לרנינג: what Puki said this session, newest first, each with a flag for a parent who heard something that
 * wasn't right, and three short steps saying what the list is, what to flag and where a report goes (to Puki's
 * makers, and only by the parent's own email). What the child said shows here, in memory only; a report carries
 * Puki's line and the game moment, not her words.
 */
struct ReportCard: View {
    @ObservedObject var companion: Companion
    @Environment(\.language) private var language
    @Environment(\.gender) private var gender
    @State private var sharing: Companion.SaidLine?

    var body: some View {
        let canSend = !Contact.reportEmail.trimmed().isEmpty
        let steps = [
            BilingualText("כאן רואים מה פוקי אמר מאז שהמשחק נפתח, מהחדש לישן, ומעל כל משפט מה קרה רגע לפניו, או מה הילד אמר לו.",
                          "Here's what Puki has said since the game opened, newest first, and above each line what happened just before, or what your child said to him.",
                          heGirl: "כאן רואים מה פוקי אמר מאז שהמשחק נפתח, מהחדש לישן, ומעל כל משפט מה קרה רגע לפניו, או מה הילדה אמרה לו."),
            BilingualText("משהו לא התאים? מפחיד, לא נכון, מבטיח משהו או סתם מוזר: לוחצים על 🚩 ליד המשפט.",
                          "Something not right? Scary, untrue, a promise, or just odd: tap 🚩 next to the line."),
            canSend ? BilingualText("אז מופיע ✉️: שולחים לנו את הדיווח מהמייל שלכם, ורואים בדיוק מה יישלח: המשפט של פוקי ומה קרה במשחק, עם השם מוחלף. מה שהילד אמר לא נשלח. ככה נתקן את פוקי לכל המשפחות. בטאבלט עצמו הוא לא נשמר, ושום דבר לא נשלח בלי שתבחרו.",
                                    "Then ✉️ appears: send the report to Puki's makers from your own email, seeing exactly what goes: Puki's line and what happened in the game, with your child's name replaced. What your child said isn't sent. That's how Puki gets fixed for every family. It isn't kept on this tablet, and nothing is sent unless you choose to.",
                                    heGirl: "אז מופיע ✉️: שולחים לנו את הדיווח מהמייל שלכם, ורואים בדיוק מה יישלח: המשפט של פוקי ומה קרה במשחק, עם השם מוחלף. מה שהילדה אמרה לא נשלח. ככה נתקן את פוקי לכל המשפחות. בטאבלט עצמו הוא לא נשמר, ושום דבר לא נשלח בלי שתבחרו.")
                    : BilingualText("הדיווח לא נשמר בטאבלט ולא יוצא ממנו.", "The report isn't kept on this tablet, and nothing leaves it."),
        ]
        VStack(alignment: .leading, spacing: 12) {
            Text("🚩 " + BilingualText("מלשין לרנינג", "What Puki said").resolve(language)).font(PukiFont.titleLarge.bold()).foregroundColor(PukiColors.earthBrown)
            Text(BilingualText("פוקי אמר משהו לא בסדר? ספרו לנו!", "Did Puki say something wrong? Tell us!").resolve(language))
                .font(PukiFont.bodyLarge).foregroundColor(PukiColors.earthBrown)
            ForEach(Array(steps.enumerated()), id: \.offset) { i, step in
                HStack(alignment: .top, spacing: 8) {
                    Text("\(i + 1).").font(PukiFont.bodyMedium.bold()).foregroundColor(PukiColors.earthBrown)
                    Text(step.resolve(language, gender)).font(PukiFont.bodyMedium).foregroundColor(PukiColors.earthBrown.opacity(0.85))
                }
            }
            if companion.recentLines.isEmpty {
                Text(BilingualText("פוקי עוד לא אמר כלום הפעם.", "Puki hasn't said anything yet this time.").resolve(language))
                    .font(PukiFont.bodyMedium).foregroundColor(PukiColors.earthBrown.opacity(0.6))
            }
            ForEach(Array(companion.recentLines.reversed().prefix(10))) { line in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        // What came just before: her own words, or what was happening in the game
                        let (label, before): (BilingualText, String) = line.heard.trimmed().isEmpty
                            ? (BilingualText("מה קרה:", "What happened:"), whatHappened(line.situation))
                            : (BilingualText("הילד אמר:", "Your child said:", heGirl: "הילדה אמרה:"), line.heard)
                        if !before.trimmed().isEmpty {
                            Text(label.resolve(language, gender) + " " + before).font(.system(size: 13)).foregroundColor(PukiColors.earthBrown.opacity(0.65)).lineLimit(2)
                        }
                        Text(line.said).font(PukiFont.bodyLarge).foregroundColor(PukiColors.earthBrown)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Button { companion.report(line) } label: {
                        Text(line.reported ? "✓" : "🚩").font(.system(size: 20)).frame(width: 48, height: 48)
                            .background(Circle().fill(line.reported ? Color(argb: 0xFFE8F5E9) : Color(argb: 0xFFFFEBEE)))
                    }
                    .buttonStyle(.plain).disabled(line.reported).padding(.leading, 8)
                    // Reported: the parent may also send it to Puki's makers (only when there's an address)
                    if line.reported && canSend {
                        Button { sharing = line } label: {
                            Text("✉️").font(.system(size: 20)).frame(width: 48, height: 48).background(Circle().fill(Color(argb: 0xFFE3F2FD)))
                        }
                        .buttonStyle(.plain).padding(.leading, 8)
                    }
                }
            }
        }
        .padding(24)
        .frame(width: 400)
        .background(RoundedRectangle(cornerRadius: 24).fill(PukiColors.cloudWhite))
        .sheet(item: $sharing) { line in ShareSheet(text: companion.shareText(line)) { sharing = nil } }
    }
}

/**
 * The game's description of the moment, for the parent: without the sentences that tell Puki what to do ("Say
 * something short about it."), which are for him, not for them.
 */
private func whatHappened(_ situation: String) -> String {
    var sentences: [String] = []
    var cur = ""
    for ch in situation {
        cur.append(ch)
        if ".!?".contains(ch) { sentences.append(cur); cur = "" }
    }
    if !cur.isEmpty { sentences.append(cur) }
    return sentences.map { $0.trimmingCharacters(in: .whitespaces) }
        .filter { !($0.hasPrefix("תגיד") || $0.hasPrefix("Say ") || $0.hasPrefix("Tell ")) }
        .joined(separator: " ").trimmed()
}

/**
 * What will be sent, before anything is: the text with the child's name replaced, which the parent can change or
 * cut. "Open email" hands it to their own mail app, which sends it (Puki never does).
 */
private struct ShareSheet: View {
    @State var text: String
    let onDone: () -> Void
    @Environment(\.language) private var language
    @Environment(\.openURL) private var openURL
    @State private var noMail = false

    init(text: String, onDone: @escaping () -> Void) { _text = State(initialValue: text); self.onDone = onDone }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(BilingualText("שליחה ליוצרי פוקי", "Send to Puki's makers").resolve(language)).font(PukiFont.titleLarge)
            Text(BilingualText("זה מה שיישלח, במייל מהטלפון שלכם. השם הוחלף, ואפשר לשנות או למחוק כל דבר.",
                               "This is what will be sent, by email from your phone. The name is replaced, and you can change or cut anything.").resolve(language))
                .font(PukiFont.bodyMedium)
            TextEditor(text: $text).frame(minHeight: 140).overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.4)))
            if noMail {
                Text(BilingualText("אין אפליקציית מייל בטלפון", "There's no email app on this device").resolve(language)).font(PukiFont.bodyMedium).foregroundColor(.secondary)
            }
            HStack {
                Spacer()
                Button(BilingualText("ביטול", "Cancel").resolve(language), action: onDone)
                Button(BilingualText("פתיחת המייל", "Open email").resolve(language)) {
                    var c = URLComponents()
                    c.scheme = "mailto"
                    c.path = Contact.reportEmail
                    c.queryItems = [URLQueryItem(name: "subject", value: "מלשין לרנינג: Puki report"), URLQueryItem(name: "body", value: text)]
                    if let url = c.url {
                        openURL(url) { ok in if ok { onDone() } else { noMail = true } }
                    }
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(24)
    }
}
