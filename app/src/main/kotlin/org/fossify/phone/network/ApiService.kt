package org.fossify.phone.network

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("upload-audio")
    suspend fun uploadAudio(
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>
}

data class UploadResponse(
    val filename: String,
    val url: String
)
