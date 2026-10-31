/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.fenix.bharatx

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.mozilla.fenix.R

/** Renders bundled policy text without a WebView, JavaScript or network requests. */
class BharatXInfoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = intent.getStringExtra("page").takeIf { it in setOf("about", "privacy", "disclaimer") } ?: "about"
        val title = when (page) {
            "privacy" -> "Privacy Policy"
            "disclaimer" -> "Disclaimer"
            else -> "About BharatX Browser"
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(6, 21, 40))
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(dp(20), insets.systemWindowInsetTop + dp(20), dp(20), insets.systemWindowInsetBottom + dp(20))
            insets
        }
        root.addView(Button(this).apply {
            text = "Back"
            contentDescription = "Go back"
            setOnClickListener { finish() }
        })
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.bharatx_logo)
            contentDescription = BharatXBrand.NAME
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(112)))
        root.addView(TextView(this).apply {
            text = title
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(16))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setAccessibilityHeading(true)
        })
        val html = assets.open("bharatx/$page.html").bufferedReader().use { it.readText() }
        root.addView(TextView(this).apply {
            text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            textSize = 16f
            setTextColor(Color.WHITE)
            setLinkTextColor(Color.rgb(102, 213, 255))
            setLineSpacing(dp(4).toFloat(), 1.1f)
            setTextIsSelectable(true)
            movementMethod = LinkMovementMethod.getInstance()
        })
        root.addView(Button(this).apply {
            text = "Contact support"
            setOnClickListener { BharatXPages.openIfLocal(this@BharatXInfoActivity, "mailto:${BharatXBrand.EMAIL}") }
        })
        root.addView(Button(this).apply {
            text = "Developer website"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BharatXBrand.WEBSITE)).setPackage(packageName))
            }
        })
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.requestApplyInsets()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
