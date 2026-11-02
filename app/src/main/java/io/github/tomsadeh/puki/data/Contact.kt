package io.github.tomsadeh.puki.data

/** How parents reach Puki's makers. */
object Contact {
    /** Shown in the parent settings' About card, for questions. */
    const val EMAIL = "hello@pukiparrot.com"

    /**
     * Where a parent's report goes when they choose to send it (מלשין לרנינג): their own mail app
     * sends it, after they've seen and edited the text. Empty: the send button stays hidden.
     */
    const val REPORT_EMAIL = "hello+report@pukiparrot.com"
}
