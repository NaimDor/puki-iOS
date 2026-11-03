import Foundation

private func documentsFile(_ name: String) -> URL {
    let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
    try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    return dir.appendingPathComponent(name)
}

private func todayString() -> String {
    let f = DateFormatter()
    f.calendar = Calendar(identifier: .gregorian)
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd"
    return f.string(from: Date())
}

/**
 * What the child did today, for Puki's bedtime recap. In memory only, so closing the app forgets it;
 * unless the parent turned on "Puki remembers between sessions" (`keep`), and then today's things are
 * kept in a small file on the device (a new day starts it over; turning it off deletes it).
 */
final class DayLog {
    static let shared = DayLog()
    private let lock = NSLock()
    private let file = documentsFile("day.json")
    private var events: [DayEvent] = []
    private var day = todayString()
    private var keeping = false
    private var restored = Set<DayEvent>()
    private static let max = 12

    static func keep(_ on: Bool) { shared.keep(on) }
    static func record(_ e: DayEvent?) { shared.record(e) }
    static func today() -> [DayEvent] { shared.today() }

    func keep(_ on: Bool) {
        lock.lock(); defer { lock.unlock() }
        if !on {
            try? FileManager.default.removeItem(at: file)
            events.removeAll { restored.contains($0) }
            restored.removeAll()
            keeping = false
            return
        }
        if keeping { return }
        keeping = true
        if let data = try? Data(contentsOf: file),
           let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           o["day"] as? String == todayString(),
           let a = o["events"] as? [[String]] {
            let back = a.compactMap { $0.count >= 2 ? DayEvent($0[0], $0[1]) : nil }.filter { !events.contains($0) }
            events.insert(contentsOf: back, at: 0)
            restored.formUnion(back)
            while events.count > Self.max { events.removeFirst() }
        }
        save()
    }

    /** She did `event` just now (the same thing twice counts once; the latest dozen are kept). */
    func record(_ event: DayEvent?) {
        guard let event else { return }
        lock.lock(); defer { lock.unlock() }
        let today = todayString()
        if today != day { events.removeAll(); day = today }
        events.removeAll { $0 == event }
        events.append(event)
        while events.count > Self.max { events.removeFirst() }
        save()
    }

    /** Today's things, the latest last. */
    func today() -> [DayEvent] {
        lock.lock(); defer { lock.unlock() }
        return day == todayString() ? events : []
    }

    private func save() {
        guard keeping else { return }
        let o: [String: Any] = ["day": day, "events": events.map { [$0.he, $0.en] }]
        if let data = try? JSONSerialization.data(withJSONObject: o) {
            try? data.write(to: file, options: [.atomic, .completeFileProtection])
        }
    }
}

/**
 * The game moments Puki can reach back to (ReachBack). In memory only, unless the parent turned on
 * "Puki remembers between sessions", and then the last three days' moments are kept in a small file.
 */
final class MomentMemory {
    static let shared = MomentMemory()
    private let lock = NSLock()
    private let file = documentsFile("moments.json")
    private var moments: [Moment] = []
    private var keeping = false
    private let sessionStart = nowMs()
    private static let keepMs: Int64 = 3 * 24 * 3600_000
    private static let max = 400

    static func keep(_ on: Bool) { shared.keep(on) }
    static func record(he: String, en: String, game: String) { shared.record(he: he, en: en, game: game) }
    static func history() -> [Moment] { shared.history() }

    func keep(_ on: Bool) {
        lock.lock(); defer { lock.unlock() }
        if !on {
            try? FileManager.default.removeItem(at: file)
            moments.removeAll { $0.t < sessionStart }
            keeping = false
            return
        }
        if keeping { return }
        keeping = true
        if let data = try? Data(contentsOf: file), let a = try? JSONSerialization.jsonObject(with: data) as? [[Any]] {
            let kept = a.compactMap { m -> Moment? in
                guard m.count >= 3, let t = (m[0] as? NSNumber)?.int64Value, let he = m[1] as? String, let en = m[2] as? String else { return nil }
                return Moment(t: t, he: he, en: en, game: m.count > 3 ? (m[3] as? String ?? "") : "")
            }
            moments.insert(contentsOf: kept.filter { $0.t < sessionStart && $0.t > nowMs() - Self.keepMs }, at: 0)
        }
        save()
    }

    func record(he: String, en: String, game: String) {
        lock.lock(); defer { lock.unlock() }
        let now = nowMs()
        moments.append(Moment(t: now, he: he.take(200), en: en.take(200), game: game))
        moments.removeAll { $0.t < now - Self.keepMs }
        while moments.count > Self.max { moments.removeFirst() }
        save()
    }

    func history() -> [Moment] {
        lock.lock(); defer { lock.unlock() }
        return moments
    }

    private func save() {
        guard keeping else { return }
        let a: [[Any]] = moments.map { [NSNumber(value: $0.t), $0.he, $0.en, $0.game] }
        if let data = try? JSONSerialization.data(withJSONObject: a) {
            try? data.write(to: file, options: [.atomic, .completeFileProtection])
        }
    }
}
