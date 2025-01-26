package com.fenixvd.govnowidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

                val usdPrice = formatUsdPrice(poolData.data.attributes.base_token_price_usd)
                val rubPrice = formatRubPrice(poolData.data.attributes.base_token_price_native_currency)
                val change = poolData.data.attributes.price_change_percentage.h24
                val changeIcon = getChangeIcon(change)
                val currentDateTime = getCurrentDateTime()

                val views = RemoteViews(applicationContext.packageName, R.layout.widget_layout).apply {
                    setTextViewText(R.id.ticker, "GOVNO")
                    setTextViewText(R.id.price, "$$usdPrice")
                    setTextViewText(R.id.price_rub, "$rubPrice ₽")
                    setTextViewText(R.id.change, "$changeIcon $change%")
                    setTextViewText(R.id.time, "Updated $currentDateTime")
                    setTextViewText(R.id.icon, "💩")
                }

                appWidgetIds.forEach { widgetId ->
                    appWidgetManager.updateAppWidget(widgetId, views)
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

    private fun formatRubPrice(price: String): String {
        return try {
            val number = price.toDouble()
            "%.3f".format(number)
        } catch (e: Exception) {
            "0.000"
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

    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date())
    }
}