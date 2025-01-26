package com.fenixvd.govnowidget

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.Call
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// Data classes
data class PoolData(
    val data: Data
) {
    data class Data(
        val attributes: Attributes
    ) {
        data class Attributes(
            val base_token_price_usd: String,
            val base_token_price_native_currency: String,
            val price_change_percentage: PriceChangePercentage
        ) {
            data class PriceChangePercentage(
                val h24: String
            )
        }
    }
}

// API interface
interface GeckoApiService {
    @Headers("Accept: application/json;version=20230302")
    @GET("api/v2/networks/ton/pools/EQAf2LUJZMdxSAGhlp-A60AN9bqZeVM994vCOXH05JFo-7dc")
    fun fetchPoolData(): Call<PoolData>
}

// NetworkUtils object
object NetworkUtils {

    private val retrofit: Retrofit by lazy {
        // Логирование запросов и ответов
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Логируем всё: заголовки и тело
        }

        // User-Agent для запросов
        val userAgentInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36")
                .build()
            chain.proceed(newRequest)
        }

        // Настройка OkHttpClient
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(userAgentInterceptor)
            .build()

        // Настройка Retrofit
        Retrofit.Builder()
            .baseUrl("https://api.geckoterminal.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }

    private val apiService: GeckoApiService by lazy {
        retrofit.create(GeckoApiService::class.java)
    }

    // Функция для выполнения запроса
    fun fetchPoolData(): PoolData? {
        return try {
            Log.d("NetworkUtils", "Starting request to API...")
            val response = apiService.fetchPoolData().execute()

            if (response.isSuccessful) {
                val poolData = response.body()
                Log.d("NetworkUtils", "Response received: ${Gson().toJson(poolData)}")
                poolData
            } else {
                Log.e("NetworkError", "Request failed with code: ${response.code()}, message: ${response.message()}")
                null
            }

        } catch (e: IOException) {
            Log.e("NetworkError", "IO Exception: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e("NetworkError", "Unexpected error: ${e.message}", e)
            null
        }
    }
}
