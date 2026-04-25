package com.smsdelete.app

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smsdelete.app.databinding.ItemThreadBinding

class ThreadListAdapter(
    private val onClick: (ThreadSummary) -> Unit,
    private val onLongClick: (ThreadSummary) -> Unit = {}
) : ListAdapter<ThreadSummary, ThreadListAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemThreadBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(thread: ThreadSummary) {
            val isUnread = thread.unreadCount > 0
            val titleStyle = if (isUnread) Typeface.BOLD else Typeface.NORMAL

            binding.tvAvatar.text = Avatars.initial(thread.displayName)
            binding.tvAvatar.background =
                Avatars.circleDrawable(Avatars.colorFor(thread.address.ifBlank { thread.displayName }))

            binding.tvAddress.text = thread.displayName
            binding.tvAddress.setTypeface(null, titleStyle)

            if (thread.displayName != thread.address && thread.address.isNotBlank()) {
                binding.tvSubAddress.visibility = View.VISIBLE
                binding.tvSubAddress.text = thread.address
            } else {
                binding.tvSubAddress.visibility = View.GONE
            }

            binding.tvLastBody.text = thread.lastBody.take(140).let {
                if (thread.lastBody.length > 140) "$it…" else it
            }
            binding.tvLastBody.setTypeface(null, titleStyle)

            binding.tvLastDate.text = SmsHelper.formatDate(thread.lastDateMs)

            if (isUnread) {
                binding.tvUnread.visibility = View.VISIBLE
                binding.tvUnread.text = if (thread.unreadCount > 99) "99+"
                                        else thread.unreadCount.toString()
            } else {
                binding.tvUnread.visibility = View.GONE
            }

            binding.root.setOnClickListener { onClick(thread) }
            binding.root.setOnLongClickListener { onLongClick(thread); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemThreadBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ThreadSummary>() {
            override fun areItemsTheSame(a: ThreadSummary, b: ThreadSummary) =
                a.threadId == b.threadId
            override fun areContentsTheSame(a: ThreadSummary, b: ThreadSummary) = a == b
        }
    }
}
