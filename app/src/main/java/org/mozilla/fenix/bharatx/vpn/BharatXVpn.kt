/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.fenix.bharatx.vpn

import android.content.Context
import com.nitaistudio.rikivpn.api.RikiVpn
import com.nitaistudio.rikivpn.models.ConnectOptions
import com.nitaistudio.rikivpn.models.VpnCredentials
import com.nitaistudio.rikivpn.models.VpnErrorCode
import com.nitaistudio.rikivpn.models.VpnState
import com.nitaistudio.rikivpn.models.VpnStatus
import com.nitaistudio.rikivpn.parser.OvpnParser
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.mozilla.fenix.R
import org.mozilla.fenix.bharatx.monetization.BharatXPremiumAccess

enum class BharatXVpnTier { FREE, PREMIUM }

data class BharatXVpnServer(
    val id: String,
    val name: String,
    val tier: BharatXVpnTier,
    val assetPath: String,
    val requiresCredentials: Boolean,
    val sha256: String,
) {
    val profileName: String get() = assetPath.substringAfterLast('/').removeSuffix(".ovpn")
}

/** All connections use a bundled, integrity checked profile. No profile or credential is logged. */
object BharatXVpn {
    fun initialize(context: Context) {
        RikiVpn.setLoggingEnabled(false)
        RikiVpn.initialize(context.applicationContext)
        BharatXPremiumAccess.observe()
    }

    suspend fun loadServers(context: Context): List<BharatXVpnServer> = withContext(Dispatchers.IO) {
        val catalog = context.assets.open("vpn/catalog.json").bufferedReader().use { JSONObject(it.readText()) }
        require(catalog.getInt("schemaVersion") == 1)
        val entries = catalog.getJSONArray("servers")
        val servers = (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            val tier = when (entry.getString("tier")) {
                "free" -> BharatXVpnTier.FREE
                "premium" -> BharatXVpnTier.PREMIUM
                else -> error("Invalid VPN tier")
            }
            require(entry.getString("access") == if (tier == BharatXVpnTier.PREMIUM) "rewarded" else "free")
            val server = BharatXVpnServer(
                id = entry.getString("id"),
                name = entry.getString("name"),
                tier = tier,
                assetPath = entry.getString("asset"),
                requiresCredentials = entry.getBoolean("requiresCredentials"),
                sha256 = entry.getString("sha256"),
            )
            require(server.id.matches(Regex("[a-z0-9-]{1,64}")))
            require(server.name.isNotBlank() && server.name.length <= 80)
            val directory = if (tier == BharatXVpnTier.FREE) "free" else "premium"
            require(server.assetPath.matches(Regex("vpn/$directory/[a-zA-Z0-9_.-]+\\.ovpn")))
            require(server.sha256.matches(Regex("[a-f0-9]{64}")))
            validateProfile(context, server)
            server
        }
        require(servers.map { it.id }.distinct().size == servers.size)
        require(servers.map { it.assetPath }.distinct().size == servers.size)
        servers
    }

    private fun validateProfile(context: Context, server: BharatXVpnServer) {
        val bytes = context.assets.open(server.assetPath).use { it.readBytes() }
        require(bytes.size in 1..262144)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(digest == server.sha256) { "VPN profile integrity check failed" }
        val profile = OvpnParser.parse(name = server.profileName, text = bytes.toString(Charsets.UTF_8))
        require(profile.embeddedCredentials == null) { "Do not bundle VPN account credentials" }
        require(profile.requiresCredentials == server.requiresCredentials)
        // Catalog tier controls this app's reward access. Provider authentication remains independent.
    }

    suspend fun connect(context: Context, server: BharatXVpnServer, credentials: VpnCredentials?): Boolean {
        withContext(Dispatchers.IO) { validateProfile(context, server) }
        require(!server.requiresCredentials || (credentials != null && credentials.username.isNotBlank() && credentials.password.isNotEmpty()))
        return withContext(Dispatchers.Main.immediate) {
            val premium = server.tier == BharatXVpnTier.PREMIUM
            if (premium && !BharatXPremiumAccess.reserve(server.id, server.profileName)) throw PremiumVpnUnlockRequired()
            var accepted = false
            try {
                accepted = RikiVpn.connectFromAssets(
                    context,
                    server.assetPath,
                    credentials,
                    ConnectOptions(
                        autoReconnect = true,
                        maxReconnectAttempts = 5,
                        allowedApps = setOf(context.packageName),
                        blockIpv6 = true,
                        sessionName = context.getString(R.string.bharatx_vpn_title),
                        notificationTitle = context.getString(R.string.bharatx_vpn_title),
                    ),
                )
                accepted
            } finally {
                if (premium && !accepted) BharatXPremiumAccess.failed(server.profileName)
            }
        }
    }

    fun isActive(state: VpnState): Boolean = state !in setOf(VpnState.DISCONNECTED, VpnState.ERROR)

    fun stateLabel(state: VpnState): Int = when (state) {
        VpnState.DISCONNECTED -> R.string.bharatx_vpn_disconnected
        VpnState.CONNECTING -> R.string.bharatx_vpn_connecting
        VpnState.AUTHENTICATING -> R.string.bharatx_vpn_authenticating
        VpnState.CONNECTED -> R.string.bharatx_vpn_connected
        VpnState.RECONNECTING -> R.string.bharatx_vpn_reconnecting
        VpnState.WAITING_FOR_NETWORK -> R.string.bharatx_vpn_waiting
        VpnState.DISCONNECTING -> R.string.bharatx_vpn_disconnecting
        VpnState.ERROR -> R.string.bharatx_vpn_error
    }

    fun errorLabel(status: VpnStatus): Int = when (status.error?.code) {
        VpnErrorCode.AUTH_FAILED, VpnErrorCode.CREDENTIALS_REQUIRED -> R.string.bharatx_vpn_auth_error
        VpnErrorCode.PERMISSION_DENIED, VpnErrorCode.REVOKED -> R.string.bharatx_vpn_permission_error
        VpnErrorCode.ENGINE_MISSING -> R.string.bharatx_vpn_engine_error
        VpnErrorCode.PROFILE_INVALID, VpnErrorCode.PROFILE_NOT_FOUND -> R.string.bharatx_vpn_profile_error
        else -> R.string.bharatx_vpn_connection_error
    }
}

class PremiumVpnUnlockRequired : IllegalStateException("Complete a rewarded video or restore an active subscription")
