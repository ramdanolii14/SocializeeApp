package com.nyantadev.socializee.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.nyantadev.socializee.repository.AppRepository
import kotlinx.coroutines.launch
import com.nyantadev.socializee.R
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.ActivityMainBinding
import com.nyantadev.socializee.utils.SessionManager
import com.google.firebase.messaging.FirebaseMessaging
import com.nyantadev.socializee.worker.UpdateCheckWorker
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var navController: NavController

    // ── Daftar izin yang dibutuhkan ───────────────────────────────────────────
    private val requiredPermissions: Array<String>
        get() = buildList {
            // Notifikasi — hanya Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // Baca media (gambar & video)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                // Android 12 ke bawah
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

    // ── Launcher untuk minta banyak izin sekaligus ────────────────────────────
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val denied = results.filterValues { !it }.keys
            if (denied.isNotEmpty()) {
                // Ada izin yang ditolak — tunjukkan dialog penjelasan
                showPermissionDeniedDialog(denied)
            }
            // App tetap berjalan meski izin ditolak;
            // fitur terkait akan dinonaktifkan di tempat masing-masing
        }
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        if (!sessionManager.isLoggedIn()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        RetrofitClient.init(sessionManager)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        scheduleUpdateCheck()

        // Cek & minta izin setelah UI siap
        checkAndRequestPermissions()

        // Daftarkan FCM token ke backend
        registerFcmToken()
    }

    // ── Permission helpers ────────────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter { perm ->
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) return   // semua sudah diberikan

        // Cek apakah ada izin yang perlu penjelasan (user pernah menolak sebelumnya)
        val needsRationale = missing.any { shouldShowRequestPermissionRationale(it) }

        if (needsRationale) {
            showPermissionRationaleDialog(missing.toTypedArray())
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /** Dialog sebelum meminta izin — muncul bila user pernah menolak sebelumnya */
    private fun showPermissionRationaleDialog(permissions: Array<String>) {
        val messages = buildPermissionMessage(permissions.toList())

        AlertDialog.Builder(this)
            .setTitle("Izin Diperlukan")
            .setMessage(
                "Aplikasi membutuhkan izin berikut agar dapat berfungsi dengan baik:\n\n" +
                        messages +
                        "\n\nTap OK untuk melanjutkan."
            )
            .setPositiveButton("OK") { _, _ ->
                permissionLauncher.launch(permissions)
            }
            .setNegativeButton("Nanti") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    /** Dialog sesudah izin ditolak — arahkan user ke Settings */
    private fun showPermissionDeniedDialog(denied: Set<String>) {
        val messages = buildPermissionMessage(denied)

        AlertDialog.Builder(this)
            .setTitle("Izin Ditolak")
            .setMessage(
                "Beberapa izin ditolak:\n\n$messages\n\n" +
                        "Beberapa fitur mungkin tidak berjalan dengan baik. " +
                        "Kamu bisa mengaktifkannya kapan saja melalui Pengaturan."
            )
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Tutup") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /** Buka halaman izin aplikasi di Settings sistem */
    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    /** Ubah nama permission teknis menjadi teks ramah pengguna */
    private fun buildPermissionMessage(permissions: Collection<String>): String {
        return permissions.joinToString("\n") { perm ->
            when (perm) {
                Manifest.permission.POST_NOTIFICATIONS      -> "• Notifikasi — agar kamu bisa menerima pemberitahuan"
                Manifest.permission.READ_MEDIA_IMAGES       -> "• Akses Foto — untuk unggah dan lihat gambar"
                Manifest.permission.READ_MEDIA_VIDEO        -> "• Akses Video — untuk unggah dan putar video"
                Manifest.permission.READ_EXTERNAL_STORAGE   -> "• Penyimpanan — untuk akses foto & video"
                else                                        -> "• ${perm.substringAfterLast('.')}"
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)
    }

    // ── WorkManager ───────────────────────────────────────────────────────────

    private fun scheduleUpdateCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "socializee_update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    // ── FCM Token ─────────────────────────────────────────────────────────────

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val repo = AppRepository(RetrofitClient.getApiService())
            lifecycleScope.launch {
                try {
                    repo.registerDeviceToken(token)
                } catch (e: Exception) {
                    // Silent — akan retry saat app dibuka lagi
                }
            }
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    fun logout() {
        WorkManager.getInstance(this).cancelUniqueWork("socializee_update_check")

        // Hapus FCM token dari backend agar tidak dapat notif setelah logout
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val repo = AppRepository(RetrofitClient.getApiService())
            lifecycleScope.launch {
                try { repo.removeDeviceToken(token) } catch (e: Exception) { }
                sessionManager.logout()
                startActivity(Intent(this@MainActivity, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }.addOnFailureListener {
            sessionManager.logout()
            startActivity(Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
        return  // return early, lanjut di callback
    }
}