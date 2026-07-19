package me.nillerusr

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/** Copies APK assets that are consumed by native Source Engine code. */
object ExtractAssets {
    const val VPK_NAME = "extras_dir.vpk"

    private const val TAG = "ExtractAssets"
    private const val ASSET_VERSION = 25
    private var preferences: SharedPreferences? = null

    private val fontAssets = arrayOf(
        "DroidSansFallback.ttf",
        "LiberationMono-Regular.ttf",
        "dejavusans-boldoblique.ttf",
        "dejavusans-bold.ttf",
        "dejavusans-oblique.ttf",
        "dejavusans.ttf",
        "Itim-Regular.otf",
    )

    @JvmStatic
    fun extractAssets(context: Context) {
        val prefs = preferences ?: context.getSharedPreferences("mod", Context.MODE_PRIVATE).also {
            preferences = it
        }
        val force = prefs.getInt("assetversion", 0) != ASSET_VERSION
        val dataDir = context.applicationInfo.dataDir
        val filesDir = context.filesDir

        chmod(dataDir, 511)
        chmod(filesDir.path, 511)
        extractAsset(context, VPK_NAME, force)
        fontAssets.forEach { extractAsset(context, it, force) }

        prefs.edit().putInt("assetversion", ASSET_VERSION).commit()
    }

    @JvmStatic
    fun extractVPK(context: Context, force: Boolean) {
        extractAsset(context, VPK_NAME, force)
    }

    @JvmStatic
    fun extractVPK(context: Context) {
        val prefs = preferences ?: context.getSharedPreferences("mod", Context.MODE_PRIVATE).also {
            preferences = it
        }
        extractAsset(context, VPK_NAME, prefs.getInt("pakversion", 0) != ASSET_VERSION)
        prefs.edit().putInt("pakversion", ASSET_VERSION).commit()
    }

    private fun extractAsset(context: Context, assetName: String, force: Boolean) {
        val target = File(context.filesDir, assetName)
        if (!force && target.isFile && target.length() > 0L) return

        val temporary = File(context.filesDir, ".$assetName.tmp")
        try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                    output.fd.sync()
                }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            chmod(target.path, 511)
            Log.d(TAG, "Extracted $assetName to ${target.path}")
        } catch (error: Exception) {
            temporary.delete()
            Log.e("SRCAPK", "Failed to extract $assetName", error)
        }
    }

    private fun chmod(path: String, mode: Int): Int {
        var result = -1
        try {
            result = Runtime.getRuntime()
                .exec(arrayOf("chmod", Integer.toOctalString(mode), path))
                .waitFor()
        } catch (error: Exception) {
            Log.d(TAG, "chmod via shell failed for $path: $error")
        }

        try {
            val fileUtils = Class.forName("android.os.FileUtils")
            val setPermissions = fileUtils.getMethod(
                "setPermissions",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            result = setPermissions.invoke(null, path, mode, -1, -1) as Int
        } catch (error: Exception) {
            Log.d(TAG, "chmod via FileUtils failed for $path: $error")
        }
        return result
    }
}
