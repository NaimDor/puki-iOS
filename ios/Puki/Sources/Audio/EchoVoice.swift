import AVFoundation

/**
 * Plays the child's recording back as a parrot would: higher and faster, by playing the 16 kHz
 * samples at a higher rate (varispeed). No AI and no network, so it works anywhere.
 */
enum EchoVoice {
    /** Plays `wav` (16 kHz mono, as MicRecorder makes it) `pitch` times higher; returns when done. */
    static func play(_ wav: Data, volume: Float, pitch: Float = 1.45) async {
        guard wav.count > 44 else { return }
        let pcm = trimSilence(wav.subdata(in: 44..<wav.count))
        if pcm.count < MicRecorder.rate / 5 { return }  // under a tenth of a second: nothing said
        let frames = pcm.count / 2
        guard let format = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: Double(MicRecorder.rate), channels: 1, interleaved: false),
              let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(frames)) else { return }
        buffer.frameLength = AVAudioFrameCount(frames)
        pcm.withUnsafeBytes { raw in
            let s = raw.bindMemory(to: Int16.self)
            for i in 0..<frames { buffer.floatChannelData![0][i] = Float(Int16(littleEndian: s[i])) / 32768 }
        }
        let engine = AVAudioEngine()
        let node = AVAudioPlayerNode()
        let vari = AVAudioUnitVarispeed()
        vari.rate = pitch
        engine.attach(node)
        engine.attach(vari)
        engine.connect(node, to: vari, format: format)
        engine.connect(vari, to: engine.mainMixerNode, format: format)
        node.volume = volume.coerceIn(0, 1)
        do { try engine.start() } catch { return }
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
            node.scheduleBuffer(buffer, at: nil, options: [], completionCallbackType: .dataPlayedBack) { _ in c.resume() }
            node.play()
        }
        await delay(150)
        node.stop()
        engine.stop()
    }

    /** Cuts the quiet before and after the talking (keeping a short margin), so the echo comes right away. */
    private static func trimSilence(_ pcm: Data) -> Data {
        let samples = pcm.count / 2
        return pcm.withUnsafeBytes { raw -> Data in
            let s = raw.bindMemory(to: Int16.self)
            func loud(_ i: Int) -> Bool { abs(Int(Int16(littleEndian: s[i]))) > 1500 }
            var start = 0
            while start < samples && !loud(start) { start += 1 }
            var end = samples - 1
            while end > start && !loud(end) { end -= 1 }
            if start >= end { return Data() }
            let margin = MicRecorder.rate / 12
            let from = max(0, start - margin), to = min(samples, end + margin)
            return pcm.subdata(in: (from * 2)..<(to * 2))
        }
    }
}
