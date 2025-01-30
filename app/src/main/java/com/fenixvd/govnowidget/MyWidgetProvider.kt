package com.fenixvd.govnowidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
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
import android.os.Handler
import android.os.Looper

class MyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Обновляем все экземпляры виджета
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
        // Запускаем обновление каждые 30 секунд
        scheduleNextUpdate(context)
    }

    private fun scheduleNextUpdate(context: Context) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            // Создаем Intent для обновления виджета
            val intent = Intent(context, MyWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            // Получаем ID всех экземпляров виджета
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, MyWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            // Отправляем broadcast для обновления виджета
            context.sendBroadcast(intent)
        }, 30_000) // Интервал в 30 секунд (30_000 миллисекунд)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            // Получаем ID всех экземпляров виджета
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            ids?.forEach { appWidgetId ->
                updateWidget(context, appWidgetManager, appWidgetId)
            }
            // Планируем следующее обновление
            scheduleNextUpdate(context)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val poolData = NetworkUtils.fetchPoolData()
            if (poolData != null) {
                // Форматируем данные
                val usdPrice = formatUsdPrice(poolData.data.attributes.base_token_price_usd)
                val usdToRubRate = fetchUsdToRubRate()
                val rubPrice = formatRubPrice(poolData.data.attributes.base_token_price_usd, usdToRubRate)
                val change = poolData.data.attributes.price_change_percentage.h24
                val changeIcon = getChangeIcon(change)
                val changeColor = getChangeColor(change)
                val currentDateTime = getCurrentDateTime()

                withContext(Dispatchers.Main) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout).apply {
                        setTextViewText(R.id.ticker, "\$GOVNO")
                        setTextViewText(R.id.price, "$$usdPrice")
                        setTextViewText(R.id.price_rub, "$rubPrice ₽")
                        setTextViewText(R.id.change, "$changeIcon $change%")
                        setTextColor(R.id.change, changeColor)
                        setTextViewText(R.id.time, "Updated $currentDateTime")
                        setTextViewText(R.id.icon, "💩")
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } else {
                withContext(Dispatchers.Main) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout).apply {
                        setTextViewText(R.id.price, "Error")
                        setTextViewText(R.id.price_rub, "Error")
                        setTextViewText(R.id.change, "Error")
                        setTextViewText(R.id.time, "Failed to update")
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
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
            val normalizedPrice = usdPrice.replace(",", ".")
            val number = normalizedPrice.toDouble()
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
                .url("https://www.cbr-xml-daily.ru/daily_json.js")
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
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
            if (changeValue >= 0) Color.GREEN else Color.RED
        } catch (e: Exception) {
            Color.RED
        }
    }

    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date())
    }
}