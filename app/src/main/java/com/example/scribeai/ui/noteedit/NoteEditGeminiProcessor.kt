package com.example.scribeai.ui.noteedit

// Removed jsonObject import as it's not used for schema definition here
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.scribeai.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable // Keep for data class
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// Define a data class for the structured JSON response
@Serializable data class GeminiOcrResponse(val extracted_notes_markdown: String)

// Callback interface for Gemini results
interface GeminiProcessorCallback {
    fun onOcrSuccess(markdownText: String)
    fun onOcrError(message: String)
}

class NoteEditGeminiProcessor(
        private val context: Context,
        private val lifecycleScope: LifecycleCoroutineScope,
        private val progressBar: ProgressBar,
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
            // Cannot proceed without API key
            return
        }

        // --- Structured Output Configuration ---
        val config = generationConfig {
            temperature = 0.2f
            responseMimeType = "application/json" // Keep requesting JSON
            // Remove the problematic responseSchema definition
            // responseSchema = ...
            stopSequences = listOf("---", "Note:", "Summary:", "Response:") // Keep stop sequences
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
                            modelName = "gemini-1.5-flash",
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
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val bitmap = uriToBitmap(imageUri)
                if (bitmap == null) {
                    callback.onOcrError("Failed to load image.")
                    progressBar.visibility = View.GONE
                    return@launch
                }

                // Updated prompt requesting Markdown, avoiding H1 (#), and fitting the structured
                // output schema
                val prompt =
                        """
                Analyze the image provided and extract all text content.
                Format the extracted text clearly as notes using Markdown syntax. Use list items (`- item`) and bold text (`**bold**`) for structure and emphasis.
                AVOID using single hash (#) for H1 headings as they are too large. If headings are necessary, use H2 (##) or H3 (###).
                Focus solely on presenting the extracted information as Markdown notes.
                Do NOT include any introductory phrases, explanations, summaries, or conversational text.
                The output MUST be a JSON object matching the specified schema, containing only the extracted notes in Markdown format within the 'extracted_notes_markdown' field.
                """.trimIndent()

                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }

                Log.d(TAG, "Sending request to Gemini...")
                val response = generativeModel.generateContent(inputContent)

                // Log the raw response text for debugging
                Log.d(TAG, "Raw Gemini Response Text: ${response.text}")

                response.text?.let { jsonString ->
                    try {
                        // Parse the JSON response using kotlinx.serialization
                        val parsedResponse = json.decodeFromString<GeminiOcrResponse>(jsonString)
                        val markdownText = parsedResponse.extracted_notes_markdown

                        Log.d(TAG, "Gemini Success. Parsed Markdown length: ${markdownText.length}")
                        callback.onOcrSuccess(markdownText)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse Gemini JSON response", e)
                        Log.w(TAG, "Using raw response text as fallback due to JSON parsing error.")
                        // Provide raw text but indicate it's not the expected format
                        callback.onOcrSuccess(
                                jsonString + "\n\n(Warning: AI response was not valid JSON.)"
                        )
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
                progressBar.visibility = View.GONE
            }
        }
    }

    // Helper function to convert Uri to Bitmap
    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error converting Uri to Bitmap", e)
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
