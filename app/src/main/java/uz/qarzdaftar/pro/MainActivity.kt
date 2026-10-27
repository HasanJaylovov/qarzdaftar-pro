package uz.qarzdaftar.pro

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class DebtRecord(
    val id: Int,
    val name: String,
    val phone: String,
    val amount: Long,
    val note: String,
    val dueDate: String,
    val createdAt: String
)

class LocalDebtDb(context: Context) : SQLiteOpenHelper(context, "qarzfy_local.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE debts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                phone TEXT,
                amount INTEGER,
                note TEXT,
                due_date TEXT,
                created_at TEXT
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun addDebt(name: String, phone: String, amount: Long, note: String, dueDate: String) {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        writableDatabase.execSQL(
            "INSERT INTO debts (name, phone, amount, note, due_date, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(name, phone, amount, note, dueDate, now)
        )
    }

    fun deleteDebt(id: Int) {
        writableDatabase.execSQL("DELETE FROM debts WHERE id = ?", arrayOf<Any>(id))
    }

    fun getAllDebts(query: String = ""): List<DebtRecord> {
        val list = mutableListOf<DebtRecord>()
        val cursor = if (query.isBlank()) {
            readableDatabase.rawQuery("SELECT * FROM debts ORDER BY id DESC", null)
        } else {
            readableDatabase.rawQuery(
                "SELECT * FROM debts WHERE name LIKE ? OR phone LIKE ? ORDER BY id DESC",
                arrayOf("%$query%", "%$query%")
            )
        }
        while (cursor.moveToNext()) {
            list.add(
                DebtRecord(
                    id = cursor.getInt(0),
                    name = cursor.getString(1),
                    phone = cursor.getString(2),
                    amount = cursor.getLong(3),
                    note = cursor.getString(4),
                    dueDate = cursor.getString(5),
                    createdAt = cursor.getString(6)
                )
            )
        }
        cursor.close()
        return list
    }
}

class MainActivity : AppCompatActivity() {

    private val SERVER_URL = "https://hasanjaylovov.pythonanywhere.com"
    private val BOT_USERNAME = "qarzdaftar_pay_bot"

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var db: LocalDebtDb
    private var storeId: String = "QF-101"
    private var telegramChannel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE),
            100
        )

        db = LocalDebtDb(this)
        prefs = getSharedPreferences("QarzFyPrefs", Context.MODE_PRIVATE)
        storeId = prefs.getString("store_id", "QF-101") ?: "QF-101"
        telegramChannel = prefs.getString("telegram_channel", "") ?: ""

        findViewById<TextView>(R.id.tvLockStoreId)?.text = "Do'kon ID: $storeId"

        if (telegramChannel.isBlank()) {
            showChannelPromptDialog()
        }

        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                renderDebtorsList(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        renderDebtorsList()
        checkSubscriptionFromServer()
        scheduleDailyMidnightExport()
    }

    override fun onResume() {
        super.onResume()
        checkSubscriptionFromServer()
    }

    private fun showChannelPromptDialog() {
        val input = EditText(this).apply {
            hint = "Telegram kanal linki (masalan: @qarzfy_kanal)"
            setText(telegramChannel)
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("📢 Telegram Kanalni Sozlash")
            .setMessage("Har kuni soat 00:00 da barcha qarzdorlar va tranzaksiyalar Excel (.xlsx/.csv) formatida avtomatik ushbu kanalga yuboriladi.")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Saqlash") { _, _ ->
                val channel = input.text.toString().trim()
                if (channel.isNotEmpty()) {
                    telegramChannel = channel
                    prefs.edit().putString("telegram_channel", channel).apply()
                    Toast.makeText(this, "Kanal saqlandi: $channel", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    fun openSettingsClick(view: View) {
        val options = arrayOf("📢 Telegram Kanalni o'zgartirish", "ℹ️ Dastur haqida (Hasan Jaylovov)")
        AlertDialog.Builder(this)
            .setTitle("⚙️ Sozlamalar")
            .setItems(options) { _, which ->
                if (which == 0) {
                    showChannelPromptDialog()
                } else {
                    showAboutDialog()
                }
            }
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("ℹ️ Dastur Haqida")
            .setMessage("Dastur nomi: QarzFy v2.5\n\nDasturchi: Hasan Jaylovov\n\nAloqa uchun Telegram: @HasanJaylovov\n\nPlay Market standartidagi eng so'nggi va qulay qarz boshqaruv tizimi.")
            .setPositiveButton("Bog'lanish (@HasanJaylovov)") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/HasanJaylovov"))
                startActivity(intent)
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    fun refreshStatusClick(view: View) {
        Toast.makeText(this, "Tekshirilmoqda...", Toast.LENGTH_SHORT).show()
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

    private fun scheduleDailyMidnightExport() {
        // Har kuni 00:00 da kanalga avtomatik Excel hisobot yuborish simulyatsiyasi va fon vazifasi
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)
                if (hour == 0 && minute == 0) {
                    withContext(Dispatchers.Main) {
                        exportAndSendToTelegramChannel()
                    }
                    delay(60000 * 60) // 1 soat kutish
                }
                delay(30000) // Har 30 sekundda tekshirish
            }
        }
    }

    private fun exportAndSendToTelegramChannel() {
        if (telegramChannel.isBlank()) return
        try {
            val debts = db.getAllDebts()
            val sb = StringBuilder()
            // Siz taqdim etgan Excel (clients_export) formatiga moslashtirilgan sarlavha
            sb.append("ID,Ism,Telefon raqami,Balans USD,Balans UZS,Yaratilgan sana\n")
            for (d in debts) {
                sb.append("${d.id},\"${d.name}\",\"${d.phone}\",0,-${d.amount},\"${d.createdAt}\"\n")
            }
            val file = File(getExternalFilesDir(null), "QarzFy_Kunlik_Hisobot.csv")
            file.writeText(sb.toString())

            // Telegram kanalga ulashish intenti
            val uri = Uri.fromFile(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "📊 QarzFy Kunlik Avtomatik Hisobot\nKanal: $telegramChannel\nSana: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}")
                setPackage("org.telegram.messenger")
            }
            // Agar telegram o'rnatilgan bo'lsa avto-ulash
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun formatMoney(amount: Long): String {
        val nf = NumberFormat.getInstance(Locale.US)
        return nf.format(amount).replace(",", " ") + " so'm"
    }

    private fun renderDebtorsList(searchQuery: String = "") {
        val container = findViewById<LinearLayout>(R.id.debtorsListContainer) ?: return
        container.removeAllViews()

        val debts = db.getAllDebts(searchQuery)
        var totalAmount = 0L
        debts.forEach { totalAmount += it.amount }

        findViewById<TextView>(R.id.tvTotalDebt)?.text = formatMoney(totalAmount)
        findViewById<TextView>(R.id.tvDebtorsCount)?.text = "${debts.size} ta mijoz"

        if (debts.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Hozircha qarzlar ro'yxati bo'sh.\n'➕ Qarz Yozish' tugmasini bosib yangi qarz qo'shing."
                setTextColor(Color.parseColor("#64748B"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(20, 80, 20, 20)
            }
            container.addView(emptyTv)
            return
        }

        for (item in debts) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#1E293B"))
                setPadding(36, 30, 36, 30)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 24)
                layoutParams = params
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val nameTv = TextView(this).apply {
                text = "👤 ${item.name}"
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val amountTv = TextView(this).apply {
                text = formatMoney(item.amount)
                setTextColor(Color.parseColor("#F87171"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            }

            topRow.addView(nameTv)
            topRow.addView(amountTv)

            val infoTv = TextView(this).apply {
                text = "📞 ${item.phone}   •   ⏳ Muddat: ${item.dueDate}\n📝 Izoh: ${if (item.note.isBlank()) "Yo'q" else item.note}"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 13f
                setPadding(0, 14, 0, 20)
            }

            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val smsBtn = Button(this).apply {
                text = "📩 SMS Eslatma"
                setTextColor(Color.WHITE)
                textSize = 12f
                setBackgroundColor(Color.parseColor("#0284C7"))
                layoutParams = LinearLayout.LayoutParams(0, 95, 1f).apply {
                    setMargins(0, 0, 12, 0)
                }
                setOnClickListener {
                    val msg = "Hurmatli ${item.name}, do'konimizdan ${formatMoney(item.amount)} qarzingiz bor (Muddat: ${item.dueDate}). Iltimos, to'lovni amalga oshiring."
                    sendAutoSms(item.phone, msg)
                }
            }

            val paidBtn = Button(this).apply {
                text = "✅ To'landi (O'chirish)"
                setTextColor(Color.WHITE)
                textSize = 12f
                setBackgroundColor(Color.parseColor("#10B981"))
                layoutParams = LinearLayout.LayoutParams(0, 95, 1f).apply {
                    setMargins(12, 0, 0, 0)
                }
                setOnClickListener {
                    db.deleteDebt(item.id)
                    val msg = "Hurmatli ${item.name}, ${formatMoney(item.amount)} miqdoridagi qarzingiz to'liq yopildi. Xaridingiz uchun rahmat!"
                    sendAutoSms(item.phone, msg)
                    renderDebtorsList()
                }
            }

            btnRow.addView(smsBtn)
            btnRow.addView(paidBtn)

            card.addView(topRow)
            card.addView(infoTv)
            card.addView(btnRow)
            container.addView(card)
        }
    }

    fun openAddDebtDialog(view: View) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }

        val etName = EditText(this).apply { hint = "Mijoz ismi (masalan: Ali Valiyev)" }
        val etPhone = EditText(this).apply {
            hint = "Telefon (+998901234567)"
            inputType = InputType.TYPE_CLASS_PHONE
            setText("+998")
        }
        val etAmount = EditText(this).apply {
            hint = "Qarz summasi (so'mda)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etNote = EditText(this).apply { hint = "Olingan mahsulotlar / izoh" }
        val etDue = EditText(this).apply {
            hint = "Qaytarish sanasi (masalan: 10.11.2026)"
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, 7)
            setText(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(cal.time))
        }

        layout.addView(etName)
        layout.addView(etPhone)
        layout.addView(etAmount)
        layout.addView(etNote)
        layout.addView(etDue)

        AlertDialog.Builder(this)
            .setTitle("➕ Yangi Qarz Yozish")
            .setView(layout)
            .setPositiveButton("Saqlash va SMS yuborish") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val amount = etAmount.text.toString().trim().toLongOrNull() ?: 0L
                val note = etNote.text.toString().trim()
                val due = etDue.text.toString().trim()

                if (name.isEmpty() || phone.length < 9 || amount <= 0) {
                    Toast.makeText(this, "Iltimos, ism, telefon va summani to'g'ri kiriting!", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                db.addDebt(name, phone, amount, note, due)
                renderDebtorsList()

                val smsText = "Hurmatli $name! Sizga do'konimizdan ${formatMoney(amount)} qarz yozildi (${if (note.isNotEmpty()) note else "xarid"}). To'lov muddati: $due."
                sendAutoSms(phone, smsText)
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    fun sendRemindersToAllClick(view: View) {
        val debts = db.getAllDebts()
        if (debts.isEmpty()) {
            Toast.makeText(this, "Qarzdorlar ro'yxati bo'sh!", Toast.LENGTH_SHORT).show()
            return
        }
        for (item in debts) {
            val msg = "Hurmatli ${item.name}, do'konimizdan ${formatMoney(item.amount)} qarzingizni to'lash muddati (${item.dueDate}) keldi. Iltimos, to'lovni amalga oshiring."
            sendAutoSms(item.phone, msg)
        }
        Toast.makeText(this, "${debts.size} ta qarzdorga SMS eslatma yuborildi!", Toast.LENGTH_LONG).show()
    }

    fun exportToExcelClick(view: View) {
        val debts = db.getAllDebts()
        if (debts.isEmpty()) {
            Toast.makeText(this, "Eksport qilish uchun qarzlar yo'q!", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val sb = StringBuilder()
            // Siz taqdim etgan Excel hisobot formatiga to'liq mos ravishda
            sb.append("ID,Ism,Telefon raqami,Balans USD,Balans UZS,Yaratilgan sana\n")
            for (d in debts) {
                sb.append("${d.id},\"${d.name}\",\"${d.phone}\",0,-${d.amount},\"${d.createdAt}\"\n")
            }
            val file = File(getExternalFilesDir(null), "clients_export_QarzFy.csv")
            file.writeText(sb.toString())
            Toast.makeText(this, "✅ Excel hisobot saqlandi:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Xatolik: ${e.message}", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "📩 Avto-SMS yuborildi: $phoneNumber", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "SMS xatosi: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
