package com.fenixvd.govnowidget

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.io.IOException
import java.net.InetAddress

// Data class для API
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

// API интерфейс с suspend функцией
interface GeckoApiService {
    @GET("api/v2/networks/ton/pools/EQAf2LUJZMdxSAGhlp-A60AN9bqZeVM994vCOXH05JFo-7dc")
    suspend fun fetchPoolData(): PoolData?
}

object NetworkUtils {

    // Bootstrap клиент для DnsOverHttps
    private val bootstrapClient = OkHttpClient.Builder().build()

    // Настройка DnsOverHttps
    private val dnsOverHttps = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("1.0.0.1")
        )
        .build()

    // Настройка Retrofit
    private val retrofit: Retrofit by lazy {
        val userAgentInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0")
                .build()
            chain.proceed(newRequest)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .dns(dnsOverHttps)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.geckoterminal.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }

    private val apiService: GeckoApiService by lazy {
        retrofit.create(GeckoApiService::class.java)
    }

    // Функция получения данных из API
    suspend fun fetchPoolData(): PoolData? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("NetworkUtils", "Запрос к API...")
                val response = apiService.fetchPoolData() // Retrofit автоматически выполняет запрос
                response?.also {
                    Log.d("NetworkUtils", "Данные получены: ${it.data.attributes.base_token_price_usd}")
                }
            } catch (e: IOException) {
                Log.e("NetworkError", "Ошибка сети: ${e.message}", e)
                null
            } catch (e: Exception) {
                Log.e("NetworkError", "Неизвестная ошибка: ${e.message}", e)
                null
            }
        }
    }

    // Функция получения курса USD к RUB
    suspend fun fetchUsdToRubRate(): Double {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://www.cbr-xml-daily.ru/daily_json.js")
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    val json = org.json.JSONObject(responseBody)
                    val usdRate = json.getJSONObject("Valute").getJSONObject("USD").getDouble("Value")
                    Log.d("NetworkUtils", "USD to RUB rate fetched: $usdRate")
                    usdRate
                } else {
                    Log.e("NetworkError", "Failed to fetch USD to RUB rate")
                    97.0
                }
            } catch (e: IOException) {
                Log.e("NetworkError", "Ошибка сети: ${e.message}", e)
                97.0
            } catch (e: Exception) {
                Log.e("NetworkError", "Неизвестная ошибка: ${e.message}", e)
                97.0
            }
        }
    }
}