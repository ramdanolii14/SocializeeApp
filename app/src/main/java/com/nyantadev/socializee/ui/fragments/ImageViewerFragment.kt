package com.nyantadev.socializee.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nyantadev.socializee.databinding.FragmentImageViewerBinding

class ImageViewerFragment : Fragment() {

    private var _binding: FragmentImageViewerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentImageViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val urls = arguments?.getStringArrayList("urls") ?: run {
            findNavController().navigateUp(); return
        }
        val startIndex = arguments?.getInt("startIndex", 0) ?: 0

        binding.ivClose.setOnClickListener { findNavController().navigateUp() }

        binding.viewPager.adapter = ImagePagerAdapter(urls)
        binding.viewPager.setCurrentItem(startIndex, false)

        // Page indicator
        if (urls.size > 1) {
            binding.tvIndicator.visibility = View.VISIBLE
            binding.tvIndicator.text = "${startIndex + 1} / ${urls.size}"
            binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.tvIndicator.text = "${position + 1} / ${urls.size}"
                }
            })
        }
    }

    inner class ImagePagerAdapter(private val urls: List<String>) :
        RecyclerView.Adapter<ImagePagerAdapter.VH>() {

        inner class VH(val iv: ImageView) : RecyclerView.ViewHolder(iv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val iv = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
            return VH(iv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            Glide.with(holder.iv.context)
                .load(urls[position])
                .into(holder.iv)
        }

        override fun getItemCount() = urls.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
