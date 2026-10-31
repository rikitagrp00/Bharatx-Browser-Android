/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.fenix.bharatx.monetization

/** An impression, a click, an error or closing an ad cannot create a reward. */
class RewardedVideoPolicy {
    private var shown = false
    private var ended = false
    private var closed = false
    private var failed = false

    fun shown() { if (!closed && !failed) shown = true }
    fun videoEnded(): Boolean {
        if (!shown || ended || closed || failed) return false
        ended = true
        return true
    }
    fun close(): Boolean { closed = true; return ended && !failed }
    fun error() { failed = true; closed = true }
}
