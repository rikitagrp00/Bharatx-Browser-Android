/* SPDX-License-Identifier: MPL-2.0 */
import com.android.billingclient.api.Purchase;
import org.json.JSONObject;
import org.mozilla.fenix.bharatx.monetization.BharatXPurchasePolicy;
import org.mozilla.fenix.bharatx.monetization.PremiumSessionPolicy;
import org.mozilla.fenix.bharatx.monetization.RewardedVideoPolicy;

/** Exercises real app policy and real Play Billing Purchase models without making ad/billing requests. */
public class MonetizationPolicySmoke {
    static final String APP = "com.nitaistudio.bharatxbrowser";
    static final String PRODUCT = "bharatx_remove_ads_monthly";
    static int checks;
    static void check(boolean value) { if (!value) throw new AssertionError("Monetization policy check " + (checks + 1)); checks++; }
    public static void main(String[] args) throws Exception {
        PremiumSessionPolicy access = new PremiumSessionPolicy();
        check(!access.canConnect("lithuania", false));
        check(!access.reserve("lithuania", "lithuania", false));
        check(access.rewardCompleted("lithuania", "video-1"));
        check(!access.rewardCompleted("lithuania", "video-1"));
        check(!access.canConnect("france", false));
        check(access.reserve("lithuania", "lithuania", false));
        check(!access.reserve("lithuania", "lithuania", false));
        access.terminal("lithuania");
        check(access.canConnect("lithuania", false)); // Failed before first connection: retry is free.
        check(access.reserve("lithuania", "lithuania", false));
        access.connected("lithuania");
        access.terminal("unrelated");
        check(!access.canConnect("lithuania", false));
        access.terminal("lithuania");
        check(!access.canConnect("lithuania", false)); // Successful session consumes reward.
        check(access.canConnect("lithuania", true));
        check(access.reserve("lithuania", "lithuania", true)); // Active subscription bypasses all ads.

        RewardedVideoPolicy early = new RewardedVideoPolicy();
        early.shown();
        check(!early.close());
        check(!early.videoEnded()); // A stale callback after close cannot unlock.
        RewardedVideoPolicy complete = new RewardedVideoPolicy();
        check(!complete.videoEnded()); // A video must actually have been shown.
        complete.shown();
        check(complete.videoEnded());
        check(!complete.videoEnded()); // Duplicate SDK reward callback.
        check(complete.close());
        RewardedVideoPolicy error = new RewardedVideoPolicy();
        error.shown(); error.error();
        check(!error.videoEnded());
        check(!error.close());

        check(classify(receipt(0, true, false, APP, PRODUCT, "token")) == BharatXPurchasePolicy.Decision.ACTIVE);
        check(classify(receipt(0, false, false, APP, PRODUCT, "token")) == BharatXPurchasePolicy.Decision.ACKNOWLEDGE);
        check(classify(receipt(4, false, false, APP, PRODUCT, "token")) == BharatXPurchasePolicy.Decision.PENDING);
        check(classify(receipt(4, true, false, APP, PRODUCT, "token")) != BharatXPurchasePolicy.Decision.ACTIVE);
        check(classify(receipt(0, true, true, APP, PRODUCT, "token")) == BharatXPurchasePolicy.Decision.NONE);
        check(classify(receipt(0, true, false, APP + ".debug", PRODUCT, "token")) == BharatXPurchasePolicy.Decision.NONE);
        check(classify(receipt(0, true, false, APP, "other-product", "token")) == BharatXPurchasePolicy.Decision.NONE);
        check(classify(receipt(0, true, false, APP, PRODUCT, "")) == BharatXPurchasePolicy.Decision.NONE);
        JSONObject cancelled = receipt(0, true, false, APP, PRODUCT, "token").put("autoRenewing", false);
        check(classify(cancelled) == BharatXPurchasePolicy.Decision.ACTIVE); // Cancelled renewal retains the paid period.
        System.out.println("Monetization policy checks passed: " + checks + "; no ads loaded, purchase launched or VPN started");
    }
    static JSONObject receipt(int state, boolean acknowledged, boolean suspended, String app, String product, String token) {
        return new JSONObject().put("packageName", app).put("productId", product).put("purchaseToken", token)
            .put("purchaseState", state).put("acknowledged", acknowledged).put("suspended", suspended);
    }
    static BharatXPurchasePolicy.Decision classify(JSONObject json) throws Exception {
        return BharatXPurchasePolicy.INSTANCE.classify(new Purchase(json.toString(), "fixture-not-a-real-receipt"), APP, PRODUCT);
    }
}
