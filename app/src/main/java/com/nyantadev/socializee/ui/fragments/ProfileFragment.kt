package com.nyantadev.socializee.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.nyantadev.socializee.R
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.FragmentProfileBinding
import com.nyantadev.socializee.models.Post
import com.nyantadev.socializee.repository.AppRepository
import com.nyantadev.socializee.ui.MainActivity
import com.nyantadev.socializee.ui.adapters.PostAdapter
import com.nyantadev.socializee.utils.SessionManager
import com.nyantadev.socializee.viewmodel.AuthViewModel
import com.nyantadev.socializee.viewmodel.ProfileState
import com.nyantadev.socializee.viewmodel.ProfileViewModel
import com.nyantadev.socializee.viewmodel.ProfileUpdateState
import com.nyantadev.socializee.viewmodel.ViewModelFactory
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
                // Tampilkan gambar yang dipilih di ImageView Edit (ivEditAvatar)
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
    }

    private fun setupUI() {
        binding.btnFollow.visibility = if (isOwnProfile) View.GONE else View.VISIBLE
        binding.btnEditProfile.visibility = if (isOwnProfile) View.VISIBLE else View.GONE
        binding.ivBack.visibility = if (isOwnProfile) View.GONE else View.VISIBLE

        // Fix: Menggunakan ivLogoutIcon sesuai XML terbaru
        binding.ivLogoutIcon.visibility = if (isOwnProfile) View.VISIBLE else View.GONE

        binding.ivBack.setOnClickListener { findNavController().navigateUp() }

        // Fix: Menggunakan ivLogoutIcon
        binding.ivLogoutIcon.setOnClickListener { (activity as? MainActivity)?.logout() }

        // Fix: Listener pindah ke ivEditAvatar (didalam editContainer)
        binding.ivEditAvatar.setOnClickListener {
            if (isOwnProfile) {
                avatarPickerLauncher.launch("image/*")
            }
        }

        binding.btnFollow.setOnClickListener {
            profileViewModel.toggleFollow(targetUserId!!)
        }

        binding.btnEditProfile.setOnClickListener {
            toggleEditMode()
        }

        binding.btnSaveProfile.setOnClickListener {
            val name = binding.etDisplayName.text.toString().trim()
            val bio = binding.etBio.text.toString().trim()

            if (name.isBlank()) {
                Toast.makeText(context, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            authViewModel.updateProfile(name, bio, selectedAvatarFile)
        }
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
            onLike = { _, _ -> },
            onComment = { post -> openComments(post) },
            onUserClick = { },
            onDelete = { _, _ -> },
            onImageClick = { urls, idx -> openImageViewer(urls, idx) }
        )
        binding.rvPosts.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeViewModels() {
        profileViewModel.profileState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ProfileState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is ProfileState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.contentContainer.visibility = View.VISIBLE

                    val user = state.user
                    binding.tvDisplayName.text = user.displayName.ifBlank { user.username }
                    binding.tvUsername.text = "@${user.username}"
                    binding.tvBio.text = user.bio
                    binding.tvBio.visibility = if (user.bio.isBlank()) View.GONE else View.VISIBLE

                    // Update: Hanya isi angka karena label sudah ada di XML
                    binding.tvPostsCount.text = user.postsCount.toString()
                    binding.tvFollowers.text = user.followersCount.toString()
                    binding.tvFollowing.text = user.followingCount.toString()

                    val avatarUrl = user.avatarUrl
                    if (!avatarUrl.isNullOrBlank()) {
                        // Load ke avatar utama
                        Glide.with(this@ProfileFragment)
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_default_avatar)
                            .error(R.drawable.ic_default_avatar)
                            .circleCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.ivAvatar)

                        // Load ke avatar yang ada di form edit
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

                    binding.btnFollow.text = if (user.isFollowing) "Diikuti" else "Ikuti"
                    binding.btnFollow.isSelected = user.isFollowing

                    postAdapter.submitList(state.posts)

                    // Fix: tvNoPosts dihapus karena tidak ada di XML terbaru
                }
                is ProfileState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        profileViewModel.followState.observe(viewLifecycleOwner) { (following, count) ->
            binding.btnFollow.text = if (following) "Diikuti" else "Ikuti"
            binding.btnFollow.isSelected = following
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

    private fun openComments(post: Post) {
        val bundle = Bundle().apply { putString("postId", post.id) }
        val actionId = if (isOwnProfile) {
            R.id.action_profileSelf_to_comments
        } else {
            R.id.action_profile_to_comments
        }
        try {
            findNavController().navigate(actionId, bundle)
        } catch (e: IllegalArgumentException) {
            try {
                val fallback = if (isOwnProfile)
                    R.id.action_profile_to_comments
                else
                    R.id.action_profileSelf_to_comments
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
        val actionId = if (isOwnProfile) {
            R.id.action_profileSelf_to_imageViewer
        } else {
            R.id.action_profile_to_imageViewer
        }
        try {
            findNavController().navigate(actionId, bundle)
        } catch (e: IllegalArgumentException) {
            try {
                val fallback = if (isOwnProfile)
                    R.id.action_profile_to_imageViewer
                else
                    R.id.action_profileSelf_to_imageViewer
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
}