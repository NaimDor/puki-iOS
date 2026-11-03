import AVFoundation

enum Sfx: String, CaseIterable {
    case pop = "snd_pop", success = "snd_success", whoosh = "snd_whoosh", wrongGentle = "snd_wrong_gentle",
         drop = "snd_drop", starCollect = "snd_star_collect", drum = "snd_drum", xylophone = "snd_xylophone",
         tambourine = "snd_tambourine", maracas = "snd_maracas"
}

/**
 * Short sound effects, preloaded (SoundPool on Android): a few players a sound, so taps can overlap,
 * at most six playing at once.
 */
@MainActor
final class SfxPlayer {
    var enabled = true
    /** The parent's sound-effects volume, 0..1 (parent settings). */
    var level: Float = 1
    private var pools: [Sfx: [AVAudioPlayer]] = [:]
    private var next: [Sfx: Int] = [:]
    private static let perSound = 3
    private static let maxStreams = 6

    func preloadAll() {
        for sfx in Sfx.allCases where pools[sfx] == nil {
            guard let url = audioURL(sfx.rawValue) else { continue }
            pools[sfx] = (0..<Self.perSound).compactMap { _ in
                guard let p = try? AVAudioPlayer(contentsOf: url) else { return nil }
                p.enableRate = true
                p.prepareToPlay()
                return p
            }
        }
    }

    /** `rate` plays it faster or slower (0.5..2), which also raises or lowers its pitch. */
    func play(_ sfx: Sfx, volume: Float = 1, rate: Float = 1) {
        guard enabled, level > 0, let pool = pools[sfx], !pool.isEmpty else { return }
        let playing = pools.values.joined().filter { $0.isPlaying }.count
        if playing >= Self.maxStreams { return }
        let i = next[sfx, default: 0]
        next[sfx] = (i + 1) % pool.count
        let p = pool[i]
        p.stop()
        p.currentTime = 0
        p.volume = volume * level
        p.rate = rate.coerceIn(0.5, 2)
        p.play()
    }

    func release() {
        pools.values.joined().forEach { $0.stop() }
    }
}
