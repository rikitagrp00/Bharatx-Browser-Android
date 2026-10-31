/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.fenix.bharatx

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Native, offline destinations; these addresses are never sent to the web engine. */
object BharatXPages {
    fun intentFor(context: Context, url: String): Intent? {
        val uri = Uri.parse(url)
        if (uri.scheme != "bharatx" || uri.host !in setOf("about", "privacy", "disclaimer")) {
            return null
        }
        return Intent(context, BharatXInfoActivity::class.java).putExtra("page", uri.host)
    }

    fun openIfLocal(context: Context, url: String): Boolean {
        val intent = intentFor(context, url) ?: if (url == "mailto:${BharatXBrand.EMAIL}") {
            Intent(Intent.ACTION_SENDTO, Uri.parse(url))
        } else {
            return false
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Support email", BharatXBrand.EMAIL))
            Toast.makeText(context, "Support email copied: ${BharatXBrand.EMAIL}", Toast.LENGTH_LONG).show()
        }
        return true
    }
}
