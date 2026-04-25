package com.nyantadev.socializee.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.FragmentFollowListBinding
import com.nyantadev.socializee.repository.AppRepository
import com.nyantadev.socializee.ui.adapters.UserAdapter
import com.nyantadev.socializee.viewmodel.ProfileViewModel
import com.nyantadev.socializee.viewmodel.ViewModelFactory

class FollowListBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val MODE_FOLLOWERS = "followers"
        const val MODE_FOLLOWING = "following"

        private const val ARG_USER_ID = "userId"
        private const val ARG_MODE   = "mode"

        fun newInstance(userId: String, mode: String): FollowListBottomSheet {
            return FollowListBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_USER_ID, userId)
                    putString(ARG_MODE, mode)
                }
            }
        }
    }

    private var _binding: FragmentFollowListBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProfileViewModel
    private lateinit var userAdapter: UserAdapter

    private val userId by lazy { arguments?.getString(ARG_USER_ID) ?: "" }
    private val mode   by lazy { arguments?.getString(ARG_MODE) ?: MODE_FOLLOWERS }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFollowListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repo    = AppRepository(RetrofitClient.getApiService())
        val factory = ViewModelFactory(repo)
        viewModel   = ViewModelProvider(this, factory)[ProfileViewModel::class.java]

        binding.tvTitle.text = if (mode == MODE_FOLLOWERS) "Pengikut" else "Mengikuti"

        setupRecyclerView()
        observeData()
        loadData()
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter(
            onUserClick  = { user ->
                // Tutup bottom sheet lalu buka profil user yang diklik
                dismiss()
                // Kirim ke ProfileFragment via NavController jika tersedia
                // Jika tidak, fragment pemanggil bisa set listener sendiri
            },
            onFollowClick = { _, _ -> /* opsional: bisa di-extend */ }
        )
        binding.rvUsers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter        = userAdapter
        }
    }

    private fun observeData() {
        if (mode == MODE_FOLLOWERS) {
            viewModel.followersList.observe(viewLifecycleOwner) { users ->
                binding.progressBar.visibility = View.GONE
                if (users.isEmpty()) {
                    binding.tvEmpty.visibility  = View.VISIBLE
                    binding.rvUsers.visibility  = View.GONE
                } else {
                    binding.tvEmpty.visibility  = View.GONE
                    binding.rvUsers.visibility  = View.VISIBLE
                    userAdapter.submitList(users)
                }
            }
        } else {
            viewModel.followingList.observe(viewLifecycleOwner) { users ->
                binding.progressBar.visibility = View.GONE
                if (users.isEmpty()) {
                    binding.tvEmpty.visibility  = View.VISIBLE
                    binding.rvUsers.visibility  = View.GONE
                } else {
                    binding.tvEmpty.visibility  = View.GONE
                    binding.rvUsers.visibility  = View.VISIBLE
                    userAdapter.submitList(users)
                }
            }
        }
    }

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility     = View.GONE
        binding.rvUsers.visibility     = View.GONE

        if (mode == MODE_FOLLOWERS) {
            viewModel.loadFollowers(userId)
        } else {
            viewModel.loadFollowing(userId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}