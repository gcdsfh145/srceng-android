package me.nillerusr

import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import com.valvesoftware.source.R
import java.io.IOException
import java.net.URL

class UpdateSystem(context: Context) : AsyncTask<String, Int, String>() {
    private val appContext = context.applicationContext
    private val deployBranch = context.getString(R.string.deploy_branch)
    private val lastCommit = context.getString(R.string.last_commit)

    override fun doInBackground(vararg params: String): String? {
        return try {
            URL("https://raw.githubusercontent.com/nillerusr/srceng-deploy/$deployBranch/version")
                .openConnection()
                .getInputStream()
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (_: IOException) {
            null
        }
    }

    override fun onPostExecute(result: String?) {
        if (!result.isNullOrEmpty() && result != lastCommit) {
            appContext.startService(Intent(appContext, UpdateService::class.java).apply {
                putExtra(
                    "update_url",
                    "https://raw.githubusercontent.com/nillerusr/srceng-deploy/$deployBranch/srceng-release.apk",
                )
            })
        }
    }
}
