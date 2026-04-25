package com.nyantadev.socializee.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nyantadev.socializee.R
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.FragmentExploreBinding
import com.nyantadev.socializee.models.Post
import com.nyantadev.socializee.repository.AppRepository
import com.nyantadev.socializee.ui.adapters.PostAdapter
import com.nyantadev.socializee.ui.adapters.UserAdapter
import com.nyantadev.socializee.utils.SessionManager
import com.nyantadev.socializee.viewmodel.FeedState
import com.nyantadev.socializee.viewmodel.FeedViewModel
import com.nyantadev.socializee.viewmodel.ProfileViewModel
import com.nyantadev.socializee.viewmodel.ViewModelFactory

class ExploreFragment : Fragment() {

    private var _binding: FragmentExploreBinding? = null
    private val binding get() = _binding!!

    private lateinit var feedViewModel: FeedViewModel
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var postAdapter: PostAdapter
    private lateinit var userAdapter: UserAdapter
    private var isSearchMode = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExploreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        val repo = AppRepository(RetrofitClient.getApiService())
        val factory = ViewModelFactory(repo)
        feedViewModel = ViewModelProvider(requireActivity(), factory)[FeedViewModel::class.java]
        profileViewModel = ViewModelProvider(this, factory)[ProfileViewModel::class.java]

        setupSearch()
        setupPostsRecyclerView()
        setupUsersRecyclerView()
        observeViewModels()

        if (feedViewModel.explorePosts.value.isNullOrEmpty()) {
            feedViewModel.loadExplore()
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    showPostsMode()
                } else {
                    showSearchMode()
                    profileViewModel.searchUsers(query)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupPostsRecyclerView() {
        postAdapter = PostAdapter(
            currentUserId = sessionManager.getUserId() ?: "",
            onLike = { post, pos -> feedViewModel.toggleLike(post, pos) },
            onComment = { post -> openComments(post) },
            onRepost = { post -> feedViewModel.toggleRepost(post) },
            onUserClick = { userId -> openProfile(userId) },
            onDelete = { post, _ -> confirmDelete(post) },
            onImageClick = { urls, idx -> openImageViewer(urls, idx) }
        )
        binding.rvPosts.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
        }
        binding.swipeRefresh.setOnRefreshListener {
            feedViewModel.loadExplore(refresh = true)
        }
    }

    private fun setupUsersRecyclerView() {
        userAdapter = UserAdapter(
            onUserClick = { user -> openProfile(user.id) },
            onFollowClick = { user, _ -> profileViewModel.toggleFollow(user.id) }
        )
        binding.rvUsers.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userAdapter
        }
    }

    private fun observeViewModels() {
        feedViewModel.exploreState.observe(viewLifecycleOwner) { state ->
            binding.swipeRefresh.isRefreshing = false
            binding.progressBar.visibility = if (state is FeedState.Loading) View.VISIBLE else View.GONE
        }

        feedViewModel.explorePosts.observe(viewLifecycleOwner) { posts ->
            postAdapter.submitList(posts.toList())
        }

        profileViewModel.searchResults.observe(viewLifecycleOwner) { users ->
            userAdapter.submitList(users)
            binding.tvNoResults.visibility = if (users.isEmpty() && isSearchMode) View.VISIBLE else View.GONE
        }

        profileViewModel.searchLoading.observe(viewLifecycleOwner) { loading ->
            binding.searchProgress.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun showSearchMode() {
        isSearchMode = true
        binding.rvPosts.visibility = View.GONE
        binding.swipeRefresh.visibility = View.GONE
        binding.rvUsers.visibility = View.VISIBLE
    }

    private fun showPostsMode() {
        isSearchMode = false
        binding.rvPosts.visibility = View.VISIBLE
        binding.swipeRefresh.visibility = View.VISIBLE
        binding.rvUsers.visibility = View.GONE
        binding.tvNoResults.visibility = View.GONE
    }

    private fun openComments(post: Post) {
        val bundle = Bundle().apply { putString("postId", post.id) }
        findNavController().navigate(R.id.action_explore_to_comments, bundle)
    }

    private fun openProfile(userId: String) {
        val bundle = Bundle().apply { putString("userId", userId) }
        findNavController().navigate(R.id.action_explore_to_profile, bundle)
    }

    private fun openImageViewer(urls: List<String>, startIndex: Int) {
        val bundle = Bundle().apply {
            putStringArray("urls", urls.toTypedArray())
            putInt("startIndex", startIndex)
        }
        findNavController().navigate(R.id.action_explore_to_imageViewer, bundle)
    }

    private fun confirmDelete(post: Post) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Post")
            .setMessage("Yakin ingin menghapus post ini?")
            .setPositiveButton("Hapus") { _, _ -> feedViewModel.deletePost(post.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}