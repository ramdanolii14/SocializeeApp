package com.nyantadev.socializee.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nyantadev.socializee.R
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.FragmentNotificationBinding
import com.nyantadev.socializee.models.Notification
import com.nyantadev.socializee.repository.AppRepository
import com.nyantadev.socializee.ui.adapters.NotificationAdapter
import com.nyantadev.socializee.viewmodel.NotificationState
import com.nyantadev.socializee.viewmodel.NotificationViewModel
import com.nyantadev.socializee.viewmodel.ViewModelFactory

class NotificationFragment : Fragment() {

    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: NotificationViewModel
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repo    = AppRepository(RetrofitClient.getApiService())
        val factory = ViewModelFactory(repo)
        viewModel   = ViewModelProvider(this, factory)[NotificationViewModel::class.java]

        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()

        binding.tvMarkAllRead.setOnClickListener { viewModel.markAllRead() }

        viewModel.loadNotifications()
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter { notif ->
            viewModel.markRead(notif.id)
            navigateToTarget(notif)
        }
        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter        = this@NotificationFragment.adapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadNotifications() }
    }

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.swipeRefresh.isRefreshing = false
            when (state) {
                is NotificationState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                }
                is NotificationState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (state.notifications.isEmpty()) {
                        binding.layoutEmpty.visibility     = View.VISIBLE
                        binding.rvNotifications.visibility = View.GONE
                    } else {
                        binding.layoutEmpty.visibility     = View.GONE
                        binding.rvNotifications.visibility = View.VISIBLE
                        adapter.submitList(state.notifications)
                    }
                }
                is NotificationState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToTarget(notif: Notification) {
        try {
            when (notif.type) {
                "like", "comment", "mention", "repost" -> {
                    notif.postId?.let { postId ->
                        findNavController().navigate(
                            R.id.action_notificationFragment_to_comments,
                            Bundle().apply { putString("postId", postId) }
                        )
                    }
                }
                "follow" -> {
                    findNavController().navigate(
                        R.id.action_notificationFragment_to_profile,
                        Bundle().apply { putString("userId", notif.actorId) }
                    )
                }
            }
        } catch (e: Exception) { /* abaikan */ }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}