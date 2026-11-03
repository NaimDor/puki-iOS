import Foundation

/** What a brain last said aloud, and what it heard the child say just before (for reports). */
struct Said { let text: String; let heard: String }

/** A brain's answer: the body (audio or JSON; empty for "nothing to say") and the line it had Puki say, if any. */
struct BrainResponse { let body: Data; let said: Said? }

/**
 * Where Puki's thinking happens: on this device (LocalBrain). The game asks it for things by path:
 *
 *  - GET  /health           {"brain": "ready" | "waking" | "asleep"}
 *  - POST /wake             start loading the models
 *  - POST /say              {"situation", "lang"} -> a line about it, spoken (audio)
 *  - POST /speak            {"text", "lang", "speed"} -> that text, spoken (audio)
 *  - POST /line             {"task", "input", "lang", and the task's check} -> a line for a game's own task, spoken
 *  - POST /listen?lang=&context=   a WAV of the child -> a spoken answer (audio)
 *  - POST /repeat?lang=&target=&alt=   a WAV -> {"heard", "match"} (Say it with Puki)
 *  - POST /hear?lang=&hint=        a WAV -> {"heard"} (answering a game out loud, see SpokenAnswer)
 *
 * Every POST also carries ?name=&gender= (the child, from the app's settings).
 */
protocol Brain: AnyObject {
    /** The response, or nil if it failed. Blocking: call it off the main thread. */
    func request(_ method: String, _ path: String, body: Data?, timeoutMs: Int, contentType: String) -> BrainResponse?
}
