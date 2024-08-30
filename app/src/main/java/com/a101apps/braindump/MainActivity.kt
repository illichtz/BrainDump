package com.a101apps.braindump

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var rootView: View
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var database: ChatDatabase
    private var editingMessage: ChatMessage? = null
    private var isKeyboardVisible = false
    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Handle night mode for navigation and status bar colors
        handleNightMode()

        // Initialize the menu button and set its click listener
        val menuButton: ImageButton = findViewById(R.id.menuButton)
        menuButton.setOnClickListener {
            openMenuActivity()
        }

        database = ChatDatabase.getDatabase(this)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        chatEditText = findViewById(R.id.chatEditText)
        sendButton = findViewById(R.id.sendButton)

        chatAdapter = ChatAdapter(chatMessages, object : ChatAdapter.MessageActions {
            override fun onEdit(message: ChatMessage) {
                startEditingMessage(message)
            }

            override fun onDelete(message: ChatMessage) {
                confirmDelete(message)
            }
        })
        chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        loadMessages()

        sendButton.setOnClickListener {
            val messageText = chatEditText.text.toString()
            if (messageText.isNotBlank()) {
                if (editingMessage != null) {
                    updateMessage(editingMessage!!.copy(text = messageText))
                    stopEditingMessage()
                } else {
                    val newMessage = ChatMessage(
                        id = System.currentTimeMillis().toString(),
                        text = messageText,
                        senderId = "userId",  // Replace with actual sender ID
                        timestamp = System.currentTimeMillis()
                    )
                    saveMessage(newMessage)
                    scrollToBottom() // Scroll to bottom after a new message is added
                }
                chatEditText.text.clear()
                editingMessage = null
                hideKeyboard()
            }
        }
        rootView = findViewById(android.R.id.content)
        setupKeyboardVisibilityListener()
    }

    private fun loadMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            val messages = database.chatMessageDao().getAllMessages()
            runOnUiThread {
                updateMessages(messages)
                scrollToBottom() // Scroll to bottom after messages are loaded
                Log.d("MainActivity", "Messages loaded and scrolled to bottom")
            }
        }
    }

    private fun scrollToBottom() {
        if (chatAdapter.itemCount > 0) {
            chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            Log.d("MainActivity", "RecyclerView scrolled to bottom")
        }
    }

    private fun setupKeyboardVisibilityListener() {
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height

            val keypadHeight = screenHeight - rect.bottom
            val isKeyboardNowVisible = keypadHeight > screenHeight * 0.15

            if (isKeyboardNowVisible != isKeyboardVisible) {
                isKeyboardVisible = isKeyboardNowVisible
                if (isKeyboardVisible && !isEditing) {
                    scrollToBottom()
                    Log.d("MainActivity", "Keyboard is visible, scrolled to bottom")
                } else {
                    Log.d("MainActivity", "Keyboard is hidden")
                }
            }
        }
    }

    private fun startEditingMessage(message: ChatMessage) {
        isEditing = true
        editingMessage = message
        chatEditText.setText(message.text)
        chatEditText.setSelection(message.text.length)
        // Change the send button icon to indicate edit mode if needed
        showKeyboard()

    }

    private fun stopEditingMessage() {
        isEditing = false
        hideKeyboard()
        chatEditText.text.clear()
        Log.d("MainActivity", "Stopped editing message")
    }

    private fun showKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(chatEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(chatEditText.windowToken, 0)
    }

    private fun openMenuActivity() {
        val intent = Intent(this, MenuActivity::class.java)
        startActivity(intent)
    }

    private fun updateMessage(message: ChatMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            database.chatMessageDao().updateMessage(message)
            val messages = database.chatMessageDao().getAllMessages()
            runOnUiThread {
                updateMessages(messages)
            }
        }
    }

    private fun confirmDelete(message: ChatMessage) {
        MaterialAlertDialogBuilder(this)
            .setMessage("Are you sure you want to delete this message?")
            .setPositiveButton("Delete") { _, _ -> deleteMessage(message) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMessage(message: ChatMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            database.chatMessageDao().deleteMessage(message)
            val messages = database.chatMessageDao().getAllMessages()
            runOnUiThread {
                updateMessages(messages)
                //  showUndoDeleteSnackbar(message)
            }
        }
    }

    private fun showUndoDeleteSnackbar(deletedMessage: ChatMessage) {
        val snackbar = Snackbar.make(
            chatRecyclerView, // Attach the Snackbar to the RecyclerView
            "Message deleted",
            Snackbar.LENGTH_LONG
        )
        snackbar.setAction("Undo") {
            undoDelete(deletedMessage)
        }
        snackbar.show()
    }

    private fun undoDelete(message: ChatMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            database.chatMessageDao().insertMessage(message)
            val messages = database.chatMessageDao().getAllMessages()
            runOnUiThread {
                updateMessages(messages)
            }
        }
    }

    private fun saveMessage(chatMessage: ChatMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            database.chatMessageDao().insertMessage(chatMessage)
            val messages = database.chatMessageDao().getAllMessages() // Fetch updated list
            runOnUiThread {
                updateMessages(messages)
            }
        }
    }

    private fun updateMessages(newMessages: List<ChatMessage>) {
        val updatedMessages = mutableListOf<ChatMessage>()
        var lastHeaderDate: Calendar? = null
        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        for (message in newMessages) {
            val messageCalendar = Calendar.getInstance().apply { timeInMillis = message.timestamp }
            if (lastHeaderDate == null || messageCalendar.get(Calendar.DAY_OF_YEAR) != lastHeaderDate.get(Calendar.DAY_OF_YEAR)) {
                val headerMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = dateFormatter.format(messageCalendar.time),
                    senderId = "",
                    timestamp = message.timestamp,
                    showDateHeader = true
                )
                updatedMessages.add(headerMessage)
                lastHeaderDate = messageCalendar
            }
            updatedMessages.add(message)
        }

        chatAdapter.updateMessages(updatedMessages)
    }

    // Function to handle night mode
    private fun handleNightMode() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val darkColor = ContextCompat.getColor(this, R.color.dark)
        val lightColor = ContextCompat.getColor(this, R.color.light)
        window.navigationBarColor = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) darkColor else lightColor
        window.statusBarColor = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) darkColor else lightColor

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val visibilityFlags = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else {
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            window.decorView.systemUiVisibility = visibilityFlags
        }
    }

}
