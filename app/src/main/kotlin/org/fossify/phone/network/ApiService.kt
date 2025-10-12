package org.fossify.phone.network

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class LoginRequest(
    val username: String,
    val password: String
)

data class SignupRequest(
    val email: String,
    val password: String,
    val first_name: String,
    val last_name: String
)

data class Token(
    val access_token: String,
    val token_type: String,
    val customer: Customer
)

data class Customer(
    val id: Int,
    val email: String,
    val name: String,
    val is_active: Boolean
)

data class GoogleAuthRequest(
    @SerializedName("token") val token: String
)

interface ApiService {
    @Multipart
    @POST("api/v1/call_analysis/upload-audio")
    suspend fun uploadAudio(
        @Part file: MultipartBody.Part,
        @Part("customer_id") customerId: RequestBody
    ): Response<UploadResponse>

    @FormUrlEncoded
    @POST("customer/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<Token>

    @POST("customer/signup")
    suspend fun signup(
        @Body signupRequest: SignupRequest
    ): Response<Token>

    @POST("auth/google-auth")
    suspend fun googleAuth(
        @Body googleAuthRequest: GoogleAuthRequest
    ): Response<Token>

    @POST("api/v1/call_analysis/do_analysis")
    suspend fun doCallAnalysis(
        @Body request: CallAnalysisRequest
    ): Response<CallAnalysisResponse>
}

data class UploadResponse(
    val status: Boolean,
    val message: String,
    val filename: String,
    val key: String,
    val customer_id: Int,
    val call_details_id: Int
)

data class CallDetails(
    val id: Int,
    val call_metadata: Any?,
    val transcribe_detail: Any?,
    val sentiment_detail: Any?,
    val story: Any?,
    val final_output: Any?,
    val participants_name: Any?,
    val created_date: String?,
    val created_by: Int?,
    val s3_path: String?,
    val filename: String?,
    val knowledge_filename: String?,
    val service_name: String?
)

data class CallAnalysisRequest(
    val fileName: String,
    val callDetailsId: Int
)

data class CallAnalysisResponse(
    val status: String,
    val message: String,
    val data: CallAnalysisData
)

data class CallAnalysisData(
    val transcribe: Any,
    val sentiment: Any,
    val response: Any,
)

