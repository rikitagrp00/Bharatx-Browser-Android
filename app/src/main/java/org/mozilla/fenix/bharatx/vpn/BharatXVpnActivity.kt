/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.fenix.bharatx.vpn

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import com.nitaistudio.rikivpn.api.RikiVpn
import com.nitaistudio.rikivpn.models.TrafficStats
import com.nitaistudio.rikivpn.models.VpnCredentials
import com.nitaistudio.rikivpn.models.VpnState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.mozilla.fenix.R
import org.mozilla.fenix.bharatx.BharatXBrand
import org.mozilla.fenix.bharatx.BharatXPages
import org.mozilla.fenix.bharatx.monetization.BharatXAdSession
import org.mozilla.fenix.bharatx.monetization.BharatXAds
import org.mozilla.fenix.bharatx.monetization.BharatXBilling
import org.mozilla.fenix.bharatx.monetization.BharatXPremiumAccess

/** Explicit, consent-based control of the SDK foreground VPN service. Closing this screen keeps it running. */
class BharatXVpnActivity : ComponentActivity() {
    private var servers = emptyList<BharatXVpnServer>()
    private var tier = BharatXVpnTier.FREE
    private var selectedId: String? = null
    private var busy = false
    private var uiMessage: Int? = null
    private var pending: Pair<BharatXVpnServer, VpnCredentials?>? = null
    private var credentialsDialog: AlertDialog? = null
    private var disclosureDialog: AlertDialog? = null
    private var choosingAds = false
    private var awaitingDisconnectAd = false
    private lateinit var ads: BharatXAdSession
    private lateinit var premiumNote: TextView
    private lateinit var subscriptionView: TextView
    private lateinit var subscribeButton: Button
    private lateinit var statusView: TextView
    private lateinit var trafficView: TextView
    private lateinit var messageView: TextView
    private lateinit var serverGroup: RadioGroup
    private lateinit var actionButton: Button
    private lateinit var emptyView: TextView
    private lateinit var freeButton: Button
    private lateinit var premiumButton: Button

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Denial does not remove Android's VPN consent requirement or stop the foreground service.
        val request = pending
        pending = null
        if (request != null) startConnection(request.first, request.second)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BharatXVpn.initialize(this)
        BharatXBilling.initialize(this)
        selectedId = savedInstanceState?.getString("selectedServer")
        tier = if (savedInstanceState?.getBoolean("premium") == true) BharatXVpnTier.PREMIUM else BharatXVpnTier.FREE
        createContent()
        lifecycleScope.launch {
            try {
                servers = BharatXVpn.loadServers(this@BharatXVpnActivity)
                val active = servers.firstOrNull { it.profileName == RikiVpn.getStatus().profileName }
                if (active != null && BharatXVpn.isActive(RikiVpn.getStatus().state)) {
                    selectedId = active.id
                    tier = active.tier
                } else if (selectedId == null) {
                    selectedId = servers.firstOrNull { it.tier == tier }?.id
                }
                showServers()
                renderStatus()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                uiMessage = R.string.bharatx_vpn_profile_error
                renderStatus()
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    RikiVpn.statusFlow.collect { status ->
                        renderStatus()
                        if (status.state == VpnState.ERROR) awaitingDisconnectAd = false
                        if (awaitingDisconnectAd && status.state == VpnState.DISCONNECTED) {
                            awaitingDisconnectAd = false
                            ads.afterDisconnect()
                        }
                        ads.reconcile()
                    }
                }
                launch { RikiVpn.trafficFlow.collect { renderTraffic(it) } }
                launch {
                    BharatXBilling.state.collect {
                        renderSubscription()
                        renderStatus()
                        ads.reconcile()
                        maybeAskAdChoices()
                    }
                }
                launch { BharatXPremiumAccess.updates.collect { renderStatus() } }
            }
        }
    }

    private fun createContent() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
            setBackgroundColor(Color.rgb(6, 21, 40))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(dp(20), insets.systemWindowInsetTop + dp(16), dp(20), insets.systemWindowInsetBottom + dp(24))
            insets
        }
        root.addView(button(R.string.bharatx_vpn_back) { finish() })
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.bharatx_logo)
            contentDescription = BharatXBrand.NAME
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(80)))
        root.addView(label(R.string.bharatx_vpn_title, 28f).apply { gravity = Gravity.CENTER })
        root.addView(label(R.string.bharatx_vpn_scope, 15f))
        statusView = label(R.string.bharatx_vpn_disconnected, 22f).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(statusView)
        trafficView = label(R.string.bharatx_vpn_no_traffic, 14f)
        root.addView(trafficView)
        messageView = label(R.string.bharatx_vpn_choose_server, 15f).apply {
            setTextColor(Color.rgb(255, 204, 128))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(messageView)
        val tabs = LinearLayout(this)
        freeButton = button(R.string.bharatx_vpn_free) { chooseTier(BharatXVpnTier.FREE) }
        premiumButton = button(R.string.bharatx_vpn_premium) { chooseTier(BharatXVpnTier.PREMIUM) }
        tabs.addView(freeButton, LinearLayout.LayoutParams(0, dp(56), 1f))
        tabs.addView(premiumButton, LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(tabs)
        emptyView = label(R.string.bharatx_vpn_loading, 16f)
        root.addView(emptyView)
        serverGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        root.addView(serverGroup)
        premiumNote = label(R.string.bharatx_reward_terms, 14f)
        root.addView(premiumNote)
        actionButton = button(R.string.bharatx_vpn_connect) {
            if (BharatXVpn.isActive(RikiVpn.getStatus().state)) {
                awaitingDisconnectAd = true
                RikiVpn.disconnect()
            } else {
                servers.firstOrNull { it.id == selectedId && it.tier == tier }?.let { discloseAndConnect(it) }
            }
        }.apply { isEnabled = false }
        root.addView(actionButton)
        root.addView(label(R.string.bharatx_vpn_reconnect_note, 14f))
        subscriptionView = label(R.string.bharatx_subscription_checking, 16f)
        root.addView(subscriptionView)
        subscribeButton = button(R.string.bharatx_subscription_loading_price) { BharatXBilling.buy(this) }
        root.addView(subscribeButton)
        root.addView(label(R.string.bharatx_subscription_terms, 14f))
        root.addView(button(R.string.bharatx_subscription_restore) { BharatXBilling.refresh() })
        root.addView(button(R.string.bharatx_subscription_manage) { BharatXBilling.manage(this) })
        root.addView(button(R.string.bharatx_ads_choices) { chooseAds { } })
        val bannerHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        bannerHolder.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(dp(20), dp(4), dp(20), insets.systemWindowInsetBottom + dp(4))
            insets
        }
        ads = BharatXAdSession(this, bannerHolder)
        root.addView(button(R.string.bharatx_vpn_privacy) { BharatXPages.openIfLocal(this, BharatXBrand.PRIVACY) })
        root.addView(button(R.string.bharatx_vpn_support) { BharatXPages.openIfLocal(this, "mailto:${BharatXBrand.EMAIL}") })
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(6, 21, 40))
            addView(ScrollView(this@BharatXVpnActivity).apply { addView(root) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bannerHolder)
        }
        setContentView(page)
        root.requestApplyInsets()
    }

    private fun chooseTier(value: BharatXVpnTier) {
        tier = value
        if (servers.none { it.id == selectedId && it.tier == tier }) {
            selectedId = servers.firstOrNull { it.tier == tier }?.id
        }
        showServers()
        renderStatus()
    }

    private fun showServers() {
        freeButton.isSelected = tier == BharatXVpnTier.FREE
        premiumButton.isSelected = tier == BharatXVpnTier.PREMIUM
        freeButton.alpha = if (freeButton.isSelected) 1f else 0.65f
        premiumButton.alpha = if (premiumButton.isSelected) 1f else 0.65f
        serverGroup.removeAllViews()
        val visible = servers.filter { it.tier == tier }
        emptyView.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        emptyView.setText(if (tier == BharatXVpnTier.PREMIUM) R.string.bharatx_vpn_no_premium else R.string.bharatx_vpn_no_free)
        visible.forEach { server ->
            serverGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = server.name
                textSize = 18f
                setTextColor(Color.WHITE)
                minHeight = dp(56)
                isChecked = server.id == selectedId
                setOnClickListener {
                    selectedId = server.id
                    renderStatus()
                }
            })
        }
    }

    private fun renderStatus() {
        val status = RikiVpn.getStatus()
        val active = BharatXVpn.isActive(status.state)
        val engine = RikiVpn.isEngineAvailable(this)
        val server = servers.firstOrNull { it.profileName == status.profileName }
        statusView.text = getString(BharatXVpn.stateLabel(status.state)) +
            if (active && server != null) " · ${server.name}" else ""
        statusView.setTextColor(if (status.state == VpnState.CONNECTED) Color.rgb(92, 230, 166) else Color.WHITE)
        val selected = servers.firstOrNull { it.id == selectedId && it.tier == tier }
        val needsVideo = selected?.tier == BharatXVpnTier.PREMIUM && !BharatXPremiumAccess.canConnect(selected.id)
        actionButton.setText(when {
            active -> R.string.bharatx_vpn_disconnect
            needsVideo -> R.string.bharatx_reward_connect
            else -> R.string.bharatx_vpn_connect
        })
        premiumNote.visibility = if (tier == BharatXVpnTier.PREMIUM) View.VISIBLE else View.GONE
        premiumNote.setText(when {
            BharatXBilling.isAdFree() -> R.string.bharatx_subscription_active
            selected != null && BharatXPremiumAccess.canConnect(selected.id) -> R.string.bharatx_reward_ready
            else -> R.string.bharatx_reward_terms
        })
        actionButton.isEnabled = !busy && status.state != VpnState.DISCONNECTING &&
            (active || (engine && servers.any { it.id == selectedId && it.tier == tier }))
        for (index in 0 until serverGroup.childCount) serverGroup.getChildAt(index).isEnabled = !active && !busy
        messageView.setText(
            when {
                busy && uiMessage != null -> uiMessage!!
                !engine -> R.string.bharatx_vpn_engine_error
                status.state == VpnState.ERROR -> BharatXVpn.errorLabel(status)
                active -> R.string.bharatx_vpn_active_note
                else -> uiMessage ?: R.string.bharatx_vpn_choose_server
            },
        )
        renderTraffic(RikiVpn.getTrafficStats())
    }

    private fun renderTraffic(stats: TrafficStats) {
        trafficView.text = getString(
            R.string.bharatx_vpn_traffic,
            Formatter.formatShortFileSize(this, stats.uploadBytes),
            Formatter.formatShortFileSize(this, stats.downloadBytes),
            Formatter.formatShortFileSize(this, stats.uploadBytesPerSecond),
            Formatter.formatShortFileSize(this, stats.downloadBytesPerSecond),
        )
    }

    private fun discloseAndConnect(server: BharatXVpnServer) {
        disclosureDialog = AlertDialog.Builder(this)
            .setTitle(R.string.bharatx_vpn_disclosure_title)
            .setMessage(getString(R.string.bharatx_vpn_disclosure, server.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.bharatx_vpn_agree) { _, _ ->
                if (server.tier == BharatXVpnTier.PREMIUM && !BharatXPremiumAccess.canConnect(server.id)) {
                    if (BharatXAds.allowed(this)) watchVideo(server)
                    else chooseAds { allowed -> if (allowed) watchVideo(server) }
                } else continueAfterAccess(server)
            }
            .setOnDismissListener { disclosureDialog = null }
            .show()
    }

    private fun continueAfterAccess(server: BharatXVpnServer) {
        if (server.requiresCredentials) askCredentials(server) else requestNotification(server, null)
    }

    private fun watchVideo(server: BharatXVpnServer) {
        busy = true
        uiMessage = R.string.bharatx_reward_loading
        renderStatus()
        try {
            ads.watchReward(server.id) { earned ->
                busy = false
                uiMessage = if (earned) null else R.string.bharatx_reward_unavailable
                renderStatus()
                if (earned) lifecycleScope.launch { lifecycle.withResumed { continueAfterAccess(server) } }
            }
        } catch (_: RuntimeException) {
            busy = false
            uiMessage = R.string.bharatx_reward_unavailable
            renderStatus()
        }
    }

    private fun chooseAds(complete: (Boolean) -> Unit) {
        if (choosingAds) return
        choosingAds = true
        BharatXAds.showChoices(this) { allowed ->
            choosingAds = false
            ads.reconcile()
            complete(allowed)
        }
    }

    private fun maybeAskAdChoices() {
        val subscription = BharatXBilling.state.value
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && subscription.known && !subscription.active &&
            !BharatXAds.choiceMade(this) && !busy && disclosureDialog == null && credentialsDialog == null) chooseAds { }
    }

    private fun renderSubscription() {
        val state = BharatXBilling.state.value
        subscriptionView.setText(when {
            BharatXBilling.isAdFree() -> R.string.bharatx_subscription_active
            state.message != null -> state.message
            state.loading || !state.known -> R.string.bharatx_subscription_checking
            else -> R.string.bharatx_subscription_benefits
        })
        subscribeButton.text = state.price?.let { getString(R.string.bharatx_subscription_price, it) }
            ?: getString(R.string.bharatx_subscription_loading_price)
        subscribeButton.isEnabled = state.known && state.purchasable && !state.loading && !BharatXBilling.isAdFree()
    }

    override fun onResume() {
        super.onResume()
        BharatXBilling.refresh()
        maybeAskAdChoices()
    }

    private fun askCredentials(server: BharatXVpnServer) {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }
        val username = EditText(this).apply {
            hint = getString(R.string.bharatx_vpn_username)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            isSingleLine = true
            isSaveEnabled = false
        }
        val password = EditText(this).apply {
            hint = getString(R.string.bharatx_vpn_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            isSaveEnabled = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fields.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        fields.addView(username)
        fields.addView(password)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.bharatx_vpn_provider_login)
            .setMessage(R.string.bharatx_vpn_provider_login_note)
            .setView(fields)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.bharatx_vpn_connect, null)
            .create()
        credentialsDialog = dialog
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnDismissListener {
            username.text.clear()
            password.text.clear()
            credentialsDialog = null
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (username.text.isBlank()) {
                    username.error = getString(R.string.bharatx_vpn_required)
                } else if (password.text.isEmpty()) {
                    password.error = getString(R.string.bharatx_vpn_required)
                } else {
                    val credentials = VpnCredentials(username.text.toString().trim(), password.text.toString())
                    dialog.dismiss()
                    requestNotification(server, credentials)
                }
            }
        }
        dialog.show()
    }

    private fun requestNotification(server: BharatXVpnServer, credentials: VpnCredentials?) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pending = server to credentials
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startConnection(server, credentials)
        }
    }

    private fun startConnection(server: BharatXVpnServer, credentials: VpnCredentials?) {
        busy = true
        renderStatus()
        lifecycleScope.launch {
            try {
                uiMessage = R.string.bharatx_vpn_requesting
                if (!BharatXVpn.connect(this@BharatXVpnActivity, server, credentials)) {
                    uiMessage = BharatXVpn.errorLabel(RikiVpn.getStatus())
                } else {
                    uiMessage = null
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: PremiumVpnUnlockRequired) {
                uiMessage = R.string.bharatx_reward_required
            } catch (_: Exception) {
                uiMessage = R.string.bharatx_vpn_connection_error
            } finally {
                busy = false
                renderStatus()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selectedServer", selectedId)
        outState.putBoolean("premium", tier == BharatXVpnTier.PREMIUM)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        pending = null
        ads.close()
        disclosureDialog?.dismiss()
        credentialsDialog?.dismiss()
        super.onDestroy()
    }

    private fun label(resource: Int, size: Float) = TextView(this).apply {
        setText(resource)
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(0, dp(10), 0, dp(10))
    }

    private fun button(resource: Int, action: () -> Unit) = Button(this).apply {
        setText(resource)
        minHeight = dp(48)
        setOnClickListener { action() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
