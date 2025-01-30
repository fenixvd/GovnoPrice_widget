package com.fenixvd.govnowidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d("MyWidgetProvider", "onUpdate called with appWidgetIds: ${appWidgetIds.joinToString()}")

        // Обновляем все экземпляры виджета
        appWidgetIds.forEach { appWidgetId ->
            Log.d("MyWidgetProvider", "Calling updateWidget for appWidgetId: $appWidgetId")
            updateWidget(context, appWidgetManager, appWidgetId)
        }

        // Запускаем обновление каждые 30 секунд
        scheduleNextUpdate(context)
    }

    private fun scheduleNextUpdate(context: Context) {
        Log.d("MyWidgetProvider", "Scheduling next update using Handler")

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            Log.d("MyWidgetProvider", "Executing scheduled update")

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
            Log.d("MyWidgetProvider", "Scheduled next update for widget IDs: ${ids.joinToString()}")
        }, 30_000) // Интервал в 30 секунд (30_000 миллисекунд)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("MyWidgetProvider", "onReceive called with action: ${intent.action}")

        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            Log.d("MyWidgetProvider", "Handling ACTION_APPWIDGET_UPDATE")

            // Получаем ID всех экземпляров виджета
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            ids?.forEach { appWidgetId ->
                Log.d("MyWidgetProvider", "Updating widget for appWidgetId: $appWidgetId")
                updateWidget(context, appWidgetManager, appWidgetId)
            }

            // Планируем следующее обновление
            scheduleNextUpdate(context)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("MyWidgetProvider", "Starting updateWidget for appWidgetId: $appWidgetId")
            Log.d("MyWidgetProvider", "Package name: ${context.packageName}")

            val poolData = NetworkUtils.fetchPoolData()
            if (poolData == null || poolData.data.attributes.base_token_price_usd.isNullOrEmpty()) {
                Log.e("MyWidgetProvider", "Failed to fetch valid pool data for appWidgetId: $appWidgetId")
                withContext(Dispatchers.Main) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout).apply {
                        setTextViewText(R.id.price, "Error")
                        setTextViewText(R.id.price_rub, "Error")
                        setTextViewText(R.id.change, "Error")
                        setTextViewText(R.id.time, "Failed to update")
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
                return@launch
            }

            val usdPrice = formatUsdPrice(poolData.data.attributes.base_token_price_usd)
            val usdToRubRate = fetchUsdToRubRate()
            val rubPrice = formatRubPrice(poolData.data.attributes.base_token_price_usd, usdToRubRate)
            val change = poolData.data.attributes.price_change_percentage.h24
            val changeIcon = getChangeIcon(change)
            val changeColor = getChangeColor(context, change)
            val currentDateTime = getCurrentDateTime()

            withContext(Dispatchers.Main) {
                Log.d("MyWidgetProvider", "Updating UI for appWidgetId: $appWidgetId")

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
                Log.d("MyWidgetProvider", "Widget updated successfully for appWidgetId: $appWidgetId")
            }
        }
    }

    private fun formatUsdPrice(price: String): String {
        return try {
            val number = price.toDouble()
            "%.2f".format(number)
        } catch (e: Exception) {
            Log.e("MyWidgetProvider", "Error formatting USD price: ${e.message}")
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
            Log.e("MyWidgetProvider", "Error formatting RUB price: ${e.message}")
            "0.000"
        }
    }

    private suspend fun fetchUsdToRubRate(): Double {
        return try {
            val client = OkHttpClient() // Теперь эта строка работает благодаря добавленной зависимости
            val request = okhttp3.Request.Builder()
                .url("https://www.cbr-xml-daily.ru/daily_json.js")
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val json = JSONObject(responseBody) // Теперь эта строка работает благодаря импорту
                val usdRate = json.getJSONObject("Valute").getJSONObject("USD").getDouble("Value")
                Log.d("MyWidgetProvider", "USD to RUB rate fetched: $usdRate")
                usdRate
            } else {
                Log.e("MyWidgetProvider", "Failed to fetch USD to RUB rate")
                98.0 // Возвращаем значение по умолчанию в случае ошибки
            }
        } catch (e: Exception) {
            Log.e("MyWidgetProvider", "Exception while fetching USD to RUB rate: ${e.message}")
            98.0 // Возвращаем значение по умолчанию в случае ошибки
        }
    }

    private fun getChangeIcon(change: String): String {
        return try {
            val changeValue = change.toDouble()
            if (changeValue >= 0) "⬆" else "⬇"
        } catch (e: Exception) {
            Log.e("MyWidgetProvider", "Error determining change icon: ${e.message}")
            "⬆"
        }
    }

    private fun getChangeColor(context: Context, change: String): Int {
        return try {
            val changeValue = change.toDouble()
            if (changeValue >= 0) {
                ContextCompat.getColor(context, R.color.text_color_positive)
            } else {
                ContextCompat.getColor(context, R.color.text_color_negative)
            }
        } catch (e: Exception) {
            Log.e("MyWidgetProvider", "Error determining change color: ${e.message}")
            ContextCompat.getColor(context, R.color.text_color_negative)
        }
    }

    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date())
    }
}