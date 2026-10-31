/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.fenix.bharatx.monetization

import com.nitaistudio.rikivpn.api.RikiVpn
import com.nitaistudio.rikivpn.models.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object BharatXPremiumAccess {
    private val policy = PremiumSessionPolicy()
    private val changes = MutableStateFlow(0L)
    val updates = changes.asStateFlow()
    private var observing = false

    fun observe() {
        if (observing) return
        observing = true
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            RikiVpn.statusFlow.collect { status ->
                when (status.state) {
                    VpnState.CONNECTED -> policy.connected(status.profileName)
                    VpnState.DISCONNECTED, VpnState.ERROR -> policy.terminal(status.profileName)
                    else -> Unit
                }
                changes.value++
            }
        }
    }

    internal fun rewardCompleted(serverId: String, requestId: String) {
        if (policy.rewardCompleted(serverId, requestId)) changes.value++
    }

    fun canConnect(serverId: String): Boolean = policy.canConnect(serverId, BharatXBilling.isAdFree())

    fun reserve(serverId: String, profile: String): Boolean =
        policy.reserve(serverId, profile, BharatXBilling.isAdFree()).also { changes.value++ }

    fun failed(profile: String) {
        policy.terminal(profile)
        changes.value++
    }
}
