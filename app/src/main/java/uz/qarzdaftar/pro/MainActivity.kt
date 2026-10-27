package uz.qarzdaftar.pro

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.telephony.SmsManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.*
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream

data class ClientRec(
    val id: Int,
    val name: String,
    val phone: String,
    val balUsd: Double,
    val balUzs: Long,
    val folder: String,
    val dueDate: String,
    val createdAt: String
)

data class TxRec(
    val txId: String,
    val kind: String,
    val personName: String,
    val personPhone: String,
    val amount: Long,
    val currency: String,
    val date: String,
    val note: String,
    val status: String,
    val isSupplier: Boolean
)

data class ProductRec(
    val id: Int,
    val name: String,
    val barcode: String,
    val qty: Int,
    val unit: String,
    val costPrice: Long,
    val salePrice: Long,
    val currency: String,
    val supplier: String
)

data class KassaRec(
    val id: Int,
    val type: String,
    val category: String,
    val amount: Long,
    val currency: String,
    val payType: String,
    val date: String,
    val note: String,
    val creator: String
)

class QarzFyDatabase(context: Context) : SQLiteOpenHelper(context, "qarzfy_v5_glass.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE clients (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                phone TEXT,
                bal_usd REAL DEFAULT 0,
                bal_uzs INTEGER DEFAULT 0,
                folder TEXT DEFAULT 'Barchasi',
                due_date TEXT,
                created_at TEXT,
                is_supplier INTEGER DEFAULT 0,
                address TEXT DEFAULT ''
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tx_id TEXT,
                kind TEXT,
                person_name TEXT,
                person_phone TEXT,
                amount INTEGER,
                currency TEXT,
                date TEXT,
                note TEXT,
                status TEXT,
                is_supplier INTEGER DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                barcode TEXT,
                qty INTEGER,
                unit TEXT,
                cost_price INTEGER,
                sale_price INTEGER,
                currency TEXT,
                supplier TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE kassa (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT,
                category TEXT,
                amount INTEGER,
                currency TEXT,
                pay_type TEXT,
                date TEXT,
                note TEXT,
                creator TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE employees (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                phone TEXT,
                role TEXT,
                salary_type TEXT
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun nowStr(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    fun upsertClientDirect(name: String, phone: String, balUsd: Double, balUzs: Long, createdAt: String, isSupplier: Boolean) {
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        val c = db.rawQuery("SELECT id FROM clients WHERE name = ? AND is_supplier = ?", arrayOf(name, supFlag.toString()))
        if (c.moveToFirst()) {
            val id = c.getInt(0)
            db.execSQL("UPDATE clients SET phone = ?, bal_usd = ?, bal_uzs = ? WHERE id = ?", arrayOf<Any>(phone, balUsd, balUzs, id))
        } else {
            db.execSQL(
                "INSERT INTO clients (name, phone, bal_usd, bal_uzs, folder, due_date, created_at, is_supplier) VALUES (?, ?, ?, ?, 'Barchasi', 'Bugun', ?, ?)",
                arrayOf<Any>(name, phone, balUsd, balUzs, createdAt.ifEmpty { nowStr() }, supFlag)
            )
        }
        c.close()
    }

    fun addOrUpdatePersonTx(name: String, phone: String, amount: Long, currency: String, kind: String, note: String, dueDate: String, isSupplier: Boolean, folder: String = "Barchasi") {
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        val c = db.rawQuery("SELECT id, bal_usd, bal_uzs FROM clients WHERE name = ? AND is_supplier = ?", arrayOf(name, supFlag.toString()))
        val delta = if (kind == "Chiqim") -amount else amount
        if (c.moveToFirst()) {
            val id = c.getInt(0)
            val curUsd = c.getDouble(1)
            val curUzs = c.getLong(2)
            if (currency == "USD") {
                db.execSQL("UPDATE clients SET bal_usd = ?, due_date = ? WHERE id = ?", arrayOf<Any>(curUsd + delta, dueDate, id))
            } else {
                db.execSQL("UPDATE clients SET bal_uzs = ?, due_date = ? WHERE id = ?", arrayOf<Any>(curUzs + delta, dueDate, id))
            }
        } else {
            val initUsd = if (currency == "USD") delta.toDouble() else 0.0
            val initUzs = if (currency == "UZS") delta else 0L
            db.execSQL(
                "INSERT INTO clients (name, phone, bal_usd, bal_uzs, folder, due_date, created_at, is_supplier) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(name, phone, initUsd, initUzs, folder, dueDate, nowStr(), supFlag)
            )
        }
        c.close()

        if (amount > 0) {
            val prefix = if (isSupplier) "ST-" else "D-"
            val txId = prefix + (100000 + Random().nextInt(900000))
            db.execSQL(
                "INSERT INTO transactions (tx_id, kind, person_name, person_phone, amount, currency, date, note, status, is_supplier) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Faol', ?)",
                arrayOf<Any>(txId, kind, name, phone, Math.abs(amount), currency, nowStr(), note, supFlag)
            )
        }
    }

    fun getPersons(isSupplier: Boolean, query: String = "", sortMode: String = "Faol"): List<ClientRec> {
        val list = mutableListOf<ClientRec>()
        val supFlag = if (isSupplier) 1 else 0
        val orderBy = when (sortMode) {
            "Summa" -> "ABS(bal_uzs) DESC"
            "A-Z" -> "name COLLATE NOCASE ASC"
            "Muddat" -> "due_date ASC"
            else -> "id DESC"
        }
        val c = if (query.isBlank()) {
            readableDatabase.rawQuery("SELECT id, name, phone, bal_usd, bal_uzs, folder, due_date, created_at FROM clients WHERE is_supplier = ? ORDER BY $orderBy", arrayOf(supFlag.toString()))
        } else {
            readableDatabase.rawQuery("SELECT id, name, phone, bal_usd, bal_uzs, folder, due_date, created_at FROM clients WHERE is_supplier = ? AND (name LIKE ? OR phone LIKE ?) ORDER BY $orderBy", arrayOf(supFlag.toString(), "%$query%", "%$query%"))
        }
        while (c.moveToNext()) {
            list.add(ClientRec(c.getInt(0), c.getString(1), c.getString(2), c.getDouble(3), c.getLong(4), c.getString(5), c.getString(6) ?: "", c.getString(7) ?: ""))
        }
        c.close()
        return list
    }

    fun deletePerson(id: Int) {
        writableDatabase.execSQL("DELETE FROM clients WHERE id = ?", arrayOf<Any>(id))
    }

    fun getTransactions(isSupplier: Boolean): List<TxRec> {
        val list = mutableListOf<TxRec>()
        val supFlag = if (isSupplier) 1 else 0
        val c = readableDatabase.rawQuery("SELECT tx_id, kind, person_name, person_phone, amount, currency, date, note, status FROM transactions WHERE is_supplier = ? ORDER BY id DESC", arrayOf(supFlag.toString()))
        while (c.moveToNext()) {
            list.add(TxRec(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4), c.getString(5), c.getString(6), c.getString(7) ?: "", c.getString(8), isSupplier))
        }
        c.close()
        return list
    }

    fun addProduct(name: String, barcode: String, qty: Int, unit: String, cost: Long, sale: Long, currency: String, supplier: String) {
        writableDatabase.execSQL(
            "INSERT INTO products (name, barcode, qty, unit, cost_price, sale_price, currency, supplier) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(name, barcode, qty, unit, cost, sale, currency, supplier)
        )
    }

    fun getProducts(query: String = ""): List<ProductRec> {
        val list = mutableListOf<ProductRec>()
        val c = if (query.isBlank()) {
            readableDatabase.rawQuery("SELECT id, name, barcode, qty, unit, cost_price, sale_price, currency, supplier FROM products ORDER BY id DESC", null)
        } else {
            readableDatabase.rawQuery("SELECT id, name, barcode, qty, unit, cost_price, sale_price, currency, supplier FROM products WHERE name LIKE ? OR barcode LIKE ? ORDER BY id DESC", arrayOf("%$query%", "%$query%"))
        }
        while (c.moveToNext()) {
            list.add(ProductRec(c.getInt(0), c.getString(1), c.getString(2), c.getInt(3), c.getString(4), c.getLong(5), c.getLong(6), c.getString(7), c.getString(8)))
        }
        c.close()
        return list
    }

    fun addKassa(type: String, category: String, amount: Long, currency: String, payType: String, note: String, creator: String) {
        writableDatabase.execSQL(
            "INSERT INTO kassa (type, category, amount, currency, pay_type, date, note, creator) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(type, category, amount, currency, payType, nowStr(), note, creator)
        )
    }

    fun getKassaList(filterType: String = "Barchasi"): List<KassaRec> {
        val list = mutableListOf<KassaRec>()
        val c = if (filterType == "Barchasi") {
            readableDatabase.rawQuery("SELECT id, type, category, amount, currency, pay_type, date, note, creator FROM kassa ORDER BY id DESC", null)
        } else {
            readableDatabase.rawQuery("SELECT id, type, category, amount, currency, pay_type, date, note, creator FROM kassa WHERE type = ? ORDER BY id DESC", arrayOf(filterType))
        }
        while (c.moveToNext()) {
            list.add(KassaRec(c.getInt(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getString(8)))
        }
        c.close()
        return list
    }

    fun addEmployee(name: String, phone: String, salaryType: String) {
        writableDatabase.execSQL("INSERT INTO employees (name, phone, role, salary_type) VALUES (?, ?, 'Sotuvchi', ?)", arrayOf<Any>(name, phone, salaryType))
    }

    fun getEmployees(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val c = readableDatabase.rawQuery("SELECT name, phone, salary_type FROM employees ORDER BY id DESC", null)
        while (c.moveToNext()) {
            list.add(Pair("${c.getString(0)} (${c.getString(2)})", c.getString(1)))
        }
        c.close()
        return list
    }
}

class MainActivity : AppCompatActivity() {

    private val SERVER_URL = "https://hasanjaylovov.pythonanywhere.com"
    private val BOT_USERNAME = "qarzdaftar_pay_bot"
    private val PICK_IMPORT_FILE = 701

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var db: QarzFyDatabase
    private lateinit var rootLayout: LinearLayout

    private var storeId = "QF-101"
    private var storeName = "Hasan Jaylovov"
    private var storeAddress = "O'zbekiston, Surxondaryo"
    private var storeCategory = "Qurilish mollari"
    private var mainCurrency = "UZS"
    private var userPhone = ""
    private var telegramChannel = "@qarzfy_hisobot"
    private var botToken = ""
    private var subscriptionExpiryMillis = 0L

    private var currentBottomTab = 0
    private var currentHomeSubTab = 0
    private var currentSortMode = "Faol"
    private var searchQuery = ""
    private var isRightSpeedDialOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE),
            100
        )

        prefs = getSharedPreferences("QarzFyGlassV5Prefs", Context.MODE_PRIVATE)
        db = QarzFyDatabase(this)
        rootLayout = findViewById(R.id.mainContentLayout)

        loadPrefs()

        if (userPhone.isBlank()) {
            renderOnboardingAndLogin()
        } else {
            checkServerSubscriptionAndRender()
            scheduleMidnightChannelExport()
        }
    }

    override fun onResume() {
        super.onResume()
        if (userPhone.isNotBlank()) {
            checkServerSubscriptionAndRender()
        }
    }

    private fun loadPrefs() {
        userPhone = prefs.getString("user_phone", "") ?: ""
        storeId = prefs.getString("store_id", "QF-101") ?: "QF-101"
        storeName = prefs.getString("store_name", "Hasan Jaylovov") ?: "Hasan Jaylovov"
        storeAddress = prefs.getString("store_address", "O'zbekiston") ?: "O'zbekiston"
        storeCategory = prefs.getString("store_category", "Qurilish mollari") ?: "Qurilish mollari"
        mainCurrency = prefs.getString("main_currency", "UZS") ?: "UZS"
        telegramChannel = prefs.getString("telegram_channel", "@qarzfy_hisobot") ?: "@qarzfy_hisobot"
        botToken = prefs.getString("bot_token", "") ?: ""
        subscriptionExpiryMillis = prefs.getLong("sub_expiry_millis", 0L)
    }

    // ==================== REALISTIC GLASSMORPHISM DRAWABLES ====================
    private fun glassSurface(fillHex: String = "#E6FFFFFF", strokeHex: String = "#80FFFFFF", rad: Float = 44f): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = rad
            setColor(Color.parseColor(fillHex))
            setStroke(3, Color.parseColor(strokeHex))
        }
    }

    private fun glassPurpleGradient(rad: Float = 46f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#6D28D9"), Color.parseColor("#4F46E5"), Color.parseColor("#2563EB"))
        ).apply {
            cornerRadius = rad
            setStroke(3, Color.parseColor("#99FFFFFF"))
        }
    }

    private fun glassOrangeGradient(rad: Float = 42f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#FB923C"), Color.parseColor("#EA580C"), Color.parseColor("#E11D48"))
        ).apply {
            cornerRadius = rad
            setStroke(3, Color.parseColor("#99FFFFFF"))
        }
    }

    private fun circleAvatarBg(letter: Char): GradientDrawable {
        val colors = listOf("#6366F1", "#8B5CF6", "#EC4899", "#EF4444", "#10B981", "#0EA5E9", "#F59E0B")
        val pick = colors[Math.abs(letter.hashCode()) % colors.size]
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(pick))
        }
    }

    private fun formatMoney(amount: Long, cur: String = mainCurrency): String {
        val nf = NumberFormat.getInstance(Locale.US)
        return nf.format(amount).replace(",", " ") + " $cur"
    }

    private fun remainingDaysText(): String {
        val diff = subscriptionExpiryMillis - System.currentTimeMillis()
        if (diff <= 0) return "Obuna tugagan"
        val days = (diff / (1000L * 60 * 60 * 24)) + 1
        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(subscriptionExpiryMillis))
        return "$days kun qoldi ($dateStr gacha)"
    }

    // ==================== OBUNA MUDDATINI TEKSHIRISH VA QO'SHISH ====================
    private fun checkServerSubscriptionAndRender() {
        val now = System.currentTimeMillis()
        if (now > subscriptionExpiryMillis) {
            renderSubscriptionLockScreen()
        } else {
            renderMainWorkspace()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$SERVER_URL/api/status/$storeId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                val text = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(text)
                val serverActive = json.optBoolean("active", true)
                val serverExpiry = json.optLong("expiry_millis", 0L)
                if (serverExpiry > subscriptionExpiryMillis) {
                    subscriptionExpiryMillis = serverExpiry
                    prefs.edit().putLong("sub_expiry_millis", subscriptionExpiryMillis).apply()
                }
                withContext(Dispatchers.Main) {
                    if (!serverActive || System.currentTimeMillis() > subscriptionExpiryMillis) {
                        renderSubscriptionLockScreen()
                    } else {
                        renderMainWorkspace()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun addSubscriptionMonths(months: Int, priceUzs: Int) {
        val now = System.currentTimeMillis()
        val baseTime = if (subscriptionExpiryMillis > now) subscriptionExpiryMillis else now
        val addedMillis = months.toLong() * 30L * 24L * 60L * 60L * 1000L
        subscriptionExpiryMillis = baseTime + addedMillis
        prefs.edit().putLong("sub_expiry_millis", subscriptionExpiryMillis).apply()

        val msg = "Assalomu alaykum! Do'kon ID: $storeId ($storeName).\nMen '$months oy — $priceUzs so'm' tarifini tanladim. Obuna muddatimni tasdiqlab bering."
        val tgUrl = "https://t.me/$BOT_USERNAME?start=${storeId}_${months}oy"
        Toast.makeText(this, "✅ +$months oy obuna muddati eskisiga qo'shildi!\nJami: ${remainingDaysText()}", Toast.LENGTH_LONG).show()

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tgUrl))
            startActivity(intent)
        } catch (_: Exception) {}

        renderMainWorkspace()
    }

    private fun renderSubscriptionLockScreen() {
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#EEF2FF"))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(50, 80, 50, 80)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glassSurface("#FFFFFF", "#C7D2FE", 54f)
            setPadding(50, 60, 50, 60)
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(200, 200).apply { bottomMargin = 24 }
        }
        card.addView(logo)
        card.addView(TextView(this).apply {
            text = "Obuna muddati tugadi"
            setTextColor(Color.parseColor("#1E1B4B"))
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "Do'kon ID: $storeId ($storeName)\nDavom etish uchun o'zingizga mos tarifni tanlang. Xarid qilingan vaqt mavjud obunangizga avtomatik qo'shiladi:"
            setTextColor(Color.parseColor("#475569"))
            textSize = 13.5f
            gravity = Gravity.CENTER
            setPadding(10, 16, 10, 36)
        })

        card.addView(tariffButton("💳 1 Oy — 49 000 so'm", "Barcha funksiyalar + 00:00 Kanal Eksport", "#6D28D9") {
            addSubscriptionMonths(1, 49000)
        })
        card.addView(tariffButton("🔥 3 Oy — 135 000 so'm (Tejamkor)", "Oylik atigi 45 000 so'mdan tushadi", "#2563EB") {
            addSubscriptionMonths(3, 135000)
        })
        card.addView(tariffButton("👑 6 Oy — 255 000 so'm (VIP)", "Oylik atigi 42 500 so'mdan + Cheksiz SMS", "#059669") {
            addSubscriptionMonths(6, 255000)
        })

        col.addView(card)
        rootLayout.addView(col)
    }

    private fun tariffButton(title: String, sub: String, colorHex: String, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 36f
                setColor(Color.parseColor(colorHex))
            }
            setPadding(38, 30, 38, 30)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
            setOnClickListener { onClick() }
        }
        box.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        })
        box.addView(TextView(this).apply {
            text = sub
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 12f
        })
        return box
    }

    // ==================== 1. ONBOARDING VA LOGIN ====================
    private fun renderOnboardingAndLogin() {
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#F1F5F9"))
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 60, 44, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glassPurpleGradient(54f)
            setPadding(44, 50, 44, 50)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 28 }
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(190, 190).apply { bottomMargin = 18 }
        }
        heroCard.addView(logo)
        heroCard.addView(TextView(this).apply {
            text = "QarzFy Glassmorphism"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })
        heroCard.addView(TextView(this).apply {
            text = "Mijozlar, ta'minotchilar, kassa va har kuni 00:00 da Telegram kanalga avto-Excel hisobot."
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(10, 8, 10, 0)
        })
        container.addView(heroCard)

        val formCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassSurface("#FFFFFF", "#CBD5E1", 48f)
            setPadding(42, 42, 42, 42)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val etStore = makeGlassInput("Do'kon nomi * (Masalan: Hasan Jaylovov)", storeName)
        val etAddr = makeGlassInput("Manzil (O'zbekiston, Surxondaryo)", storeAddress)
        val etPhone = makeGlassInput("Telefon raqam (+998909958297)", "+998")
        etPhone.inputType = InputType.TYPE_CLASS_PHONE
        val etPass = makeGlassInput("Parol (Admin panel uchun)", "")
        etPass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val etChannel = makeGlassInput("Telegram Kanal (@kanal_linki yoki Chat ID)", telegramChannel)
        val etToken = makeGlassInput("Telegram Bot Token (00:00 avto yuborish uchun)", botToken)

        formCard.addView(etStore)
        formCard.addView(etAddr)
        formCard.addView(etPhone)
        formCard.addView(etPass)
        formCard.addView(etChannel)
        formCard.addView(etToken)

        val btnStart = Button(this).apply {
            text = "🚀 Ro'yxatdan o'tish (10 kun bepul sinov)"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glassPurpleGradient(36f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 135).apply { topMargin = 20 }
            setOnClickListener {
                val ph = etPhone.text.toString().trim()
                val pw = etPass.text.toString().trim()
                val st = etStore.text.toString().trim()
                if (ph.length < 9 || pw.isEmpty() || st.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Do'kon nomi, telefon va parolni kiriting!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                userPhone = ph
                storeName = st
                storeAddress = etAddr.text.toString().trim()
                telegramChannel = etChannel.text.toString().trim().ifEmpty { "@qarzfy_hisobot" }
                botToken = etToken.text.toString().trim()
                storeId = "QF-" + Math.abs(ph.hashCode() % 90000 + 10000)
                subscriptionExpiryMillis = System.currentTimeMillis() + (10L * 24L * 60L * 60L * 1000L)

                prefs.edit()
                    .putString("user_phone", userPhone)
                    .putString("store_id", storeId)
                    .putString("store_name", storeName)
                    .putString("store_address", storeAddress)
                    .putString("telegram_channel", telegramChannel)
                    .putString("bot_token", botToken)
                    .putLong("sub_expiry_millis", subscriptionExpiryMillis)
                    .apply()

                // Namuna sifatida 2 ta mijoz qo'shamiz
                if (db.getPersons(false).isEmpty()) {
                    db.upsertClientDirect("Hasan toga", "+998 90 995 82 97", 0.0, -20000L, db.nowStr(), false)
                    db.upsertClientDirect("Nortoy oshna", "+998 93 363 97 12", 0.0, 0L, db.nowStr(), false)
                }

                syncStoreWithAdminServer(ph, pw)
                renderMainWorkspace()
                scheduleMidnightChannelExport()
            }
        }
        formCard.addView(btnStart)
        container.addView(formCard)
        scroll.addView(container)
        rootLayout.addView(scroll)
    }

    private fun makeGlassInput(hintText: String, initial: String = ""): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(initial)
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            background = glassSurface("#F8FAFC", "#CBD5E1", 32f)
            setPadding(34, 28, 34, 28)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        }
    }

    private fun syncStoreWithAdminServer(phone: String, pass: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$SERVER_URL/api/register_store")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject().apply {
                    put("phone", phone)
                    put("password", pass)
                    put("store_id", storeId)
                    put("store_name", storeName)
                    put("telegram_channel", telegramChannel)
                }
                conn.outputStream.write(body.toString().toByteArray())
                conn.inputStream.close()
            } catch (_: Exception) {}
        }
    }

    // ==================== 2. ASOSIY WORKSPACE + 2-RASMDAGI CHAP STACK VA 1-RASMDAGI O'NG PANEL ====================
    private fun renderMainWorkspace() {
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#F3F4F8"))

        val mainFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val pageView = when (currentBottomTab) {
            0 -> buildHomeTab()
            1 -> buildKassaTab()
            2 -> buildMahsulotTab()
            3 -> buildStatistikaTab()
            else -> buildProfilTab()
        }
        mainFrame.addView(pageView)

        // 2-RASMDAGI CHAP TOMONDAGI 4 TA SUZUVCHI TEZKOR TUGMA (Faqat Asosiy Mijozlar/Ta'minotchilar bo'limida)
        if (currentBottomTab == 0 && currentHomeSubTab in 0..1) {
            val isSup = (currentHomeSubTab == 1)
            val leftFloatingStack = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START
                ).apply {
                    leftMargin = 32
                    bottomMargin = 36
                }
            }

            // 1) Import tugmasi (Pastga strelka)
            leftFloatingStack.addView(makeFloatingActionSquare("📥", "#6366F1") {
                showImportBottomSheet()
            })
            // 2) Eksport + Kanalga yuborish tugmasi (Fayl tepaga strelka)
            leftFloatingStack.addView(makeFloatingActionSquare("📤", "#6366F1") {
                showExportBottomSheet()
            })
            // 3) Qarzdor / Ta'minotchi qo'shish (Odamcha +)
            leftFloatingStack.addView(makeFloatingActionSquare("👤+", "#6366F1") {
                showAddClientBottomSheet(isSup)
            })
            // 4) Tezkor tranzaksiya (To'q sariq Chaqmoq — 4-rasmdagi Slide-up BottomSheet)
            leftFloatingStack.addView(makeFloatingActionSquare("⚡", "#F97316", isOrange = true) {
                showQuickTransactionBottomSheet(isSup)
            })

            mainFrame.addView(leftFloatingStack)
        }

        // O'NG TOMONDAGI QR VA 1-RASMDAGI SPEED-DIAL PANEL (Barcha ekranlarda)
        val rightOverlayContainer = buildRightSpeedDialOverlay()
        mainFrame.addView(rightOverlayContainer)

        rootLayout.addView(mainFrame)
        rootLayout.addView(buildBottomNavBar())
    }

    private fun makeFloatingActionSquare(symbol: String, hex: String, isOrange: Boolean = false, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = symbol
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = if (symbol.length > 2) 15f else 20f
            setTypeface(null, Typeface.BOLD)
            background = if (isOrange) glassOrangeGradient(38f) else GradientDrawable().apply {
                cornerRadius = 38f
                setColor(Color.parseColor(hex))
                setStroke(3, Color.parseColor("#80FFFFFF"))
            }
            elevation = 14f
            layoutParams = LinearLayout.LayoutParams(132, 132).apply { topMargin = 18 }
            setOnClickListener { onClick() }
        }
    }

    // 1-RASMDAGI O'NG TOMONDAN CHIQUVCHI PANEL (Kalkulyator, Bozorlik, Vazifalar, Sotuv, SMS jo'natma, Qo'ng'iroq)
    private fun buildRightSpeedDialOverlay(): View {
        val wrapper = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        if (isRightSpeedDialOpen) {
            wrapper.setBackgroundColor(Color.parseColor("#660F172A"))
            wrapper.setOnClickListener {
                isRightSpeedDialOpen = false
                renderMainWorkspace()
            }

            val menuCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL or Gravity.END
                ).apply { rightMargin = 32 }
            }

            val items = listOf(
                Triple("Kalkulyator", "🧮") { openSalesCalculatorDialog() },
                Triple("Bozorlik", "📋") { openShoppingListDialog() },
                Triple("Vazifalar", "☑️") { showCommunityAndAmbassadorDialog() },
                Triple("Sotuv", "🧾") { currentBottomTab = 2; isRightSpeedDialOpen = false; renderMainWorkspace() },
                Triple("SMS jo'natma", "📢") { sendBulkSmsReminders(false) },
                Triple("Qo'ng'iroq", "📞") { showVoiceSettingsDialog() }
            )

            items.forEach { (label, icon, act) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 22 }
                    setOnClickListener {
                        isRightSpeedDialOpen = false
                        act.invoke()
                    }
                }
                val pillLabel = TextView(this).apply {
                    text = label
                    setTextColor(Color.parseColor("#1E293B"))
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    background = glassSurface("#FFFFFF", "#E2E8F0", 30f)
                    elevation = 10f
                    setPadding(36, 22, 36, 22)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 20 }
                }
                val iconBox = TextView(this).apply {
                    text = icon
                    gravity = Gravity.CENTER
                    textSize = 20f
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = 34f
                        setColor(Color.parseColor("#6366F1"))
                        setStroke(2, Color.WHITE)
                    }
                    elevation = 12f
                    layoutParams = LinearLayout.LayoutParams(125, 125)
                }
                row.addView(pillLabel)
                row.addView(iconBox)
                menuCol.addView(row)
            }
            wrapper.addView(menuCol)
        }

        // O'ng pastki suzuvchi tugmalar: 🤖 Menyu ochish va 🔳 QR
        val rightBottomCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = 32
                bottomMargin = 36
            }
        }

        val speedBtn = TextView(this).apply {
            text = if (isRightSpeedDialOpen) "✕" else "🤖"
            gravity = Gravity.CENTER
            textSize = 20f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0EA5E9"))
                setStroke(4, Color.WHITE)
            }
            elevation = 14f
            layoutParams = LinearLayout.LayoutParams(120, 120).apply { bottomMargin = 18 }
            setOnClickListener {
                isRightSpeedDialOpen = !isRightSpeedDialOpen
                renderMainWorkspace()
            }
        }

        val qrFloatingBtn = makeFloatingActionSquare("🔳", "#6366F1") {
            showQrCodeAndOpenPageDialog()
        }

        rightBottomCol.addView(speedBtn)
        rightBottomCol.addView(qrFloatingBtn)
        wrapper.addView(rightBottomCol)
        return wrapper
    }

    // ==================== TAB 0: ASOSIY EKRAN (2-va 4-rasmdagi interfeys) ====================
    private fun buildHomeTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 240)
        }

        // Header: ☰ + Logo + StoreName + Savat + Kanalga yuborish
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 22 }
        }
        val menuIcon = TextView(this).apply {
            text = "☰"
            textSize = 20f
            setTextColor(Color.parseColor("#1E293B"))
            gravity = Gravity.CENTER
            background = glassSurface("#FFFFFF", "#E2E8F0", 30f)
            layoutParams = LinearLayout.LayoutParams(105, 105).apply { rightMargin = 16 }
            setOnClickListener {
                isRightSpeedDialOpen = !isRightSpeedDialOpen
                renderMainWorkspace()
            }
        }
        val logoImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(95, 95).apply { rightMargin = 14 }
        }
        val storeTitle = TextView(this).apply {
            text = "🇺🇿 $storeName ❯"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openBusinessInfoDialog() }
        }
        val channelBtn = TextView(this).apply {
            text = "📤 Kanalga"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            background = glassPurpleGradient(28f)
            setPadding(26, 20, 26, 20)
            setOnClickListener { showExportBottomSheet() }
        }

        header.addView(menuIcon)
        header.addView(logoImg)
        header.addView(storeTitle)
        header.addView(channelBtn)
        col.addView(header)

        // Binafsha Glassmorphism Premium Banner (2-rasmdagidek)
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glassPurpleGradient(44f)
            elevation = 10f
            setPadding(38, 32, 38, 32)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
            setOnClickListener { showSubscriptionTariffsBottomSheet() }
        }
        banner.addView(TextView(this).apply {
            text = "🎁"
            textSize = 26f
            setPadding(0, 0, 24, 0)
        })
        banner.addView(TextView(this).apply {
            text = "Obuna: ${remainingDaysText()}\n1 oy 49 000 • 3 oy 135 000 • 6 oy 255 000 so'm"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        banner.addView(TextView(this).apply {
            text = "❯"
            setTextColor(Color.WHITE)
            textSize = 18f
        })
        col.addView(banner)

        // Sub-tabs: Mijozlar | Ta'minotchilar | Xodimlar
        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        }
        listOf("Mijozlar", "Ta'minotchilar", "Xodimlar").forEachIndexed { i, t ->
            val sel = (currentHomeSubTab == i)
            tabsRow.addView(TextView(this).apply {
                text = t
                gravity = Gravity.CENTER
                textSize = 13.5f
                setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#475569"))
                background = if (sel) glassPurpleGradient(30f) else glassSurface("#FFFFFF", "#E2E8F0", 30f)
                setPadding(16, 24, 16, 24)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i < 2) rightMargin = 12
                }
                setOnClickListener {
                    currentHomeSubTab = i
                    renderMainWorkspace()
                }
            })
        }
        col.addView(tabsRow)

        if (currentHomeSubTab == 2) {
            col.addView(buildEmployeesSection())
            scroll.addView(col)
            return scroll
        }

        val isSup = (currentHomeSubTab == 1)
        val persons = db.getPersons(isSup, searchQuery, currentSortMode)

        // Barchasi [135]  [+] qatori (2-rasmdagidek)
        val folderBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        folderBar.addView(TextView(this).apply {
            text = "Barchasi  ${persons.size}"
            setTextColor(Color.parseColor("#4F46E5"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(16, 12, 28, 12)
        })
        folderBar.addView(TextView(this).apply {
            text = "+"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.parseColor("#475569"))
            background = glassSurface("#E2E8F0", "#CBD5E1", 24f)
            layoutParams = LinearLayout.LayoutParams(80, 80)
            setOnClickListener { openCreateFolderDialog() }
        })
        col.addView(folderBar)

        // Filtrlar: 🕒 Faol ↓ | ⚠️ Muddat | 💵 Summa | 🔍 | A-Z | ⏳ Filtr
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        listOf("Faol", "Muddat", "Summa", "A-Z").forEach { m ->
            val act = (currentSortMode == m)
            filterRow.addView(TextView(this).apply {
                text = when (m) {
                    "Faol" -> "🕒 Faol ↓"
                    "Muddat" -> "⚠️ Muddat"
                    "Summa" -> "💵 Summa"
                    else -> "A-Z"
                }
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (act) Color.parseColor("#4F46E5") else Color.parseColor("#1E293B"))
                background = glassSurface(if (act) "#E0E7FF" else "#FFFFFF", "#CBD5E1", 24f)
                setPadding(22, 18, 22, 18)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 10 }
                setOnClickListener {
                    currentSortMode = m
                    renderMainWorkspace()
                }
            })
        }
        col.addView(filterRow)

        // Qidiruv qatori
        val etSearch = makeGlassInput("🔍 Ism yoki telefon raqam bo'yicha qidiruv...", searchQuery)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        col.addView(etSearch)

        // Mijozlar ro'yxati (2-va 4-rasmdagidek rangli harfli avatar + Ism + Telefon + O'ngda Balans)
        if (persons.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "Ro'yxat bo'sh.\nChap pastdagi 📥 tugmasi orqali Excel import qiling yoki 👤+ tugmasi orqali mijoz qo'shing."
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#64748B"))
                textSize = 14f
                background = glassSurface("#FFFFFF", "#E2E8F0", 38f)
                setPadding(40, 70, 40, 70)
            })
        } else {
            persons.forEach { p ->
                val rowCard = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = glassSurface("#FFFFFF", "#E2E8F0", 36f)
                    elevation = 4f
                    setPadding(28, 26, 32, 26)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                    setOnClickListener {
                        showClientKirimChiqimBottomSheet(p, isSup)
                    }
                }

                val firstChar = p.name.trim().firstOrNull()?.uppercaseChar() ?: 'M'
                val avatar = TextView(this).apply {
                    text = firstChar.toString()
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    background = circleAvatarBg(firstChar)
                    layoutParams = LinearLayout.LayoutParams(115, 115).apply { rightMargin = 26 }
                }

                val midCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                midCol.addView(TextView(this).apply {
                    text = p.name
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                })
                midCol.addView(TextView(this).apply {
                    text = p.phone
                    setTextColor(Color.parseColor("#64748B"))
                    textSize = 13f
                })

                val rightCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.END
                }
                rightCol.addView(TextView(this).apply {
                    text = formatMoney(p.balUzs, "UZS")
                    setTextColor(if (p.balUzs < 0) Color.parseColor("#DC2626") else Color.parseColor("#059669"))
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                })
                rightCol.addView(TextView(this).apply {
                    text = p.dueDate.ifEmpty { "Bugun" }
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 11.5f
                })

                rowCard.addView(avatar)
                rowCard.addView(midCol)
                rowCard.addView(rightCol)
                col.addView(rowCard)
            }
        }

        scroll.addView(col)
        return scroll
    }

    // ==================== SLIDE-UP BOTTOM SHEETS (3-VA 4-RASMDAGIDEK) ====================

    // 3-RASMDAGI MIJOZ KIRIM / CHIQIM SLIDE-UP BOTTOM SHEET
    private fun showClientKirimChiqimBottomSheet(client: ClientRec, isSupplier: Boolean) {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassSurface("#FFFFFF", "#E2E8F0", 54f)
            setPadding(48, 32, 48, 56)
        }

        // Top drag handle
        wrap.addView(View(this).apply {
            background = glassSurface("#CBD5E1", "#CBD5E1", 10f)
            layoutParams = LinearLayout.LayoutParams(120, 14).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 28
            }
        })

        // Kirim / Chiqim switch
        var selectedKind = "Kirim"
        val kindSwitchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glassSurface("#F1F5F9", "#E2E8F0", 32f)
            setPadding(10, 10, 10, 10)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
        }
        val btnKirim = TextView(this)
        val btnChiqim = TextView(this)

        fun updateKindTabs() {
            val kSel = (selectedKind == "Kirim")
            btnKirim.apply {
                text = "Kirim (To'lov olish)"
                gravity = Gravity.CENTER
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (kSel) Color.WHITE else Color.parseColor("#475569"))
                background = if (kSel) glassPurpleGradient(26f) else null
                setPadding(16, 22, 16, 22)
            }
            btnChiqim.apply {
                text = "Chiqim (Qarz yozish)"
                gravity = Gravity.CENTER
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (!kSel) Color.WHITE else Color.parseColor("#475569"))
                background = if (!kSel) glassOrangeGradient(26f) else null
                setPadding(16, 22, 16, 22)
            }
        }
        btnKirim.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        btnChiqim.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        btnKirim.setOnClickListener { selectedKind = "Kirim"; updateKindTabs() }
        btnChiqim.setOnClickListener { selectedKind = "Chiqim"; updateKindTabs() }
        updateKindTabs()
        kindSwitchRow.addView(btnKirim)
        kindSwitchRow.addView(btnChiqim)
        wrap.addView(kindSwitchRow)

        wrap.addView(TextView(this).apply {
            text = "👤 ${client.name} (${client.phone})"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        })

        // 3-rasmdagi SMS promo info box
        wrap.addView(TextView(this).apply {
            text = "💬 Avto-SMS bildirishnoma SIM-karta orqali bepul yuboriladi"
            setTextColor(Color.parseColor("#4F46E5"))
            textSize = 12.5f
            background = glassSurface("#EEF2FF", "#C7D2FE", 28f)
            setPadding(30, 22, 30, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        })

        // Miqdorni qo'shish + Valyuta UZS
        val amtRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        val etAmount = EditText(this).apply {
            hint = "Miqdorni qo'shish"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.parseColor("#0F172A"))
            background = glassSurface("#F8FAFC", "#CBD5E1", 28f)
            setPadding(32, 26, 32, 26)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 12 }
        }
        val spCur = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("UZS", "USD"))
            background = glassSurface("#F8FAFC", "#CBD5E1", 28f)
            setPadding(20, 14, 20, 14)
        }
        amtRow.addView(etAmount)
        amtRow.addView(spCur)
        wrap.addView(amtRow)

        // Tezkor summa chiplari (+ 20 000, + 50 000, To'liq yopish)
        val quickChipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }
        val absBal = Math.abs(client.balUzs)
        val chipList = mutableListOf(20000L, 50000L, 100000L)
        if (absBal > 0 && !chipList.contains(absBal)) chipList.add(0, absBal)
        chipList.take(3).forEach { v ->
            quickChipRow.addView(TextView(this).apply {
                text = "+ ${formatMoney(v, "")}".trim()
                setTextColor(Color.parseColor("#4F46E5"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                background = glassSurface("#EEF2FF", "#C7D2FE", 20f)
                setPadding(22, 12, 22, 12)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 12 }
                setOnClickListener { etAmount.setText(v.toString()) }
            })
        }
        wrap.addView(quickChipRow)

        wrap.addView(TextView(this).apply {
            text = "Balans: ${formatMoney(client.balUzs, "UZS")}"
            setTextColor(Color.parseColor("#DC2626"))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(4, 0, 4, 20)
        })

        wrap.addView(TextView(this).apply {
            text = "Nima uchun (ixtiyoriy)?"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(4, 0, 4, 10)
        })

        val etNote = EditText(this).apply {
            hint = "Mahsulot 1,\nMahsulot 2"
            minLines = 3
            gravity = Gravity.TOP
            setTextColor(Color.parseColor("#0F172A"))
            background = glassSurface("#F8FAFC", "#CBD5E1", 28f)
            setPadding(32, 26, 32, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 26 }
        }
        wrap.addView(etNote)

        val btnSubmit = Button(this).apply {
            text = "To'lov qo'shish / Saqlash"
            setTextColor(Color.WHITE)
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            background = glassPurpleGradient(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 135)
            setOnClickListener {
                val amt = etAmount.text.toString().trim().toLongOrNull() ?: 0L
                if (amt <= 0) {
                    Toast.makeText(this@MainActivity, "Summani kiriting!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val cur = spCur.selectedItem.toString()
                val note = etNote.text.toString().trim()
                db.addOrUpdatePersonTx(client.name, client.phone, amt, cur, selectedKind, note, "Bugun", isSupplier)

                val sms = if (selectedKind == "Kirim") {
                    "Hurmatli ${client.name}, $storeName do'konidagi qarzingizdan ${formatMoney(amt, cur)} to'lov qabul qilindi. Rahmat!"
                } else {
                    "Hurmatli ${client.name}, $storeName do'konida sizga ${formatMoney(amt, cur)} qarz yozildi ($note)."
                }
                sendAutoSms(client.phone, sms)
                sheet.dismiss()
                renderMainWorkspace()
            }
        }
        wrap.addView(btnSubmit)

        sheet.setContentView(wrap)
        sheet.show()
    }

    // 4-RASMDAGI TEZKOR TRANZAKSIYA SLIDE-UP BOTTOM SHEET
    private fun showQuickTransactionBottomSheet(isSupplier: Boolean) {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassSurface("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 30, 44, 56)
        }

        wrap.addView(View(this).apply {
            background = glassSurface("#CBD5E1", "#CBD5E1", 10f)
            layoutParams = LinearLayout.LayoutParams(120, 14).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 24
            }
        })

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
        }
        topRow.addView(TextView(this).apply {
            text = "Tezkor tranzaksiya"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topRow.addView(TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(16, 8, 16, 8)
            setOnClickListener { sheet.dismiss() }
        })
        wrap.addView(topRow)

        val etFind = makeGlassInput("🔍  Mijozni qidiring...", "")
        wrap.addView(etFind)

        wrap.addView(TextView(this).apply {
            text = "Yaqinda foydalanilgan"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(4, 12, 4, 20)
        })

        val hScroll = HorizontalScrollView(this)
        val rowCards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val recentPersons = db.getPersons(isSupplier)
        recentPersons.take(10).forEach { p ->
            val miniCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = glassSurface("#F8FAFC", "#E2E8F0", 34f)
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(270, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 18 }
                setOnClickListener {
                    sheet.dismiss()
                    showClientKirimChiqimBottomSheet(p, isSupplier)
                }
            }
            val ch = p.name.trim().firstOrNull()?.uppercaseChar() ?: 'M'
            miniCard.addView(TextView(this).apply {
                text = ch.toString()
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#4F46E5"))
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                background = glassSurface("#E0E7FF", "#C7D2FE", 60f)
                layoutParams = LinearLayout.LayoutParams(110, 110).apply { bottomMargin = 14 }
            })
            miniCard.addView(TextView(this).apply {
                text = p.name
                maxLines = 1
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
            })
            miniCard.addView(TextView(this).apply {
                text = formatMoney(Math.abs(p.balUzs), "UZS")
                setTextColor(if (p.balUzs < 0) Color.parseColor("#EA580C") else Color.parseColor("#059669"))
                textSize = 12f
            })
            rowCards.addView(miniCard)
        }
        hScroll.addView(rowCards)
        wrap.addView(hScroll)

        val btnNew = Button(this).apply {
            text = "➕ Yangi mijozga tezkor qarz yozish"
            setTextColor(Color.WHITE)
            background = glassPurpleGradient(32f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130).apply { topMargin = 28 }
            setOnClickListener {
                sheet.dismiss()
                showAddClientBottomSheet(isSupplier)
            }
        }
        wrap.addView(btnNew)

        sheet.setContentView(wrap)
        sheet.show()
    }

    // YANGI MIJOZ / QARZDOR QO'SHISH SLIDE-UP BOTTOM SHEET
    private fun showAddClientBottomSheet(isSupplier: Boolean) {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassSurface("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 30, 44, 56)
        }
        wrap.addView(View(this).apply {
            background = glassSurface("#CBD5E1", "#CBD5E1", 10f)
            layoutParams = LinearLayout.LayoutParams(120, 14).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 24
            }
        })
        wrap.addView(TextView(this).apply {
            text = if (isSupplier) "🚚 Ta'minotchi qo'shish" else "👤+ Yangi Mijoz / Qarzdor Qo'shish"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        })

        val etName = makeGlassInput("Ism (masalan: Sayfiddin jiyan) *", "")
        val etPhone = makeGlassInput("UZ Telefon (+998 94 265 90 11)", "+998 ")
        etPhone.inputType = InputType.TYPE_CLASS_PHONE
        val etDebt = makeGlassInput("Boshlang'ich qarz summasi (ixtiyoriy, UZS)", "")
        etDebt.inputType = InputType.TYPE_CLASS_NUMBER
        val etNote = makeGlassInput("Izoh / Olingan mahsulotlar", "")

        wrap.addView(etName)
        wrap.addView(etPhone)
        wrap.addView(etDebt)
        wrap.addView(etNote)

        val btnSave = Button(this).apply {
            text = "✅ Saqlash va Ro'yxatga Qo'shish"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glassPurpleGradient(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 135).apply { topMargin = 14 }
            setOnClickListener {
                val n = etName.text.toString().trim()
                val ph = etPhone.text.toString().trim()
                val debt = etDebt.text.toString().trim().toLongOrNull() ?: 0L
                val note = etNote.text.toString().trim()
                if (n.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Ismni kiriting!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (debt > 0) {
                    db.addOrUpdatePersonTx(n, ph, debt, "UZS", "Chiqim", note, "Bugun", isSupplier)
                    sendAutoSms(ph, "Hurmatli $n! $storeName do'konida sizga ${formatMoney(debt, "UZS")} qarz yozildi.")
                } else {
                    db.upsertClientDirect(n, ph, 0.0, 0L, db.nowStr(), isSupplier)
                }
                sheet.dismiss()
                renderMainWorkspace()
            }
        }
        wrap.addView(btnSave)
        sheet.setContentView(wrap)
        sheet.show()
    }

    // EKSPORT SLIDE-UP BOTTOM SHEET (DOWNLOAD PAPKASIGA SAQLASH + KANALGA YUBORISH)
    private fun showExportBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassSurface("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 30, 44, 56)
        }
        wrap.addView(View(this).apply {
            background = glassSurface("#CBD5E1", "#CBD5E1", 10f)
            layoutParams = LinearLayout.LayoutParams(120, 14).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 24
            }
        })
        wrap.addView(TextView(this).apply {
            text = "📤 Excel Eksport va Telegram Kanal"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })
        wrap.addView(TextView(this).apply {
            text = "6-betli hisobot (Mijozlar, Tranzaksiyalar, Ta'minotchilar, Kassa) telefonning 'Download' papkasiga saqlanadi hamda Telegram kanalingizga ($telegramChannel) yuboriladi."
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            setPadding(0, 0, 0, 24)
        })

        val btnDownload = Button(this).apply {
            text = "💾 Download papkaga saqlash (.csv / Excel)"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glassPurpleGradient(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130).apply { bottomMargin = 16 }
            setOnClickListener {
                val (file, savedPath) = saveExportFileToDownloads()
                sheet.dismiss()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("✅ Download Papkasiga Saqlandi!")
                    .setMessage("Fayl nomi:\n${file.name}\n\nSaqlangan joyi:\n$savedPath")
                    .setPositiveButton("📤 Telegram Kanalga Yuborish") { _, _ ->
                        exportAllSheetsAndSendToChannel(manual = true)
                    }
                    .setNegativeButton("Yopish", null)
                    .show()
            }
        }

        val btnSendChannel = Button(this).apply {
            text = "📢 Hoziroq Telegram Kanalga Yuborish"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glassOrangeGradient(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130).apply { bottomMargin = 16 }
            setOnClickListener {
                sheet.dismiss()
                exportAllSheetsAndSendToChannel(manual = true)
            }
        }

        val btnConfigChannel = Button(this).apply {
            text = "⚙️ Kanal linki va Bot Tokenni sozlash ($telegramChannel)"
            setTextColor(Color.parseColor("#1E293B"))
            background = glassSurface("#F1F5F9", "#CBD5E1", 32f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120)
            setOnClickListener {
                sheet.dismiss()
                openChannelSettingsDialog()
            }
        }

        wrap.addView(btnDownload)
        wrap.addView(btnSendChannel)
        wrap.addView(btnConfigChannel)
        sheet.setContentView(wrap)
        sheet.show()
    }

    // IMPORT SLIDE-UP BOTTOM SHEET (.XLSX VA .CSV FAYLLARNI 100% O'QIYDIGAN PARSER BILAN)
    private fun showImportBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassSurface("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 30, 44, 56)
        }
        wrap.addView(View(this).apply {
            background = glassSurface("#CBD5E1", "#CBD5E1", 10f)
            layoutParams = LinearLayout.LayoutParams(120, 14).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 24
            }
        })
        wrap.addView(TextView(this).apply {
            text = "📥 Excel (.xlsx / .csv) Import Qilish"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })
        wrap.addView(TextView(this).apply {
            text = "clients_export.xlsx yoki .csv faylni tanlang. Ichidagi barcha mijozlar, telefon raqamlar va '-4,033,000' ko'rinishidagi balanslar avtomatik o'qiladi!"
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            setPadding(0, 0, 0, 24)
        })

        val btnPickFile = Button(this).apply {
            text = "📂 Telefondan Excel (.xlsx / .csv) Fayl Tanlash"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glassPurpleGradient(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 135).apply { bottomMargin = 16 }
            setOnClickListener {
                sheet.dismiss()
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, PICK_IMPORT_FILE)
            }
        }
        wrap.addView(btnPickFile)
        sheet.setContentView(wrap)
        sheet.show()
    }

    // ==================== 3. DOWNLOAD PAPKASIGA SAQLASH VA .XLSX/.CSV IMPORT PARSER ====================
    private fun saveExportFileToDownloads(): Pair<File, String> {
        val content = buildSixSheetCsvContent()
        val fileName = "clients_export_${System.currentTimeMillis()}.csv"

        // 1) App external dir file (ulashish va Bot API uchun)
        val localFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        localFile.writeText(content, Charsets.UTF_8)

        var publicPath = localFile.absolutePath

        // 2) Telefonning haqiqiy "Download" papkasiga yozish
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    publicPath = "/storage/emulated/0/Download/$fileName"
                }
            } else {
                val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dlDir.exists()) dlDir.mkdirs()
                val dlFile = File(dlDir, fileName)
                dlFile.writeText(content, Charsets.UTF_8)
                publicPath = dlFile.absolutePath
            }
        } catch (_: Exception) {}

        return Pair(localFile, publicPath)
    }

    private fun buildSixSheetCsvContent(): String {
        val clients = db.getPersons(false)
        val clientTxs = db.getTransactions(false)
        val suppliers = db.getPersons(true)
        val supTxs = db.getTransactions(true)
        val kassaList = db.getKassaList()

        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("ID,Ism,Telefon raqami,Balans USD,Balans UZS,Yaratilgan sana\n")
        clients.forEach { c ->
            sb.append("${c.id},\"${c.name}\",\"${c.phone}\",${c.balUsd},\"${c.balUzs}\",\"${c.createdAt}\"\n")
        }
        sb.append("\nTranzaksiya ID,Turi,Mijoz ismi,Mijoz telefoni,Summa,Valyuta,Sana,Tavsif,Holat\n")
        clientTxs.forEach { t ->
            sb.append("${t.txId},${t.kind},\"${t.personName}\",\"${t.personPhone}\",\"${t.amount}\",${t.currency},\"${t.date}\",\"${t.note}\",${t.status}\n")
        }
        sb.append("\nID,Ism,Telefon raqami,Balans USD,Balans UZS,Manzil,Yaratilgan sana\n")
        suppliers.forEach { s ->
            sb.append("${s.id},\"${s.name}\",\"${s.phone}\",${s.balUsd},\"${s.balUzs}\",\"$storeAddress\",\"${s.createdAt}\"\n")
        }
        sb.append("\nTranzaksiya ID,Turi,Ta'minotchi ismi,Ta'minotchi telefoni,Summa,Valyuta,Sana,Tavsif,Holat\n")
        supTxs.forEach { t ->
            sb.append("${t.txId},${t.kind},\"${t.personName}\",\"${t.personPhone}\",\"${t.amount}\",${t.currency},\"${t.date}\",\"${t.note}\",${t.status}\n")
        }
        sb.append("\nID,Turi,Kategoriya,Summa,Valyuta,To'lov turi,Sana,Tavsif,Kim yaratdi\n")
        kassaList.forEach { k ->
            sb.append("${k.id},${k.type},\"${k.category}\",\"${k.amount}\",${k.currency},${k.payType},\"${k.date}\",\"${k.note}\",\"${k.creator}\"\n")
        }
        return sb.toString()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMPORT_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return
                    val importedCount = if (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                        // Haqiqiy .XLSX (ZIP/XML) fayl!
                        parseXlsxZipBytes(bytes)
                    } else {
                        // .CSV yoki .TSV matnli fayl!
                        val text = String(bytes, Charsets.UTF_8)
                        parseCsvSmartLines(text.lines())
                    }
                    Toast.makeText(this, "✅ Jami $importedCount ta mijoz muvaffaqiyatli import qilindi!", Toast.LENGTH_LONG).show()
                    renderMainWorkspace()
                } catch (e: Exception) {
                    Toast.makeText(this, "Import xatosi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Haqiqiy .xlsx faylning ichidan 'xl/sharedStrings.xml' va 'xl/worksheets/sheet1.xml' ni o'quvchi parser
    private fun parseXlsxZipBytes(bytes: ByteArray): Int {
        val entries = mutableMapOf<String, ByteArray>()
        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        var entry = zis.nextEntry
        while (entry != null) {
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var len: Int
            while (zis.read(buf).also { len = it } > 0) {
                baos.write(buf, 0, len)
            }
            entries[entry.name] = baos.toByteArray()
            entry = zis.nextEntry
        }
        zis.close()

        val sharedStrings = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { ssBytes ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(ByteArrayInputStream(ssBytes), "UTF-8")
            var event = parser.eventType
            var currentSi = StringBuilder()
            var insideSi = false
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "si") {
                    insideSi = true
                    currentSi = StringBuilder()
                } else if (event == XmlPullParser. START_TAG && parser.name == "t" && insideSi) {
                    currentSi.append(parser.nextText())
                } else if (event == XmlPullParser.END_TAG && parser.name == "si") {
                    insideSi = false
                    sharedStrings.add(currentSi.toString())
                }
                event = parser.next()
            }
        }

        val sheetBytes = entries["xl/worksheets/sheet1.xml"] ?: return 0
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(ByteArrayInputStream(sheetBytes), "UTF-8")
        var event = parser.eventType

        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var cellType = ""
        var cellValue = ""

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> {
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            cellValue = ""
                        }
                        "v", "t" -> {
                            val raw = parser.nextText()
                            cellValue = if (cellType == "s") {
                                val idx = raw.toIntOrNull() ?: -1
                                if (idx in sharedStrings.indices) sharedStrings[idx] else raw
                            } else {
                                raw
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "c") {
                        currentRow.add(cellValue)
                    } else if (parser.name == "row") {
                        if (currentRow.isNotEmpty()) rows.add(currentRow)
                    }
                }
            }
            event = parser.next()
        }

        var count = 0
        rows.forEachIndexed { idx, r ->
            if (idx > 0 && r.size >= 3) {
                val name = r.getOrNull(1)?.trim() ?: ""
                val phone = r.getOrNull(2)?.trim() ?: ""
                val usdStr = r.getOrNull(3)?.replace(",", "")?.trim() ?: "0"
                val uzsStr = r.getOrNull(4)?.replace(",", "")?.replace(" ", "")?.trim() ?: "0"
                val dateStr = r.getOrNull(5)?.trim() ?: db.nowStr()

                if (name.isNotEmpty() && !name.equals("Ism", ignoreCase = true)) {
                    val usd = usdStr.toDoubleOrNull() ?: 0.0
                    val uzs = uzsStr.toDoubleOrNull()?.toLong() ?: 0L
                    db.upsertClientDirect(name, phone, usd, uzs, dateStr, false)
                    count++
                }
            }
        }
        return count
    }

    // Qo'shtirnoq ichidagi vergullarni (-4,033,000) buzmasdan o'quvchi aqlli CSV parser
    private fun parseCsvSmartLines(lines: List<String>): Int {
        var count = 0
        lines.forEach { rawLine ->
            val line = rawLine.replace("\uFEFF", "").trim()
            if (line.isEmpty() || line.startsWith("ID") || line.startsWith("===") || line.startsWith("Tranzaksiya")) return@forEach

            val cols = splitCsvRespectingQuotes(line)
            if (cols.size >= 3) {
                val name = cols.getOrNull(1)?.trim() ?: ""
                val phone = cols.getOrNull(2)?.trim() ?: ""
                val usdStr = cols.getOrNull(3)?.replace(",", "")?.trim() ?: "0"
                val uzsStr = cols.getOrNull(4)?.replace(",", "")?.replace(" ", "")?.trim() ?: "0"
                val dateStr = cols.getOrNull(5)?.trim() ?: db.nowStr()

                if (name.isNotEmpty() && !name.equals("Ism", ignoreCase = true)) {
                    val usd = usdStr.toDoubleOrNull() ?: 0.0
                    val uzs = uzsStr.toDoubleOrNull()?.toLong() ?: 0L
                    db.upsertClientDirect(name, phone, usd, uzs, dateStr, false)
                    count++
                }
            }
        }
        return count
    }

    private fun splitCsvRespectingQuotes(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                (ch == ',' || ch == ';') && !inQuotes -> {
                    result.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        result.add(cur.toString())
        return result
    }

    private fun exportAllSheetsAndSendToChannel(manual: Boolean) {
        val (file, savedPath) = saveExportFileToDownloads()
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val caption = "📊 QarzFy Kunlik Excel Hisobot\n🏪 Do'kon: $storeName ($storeId)\n📢 Kanal: $telegramChannel\n⏰ Vaqt: $dateStr"

        if (botToken.isNotBlank() && telegramChannel.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                val ok = sendDocumentViaTelegramBotApi(botToken, telegramChannel, file, caption)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        Toast.makeText(this@MainActivity, "✅ Hisobot Download'ga saqlandi va $telegramChannel kanaliga joylandi!", Toast.LENGTH_LONG).show()
                    } else if (manual) {
                        Toast.makeText(this@MainActivity, "💾 Download'ga saqlandi: $savedPath", Toast.LENGTH_LONG).show()
                        shareFileToTelegramIntent(file, caption)
                    }
                }
            }
        } else if (manual) {
            Toast.makeText(this, "💾 Download'ga saqlandi:\n$savedPath", Toast.LENGTH_LONG).show()
            shareFileToTelegramIntent(file, caption)
        }
    }

    private fun sendDocumentViaTelegramBotApi(token: String, chatId: String, file: File, caption: String): Boolean {
        return try {
            val boundary = "----QarzFyBoundary" + System.currentTimeMillis()
            val url = URL("https://api.telegram.org/bot$token/sendDocument")
            val conn = url.openConnection() as HttpURLConnection
            conn.useCaches = false
            conn.doOutput = true
            conn.doInput = true
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val out = DataOutputStream(conn.outputStream)
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n$chatId\r\n")
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"caption\"\r\n\r\n$caption\r\n")
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"\r\n")
            out.writeBytes("Content-Type: text/csv\r\n\r\n")
            out.write(file.readBytes())
            out.writeBytes("\r\n--$boundary--\r\n")
            out.flush()
            out.close()

            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun shareFileToTelegramIntent(file: File, caption: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$caption\n\n📂 Fayl manzili: Download/${file.name}")
            }
            startActivity(Intent.createChooser(intent, "Telegram kanalga yuborish"))
        } catch (_: Exception) {}
    }

    private fun scheduleMidnightChannelExport() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val cal = Calendar.getInstance()
                if (cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0) {
                    withContext(Dispatchers.Main) {
                        exportAllSheetsAndSendToChannel(manual = false)
                    }
                    delay(65_000L)
                }
                delay(25_000L)
            }
        }
    }

    // ==================== YANGI TARIFLAR BOTTOM SHEET (1 OY 49 000, 3 OY 135 000, 6 OY 255 000) ====================
    private fun showSubscriptionTariffsBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassSurface("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 30, 44, 56)
        }
        wrap.addView(View(this).apply {
            background = glassSurface("#CBD5E1", "#CBD5E1", 10f)
            layoutParams = LinearLayout.LayoutParams(120, 14).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 24
            }
        })
        wrap.addView(TextView(this).apply {
            text = "💎 Obuna Tariflari va Vaqt Qo'shish"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
        })
        wrap.addView(TextView(this).apply {
            text = "Joriy holat: ${remainingDaysText()}\nTanlangan tarif muddati mavjud obunangiz ustiga avtomatik qo'shiladi va Telegram botga yuboriladi:"
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            setPadding(0, 8, 0, 24)
        })

        wrap.addView(tariffButton("💳 1 Oylik Tarif — 49 000 so'm", "Botga '1 oy' so'rovi yuboriladi va +30 kun qo'shiladi", "#6D28D9") {
            sheet.dismiss()
            addSubscriptionMonths(1, 49000)
        })
        wrap.addView(tariffButton("🔥 3 Oylik Tarif — 135 000 so'm", "Botga '3 oy' so'rovi yuboriladi va +90 kun qo'shiladi", "#2563EB") {
            sheet.dismiss()
            addSubscriptionMonths(3, 135000)
        })
        wrap.addView(tariffButton("👑 6 Oylik Tarif — 255 000 so'm", "Botga '6 oy' so'rovi yuboriladi va +180 kun qo'shiladi", "#059669") {
            sheet.dismiss()
            addSubscriptionMonths(6, 255000)
        })

        sheet.setContentView(wrap)
        sheet.show()
    }

    // ==================== TAB 1, 2, 3, 4 (KASSA, MAHSULOT, STATISTIKA, PROFIL) ====================
    private fun buildEmployeesSection(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val btn = Button(this).apply {
            text = "➕ Xodim qo'shish (Oylik / Kunlik)"
            setTextColor(Color.WHITE)
            background = glassPurpleGradient(32f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 125).apply { bottomMargin = 20 }
            setOnClickListener { openAddEmployeeDialog() }
        }
        col.addView(btn)
        val emps = db.getEmployees()
        if (emps.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "👨‍💼 Xodimlar topilmadi"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#64748B"))
                background = glassSurface("#FFFFFF", "#E2E8F0", 36f)
                setPadding(40, 60, 40, 60)
            })
        } else {
            emps.forEach { (n, p) ->
                col.addView(TextView(this).apply {
                    text = "👨‍💼 $n  •  📞 $p"
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    background = glassSurface("#FFFFFF", "#E2E8F0", 32f)
                    setPadding(34, 28, 34, 28)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                })
            }
        }
        return col
    }

    private fun buildKassaTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 200)
        }
        val list = db.getKassaList()
        var kIn = 0L
        var kOut = 0L
        list.forEach { if (it.type == "Kirim") kIn += it.amount else kOut += it.amount }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassPurpleGradient(44f)
            setPadding(40, 38, 40, 38)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
        }
        card.addView(TextView(this).apply {
            text = "💵 Kassa Balansi: ${formatMoney(kIn - kOut)}"
            setTextColor(Color.WHITE)
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "Kirimlar: ${formatMoney(kIn)}   |   Xarajatlar: ${formatMoney(kOut)}"
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 13f
            setPadding(0, 10, 0, 0)
        })
        col.addView(card)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        row.addView(Button(this).apply {
            text = "➕ Kirim"
            setTextColor(Color.WHITE)
            background = glassPurpleGradient(30f)
            layoutParams = LinearLayout.LayoutParams(0, 125, 1f).apply { rightMargin = 10 }
            setOnClickListener { openAddKassaDialog("Kirim") }
        })
        row.addView(Button(this).apply {
            text = "➖ Xarajat"
            setTextColor(Color.WHITE)
            background = glassOrangeGradient(30f)
            layoutParams = LinearLayout.LayoutParams(0, 125, 1f).apply { leftMargin = 10 }
            setOnClickListener { openAddKassaDialog("Chiqim") }
        })
        col.addView(row)

        list.forEach { k ->
            col.addView(TextView(this).apply {
                text = "${if (k.type == "Kirim") "🟢" else "🔴"} ${k.category} (${k.payType}) — ${formatMoney(k.amount)}\n${k.date} • ${k.note}"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 14f
                background = glassSurface("#FFFFFF", "#E2E8F0", 32f)
                setPadding(32, 26, 32, 26)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
            })
        }
        scroll.addView(col)
        return scroll
    }

    private fun buildMahsulotTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 200)
        }
        val prods = db.getProducts()
        var totalCost = 0L
        var totalSale = 0L
        prods.forEach { totalCost += it.costPrice * it.qty; totalSale += it.salePrice * it.qty }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassPurpleGradient(44f)
            setPadding(40, 36, 40, 36)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        card.addView(TextView(this).apply {
            text = "📦 Ombor qiymati: ${formatMoney(totalCost)}"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "Sotuv narxi: ${formatMoney(totalSale)} • Kutilayotgan foyda: ${formatMoney(totalSale - totalCost)}"
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 12.5f
            setPadding(0, 8, 0, 0)
        })
        col.addView(card)

        col.addView(Button(this).apply {
            text = "➕ Mahsulot yaratish / Kirim qilish"
            setTextColor(Color.WHITE)
            background = glassPurpleGradient(32f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130).apply { bottomMargin = 20 }
            setOnClickListener { openAddProductDialog() }
        })

        prods.forEach { pr ->
            col.addView(TextView(this).apply {
                text = "📦 ${pr.name} (${pr.qty} ${pr.unit})\nTan narx: ${formatMoney(pr.costPrice)} | Sotuv: ${formatMoney(pr.salePrice)}"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 14f
                background = glassSurface("#FFFFFF", "#E2E8F0", 32f)
                setPadding(32, 26, 32, 26)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
            })
        }
        scroll.addView(col)
        return scroll
    }

    private fun buildStatistikaTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 200)
        }
        val clients = db.getPersons(false)
        var jamiQarz = 0L
        clients.forEach { if (it.balUzs < 0) jamiQarz += Math.abs(it.balUzs) }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassSurface("#FFFFFF", "#E2E8F0", 42f)
            setPadding(38, 36, 38, 36)
        }
        card.addView(TextView(this).apply {
            text = "📊 Umumiy Statistika"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "\n👥 Mijozlar soni: ${clients.size} ta\n🔴 Jami qarzlar: ${formatMoney(jamiQarz)}\n📢 Kanal: $telegramChannel\n⏳ Obuna: ${remainingDaysText()}"
            setTextColor(Color.parseColor("#334155"))
            textSize = 14.5f
        })
        col.addView(card)
        scroll.addView(col)
        return scroll
    }

    // 1-RASMDAGI PROFIL TABI (2 USTUNLI GRID KARTOCHKALAR BILAN)
    private fun buildProfilTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 220)
        }

        // Profil Top Header (1-rasmdagidek)
        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glassSurface("#FFFFFF", "#E2E8F0", 44f)
            setPadding(34, 30, 34, 30)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        val profAvatar = TextView(this).apply {
            text = "👤\nFaol"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = glassPurpleGradient(60f)
            layoutParams = LinearLayout.LayoutParams(135, 135).apply { rightMargin = 26 }
        }
        val profInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        profInfo.addView(TextView(this).apply {
            text = storeName
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
        })
        profInfo.addView(TextView(this).apply {
            text = "$userPhone  •  Biznes egasi"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 13f
        })
        headerCard.addView(profAvatar)
        headerCard.addView(profInfo)
        col.addView(headerCard)

        // 2 ustunli kartochkalar: Bildirishnomalar | Limitlar va tarif
        val r1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        r1.addView(profileGridCard("🔔 Bildirishnomalar", "08:00 / 20:00 eslatma") { showPushSettingsDialog() })
        r1.addView(profileGridCard("💎 Limitlar va tarif", remainingDaysText()) { showSubscriptionTariffsBottomSheet() })
        col.addView(r1)

        // Asosiy Balans | Referal hisob
        val r2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        r2.addView(profileGridCard("💳 Obuna xarid qilish", "49k / 135k / 255k UZS") { showSubscriptionTariffsBottomSheet() })
        r2.addView(profileGridCard("🎁 Referal hisob", "0 UZS • Do'st chaqiring") { showReferralDialog() })
        col.addView(r2)

        // SMS qoldi kartochkasi (1-rasmdagidek yashil progress chizig'i bilan)
        val smsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassSurface("#FFFFFF", "#E2E8F0", 38f)
            setPadding(36, 30, 36, 30)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
            setOnClickListener { showSmsSettingsDialog() }
        }
        smsCard.addView(TextView(this).apply {
            text = "💬 SMS  •  Cheksiz SIM Avto-SMS faol"
            setTextColor(Color.parseColor("#059669"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        })
        smsCard.addView(View(this).apply {
            background = glassSurface("#10B981", "#10B981", 10f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 16).apply { topMargin = 16 }
        })
        col.addView(smsCard)

        val settingsList = listOf(
            "📢 Telegram Kanal va 00:00 Avto-Excel ($telegramChannel)" to { openChannelSettingsDialog() },
            "📤 Hozir Kanalga va Download'ga Excel Eksport" to { showExportBottomSheet() },
            "📥 Excel (.xlsx / .csv) Fayldan Mijozlarni Import Qilish" to { showImportBottomSheet() },
            "🖨 Bluetooth Printer Sozlamalari (58mm / 80mm / A4)" to { showPrinterSettingsDialog() },
            "🤝 Hamjamiyat va Ambassador Dasturi" to { showCommunityAndAmbassadorDialog() },
            "👨‍💻 Dastur haqida: Dasturchi Hasan Jaylovov (@HasanJaylovov)" to { showDeveloperDialog() }
        )
        settingsList.forEach { (title, act) ->
            col.addView(TextView(this).apply {
                text = "$title   ❯"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                background = glassSurface("#FFFFFF", "#E2E8F0", 34f)
                setPadding(34, 28, 34, 28)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                setOnClickListener { act.invoke() }
            })
        }

        scroll.addView(col)
        return scroll
    }

    private fun profileGridCard(title: String, sub: String, onClick: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassSurface("#FFFFFF", "#E2E8F0", 36f)
            setPadding(30, 28, 30, 28)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(6, 0, 6, 0)
            }
            setOnClickListener { onClick() }
        }
        card.addView(TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = sub
            setTextColor(Color.parseColor("#059669"))
            textSize = 12f
            setPadding(0, 8, 0, 0)
        })
        return card
    }

    private fun buildBottomNavBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glassSurface("#FFFFFF", "#E2E8F0", 0f)
            elevation = 20f
            setPadding(12, 16, 12, 16)
            gravity = Gravity.CENTER
        }
        val tabs = listOf(
            "🏠\nAsosiy",
            "💵\nKassa",
            "📦\nMahsulot",
            "📊\nStatistika",
            "👤\nProfil"
        )
        tabs.forEachIndexed { idx, title ->
            val active = (currentBottomTab == idx)
            val btn = TextView(this).apply {
                text = title
                gravity = Gravity.CENTER
                textSize = 11.5f
                setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (active) Color.parseColor("#4F46E5") else Color.parseColor("#64748B"))
                background = if (active) glassSurface("#EEF2FF", "#C7D2FE", 28f) else null
                setPadding(8, 14, 8, 14)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(4, 0, 4, 0)
                }
                setOnClickListener {
                    currentBottomTab = idx
                    isRightSpeedDialOpen = false
                    renderMainWorkspace()
                }
            }
            bar.addView(btn)
        }
        return bar
    }

    // ==================== YORDAMCHI DIALOGLAR ====================
    private fun openShoppingListDialog() {
        val et = EditText(this).apply { hint = "Bozorlik ro'yxati (masalan: Tovar 1, Tovar 2)" }
        AlertDialog.Builder(this)
            .setTitle("📋 Bozorlik Ro'yxati")
            .setView(et)
            .setPositiveButton("Saqlash", null)
            .show()
    }

    private fun openAddKassaDialog(type: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 25, 45, 10)
        }
        val etCat = EditText(this).apply { hint = "Kategoriya (masalan: Savdo, Ijara)" }
        val etAmt = EditText(this).apply { hint = "Summa (UZS)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val etNote = EditText(this).apply { hint = "Izoh" }
        layout.addView(etCat); layout.addView(etAmt); layout.addView(etNote)

        AlertDialog.Builder(this)
            .setTitle("💵 Kassaga $type")
            .setView(layout)
            .setPositiveButton("Saqlash") { _, _ ->
                val amt = etAmt.text.toString().toLongOrNull() ?: 0L
                if (amt > 0) {
                    db.addKassa(type, etCat.text.toString().ifEmpty { "Umumiy" }, amt, "UZS", "Naqd", etNote.text.toString(), storeName)
                    renderMainWorkspace()
                }
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun openAddProductDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 25, 45, 10)
        }
        val etName = EditText(this).apply { hint = "Mahsulot nomi *" }
        val etQty = EditText(this).apply { hint = "Miqdori"; inputType = InputType.TYPE_CLASS_NUMBER; setText("1") }
        val etCost = EditText(this).apply { hint = "Tan narxi (UZS)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val etSale = EditText(this).apply { hint = "Sotuv narxi (UZS)"; inputType = InputType.TYPE_CLASS_NUMBER }
        layout.addView(etName); layout.addView(etQty); layout.addView(etCost); layout.addView(etSale)

        AlertDialog.Builder(this)
            .setTitle("📦 Mahsulot Qo'shish")
            .setView(layout)
            .setPositiveButton("Saqlash") { _, _ ->
                val n = etName.text.toString().trim()
                if (n.isNotEmpty()) {
                    db.addProduct(n, "BC-101", etQty.text.toString().toIntOrNull() ?: 1, "dona", etCost.text.toString().toLongOrNull() ?: 0L, etSale.text.toString().toLongOrNull() ?: 0L, "UZS", storeName)
                    renderMainWorkspace()
                }
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun openAddEmployeeDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 25, 45, 10)
        }
        val etName = EditText(this).apply { hint = "Xodim ismi" }
        val etPhone = EditText(this).apply { hint = "Telefon"; setText("+998") }
        layout.addView(etName); layout.addView(etPhone)
        AlertDialog.Builder(this)
            .setTitle("👨‍💼 Xodim Qo'shish")
            .setView(layout)
            .setPositiveButton("Saqlash") { _, _ ->
                if (etName.text.isNotEmpty()) {
                    db.addEmployee(etName.text.toString(), etPhone.text.toString(), "Oylik")
                    renderMainWorkspace()
                }
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun openCreateFolderDialog() {
        val et = EditText(this).apply { hint = "Papka nomi" }
        AlertDialog.Builder(this)
            .setTitle("📁 Papka Yaratish")
            .setView(et)
            .setPositiveButton("Saqlash", null)
            .show()
    }

    private fun openSalesCalculatorDialog() {
        val et = EditText(this).apply { hint = "Summa (UZS)"; inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this)
            .setTitle("🧮 Kalkulyator")
            .setView(et)
            .setPositiveButton("Kassaga Kirim") { _, _ ->
                val v = et.text.toString().toLongOrNull() ?: 0L
                if (v > 0) db.addKassa("Kirim", "Kalkulyator Sotuv", v, "UZS", "Naqd", "", storeName)
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun openChannelSettingsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 25, 45, 10)
        }
        val etCh = EditText(this).apply { hint = "Telegram kanal (@kanal_nomi)"; setText(telegramChannel) }
        val etTk = EditText(this).apply { hint = "Bot Token (ixtiyoriy)"; setText(botToken) }
        layout.addView(etCh); layout.addView(etTk)

        AlertDialog.Builder(this)
            .setTitle("📢 Telegram Kanal Sozlamasi")
            .setView(layout)
            .setPositiveButton("Saqlash") { _, _ ->
                telegramChannel = etCh.text.toString().trim()
                botToken = etTk.text.toString().trim()
                prefs.edit().putString("telegram_channel", telegramChannel).putString("bot_token", botToken).apply()
                renderMainWorkspace()
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun showSmsSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("💬 SMS Sozlamalari")
            .setMessage("SIM-karta orqali qarzdorlarga avtomatik SMS eslatma yuborish yoqilgan.")
            .setPositiveButton("Yopish", null)
            .show()
    }

    private fun showPushSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("🔔 Bildirishnomalar")
            .setMessage("• Tonggi salomlashish: 08:00\n• Kechki qarz eslatmasi: 20:00\n• Har kuni 00:00 da Kanalga Excel yuborish: Faol")
            .setPositiveButton("Yopish", null)
            .show()
    }

    private fun showVoiceSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("📞 Ovozli Eslatma va Qo'ng'iroq")
            .setMessage("Mijozlarga o'zbek tilida (Nigora ovozi) avtomatik eslatma.")
            .setPositiveButton("Yopish", null)
            .show()
    }

    private fun showPrinterSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("🖨 Bluetooth Printer")
            .setMessage("Qog'oz eni: 80mm / 58mm / A4.")
            .setPositiveButton("Test chek", null)
            .show()
    }

    private fun showQrCodeAndOpenPageDialog() {
        AlertDialog.Builder(this)
            .setTitle("🔳 Mening QR-kodim va Ochiq Sahifa")
            .setMessage("Do'kon: $storeName\nHavola: https://qarzfy.uz/sh/$storeId")
            .setPositiveButton("Ulashish", null)
            .show()
    }

    private fun showCommunityAndAmbassadorDialog() {
        AlertDialog.Builder(this)
            .setTitle("🤝 Hamjamiyat va Vazifalar")
            .setMessage("Story joylang va +5 000 UZS bonus oling!")
            .setPositiveButton("Yopish", null)
            .show()
    }

    private fun showReferralDialog() {
        AlertDialog.Builder(this)
            .setTitle("🎁 Referal Tizim")
            .setMessage("Referal kodingiz: $storeId")
            .setPositiveButton("Yopish", null)
            .show()
    }

    private fun openBusinessInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("🏪 Biznes Ma'lumotlari")
            .setMessage("Do'kon: $storeName\nID: $storeId\nObuna: ${remainingDaysText()}")
            .setPositiveButton("Tarifni uzaytirish") { _, _ -> showSubscriptionTariffsBottomSheet() }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun showDeveloperDialog() {
        AlertDialog.Builder(this)
            .setTitle("👨‍💻 Dastur Haqida")
            .setMessage("Ilova: QarzFy v5.0 (Realistic Glassmorphism)\n\nDasturchi: Hasan Jaylovov\nTelegram: @HasanJaylovov")
            .setPositiveButton("Bog'lanish (@HasanJaylovov)") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/HasanJaylovov")))
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun sendBulkSmsReminders(isSupplier: Boolean) {
        val list = db.getPersons(isSupplier)
        var sent = 0
        list.forEach { p ->
            if (p.balUzs < 0) {
                sendAutoSms(p.phone, "Hurmatli ${p.name}, $storeName do'konidan ${formatMoney(Math.abs(p.balUzs))} qarzingiz bor.")
                sent++
            }
        }
        Toast.makeText(this, "📩 $sent ta qarzdorga SMS yuborildi!", Toast.LENGTH_LONG).show()
    }

    private fun sendAutoSms(phoneNumber: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
        } catch (_: Exception) {}
    }
}
