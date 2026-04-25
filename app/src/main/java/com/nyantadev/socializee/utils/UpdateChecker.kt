package com.nyantadev.socializee.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cek versi terbaru aplikasi dari GitHub Releases.
 *
 * Ganti [GITHUB_OWNER] dan [GITHUB_REPO] sesuai repo kamu.
 * Format tag GitHub Release harus: v1.0.0  (dengan awalan "v")
 *
 * Cara pakai di Fragment:
 *   lifecycleScope.launch {
 *       val result = UpdateChecker.check(BuildConfig.VERSION_NAME)
 *       if (result != null) showUpdateBanner(result)
 *   }
 */
object UpdateChecker {

    // ────── GANTI SESUAI REPO KAMU ──────────────────────────────────────────
    private const val GITHUB_OWNER = "ramdanolii14"          // username GitHub
    private const val GITHUB_REPO  = "SocializeeApp"         // nama repo
    // ────────────────────────────────────────────────────────────────────────

    val githubReleasesUrl = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private val apiUrl    = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,   // e.g. "1.2.0"
        val releaseUrl: String,      // link ke halaman release GitHub
        val releaseNotes: String     // body / changelog dari release
    )

    /**
     * Mengembalikan [UpdateInfo] jika ada versi baru, null jika sudah up-to-date
     * atau terjadi error (silent fail — tidak crash app).
     *
     * [currentVersionName] = BuildConfig.VERSION_NAME, e.g. "1.0.0"
     */
    suspend fun check(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod        = "GET"
                connectTimeout       = 5_000
                readTimeout          = 5_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }

            if (conn.responseCode != 200) return@withContext null

            val json        = conn.inputStream.bufferedReader().readText()
            val obj         = JSONObject(json)
            val tagName     = obj.optString("tag_name", "")   // e.g. "v1.2.0"
            val htmlUrl     = obj.optString("html_url", githubReleasesUrl)
            val body        = obj.optString("body", "")

            val latestClean  = tagName.trimStart('v', 'V')
            val currentClean = currentVersionName.trimStart('v', 'V')

            if (latestClean.isBlank()) return@withContext null
            if (!isNewer(latestClean, currentClean)) return@withContext null

            UpdateInfo(latestClean, htmlUrl, body)
        } catch (e: Exception) {
            null // silent fail
        }
    }

    /**
     * Bandingkan dua versi semver (major.minor.patch).
     * Return true jika [latest] > [current].
     */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(l.size, c.size)
        for (i in 0 until maxLen) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}