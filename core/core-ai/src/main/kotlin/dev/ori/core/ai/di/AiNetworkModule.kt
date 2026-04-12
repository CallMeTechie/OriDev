package dev.ori.core.ai.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.core.ai.ClaudeApiService
import dev.ori.core.ai.ClaudeApiServiceImpl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiNetworkModule {

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 120L
    private const val CALL_TIMEOUT_SECONDS = 180L

    @Provides
    @Singleton
    @ClaudeHttpClient
    fun provideClaudeOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @ClaudeMoshi
    fun provideClaudeMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideClaudeApiService(
        @ClaudeHttpClient okHttpClient: OkHttpClient,
        @ClaudeMoshi moshi: Moshi,
    ): ClaudeApiService = ClaudeApiServiceImpl(okHttpClient, moshi)
}

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeHttpClient

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeMoshi
