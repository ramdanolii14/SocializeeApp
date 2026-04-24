package com.nyantadev.socializee.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nyantadev.socializee.R
import com.nyantadev.socializee.databinding.ItemUserBinding
import com.nyantadev.socializee.models.User

class UserAdapter(
    private val onUserClick: (User) -> Unit,
    private val onFollowClick: (User, Int) -> Unit
) : ListAdapter<User, UserAdapter.UserViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(old: User, new: User) = old.id == new.id
        override fun areContentsTheSame(old: User, new: User) = old == new
    }

    inner class UserViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User, position: Int) = with(binding) {
            tvDisplayName.text = user.displayName.ifBlank { user.username }
            tvUsername.text = "@${user.username}"
            tvBio.text = user.bio
            tvFollowers.text = "${user.followersCount} pengikut"

            Glide.with(root.context)
                .load(user.avatarUrl.ifBlank { null })
                .placeholder(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(ivAvatar)

            btnFollow.text = if (user.isFollowing) "Diikuti" else "Ikuti"
            btnFollow.isSelected = user.isFollowing

            root.setOnClickListener { onUserClick(user) }
            btnFollow.setOnClickListener { onFollowClick(user, position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
}
