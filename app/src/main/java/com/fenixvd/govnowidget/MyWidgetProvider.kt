package com.fenixvd.govnowidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class MyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateWidget(context, appWidgetManager, appWidgetIds)
        scheduleUpdateWorker(context)
    }

    private fun scheduleUpdateWorker(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // Создаем периодическую задачу с интервалом
        val updateWorkRequest = PeriodicWorkRequest.Builder(
            UpdateWorker::class.java,
            1, TimeUnit.MINUTES
        ).build()

        // Запускаем задачу с уникальным именем
        workManager.enqueueUniquePeriodicWork(
            "UpdateWidgetWork",
            ExistingPeriodicWorkPolicy.KEEP,
            updateWorkRequest
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.fenixvd.govnowidget.ACTION_UPDATE") {
            // Обновляем виджет вручную
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, MyWidgetProvider::class.java)
            )
            updateWidget(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val poolData = NetworkUtils.fetchPoolData()
            if (poolData != null) {
                val usdPrice = formatUsdPrice(poolData.data.attributes.base_token_price_usd)
                val usdToRubRate = fetchUsdToRubRate() // Получаем курс доллара к рублю
                val rubPrice = formatRubPrice(poolData.data.attributes.base_token_price_usd, usdToRubRate)
                val change = poolData.data.attributes.price_change_percentage.h24
                val changeIcon = getChangeIcon(change)
                val changeColor = getChangeColor(change) // Получаем цвет для стрелочки
                val currentDateTime = getCurrentDateTime()

                withContext(Dispatchers.Main) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout).apply {
                        setTextViewText(R.id.ticker, "GOVNO")
                        setTextViewText(R.id.price, "$$usdPrice")
                        setTextViewText(R.id.price_rub, "$rubPrice ₽") // Форматируем рубли в нужный формат
                        setTextViewText(R.id.change, "$changeIcon $change%")
                        setTextColor(R.id.change, changeColor) // Устанавливаем цвет для текста с изменением цены
                        setTextViewText(R.id.time, "Updated $currentDateTime")
                        setTextViewText(R.id.icon, "💩")
                    }

                    // Настройка PendingIntent для кнопки обновления
                    setUpdateOnClick(context, views)

                    appWidgetIds.forEach { widgetId ->
                        appWidgetManager.updateAppWidget(widgetId, views)
                    }
                }
            }
        }
    }

    private fun setUpdateOnClick(context: Context, views: RemoteViews) {
        // Создаем Intent для обновления виджета
        val intent = Intent(context, MyWidgetProvider::class.java).apply {
            action = "com.fenixvd.govnowidget.ACTION_UPDATE" // Уникальное действие для обновления
        }

        // Создаем PendingIntent
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Назначаем PendingIntent на кнопку
        views.setOnClickPendingIntent(R.id.icon, pendingIntent)
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