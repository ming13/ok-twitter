package com.github.ming13.oktwitter.dependency.module

import android.content.Context
import com.github.ming13.oktwitter.dependency.annotation.AsyncScheduler
import com.github.ming13.oktwitter.dependency.annotation.ViewScheduler
import com.github.ming13.oktwitter.repository.http.HttpAuthenticator
import com.github.ming13.oktwitter.repository.http.HttpLogger
import com.github.ming13.oktwitter.repository.api.StreamingApi
import com.github.ming13.oktwitter.repository.json.TweetDeserializer
import com.github.ming13.oktwitter.repository.model.Tweet
import com.github.ming13.oktwitter.storage.TwitterEndpoints
import com.github.ming13.oktwitter.storage.TwitterKeys
import com.github.ming13.oktwitter.util.Android
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.CallAdapter
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import rx.Scheduler
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File
import javax.inject.Singleton

@Module
class RepositoryModule
{
    @Provides @Singleton
    fun provideStreamingApi(endpoint: String, httpCaller: Call.Factory, jsonConverter: Converter.Factory, asyncCaller: CallAdapter.Factory): StreamingApi {
        val apiCreator = Retrofit.Builder()
            .baseUrl(endpoint)
            .callFactory(httpCaller)
            .addConverterFactory(jsonConverter)
            .addCallAdapterFactory(asyncCaller)
            .build()

        return apiCreator.create(StreamingApi::class.java)
    }

    @Provides
    fun provideEndpoint(): String {
        return TwitterEndpoints().streamingApi()
    }

    @Provides
    fun provideHttpCaller(httpCache: Cache): Call.Factory {
        val callerBuilder = OkHttpClient.Builder()

        callerBuilder.cache(httpCache)

        // For some reason injecting List of interceptors using @Provides method does not work.

        val httpInterceptors = listOf(
            createAuthenticationInterceptor(),
            createLoggingInterceptor()
        )

        httpInterceptors.forEach { interceptor ->
            callerBuilder.addInterceptor(interceptor)
        }

        return callerBuilder.build()
    }

    @Provides
    fun provideHttpCache(context: Context): Cache {
        val cacheFile = File(context.cacheDir, "http-cache")

        return Cache(cacheFile, 10 * 1024 * 1024)
    }

    private fun createAuthenticationInterceptor(): Interceptor {
        return HttpAuthenticator(TwitterKeys())
    }

    private fun createLoggingInterceptor(): Interceptor {
        val interceptor = HttpLoggingInterceptor(HttpLogger())

        if (Android.isDebugging()) {
            interceptor.level = HttpLoggingInterceptor.Level.HEADERS
        } else {
            interceptor.level = HttpLoggingInterceptor.Level.NONE
        }

        return interceptor
    }

    @Provides
    fun provideJsonConverter(jsonReader: Gson): Converter.Factory {
        return GsonConverterFactory.create(jsonReader)
    }

    @Provides @Singleton
    fun provideJsonReader(): Gson {
        val jsonReaderBuilder = GsonBuilder()

        jsonReaderBuilder.registerTypeAdapter(Tweet::class.java, TweetDeserializer())

        return jsonReaderBuilder.create()
    }

    @Provides
    fun provideAsyncCaller(@AsyncScheduler asyncScheduler: Scheduler): CallAdapter.Factory {
        return RxJavaCallAdapterFactory.createWithScheduler(asyncScheduler)
    }

    @Provides @AsyncScheduler
    fun provideAsyncScheduler(): Scheduler {
        return Schedulers.io()
    }

    @Provides @ViewScheduler
    fun provideViewScheduler(): Scheduler {
        return AndroidSchedulers.mainThread()
    }
}