package com.a101apps.braindump

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

class MenuActivity : AppCompatActivity() {
    companion object {
        private const val CREATE_FILE_REQUEST_CODE = 1
    }

    private var csvData: String? = null
    private lateinit var progressBar: ProgressBar
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        // Handle night mode for navigation and status bar colors
        handleNightMode()

        setupToolbar()
        setupExportButton()
        progressBar = findViewById(R.id.progressBar) // Assuming you have a ProgressBar with id progressBar in your layout
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_back_white) // Set your back icon drawable here
            title = getString(R.string.settings)

            // Use theme color for title text
            toolbar.setTitleTextColor(ContextCompat.getColor(this@MenuActivity, R.color.toolbar_text_color))
        }

        // Tint the back icon to adapt to the current theme
        val backIcon = toolbar.navigationIcon
        backIcon?.setTint(ContextCompat.getColor(this@MenuActivity, R.color.toolbar_icon_color))
        toolbar.navigationIcon = backIcon

        // Listener for the back button
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish() // Close the current activity
        return true
    }

    private fun setupExportButton() {
        findViewById<Button>(R.id.exportButton).setOnClickListener {
            exportChatMessagesToCSV()
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun exportChatMessagesToCSV() {
        coroutineScope.launch {
            val db = ChatDatabase.getDatabase(applicationContext)
            val messages = withContext(Dispatchers.IO) {
                db.chatMessageDao().getAllMessages()
            }
            val csvData = convertToCSV(messages)

            // Attempt to write to the Downloads directory
            withContext(Dispatchers.IO) {
                try {
                    // Get the Downloads directory
                    val downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                    // Check if the directory exists or attempt to create it
                    if (!downloadsPath.exists() && !downloadsPath.mkdirs()) {
                        throw IOException("Cannot create or access the Downloads directory")
                    }

                    // Get current date and time
                    val dateFormat = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
                    val currentTime = dateFormat.format(Date())

                    // Specify the file name and path with date and time
                    val file = File(downloadsPath, "BrainDump_export_$currentTime.csv")

                    // Write the data to the file
                    file.writeText(csvData ?: "")

                    withContext(Dispatchers.Main) {
                        // Notify the user of success
                        Toast.makeText(this@MenuActivity, "Exported to Downloads: ${file.name}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        // Notify the user of the failure
                        Toast.makeText(this@MenuActivity, "Failed to export data. Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun convertToCSV(messages: List<ChatMessage>): String {
        val header = "ID,Text,SenderId,Timestamp\n"
        return messages.joinToString(
            separator = "\n",
            prefix = header
        ) { message ->
            val text = "\"${message.text.replace("\"", "\"\"")}\""
            "${message.id},$text,${message.senderId},${formatTimestamp(message.timestamp)}"
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                showLoading(true) // Show loading icon when file location is selected
                exportData(uri)
            }
        }
    }

    private fun exportData(uri: Uri) {
        coroutineScope.launch {
            val db = ChatDatabase.getDatabase(applicationContext)
            val messages = withContext(Dispatchers.IO) {
                db.chatMessageDao().getAllMessages()
            }
            val csvData = convertToCSV(messages)
            writeToFile(uri, csvData)
        }
    }

    private fun writeToFile(uri: Uri, data: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                var success = false
                contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { fos ->
                        fos.write(data.toByteArray())
                        success = true
                    }
                }

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@MenuActivity, "Successfully exported to the selected location.", Toast.LENGTH_LONG).show()
                    } else {
                        throw IOException("Failed to write to the selected file.")
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MenuActivity, "Failed to export data. Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    showLoading(false) // Hide loading icon when operation is finished
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel() // Cancel the scope when the activity is destroyed
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
