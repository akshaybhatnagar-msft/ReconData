package com.smsdelete.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smsdelete.app.databinding.ItemMessageReceivedBinding
import com.smsdelete.app.databinding.ItemMessageSentBinding

class SmsPreviewAdapter :
    ListAdapter<SmsEntry, RecyclerView.ViewHolder>(DIFF) {

    inner class ReceivedViewHolder(private val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: SmsEntry) =
            bindCommon(entry, binding.ivAttachment, binding.tvBody, binding.tvDate)
    }

    inner class SentViewHolder(private val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: SmsEntry) =
            bindCommon(entry, binding.ivAttachment, binding.tvBody, binding.tvDate)
    }

    private fun bindCommon(entry: SmsEntry, image: ImageView, body: TextView, date: TextView) {
        if (entry.attachmentUri != null) {
            image.visibility = View.VISIBLE
            image.setImageURI(entry.attachmentUri)
        } else {
            image.visibility = View.GONE
            image.setImageDrawable(null)
        }
        if (entry.body.isBlank()) {
            body.visibility = View.GONE
        } else {
            body.visibility = View.VISIBLE
            body.text = entry.body
        }
        date.text = SmsHelper.formatDate(entry.dateMs)
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isSent) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            SentViewHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
        } else {
            ReceivedViewHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val entry = getItem(position)
        when (holder) {
            is SentViewHolder     -> holder.bind(entry)
            is ReceivedViewHolder -> holder.bind(entry)
        }
    }

    companion object {
        private const val VIEW_TYPE_RECEIVED = 0
        private const val VIEW_TYPE_SENT = 1

        private val DIFF = object : DiffUtil.ItemCallback<SmsEntry>() {
            override fun areItemsTheSame(a: SmsEntry, b: SmsEntry) =
                a.id == b.id && a.isMms == b.isMms
            override fun areContentsTheSame(a: SmsEntry, b: SmsEntry) = a == b
        }
    }
}
