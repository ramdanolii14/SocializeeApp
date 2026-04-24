package com.nyantadev.socializee.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nyantadev.socializee.R
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.FragmentFeedBinding
import com.nyantadev.socializee.models.Post
import com.nyantadev.socializee.repository.AppRepository
import com.nyantadev.socializee.ui.adapters.PostAdapter
import com.nyantadev.socializee.utils.SessionManager
import com.nyantadev.socializee.viewmodel.FeedState
import com.nyantadev.socializee.viewmodel.FeedViewModel
import com.nyantadev.socializee.viewmodel.ViewModelFactory

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: FeedViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var postAdapter: PostAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        val repo = AppRepository(RetrofitClient.getApiService())
        viewModel = ViewModelProvider(requireActivity(), ViewModelFactory(repo))[FeedViewModel::class.java]

        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        observeViewModel()

        if (viewModel.posts.value.isNullOrEmpty()) {
            viewModel.loadFeed()
        }
    }

    private fun setupRecyclerView() {
        postAdapter = PostAdapter(
            currentUserId = sessionManager.getUserId() ?: "",
            onLike = { post, pos -> viewModel.toggleLike(post, pos) },
            onComment = { post -> openComments(post) },
            onUserClick = { userId -> openProfile(userId) },
            onDelete = { post, _ -> confirmDelete(post) },
            onImageClick = { urls, idx -> openImageViewer(urls, idx) }
        )

        binding.rvFeed.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(rv, dx, dy)
                    val lm = rv.layoutManager as LinearLayoutManager
                    val lastVisible = lm.findLastVisibleItemPosition()
                    val total = lm.itemCount
                    if (lastVisible >= total - 3 && !viewModel.isFeedLoadingMore) {
                        viewModel.loadFeed()
                    }
                }
            })
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadFeed(refresh = true)
        }
    }

    private fun setupFab() {
        binding.fabNewPost.setOnClickListener {
            findNavController().navigate(R.id.action_feed_to_createPost)
        }
    }

    private fun observeViewModel() {
        viewModel.feedState.observe(viewLifecycleOwner) { state ->
            binding.swipeRefresh.isRefreshing = false
            when (state) {
                is FeedState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is FeedState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility = if (state.posts.isEmpty()) View.VISIBLE else View.GONE
                }
                is FeedState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.posts.observe(viewLifecycleOwner) { posts ->
            postAdapter.submitList(posts.toList())
        }
    }

    private fun openComments(post: Post) {
        val bundle = Bundle().apply { putString("postId", post.id) }
        findNavController().navigate(R.id.action_feed_to_comments, bundle)
    }

    private fun openProfile(userId: String) {
        val bundle = Bundle().apply { putString("userId", userId) }
        findNavController().navigate(R.id.action_feed_to_profile, bundle)
    }

    private fun openImageViewer(urls: List<String>, startIndex: Int) {
        val bundle = Bundle().apply {
            putStringArray("urls", urls.toTypedArray())
            putInt("startIndex", startIndex)
        }
        findNavController().navigate(R.id.action_feed_to_imageViewer, bundle)
    }

    private fun confirmDelete(post: Post) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Post")
            .setMessage("Yakin ingin menghapus post ini?")
            .setPositiveButton("Hapus") { _, _ -> viewModel.deletePost(post.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}