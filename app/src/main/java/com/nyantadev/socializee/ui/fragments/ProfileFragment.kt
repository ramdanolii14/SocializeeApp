package com.nyantadev.socializee.ui.fragments

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.nyantadev.socializee.BuildConfig
import com.nyantadev.socializee.R
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.FragmentProfileBinding
import com.nyantadev.socializee.models.Post
import com.nyantadev.socializee.repository.AppRepository
import com.nyantadev.socializee.ui.AuthActivity
import com.nyantadev.socializee.ui.MainActivity
import com.nyantadev.socializee.ui.adapters.PostAdapter
import com.nyantadev.socializee.utils.SessionManager
import com.nyantadev.socializee.utils.UpdateChecker
import com.nyantadev.socializee.utils.UpdateNotificationHelper
import com.nyantadev.socializee.viewmodel.AuthViewModel
import com.nyantadev.socializee.viewmodel.FeedViewModel
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
    private lateinit var feedViewModel: FeedViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var postAdapter: PostAdapter

    private var targetUserId: String? = null
    private var isOwnProfile = false
    private var selectedAvatarFile: File? = null
    private var isTargetUserBanned = false

    // ── Avatar picker ─────────────────────────────────────────────────────
    private val avatarPickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            selectedAvatarFile = copyUriToTempFile(uri)
            if (selectedAvatarFile != null) {
                Glide.with(this).load(uri).circleCrop().into(binding.ivEditAvatar)
            } else {
                Toast.makeText(context, getString(R.string.failed_process_media), Toast.LENGTH_SHORT).show()
            }
        }

    private fun copyUriToTempFile(uri: Uri): File? = try {
        val ctx = requireContext()
        val mimeType = ctx.contentResolver.getType(uri)
        val ext = when (mimeType) {
            "image/png"  -> ".png"
            "image/webp" -> ".webp"
            "image/gif"  -> ".gif"
            else         -> ".jpg"
        }
        val tempFile = File(ctx.cacheDir, "avatar_${System.currentTimeMillis()}$ext")
        ctx.contentResolver.openInputStream(uri)?.use { i ->
            FileOutputStream(tempFile).use { o -> i.copyTo(o) }
        }
        tempFile
    } catch (e: Exception) { null }

    // ─────────────────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        val repo    = AppRepository(RetrofitClient.getApiService())
        val factory = ViewModelFactory(repo)

        profileViewModel = ViewModelProvider(requireActivity(), factory)[ProfileViewModel::class.java]
        authViewModel    = ViewModelProvider(this, factory)[AuthViewModel::class.java]
        feedViewModel    = ViewModelProvider(requireActivity(), factory)[FeedViewModel::class.java]

        targetUserId = arguments?.getString("userId") ?: sessionManager.getUserId()
        isOwnProfile = targetUserId == sessionManager.getUserId()

        setupUI()
        setupRecyclerView()
        observeViewModels()
        profileViewModel.loadProfile(targetUserId!!)
        checkForUpdate()
    }

    // ── Update checker ────────────────────────────────────────────────────

    private fun checkForUpdate() {
        viewLifecycleOwner.lifecycleScope.launch {
            val info = UpdateChecker.check(BuildConfig.VERSION_NAME) ?: return@launch
            UpdateNotificationHelper.showUpdateNotification(requireContext(), info)
            Snackbar.make(
                binding.root,
                "✨ Versi ${info.latestVersion} tersedia! Kamu masih pakai ${BuildConfig.VERSION_NAME}.",
                Snackbar.LENGTH_INDEFINITE
            ).setAction(getString(R.string.btn_download_now)) { openUrl(info.releaseUrl) }
                .setActionTextColor(requireContext().getColor(R.color.secondary))
                .show()
        }
    }

    // ── Popup menu (titik tiga) ───────────────────────────────────────────

    private fun showMoreOptionsMenu() {
        val anchor       = binding.ivMoreOptions
        val themedCtx    = ContextThemeWrapper(requireContext(), R.style.Theme_Socializee)
        val popup        = PopupMenu(themedCtx, anchor)

        if (isOwnProfile) {
            popup.menu.add(0, MENU_SETTINGS,     0, getString(R.string.menu_settings))
            popup.menu.add(0, MENU_ABOUT,        1, getString(R.string.menu_about))
            popup.menu.add(0, MENU_CHECK_UPDATE, 2, getString(R.string.menu_check_update))
            popup.menu.add(0, MENU_LOGOUT,       3, getString(R.string.menu_logout))
        } else {
            popup.menu.add(0, MENU_REPORT_USER,   0, getString(R.string.menu_report))
            popup.menu.add(0, MENU_BLOCK_USER,    1, getString(R.string.menu_block))
            popup.menu.add(0, MENU_COPY_LINK,     2, getString(R.string.menu_copy_link))
            popup.menu.add(0, MENU_SHARE_PROFILE, 3, getString(R.string.menu_share_profile))

            if (sessionManager.isAdmin()) {
                val banLabel  = if (isTargetUserBanned)
                    getString(R.string.menu_unban_user) else getString(R.string.menu_ban_user)
                val spannable = SpannableString(banLabel)
                spannable.setSpan(ForegroundColorSpan(Color.RED), 0, spannable.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                popup.menu.add(0, MENU_BAN_USER, 4, banLabel).title = spannable
            }
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SETTINGS     -> { showSettingsDialog() ; true }
                MENU_ABOUT        -> { showAboutDialog() ; true }
                MENU_CHECK_UPDATE -> { manualCheckUpdate() ; true }
                MENU_LOGOUT       -> { confirmLogout() ; true }
                MENU_REPORT_USER  -> { comingSoon(getString(R.string.menu_report)) ; true }
                MENU_BLOCK_USER   -> { comingSoon(getString(R.string.menu_block)) ; true }
                MENU_COPY_LINK    -> { copyProfileLink() ; true }
                MENU_SHARE_PROFILE -> { shareProfile() ; true }
                MENU_BAN_USER     -> { confirmBanOrUnban() ; true }
                else -> false
            }
        }
        popup.show()
    }

    // ── [REQ #3] Settings dialog ──────────────────────────────────────────

    private fun showSettingsDialog() {
        // Inflate custom view dengan Switch dark mode + tombol hapus akun
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_settings, null)

        val switchDark: MaterialSwitch = dialogView.findViewById(R.id.switchDarkMode)
        switchDark.isChecked = sessionManager.isDarkMode()

        switchDark.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.saveDarkMode(isChecked)
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        val btnDeleteAccount: View = dialogView.findViewById(R.id.btnDeleteAccount)
        btnDeleteAccount.setOnClickListener {
            Toast.makeText(
                requireContext(),
                getString(R.string.delete_account_coming_soon),
                Toast.LENGTH_LONG
            ).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.title_settings))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.btn_close), null)
            .show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.logout_confirm_title))
            .setMessage(getString(R.string.logout_confirm_message))
            .setPositiveButton(getString(R.string.btn_yes_logout)) { _, _ ->
                (activity as? MainActivity)?.logout()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    // ── Admin ban / unban ─────────────────────────────────────────────────

    private fun confirmBanOrUnban() {
        val username = binding.tvUsername.text.toString()
        if (isTargetUserBanned) {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.menu_unban_user))
                .setMessage("Hapus ban dari $username? User ini akan bisa login kembali.")
                .setPositiveButton(getString(R.string.menu_unban_user)) { _, _ -> unbanUser() }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.menu_ban_user))
                .setMessage("Yakin ban $username?\n\nUser ini tidak bisa login dan semua postingannya akan dihapus.")
                .setPositiveButton(getString(R.string.menu_ban_user)) { _, _ -> banUser() }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
    }

    private fun banUser() {
        val uid = targetUserId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = RetrofitClient.getApiService().banUser(uid)
                if (res.isSuccessful && res.body()?.success == true) {
                    isTargetUserBanned = true
                    Toast.makeText(context, res.body()?.message ?: "User berhasil dibanned!", Toast.LENGTH_LONG).show()
                    findNavController().navigateUp()
                } else {
                    val msg = res.errorBody()?.string()
                        ?.let { Regex("\"message\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
                        ?: "Gagal ban user."
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun unbanUser() {
        val uid = targetUserId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = RetrofitClient.getApiService().unbanUser(uid)
                if (res.isSuccessful && res.body()?.success == true) {
                    isTargetUserBanned = false
                    Toast.makeText(context, res.body()?.message ?: "User berhasil di-unban!", Toast.LENGTH_LONG).show()
                    profileViewModel.loadProfile(uid)
                } else {
                    val msg = res.errorBody()?.string()
                        ?.let { Regex("\"message\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
                        ?: "Gagal unban user."
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Misc helpers ──────────────────────────────────────────────────────

    private fun comingSoon(name: String) {
        Toast.makeText(context, "⏳ $name — ${getString(R.string.coming_soon)}", Toast.LENGTH_SHORT).show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.menu_about))
            .setMessage(
                "Versi: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\n" +
                        "Made with ❤️ by Ramdan.\n\n" +
                        "Kunjungi GitHub kami untuk kode sumber dan versi terbaru."
            )
            .setPositiveButton(getString(R.string.btn_open_github)) { _, _ ->
                openUrl(UpdateChecker.githubReleasesUrl)
            }
            .setNegativeButton(getString(R.string.btn_close), null)
            .show()
    }

    private fun manualCheckUpdate() {
        Toast.makeText(context, getString(R.string.checking_update), Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val info = UpdateChecker.check(BuildConfig.VERSION_NAME)
            if (info != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.update_available_title))
                    .setMessage(
                        "Versi Baru: ${info.latestVersion}\n" +
                                "Versimu: ${BuildConfig.VERSION_NAME}\n\n" +
                                if (info.releaseNotes.isNotBlank())
                                    "Yang baru:\n${info.releaseNotes.take(300)}" else ""
                    )
                    .setPositiveButton(getString(R.string.btn_download_now)) { _, _ -> openUrl(info.releaseUrl) }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            } else {
                Toast.makeText(context, getString(R.string.latest_version), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyProfileLink() {
        val username = binding.tvUsername.text.toString().removePrefix("@")
        val link = "https://nyanpixel.my.id/u/$username"
        val clip = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
        clip.setPrimaryClip(android.content.ClipData.newPlainText("Profile Link", link))
        Toast.makeText(context, getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
    }

    private fun shareProfile() {
        val username = binding.tvUsername.text.toString().removePrefix("@")
        val link = "https://nyanpixel.my.id/u/$username"
        startActivity(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Lihat profil @$username di Socializee: $link")
            }.let { Intent.createChooser(it, "Bagikan profil via") }
        )
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.err_open_browser), Toast.LENGTH_SHORT).show()
        }
    }

    // ── Setup UI ──────────────────────────────────────────────────────────

    private fun setupUI() {
        binding.btnFollow.visibility      = if (isOwnProfile) View.GONE else View.VISIBLE
        binding.btnEditProfile.visibility = if (isOwnProfile) View.VISIBLE else View.GONE
        binding.ivBack.visibility         = if (isOwnProfile) View.GONE else View.VISIBLE

        binding.ivMoreOptions.setOnClickListener { showMoreOptionsMenu() }
        binding.ivBack.setOnClickListener { findNavController().navigateUp() }
        binding.ivEditAvatar.setOnClickListener {
            if (isOwnProfile) avatarPickerLauncher.launch("image/*")
        }
        binding.btnFollow.setOnClickListener { profileViewModel.toggleFollow(targetUserId!!) }
        binding.btnEditProfile.setOnClickListener { toggleEditMode() }
        binding.btnSaveProfile.setOnClickListener {
            val name = binding.etDisplayName.text.toString().trim()
            val bio  = binding.etBio.text.toString().trim()
            if (name.isBlank()) {
                Toast.makeText(context, getString(R.string.err_name_empty), Toast.LENGTH_SHORT).show()
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
            binding.btnEditProfile.text = getString(R.string.btn_edit_profile)
            selectedAvatarFile = null
        } else {
            binding.editContainer.visibility = View.VISIBLE
            binding.btnEditProfile.text = getString(R.string.btn_cancel_edit)
        }
    }

    private fun setupRecyclerView() {
        postAdapter = PostAdapter(
            currentUserId = sessionManager.getUserId() ?: "",
            isAdmin       = sessionManager.isAdmin(),
            onLike        = { post, pos -> feedViewModel.toggleLike(post, pos) },
            onComment     = { post -> openComments(post) },
            onRepost      = { post -> feedViewModel.toggleRepost(post) },
            onUserClick   = { userId -> if (userId != targetUserId) openProfile(userId) },
            onDelete      = { post, _ -> confirmDelete(post) },
            onImageClick  = { urls, idx -> openImageViewer(urls, idx) }
        )
        binding.rvPosts.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
            isNestedScrollingEnabled = false
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────

    private fun observeViewModels() {
        profileViewModel.profileState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ProfileState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is ProfileState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.contentContainer.visibility = View.VISIBLE
                    val user = state.user

                    isTargetUserBanned = user.isBanned
                    binding.tvBannedBadge.visibility =
                        if (user.isBanned && sessionManager.isAdmin() && !isOwnProfile)
                            View.VISIBLE else View.GONE

                    binding.tvDisplayName.text = user.displayName.ifBlank { user.username }
                    binding.tvUsername.text    = "@${user.username}"
                    binding.tvBio.text         = user.bio
                    binding.tvBio.visibility   = if (user.bio.isBlank()) View.GONE else View.VISIBLE
                    binding.tvPostsCount.text  = user.postsCount.toString()
                    binding.tvFollowers.text   = user.followersCount.toString()
                    binding.tvFollowing.text   = user.followingCount.toString()

                    val av = user.avatarUrl
                    if (!av.isNullOrBlank()) {
                        Glide.with(this).load(av).placeholder(R.drawable.ic_default_avatar)
                            .circleCrop().diskCacheStrategy(DiskCacheStrategy.ALL).into(binding.ivAvatar)
                        Glide.with(this).load(av).placeholder(R.drawable.ic_default_avatar)
                            .circleCrop().diskCacheStrategy(DiskCacheStrategy.ALL).into(binding.ivEditAvatar)
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
                    binding.btnEditProfile.text = getString(R.string.btn_edit_profile)
                    profileViewModel.loadProfile(targetUserId!!)
                    Toast.makeText(context, "Profil berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                }
                is ProfileUpdateState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        feedViewModel.posts.observe(viewLifecycleOwner) { feedPosts ->
            val currentList = postAdapter.currentList
            if (currentList.isEmpty()) return@observe
            val updated = currentList.map { feedPosts.find { fp -> fp.id == it.id } ?: it }
            if (updated != currentList) postAdapter.submitList(updated)
        }

        feedViewModel.bannedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { handleBanned(it) }
        }
    }

    private fun handleBanned(message: String) {
        sessionManager.logout()
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        startActivity(Intent(requireContext(), AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun updateFollowButton(isFollowing: Boolean) {
        binding.btnFollow.isSelected = isFollowing
        if (isFollowing) {
            binding.btnFollow.text = getString(R.string.btn_following)
            binding.btnFollow.setBackgroundColor(requireContext().getColor(android.R.color.transparent))
            binding.btnFollow.setStrokeColorResource(R.color.text_secondary)
            binding.btnFollow.setTextColor(requireContext().getColorStateList(R.color.text_secondary))
            binding.btnFollow.strokeWidth = 2
        } else {
            binding.btnFollow.text = getString(R.string.btn_follow)
            binding.btnFollow.setBackgroundColor(requireContext().getColor(R.color.primary))
            binding.btnFollow.strokeWidth = 0
            binding.btnFollow.setTextColor(requireContext().getColorStateList(android.R.color.white))
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────

    private fun openComments(post: Post) {
        val bundle   = Bundle().apply { putString("postId", post.id) }
        val actionId = if (isOwnProfile) R.id.action_profileSelf_to_comments
        else R.id.action_profile_to_comments
        try { findNavController().navigate(actionId, bundle) }
        catch (e: Exception) {
            try {
                val fallback = if (isOwnProfile) R.id.action_profile_to_comments
                else R.id.action_profileSelf_to_comments
                findNavController().navigate(fallback, bundle)
            } catch (_: Exception) {
                Toast.makeText(context, "Tidak bisa membuka komentar.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openProfile(userId: String) {
        if (userId == targetUserId) return
        val bundle   = Bundle().apply { putString("userId", userId) }
        val actionId = if (isOwnProfile) R.id.action_profileSelf_to_profile
        else R.id.action_profile_to_profile
        try { findNavController().navigate(actionId, bundle) }
        catch (e: Exception) {
            Toast.makeText(context, "Tidak bisa membuka profil.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openImageViewer(urls: List<String>, startIndex: Int) {
        val bundle   = Bundle().apply {
            putStringArray("urls", urls.toTypedArray())
            putInt("startIndex", startIndex)
        }
        val actionId = if (isOwnProfile) R.id.action_profileSelf_to_imageViewer
        else R.id.action_profile_to_imageViewer
        try { findNavController().navigate(actionId, bundle) }
        catch (e: Exception) {
            Toast.makeText(context, "Tidak bisa membuka media.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(post: Post) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_delete_post)
            .setMessage(R.string.msg_delete_post)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                feedViewModel.deletePost(post.id)
                postAdapter.submitList(postAdapter.currentList.filter { it.id != post.id })
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MENU_SETTINGS     = 1
        private const val MENU_ABOUT        = 2
        private const val MENU_CHECK_UPDATE = 3
        private const val MENU_LOGOUT       = 4
        private const val MENU_REPORT_USER  = 5
        private const val MENU_BLOCK_USER   = 6
        private const val MENU_COPY_LINK    = 7
        private const val MENU_SHARE_PROFILE = 8
        private const val MENU_BAN_USER     = 9
    }
}