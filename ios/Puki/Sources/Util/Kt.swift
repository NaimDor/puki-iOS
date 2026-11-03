import Foundation
import SwiftUI

// Small helpers that keep the port close to the Kotlin it came from.

extension Color {
    /** An Android-style 0xAARRGGBB color. */
    init(argb: UInt32) {
        self.init(.sRGB,
                  red: Double((argb >> 16) & 0xFF) / 255,
                  green: Double((argb >> 8) & 0xFF) / 255,
                  blue: Double(argb & 0xFF) / 255,
                  opacity: Double((argb >> 24) & 0xFF) / 255)
    }
}

extension Comparable {
    func coerceIn(_ lo: Self, _ hi: Self) -> Self { min(max(self, lo), hi) }
}

extension String {
    /** Kotlin's substringAfter: the whole string when `d` isn't there. */
    func substringAfter(_ d: String) -> String {
        guard let r = range(of: d) else { return self }
        return String(self[r.upperBound...])
    }
    func substringBefore(_ d: String) -> String {
        guard let r = range(of: d) else { return self }
        return String(self[..<r.lowerBound])
    }
    func substringAfterLast(_ d: String) -> String {
        guard let r = range(of: d, options: .backwards) else { return self }
        return String(self[r.upperBound...])
    }
    func removePrefix(_ p: String) -> String { hasPrefix(p) ? String(dropFirst(p.count)) : self }
    func removeSuffix(_ p: String) -> String { hasSuffix(p) ? String(dropLast(p.count)) : self }
    func take(_ n: Int) -> String { String(prefix(Swift.max(0, n))) }
    func drop(_ n: Int) -> String { String(dropFirst(Swift.max(0, n))) }
    func takeLast(_ n: Int) -> String { String(suffix(Swift.max(0, n))) }
    func trimmed() -> String { trimmingCharacters(in: .whitespacesAndNewlines) }
    /** Kotlin's replaceFirstChar { it.uppercase() }. */
    var capitalizedFirst: String { isEmpty ? self : prefix(1).uppercased() + dropFirst() }
    func ifEmpty(_ s: String) -> String { isEmpty ? s : self }
    /** Kotlin's trimStart { it in chars }. */
    func trimStart(_ chars: String) -> String { String(drop(while: { chars.contains($0) })) }
    func trimEnd(_ chars: String) -> String {
        var s = Substring(self)
        while let l = s.last, chars.contains(l) { s = s.dropLast() }
        return String(s)
    }
    /** The number of UTF-16 units, as Kotlin's length counts. */
    var ktLength: Int { utf16.count }
}

/** A thin NSRegularExpression wrapper with Kotlin's Regex calls (ICU syntax, as on Android). */
struct KRegex {
    let re: NSRegularExpression
    init(_ pattern: String, ignoreCase: Bool = false) {
        re = try! NSRegularExpression(pattern: pattern, options: ignoreCase ? [.caseInsensitive] : [])
    }
    private func ns(_ s: String) -> NSRange { NSRange(s.startIndex..<s.endIndex, in: s) }

    /** Every match's text. */
    func findAll(_ s: String) -> [String] {
        re.matches(in: s, range: ns(s)).compactMap { Range($0.range, in: s).map { String(s[$0]) } }
    }
    /** Every match, with its groups (nil for a group that didn't take part). */
    func findAllGroups(_ s: String) -> [[String?]] {
        re.matches(in: s, range: ns(s)).map { m in
            (0..<m.numberOfRanges).map { i in Range(m.range(at: i), in: s).map { String(s[$0]) } }
        }
    }
    func find(_ s: String) -> [String?]? { findAllGroups(s).first }
    func containsMatchIn(_ s: String) -> Bool { re.firstMatch(in: s, range: ns(s)) != nil }
    func matches(_ s: String) -> Bool {
        guard let m = re.firstMatch(in: s, options: [.anchored], range: ns(s)) else { return false }
        return m.range.length == (s as NSString).length
    }
    /** `$1`-style templates, as Kotlin's replace. */
    func replace(_ s: String, _ template: String) -> String {
        re.stringByReplacingMatches(in: s, range: ns(s), withTemplate: template)
    }
    func replace(_ s: String, _ transform: ([String?]) -> String) -> String {
        var out = ""
        var last = s.startIndex
        for m in re.matches(in: s, range: ns(s)) {
            guard let r = Range(m.range, in: s) else { continue }
            out += s[last..<r.lowerBound]
            let groups = (0..<m.numberOfRanges).map { i in Range(m.range(at: i), in: s).map { String(s[$0]) } }
            out += transform(groups)
            last = r.upperBound
        }
        out += s[last...]
        return out
    }
    func split(_ s: String) -> [String] {
        var out: [String] = []
        var last = s.startIndex
        for m in re.matches(in: s, range: ns(s)) {
            guard let r = Range(m.range, in: s) else { continue }
            out.append(String(s[last..<r.lowerBound]))
            last = r.upperBound
        }
        out.append(String(s[last...]))
        return out
    }
}

extension Array {
    func getOrNull(_ i: Int) -> Element? { i >= 0 && i < count ? self[i] : nil }
    func take(_ n: Int) -> [Element] { Array(prefix(Swift.max(0, n))) }
    func drop(_ n: Int) -> [Element] { Array(dropFirst(Swift.max(0, n))) }
    func takeLast(_ n: Int) -> [Element] { Array(suffix(Swift.max(0, n))) }
}

extension Array where Element: Hashable {
    func distinct() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}

extension Sequence {
    func distinctBy<K: Hashable>(_ key: (Element) -> K) -> [Element] {
        var seen = Set<K>()
        return filter { seen.insert(key($0)).inserted }
    }
}

/** Sleeps for `ms` milliseconds; false when the task was cancelled (like a coroutine's delay throwing). */
@discardableResult
func delay(_ ms: Int) async -> Bool {
    do { try await Task.sleep(nanoseconds: UInt64(max(0, ms)) * 1_000_000); return true } catch { return false }
}

/** The time since the device started, in ms (Android's SystemClock.uptimeMillis). */
func uptimeMs() -> Int64 { Int64(ProcessInfo.processInfo.systemUptime * 1000) }

/** An ordered dictionary, for Kotlin's linkedMapOf. */
struct OrderedMap<K: Hashable, V> {
    private(set) var keys: [K] = []
    private var dict: [K: V] = [:]
    init(_ pairs: [(K, V)]) { for (k, v) in pairs { if dict[k] == nil { keys.append(k) }; dict[k] = v } }
    subscript(_ k: K) -> V? { dict[k] }
    var values: [V] { keys.map { dict[$0]! } }
    var entries: [(K, V)] { keys.map { ($0, dict[$0]!) } }
    func contains(_ k: K) -> Bool { dict[k] != nil }
}

/** Kotlin's CompletableDeferred, for a value one part of a game waits on and another gives. */
@MainActor
final class Deferred<T> {
    private var result: T?
    private var waiters: [CheckedContinuation<T?, Never>] = []
    private(set) var isCompleted = false

    @discardableResult
    func complete(_ v: T) -> Bool {
        if isCompleted { return false }
        isCompleted = true
        result = v
        let w = waiters
        waiters = []
        w.forEach { $0.resume(returning: v) }
        return true
    }

    /** The value, or nil after `timeoutMs` (withTimeoutOrNull { await() }). */
    func wait(timeoutMs: Int) async -> T? {
        if isCompleted { return result }
        return await withCheckedContinuation { (c: CheckedContinuation<T?, Never>) in
            waiters.append(c)
            DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(timeoutMs)) { [weak self] in
                guard let self, !self.isCompleted else { return }
                self.isCompleted = true
                let w = self.waiters
                self.waiters = []
                w.forEach { $0.resume(returning: nil) }
            }
        }
    }
}
