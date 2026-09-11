package com.spiritdev.proxyvault.data

import android.content.Context
import android.content.SharedPreferences

enum class Tier { FREE, PREMIUM, PRO, LIFETIME }

object LicenseManager {
    private const val PREFS = "proxyvault_license"
    private const val KEY_TIER = "tier"
    private const val KEY_CODE = "activated_code"
    private const val KEY_REDEEMED = "redeemed_codes"

    private val PREMIUM_CODES = setOf("premium-mr-unknown", "mr-premium-unknown")
    private val PRO_CODES = setOf("pro-mr-unknown", "pro-unknown-mr")
    private val LIFETIME_CODES = setOf("lifetime-mr-unknown09")

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun currentTier(ctx: Context): Tier {
        val name = prefs(ctx).getString(KEY_TIER, Tier.FREE.name) ?: Tier.FREE.name
        return try { Tier.valueOf(name) } catch (_: Exception) { Tier.FREE }
    }

    fun activatedCode(ctx: Context): String? = prefs(ctx).getString(KEY_CODE, null)

    fun exportLimit(ctx: Context): Int = when (currentTier(ctx)) {
        Tier.FREE -> 50
        else -> Int.MAX_VALUE
    }

    fun validationCap(ctx: Context): Int = when (currentTier(ctx)) {
        Tier.FREE -> 400
        Tier.PREMIUM -> 1200
        Tier.PRO, Tier.LIFETIME -> 3000
    }

    data class RedeemResult(val ok: Boolean, val message: String, val tier: Tier = Tier.FREE)

    fun redeem(ctx: Context, rawCode: String): RedeemResult {
        val code = rawCode.trim().lowercase().replace(" ", "")
        if (code.isEmpty()) return RedeemResult(false, "Enter a code")
        val redeemed = prefs(ctx).getStringSet(KEY_REDEEMED, emptySet())?.toSet() ?: emptySet()
        if (code in redeemed) return RedeemResult(false, "Code already used on this device")
        val tier = when {
            code in LIFETIME_CODES -> Tier.LIFETIME
            code in PRO_CODES -> Tier.PRO
            code in PREMIUM_CODES -> Tier.PREMIUM
            else -> null
        } ?: return RedeemResult(false, "Invalid code")
        val current = currentTier(ctx)
        val order = listOf(Tier.FREE, Tier.PREMIUM, Tier.PRO, Tier.LIFETIME)
        val next = if (order.indexOf(current) >= order.indexOf(tier)) current else tier
        prefs(ctx).edit()
            .putString(KEY_TIER, next.name)
            .putString(KEY_CODE, code)
            .putStringSet(KEY_REDEEMED, redeemed + code)
            .apply()
        val label = when (next) {
            Tier.LIFETIME -> "Lifetime"
            Tier.PRO -> "Pro"
            Tier.PREMIUM -> "Premium"
            else -> "Free"
        }
        return RedeemResult(true, "Activated $label", next)
    }
}
