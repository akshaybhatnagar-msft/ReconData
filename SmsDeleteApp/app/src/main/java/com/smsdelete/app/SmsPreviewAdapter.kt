package com.smsdelete.app

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smsdelete.app.databinding.ItemDayHeaderBinding
import com.smsdelete.app.databinding.ItemMessageReceivedBinding
import com.smsdelete.app.databinding.ItemMessageSentBinding

class SmsPreviewAdapter(
    private val onMessageLongClick: (SmsEntry) -> Unit = {},
    private val onAttachmentClick: (Uri) -> Unit = {}
) : ListAdapter<MessageItem, RecyclerView.ViewHolder>(DIFF) {

    inner class DayViewHolder(private val binding: ItemDayHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MessageItem.DayHeader) {
            binding.tvDayLabel.text = item.label
        }
    }

    inner class ReceivedViewHolder(private val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: SmsEntry) {
            bindCommon(entry, binding.bubble, binding.ivAttachment, binding.tvBody, binding.tvDate)
        }
    }

    inner class SentViewHolder(private val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: SmsEntry) {
            bindCommon(entry, binding.bubble, binding.ivAttachment, binding.tvBody, binding.tvDate)
        }
    }

    private fun bindCommon(
        entry: SmsEntry,
        bubble: View,
        image: ImageView,
        body: TextView,
        date: TextView
    ) {
        if (entry.attachmentUri != null) {
            image.visibility = View.VISIBLE
            image.setImageURI(entry.attachmentUri)
            image.setOnClickListener { onAttachmentClick(entry.attachmentUri) }
        } else {
            image.visibility = View.GONE
            image.setImageDrawable(null)
            image.setOnClickListener(null)
        }
        if (entry.body.isBlank()) {
            body.visibility = View.GONE
        } else {
            body.visibility = View.VISIBLE
            body.text = entry.body
        }
        date.text = SmsHelper.formatDate(entry.dateMs)
        bubble.setOnLongClickListener {
            onMessageLongClick(entry)
            true
        }
    }

    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is MessageItem.DayHeader -> VIEW_TYPE_DAY
        is MessageItem.Message   -> if (item.entry.isSent) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DAY  -> DayViewHolder(ItemDayHeaderBinding.inflate(inflater, parent, false))
            VIEW_TYPE_SENT -> SentViewHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
            else           -> ReceivedViewHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is MessageItem.DayHeader -> (holder as DayViewHolder).bind(item)
            is MessageItem.Message   -> when (holder) {
                is SentViewHolder     -> holder.bind(item.entry)
                is ReceivedViewHolder -> holder.bind(item.entry)
                else                  -> Unit
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_DAY = 0
        private const val VIEW_TYPE_RECEIVED = 1
        private const val VIEW_TYPE_SENT = 2

        private val DIFF = object : DiffUtil.ItemCallback<MessageItem>() {
            override fun areItemsTheSame(a: MessageItem, b: MessageItem): Boolean = when {
                a is MessageItem.DayHeader && b is MessageItem.DayHeader -> a.key == b.key
                a is MessageItem.Message && b is MessageItem.Message     -> a.key == b.key
                else -> false
            }
            override fun areContentsTheSame(a: MessageItem, b: MessageItem) = a == b
        }
    }
}
