package com.nyantadev.socializee.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nyantadev.socializee.R
import com.nyantadev.socializee.databinding.ItemCommentBinding
import com.nyantadev.socializee.models.Comment

class CommentAdapter : ListAdapter<Comment, CommentAdapter.CommentViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(old: Comment, new: Comment) = old.id == new.id
        override fun areContentsTheSame(old: Comment, new: Comment) = old == new
    }

    inner class CommentViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: Comment) = with(binding) {
            tvDisplayName.text = comment.displayName.ifBlank { comment.username }
            tvUsername.text = "@${comment.username}"
            tvContent.text = comment.content

            Glide.with(root.context)
                .load(comment.avatarUrl.ifBlank { null })
                .placeholder(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(ivAvatar)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
