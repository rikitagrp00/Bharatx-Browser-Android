/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.fenix.bharatx.monetization

/** One completed video pays for one connection, with retries until the first successful tunnel. */
class PremiumSessionPolicy {
    private var creditFor: String? = null
    private var reservedServer: String? = null
    private var reservedProfile: String? = null
    private var established = false
    private val completedRewards = linkedSetOf<String>()

    @Synchronized
    fun rewardCompleted(serverId: String, requestId: String): Boolean {
        if (!completedRewards.add(requestId)) return false
        while (completedRewards.size > 64) completedRewards.remove(completedRewards.first())
        creditFor = serverId
        return true
    }

    @Synchronized
    fun canConnect(serverId: String, subscribed: Boolean): Boolean = subscribed || creditFor == serverId

    @Synchronized
    fun reserve(serverId: String, profile: String, subscribed: Boolean): Boolean {
        if (subscribed) return true
        if (reservedServer != null || creditFor != serverId) return false
        creditFor = null
        reservedServer = serverId
        reservedProfile = profile
        established = false
        return true
    }

    @Synchronized
    fun connected(profile: String?) {
        if (profile != null && profile == reservedProfile) established = true
    }

    @Synchronized
    fun terminal(profile: String?) {
        if (reservedProfile == null || (profile != null && profile != reservedProfile)) return
        if (!established) creditFor = reservedServer
        reservedServer = null
        reservedProfile = null
        established = false
    }
}
