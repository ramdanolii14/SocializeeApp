package com.nyantadev.socializee.ui.fragments

import android.app.Activity
import android.net.Uri
import android.os.Bundle
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

class CreatePostFragment : Fragment() {

    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: FeedViewModel
    private lateinit var sessionManager: SessionManager
    private val selectedImageFiles = mutableListOf<File>()
    private val selectedImageUris = mutableListOf<Uri>()

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val file = File(uri.path ?: return@registerForActivityResult)
            if (selectedImageFiles.size < 4) {
                selectedImageFiles.add(file)
                selectedImageUris.add(uri)
                updateImagePreviews()
            } else {
                Toast.makeText(context, "Maksimal 4 foto", Toast.LENGTH_SHORT).show()
            }
        }
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

        binding.btnAddImage.setOnClickListener { pickImage() }
        binding.btnClose.setOnClickListener { findNavController().navigateUp() }
        binding.btnPost.setOnClickListener { submitPost() }

        observeViewModel()
    }

    private fun pickImage() {
        ImagePicker.with(this)
            .crop()
            .compress(1024)
            .maxResultSize(1080, 1080)
            .createIntent { imagePickerLauncher.launch(it) }
    }

    private fun updateImagePreviews() {
        binding.imagePreviewContainer.removeAllViews()
        val sizePx = (90 * resources.displayMetrics.density).toInt()
        val marginPx = (8 * resources.displayMetrics.density).toInt()

        selectedImageUris.forEachIndexed { index, uri ->
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

            val btnRemove = ImageView(requireContext()).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                }
                setImageResource(com.nyantadev.socializee.R.drawable.ic_close_circle)
                setOnClickListener {
                    selectedImageFiles.removeAt(index)
                    selectedImageUris.removeAt(index)
                    updateImagePreviews()
                }
            }

            frame.addView(iv)
            frame.addView(btnRemove)
            binding.imagePreviewContainer.addView(frame)
        }

        binding.btnAddImage.visibility = if (selectedImageFiles.size < 4) View.VISIBLE else View.GONE
    }

    private fun submitPost() {
        val content = binding.etContent.text.toString().trim()
        if (content.isEmpty() && selectedImageFiles.isEmpty()) {
            Toast.makeText(context, "Tulis sesuatu atau pilih foto", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.createPost(content, selectedImageFiles.toList())
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
