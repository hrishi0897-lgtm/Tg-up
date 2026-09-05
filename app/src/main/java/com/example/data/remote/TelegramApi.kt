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
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface TelegramApi {

    @GET
    suspend fun getMe(
        @Url url: String
    ): Response<TelegramResponse<TelegramUser>>

    @FormUrlEncoded
    @POST
    suspend fun sendMessage(
        @Url url: String,
        @Field("chat_id") chatId: String,
        @Field("text") text: String
    ): Response<TelegramResponse<TelegramMessage>>

    @Multipart
    @POST
    suspend fun sendDocument(
        @Url url: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("caption") caption: RequestBody?,
        @Part document: MultipartBody.Part
    ): Response<TelegramResponse<TelegramMessage>>

    @GET
    suspend fun getFile(
        @Url url: String,
        @Query("file_id") fileId: String
    ): Response<TelegramResponse<TelegramRemoteFile>>

    @Streaming
    @GET
    suspend fun downloadFile(
        @Url url: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST
    suspend fun deleteMessage(
        @Url url: String,
        @Field("chat_id") chatId: String,
        @Field("message_id") messageId: Long
    ): Response<TelegramResponse<Boolean>>

    @GET
    suspend fun getUpdates(
        @Url url: String,
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Int? = 100
    ): Response<TelegramResponse<List<TelegramUpdate>>>
}
