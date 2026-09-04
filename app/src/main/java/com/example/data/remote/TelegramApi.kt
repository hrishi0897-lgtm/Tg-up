package com.example.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface TelegramApi {

    @GET("bot{token}/getMe")
    suspend fun getMe(
        @Path("token") token: String
    ): Response<TelegramResponse<TelegramUser>>

    @FormUrlEncoded
    @POST("bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path("token") token: String,
        @Field("chat_id") chatId: String,
        @Field("text") text: String
    ): Response<TelegramResponse<TelegramMessage>>

    @Multipart
    @POST("bot{token}/sendDocument")
    suspend fun sendDocument(
        @Path("token") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("caption") caption: RequestBody?,
        @Part document: MultipartBody.Part
    ): Response<TelegramResponse<TelegramMessage>>

    @GET("bot{token}/getFile")
    suspend fun getFile(
        @Path("token") token: String,
        @Query("file_id") fileId: String
    ): Response<TelegramResponse<TelegramRemoteFile>>

    @Streaming
    @GET("file/bot{token}/{filePath}")
    suspend fun downloadFile(
        @Path("token") token: String,
        @Path("filePath", encoded = true) filePath: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("bot{token}/deleteMessage")
    suspend fun deleteMessage(
        @Path("token") token: String,
        @Field("chat_id") chatId: String,
        @Field("message_id") messageId: Long
    ): Response<TelegramResponse<Boolean>>

    @GET("bot{token}/getUpdates")
    suspend fun getUpdates(
        @Path("token") token: String,
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Int? = 100
    ): Response<TelegramResponse<List<TelegramUpdate>>>
}
