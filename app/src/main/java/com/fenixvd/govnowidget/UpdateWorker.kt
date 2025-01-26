package com.fenixvd.govnowidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Выполняем запрос к API
            val poolData = NetworkUtils.fetchPoolData()
            if (poolData != null) {
                // Обновляем виджет
                val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                val componentName = ComponentName(applicationContext, MyWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                // Получаем курс USD
                val usdPrice = formatUsdPrice(poolData.data.attributes.base_token_price_usd)

                // Получаем курс рубля (или другой валюты)
                val usdToRubRate = fetchUsdToRubRate() // Курс USD к RUB
                val rubPrice = formatRubPrice(poolData.data.attributes.base_token_price_usd, usdToRubRate)

                // Получаем изменение цены
                val change = poolData.data.attributes.price_change_percentage.h24
                val changeIcon = getChangeIcon(change)
                val changeColor = getChangeColor(change)

                // Получаем текущее время
                val currentDateTime = getCurrentDateTime()

                // Создаем RemoteViews и обновляем виджет
                val views = RemoteViews(applicationContext.packageName, R.layout.widget_layout).apply {
                    setTextViewText(R.id.ticker, "GOVNO")
                    setTextViewText(R.id.price, "$$usdPrice")
                    setTextViewText(R.id.price_rub, "$rubPrice ₽")
                    setTextViewText(R.id.change, "$changeIcon $change%")
                    setTextColor(R.id.change, changeColor) // Устанавливаем цвет изменения цены
                    setTextViewText(R.id.time, "Updated $currentDateTime")
                    setTextViewText(R.id.icon, "💩")
                }

                // Обновляем все экземпляры виджета
                withContext(Dispatchers.Main) {
                    appWidgetIds.forEach { widgetId ->
                        appWidgetManager.updateAppWidget(widgetId, views)
                    }
                }

                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun formatUsdPrice(price: String): String {
        return try {
            val number = price.toDouble()
            "%.2f".format(number)
        } catch (e: Exception) {
            "0.00"
        }
    }

    private fun formatRubPrice(usdPrice: String, usdToRubRate: Double): String {
        return try {
            // Заменяем запятую на точку, если она есть
            val normalizedPrice = usdPrice.replace(",", ".")
            val number = normalizedPrice.toDouble() // Преобразуем строку в число

            // Конвертируем цену из долларов в рубли
            val rubPrice = number * usdToRubRate

            // Форматируем число с тремя знаками после запятой
            "%.3f".format(rubPrice)
        } catch (e: Exception) {
            "0.000"
        }
    }

    private suspend fun fetchUsdToRubRate(): Double {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://www.cbr-xml-daily.ru/daily_json.js") // API ЦБ РФ
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                val json = JSONObject(responseBody)
                val usdRate = json.getJSONObject("Valute").getJSONObject("USD").getDouble("Value")
                usdRate
            } else {
                98.0 // Возвращаем значение по умолчанию в случае ошибки
            }
        } catch (e: Exception) {
            98.0 // Возвращаем значение по умолчанию в случае ошибки
        }
    }

    private fun getChangeIcon(change: String): String {
        return try {
            val changeValue = change.toDouble()
            if (changeValue >= 0) "⬆" else "⬇"
        } catch (e: Exception) {
            "⬆"
        }
    }

    private fun getChangeColor(change: String): Int {
        return try {
            val changeValue = change.toDouble()
            if (changeValue >= 0) {
                0xFF4CAF50.toInt() // Зелёный цвет
            } else {
                0xFFF44336.toInt() // Красный цвет
            }
        } catch (e: Exception) {
            0xFFF44336.toInt() // Красный цвет по умолчанию в случае ошибки
        }
    }

    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date())
    }
}