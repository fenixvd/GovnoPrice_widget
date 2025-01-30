package com.fenixvd.govnowidget

import android.util.Log
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.Call
import java.io.IOException

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
    @GET("api/v2/networks/ton/pools/EQAf2LUJZMdxSAGhlp-A60AN9bqZeVM994vCOXH05JFo-7dc")
    fun fetchPoolData(): Call<PoolData>
}

// NetworkUtils object
object NetworkUtils {
    private val retrofit: Retrofit by lazy {
        // User-Agent для запросов
        val userAgentInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0")
                .build()
            chain.proceed(newRequest)
        }

        // Настройка OkHttpClient
        val client = OkHttpClient.Builder()
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