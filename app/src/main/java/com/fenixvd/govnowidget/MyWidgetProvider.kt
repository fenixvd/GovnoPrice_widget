package com.fenixvd.govnowidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyWidgetProvider : AppWidgetProvider() {

    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d("MyWidgetProvider", "onUpdate called with appWidgetIds: ${appWidgetIds.joinToString()}")

        // Обновляем все экземпляры виджета
        appWidgetIds.forEach { appWidgetId ->
            CoroutineScope(Dispatchers.Main).launch {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }

        // Запускаем периодические обновления
        scheduleNextUpdate(context, appWidgetManager, appWidgetIds)
    }

    private fun scheduleNextUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateRunnable?.let { handler.removeCallbacks(it) }

        updateRunnable = Runnable {
            appWidgetIds.forEach { appWidgetId ->
                CoroutineScope(Dispatchers.Main).launch {
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            }
            scheduleNextUpdate(context, appWidgetManager, appWidgetIds)
        }

        handler.postDelayed(updateRunnable!!, 30_000)
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
        val usdToRubRate = NetworkUtils.fetchUsdToRubRate()
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

            // Добавляем PendingIntent для обработки кликов по кнопке
            val intent = Intent(context, MyWidgetProvider::class.java).apply {
                action = "com.fenixvd.govnowidget.UPDATE_WIDGET"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId, // Уникальный requestCode для каждого виджета
                intent,
                pendingIntentFlags
            )
            setOnClickPendingIntent(R.id.icon, pendingIntent)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d("MyWidgetProvider", "Widget updated successfully for appWidgetId: $appWidgetId")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            "com.fenixvd.govnowidget.UPDATE_WIDGET" -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Log.d("MyWidgetProvider", "Manual update triggered for appWidgetId: $appWidgetId")
                    CoroutineScope(Dispatchers.Main).launch {
                        updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
                    }
                }
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
            "%.2f".format(rubPrice)
        } catch (e: Exception) {
            Log.e("MyWidgetProvider", "Error formatting RUB price: ${e.message}")
            "0.00"
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

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d("MyWidgetProvider", "onDisabled called, stopping updates")
        // Останавливаем обновления
        updateRunnable?.let { handler.removeCallbacks(it) }
    }
}