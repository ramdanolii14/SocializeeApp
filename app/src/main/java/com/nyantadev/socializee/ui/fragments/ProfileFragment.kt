package com.nyantadev.socializee.ui.fragments

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import com.github.dhaval2404.imagepicker.ImagePicker
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

    private val avatarPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            // Copy URI ke file sementara dengan ekstensi yang benar dari MIME type
            selectedAvatarFile = copyUriToTempFile(uri)
            Glide.with(this).load(uri).circleCrop().into(binding.ivAvatar)
        }
    }

    /**
     * Copy URI ke file sementara.
     * Ekstensi file ditentukan dari MIME type via ContentResolver agar
     * backend multer bisa mengenali tipe file dengan benar.
     */
    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val context = requireContext()

            // Deteksi MIME type dari ContentResolver (lebih akurat daripada nama file)
            val mimeType = context.contentResolver.getType(uri)
            val ext = when (mimeType) {
                "image/jpeg" -> ".jpg"
                "image/jpg"  -> ".jpg"
                "image/png"  -> ".png"
                "image/gif"  -> ".gif"
                "image/webp" -> ".webp"
                "image/bmp"  -> ".bmp"
                else -> {
                    // Fallback: ambil dari nama file asli
                    val fileName = getFileName(context, uri)
                    if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ".jpg"
                }
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

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "file.jpg"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) {
                name = cursor.getString(idx)
            }
        }
        return name
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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
        binding.btnLogout.visibility = if (isOwnProfile) View.VISIBLE else View.GONE
        binding.ivBack.visibility = if (isOwnProfile) View.GONE else View.VISIBLE

        binding.ivBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnLogout.setOnClickListener { (activity as? MainActivity)?.logout() }

        binding.ivAvatar.setOnClickListener {
            if (isOwnProfile) pickAvatar()
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
        } else {
            binding.editContainer.visibility = View.VISIBLE
            binding.btnEditProfile.text = "Batal"
        }
    }

    private fun pickAvatar() {
        ImagePicker.with(this)
            .cropSquare()
            .compress(512)
            .maxResultSize(512, 512)
            .createIntent { avatarPickerLauncher.launch(it) }
    }

    private fun setupRecyclerView() {
        postAdapter = PostAdapter(
            currentUserId = sessionManager.getUserId() ?: "",
            onLike = { post, pos -> },
            onComment = { post -> openComments(post) },
            onUserClick = { },
            onDelete = { post, _ -> },
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
                    binding.tvFollowers.text = "${user.followersCount} Pengikut"
                    binding.tvFollowing.text = "${user.followingCount} Mengikuti"
                    binding.tvPostsCount.text = "${user.postsCount} Post"

                    Glide.with(this)
                        .load(user.avatarUrl.ifBlank { null })
                        .placeholder(R.drawable.ic_default_avatar)
                        .circleCrop()
                        .into(binding.ivAvatar)

                    binding.etDisplayName.setText(user.displayName)
                    binding.etBio.setText(user.bio)

                    binding.btnFollow.text = if (user.isFollowing) "Diikuti" else "Ikuti"
                    binding.btnFollow.isSelected = user.isFollowing

                    postAdapter.submitList(state.posts)
                    binding.tvNoPosts.visibility = if (state.posts.isEmpty()) View.VISIBLE else View.GONE
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
            binding.tvFollowers.text = "$count Pengikut"
        }

        authViewModel.profileState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ProfileUpdateState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is ProfileUpdateState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    sessionManager.saveUser(state.user)
                    toggleEditMode()
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
        findNavController().navigate(R.id.action_profile_to_comments, bundle)
    }

    private fun openImageViewer(urls: List<String>, startIndex: Int) {
        val bundle = Bundle().apply {
            putStringArrayList("urls", ArrayList(urls))
            putInt("startIndex", startIndex)
        }
        findNavController().navigate(R.id.action_profile_to_imageViewer, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}