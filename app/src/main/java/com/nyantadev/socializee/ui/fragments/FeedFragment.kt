package com.nyantadev.socializee.ui.fragments

import android.content.Intent
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
import com.google.android.material.chip.ChipGroup
import com.nyantadev.socializee.R
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.FragmentFeedBinding
import com.nyantadev.socializee.models.Post
import com.nyantadev.socializee.repository.AppRepository
import com.nyantadev.socializee.ui.AuthActivity
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

    // Sort state — default: terbaru
    private var currentSort: FeedViewModel.SortMode = FeedViewModel.SortMode.NEWEST

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        val repo = AppRepository(RetrofitClient.getApiService())
        viewModel = ViewModelProvider(
            requireActivity(), ViewModelFactory(repo)
        )[FeedViewModel::class.java]

        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        setupSortChips()
        observeViewModel()

        if (viewModel.posts.value.isNullOrEmpty()) {
            viewModel.loadFeed(sort = currentSort)
        }
    }

    // ── Sort chips ────────────────────────────────────────────────────────

    private fun setupSortChips() {
        binding.chipGroupSort.setOnCheckedStateChangeListener { _, checkedIds ->
            val sort = when (checkedIds.firstOrNull()) {
                R.id.chipTrending -> FeedViewModel.SortMode.TRENDING
                R.id.chipOldest   -> FeedViewModel.SortMode.OLDEST
                R.id.chipRandom   -> FeedViewModel.SortMode.RANDOM
                else              -> FeedViewModel.SortMode.NEWEST
            }
            if (sort != currentSort) {
                currentSort = sort
                viewModel.loadFeed(refresh = true, sort = currentSort)
            }
        }
    }

    // ── RecyclerView ──────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        postAdapter = PostAdapter(
            currentUserId = sessionManager.getUserId() ?: "",
            isAdmin       = sessionManager.isAdmin(),
            onLike        = { post, pos -> viewModel.toggleLike(post, pos) },
            onComment     = { post -> openComments(post) },
            onRepost      = { post -> viewModel.toggleRepost(post) },
            onUserClick   = { userId -> openProfile(userId) },
            onDelete      = { post, _ -> confirmDelete(post) },
            onImageClick  = { urls, idx -> openImageViewer(urls, idx) }
        )

        val layoutManager = LinearLayoutManager(context)
        binding.rvFeed.apply {
            this.layoutManager = layoutManager
            adapter = postAdapter

            // Infinite scroll
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(rv, dx, dy)
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    val total = layoutManager.itemCount
                    if (lastVisible >= total - 3 && !viewModel.isFeedLoadingMore) {
                        viewModel.loadFeed(sort = currentSort)
                    }
                }
            })

            // Video auto-play / auto-pause on scroll (req #5)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(rv, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        playVisibleVideo(layoutManager)
                    }
                }

                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(rv, dx, dy)
                    pauseOffScreenVideos(layoutManager)
                }
            })
        }
    }

    /**
     * Auto-play video yang paling besar terlihat di layar (muted).
     * Pause semua video lain.
     */
    private fun playVisibleVideo(lm: LinearLayoutManager) {
        val first = lm.findFirstVisibleItemPosition()
        val last  = lm.findLastVisibleItemPosition()
        if (first < 0) return

        var bestHolder: PostAdapter.PostViewHolder? = null
        var bestArea = 0

        for (i in first..last) {
            val holder = binding.rvFeed.findViewHolderForAdapterPosition(i)
                    as? PostAdapter.PostViewHolder ?: continue
            val area = holder.getVisibleArea()
            if (area > bestArea) {
                bestArea = area
                bestHolder = holder
            }
        }

        // Pause semua, lalu play yang paling kelihatan
        for (i in first..last) {
            val holder = binding.rvFeed.findViewHolderForAdapterPosition(i)
                    as? PostAdapter.PostViewHolder ?: continue
            if (holder == bestHolder) holder.playVideo()
            else holder.pauseVideo()
        }
    }

    private fun pauseOffScreenVideos(lm: LinearLayoutManager) {
        val first = lm.findFirstVisibleItemPosition()
        val last  = lm.findLastVisibleItemPosition()
        val itemCount = lm.itemCount
        // Pause items yang sudah keluar layar (bawah dan atas)
        for (i in 0 until itemCount) {
            if (i in first..last) continue
            val holder = binding.rvFeed.findViewHolderForAdapterPosition(i)
                    as? PostAdapter.PostViewHolder ?: continue
            holder.pauseVideo()
        }
    }

    // ── Swipe refresh ─────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadFeed(refresh = true, sort = currentSort)
        }
    }

    // ── FAB ───────────────────────────────────────────────────────────────

    private fun setupFab() {
        binding.fabNewPost.setOnClickListener {
            findNavController().navigate(R.id.action_feed_to_createPost)
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.feedState.observe(viewLifecycleOwner) { state ->
            binding.swipeRefresh.isRefreshing = false
            when (state) {
                is FeedState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is FeedState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility =
                        if (state.posts.isEmpty()) View.VISIBLE else View.GONE
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

        viewModel.bannedEvent.observe(viewLifecycleOwner) { event ->
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

    // ── Navigation ────────────────────────────────────────────────────────

    private fun openComments(post: Post) =
        findNavController().navigate(R.id.action_feed_to_comments,
            Bundle().apply { putString("postId", post.id) })

    private fun openProfile(userId: String) =
        findNavController().navigate(R.id.action_feed_to_profile,
            Bundle().apply { putString("userId", userId) })

    private fun openImageViewer(urls: List<String>, startIndex: Int) =
        findNavController().navigate(R.id.action_feed_to_imageViewer, Bundle().apply {
            putStringArray("urls", urls.toTypedArray())
            putInt("startIndex", startIndex)
        })

    private fun confirmDelete(post: Post) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_delete_post)
            .setMessage(R.string.msg_delete_post)
            .setPositiveButton(R.string.btn_delete) { _, _ -> viewModel.deletePost(post.id) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        // Pause semua video saat fragment tidak kelihatan
        val lm = binding.rvFeed.layoutManager as? LinearLayoutManager ?: return
        for (i in lm.findFirstVisibleItemPosition()..lm.findLastVisibleItemPosition()) {
            (binding.rvFeed.findViewHolderForAdapterPosition(i) as? PostAdapter.PostViewHolder)
                ?.pauseVideo()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}