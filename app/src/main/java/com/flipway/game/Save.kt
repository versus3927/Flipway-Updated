package com.flipway.game

import android.content.Context

/** Прогресс игрока: рекорд, кошелёк, скины, прокачка бонусов. */
class Save(ctx: Context) {
    private val sp = ctx.getSharedPreferences("flipway", Context.MODE_PRIVATE)

    var best: Int
        get() = sp.getInt("best", 0)
        set(v) = sp.edit().putInt("best", v).apply()
    var wallet: Int
        get() = sp.getInt("wallet", 0)
        set(v) = sp.edit().putInt("wallet", v).apply()
    var runs: Int
        get() = sp.getInt("runs", 0)
        set(v) = sp.edit().putInt("runs", v).apply()
    var skin: Int
        get() = sp.getInt("skin", 0).coerceIn(0, Palette.skins.size - 1)
        set(v) = sp.edit().putInt("skin", v).apply()
    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()

    fun owned(i: Int) = i == 0 || sp.getBoolean("skin$i", false)
    fun own(i: Int) = sp.edit().putBoolean("skin$i", true).apply()

    fun level(p: Power) = sp.getInt("up_${p.name}", 0)
    fun setLevel(p: Power, v: Int) = sp.edit().putInt("up_${p.name}", v).apply()
    fun upgrades() = IntArray(Power.values().size) { level(Power.values()[it]) }

    companion object {
        private val UPGRADE_COST = intArrayOf(150, 400, 800, 1300, 2000)
        fun upgradeCost(level: Int) = if (level < UPGRADE_COST.size) UPGRADE_COST[level] else -1
    }
}
