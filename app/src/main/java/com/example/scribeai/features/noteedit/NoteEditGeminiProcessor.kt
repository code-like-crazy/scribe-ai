package com.example.scribeai.features.noteedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.scribeai.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
data class GeminiOcrResponse(val extracted_notes_markdown: String)

interface GeminiProcessorCallback {
    fun onOcrSuccess(markdownText: String)
    fun onOcrError(message: String)
}

class NoteEditGeminiProcessor(
        private val context: Context,
        private val coroutineScope: CoroutineScope,
        private val loadingOverlay: ConstraintLayout,
        private val progressIndicator: CircularProgressIndicator,
        private val processingText: TextView,
        private val callback: GeminiProcessorCallback
) {

    private lateinit var generativeModel: GenerativeModel
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "NoteEditGeminiProcessor"
    }

    init {
        initializeGeminiModel()
    }

    private fun initializeGeminiModel() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) {
            Log.e(TAG, "Gemini API Key is missing.")
            callback.onOcrError("Error: Gemini API Key is missing.")
            return
        }

        val config = generationConfig {
            temperature = 0.2f
            responseMimeType = "application/json"
            stopSequences = listOf("---", "Note:", "Summary:", "Response:")
        }

        val safetySettings =
                listOf(
                        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.MEDIUM_AND_ABOVE),
                        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.MEDIUM_AND_ABOVE),
                        SafetySetting(
                                HarmCategory.SEXUALLY_EXPLICIT,
                                BlockThreshold.MEDIUM_AND_ABOVE
                        ),
                        SafetySetting(
                                HarmCategory.DANGEROUS_CONTENT,
                                BlockThreshold.MEDIUM_AND_ABOVE
                        )
                )

        val requestOptions = RequestOptions(timeout = 60_000L)

        try {
            generativeModel =
                    GenerativeModel(
                            modelName = "gemini-2.0-flash",
                            apiKey = apiKey,
                            generationConfig = config,
                            safetySettings = safetySettings,
                            requestOptions = requestOptions
                    )
            Log.d(TAG, "Gemini Model Initialized Successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gemini Model", e)
            callback.onOcrError("Failed to initialize AI model: ${e.message}")
        }
    }

    fun processImageForOcr(imageUri: Uri) {
        if (!::generativeModel.isInitialized) {
            callback.onOcrError("AI Model not initialized. Cannot process image.")
            return
        }

        Log.d(TAG, "Starting Gemini Vision process for URI: $imageUri")
        loadingOverlay.visibility = View.VISIBLE

        coroutineScope.launch {
            try {
                val bitmap = uriToBitmap(imageUri)
                if (bitmap == null) {
                    callback.onOcrError("Failed to load image.")
                    loadingOverlay.visibility = View.GONE
                    return@launch
                }

                val prompt =
                        """
                Analyze the image provided and extract all text content meticulously.
                Format the extracted text clearly and aesthetically as notes using Markdown syntax.
                Employ varied formatting suitable for notes, such as:
                - Paragraphs for descriptive text.
                - Bullet points (`- item`) or numbered lists (`1. item`) for lists.
                - Bold (`**bold**`) and italics (`*italic*`) for emphasis.
                - Headings (use H2 `##` or H3 `###` ONLY if appropriate for structure; AVOID H1 `#`).
                - Blockquotes (`> quote`) if quoting text.
                - Code blocks (```code```) if code is present.
                Identify any URLs or clear references within the extracted text and format them as embedded Markdown links (e.g., `[ScribeAI Website](https://scribeai.example.com)`).
                Focus solely on presenting the extracted information as well-structured Markdown notes.
                Do NOT include any introductory phrases, explanations, summaries, or conversational text outside the Markdown content itself.
                The output MUST be a JSON object matching the specified schema: `{"extracted_notes_markdown": "..."}`, containing only the extracted notes in Markdown format within the 'extracted_notes_markdown' field.
                """.trimIndent()

                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }

                Log.d(TAG, "Sending request to Gemini...")
                val response = generativeModel.generateContent(inputContent)

                Log.d(TAG, "Raw Gemini Response Text: ${response.text}")

                response.text?.let { jsonString ->
                    try {
                        val parsedResponse = json.decodeFromString<GeminiOcrResponse>(jsonString)
                        val markdownText = parsedResponse.extracted_notes_markdown

                        Log.d(TAG, "Gemini Success. Parsed Markdown length: ${markdownText.length}")
                        callback.onOcrSuccess(markdownText)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse Gemini JSON response", e)
                        if (jsonString.trimStart().startsWith("{")) {
                            Log.e(TAG, "Malformed/incomplete JSON received: $jsonString")
                            callback.onOcrError(
                                    "AI response was incomplete or malformed. Please try again."
                            )
                        } else {
                            Log.w(
                                    TAG,
                                    "Received non-JSON response when JSON was expected: $jsonString"
                            )
                            callback.onOcrError(
                                    "AI returned an unexpected response format. Please try again."
                            )
                        }
                    }
                }
                        ?: run {
                            val errorDetails =
                                    response.candidates?.firstOrNull()?.finishReason?.name
                                            ?: "Unknown reason"
                            val safetyRatings =
                                    response.candidates?.firstOrNull()
                                            ?.safetyRatings
                                            ?.joinToString { "${it.category}: ${it.probability}" }
                                            ?: "N/A"
                            Log.w(
                                    TAG,
                                    "Gemini response text was null. Finish Reason: $errorDetails, Safety Ratings: $safetyRatings"
                            )
                            callback.onOcrError("AI could not extract text. Reason: $errorDetails")
                        }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Vision Failed", e)
                callback.onOcrError("AI text extraction failed: ${e.message}")
            } finally {
                loadingOverlay.visibility = View.GONE
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: IOException) {
            Log.e(TAG, "IO Error converting Uri to Bitmap", e)
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception reading Uri", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Generic error converting Uri to Bitmap", e)
            null
        }
    }
}
