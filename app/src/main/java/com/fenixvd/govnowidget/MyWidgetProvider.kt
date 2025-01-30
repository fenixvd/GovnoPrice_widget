package com.fenixvd.govnowidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d("MyWidgetProvider", "onUpdate called with appWidgetIds: ${appWidgetIds.joinToString()}")

        // Обновляем все экземпляры виджета
        appWidgetIds.forEach { appWidgetId ->
            Log.d("MyWidgetProvider", "Calling updateWidget for appWidgetId: $appWidgetId")
            CoroutineScope(Dispatchers.Main).launch {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }

        // Запускаем периодические обновления
        NetworkUtils.startPeriodicUpdates {
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, MyWidgetProvider::class.java)
            )
            ids.forEach { appWidgetId ->
                CoroutineScope(Dispatchers.Main).launch {
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d("MyWidgetProvider", "onDisabled called, stopping periodic updates")
        // Останавливаем обновления, если виджет удалён
        NetworkUtils.stopPeriodicUpdates()
    }

    private suspend fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val poolData = NetworkUtils.fetchPoolData()
        if (poolData == null || poolData.data.attributes.base_token_price_usd.isNullOrEmpty()) {
            Log.e("MyWidgetProvider", "Failed to fetch valid pool data for appWidgetId: $appWidgetId")
            val views = RemoteViews(context.packageName, R.layout.widget_layout).apply {
                setTextViewText(R.id.price, "Error")
                setTextViewText(R.id.price_rub, "Error")
                setTextViewText(R.id.change, "Error")
                setTextViewText(R.id.time, "Failed to update")
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        val usdPrice = formatUsdPrice(poolData.data.attributes.base_token_price_usd)
        val usdToRubRate = fetchUsdToRubRate()
        val rubPrice = formatRubPrice(poolData.data.attributes.base_token_price_usd, usdToRubRate)
        val change = poolData.data.attributes.price_change_percentage.h24
        val changeIcon = getChangeIcon(change)
        val changeColor = getChangeColor(context, change)
        val currentDateTime = getCurrentDateTime()

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
            "%.2f".format(rubPrice)
        } catch (e: Exception) {
            Log.e("MyWidgetProvider", "Error formatting RUB price: ${e.message}")
            "0.00"
        }
    }

    private suspend fun fetchUsdToRubRate(): Double {
        return withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://www.cbr-xml-daily.ru/daily_json.js")
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    val json = org.json.JSONObject(responseBody)
                    val usdRate = json.getJSONObject("Valute").getJSONObject("USD").getDouble("Value")
                    Log.d("MyWidgetProvider", "USD to RUB rate fetched: $usdRate")
                    usdRate
                } else {
                    Log.e("MyWidgetProvider", "Failed to fetch USD to RUB rate")
                    98.0
                }
            } catch (e: Exception) {
                Log.e("MyWidgetProvider", "Exception while fetching USD to RUB rate: ${e.message}")
                98.0
            }
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