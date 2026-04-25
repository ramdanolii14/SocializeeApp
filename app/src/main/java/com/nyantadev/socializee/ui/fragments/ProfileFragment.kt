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
import androidx.appcompat.app.AlertDialog
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
                Toast.makeText(context, "Failed to process media.", Toast.LENGTH_SHORT).show()
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

    // ────────────────────────────────────────────────────────────────────────
    // Update Checker
    // ────────────────────────────────────────────────────────────────────────

    private fun checkForUpdate() {
        viewLifecycleOwner.lifecycleScope.launch {
            val info = UpdateChecker.check(BuildConfig.VERSION_NAME) ?: return@launch

            UpdateNotificationHelper.showUpdateNotification(requireContext(), info)

            val snackbar = Snackbar.make(
                binding.root,
                "✨ Versi ${info.latestVersion} Available! You still using ${BuildConfig.VERSION_NAME}.",
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
        val themedContext = ContextThemeWrapper(requireContext(), R.style.Theme_Socializee)
        val popup = PopupMenu(themedContext, anchor)

        if (isOwnProfile) {
            popup.menu.add(0, MENU_SETTINGS,      0, "Settings")
            popup.menu.add(0, MENU_PRIVACY,       1, "Privacy & Security")
            popup.menu.add(0, MENU_NOTIFICATIONS, 2, "Notification")
            popup.menu.add(0, MENU_THEME,         3, "Theme")
            popup.menu.add(0, MENU_HELP,          4, "Help & Support")
            popup.menu.add(0, MENU_ABOUT,         5, "About App")
            popup.menu.add(0, MENU_CHECK_UPDATE,  6, "Check Update")
            popup.menu.add(0, MENU_LOGOUT,        7, "Log Out")
        } else {
            popup.menu.add(0, MENU_REPORT_USER,   0, "Report")
            popup.menu.add(0, MENU_BLOCK_USER,    1, "Block User")
            popup.menu.add(0, MENU_COPY_LINK,     2, "Copy Profile Link")
            popup.menu.add(0, MENU_SHARE_PROFILE, 3, "Share Profile")

            // [NEW] Menu ban khusus admin
            if (sessionManager.isAdmin()) {
                popup.menu.add(0, MENU_BAN_USER, 4, "Ban User")
            }
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SETTINGS      -> { comingSoon("Settings") ; true }
                MENU_PRIVACY       -> { comingSoon("Privacy & Security") ; true }
                MENU_NOTIFICATIONS -> { comingSoon("Notification") ; true }
                MENU_THEME         -> { comingSoon("Theme") ; true }
                MENU_HELP          -> { comingSoon("Help & Support") ; true }
                MENU_ABOUT         -> { showAboutDialog() ; true }
                MENU_CHECK_UPDATE  -> { manualCheckUpdate() ; true }
                MENU_LOGOUT        -> { (activity as? MainActivity)?.logout() ; true }
                MENU_REPORT_USER   -> { comingSoon("Report User") ; true }
                MENU_BLOCK_USER    -> { comingSoon("Block User") ; true }
                MENU_COPY_LINK     -> { copyProfileLink() ; true }
                MENU_SHARE_PROFILE -> { shareProfile() ; true }
                MENU_BAN_USER      -> { confirmBanUser() ; true }  // [NEW]
                else -> false
            }
        }

        popup.show()
    }

    // [NEW] Konfirmasi ban user
    private fun confirmBanUser() {
        val username = binding.tvUsername.text.toString()
        AlertDialog.Builder(requireContext())
            .setTitle("Ban User")
            .setMessage("Are you sure to ban $username?\n\nThis user can't login and will be automatically logout.")
            .setPositiveButton("Ban") { _, _ ->
                banUser()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // [NEW] Eksekusi ban via ViewModel
    private fun banUser() {
        val uid = targetUserId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = RetrofitClient.getApiService().banUser(uid)
                if (res.isSuccessful && res.body()?.success == true) {
                    Toast.makeText(
                        context,
                        res.body()?.message ?: "User successfully banned!",
                        Toast.LENGTH_LONG
                    ).show()
                    // Kembali ke halaman sebelumnya
                    findNavController().navigateUp()
                } else {
                    val errMsg = res.errorBody()?.string()
                        ?.let { Regex("\"message\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
                        ?: "Failed to ban user."
                    Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun comingSoon(featureName: String) {
        Toast.makeText(context, "⏳ $featureName — Coming Soon!", Toast.LENGTH_SHORT).show()
    }

    private fun showAboutDialog() {
        val version = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        AlertDialog.Builder(requireContext())
            .setTitle("About Socializee")
            .setMessage(
                "Versi: $version ($versionCode)\n\n" +
                        "Made with ❤️ by Ramdan.\n\n" +
                        "Visit Our GitHub to see the source code and our latest version."
            )
            .setPositiveButton("Open Github") { _, _ -> openUrl(UpdateChecker.githubReleasesUrl) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun manualCheckUpdate() {
        Toast.makeText(context, "Checking for update...", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val info = UpdateChecker.check(BuildConfig.VERSION_NAME)
            if (info != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle("🎉 Update Available!")
                    .setMessage(
                        "New Version: ${info.latestVersion}\n" +
                                "Your Version: ${BuildConfig.VERSION_NAME}\n\n" +
                                if (info.releaseNotes.isNotBlank()) "What's new:\n${info.releaseNotes.take(300)}" else ""
                    )
                    .setPositiveButton("Download Now") { _, _ -> openUrl(info.releaseUrl) }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(context, "You using the latest version!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyProfileLink() {
        val username = binding.tvUsername.text.toString().removePrefix("@")
        val link = "https://nyanpixel.my.id/u/$username"
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Profile Link", link))
        Toast.makeText(context, "🔗 Tautan profil disalin!", Toast.LENGTH_SHORT).show()
    }

    private fun shareProfile() {
        val username = binding.tvUsername.text.toString().removePrefix("@")
        val link = "https://nyanpixel.my.id/u/$username"
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
            Toast.makeText(context, "Error while trying opening browser.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Name can't be empty!", Toast.LENGTH_SHORT).show()
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
            binding.btnEditProfile.text = "Edit Profile"
            selectedAvatarFile = null
        } else {
            binding.editContainer.visibility = View.VISIBLE
            binding.btnEditProfile.text = "Cancel"
        }
    }

    private fun setupRecyclerView() {
        postAdapter = PostAdapter(
            currentUserId = sessionManager.getUserId() ?: "",
            isAdmin = sessionManager.isAdmin(),          // [NEW]
            onLike       = { post, pos -> feedViewModel.toggleLike(post, pos) },
            onComment    = { post -> openComments(post) },
            onRepost     = { post -> feedViewModel.toggleRepost(post) },
            onUserClick  = { userId ->
                if (userId != targetUserId) openProfile(userId)
            },
            onDelete     = { post, _ -> confirmDelete(post) },
            onImageClick = { urls, idx -> openImageViewer(urls, idx) }
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
                    binding.btnEditProfile.text = "Edit Profile"
                    profileViewModel.loadProfile(targetUserId!!)
                    Toast.makeText(context, "Profile changed!", Toast.LENGTH_SHORT).show()
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
            val updated = currentList.map { profilePost ->
                feedPosts.find { it.id == profilePost.id } ?: profilePost
            }
            if (updated != currentList) postAdapter.submitList(updated)
        }

        // [NEW] Observe banned event dari FeedViewModel
        feedViewModel.bannedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                handleBanned(message)
            }
        }
    }

    // [NEW] Paksa logout saat kena ban (misal admin ban dirinya sendiri — edge case)
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
            binding.btnFollow.text = "Following"
            binding.btnFollow.setBackgroundColor(requireContext().getColor(android.R.color.transparent))
            binding.btnFollow.setStrokeColorResource(R.color.text_secondary)
            binding.btnFollow.setTextColor(requireContext().getColorStateList(R.color.text_secondary))
            binding.btnFollow.strokeWidth = 2
        } else {
            binding.btnFollow.text = "Follow"
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
                Toast.makeText(context, "Can't open the comments.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openProfile(userId: String) {
        if (userId == targetUserId) return
        val bundle = Bundle().apply { putString("userId", userId) }
        val actionId = if (isOwnProfile) R.id.action_profileSelf_to_profile
        else R.id.action_profile_to_profile
        try {
            findNavController().navigate(actionId, bundle)
        } catch (e: Exception) {
            Toast.makeText(context, "Can't open the profile.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Can't open the media.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(post: Post) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Post")
            .setMessage("Are you sure to delete this post?")
            .setPositiveButton("Delete") { _, _ ->
                feedViewModel.deletePost(post.id)
                val updated = postAdapter.currentList.filter { it.id != post.id }
                postAdapter.submitList(updated)
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        private const val MENU_BAN_USER      = 13  // [NEW]
    }
}