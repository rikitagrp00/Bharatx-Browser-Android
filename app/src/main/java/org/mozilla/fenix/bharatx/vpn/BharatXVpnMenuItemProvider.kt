/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.fenix.bharatx.vpn

import android.content.Context
import android.content.Intent
import com.nitaistudio.rikivpn.api.RikiVpn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mozilla.components.compose.base.text.Text
import mozilla.components.compose.menu.data.MenuItem
import mozilla.components.compose.menu.data.MenuItemSummary
import mozilla.components.compose.menu.data.StandardMenuItem
import mozilla.components.compose.menu.store.MenuEvent
import mozilla.components.compose.menu.ui.MenuItemIconRes
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.components.menu.MenuHost
import org.mozilla.fenix.components.menu.MenuItemProvider

class BharatXVpnMenuItemProvider(private val context: Context, scope: CoroutineScope) : MenuItemProvider {
    private data object OpenVpn : MenuEvent

    private fun item(): MenuItem = StandardMenuItem(
        title = Text.Resource(R.string.bharatx_vpn_title),
        icon = MenuItemIconRes(iconsR.drawable.mozac_ic_globe_24),
        summary = MenuItemSummary(text = Text.Resource(BharatXVpn.stateLabel(RikiVpn.getStatus().state))),
        onClickEvent = OpenVpn,
    )

    override val itemFlow: StateFlow<MenuItem?> = RikiVpn.statusFlow.map { item() }
        .stateIn(scope, SharingStarted.Eagerly, item())

    override fun handles(event: MenuEvent): Boolean = event == OpenVpn

    override fun onEvent(event: MenuEvent, menu: MenuHost) {
        context.startActivity(Intent(context, BharatXVpnActivity::class.java))
        menu.dismiss()
    }
}
