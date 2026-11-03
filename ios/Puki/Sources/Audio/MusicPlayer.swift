import AVFoundation

/**
 * Background music. Tracks are optional files (music_hub, music_night, music_dance): a missing track just
 * means silence. Switching tracks fades out and in.
 */
@MainActor
final class MusicPlayer {
    private var player: AVAudioPlayer?
    private var enabled = true
    private var currentTrack: String?
    private var ducked = false
    private static let baseVolume: Float = 0.4
    private static let duckedVolume: Float = 0.12
    private static let fade: TimeInterval = 1.2

    /** The parent's music volume, 0..1 (parent settings). */
    var level: Float = 1 {
        didSet { level = level.coerceIn(0, 1); player?.setVolume(target(), fadeDuration: Self.fade) }
    }

    private func target() -> Float { (ducked ? Self.duckedVolume : Self.baseVolume) * level }

    /** Plays the track `name` on a loop, fading over from whatever was playing. */
    func playTrack(_ name: String) {
        if name == currentTrack && player != nil { return }
        guard let url = audioURL(name) else { return }
        currentTrack = name
        let old = player
        player = nil
        if let old {
            old.setVolume(0, fadeDuration: Self.fade)
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.fade) { old.stop() }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + (old == nil ? 0 : Self.fade)) { [weak self] in
            guard let self, self.currentTrack == name else { return }
            guard let p = try? AVAudioPlayer(contentsOf: url) else { return }
            p.numberOfLoops = -1
            p.volume = 0
            p.prepareToPlay()
            self.player = p
            if self.enabled && !self.held && self.foreground { p.play() }
            p.setVolume(self.target(), fadeDuration: Self.fade)
        }
    }

    /** Quieter while something important is spoken (e.g. a bedtime story). */
    func duck(_ on: Bool) {
        ducked = on
        player?.setVolume(target(), fadeDuration: Self.fade)
    }

    func pause() { if player?.isPlaying == true { player?.pause() } }

    /** The game is on screen; away, nothing starts the music. */
    var foreground = true

    func resume() {
        guard enabled, !held, foreground, let p = player, !p.isPlaying else { return }
        p.play()
    }

    /**
     * While held (the time limit's goodbye is up), nothing restarts the music: not a game's loop,
     * not coming back to the app. Letting go doesn't start it either; whoever held it resumes.
     */
    var held = false

    func stop() {
        player?.stop()
        player = nil
        currentTrack = nil
    }

    func setEnabled(_ value: Bool) {
        enabled = value
        if !value { pause() } else if player != nil { resume() }
    }

    func release() { stop() }
}
