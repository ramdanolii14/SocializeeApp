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
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.FragmentCommentsBinding
import com.nyantadev.socializee.models.Comment
import com.nyantadev.socializee.repository.AppRepository
import com.nyantadev.socializee.ui.adapters.CommentAdapter
import com.nyantadev.socializee.viewmodel.ViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommentsFragment : Fragment() {

    private var _binding: FragmentCommentsBinding? = null
    private val binding get() = _binding!!
    private lateinit var commentAdapter: CommentAdapter
    private var postId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postId = arguments?.getString("postId") ?: run {
            findNavController().navigateUp()
            return
        }

        val repo = AppRepository(RetrofitClient.getApiService())

        commentAdapter = CommentAdapter()
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentAdapter
        }

        binding.ivBack.setOnClickListener { findNavController().navigateUp() }

        binding.btnSend.setOnClickListener {
            val text = binding.etComment.text.toString().trim()
            if (text.isBlank()) return@setOnClickListener
            sendComment(repo, text)
        }

        loadComments(repo)
    }

    private fun loadComments(repo: AppRepository) {
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val res = repo.getComments(postId)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (res.isSuccessful && res.body()?.success == true) {
                        val comments = res.body()!!.comments ?: emptyList()
                        commentAdapter.submitList(comments)
                        binding.tvEmpty.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Gagal memuat komentar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendComment(repo: AppRepository, text: String) {
        binding.btnSend.isEnabled = false
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val res = repo.addComment(postId, text)
                withContext(Dispatchers.Main) {
                    binding.btnSend.isEnabled = true
                    if (res.isSuccessful && res.body()?.success == true) {
                        val comment = res.body()!!.comment!!
                        val current = commentAdapter.currentList.toMutableList()
                        current.add(comment)
                        commentAdapter.submitList(current)
                        binding.etComment.setText("")
                        binding.tvEmpty.visibility = View.GONE
                        binding.rvComments.scrollToPosition(current.size - 1)
                    } else {
                        Toast.makeText(context, "Gagal mengirim komentar", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnSend.isEnabled = true
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
