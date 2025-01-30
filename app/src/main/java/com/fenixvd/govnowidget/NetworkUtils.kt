package com.fenixvd.govnowidget

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.Call
import java.io.IOException
import java.net.InetAddress
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl

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

    private val handler = Handler(Looper.getMainLooper())
    private var isUpdating = false // Флаг для предотвращения множественных обновлений

    // Bootstrap клиент для DnsOverHttps
    private val bootstrapClient = OkHttpClient.Builder().build()

    // Настройка DnsOverHttps
    private val dnsOverHttps = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("1.1.1.1"), // Bootstrap DNS для Cloudflare
            InetAddress.getByName("1.0.0.1")
        )
        .build()

    // Настройка Retrofit
    private val retrofit: Retrofit by lazy {
        // User-Agent для запросов
        val userAgentInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0")
                .build()
            chain.proceed(newRequest)
        }

        // Логирование запросов (для отладки)
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Настройка OkHttpClient с DnsOverHttps
        val client = OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .dns(dnsOverHttps) // Используем DnsOverHttps
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

    // Функция для выполнения запроса (асинхронная с корутинами)
    suspend fun fetchPoolData(): PoolData? {
        return withContext(Dispatchers.IO) { // Выполняем запрос в фоновом потоке
            try {
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

    // Метод для запуска периодических обновлений
    fun startPeriodicUpdates(onUpdate: () -> Unit) {
        if (isUpdating) return // Предотвращаем множественные обновления
        isUpdating = true

        handler.postDelayed(object : Runnable {
            override fun run() {
                Log.d("NetworkUtils", "Executing periodic update")
                CoroutineScope(Dispatchers.Main).launch {
                    onUpdate.invoke() // Вызываем callback для обновления виджета
                }
                handler.postDelayed(this, 30_000) // Планируем следующее обновление
            }
        }, 30_000) // Первое обновление через 30 секунд
    }

    // Метод для остановки обновлений
    fun stopPeriodicUpdates() {
        handler.removeCallbacksAndMessages(null)
        isUpdating = false
    }
}