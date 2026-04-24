package com.nyantadev.socializee.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.FragmentCreatePostBinding
import com.nyantadev.socializee.repository.AppRepository
import com.nyantadev.socializee.utils.SessionManager
import com.nyantadev.socializee.viewmodel.CreatePostState
import com.nyantadev.socializee.viewmodel.FeedViewModel
import com.nyantadev.socializee.viewmodel.ViewModelFactory
import java.io.File
import java.io.FileOutputStream
import com.nyantadev.socializee.R

class CreatePostFragment : Fragment() {

    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: FeedViewModel
    private lateinit var sessionManager: SessionManager

    private val selectedMediaFiles = mutableListOf<File>()
    private val selectedMediaUris = mutableListOf<Uri>()
    private val selectedMediaTypes = mutableListOf<String>() // "image" or "video"

    companion object {
        private const val MAX_MEDIA = 4
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024L // 50MB
    }

    // ---- Photo Picker API (Android 13+) with multiple selection ----
    private val mediaPickerLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_MEDIA)) { uris ->
            if (uris.isEmpty()) return@registerForActivityResult
            val remaining = MAX_MEDIA - selectedMediaFiles.size
            if (remaining <= 0) {
                Toast.makeText(context, "Maksimal $MAX_MEDIA media", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            uris.take(remaining).forEach { uri ->
                val mimeType = requireContext().contentResolver.getType(uri) ?: ""
                val type = if (mimeType.startsWith("video")) "video" else "image"
                val fileSize = getFileSize(uri)
                if (fileSize > MAX_FILE_SIZE) {
                    Toast.makeText(
                        context,
                        "File terlalu besar (maks 50MB): $uri",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@forEach
                }
                val file = copyUriToTempFile(uri, mimeType) ?: run {
                    Toast.makeText(context, "Gagal memproses file", Toast.LENGTH_SHORT).show()
                    return@forEach
                }
                selectedMediaFiles.add(file)
                selectedMediaUris.add(uri)
                selectedMediaTypes.add(type)
            }
            updateImagePreviews()
        }

    // Fallback launcher for older Android (< 13): system file picker
    private val legacyMediaPickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isEmpty()) return@registerForActivityResult
            val remaining = MAX_MEDIA - selectedMediaFiles.size
            uris.take(remaining).forEach { uri ->
                val mimeType = requireContext().contentResolver.getType(uri) ?: ""
                val type = if (mimeType.startsWith("video")) "video" else "image"
                val fileSize = getFileSize(uri)
                if (fileSize > MAX_FILE_SIZE) {
                    Toast.makeText(context, "File terlalu besar (maks 50MB)", Toast.LENGTH_SHORT).show()
                    return@forEach
                }
                val file = copyUriToTempFile(uri, mimeType) ?: run {
                    Toast.makeText(context, "Gagal memproses file", Toast.LENGTH_SHORT).show()
                    return@forEach
                }
                selectedMediaFiles.add(file)
                selectedMediaUris.add(uri)
                selectedMediaTypes.add(type)
            }
            updateImagePreviews()
        }

    private fun getFileSize(uri: Uri): Long {
        return try {
            requireContext().contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun copyUriToTempFile(uri: Uri, mimeType: String): File? {
        return try {
            val context = requireContext()
            val ext = when {
                mimeType == "image/jpeg" || mimeType == "image/jpg" -> ".jpg"
                mimeType == "image/png"  -> ".png"
                mimeType == "image/gif"  -> ".gif"
                mimeType == "image/webp" -> ".webp"
                mimeType == "video/mp4"  -> ".mp4"
                mimeType == "video/quicktime" -> ".mov"
                mimeType == "video/x-msvideo" -> ".avi"
                mimeType == "video/x-matroska" -> ".mkv"
                mimeType == "video/3gpp" -> ".3gp"
                mimeType.startsWith("video") -> ".mp4"
                else -> ".jpg"
            }
            val tempFile = File(context.cacheDir, "${System.currentTimeMillis()}$ext")
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
        _binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        val repo = AppRepository(RetrofitClient.getApiService())
        viewModel = ViewModelProvider(
            requireActivity(), ViewModelFactory(repo)
        )[FeedViewModel::class.java]

        val user = sessionManager.getUser()
        binding.tvUsername.text = "@${user?.username}"
        binding.tvDisplayName.text = user?.displayName ?: user?.username ?: ""

        Glide.with(this)
            .load(user?.avatarUrl?.ifBlank { null })
            .placeholder(R.drawable.ic_default_avatar)
            .circleCrop()
            .into(binding.ivAvatar)

        binding.btnAddImage.setOnClickListener { openMediaPicker() }
        binding.btnClose.setOnClickListener { findNavController().navigateUp() }
        binding.btnPost.setOnClickListener { submitPost() }

        observeViewModel()
    }

    private fun openMediaPicker() {
        if (selectedMediaFiles.size >= MAX_MEDIA) {
            Toast.makeText(context, "Maksimal $MAX_MEDIA media", Toast.LENGTH_SHORT).show()
            return
        }
        // Try Photo Picker API (Android 13+ / backported via Google Play Services)
        if (PickVisualMedia.isPhotoPickerAvailable(requireContext())) {
            mediaPickerLauncher.launch(
                PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)
            )
        } else {
            // Fallback for older devices: accept images and videos
            legacyMediaPickerLauncher.launch("*/*")
        }
    }

    private fun updateImagePreviews() {
        binding.imagePreviewContainer.removeAllViews()
        val sizePx = (90 * resources.displayMetrics.density).toInt()
        val marginPx = (8 * resources.displayMetrics.density).toInt()

        selectedMediaUris.forEachIndexed { index, uri ->
            val isVideo = selectedMediaTypes[index] == "video"

            val frame = android.widget.FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    rightMargin = marginPx
                }
            }

            val iv = ImageView(requireContext()).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(sizePx, sizePx)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            Glide.with(this).load(uri).centerCrop().into(iv)
            frame.addView(iv)

            if (isVideo) {
                // Video badge overlay
                val playIcon = ImageView(requireContext()).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                    setImageResource(android.R.drawable.ic_media_play)
                }
                frame.addView(playIcon)
            }

            val btnRemove = ImageView(requireContext()).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                }
                setImageResource(R.drawable.ic_close_circle)
                setOnClickListener {
                    // capture index in val to avoid closure issue
                    val i = selectedMediaUris.indexOf(uri)
                    if (i >= 0) {
                        selectedMediaFiles.removeAt(i)
                        selectedMediaUris.removeAt(i)
                        selectedMediaTypes.removeAt(i)
                        updateImagePreviews()
                    }
                }
            }
            frame.addView(btnRemove)
            binding.imagePreviewContainer.addView(frame)
        }

        binding.btnAddImage.visibility =
            if (selectedMediaFiles.size < MAX_MEDIA) View.VISIBLE else View.GONE
    }

    private fun submitPost() {
        val content = binding.etContent.text.toString().trim()
        if (content.isEmpty() && selectedMediaFiles.isEmpty()) {
            Toast.makeText(context, "Tulis sesuatu atau pilih media", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.createPost(content, selectedMediaFiles.toList())
    }

    private fun observeViewModel() {
        viewModel.createPostState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is CreatePostState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnPost.isEnabled = false
                }
                is CreatePostState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    viewModel.resetCreatePost()
                    Toast.makeText(context, "Post berhasil diunggah!", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                is CreatePostState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnPost.isEnabled = true
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                }
                is CreatePostState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnPost.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}