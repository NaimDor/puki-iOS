import AVFoundation

/**
 * Records the child talking to the parrot: 16 kHz mono 16-bit PCM (what the ears want), returned as a
 * WAV file. Stops on its own after `maxSeconds`.
 */
final class MicRecorder {
    static let rate = 16000
    static let maxSeconds = 8

    private let engine = AVAudioEngine()
    private var pcm = Data()
    private let lock = NSLock()
    private var converter: AVAudioConverter?
    private(set) var isRecording = false

    /** How loud the last moment was, 0 (silence) to about 1 (shouting). */
    private(set) var level: Float = 0

    /** Caller must hold the microphone permission. */
    func start() -> Bool {
        if isRecording { return true }
        let input = engine.inputNode
        let inFormat = input.outputFormat(forBus: 0)
        guard inFormat.sampleRate > 0,
              let outFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: Double(Self.rate), channels: 1, interleaved: true),
              let conv = AVAudioConverter(from: inFormat, to: outFormat) else { return false }
        converter = conv
        lock.lock(); pcm = Data(); lock.unlock()
        let maxBytes = Self.rate * 2 * Self.maxSeconds
        input.installTap(onBus: 0, bufferSize: 2048, format: inFormat) { [weak self] buffer, _ in
            guard let self else { return }
            let cap = AVAudioFrameCount(Double(buffer.frameLength) * Double(Self.rate) / inFormat.sampleRate) + 32
            guard let out = AVAudioPCMBuffer(pcmFormat: outFormat, frameCapacity: cap) else { return }
            var fed = false
            var err: NSError?
            conv.convert(to: out, error: &err) { _, status in
                if fed { status.pointee = .noDataNow; return nil }
                fed = true
                status.pointee = .haveData
                return buffer
            }
            guard err == nil, let ch = out.int16ChannelData else { return }
            let n = Int(out.frameLength)
            var sum = 0.0
            for i in 0..<n { let v = Double(ch[0][i]) / 32768; sum += v * v }
            self.level = Float((sum / Double(max(1, n))).squareRoot() * 4)  // speech sits around 0.1-0.6
            self.lock.lock()
            if self.pcm.count < maxBytes { self.pcm.append(Data(bytes: ch[0], count: n * 2)) }
            let full = self.pcm.count >= maxBytes
            self.lock.unlock()
            if full { DispatchQueue.main.async { self.stopEngine() } }
        }
        do {
            engine.prepare()
            try engine.start()
        } catch {
            input.removeTap(onBus: 0)
            return false
        }
        isRecording = true
        return true
    }

    private func stopEngine() {
        guard engine.isRunning || isRecording else { return }
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        level = 0
    }

    /** Stops recording and returns the WAV bytes (empty if nothing was recorded). */
    func stop() -> Data {
        stopEngine()
        isRecording = false
        lock.lock(); let data = pcm; pcm = Data(); lock.unlock()
        return data.isEmpty ? data : wavBytes(data, rate: Self.rate)
    }

    func startOffMain() async -> Bool { await Task.detached { self.start() }.value }
    func stopOffMain() async -> Data { await Task.detached { self.stop() }.value }

    /** For a screen closing, which can't wait: the microphone goes off, just not on this thread. */
    func stopInBackground() {
        if isRecording { Task.detached { _ = self.stop() } }
    }
}
