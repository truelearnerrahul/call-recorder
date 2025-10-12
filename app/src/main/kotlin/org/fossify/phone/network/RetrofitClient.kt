package org.fossify.phone.network

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.fossify.phone.helpers.AuthHelper
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // For Android Emulator, use 10.0.2.2 to access localhost
    private const val BASE_URL = "http://10.49.18.46:8000/"  // Keep trailing slash
    private var apiService: ApiService? = null

    fun getApiService(): ApiService {
        return getClient()
    }

    private fun getClient(): ApiService {
        if (apiService == null) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(5, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit.create(ApiService::class.java)
        }
        return apiService!!
    }

    suspend fun uploadAudioFile(context: Context, uri: Uri): Result<UploadResponse> {
        var tempFile: File? = null
        return try {
            val customerId = AuthHelper.getUserId(context)
            if (customerId == -1) {
                Log.e("Upload", "Customer ID not found. Please login again.")
                Toast.makeText(context, "Customer ID not found. Please login again.", Toast.LENGTH_SHORT).show()
                return Result.failure(Exception("Upload failed: Customer ID not found. Please login again."))
            }

            // Create a temporary file from the URI
            tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(Exception("Failed to open input stream for URI"))

            // Detect MIME type
            val mimeType = context.contentResolver.getType(uri) ?: "audio/*"
            Log.d("RetrofitClient", "Uploading file: ${tempFile.name}, MIME type: $mimeType")

            // Create request body
            val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
            val customerIdBody = customerId.toString().toRequestBody("text/plain".toMediaTypeOrNull())

            // Make the API call
            val response = getClient().uploadAudio(body, customerIdBody)

            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorMsg = response.errorBody()?.string()
                Log.e("RetrofitClient", "Upload failed: ${response.code()} - $errorMsg")
                Result.failure(Exception("Upload failed: ${response.code()} - $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e("RetrofitClient", "Error uploading file", e)
            Result.failure(e)
        } finally {
            // Always delete temp file
            tempFile?.delete()
        }
    }

    suspend fun triggerCallAnalysis(
        uploadResponse: UploadResponse,
    ): Result<CallAnalysisResponse> {
        return try {
            val callDetailsId = uploadResponse.call_details_id
            val fileKey = uploadResponse.key

            if (callDetailsId == null) {
                return Result.failure(Exception("callDetailsId not found in upload response"))
            }

            val request = CallAnalysisRequest(
                fileName = fileKey,
                callDetailsId = callDetailsId
            )

            val response = getClient().doCallAnalysis(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string()
                Log.e("RetrofitClient", "Call analysis failed: ${response.code()} - $errorMsg")
                Result.failure(Exception("Call analysis failed: ${response.code()} - $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e("RetrofitClient", "Error triggering call analysis", e)
            Result.failure(e)
        }
    }

}
