import AVFoundation

enum AudioSetup {
    /** One session for the whole game: playback with the microphone ready for Puki's ears. */
    static func configure() {
        let s = AVAudioSession.sharedInstance()
        try? s.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetoothA2DP, .mixWithOthers])
        try? s.setActive(true)
    }
}

/** Whether the parent has let Puki use the microphone. */
func hasMicPermission() -> Bool {
    if #available(iOS 17.0, *) { return AVAudioApplication.shared.recordPermission == .granted }
    return AVAudioSession.sharedInstance().recordPermission == .granted
}

/** Asks for the microphone; `done` runs on the main thread with the answer. */
func requestMicPermission(_ done: @escaping (Bool) -> Void) {
    let finish: (Bool) -> Void = { granted in
        DispatchQueue.main.async {
            micRefused = !granted
            done(granted)
        }
    }
    if #available(iOS 17.0, *) {
        AVAudioApplication.requestRecordPermission(completionHandler: finish)
    } else {
        AVAudioSession.sharedInstance().requestRecordPermission(finish)
    }
}

/**
 * Whether the microphone was refused. iOS asks only once, so asking again does nothing at all:
 * Puki answers the tap with a reaction instead.
 */
var micRefused: Bool = {
    if #available(iOS 17.0, *) { return AVAudioApplication.shared.recordPermission == .denied }
    return AVAudioSession.sharedInstance().recordPermission == .denied
}()

/** 16-bit mono PCM (`pcm`, little-endian) at `rate` Hz, wrapped as a WAV file. */
func wavBytes(_ pcm: Data, rate: Int) -> Data {
    var h = Data()
    func u32(_ v: UInt32) { var x = v.littleEndian; h.append(Data(bytes: &x, count: 4)) }
    func u16(_ v: UInt16) { var x = v.littleEndian; h.append(Data(bytes: &x, count: 2)) }
    h.append("RIFF".data(using: .ascii)!); u32(UInt32(36 + pcm.count)); h.append("WAVE".data(using: .ascii)!)
    h.append("fmt ".data(using: .ascii)!); u32(16); u16(1); u16(1)
    u32(UInt32(rate)); u32(UInt32(rate * 2)); u16(2); u16(16)
    h.append("data".data(using: .ascii)!); u32(UInt32(pcm.count))
    return h + pcm
}

/** Where a bundled sound lives: Audio/<name>.<m4a|wav|mp3>. */
func audioURL(_ name: String) -> URL? {
    for ext in ["m4a", "wav", "mp3"] {
        if let u = Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "Audio") ?? Bundle.main.url(forResource: name, withExtension: ext) {
            return u
        }
    }
    return nil
}
