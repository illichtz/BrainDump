package com.a101apps.braindump

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    internal var messages: MutableList<ChatMessage>,
    private val messageActions: MessageActions
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val viewTypeMessage = 0
    private val viewTypeDateHeader = 1

    interface MessageActions {
        fun onEdit(message: ChatMessage)
        fun onDelete(message: ChatMessage)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].showDateHeader) viewTypeDateHeader else viewTypeMessage
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == viewTypeDateHeader) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_date_header, parent, false)
            DateHeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
            ChatMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DateHeaderViewHolder -> if (messages[position].showDateHeader) {
                holder.bind(messages[position])
            }
            is ChatMessageViewHolder -> {
                holder.bind(messages[position])
                holder.itemView.setOnLongClickListener {
                    // Pass the context from the itemView to the method
                    showEditDeleteOptions(holder.itemView.context, messages[position])
                    true
                }
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    fun updateMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    private fun showEditDeleteOptions(context: Context, message: ChatMessage) {
        // Custom layout for the dialog
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_delete_options, null)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        // Set dialog width and height
        dialog.setOnShowListener {
            val width = (context.resources.displayMetrics.widthPixels * 0.8).toInt() // 80% of screen width
            val height = ViewGroup.LayoutParams.WRAP_CONTENT
            dialog.window?.setLayout(width, height)
        }

        // Set up options
        val editOption = dialogView.findViewById<TextView>(R.id.option_edit)
        val deleteOption = dialogView.findViewById<TextView>(R.id.option_delete)
        val copyOption = dialogView.findViewById<TextView>(R.id.option_copy)

        editOption.setOnClickListener {
            messageActions.onEdit(message)
            dialog.dismiss()
        }
        deleteOption.setOnClickListener {
            messageActions.onDelete(message)
            dialog.dismiss()
        }
        copyOption.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Message", message.text)
            clipboard.setPrimaryClip(clip)
            dialog.dismiss()
        }

        dialog.show()
    }

    // ViewHolder for chat messages
    class ChatMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.messageText)

        fun bind(chatMessage: ChatMessage) {
            messageText.text = chatMessage.text
            // Additional binding logic for chat message
        }
    }

    // ViewHolder for date headers
    class DateHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateText: TextView = view.findViewById(R.id.dateText)

        fun bind(chatMessage: ChatMessage) {
            val date = Date(chatMessage.timestamp)
            val formatter = SimpleDateFormat("dd MMM, EEEE", Locale.getDefault())
            dateText.text = "--- ${formatter.format(date)} ---"
        }
    }
}
