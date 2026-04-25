package com.smsdelete.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smsdelete.app.databinding.ItemThreadBinding

class ThreadListAdapter(
    private val onClick: (ThreadSummary) -> Unit
) : ListAdapter<ThreadSummary, ThreadListAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemThreadBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(thread: ThreadSummary) {
            binding.tvAddress.text = thread.displayName
            // If displayName came from contacts, also show the underlying number
            if (thread.displayName != thread.address && thread.address.isNotBlank()) {
                binding.tvSubAddress.visibility = View.VISIBLE
                binding.tvSubAddress.text = thread.address
            } else {
                binding.tvSubAddress.visibility = View.GONE
            }
            binding.tvLastBody.text = thread.lastBody.take(140).let {
                if (thread.lastBody.length > 140) "$it…" else it
            }
            binding.tvLastDate.text = SmsHelper.formatDate(thread.lastDateMs)
            binding.tvCount.text = itemView.resources.getQuantityString(
                R.plurals.thread_message_count, thread.count, thread.count
            )
            binding.root.setOnClickListener { onClick(thread) }
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
