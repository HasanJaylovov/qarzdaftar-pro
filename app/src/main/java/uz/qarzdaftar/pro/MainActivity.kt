package uz.qarzdaftar.pro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val SERVER_URL = "https://hasanjaylovov.pythonanywhere.com"
    private val BOT_USERNAME = "qarzdaftar_pay_bot"

    private lateinit var prefs: android.content.SharedPreferences
    private var storeId: String = "QD-101"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE),
            100
        )

        prefs = getSharedPreferences("QarzDaftarPrefs", Context.MODE_PRIVATE)
        storeId = prefs.getString("store_id", "QD-101") ?: "QD-101"

        checkSubscriptionFromServer()
    }

    private fun checkSubscriptionFromServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$SERVER_URL/api/status/$storeId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000

                val responseText = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val isActive = json.optBoolean("active", false)

                withContext(Dispatchers.Main) {
                    val lockLayout = findViewById<LinearLayout>(R.id.lockScreenLayout)
                    val mainContentLayout = findViewById<LinearLayout>(R.id.mainContentLayout)

                    if (isActive) {
                        lockLayout?.visibility = View.GONE
                        mainContentLayout?.visibility = View.VISIBLE
                    } else {
                        lockLayout?.visibility = View.VISIBLE
                        mainContentLayout?.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun openTelegramBotForPayment(view: View) {
        val tgUri = Uri.parse("https://t.me/$BOT_USERNAME?start=$storeId")
        startActivity(Intent(Intent.ACTION_VIEW, tgUri))
    }

    fun sendAutoSms(phoneNumber: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Toast.makeText(this, "Avto-SMS yuborildi!", Toast.SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "SMS xatosi: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
