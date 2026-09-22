package app.nextsay.provider

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface OpenAiCompatibleApi {
    @POST
    suspend fun complete(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequestDto,
    ): Response<ChatCompletionResponseDto>
}
