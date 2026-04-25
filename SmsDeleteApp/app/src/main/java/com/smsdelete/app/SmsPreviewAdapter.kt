package com.smsdelete.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smsdelete.app.databinding.ItemSmsBinding

class SmsPreviewAdapter : ListAdapter<SmsEntry, SmsPreviewAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemSmsBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: SmsEntry) {
            binding.tvSender.text  = entry.address.ifBlank { "Unknown" }
            binding.tvBody.text    = entry.body.take(120).let {
                if (entry.body.length > 120) "$it…" else it
            }
            binding.tvDate.text    = SmsHelper.formatDate(entry.dateMs)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSmsBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SmsEntry>() {
            override fun areItemsTheSame(a: SmsEntry, b: SmsEntry) = a.id == b.id
            override fun areContentsTheSame(a: SmsEntry, b: SmsEntry) = a == b
        }
    }
}
