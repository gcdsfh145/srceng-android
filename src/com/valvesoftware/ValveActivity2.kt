package com.valvesoftware

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import me.nillerusr.ExtractAssets
import me.nillerusr.LauncherActivity
import java.io.File
import java.util.Locale

/** Native launch configuration bridge used by SDLActivity. */
object ValveActivity2 {
    @JvmField
    var mPref: SharedPreferences? = null

    @JvmStatic
    external fun setArgs(args: String)

    @JvmStatic
    external fun setenv(name: String, value: String, overwrite: Int): Int

    @JvmStatic
    private external fun nativeOnActivityResult(activity: Activity, requestCode: Int, resultCode: Int, intent: Intent?)

    @JvmStatic
    fun findGameinfo(path: String): Boolean {
        val root = File(path)
        return root.listFiles().orEmpty()
            .filter { it.isDirectory }
            .flatMap { it.listFiles().orEmpty().asSequence().asIterable() }
            .any { it.isFile && it.name.equals("gameinfo.txt", ignoreCase = true) }
    }

    @JvmStatic
    fun isModGameinfoExists(path: String): Boolean {
        return File(path).listFiles().orEmpty()
            .any { it.isFile && it.name.equals("gameinfo.txt", ignoreCase = true) }
    }

    @JvmStatic
    fun preInit(context: Context, intent: Intent): Boolean {
        val prefs = context.getSharedPreferences("mod", Context.MODE_PRIVATE)
        mPref = prefs
        val gamePath = prefs.getString(
            "gamepath",
            LauncherActivity.getDefaultDir() + "/srceng",
        ).orEmpty()
        val gameDir = intent.getStringExtra("gamedir")
            ?.takeIf { it.isNotEmpty() }
            ?: "hl2"

        return findGameinfo(gamePath) && isModGameinfoExists("$gamePath/$gameDir")
    }

    @JvmStatic
    fun initNatives(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("mod", Context.MODE_PRIVATE)
        mPref = prefs
        val appInfo = context.applicationInfo
        val gamePath = prefs.getString(
            "gamepath",
            LauncherActivity.getDefaultDir() + "/srceng",
        ).orEmpty()
        val gameDir = intent.getStringExtra("gamedir")
            ?.takeIf { it.isNotEmpty() }
            ?: "hl2"
        val customVpk = intent.getStringExtra("vpk")
        val gameLibDir = intent.getStringExtra("gamelibdir")

        var argv = intent.getStringExtra("argv")
            ?.takeIf { it.isNotEmpty() }
            ?: prefs.getString("argv", "-console").orEmpty()
        argv = "-game $gameDir $argv"

        if (!gameLibDir.isNullOrEmpty()) setenv("APP_MOD_LIB", gameLibDir, 1)

        // Native font loading uses APP_DATA_PATH/files/*.ttf.
        ExtractAssets.extractAssets(context)
        var vpks = File(context.filesDir, ExtractAssets.VPK_NAME).path
        if (!customVpk.isNullOrEmpty()) vpks = "$customVpk,$vpks"

        setenv("EXTRAS_VPK_PATH", vpks, 1)
        setenv("LANG", Locale.getDefault().toString(), 1)
        setenv("APP_DATA_PATH", appInfo.dataDir, 1)
        setenv("APP_LIB_PATH", appInfo.nativeLibraryDir, 1)

        val valveGamePath = if (prefs.getBoolean("rodir", false)) {
            LauncherActivity.getAndroidDataDir()
        } else {
            gamePath
        }
        setenv("VALVE_GAME_PATH", valveGamePath, 1)

        Log.v("SRCAPK", "argv=$argv")
        Log.v("SRCAPK", "vpks=$vpks")
        Log.v("SRCAPK", "fontDir=${context.filesDir.path}")
        setArgs(argv)
    }
}
