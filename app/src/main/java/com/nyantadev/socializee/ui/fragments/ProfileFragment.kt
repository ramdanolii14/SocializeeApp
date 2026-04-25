package com.nyantadev.socializee.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.snackbar.Snackbar
import com.nyantadev.socializee.BuildConfig
import com.nyantadev.socializee.R
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.FragmentProfileBinding
import com.nyantadev.socializee.models.Post
import com.nyantadev.socializee.repository.AppRepository
import com.nyantadev.socializee.ui.MainActivity
import com.nyantadev.socializee.ui.adapters.PostAdapter
import com.nyantadev.socializee.utils.SessionManager
import com.nyantadev.socializee.utils.UpdateChecker
import com.nyantadev.socializee.viewmodel.AuthViewModel
import com.nyantadev.socializee.viewmodel.ProfileState
import com.nyantadev.socializee.viewmodel.ProfileViewModel
import com.nyantadev.socializee.viewmodel.ProfileUpdateState
import com.nyantadev.socializee.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var authViewModel: AuthViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var postAdapter: PostAdapter

    private var targetUserId: String? = null
    private var isOwnProfile = false
    private var selectedAvatarFile: File? = null

    // ---- Avatar picker ----
    private val avatarPickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            selectedAvatarFile = copyUriToTempFile(uri)
            if (selectedAvatarFile != null) {
                Glide.with(this)
                    .load(uri)
                    .circleCrop()
                    .into(binding.ivEditAvatar)
            } else {
                Toast.makeText(context, "Gagal memproses gambar", Toast.LENGTH_SHORT).show()
            }
        }

    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val context = requireContext()
            val mimeType = context.contentResolver.getType(uri)
            val ext = when (mimeType) {
                "image/png"  -> ".png"
                "image/webp" -> ".webp"
                "image/gif"  -> ".gif"
                else         -> ".jpg"
            }
            val tempFile = File(context.cacheDir, "avatar_${System.currentTimeMillis()}$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        val repo = AppRepository(RetrofitClient.getApiService())
        val factory = ViewModelFactory(repo)

        profileViewModel = ViewModelProvider(this, factory)[ProfileViewModel::class.java]
        authViewModel = ViewModelProvider(this, factory)[AuthViewModel::class.java]

        targetUserId = arguments?.getString("userId") ?: sessionManager.getUserId()
        isOwnProfile = targetUserId == sessionManager.getUserId()

        setupUI()
        setupRecyclerView()
        observeViewModels()

        profileViewModel.loadProfile(targetUserId!!)
        checkForUpdate()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Update Checker
    // ────────────────────────────────────────────────────────────────────────

    private fun checkForUpdate() {
        viewLifecycleOwner.lifecycleScope.launch {
            val info = UpdateChecker.check(BuildConfig.VERSION_NAME) ?: return@launch
            val snackbar = Snackbar.make(
                binding.root,
                "✨ Versi ${info.latestVersion} tersedia! Kamu masih pakai ${BuildConfig.VERSION_NAME}.",
                Snackbar.LENGTH_INDEFINITE
            )
            snackbar.setAction("Update") { openUrl(info.releaseUrl) }
            snackbar.setActionTextColor(requireContext().getColor(R.color.primary))
            snackbar.show()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Popup Menu (titik tiga)
    // ────────────────────────────────────────────────────────────────────────

    private fun showMoreOptionsMenu() {
        val anchor = binding.ivMoreOptions

        // ContextThemeWrapper memaksa PopupMenu pakai tema app (DayNight),
        // sehingga background & teks otomatis ikut light / dark mode.
        val themedContext = ContextThemeWrapper(requireContext(), R.style.Theme_Socializee)
        val popup = PopupMenu(themedContext, anchor)

        if (isOwnProfile) {
            popup.menu.add(0, MENU_SETTINGS,      0, "⚙Pengaturan")
            popup.menu.add(0, MENU_PRIVACY,       1, "Privasi & Keamanan")
            popup.menu.add(0, MENU_NOTIFICATIONS, 2, "Notifikasi")
            popup.menu.add(0, MENU_THEME,         3, "Tema")
            popup.menu.add(0, MENU_HELP,          4, "Bantuan & Dukungan")
            popup.menu.add(0, MENU_ABOUT,         5, "ℹTentang Aplikasi")
            popup.menu.add(0, MENU_CHECK_UPDATE,  6, "Cek Pembaruan")
            popup.menu.add(0, MENU_LOGOUT,        7, "Keluar")
        } else {
            popup.menu.add(0, MENU_REPORT_USER,   0, "🚩  Laporkan Pengguna")
            popup.menu.add(0, MENU_BLOCK_USER,    1, "🚫  Blokir Pengguna")
            popup.menu.add(0, MENU_COPY_LINK,     2, "🔗  Salin Tautan Profil")
            popup.menu.add(0, MENU_SHARE_PROFILE, 3, "↗️  Bagikan Profil")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SETTINGS      -> { comingSoon("Pengaturan") ; true }
                MENU_PRIVACY       -> { comingSoon("Privasi & Keamanan") ; true }
                MENU_NOTIFICATIONS -> { comingSoon("Notifikasi") ; true }
                MENU_THEME         -> { comingSoon("Tema") ; true }
                MENU_HELP          -> { comingSoon("Bantuan & Dukungan") ; true }
                MENU_ABOUT         -> { showAboutDialog() ; true }
                MENU_CHECK_UPDATE  -> { manualCheckUpdate() ; true }
                MENU_LOGOUT        -> { (activity as? MainActivity)?.logout() ; true }
                MENU_REPORT_USER   -> { comingSoon("Laporkan Pengguna") ; true }
                MENU_BLOCK_USER    -> { comingSoon("Blokir Pengguna") ; true }
                MENU_COPY_LINK     -> { copyProfileLink() ; true }
                MENU_SHARE_PROFILE -> { shareProfile() ; true }
                else -> false
            }
        }

        popup.show()
    }

    private fun comingSoon(featureName: String) {
        Toast.makeText(context, "⏳ $featureName — Coming Soon!", Toast.LENGTH_SHORT).show()
    }

    private fun showAboutDialog() {
        val version = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("About Socializee")
            .setMessage(
                "Versi: $version ($versionCode)\n\n" +
                        "Made with ❤️ by Ramdan.\n\n" +
                        "Kunjungi GitHub kami untuk melihat source code dan pembaruan terbaru."
            )
            .setPositiveButton("Buka GitHub") { _, _ -> openUrl(UpdateChecker.githubReleasesUrl) }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun manualCheckUpdate() {
        Toast.makeText(context, "Memeriksa pembaruan...", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val info = UpdateChecker.check(BuildConfig.VERSION_NAME)
            if (info != null) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("🎉 Pembaruan Tersedia!")
                    .setMessage(
                        "Versi terbaru: ${info.latestVersion}\n" +
                                "Versi kamu: ${BuildConfig.VERSION_NAME}\n\n" +
                                if (info.releaseNotes.isNotBlank()) "Apa yang baru:\n${info.releaseNotes.take(300)}" else ""
                    )
                    .setPositiveButton("Download Sekarang") { _, _ -> openUrl(info.releaseUrl) }
                    .setNegativeButton("Nanti Saja", null)
                    .show()
            } else {
                Toast.makeText(context, "Aplikasi sudah versi terbaru!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyProfileLink() {
        val username = binding.tvUsername.text.toString().removePrefix("@")
        val link = "https://socializee.app/u/$username"
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Profile Link", link))
        Toast.makeText(context, "🔗 Tautan profil disalin!", Toast.LENGTH_SHORT).show()
    }

    private fun shareProfile() {
        val username = binding.tvUsername.text.toString().removePrefix("@")
        val link = "https://socializee.app/u/$username"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Lihat profil @$username di Socializee: $link")
        }
        startActivity(Intent.createChooser(intent, "Bagikan profil via"))
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, "Tidak bisa membuka browser", Toast.LENGTH_SHORT).show()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Setup & UI
    // ────────────────────────────────────────────────────────────────────────

    private fun setupUI() {
        binding.btnFollow.visibility      = if (isOwnProfile) View.GONE else View.VISIBLE
        binding.btnEditProfile.visibility = if (isOwnProfile) View.VISIBLE else View.GONE
        binding.ivBack.visibility         = if (isOwnProfile) View.GONE else View.VISIBLE

        binding.ivMoreOptions.setOnClickListener { showMoreOptionsMenu() }
        binding.ivBack.setOnClickListener { findNavController().navigateUp() }

        binding.ivEditAvatar.setOnClickListener {
            if (isOwnProfile) avatarPickerLauncher.launch("image/*")
        }

        binding.btnFollow.setOnClickListener {
            profileViewModel.toggleFollow(targetUserId!!)
        }

        binding.btnEditProfile.setOnClickListener { toggleEditMode() }

        binding.btnSaveProfile.setOnClickListener {
            val name = binding.etDisplayName.text.toString().trim()
            val bio  = binding.etBio.text.toString().trim()
            if (name.isBlank()) {
                Toast.makeText(context, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            authViewModel.updateProfile(name, bio, selectedAvatarFile)
        }

        binding.layoutFollowers.setOnClickListener {
            showFollowList(FollowListBottomSheet.MODE_FOLLOWERS)
        }
        binding.layoutFollowing.setOnClickListener {
            showFollowList(FollowListBottomSheet.MODE_FOLLOWING)
        }
    }

    private fun showFollowList(mode: String) {
        val uid = targetUserId ?: return
        FollowListBottomSheet.newInstance(uid, mode)
            .show(parentFragmentManager, "follow_list")
    }

    private fun toggleEditMode() {
        val isEditing = binding.editContainer.visibility == View.VISIBLE
        if (isEditing) {
            binding.editContainer.visibility = View.GONE
            binding.btnEditProfile.text = "Edit Profil"
            selectedAvatarFile = null
        } else {
            binding.editContainer.visibility = View.VISIBLE
            binding.btnEditProfile.text = "Batal"
        }
    }

    private fun setupRecyclerView() {
        postAdapter = PostAdapter(
            currentUserId = sessionManager.getUserId() ?: "",
            onLike        = { _, _ -> },
            onComment     = { post -> openComments(post) },
            onUserClick   = { },
            onDelete      = { _, _ -> },
            onImageClick  = { urls, idx -> openImageViewer(urls, idx) }
        )
        binding.rvPosts.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
            isNestedScrollingEnabled = false
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Observers
    // ────────────────────────────────────────────────────────────────────────

    private fun observeViewModels() {
        profileViewModel.profileState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ProfileState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is ProfileState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.contentContainer.visibility = View.VISIBLE

                    val user = state.user
                    binding.tvDisplayName.text = user.displayName.ifBlank { user.username }
                    binding.tvUsername.text    = "@${user.username}"
                    binding.tvBio.text         = user.bio
                    binding.tvBio.visibility   = if (user.bio.isBlank()) View.GONE else View.VISIBLE

                    binding.tvPostsCount.text = user.postsCount.toString()
                    binding.tvFollowers.text  = user.followersCount.toString()
                    binding.tvFollowing.text  = user.followingCount.toString()

                    val avatarUrl = user.avatarUrl
                    if (!avatarUrl.isNullOrBlank()) {
                        Glide.with(this@ProfileFragment)
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_default_avatar)
                            .error(R.drawable.ic_default_avatar)
                            .circleCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.ivAvatar)

                        Glide.with(this@ProfileFragment)
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_default_avatar)
                            .circleCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.ivEditAvatar)
                    } else {
                        binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
                        binding.ivEditAvatar.setImageResource(R.drawable.ic_default_avatar)
                    }

                    binding.etDisplayName.setText(user.displayName)
                    binding.etBio.setText(user.bio)

                    updateFollowButton(user.isFollowing)
                    postAdapter.submitList(state.posts)
                }
                is ProfileState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        profileViewModel.followState.observe(viewLifecycleOwner) { (following, count) ->
            updateFollowButton(following)
            binding.tvFollowers.text = count.toString()
        }

        authViewModel.profileState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ProfileUpdateState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is ProfileUpdateState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    selectedAvatarFile = null
                    sessionManager.saveUser(state.user)
                    binding.editContainer.visibility = View.GONE
                    binding.btnEditProfile.text = "Edit Profil"
                    profileViewModel.loadProfile(targetUserId!!)
                    Toast.makeText(context, "Profil diperbarui!", Toast.LENGTH_SHORT).show()
                }
                is ProfileUpdateState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateFollowButton(isFollowing: Boolean) {
        binding.btnFollow.isSelected = isFollowing
        if (isFollowing) {
            binding.btnFollow.text = "Diikuti"
            binding.btnFollow.setBackgroundColor(requireContext().getColor(android.R.color.transparent))
            binding.btnFollow.setStrokeColorResource(R.color.text_secondary)
            binding.btnFollow.setTextColor(requireContext().getColorStateList(R.color.text_secondary))
            binding.btnFollow.strokeWidth = 2
        } else {
            binding.btnFollow.text = "Ikuti"
            binding.btnFollow.setBackgroundColor(requireContext().getColor(R.color.primary))
            binding.btnFollow.strokeWidth = 0
            binding.btnFollow.setTextColor(requireContext().getColorStateList(android.R.color.white))
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Navigation helpers
    // ────────────────────────────────────────────────────────────────────────

    private fun openComments(post: Post) {
        val bundle = Bundle().apply { putString("postId", post.id) }
        val actionId = if (isOwnProfile) R.id.action_profileSelf_to_comments
        else R.id.action_profile_to_comments
        try {
            findNavController().navigate(actionId, bundle)
        } catch (e: IllegalArgumentException) {
            try {
                val fallback = if (isOwnProfile) R.id.action_profile_to_comments
                else R.id.action_profileSelf_to_comments
                findNavController().navigate(fallback, bundle)
            } catch (e2: Exception) {
                Toast.makeText(context, "Tidak bisa membuka komentar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openImageViewer(urls: List<String>, startIndex: Int) {
        val bundle = Bundle().apply {
            putStringArray("urls", urls.toTypedArray())
            putInt("startIndex", startIndex)
        }
        val actionId = if (isOwnProfile) R.id.action_profileSelf_to_imageViewer
        else R.id.action_profile_to_imageViewer
        try {
            findNavController().navigate(actionId, bundle)
        } catch (e: IllegalArgumentException) {
            try {
                val fallback = if (isOwnProfile) R.id.action_profile_to_imageViewer
                else R.id.action_profileSelf_to_imageViewer
                findNavController().navigate(fallback, bundle)
            } catch (e2: Exception) {
                Toast.makeText(context, "Tidak bisa membuka gambar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ────────────────────────────────────────────────────────────────────────
    // Menu item ID constants
    // ────────────────────────────────────────────────────────────────────────

    companion object {
        private const val MENU_SETTINGS      = 1
        private const val MENU_PRIVACY       = 2
        private const val MENU_NOTIFICATIONS = 3
        private const val MENU_THEME         = 4
        private const val MENU_HELP          = 5
        private const val MENU_ABOUT         = 6
        private const val MENU_CHECK_UPDATE  = 7
        private const val MENU_LOGOUT        = 8
        private const val MENU_REPORT_USER   = 9
        private const val MENU_BLOCK_USER    = 10
        private const val MENU_COPY_LINK     = 11
        private const val MENU_SHARE_PROFILE = 12
    }
}