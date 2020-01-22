package org.mozilla.kitfox

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView.OnEditorActionListener
import com.mozilla.speechlibrary.ISpeechRecognitionListener
import com.mozilla.speechlibrary.MozillaSpeechService
import com.mozilla.speechlibrary.MozillaSpeechService.SpeechState
import com.mozilla.speechlibrary.STTResult
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONException
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoResult.OnExceptionListener
import org.mozilla.geckoview.GeckoResult.OnValueListener
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.*

/**
 * Kitfox main activity.
 */
class MainActivity : Activity() {
    private lateinit var session: GeckoSession
    private lateinit var runtime: GeckoRuntime
    private lateinit var speechService: MozillaSpeechService
    private lateinit var chatArrayAdapter: ChatArrayAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Request record audio permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO),
                123
            )
        }
        // Request storage write permission
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                124
            )
        }
        session = GeckoSession()
        runtime = GeckoRuntime.create(this)
        session.open(runtime)
        geckoView.setSession(session)
        session.loadUri(HOME_PAGE)
        chatArrayAdapter = ChatArrayAdapter(applicationContext, R.layout.incoming_message)
        chatView.adapter = chatArrayAdapter

        /**
         * Navigate to URL or send message on submit.
         */
        urlBar?.setOnEditorActionListener(OnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val url = urlBar.text.toString()
                if (url.isEmpty()) {
                    return@OnEditorActionListener false
                }
                // Navigate to URL or submit chat message
                if (URLUtil.isValidUrl(url) && url.contains(".")) {
                    showWebView()
                    session.loadUri(url)
                } else if (URLUtil.isValidUrl("http://$url") && url.contains(".")) {
                    showWebView()
                    session.loadUri("http://$url")
                } else {
                    val message = urlBar.text.toString()
                    urlBar.setText("")
                    showChatView()
                    showChatMessage(false, message)
                    sendChatMessage(message)
                }
                // Blur URL bar and hide keyboard
                urlBar.clearFocus()
                val imm =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                return@OnEditorActionListener true
            }
            false
        })

        val speechListener = ISpeechRecognitionListener { aState, aPayload ->
            runOnUiThread {
                when (aState) {
                    SpeechState.DECODING -> {
                        // Handle when the speech object changes to decoding state
                        speakButton.setImageResource(R.drawable.speak_button)
                        speakButton.visibility = View.GONE
                        loadingSpinner.visibility = View.VISIBLE
                        Log.d(TAG, "*** Decoding...")
                    }
                    SpeechState.MIC_ACTIVITY -> {
                        // Captures the activity from the microphone
                        val db = - (aPayload as Double)
                        Log.d(TAG,"*** Mic activity: $db")
                    }
                    SpeechState.STT_RESULT -> {
                        // When the api finished processing and returned a hypothesis
                        loadingSpinner.visibility = View.GONE
                        speakButton.visibility = View.VISIBLE
                        val transcription = (aPayload as STTResult).mTranscription
                        val confidence = aPayload.mConfidence
                        Log.d(TAG, "*** Result: $transcription ($confidence)")
                        showChatView()
                        showChatMessage(false, transcription)
                        sendChatMessage(transcription)
                    }
                    SpeechState.START_LISTEN -> {
                        // Handle when the api successfully opened the microphone and started listening
                        speakButton.setImageResource(R.drawable.speak_button_active)
                        Log.d(TAG, "*** Listening...")
                    }
                    SpeechState.NO_VOICE ->  // Handle when the api didn't detect any voice
                        Log.d(TAG, "*** No voice detected.")
                    SpeechState.CANCELED -> {
                        // Handle when a cancellation was fully executed
                        speakButton.setImageResource(R.drawable.speak_button)
                        loadingSpinner.visibility = View.GONE
                        speakButton.visibility = View.VISIBLE
                        Log.d(TAG, "*** Cancelled.")
                    }
                    SpeechState.ERROR -> {
                        // Handle when any error occurred
                        speakButton.setImageResource(R.drawable.speak_button)
                        loadingSpinner.visibility = View.GONE
                        speakButton.visibility = View.VISIBLE
                        println("*** Error: $aPayload")
                    }
                    else -> {
                    }
                }
            }
        }
        speechService = MozillaSpeechService.getInstance()
        speechService.setLanguage("en-GB")
        speechService.addListener(speechListener)
        speakButton.setOnClickListener(View.OnClickListener {
            speechService.start(applicationContext)
            Log.d(TAG, "*** Speak button clicked")
        })
    }

    /**
     * Show web view and navigate to home page.
     *
     * @param view
     */
    fun goHome(view: View?) {
        showWebView()
        session.loadUri(HOME_PAGE)
        urlBar.setText("")
    }

    /**
     * Show web view.
     */
    private fun showWebView() {
        chatView.visibility = View.GONE
        geckoView.visibility = View.VISIBLE
    }

    /**
     * Show chat view.
     */
    private fun showChatView() {
        geckoView.visibility = View.GONE
        chatView.visibility = View.VISIBLE
    }

    /**
     * Show a message in the chat UI.
     */
    private fun showChatMessage(direction: Boolean, message: String) {
        chatArrayAdapter.add(ChatMessage(direction, message))
    }

    /**
     * Send a message to the Kitfox server.
     */
    private fun sendChatMessage(message: String) { // Build URL
        // Build body
        val properties: MutableMap<String?, String?> =  HashMap()
        properties["text"] = message
        val bodyObject = JSONObject(properties)
        val body = jsonObjectToByteBuffer(bodyObject)

        // Build request
        val builder = WebRequest.Builder(COMMANDS_URL)
        builder.method("POST")
        builder.header("Content-Type", "application/json")
        builder.body(body)
        val request = builder.build()

        // Send request
        val executor = GeckoWebExecutor(runtime)
        val result = executor.fetch(request)

        // Handle response
        result.then(OnValueListener<WebResponse, Void> { response ->
            if (response != null) handleMessageResponse(response)
            null
        }, OnExceptionListener {
            Log.e("Kitfox", "Exception with response from server")
            null
        })
    }


    /**
     * Handle a response from the Kitfox server.
     *
     * @param response WebResponse received.
     */
    private fun handleMessageResponse(response: WebResponse) { // Detect error responses
        if (response.statusCode != 200) {
            Log.e(TAG, "Received bad response from server " + response.statusCode)
            return
        }

        // Parse response
        val jsonResponse = inputStreamToJsonObject(response.body)

        val method = try {
            jsonResponse?.getString("method")
        } catch (exception: JSONException) {
            null
        }

        val url = try {
            jsonResponse?.getString("url")
        } catch (exception: JSONException) {
            null
        }

        val text = try {
            jsonResponse?.getString("text")
        } catch (exception: JSONException) {
            null
        }

        // If a textual response is provided, show it in chat
        if (text != null && text.isNotEmpty()) {
            showChatMessage(true, text)
        } else {
            Log.d(TAG, "No text response provided by server.")
        }

        if (method == null || url == null) {
            Log.d(TAG, "No method or URL provided by server.")
            return
        }

        // If a GET action with a URL is provided, navigate to it
        if (method.toUpperCase() == "GET" && url.isNotEmpty()) {
            session.loadUri(url)
            // Wait a couple of seconds before showing the WebView
            object : CountDownTimer(GET_URL_DELAY, GET_URL_DELAY) {
                override fun onTick(m: Long) {}
                override fun onFinish() {
                    showWebView()
                }
            }.start()
        }
    }

    /**
     * Convert a JSONObject to a ByteBuffer.
     *
     * @param object A JSONObject to be converted
     * @return ByteBuffer A ByteBuffer to be sent via fetch()
     */
    private fun jsonObjectToByteBuffer(obj: JSONObject): ByteBuffer {
        val charBuffer = CharBuffer.wrap(obj.toString())
        val byteBuffer = ByteBuffer.allocateDirect(charBuffer.length)
        Charset.forName("UTF-8").newEncoder().encode(charBuffer, byteBuffer, true)
        return byteBuffer
    }

    /**
     * Convert an InputStream to a JSONObject
     *
     * @param inputStream The InputStream to convert
     * @return JSONObject
     */
    private fun inputStreamToJsonObject(inputStream: InputStream?): JSONObject? {
        val streamReader = inputStream?.bufferedReader()
        val inputString = streamReader?.use(BufferedReader::readText)

        // Try to parse string as JSON
        return try {
            JSONObject(inputString)
        } catch (exception: JSONException) {
            Log.e(TAG, "Invalid JSON")
            null
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val HOME_PAGE = "http://kitfox.tola.me.uk"
        private const val KITFOX_SERVER = "http://kitfox.tola.me.uk"
        private const val COMMANDS_URL = "$KITFOX_SERVER/commands"
        private const val GET_URL_DELAY = 2000L // ms
    }
}