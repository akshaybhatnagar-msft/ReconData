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
            val ctx = itemView.context
            binding.tvSender.text = when {
                entry.isSent           -> ctx.getString(R.string.sender_you)
                entry.address.isNotBlank() -> entry.address
                else                   -> ctx.getString(R.string.sender_them)
            }
            val prefix = if (entry.isMms) "📎 " else ""
            val body = entry.body
            binding.tvBody.text = (prefix + body).take(160 + prefix.length).let {
                if (body.length > 160) "$it…" else it
            }
            binding.tvDate.text = SmsHelper.formatDate(entry.dateMs)
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
            override fun areItemsTheSame(a: SmsEntry, b: SmsEntry) =
                a.id == b.id && a.isMms == b.isMms
            override fun areContentsTheSame(a: SmsEntry, b: SmsEntry) = a == b
        }
    }
}
