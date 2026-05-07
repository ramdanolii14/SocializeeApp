package com.nyantadev.socializee.ui.fragments

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.nyantadev.socializee.R
import com.nyantadev.socializee.databinding.FragmentImageViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class ImageViewerFragment : Fragment() {

    private var _binding: FragmentImageViewerBinding? = null
    private val binding get() = _binding!!

    private val players = mutableListOf<ExoPlayer>()
    private var currentUrls: List<String> = emptyList()
    private var currentPosition = 0

    // ── Storage permission launcher ───────────────────────────────────────
    private val writePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) downloadCurrentImage()
            else Toast.makeText(
                requireContext(),
                getString(R.string.perm_storage),
                Toast.LENGTH_SHORT
            ).show()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val urls = arguments?.getStringArray("urls")?.toList() ?: run {
            findNavController().navigateUp()
            return
        }
        val startIndex = arguments?.getInt("startIndex", 0) ?: 0

        currentUrls     = urls
        currentPosition = startIndex

        binding.ivClose.setOnClickListener { findNavController().navigateUp() }

        // ── Download button (req #7) ──────────────────────────────────────
        binding.ivDownload.setOnClickListener { requestDownload() }
        // Only show download button for images (hide for video pages)
        updateDownloadVisibility(currentPosition)

        // ── ViewPager ─────────────────────────────────────────────────────
        binding.viewPager.adapter = MediaPagerAdapter(urls)
        binding.viewPager.setCurrentItem(startIndex, false)

        if (urls.size > 1) {
            binding.tvIndicator.visibility = View.VISIBLE
            binding.tvIndicator.text = "${startIndex + 1} / ${urls.size}"
            binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentPosition = position
                    binding.tvIndicator.text = "${position + 1} / ${urls.size}"
                    updateDownloadVisibility(position)
                }
            })
        } else {
            binding.tvIndicator.visibility = View.GONE
        }
    }

    private fun updateDownloadVisibility(position: Int) {
        val url = currentUrls.getOrNull(position) ?: return
        // Hanya tampilkan tombol download untuk gambar, bukan video
        binding.ivDownload.visibility = if (isVideoUrl(url)) View.GONE else View.VISIBLE
    }

    // ── Download logic ────────────────────────────────────────────────────

    private fun requestDownload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 ke bawah perlu izin WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        downloadCurrentImage()
    }

    private fun downloadCurrentImage() {
        val url = currentUrls.getOrNull(currentPosition) ?: return
        if (isVideoUrl(url)) return

        Toast.makeText(requireContext(), R.string.msg_downloading, Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Ambil bitmap via Glide (IO dispatcher)
                val bitmap = withContext(Dispatchers.IO) {
                    Glide.with(requireContext())
                        .asBitmap()
                        .load(url)
                        .submit()
                        .get()
                }

                withContext(Dispatchers.IO) {
                    saveBitmapToGallery(bitmap, url)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(), R.string.msg_download_success, Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(), R.string.msg_download_failed, Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, sourceUrl: String) {
        val filename = "socializee_${System.currentTimeMillis()}.jpg"
        val outputStream: OutputStream?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = requireContext().contentResolver
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Socializee")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)!!
            outputStream = resolver.openOutputStream(uri)
            outputStream?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            cv.clear()
            cv.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, cv, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val folder = java.io.File(dir, "Socializee").also { it.mkdirs() }
            val file   = java.io.File(folder, filename)
            outputStream = java.io.FileOutputStream(file)
            outputStream.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            // Scan media library
            android.media.MediaScannerConnection.scanFile(
                requireContext(), arrayOf(file.absolutePath), null, null
            )
        }
    }

    // ── ViewPager adapter ─────────────────────────────────────────────────

    inner class MediaPagerAdapter(private val urls: List<String>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int) =
            if (isVideoUrl(urls[position])) TYPE_VIDEO else TYPE_IMAGE

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_VIDEO) {
                val pv = PlayerView(parent.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                VideoVH(pv)
            } else {
                val iv = ImageView(parent.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }
                ImageVH(iv)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val url = urls[position]
            when (holder) {
                is ImageVH -> Glide.with(holder.iv.context).load(url).into(holder.iv)
                is VideoVH -> {
                    val exo = ExoPlayer.Builder(holder.pv.context).build().also { players.add(it) }
                    holder.pv.player = exo
                    exo.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                    exo.prepare()
                }
            }
        }

        override fun getItemCount() = urls.size

        inner class ImageVH(val iv: ImageView) : RecyclerView.ViewHolder(iv)
        inner class VideoVH(val pv: PlayerView) : RecyclerView.ViewHolder(pv)
    }

    private fun isVideoUrl(url: String): Boolean {
        val path = url.substringBefore("?").lowercase()
        return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".avi") ||
                path.endsWith(".mkv") || path.endsWith(".3gp") || path.contains("/videos/")
    }

    override fun onPause() {
        super.onPause()
        players.forEach { it.pause() }
    }

    override fun onDestroyView() {
        players.forEach { it.release() }
        players.clear()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TYPE_IMAGE = 0
        const val TYPE_VIDEO = 1
    }
}