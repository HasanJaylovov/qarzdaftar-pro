package uz.qarzdaftar.pro

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

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

class QarzFyDatabase(context: Context) : SQLiteOpenHelper(context, "qarzfy_v4_glass.db", null, 1) {
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

        val prefix = if (isSupplier) "ST-" else "D-"
        val txId = prefix + (100000 + Random().nextInt(900000))
        db.execSQL(
            "INSERT INTO transactions (tx_id, kind, person_name, person_phone, amount, currency, date, note, status, is_supplier) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Faol', ?)",
            arrayOf<Any>(txId, kind, name, phone, Math.abs(amount), currency, nowStr(), note, supFlag)
        )
    }

    fun getPersons(isSupplier: Boolean, query: String = "", sortMode: String = "Faol"): List<ClientRec> {
        val list = mutableListOf<ClientRec>()
        val supFlag = if (isSupplier) 1 else 0
        val orderBy = when (sortMode) {
            "Summa" -> "ABS(bal_uzs) DESC"
            "A-Z" -> "name ASC"
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
    private var telegramChannel = ""
    private var botToken = ""

    private var currentBottomTab = 0
    private var currentHomeSubTab = 0 // 0=Mijozlar, 1=Ta'minotchilar, 2=Xodimlar
    private var currentSortMode = "Faol"
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE),
            100
        )

        prefs = getSharedPreferences("QarzFyGlassPrefs", Context.MODE_PRIVATE)
        db = QarzFyDatabase(this)
        rootLayout = findViewById(R.id.mainContentLayout)

        loadPrefs()

        if (userPhone.isBlank()) {
            renderOnboardingAndLogin()
        } else {
            renderMainWorkspace()
            scheduleMidnightChannelExport()
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
    }

    private fun glassBox(bgColor: String = "#1A2639", strokeColor: String = "#4D38BDF8", rad: Float = 42f): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = rad
            setColor(Color.parseColor(bgColor))
            setStroke(3, Color.parseColor(strokeColor))
        }
    }

    private fun glassGradientBox(c1: String, c2: String, strokeColor: String = "#80FFFFFF", radius: Float = 44f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
        ).apply {
            cornerRadius = radius
            setStroke(3, Color.parseColor(strokeColor))
        }
    }

    private fun formatMoney(amount: Long, cur: String = mainCurrency): String {
        val nf = NumberFormat.getInstance(Locale.US)
        return nf.format(amount).replace(",", " ") + " $cur"
    }

    // ==================== 1. ONBOARDING & LOGIN (PDF 1-7 betlar) ====================
    private fun renderOnboardingAndLogin() {
        rootLayout.removeAllViews()
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 70, 48, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(220, 220).apply { bottomMargin = 24 }
        }
        container.addView(logo)

        val title = TextView(this).apply {
            text = "QarzFy bular uchun yaratilgan!"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val sub = TextView(this).apply {
            text = "Mijozlar, ta'minotchilar, ombor va kassa — hammasini Glassy 3D muhitida boshqaring."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(10, 12, 10, 36)
        }
        container.addView(title)
        container.addView(sub)

        val features = listOf(
            "👥 Mijozlar • Qarzlar va Avto-SMS avtomatlashtirish",
            "🚚 Ta'minotchilar • Kirim va qarzlar hisobi",
            "💵 Kassa • Naqd va Plastik moliya nazorati",
            "📦 Ombor • Mahsulot, Shtrix-kod va Sotuv",
            "📊 Telegram Kanal • Har kuni 00:00 da avto Excel hisobot"
        )
        features.forEach { f ->
            val tv = TextView(this).apply {
                text = f
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 13f
                background = glassBox("#152238", "#3338BDF8", 30f)
                setPadding(32, 24, 32, 24)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
            }
            container.addView(tv)
        }

        val formCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassGradientBox("#1E293B", "#0F172A", "#6638BDF8", 46f)
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
        }

        val formTitle = TextView(this).apply {
            text = "Do'kon va Profilni Ro'yxatdan O'tkazish"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        formCard.addView(formTitle)

        val etStore = makeInput("Do'kon nomi * (Masalan: Hasan Jaylovov)", storeName)
        val etAddr = makeInput("Manzil (Masalan: O'zbekiston, Surxondaryo)", storeAddress)

        val categories = arrayOf(
            "Qurilish mollari", "Oziq-ovqat va ichimliklar", "Telefon va aksessuarlar",
            "Kiyim-kechak va aksessuarlar", "Avto ehtiyot qismlari", "Uy-ro'zg'or buyumlari",
            "Dorixona / Med-mahsulotlar", "Konstovar / Ofis jihozlari", "Kompyuter va texnika",
            "Restoran / Kafe / Bar", "Go'zallik saloni", "Buxgalteriya xizmatlari", "Yem mahsulotlari", "Boshqa"
        )
        val spCategory = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categories)
            background = glassBox("#0F172A", "#4D38BDF8", 28f)
            setPadding(20, 12, 20, 12)
        }

        val currencies = arrayOf("UZS", "USD")
        val spCurrency = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, currencies)
            background = glassBox("#0F172A", "#4D38BDF8", 28f)
            setPadding(20, 12, 20, 12)
        }

        val etPhone = makeInput("Telefon raqam (+998909958297)", "+998")
        etPhone.inputType = InputType.TYPE_CLASS_PHONE
        val etPass = makeInput("Parol (Admin panel uchun)", "")
        etPass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val etChannel = makeInput("Telegram Kanal (@kanal_linki yoki Chat ID)", telegramChannel)
        val etToken = makeInput("Telegram Bot Token (00:00 avto-eksport uchun, ixtiyoriy)", botToken)

        formCard.addView(etStore)
        formCard.addView(etAddr)
        formCard.addView(labelTv("Biznes kategoriyasi:"))
        formCard.addView(spCategory)
        formCard.addView(labelTv("Asosiy valyuta:"))
        formCard.addView(spCurrency)
        formCard.addView(etPhone)
        formCard.addView(etPass)
        formCard.addView(etChannel)
        formCard.addView(etToken)

        val btnStart = Button(this).apply {
            text = "🚀 Boshlash va Biznesni Yangilash"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glassGradientBox("#0B57D0", "#059669", "#80FFFFFF", 36f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130).apply { topMargin = 30 }
            setOnClickListener {
                val ph = etPhone.text.toString().trim()
                val pw = etPass.text.toString().trim()
                val st = etStore.text.toString().trim()
                val ch = etChannel.text.toString().trim()
                if (ph.length < 9 || pw.isEmpty() || st.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Do'kon nomi, telefon va parolni kiriting!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                userPhone = ph
                storeName = st
                storeAddress = etAddr.text.toString().trim()
                storeCategory = spCategory.selectedItem.toString()
                mainCurrency = spCurrency.selectedItem.toString()
                telegramChannel = if (ch.isNotEmpty()) ch else "@qarzfy_kanal"
                botToken = etToken.text.toString().trim()
                storeId = "QF-" + Math.abs(ph.hashCode() % 90000 + 10000)

                prefs.edit()
                    .putString("user_phone", userPhone)
                    .putString("store_id", storeId)
                    .putString("store_name", storeName)
                    .putString("store_address", storeAddress)
                    .putString("store_category", storeCategory)
                    .putString("main_currency", mainCurrency)
                    .putString("telegram_channel", telegramChannel)
                    .putString("bot_token", botToken)
                    .apply()

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

    private fun makeInput(hintText: String, initial: String = ""): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(initial)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            textSize = 14f
            background = glassBox("#0F172A", "#3338BDF8", 28f)
            setPadding(32, 26, 32, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        }
    }

    private fun labelTv(txt: String): TextView {
        return TextView(this).apply {
            text = txt
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(8, 6, 8, 6)
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
                    put("category", storeCategory)
                    put("telegram_channel", telegramChannel)
                }
                conn.outputStream.write(body.toString().toByteArray())
                conn.inputStream.close()
            } catch (_: Exception) {}
        }
    }

    // ==================== 2. ASOSIY WORKSPACE VA 5 TA PASTKI TAB ====================
    private fun renderMainWorkspace() {
        rootLayout.removeAllViews()

        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        when (currentBottomTab) {
            0 -> contentFrame.addView(buildHomeTab())
            1 -> contentFrame.addView(buildKassaTab())
            2 -> contentFrame.addView(buildMahsulotTab())
            3 -> contentFrame.addView(buildStatistikaTab())
            4 -> contentFrame.addView(buildProfilTab())
        }

        rootLayout.addView(contentFrame)
        rootLayout.addView(buildBottomNavBar())
    }

    private fun buildBottomNavBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glassGradientBox("#162032", "#0B111E", "#4D38BDF8", 0f)
            setPadding(12, 16, 12, 16)
            gravity = Gravity.CENTER
        }
        val tabs = listOf(
            "🏠 Asosiy",
            "💵 Kassa",
            "📦 Mahsulot",
            "📊 Statistika",
            "👤 Profil"
        )
        tabs.forEachIndexed { idx, title ->
            val active = (currentBottomTab == idx)
            val btn = TextView(this).apply {
                text = title
                gravity = Gravity.CENTER
                textSize = if (active) 12.5f else 11.5f
                setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (active) Color.WHITE else Color.parseColor("#94A3B8"))
                background = if (active) glassGradientBox("#0B57D0", "#059669", "#80FFFFFF", 28f) else null
                setPadding(10, 22, 10, 22)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(6, 0, 6, 0)
                }
                setOnClickListener {
                    currentBottomTab = idx
                    renderMainWorkspace()
                }
            }
            bar.addView(btn)
        }
        return bar
    }

    // ==================== TAB 0: ASOSIY (Mijozlar, Ta'minotchilar, Xodimlar, Excel, Kanal) ====================
    private fun buildHomeTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 40)
        }

        // Top Bar: Logo + Store Name + QR + Excel / Kanalga yuborish
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 22 }
        }
        val logoImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(95, 95).apply { rightMargin = 20 }
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = "$storeName ▾"
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { openBusinessInfoDialog() }
        })
        titleCol.addView(TextView(this).apply {
            text = "ID: $storeId • $telegramChannel"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 11f
        })

        val qrBtn = smallActionBtn("🔳 QR", "#334155") { showQrCodeAndOpenPageDialog() }
        val tgSendBtn = smallActionBtn("📤 Kanalga", "#0B57D0") { exportAllSheetsAndSendToChannel(manual = true) }

        topBar.addView(logoImg)
        topBar.addView(titleCol)
        topBar.addView(qrBtn)
        topBar.addView(tgSendBtn)
        col.addView(topBar)

        // 10 kun bepul Premium Glassy 3D Banner
        val premBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glassGradientBox("#4F46E5", "#0B57D0", "#8038BDF8", 38f)
            setPadding(34, 26, 34, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 22 }
            setOnClickListener { showSubscriptionTariffsDialog() }
        }
        premBanner.addView(TextView(this).apply {
            text = "🎁  10 kun bepul Premium\nIstalgan Premium tarifni bepul sinab ko'ring • 00:00 Avto-Eksport faol"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        premBanner.addView(TextView(this).apply {
            text = "❯"
            setTextColor(Color.WHITE)
            textSize = 18f
        })
        col.addView(premBanner)

        // Excel Export / Import 3D tugmalar qatori
        val excelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        excelRow.addView(actionPillBtn("📊 6-Betli Excel Eksport", "#059669", 1f) {
            val f = generateSixSheetExcelFile()
            Toast.makeText(this, "✅ Excel saqlandi:\n${f.name}", Toast.LENGTH_LONG).show()
            shareFileIntent(f, "📊 QarzFy To'liq 6-betli Excel Hisobot ($storeName)")
        })
        excelRow.addView(actionPillBtn("📥 Excel Import", "#2563EB", 1f) {
            openImportExcelDialog()
        })
        col.addView(excelRow)

        // Sub-tabs: Mijozlar | Ta'minotchilar | Xodimlar
        val subTabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glassBox("#111927", "#3338BDF8", 32f)
            setPadding(10, 10, 10, 10)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        }
        val subTitles = listOf("Mijozlar", "Ta'minotchilar", "Xodimlar")
        subTitles.forEachIndexed { idx, sTitle ->
            val sel = (currentHomeSubTab == idx)
            subTabRow.addView(TextView(this).apply {
                text = sTitle
                gravity = Gravity.CENTER
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#94A3B8"))
                setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
                background = if (sel) glassGradientBox("#0B57D0", "#1D4ED8", "#80FFFFFF", 24f) else null
                setPadding(16, 20, 16, 20)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    currentHomeSubTab = idx
                    renderMainWorkspace()
                }
            })
        }
        col.addView(subTabRow)

        if (currentHomeSubTab == 2) {
            // XODIMLAR BO'LIMI (PDF 77-78 betlar)
            val empHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
            }
            empHeader.addView(actionPillBtn("➕ Xodim qo'shish (Oylik / Kunlik)", "#0B57D0", 1f) {
                openAddEmployeeDialog()
            })
            col.addView(empHeader)

            val emps = db.getEmployees()
            if (emps.isEmpty()) {
                col.addView(emptyStateCard("👨‍💼 Xodimlar topilmadi\nSotuvchi va xodimlarni qo'shib, ularning amallarini nazorat qiling."))
            } else {
                emps.forEach { (nameRole, phone) ->
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = glassBox("#17223B", "#4D38BDF8", 32f)
                        setPadding(32, 26, 32, 26)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                    }
                    card.addView(TextView(this).apply {
                        text = "👨‍💼 $nameRole"
                        setTextColor(Color.WHITE)
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                    })
                    card.addView(TextView(this).apply {
                        text = "📞 $phone"
                        setTextColor(Color.parseColor("#38BDF8"))
                        textSize = 13f
                    })
                    col.addView(card)
                }
            }
            scroll.addView(col)
            return scroll
        }

        // Mijozlar yoki Ta'minotchilar filtrlari: Faol | Muddat | Summa | A-Z | +Papka
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        listOf("Faol", "Muddat", "Summa", "A-Z").forEach { mode ->
            val active = (currentSortMode == mode)
            filterRow.addView(TextView(this).apply {
                text = if (mode == "Faol") "⏱ Faol ↓" else if (mode == "Muddat") "⚠️ Muddat" else if (mode == "Summa") "💵 Summa" else "🔤 A-Z"
                gravity = Gravity.CENTER
                textSize = 11.5f
                setTextColor(if (active) Color.WHITE else Color.parseColor("#94A3B8"))
                background = glassBox(if (active) "#0B57D0" else "#162032", if (active) "#80FFFFFF" else "#3338BDF8", 22f)
                setPadding(18, 14, 18, 14)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
                setOnClickListener {
                    currentSortMode = mode
                    renderMainWorkspace()
                }
            })
        }
        filterRow.addView(TextView(this).apply {
            text = "📁+"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            background = glassBox("#1E293B", "#4D10B981", 22f)
            setPadding(20, 14, 20, 14)
            setOnClickListener { openCreateFolderDialog() }
        })
        col.addView(filterRow)

        // Qidiruv va Yangi Qarz / Ta'minotchi qo'shish
        val etSearch = makeInput("🔍 Ism yoki telefon raqam bo'yicha qidiruv...", searchQuery)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        col.addView(etSearch)

        val addActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        val isSup = (currentHomeSubTab == 1)
        addActionRow.addView(actionPillBtn(if (isSup) "➕ Ta'minotchi / Kirim" else "➕ Mijozga Qarz Yozish", "#0B57D0", 1f) {
            openAddPersonTransactionDialog(isSup)
        })
        addActionRow.addView(actionPillBtn("📩 Yoppasiga SMS", "#D97706", 1f) {
            sendBulkSmsReminders(isSup)
        })
        col.addView(addActionRow)

        val persons = db.getPersons(isSup, searchQuery, currentSortMode)
        var totalUzs = 0L
        var totalUsd = 0.0
        persons.forEach {
            totalUzs += it.balUzs
            totalUsd += it.balUsd
        }

        // Jami balans kartochkasi
        val summaryCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glassGradientBox("#1E293B", "#0F172A", "#4D38BDF8", 34f)
            setPadding(32, 26, 32, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        summaryCard.addView(TextView(this).apply {
            text = "Jami (${persons.size} ta):\n${formatMoney(totalUzs, "UZS")}"
            setTextColor(Color.parseColor("#F87171"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        summaryCard.addView(TextView(this).apply {
            text = "Valyuta (USD):\n$totalUsd $"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.END
        })
        col.addView(summaryCard)

        if (persons.isEmpty()) {
            col.addView(emptyStateCard(if (isSup) "🚚 Ta'minotchi topilmadi\nYangi ta'minotchi va yuk kirimini qo'shing." else "👥 Qarzdor mijozlar yo'q\nQarz yuritishni boshlash uchun birinchi mijozingizni qo'shing!"))
        } else {
            persons.forEach { p ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = glassBox("#162238", "#4D38BDF8", 36f)
                    setPadding(34, 28, 34, 28)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
                }
                val top = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                top.addView(TextView(this).apply {
                    text = "👤 ${p.name}"
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                top.addView(TextView(this).apply {
                    text = formatMoney(p.balUzs, "UZS")
                    setTextColor(if (p.balUzs < 0) Color.parseColor("#F87171") else Color.parseColor("#10B981"))
                    textSize = 15.5f
                    setTypeface(null, Typeface.BOLD)
                })
                card.addView(top)

                card.addView(TextView(this).apply {
                    text = "📞 ${p.phone}  •  ⏳ Muddat: ${if (p.dueDate.isBlank()) "Belgilanmagan" else p.dueDate}\n📅 Yaratilgan: ${p.createdAt}"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 12f
                    setPadding(0, 10, 0, 18)
                })

                val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                btns.addView(actionPillBtn("📩 SMS Eslatma", "#0284C7", 1f) {
                    val msg = "Hurmatli ${p.name}, $storeName do'konidan ${formatMoney(Math.abs(p.balUzs), "UZS")} qarzingiz bor. To'lov muddati: ${p.dueDate}."
                    sendAutoSms(p.phone, msg)
                })
                btns.addView(actionPillBtn("✅ To'lov / Yopish", "#059669", 1f) {
                    db.deletePerson(p.id)
                    val msg = "Hurmatli ${p.name}, $storeName do'konidagi qarzingiz to'liq yopildi. Rahmat!"
                    sendAutoSms(p.phone, msg)
                    renderMainWorkspace()
                })
                card.addView(btns)
                col.addView(card)
            }
        }

        scroll.addView(col)
        return scroll
    }

    // ==================== TAB 1: KASSA (PDF 13-bet, Kirim/Chiqim, Naqd/Plastik) ====================
    private fun buildKassaTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 40)
        }

        val allKassa = db.getKassaList()
        var kirimUzs = 0L
        var chiqimUzs = 0L
        var naqdBal = 0L
        var plastikBal = 0L

        allKassa.forEach { k ->
            if (k.type == "Kirim") {
                kirimUzs += k.amount
                if (k.payType == "Naqd") naqdBal += k.amount else plastikBal += k.amount
            } else {
                chiqimUzs += k.amount
                if (k.payType == "Naqd") naqdBal -= k.amount else plastikBal -= k.amount
            }
        }
        val netBal = kirimUzs - chiqimUzs

        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassGradientBox("#3B0764", "#1E3A8A", "#8038BDF8", 42f)
            setPadding(38, 36, 38, 36)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 22 }
        }
        headerCard.addView(TextView(this).apply {
            text = "💵 Kassa Balansi ($mainCurrency)"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 13f
        })
        headerCard.addView(TextView(this).apply {
            text = formatMoney(netBal, "UZS")
            setTextColor(Color.parseColor("#10B981"))
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 14)
        })
        headerCard.addView(TextView(this).apply {
            text = "🟢 Jami Kirim: ${formatMoney(kirimUzs)}   |   🔴 Jami Chiqim: ${formatMoney(chiqimUzs)}\n💵 Naqd: ${formatMoney(naqdBal)}   |   💳 Plastik: ${formatMoney(plastikBal)}"
            setTextColor(Color.WHITE)
            textSize = 12f
        })
        col.addView(headerCard)

        // Davr va To'lov turi filtrlari (PDF 13-bet)
        val periodRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        }
        listOf("Hammasi", "Naqd", "Plastik", "14 kun", "Shu oy", "1 yil").forEach { chip ->
            periodRow.addView(TextView(this).apply {
                text = chip
                textSize = 11f
                setTextColor(Color.WHITE)
                background = glassBox("#17223B", "#3338BDF8", 24f)
                setPadding(20, 14, 20, 14)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 10 }
            })
        }
        col.addView(periodRow)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 22 }
        }
        btnRow.addView(actionPillBtn("➕ Kassaga Kirim", "#059669", 1f) { openAddKassaDialog("Kirim") })
        btnRow.addView(actionPillBtn("➖ Xarajat / Chiqim", "#DC2626", 1f) { openAddKassaDialog("Chiqim") })
        col.addView(btnRow)

        if (allKassa.isEmpty()) {
            col.addView(emptyStateCard("💵 Kassa yozuvlari mavjud emas\nKirim va xarajatlarni naqd yoki plastik kesimida kiriting."))
        } else {
            allKassa.forEach { k ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = glassBox("#162032", "#3338BDF8", 30f)
                    setPadding(30, 22, 30, 22)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                }
                item.addView(TextView(this).apply {
                    text = "${if (k.type == "Kirim") "🟢 KIRIM" else "🔴 CHIQIM"} • ${k.category} (${k.payType})"
                    setTextColor(Color.WHITE)
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                })
                item.addView(TextView(this).apply {
                    text = "${formatMoney(k.amount, k.currency)}  •  ${k.date}\nIzoh: ${k.note}"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 12f
                })
                col.addView(item)
            }
        }

        scroll.addView(col)
        return scroll
    }

    // ==================== TAB 2: MAHSULOT VA OMBOR (PDF 14-20 betlar) ====================
    private fun buildMahsulotTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 40)
        }

        val products = db.getProducts()
        var totalCostVal = 0L
        var totalSaleVal = 0L
        products.forEach {
            totalCostVal += (it.costPrice * it.qty)
            totalSaleVal += (it.salePrice * it.qty)
        }
        val expectedProfit = totalSaleVal - totalCostVal

        val omborCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassGradientBox("#4C1D95", "#0B57D0", "#8038BDF8", 42f)
            setPadding(36, 32, 36, 32)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        omborCard.addView(TextView(this).apply {
            text = "📦 Ombor Qiymati (${products.size} xil mahsulot)"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13f
        })
        omborCard.addView(TextView(this).apply {
            text = formatMoney(totalCostVal, "UZS")
            setTextColor(Color.WHITE)
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 6, 0, 10)
        })
        omborCard.addView(TextView(this).apply {
            text = "🏷 Sotuv narxida: ${formatMoney(totalSaleVal)}   |   📈 Kutilayotgan foyda: ${formatMoney(expectedProfit)}"
            setTextColor(Color.parseColor("#34D399"))
            textSize = 12f
        })
        col.addView(omborCard)

        // PDF 16-betdagi 5 ta ombor amali
        val actionsGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r1.addView(actionPillBtn("➕ Mahsulot yaratish", "#0B57D0", 1f) { openAddProductDialog() })
        r1.addView(actionPillBtn("📥 Mahsulot kirim qilish", "#059669", 1f) { openAddProductDialog() })
        actionsGrid.addView(r1)

        val r2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 }
        }
        r2.addView(actionPillBtn("📸 Tokchadan AI Skan", "#7C3AED", 1f) {
            Toast.makeText(this, "📸 AI Katalog Skaneri: Rasm tanlang (Bepul skanlar: 1/1)", Toast.LENGTH_LONG).show()
        })
        r2.addView(actionPillBtn("🧮 Sotuv Kalkulyatori", "#D97706", 1f) {
            openSalesCalculatorDialog()
        })
        actionsGrid.addView(r2)
        col.addView(actionsGrid)

        if (products.isEmpty()) {
            col.addView(emptyStateCard("📦 Omborda mahsulot yo'q\nBirinchi yetkazib berishni qabul qilish uchun '➕ Mahsulot yaratish' tugmasini bosing."))
        } else {
            products.forEach { pr ->
                val pCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = glassBox("#162238", "#3338BDF8", 32f)
                    setPadding(30, 24, 30, 24)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                }
                pCard.addView(TextView(this).apply {
                    text = "📦 ${pr.name}  •  Qoldiq: ${pr.qty} ${pr.unit}"
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                })
                pCard.addView(TextView(this).apply {
                    text = "Tan narx: ${formatMoney(pr.costPrice, pr.currency)}  |  Sotuv: ${formatMoney(pr.salePrice, pr.currency)}\nShtrix-kod: ${pr.barcode}  |  Ta'minotchi: ${pr.supplier}"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 12f
                })
                col.addView(pCard)
            }
        }

        scroll.addView(col)
        return scroll
    }

    // ==================== TAB 3: STATISTIKA VA AI YORDAMCHI (PDF 21-29 betlar) ====================
    private fun buildStatistikaTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 40)
        }

        val clients = db.getPersons(false)
        val sups = db.getPersons(true)
        val txs = db.getTransactions(false)

        var jamiQarz = 0L
        clients.forEach { if (it.balUzs < 0) jamiQarz += Math.abs(it.balUzs) }

        val statCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassGradientBox("#0F172A", "#1E3A8A", "#6638BDF8", 40f)
            setPadding(36, 32, 36, 32)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        statCard.addView(TextView(this).apply {
            text = "📊 Davr bo'yicha Xulosa (Barcha vaqt / Bugun / Shu oy)"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        })
        statCard.addView(TextView(this).apply {
            text = "\n👥 Qarzdor mijozlar: ${clients.size} ta\n🔴 Jami mijoz qarzlari: ${formatMoney(jamiQarz)}\n🚚 Ta'minotchilar: ${sups.size} ta\n📑 Jami tranzaksiyalar: ${txs.size} ta\n\nNaqd sotuvlar: 0 UZS  |  Plastik karta: 0 UZS\nTerminal: 0 UZS  |  Nasiya / Qarz: ${formatMoney(jamiQarz)}"
            setTextColor(Color.WHITE)
            textSize = 13.5f
        })
        col.addView(statCard)

        // AI Yordamchi (PDF 23-bet)
        val aiCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassBox("#17223B", "#4D10B981", 36f)
            setPadding(34, 28, 34, 28)
        }
        aiCard.addView(TextView(this).apply {
            text = "🤖 QarzFy AI Yordamchi"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        })
        aiCard.addView(TextView(this).apply {
            text = "QarzFy bo'yicha savollaringizga javob beraman va hisobotlarni tahlil qilaman."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, 6, 0, 14)
        })
        val etAi = makeInput("Savolingizni yozing...", "")
        aiCard.addView(etAi)
        aiCard.addView(actionPillBtn("✨ AI dan javob olish", "#059669", 1f) {
            AlertDialog.Builder(this)
                .setTitle("🤖 QarzFy AI Tahlili")
                .setMessage("Do'koningizda hozirda ${clients.size} ta mijoz bo'lib, jami qarz miqdori ${formatMoney(jamiQarz)} ni tashkil etadi.\n\nHar kuni soat 00:00 da Excel hisobot $telegramChannel kanaliga avtomatik yuborilmoqda.")
                .setPositiveButton("Tushunarli", null)
                .show()
        })
        col.addView(aiCard)

        scroll.addView(col)
        return scroll
    }

    // ==================== TAB 4: PROFIL VA BARCHA SOZLAMALAR (PDF 30-76 betlar) ====================
    private fun buildProfilTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 40)
        }

        // Profil Header
        val profCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassGradientBox("#1E293B", "#0B57D0", "#8038BDF8", 42f)
            setPadding(36, 32, 36, 32)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        profCard.addView(TextView(this).apply {
            text = "👑 $storeName (Biznes egasi)"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
        })
        profCard.addView(TextView(this).apply {
            text = "📞 $userPhone  •  ID: $storeId\n📍 $storeAddress  •  🏷 $storeCategory\n📢 Kanal: $telegramChannel (00:00 Avto-Excel faol)"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 12.5f
            setPadding(0, 10, 0, 0)
        })
        col.addView(profCard)

        val settingsItems = listOf(
            "💎 Limitlar va Tariflar (Bepul / Kichik / O'rta / Katta)" to { showSubscriptionTariffsDialog() },
            "📢 Telegram Kanal va 00:00 Avto-Excel Sozlamasi" to { openChannelSettingsDialog() },
            "📩 SMS Sozlamalari va Shablonlar (MobSMS / 4546)" to { showSmsSettingsDialog() },
            "🔔 Push va Tonggi Salomlashish (08:00 / 20:00)" to { showPushSettingsDialog() },
            "🗣 Ovozli Qo'ng'iroq (O'zbekcha Nigora / Ruscha Alena)" to { showVoiceSettingsDialog() },
            "🖨 Bluetooth Printer Sozlamalari (58mm / 80mm / A4)" to { showPrinterSettingsDialog() },
            "🌐 Ochiq Sahifa va QR-Kod Katalog" to { showQrCodeAndOpenPageDialog() },
            "🤝 Hamjamiyat, Vazifalar (+5000 UZS) va Ambassador" to { showCommunityAndAmbassadorDialog() },
            "🎁 Referal Tizim va Promo-kod" to { showReferralDialog() },
            "🎨 Mavzular (Glassy 3D), Shrift va Ilova Tili (9 ta til)" to { showThemeAndLangDialog() },
            "👨‍💻 Dasturchi haqida: Hasan Jaylovov (@HasanJaylovov)" to { showDeveloperDialog() }
        )

        settingsItems.forEach { (title, action) ->
            val row = TextView(this).apply {
                text = "$title   ❯"
                setTextColor(Color.WHITE)
                textSize = 13.5f
                background = glassBox("#152033", "#3338BDF8", 30f)
                setPadding(34, 28, 34, 28)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                setOnClickListener { action.invoke() }
            }
            col.addView(row)
        }

        scroll.addView(col)
        return scroll
    }

    // ==================== 3. EXCEL 6-BETLI EKSPORT, IMPORT VA TELEGRAM KANALGA YUBORISH ====================
    private fun generateSixSheetExcelFile(): File {
        val clients = db.getPersons(false)
        val clientTxs = db.getTransactions(false)
        val suppliers = db.getPersons(true)
        val supTxs = db.getTransactions(true)
        val kassaList = db.getKassaList()

        val sb = StringBuilder()
        sb.append("\uFEFF") // UTF-8 BOM for Excel

        // 1-VARAQ: Mijozlar
        sb.append("=== VARAQ 1: Mijozlar ===\n")
        sb.append("ID,Ism,Telefon raqami,Balans USD,Balans UZS,Yaratilgan sana\n")
        clients.forEach { c ->
            sb.append("${c.id},\"${c.name}\",\"${c.phone}\",${c.balUsd},${c.balUzs},\"${c.createdAt}\"\n")
        }

        // 2-VARAQ: Tranzaksiyalar
        sb.append("\n=== VARAQ 2: Tranzaksiyalar ===\n")
        sb.append("Tranzaksiya ID,Turi,Mijoz ismi,Mijoz telefoni,Summa,Valyuta,Sana,Tavsif,Holat\n")
        clientTxs.forEach { t ->
            sb.append("${t.txId},${t.kind},\"${t.personName}\",\"${t.personPhone}\",${t.amount},${t.currency},\"${t.date}\",\"${t.note}\",${t.status}\n")
        }

        // 3-VARAQ: Ta'minotchilar
        sb.append("\n=== VARAQ 3: Ta'minotchilar ===\n")
        sb.append("ID,Ism,Telefon raqami,Balans USD,Balans UZS,Manzil,Yaratilgan sana\n")
        suppliers.forEach { s ->
            sb.append("${s.id},\"${s.name}\",\"${s.phone}\",${s.balUsd},${s.balUzs},\"$storeAddress\",\"${s.createdAt}\"\n")
        }

        // 4-VARAQ: Ta'minotchi tranzaksiyalari
        sb.append("\n=== VARAQ 4: Ta'minotchi tranzaksiyalari ===\n")
        sb.append("Tranzaksiya ID,Turi,Ta'minotchi ismi,Ta'minotchi telefoni,Summa,Valyuta,Sana,Tavsif,Holat\n")
        supTxs.forEach { t ->
            sb.append("${t.txId},${t.kind},\"${t.personName}\",\"${t.personPhone}\",${t.amount},${t.currency},\"${t.date}\",\"${t.note}\",${t.status}\n")
        }

        // 5-VARAQ: Kassa xulosasi
        var kIn = 0L
        var kOut = 0L
        var naqdIn = 0L
        var plastIn = 0L
        var naqdOut = 0L
        var plastOut = 0L
        kassaList.forEach { k ->
            if (k.type == "Kirim") {
                kIn += k.amount
                if (k.payType == "Naqd") naqdIn += k.amount else plastIn += k.amount
            } else {
                kOut += k.amount
                if (k.payType == "Naqd") naqdOut += k.amount else plastOut += k.amount
            }
        }
        sb.append("\n=== VARAQ 5: Kassa xulosasi ===\n")
        sb.append("Valyuta,Jami kirim,Jami chiqim,Balans,Naqd kirim,Plastik kirim,Naqd chiqim,Plastik chiqim\n")
        sb.append("UZS,$kIn,$kOut,${kIn - kOut},$naqdIn,$plastIn,$naqdOut,$plastOut\n")

        // 6-VARAQ: Kassa yozuvlari
        sb.append("\n=== VARAQ 6: Kassa yozuvlari ===\n")
        sb.append("ID,Turi,Kategoriya,Summa,Valyuta,To'lov turi,Sana,Tavsif,Kim yaratdi\n")
        kassaList.forEach { k ->
            sb.append("${k.id},${k.type},\"${k.category}\",${k.amount},${k.currency},${k.payType},\"${k.date}\",\"${k.note}\",\"${k.creator}\"\n")
        }

        val fileName = "clients_export_${System.currentTimeMillis()}.csv"
        val file = File(getExternalFilesDir(null), fileName)
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    private fun exportAllSheetsAndSendToChannel(manual: Boolean) {
        val file = generateSixSheetExcelFile()
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val caption = "📊 QarzFy Kunlik Avtomatik Excel Hisobot\n🏪 Do'kon: $storeName ($storeId)\n📢 Kanal: $telegramChannel\n⏰ Vaqt: $dateStr"

        if (botToken.isNotBlank() && telegramChannel.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                val ok = sendDocumentViaTelegramBotApi(botToken, telegramChannel, file, caption)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        Toast.makeText(this@MainActivity, "✅ Hisobot $telegramChannel kanaliga avtomatik joylandi!", Toast.LENGTH_LONG).show()
                    } else if (manual) {
                        shareFileIntent(file, caption)
                    }
                }
            }
        } else if (manual) {
            shareFileIntent(file, caption)
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
        } catch (e: Exception) {
            false
        }
    }

    private fun shareFileIntent(file: File, text: String) {
        try {
            val uri = Uri.fromFile(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, "Telegram kanalga yuborish"))
        } catch (e: Exception) {
            Toast.makeText(this, "Fayl saqlandi: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
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

    private fun openImportExcelDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 20)
        }
        val info = TextView(this).apply {
            text = "Excel (.csv) fayldan mijozlar va qarzlarni tezkor import qilish.\nFormat: ID, Ism, Telefon raqami, Balans USD, Balans UZS, Yaratilgan sana"
            textSize = 12.5f
        }
        val etPaste = EditText(this).apply {
            hint = "CSV qatorlarini bu yerga nusxalab tashlang yoki pastdan Fayl Tanlang..."
            minLines = 4
        }
        layout.addView(info)
        layout.addView(etPaste)

        AlertDialog.Builder(this)
            .setTitle("📥 Excel / CSV Import Qilish")
            .setView(layout)
            .setPositiveButton("Matndan Import") { _, _ ->
                val raw = etPaste.text.toString().trim()
                if (raw.isNotEmpty()) {
                    val count = parseAndImportCsvLines(raw.lines())
                    Toast.makeText(this, "✅ $count ta yozuv muvaffaqiyatli import qilindi!", Toast.LENGTH_LONG).show()
                    renderMainWorkspace()
                }
            }
            .setNeutralButton("📂 Fayl Tanlash") { _, _ ->
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, PICK_IMPORT_FILE)
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMPORT_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val reader = BufferedReader(InputStreamReader(contentResolver.openInputStream(uri)))
                    val lines = reader.readLines()
                    reader.close()
                    val count = parseAndImportCsvLines(lines)
                    Toast.makeText(this, "✅ Fayldan $count ta mijoz import qilindi!", Toast.LENGTH_LONG).show()
                    renderMainWorkspace()
                } catch (e: Exception) {
                    Toast.makeText(this, "Import xatosi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun parseAndImportCsvLines(lines: List<String>): Int {
        var imported = 0
        lines.forEach { line ->
            val clean = line.trim()
            if (clean.isNotEmpty() && !clean.startsWith("ID") && !clean.startsWith("===") && !clean.startsWith("Tranzaksiya")) {
                val parts = clean.split(",")
                if (parts.size >= 5) {
                    val name = parts[1].replace("\"", "").trim()
                    val phone = parts[2].replace("\"", "").trim()
                    val balUzsStr = parts[4].replace("\"", "").replace(" ", "").trim()
                    val balUzs = balUzsStr.toLongOrNull() ?: 0L
                    if (name.isNotEmpty() && balUzs != 0L) {
                        val kind = if (balUzs < 0) "Chiqim" else "Kirim"
                        db.addOrUpdatePersonTx(name, phone, Math.abs(balUzs), "UZS", kind, "Excel Import", "10.11.2026", false)
                        imported++
                    }
                }
            }
        }
        return imported
    }

    // ==================== 4. DIALOGLAR VA QO'SHIMCHA FUNKSIYALAR ====================
    private fun openAddPersonTransactionDialog(isSupplier: Boolean) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 30, 45, 10)
        }
        val etName = EditText(this).apply { hint = if (isSupplier) "Ta'minotchi ismi" else "Mijoz ismi (masalan: Gulbaxor opam)" }
        val etPhone = EditText(this).apply {
            hint = "Telefon (+998901234567)"
            inputType = InputType.TYPE_CLASS_PHONE
            setText("+998")
        }
        val etAmount = EditText(this).apply {
            hint = "Summa"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val spCur = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("UZS", "USD"))
        }
        val spKind = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Chiqim (Qarz berish)", "Kirim (To'lov olish)"))
        }
        val etNote = EditText(this).apply { hint = "Tavsif / Mahsulot izohi" }
        val etDue = EditText(this).apply {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 7) }
            setText(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(cal.time))
        }

        layout.addView(etName)
        layout.addView(etPhone)
        layout.addView(etAmount)
        layout.addView(spCur)
        layout.addView(spKind)
        layout.addView(etNote)
        layout.addView(etDue)

        AlertDialog.Builder(this)
            .setTitle(if (isSupplier) "🚚 Ta'minotchi Tranzaksiyasi" else "➕ Mijozga Qarz / To'lov Yozish")
            .setView(layout)
            .setPositiveButton("Saqlash va Avto-SMS") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val amt = etAmount.text.toString().trim().toLongOrNull() ?: 0L
                val cur = spCur.selectedItem.toString()
                val kind = if (spKind.selectedItemPosition == 0) "Chiqim" else "Kirim"
                val note = etNote.text.toString().trim()
                val due = etDue.text.toString().trim()

                if (name.isEmpty() || amt <= 0) {
                    Toast.makeText(this, "Ism va summani to'g'ri kiriting!", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                db.addOrUpdatePersonTx(name, phone, amt, cur, kind, note, due, isSupplier)
                renderMainWorkspace()

                val sms = "Hurmatli $name! $storeName do'konida sizga ${formatMoney(amt, cur)} ($kind) yozildi. Izoh: $note. Muddat: $due."
                sendAutoSms(phone, sms)
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun openAddKassaDialog(type: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 30, 45, 10)
        }
        val etCat = EditText(this).apply { hint = "Kategoriya (masalan: Savdo, Ijara, Oylik)" }
        val etAmt = EditText(this).apply {
            hint = "Summa (UZS)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val spPay = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Naqd", "Plastik"))
        }
        val etNote = EditText(this).apply { hint = "Tavsif / Izoh" }
        layout.addView(etCat)
        layout.addView(etAmt)
        layout.addView(spPay)
        layout.addView(etNote)

        AlertDialog.Builder(this)
            .setTitle("💵 Kassaga $type Qo'shish")
            .setView(layout)
            .setPositiveButton("Saqlash") { _, _ ->
                val cat = etCat.text.toString().trim().ifEmpty { "Umumiy" }
                val amt = etAmt.text.toString().trim().toLongOrNull() ?: 0L
                val pay = spPay.selectedItem.toString()
                val note = etNote.text.toString().trim()
                if (amt > 0) {
                    db.addKassa(type, cat, amt, "UZS", pay, note, storeName)
                    renderMainWorkspace()
                }
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun openAddProductDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 25, 45, 10)
        }
        val etName = EditText(this).apply { hint = "Mahsulot nomi *" }
        val etBarcode = EditText(this).apply { hint = "Shtrix-kod / Kod" }
        val etQty = EditText(this).apply {
            hint = "Miqdor (dona/kg)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        val etCost = EditText(this).apply {
            hint = "Tan narxi (UZS)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etSale = EditText(this).apply {
            hint = "Sotuv narxi (UZS)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etSup = EditText(this).apply { hint = "Ta'minotchi nomi" }
        layout.addView(etName)
        layout.addView(etBarcode)
        layout.addView(etQty)
        layout.addView(etCost)
        layout.addView(etSale)
        layout.addView(etSup)

        AlertDialog.Builder(this)
            .setTitle("📦 Mahsulot Yaratish / Kirim")
            .setView(layout)
            .setPositiveButton("Saqlash") { _, _ ->
                val name = etName.text.toString().trim()
                val code = etBarcode.text.toString().trim().ifEmpty { "BC-" + (1000 + Random().nextInt(9000)) }
                val qty = etQty.text.toString().trim().toIntOrNull() ?: 1
                val cost = etCost.text.toString().trim().toLongOrNull() ?: 0L
                val sale = etSale.text.toString().trim().toLongOrNull() ?: 0L
                val sup = etSup.text.toString().trim().ifEmpty { "Asosiy ta'minotchi" }
                if (name.isNotEmpty()) {
                    db.addProduct(name, code, qty, "dona", cost, sale, "UZS", sup)
                    renderMainWorkspace()
                }
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun openAddEmployeeDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 25, 45, 10)
        }
        val etName = EditText(this).apply { hint = "Xodim ismi" }
        val etPhone = EditText(this).apply {
            hint = "UZ Telefon (+998)"
            setText("+998")
        }
        val spType = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Oylik", "Kunlik"))
        }
        layout.addView(etName)
        layout.addView(etPhone)
        layout.addView(spType)

        AlertDialog.Builder(this)
            .setTitle("👨‍💼 Xodim Qo'shish")
            .setView(layout)
            .setPositiveButton("Saqlash") { _, _ ->
                val n = etName.text.toString().trim()
                val p = etPhone.text.toString().trim()
                if (n.isNotEmpty()) {
                    db.addEmployee(n, p, spType.selectedItem.toString())
                    renderMainWorkspace()
                }
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun openCreateFolderDialog() {
        val et = EditText(this).apply { hint = "Papka nomi (masalan: Ulgurji mijozlar)" }
        AlertDialog.Builder(this)
            .setTitle("📁 Yangi Papka Yaratish")
            .setMessage("Belgini va rangni tanlab, mijozlarni guruhlang.")
            .setView(et)
            .setPositiveButton("Saqlash") { _, _ ->
                Toast.makeText(this, "📁 '${et.text}' papkasi yaratildi!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun openSalesCalculatorDialog() {
        val et = EditText(this).apply {
            hint = "Miqdor * Narx (masalan: 25000)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle("🧮 Tezkor Sotuv Kalkulyatori")
            .setView(et)
            .setPositiveButton("Kassaga Kirim Qilish") { _, _ ->
                val v = et.text.toString().toLongOrNull() ?: 0L
                if (v > 0) {
                    db.addKassa("Kirim", "Tezkor Sotuv", v, "UZS", "Naqd", "Kalkulyator orqali", storeName)
                    Toast.makeText(this, "✅ Sotuv kassaga qo'shildi!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun showSubscriptionTariffsDialog() {
        val msg = """
            Joriy tarif: Bepul (10 kun Premium sinov faol)
            
            🔹 Kichik: $1.99 / oylik (23 569 UZS) | $5.67 choraklik | $19.99 yillik
            • Bizneslar soni: 2 ta • Mijoz tarixi: ∞
            
            🔷 O'rta: $9.99 / oylik | $28.47 choraklik | $99.90 yillik
            • Ombor, Shtrix-kod va Xodimlar nazorati
            
            👑 Katta: $49.99 / oylik | Distribyutor: $99.00 / oylik
            • Cheksiz filiallar va VIP Avto-SMS + Telegram Eksport
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("💎 Obuna Sotib Olish va Tariflar")
            .setMessage(msg)
            .setPositiveButton("Telegram Botda Faollashtirish") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/qarzdaftar_pay_bot?start=$storeId")))
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun openChannelSettingsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 25, 45, 10)
        }
        val etCh = EditText(this).apply {
            hint = "Telegram kanal (@kanal_nomi yoki -100...)"
            setText(telegramChannel)
        }
        val etTk = EditText(this).apply {
            hint = "Bot Token (00:00 da fonda avto yuborish uchun)"
            setText(botToken)
        }
        layout.addView(etCh)
        layout.addView(etTk)

        AlertDialog.Builder(this)
            .setTitle("📢 Telegram Kanal va 00:00 Avto-Eksport")
            .setMessage("Har kuni soat 00:00 da 6-varaqdan iborat Excel hisobot ushbu kanalga avtomatik yuboriladi.")
            .setView(layout)
            .setPositiveButton("Saqlash") { _, _ ->
                telegramChannel = etCh.text.toString().trim()
                botToken = etTk.text.toString().trim()
                prefs.edit().putString("telegram_channel", telegramChannel).putString("bot_token", botToken).apply()
                Toast.makeText(this, "✅ Kanal sozlamalari saqlandi!", Toast.LENGTH_SHORT).show()
                renderMainWorkspace()
            }
            .setNeutralButton("📤 Hozir Yuborish") { _, _ ->
                exportAllSheetsAndSendToChannel(manual = true)
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun showSmsSettingsDialog() {
        val msg = """
            📩 Qarz eslatmalari:
            • Qarz muddati yaqinlashganda: 2 kun oldin (09:00 da)
            • Qarz muddati o'tgandan so'ng: 3 kungacha
            • SMSda barcha valyuta balanslari va qarz izohi qo'shiladi
            
            📡 SMS Shlyuz:
            ☑️ Bepul MobSMS (o'z SIM-kartangizdan avtomatik)
            ⬜ 4546 / QARZFY.UZ (standart shlyuz)
            
            🚚 Ta'minotchilarga kirim-chiqimda SMS yuborish: Yoqilgan
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("📩 SMS Sozlamalari va Shablonlar")
            .setMessage(msg)
            .setPositiveButton("Eslatmalarni saqlash", null)
            .show()
    }

    private fun showPushSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("🔔 Push va Bildirishnoma Sozlamalari")
            .setMessage("• Qarzlar haqida kechki eslatma: 20:00\n• Tonggi salomlashish vaqti: 08:00\n• Manfiy qoldiq kirim eslatmasi: 21:00\n• Sotuvchilar amallari haqida darhol xabar: Yoqilgan")
            .setPositiveButton("Saqlash", null)
            .show()
    }

    private fun showVoiceSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("🗣 Ovozli Qo'ng'iroq Sozlamalari")
            .setMessage("🇺🇿 O'zbekcha: Nigora (Standart tezlik va ohang)\n🇷🇺 Ruscha: Alena (Standart)\n🇬🇧 Inglizcha: Jon (Standart)\n\nMijozlarga qarz muddati kelganda avtomatik ovozli eslatma.")
            .setPositiveButton("Saqlash", null)
            .show()
    }

    private fun showPrinterSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("🖨 Bluetooth Printer Sozlamalari")
            .setMessage("📄 Qog'oz eni: 80 mm / 58 mm / A4\n🔌 Ulangan qurilmalar: Bluetooth orqali qidirish\n\nSotuv va qarz cheklarini bir bosishda chop etish.")
            .setPositiveButton("🧪 Test chek chiqarish") { _, _ ->
                Toast.makeText(this, "🖨 Test chek Bluetooth printerga yuborildi!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun showQrCodeAndOpenPageDialog() {
        AlertDialog.Builder(this)
            .setTitle("🔳 Mening QR-kodim va Ochiq Sahifa")
            .setMessage("Do'kon: $storeName\nOchiq katalog havolasi:\nhttps://qarzfy.uz/sh/$storeId\n\n☑️ Katalogda narxni ko'rsatish\n☑️ Biznes nomi, rasmi, manzili va telefon raqami ko'rinsin")
            .setPositiveButton("Havolani Ulashish") { _, _ ->
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "🏪 $storeName onlayn katalogi va QR sahifasi: https://qarzfy.uz/sh/$storeId")
                }
                startActivity(Intent.createChooser(i, "Ulashish"))
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun showCommunityAndAmbassadorDialog() {
        AlertDialog.Builder(this)
            .setTitle("🤝 Hamjamiyat, Vazifalar va Ambassador")
            .setMessage("💬 Tadbirkorlar suhbati va Yaqin atrofdagi do'konlar xaritasi\n\n🎯 Vazifalar:\n• Postni Telegram Storyga joylang: +5 000 UZS\n• Instagramda Story joylang: +5 000 UZS\n\n🎥 Ambassador dasturi:\n• Yuzingiz bilan video: 20 000 UZS (2x mukofot)\n• Yuzsiz (do'kon videosi): 10 000 UZS")
            .setPositiveButton("Vazifani Bajarish", null)
            .show()
    }

    private fun showReferralDialog() {
        AlertDialog.Builder(this)
            .setTitle("🎁 Referal Tizim (Hisob: 0 UZS)")
            .setMessage("Sizning referal kodingiz: $storeId\n\nSiz taklif qilgan foydalanuvchilar pullik obuna sotib olganda, referal hisobingizga bonus yoziladi.")
            .setPositiveButton("Do'stlarni Chaqirish", null)
            .show()
    }

    private fun showThemeAndLangDialog() {
        AlertDialog.Builder(this)
            .setTitle("🎨 Mavzu, Shrift va Ilova Tili")
            .setMessage("🌐 Tillar: O'zbek tili, Русский, English, Qaraqalpaq, Қазақ, Кыргыз, Тоҷикӣ, Türkçe, العربية\n\n✨ Dizayn: Glassy 3D Dark (Faol)\n🔤 Shrift o'lchami: Standart / Katta")
            .setPositiveButton("Saqlash", null)
            .show()
    }

    private fun openBusinessInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("🏪 Biznes Ma'lumotlari")
            .setMessage("Nomi: $storeName\nID: $storeId\nManzil: $storeAddress\nKategoriya: $storeCategory\nAsosiy valyuta: $mainCurrency\nTelefon: $userPhone")
            .setPositiveButton("Yopish", null)
            .show()
    }

    private fun showDeveloperDialog() {
        AlertDialog.Builder(this)
            .setTitle("ℹ️ QarzFy Haqida")
            .setMessage("Ilova nomi: QarzFy v4.0 (Glassy 3D Edition)\nDo'kon ID: $storeId\n\n👨‍💻 Dasturchi: Hasan Jaylovov\n📢 Aloqa uchun Telegram: @HasanJaylovov\n\nBarcha huquqlar himoyalangan.")
            .setPositiveButton("Bog'lanish (@HasanJaylovov)") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/HasanJaylovov")))
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun sendBulkSmsReminders(isSupplier: Boolean) {
        val list = db.getPersons(isSupplier)
        if (list.isEmpty()) {
            Toast.makeText(this, "Ro'yxat bo'sh!", Toast.LENGTH_SHORT).show()
            return
        }
        list.forEach { p ->
            if (p.balUzs < 0) {
                val msg = "Hurmatli ${p.name}, $storeName do'konidan ${formatMoney(Math.abs(p.balUzs))} qarzingizni to'lash muddati (${p.dueDate}) keldi."
                sendAutoSms(p.phone, msg)
            }
        }
        Toast.makeText(this, "📩 Qarzdorlarga SMS eslatmalar yuborildi!", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "📩 Avto-SMS yuborildi: $phoneNumber", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "SMS xatosi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun smallActionBtn(label: String, hex: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            background = glassBox(hex, "#66FFFFFF", 24f)
            setPadding(24, 18, 24, 18)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = 10 }
            setOnClickListener { onClick() }
        }
    }

    private fun actionPillBtn(label: String, hex: String, weight: Float, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = glassBox(hex, "#66FFFFFF", 28f)
            setPadding(20, 26, 20, 26)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
                setMargins(8, 0, 8, 0)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun emptyStateCard(txt: String): TextView {
        return TextView(this).apply {
            text = txt
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13.5f
            background = glassBox("#111927", "#2638BDF8", 34f)
            setPadding(40, 70, 40, 70)
        }
    }
}
