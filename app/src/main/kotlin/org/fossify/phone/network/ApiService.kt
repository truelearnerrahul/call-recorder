package org.fossify.phone.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
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
    @POST("upload-audio")
    suspend fun uploadAudio(
        @Part file: MultipartBody.Part
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
}

data class UploadResponse(
    val filename: String,
    val url: String
)
