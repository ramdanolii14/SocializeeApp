package com.nyantadev.socializee.ui.fragments

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.github.dhaval2404.imagepicker.ImagePicker
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.FragmentCreatePostBinding
import com.nyantadev.socializee.repository.AppRepository
import com.nyantadev.socializee.utils.SessionManager
import com.nyantadev.socializee.viewmodel.CreatePostState
import com.nyantadev.socializee.viewmodel.FeedViewModel
import com.nyantadev.socializee.viewmodel.ViewModelFactory
import java.io.File
import java.io.FileOutputStream

class CreatePostFragment : Fragment() {

    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: FeedViewModel
    private lateinit var sessionManager: SessionManager
    private val selectedMediaFiles = mutableListOf<File>()
    private val selectedMediaUris = mutableListOf<Uri>()
    private val selectedMediaTypes = mutableListOf<String>() // "image" or "video"

    companion object {
        private const val MAX_MEDIA = 10
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024L // 50MB
    }

    // Image picker launcher (menggunakan ImagePicker library)
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            handleMediaSelected(uri, "image")
        }
    }

    // Video picker launcher (menggunakan system picker)
    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        handleMediaSelected(uri, "video")
    }

    private fun handleMediaSelected(uri: Uri, type: String) {
        if (selectedMediaFiles.size >= MAX_MEDIA) {
            Toast.makeText(context, "Maksimal $MAX_MEDIA media", Toast.LENGTH_SHORT).show()
            return
        }

        val fileSize = getFileSize(uri)
        if (fileSize > MAX_FILE_SIZE) {
            Toast.makeText(context, "File terlalu besar (maks 50MB per file)", Toast.LENGTH_SHORT).show()
            return
        }

        val file = copyUriToTempFile(uri, type) ?: run {
            Toast.makeText(context, "Gagal memproses file", Toast.LENGTH_SHORT).show()
            return
        }

        selectedMediaFiles.add(file)
        selectedMediaUris.add(uri)
        selectedMediaTypes.add(type)
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

    /**
     * Copy URI ke file sementara.
     * Ekstensi file ditentukan dari MIME type via ContentResolver agar
     * backend multer bisa mengenali tipe file dengan benar.
     */
    private fun copyUriToTempFile(uri: Uri, type: String): File? {
        return try {
            val context = requireContext()

            // Deteksi MIME type dari ContentResolver (lebih akurat daripada nama file)
            val mimeType = context.contentResolver.getType(uri)
            val ext = when (mimeType) {
                "image/jpeg"      -> ".jpg"
                "image/jpg"       -> ".jpg"
                "image/png"       -> ".png"
                "image/gif"       -> ".gif"
                "image/webp"      -> ".webp"
                "image/bmp"       -> ".bmp"
                "video/mp4"       -> ".mp4"
                "video/quicktime" -> ".mov"
                "video/x-msvideo" -> ".avi"
                "video/x-matroska"-> ".mkv"
                "video/3gpp"      -> ".3gp"
                "video/x-ms-wmv"  -> ".wmv"
                else -> {
                    // Fallback: ambil dari nama file asli
                    val fileName = getFileName(context, uri)
                    if (fileName.contains(".")) {
                        ".${fileName.substringAfterLast(".")}"
                    } else {
                        if (type == "video") ".mp4" else ".jpg"
                    }
                }
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

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) {
                name = cursor.getString(idx)
            }
        }
        return name
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        val repo = AppRepository(RetrofitClient.getApiService())
        viewModel = ViewModelProvider(requireActivity(), ViewModelFactory(repo))[FeedViewModel::class.java]

        val user = sessionManager.getUser()
        binding.tvUsername.text = "@${user?.username}"
        binding.tvDisplayName.text = user?.displayName ?: user?.username ?: ""

        Glide.with(this)
            .load(user?.avatarUrl?.ifBlank { null })
            .placeholder(com.nyantadev.socializee.R.drawable.ic_default_avatar)
            .circleCrop()
            .into(binding.ivAvatar)

        binding.btnAddImage.setOnClickListener { showMediaPickerOptions() }
        binding.btnClose.setOnClickListener { findNavController().navigateUp() }
        binding.btnPost.setOnClickListener { submitPost() }

        observeViewModel()
    }

    private fun showMediaPickerOptions() {
        val options = arrayOf("Foto", "Video")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Pilih Media")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImage()
                    1 -> pickVideo()
                }
            }
            .show()
    }

    private fun pickImage() {
        ImagePicker.with(this)
            .crop()
            .compress(1024)
            .maxResultSize(1080, 1080)
            .createIntent { imagePickerLauncher.launch(it) }
    }

    private fun pickVideo() {
        videoPickerLauncher.launch("video/*")
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

            if (isVideo) {
                Glide.with(this).load(uri).centerCrop().into(iv)
                val playIcon = ImageView(requireContext()).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                    setImageResource(android.R.drawable.ic_media_play)
                }
                frame.addView(iv)
                frame.addView(playIcon)
            } else {
                Glide.with(this).load(uri).centerCrop().into(iv)
                frame.addView(iv)
            }

            val btnRemove = ImageView(requireContext()).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                }
                setImageResource(com.nyantadev.socializee.R.drawable.ic_close_circle)
                setOnClickListener {
                    selectedMediaFiles.removeAt(index)
                    selectedMediaUris.removeAt(index)
                    selectedMediaTypes.removeAt(index)
                    updateImagePreviews()
                }
            }

            frame.addView(btnRemove)
            binding.imagePreviewContainer.addView(frame)
        }

        binding.btnAddImage.visibility = if (selectedMediaFiles.size < MAX_MEDIA) View.VISIBLE else View.GONE
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