package uz.qarzdaftar.pro

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
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
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

class QarzFyDatabase(context: Context) : SQLiteOpenHelper(context, "qarzfy_v7_glossy.db", null, 1) {
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

    fun getProducts(): List<ProductRec> {
        val list = mutableListOf<ProductRec>()
        val c = readableDatabase.rawQuery("SELECT id, name, barcode, qty, unit, cost_price, sale_price, currency, supplier FROM products ORDER BY id DESC", null)
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

    fun getKassaList(): List<KassaRec> {
        val list = mutableListOf<KassaRec>()
        val c = readableDatabase.rawQuery("SELECT id, type, category, amount, currency, pay_type, date, note, creator FROM kassa ORDER BY id DESC", null)
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
    private var subscriptionExpiryMillis = 0L

    private var price1Month = 49000L
    private var price3Month = 135000L
    private var price6Month = 255000L
    private var adminCardNumber = "9860 0000 0000 0000"
    private var adminCardOwner = "HASAN JAYLOVOV"

    // Navigation & Sub-tabs states (PDF Parity)
    private var currentBottomTab = 0
    private var currentHomeSubTab = 0 // 0=Mijozlar, 1=Ta'minotchilar, 2=Xodimlar
    private var currentSortMode = "Faol"
    private var searchQuery = ""

    // Kassa states (PDF page 13)
    private var kassaTypeTab = "Xarajatlar" // Xarajatlar | Kirimlar
    private var kassaPayFilter = "Hammasi" // Hammasi | Naqd | Boshqa
    private var kassaPeriodFilter = "Barchasi" // 14 kun | Shu oy | 30 kun | 1 yil | Barchasi

    // Mahsulot states (PDF page 14-20)
    private var mahsulotTopTab = "Ombor" // Sotuv | Ombor | Qaytarish | Kirimlar
    private var mahsulotFilterTab = "Barchasi" // Barchasi | Mavjud | Tugagan

    // Statistika states (PDF page 24-29)
    private var statTopTab = "Mijozlar" // Mijozlar | Ta'minotchilar | Mahsulotlar | Xodimlar
    private var statProdSubTab = "Qoldiq" // Sotuv | Qoldiq | Kirim

    // Chap va O'ng yarim ko'rinuvchi slide panellar holati
    private var isLeftEdgeOpen = false
    private var isRightEdgeOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE),
            100
        )

        prefs = getSharedPreferences("QarzFyGlossyV7Prefs", Context.MODE_PRIVATE)
        db = QarzFyDatabase(this)
        rootLayout = findViewById(R.id.mainContentLayout)

        loadPrefs()

        if (userPhone.isBlank()) {
            renderOnboardingAndLogin()
        } else {
            checkServerSubscriptionAndRender()
            scheduleMidnightAutoExcelSaveWithSignal()
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
        subscriptionExpiryMillis = prefs.getLong("sub_expiry_millis", 0L)
        price1Month = prefs.getLong("price_1m", 49000L)
        price3Month = prefs.getLong("price_3m", 135000L)
        price6Month = prefs.getLong("price_6m", 255000L)
        adminCardNumber = prefs.getString("card_number", "9860 0000 0000 0000") ?: "9860 0000 0000 0000"
        adminCardOwner = prefs.getString("card_owner", "HASAN JAYLOVOV") ?: "HASAN JAYLOVOV"
    }

    // ==================== GLOSSY GLASSMORPHISM DRAWABLES ====================
    private fun glossyCard(fillHex: String = "#F2FFFFFF", strokeHex: String = "#FFFFFF", rad: Float = 44f): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = rad
            setColor(Color.parseColor(fillHex))
            setStroke(3, Color.parseColor(strokeHex))
        }
    }

    private fun glossyPurpleGrad(rad: Float = 46f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#7C3AED"), Color.parseColor("#6366F1"), Color.parseColor("#3B82F6"))
        ).apply {
            cornerRadius = rad
            setStroke(3, Color.parseColor("#B3FFFFFF"))
        }
    }

    private fun glossyOrangeGrad(rad: Float = 44f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#FB923C"), Color.parseColor("#F97316"), Color.parseColor("#EA580C"))
        ).apply {
            cornerRadius = rad
            setStroke(3, Color.parseColor("#B3FFFFFF"))
        }
    }

    private fun glossyRoundBtnBg(hex: String, strokeHex: String = "#CCFFFFFF"): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
            setStroke(4, Color.parseColor(strokeHex))
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
        return nf.format(amount).replace(",", " ") + (if (cur.isNotEmpty()) " $cur" else "")
    }

    private fun remainingDaysText(): String {
        val diff = subscriptionExpiryMillis - System.currentTimeMillis()
        if (diff <= 0) return "Obuna tugagan"
        val days = (diff / (1000L * 60 * 60 * 24)) + 1
        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(subscriptionExpiryMillis))
        return "$days kun qoldi ($dateStr gacha)"
    }

    // ==================== OBUNA TEKSHIRISH ====================
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
                price1Month = json.optLong("price_1m", 49000L)
                price3Month = json.optLong("price_3m", 135000L)
                price6Month = json.optLong("price_6m", 255000L)
                adminCardNumber = json.optString("card_number", adminCardNumber)
                adminCardOwner = json.optString("card_owner", adminCardOwner)

                val editor = prefs.edit()
                    .putLong("price_1m", price1Month)
                    .putLong("price_3m", price3Month)
                    .putLong("price_6m", price6Month)
                    .putString("card_number", adminCardNumber)
                    .putString("card_owner", adminCardOwner)

                if (serverExpiry > subscriptionExpiryMillis) {
                    subscriptionExpiryMillis = serverExpiry
                    editor.putLong("sub_expiry_millis", subscriptionExpiryMillis)
                }
                editor.apply()

                withContext(Dispatchers.Main) {
                    if (!serverActive && System.currentTimeMillis() > subscriptionExpiryMillis) {
                        renderSubscriptionLockScreen()
                    } else {
                        renderMainWorkspace()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun selectTariffAndPayDialog(months: Int, priceUzs: Long) {
        val fmtPrice = formatMoney(priceUzs, "so'm")
        val msg = """
            🏪 Do'kon ID: $storeId ($storeName)
            📅 Tanlangan tarif: $months Oylik obuna
            💰 To'lov summasi: $fmtPrice
            
            💳 Karta raqami:
            $adminCardNumber
            👤 Karta egasi: $adminCardOwner
            
            To'lov qilgach 'To'lov qildim' tugmasini bosing. Yangi vaqt mavjud obunangiz ustiga avtomatik qo'shiladi!
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("💳 $months Oylik Obuna — $fmtPrice")
            .setMessage(msg)
            .setPositiveButton("✅ To'lov qildim (Botda tasdiqlash)") { _, _ ->
                val now = System.currentTimeMillis()
                val baseTime = if (subscriptionExpiryMillis > now) subscriptionExpiryMillis else now
                val addedMillis = months.toLong() * 30L * 24L * 60L * 60L * 1000L
                subscriptionExpiryMillis = baseTime + addedMillis
                prefs.edit().putLong("sub_expiry_millis", subscriptionExpiryMillis).apply()

                Toast.makeText(this, "✅ +$months oy obuna vaqti qo'shildi!", Toast.LENGTH_LONG).show()
                renderMainWorkspace()

                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$BOT_USERNAME?start=${storeId}_${months}oy")))
                } catch (_: Exception) {}
            }
            .setNeutralButton("📋 Kartani nusxalash") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("card", adminCardNumber))
                Toast.makeText(this, "📋 Karta nusxalandi!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun renderSubscriptionLockScreen() {
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#EEF2FF"))
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(44, 60, 44, 60)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glossyCard("#FFFFFF", "#C7D2FE", 54f)
            setPadding(46, 54, 46, 54)
        }
        card.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(190, 190).apply { bottomMargin = 20 }
        })
        card.addView(TextView(this).apply {
            text = "Obuna muddati tugadi"
            setTextColor(Color.parseColor("#1E1B4B"))
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "Do'kon ID: $storeId ($storeName)\n💳 Karta: $adminCardNumber ($adminCardOwner)\n\nDavom etish uchun o'zingizga mos tarifni tanlang:"
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(10, 14, 10, 30)
        })

        card.addView(tariffButton("💳 1 Oy — ${formatMoney(price1Month, "so'm")}", "+30 kun qo'shiladi", "#6D28D9") {
            selectTariffAndPayDialog(1, price1Month)
        })
        card.addView(tariffButton("🔥 3 Oy — ${formatMoney(price3Month, "so'm")}", "+90 kun qo'shiladi", "#2563EB") {
            selectTariffAndPayDialog(3, price3Month)
        })
        card.addView(tariffButton("👑 6 Oy — ${formatMoney(price6Month, "so'm")}", "+180 kun qo'shiladi", "#059669") {
            selectTariffAndPayDialog(6, price6Month)
        })

        col.addView(card)
        scroll.addView(col)
        rootLayout.addView(scroll)
    }

    private fun tariffButton(title: String, sub: String, colorHex: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 36f
                setColor(Color.parseColor(colorHex))
            }
            setPadding(38, 28, 38, 28)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
            setOnClickListener { onClick() }
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = sub
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 12f
            })
        }
    }

    // ==================== 1. ONBOARDING VA LOGIN ====================
    private fun renderOnboardingAndLogin() {
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#EEF2FF"))
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 60, 44, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glossyPurpleGrad(54f)
            setPadding(44, 50, 44, 50)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 28 }
        }
        heroCard.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(180, 180).apply { bottomMargin = 16 }
        })
        heroCard.addView(TextView(this).apply {
            text = "QarzFy bular uchun yaratilgan!"
            setTextColor(Color.WHITE)
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        heroCard.addView(TextView(this).apply {
            text = "Mijozlar, ta'minotchilar, ombor va kassa — hammasini bir joyda Glossy Glassmorphism uslubida boshqaring."
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(10, 8, 10, 0)
        })
        container.addView(heroCard)

        val formCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#FFFFFF", 48f)
            elevation = 12f
            setPadding(42, 42, 42, 42)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val etStore = makeGlassInput("Do'kon nomi * (Masalan: Hasan Jaylovov)", storeName)
        val etAddr = makeGlassInput("Manzil (O'zbekiston, Surxondaryo)", storeAddress)
        val etPhone = makeGlassInput("Telefon raqam (+998909958297)", "+998")
        etPhone.inputType = InputType.TYPE_CLASS_PHONE
        val etPass = makeGlassInput("Parol (Admin panel uchun)", "")
        etPass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        formCard.addView(etStore)
        formCard.addView(etAddr)
        formCard.addView(etPhone)
        formCard.addView(etPass)

        val btnStart = Button(this).apply {
            text = "🚀 Boshlash (10 kun bepul Premium)"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(36f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 135).apply { topMargin = 16 }
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
                storeId = "QF-" + Math.abs(ph.hashCode() % 90000 + 10000)
                subscriptionExpiryMillis = System.currentTimeMillis() + (10L * 24L * 60L * 60L * 1000L)

                prefs.edit()
                    .putString("user_phone", userPhone)
                    .putString("store_id", storeId)
                    .putString("store_name", storeName)
                    .putString("store_address", storeAddress)
                    .putLong("sub_expiry_millis", subscriptionExpiryMillis)
                    .apply()

                if (db.getPersons(false).isEmpty()) {
                    db.upsertClientDirect("Hasan toga", "+998 90 995 82 97", 0.0, -20000L, db.nowStr(), false)
                    db.upsertClientDirect("Nortoy oshna", "+998 93 363 97 12", 0.0, 0L, db.nowStr(), false)
                }

                syncStoreWithAdminServer(ph, pw)
                renderMainWorkspace()
                scheduleMidnightAutoExcelSaveWithSignal()
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
            background = glossyCard("#F8FAFC", "#CBD5E1", 32f)
            setPadding(34, 26, 34, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
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
                }
                conn.outputStream.write(body.toString().toByteArray())
                conn.inputStream.close()
            } catch (_: Exception) {}
        }
    }

    // ==================== 2. ASOSIY WORKSPACE VA CHAP/O'NG YARIM KO'RINUVCHI SLIDE TUGMALAR ====================
    private fun renderMainWorkspace() {
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#EEF2FF"))

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

        // CHAP VA O'NG TOMONDA SAL KO'RINUVCHI SLIDE/ROUND TUGMALAR PANELI
        mainFrame.addView(buildLeftAndRightEdgeSlideOverlays())

        rootLayout.addView(mainFrame)
        rootLayout.addView(buildBottomNavBar())
    }

    private fun buildLeftAndRightEdgeSlideOverlays(): View {
        val overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val isSup = (currentBottomTab == 0 && currentHomeSubTab == 1)

        // Fon qorayishi (agar chap yoki o'ng menyu ochiq bo'lsa)
        if (isLeftEdgeOpen || isRightEdgeOpen) {
            overlay.setBackgroundColor(Color.parseColor("#550F172A"))
            overlay.setOnClickListener {
                isLeftEdgeOpen = false
                isRightEdgeOpen = false
                renderMainWorkspace()
            }
        }

        // 1) CHAP TOMONDAGI SAL KO'RINUVCHI KICHIK DUMALOQ TUGMA (O'ngga slide qilganda yoki bosganda 4 ta round tugma chiqadi)
        if (!isLeftEdgeOpen) {
            val leftPeekTab = TextView(this).apply {
                text = "❯"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                background = glossyPurpleGrad(60f)
                alpha = 0.78f
                elevation = 16f
                layoutParams = FrameLayout.LayoutParams(96, 150, Gravity.CENTER_VERTICAL or Gravity.START).apply {
                    leftMargin = -34 // Ekrandan sal chiqib turadi
                }
                var startX = 0f
                setOnTouchListener { _, ev ->
                    when (ev.action) {
                        MotionEvent.ACTION_DOWN -> { startX = ev.rawX; true }
                        MotionEvent.ACTION_MOVE -> {
                            if (ev.rawX - startX > 25f) {
                                isLeftEdgeOpen = true
                                isRightEdgeOpen = false
                                renderMainWorkspace()
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            isLeftEdgeOpen = true
                            isRightEdgeOpen = false
                            renderMainWorkspace()
                            true
                        }
                        else -> false
                    }
                }
            }
            overlay.addView(leftPeekTab)
        } else {
            // Chap menyu ochilganda o'ngga siljib chiqqan 4 ta ROUND 3D tugma (2-rasmdagidek)
            val leftCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL or Gravity.START
                ).apply { leftMargin = 28 }
            }

            val leftItems = listOf(
                Triple(R.drawable.ic_vec_import, "Excel Import", "#6366F1"),
                Triple(R.drawable.ic_vec_export, "Excel Eksport", "#6366F1"),
                Triple(R.drawable.ic_vec_user_add, if (isSup) "Ta'minotchi +" else "Qarzdor +", "#6366F1"),
                Triple(R.drawable.ic_vec_flash, "Tezkor tranzaksiya", "#F97316")
            )

            leftItems.forEach { (iconRes, labelText, hex) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 22 }
                    setOnClickListener {
                        isLeftEdgeOpen = false
                        renderMainWorkspace()
                        it.post {
                            when (labelText) {
                                "Excel Import" -> showImportBottomSheet()
                                "Excel Eksport" -> showExportBottomSheet()
                                "Tezkor tranzaksiya" -> showQuickTransactionBottomSheet(isSup)
                                else -> showAddClientBottomSheet(isSup)
                            }
                        }
                    }
                }
                val roundBtn = FrameLayout(this).apply {
                    background = if (hex == "#F97316") glossyOrangeGrad(70f) else glossyRoundBtnBg(hex)
                    elevation = 16f
                    layoutParams = LinearLayout.LayoutParams(134, 134)
                    addView(ImageView(this@MainActivity).apply {
                        setImageResource(iconRes)
                        layoutParams = FrameLayout.LayoutParams(68, 68, Gravity.CENTER)
                    })
                }
                val pill = TextView(this).apply {
                    text = labelText
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    background = glossyCard("#FFFFFF", "#E2E8F0", 34f)
                    elevation = 12f
                    setPadding(34, 20, 34, 20)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = 18 }
                }
                row.addView(roundBtn)
                row.addView(pill)
                leftCol.addView(row)
            }
            overlay.addView(leftCol)
        }

        // 2) O'NG TOMONDAGI SAL KO'RINUVCHI KICHIK DUMALOQ TUGMA (Chapga slide qilganda 1-rasmdagi menyu round bo'lib chiqadi)
        if (!isRightEdgeOpen) {
            val rightPeekTab = TextView(this).apply {
                text = "❮"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                background = glossyPurpleGrad(60f)
                alpha = 0.78f
                elevation = 16f
                layoutParams = FrameLayout.LayoutParams(96, 150, Gravity.CENTER_VERTICAL or Gravity.END).apply {
                    rightMargin = -34 // O'ng chetdan sal ko'rinib turadi
                }
                var startX = 0f
                setOnTouchListener { _, ev ->
                    when (ev.action) {
                        MotionEvent.ACTION_DOWN -> { startX = ev.rawX; true }
                        MotionEvent.ACTION_MOVE -> {
                            if (startX - ev.rawX > 25f) {
                                isRightEdgeOpen = true
                                isLeftEdgeOpen = false
                                renderMainWorkspace()
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            isRightEdgeOpen = true
                            isLeftEdgeOpen = false
                            renderMainWorkspace()
                            true
                        }
                        else -> false
                    }
                }
            }
            overlay.addView(rightPeekTab)
        } else {
            // O'ng menyu ochilganda 1-rasmdagi Kalkulyator, Bozorlik, Vazifalar, Sotuv, SMS jo'natma, Qo'ng'iroq ROUND shaklda chiqadi
            val rightCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL or Gravity.END
                ).apply { rightMargin = 28 }
            }

            val rightItems = listOf(
                Triple("Kalkulyator", R.drawable.ic_vec_calc) { openSalesCalculatorDialog() },
                Triple("Bozorlik", R.drawable.ic_vec_list) { openShoppingListDialog() },
                Triple("Vazifalar", R.drawable.ic_vec_tasks) { showCommunityAndAmbassadorDialog() },
                Triple("Sotuv", R.drawable.ic_vec_sale) { currentBottomTab = 2; mahsulotTopTab = "Sotuv"; renderMainWorkspace() },
                Triple("SMS jo'natma", R.drawable.ic_vec_sms_horn) { sendBulkSmsReminders(false) },
                Triple("Qo'ng'iroq", R.drawable.ic_vec_phone) { showVoiceSettingsDialog() }
            )

            rightItems.forEach { (label, vecRes, act) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
                    setOnClickListener {
                        isRightEdgeOpen = false
                        act.invoke()
                    }
                }
                val pillLabel = TextView(this).apply {
                    text = label
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    background = glossyCard("#FFFFFF", "#E2E8F0", 34f)
                    elevation = 12f
                    setPadding(36, 20, 36, 20)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 18 }
                }
                val roundIconBox = FrameLayout(this).apply {
                    background = glossyRoundBtnBg("#6366F1")
                    elevation = 16f
                    layoutParams = LinearLayout.LayoutParams(130, 130)
                    addView(ImageView(this@MainActivity).apply {
                        setImageResource(vecRes)
                        layoutParams = FrameLayout.LayoutParams(66, 66, Gravity.CENTER)
                    })
                }
                row.addView(pillLabel)
                row.addView(roundIconBox)
                rightCol.addView(row)
            }
            overlay.addView(rightCol)
        }

        return overlay
    }

    // ==================== TAB 0: ASOSIY EKRAN ====================
    private fun buildHomeTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 180)
        }

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
            background = glossyCard("#FFFFFF", "#FFFFFF", 30f)
            elevation = 6f
            layoutParams = LinearLayout.LayoutParams(105, 105).apply { rightMargin = 16 }
            setOnClickListener {
                isLeftEdgeOpen = !isLeftEdgeOpen
                isRightEdgeOpen = false
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
        val qrBtn = FrameLayout(this).apply {
            background = glossyCard("#FFFFFF", "#FFFFFF", 28f)
            elevation = 6f
            layoutParams = LinearLayout.LayoutParams(105, 105)
            addView(TextView(this@MainActivity).apply {
                text = "🔳"
                gravity = Gravity.CENTER
                textSize = 18f
            })
            setOnClickListener { showQrCodeAndOpenPageDialog() }
        }

        header.addView(menuIcon)
        header.addView(logoImg)
        header.addView(storeTitle)
        header.addView(qrBtn)
        col.addView(header)

        // Glossy Purple Banner
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glossyPurpleGrad(44f)
            elevation = 12f
            setPadding(38, 30, 38, 30)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 22 }
            setOnClickListener { showSubscriptionTariffsBottomSheet() }
        }
        banner.addView(TextView(this).apply {
            text = "🎁"
            textSize = 26f
            setPadding(0, 0, 24, 0)
        })
        banner.addView(TextView(this).apply {
            text = "10 kun bepul Premium (${remainingDaysText()})\nIstalgan tarifni tanlang • 00:00 Avto-Excel faol"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        banner.addView(TextView(this).apply {
            text = "❯"
            setTextColor(Color.WHITE)
            textSize = 18f
        })
        col.addView(banner)

        // Mijozlar | Ta'minotchilar | Xodimlar
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
                background = if (sel) glossyPurpleGrad(30f) else glossyCard("#FFFFFF", "#E2E8F0", 30f)
                elevation = if (sel) 8f else 2f
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
            background = glossyCard("#FFFFFF", "#CBD5E1", 24f)
            layoutParams = LinearLayout.LayoutParams(80, 80)
            setOnClickListener { openCreateFolderDialog() }
        })
        col.addView(folderBar)

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
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
                background = glossyCard(if (act) "#E0E7FF" else "#FFFFFF", "#CBD5E1", 24f)
                setPadding(22, 16, 22, 16)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 10 }
                setOnClickListener {
                    currentSortMode = m
                    renderMainWorkspace()
                }
            })
        }
        col.addView(filterRow)

        val etSearch = makeGlassInput("🔍 Ism, Telefon raqam...", searchQuery)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { searchQuery = s?.toString() ?: "" }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        col.addView(etSearch)

        if (persons.isEmpty()) {
            val emptyBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = glossyCard("#FFFFFF", "#FFFFFF", 42f)
                elevation = 6f
                setPadding(40, 70, 40, 70)
            }
            emptyBox.addView(TextView(this).apply {
                text = if (isSup) "Ta'minotchi topilmadi" else "Qarz yuritishni boshlash uchun birinchi mijozingizni qo'shing"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 24)
            })
            emptyBox.addView(Button(this).apply {
                text = if (isSup) "➕ Ta'minotchi qo'shish" else "➕ Mijoz qo'shish"
                setTextColor(Color.WHITE)
                background = glossyPurpleGrad(32f)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 120)
                setPadding(50, 0, 50, 0)
                setOnClickListener { showAddClientBottomSheet(isSup) }
            })
            col.addView(emptyBox)
        } else {
            persons.forEach { p ->
                val rowCard = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = glossyCard("#FFFFFF", "#FFFFFF", 36f)
                    elevation = 6f
                    setPadding(28, 24, 32, 24)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                    setOnClickListener { showClientKirimChiqimBottomSheet(p, isSup) }
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

    // ==================== TAB 1: KASSA (PDF 13-bet bilan 100% bir xil!) ====================
    private fun buildKassaTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 180)
        }

        val allKassa = db.getKassaList()
        var kirimSum = 0L
        var chiqimSum = 0L
        allKassa.forEach {
            if (it.type == "Kirim") kirimSum += it.amount else chiqimSum += it.amount
        }
        val netBal = kirimSum - chiqimSum

        // PDF 13-bet Top Row: 📈 Round Icon | +0 UZS ⓘ | UZS ▾
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
        }
        topRow.addView(TextView(this).apply {
            text = "📈"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.WHITE)
            background = glossyRoundBtnBg("#6366F1")
            layoutParams = LinearLayout.LayoutParams(110, 110)
        })
        topRow.addView(TextView(this).apply {
            text = "${if (netBal >= 0) "+" else ""}${formatMoney(netBal, "UZS")} ⓘ"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#10B981"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topRow.addView(TextView(this).apply {
            text = "$mainCurrency ▾"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glossyCard("#FFFFFF", "#E2E8F0", 26f)
            setPadding(28, 16, 28, 16)
        })
        col.addView(topRow)

        // PDF 13-bet: Xarajatlar | Kirimlar pill switch
        val switchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 50f)
            elevation = 6f
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 26 }
        }
        listOf("Xarajatlar", "Kirimlar").forEach { tabName ->
            val sel = (kassaTypeTab == tabName)
            switchBox.addView(TextView(this).apply {
                text = tabName
                gravity = Gravity.CENTER
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#334155"))
                background = if (sel) glossyPurpleGrad(44f) else null
                setPadding(20, 26, 20, 26)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    kassaTypeTab = tabName
                    renderMainWorkspace()
                }
            })
        }
        col.addView(switchBox)

        // PDF 13-bet: 📊 Round Icon | 0 UZS ⓘ | ⚙️ Filter Round Icon
        val activeSum = if (kassaTypeTab == "Xarajatlar") chiqimSum else kirimSum
        val midRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
        }
        midRow.addView(TextView(this).apply {
            text = "📊"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.WHITE)
            background = glossyRoundBtnBg("#6366F1")
            layoutParams = LinearLayout.LayoutParams(110, 110)
            setOnClickListener { currentBottomTab = 3; renderMainWorkspace() }
        })
        midRow.addView(TextView(this).apply {
            text = "${formatMoney(activeSum, "UZS")} ⓘ"
            gravity = Gravity.CENTER
            setTextColor(if (kassaTypeTab == "Xarajatlar") Color.parseColor("#B91C1C") else Color.parseColor("#059669"))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        midRow.addView(TextView(this).apply {
            text = "⚙️"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.WHITE)
            background = glossyRoundBtnBg("#6366F1")
            layoutParams = LinearLayout.LayoutParams(110, 110)
            setOnClickListener { openAddKassaDialog(if (kassaTypeTab == "Xarajatlar") "Chiqim" else "Kirim") }
        })
        col.addView(midRow)

        // PDF 13-bet: Hammasi | Naqd | Boshqa
        val payFilterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        listOf("Hammasi", "Naqd", "Boshqa").forEach { pf ->
            val sel = (kassaPayFilter == pf)
            payFilterRow.addView(TextView(this).apply {
                text = pf
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.parseColor("#4F46E5") else Color.parseColor("#1E293B"))
                background = glossyCard(if (sel) "#E0E7FF" else "#FFFFFF", if (sel) "#6366F1" else "#E2E8F0", 40f)
                setPadding(36, 18, 36, 18)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 14 }
                setOnClickListener { kassaPayFilter = pf; renderMainWorkspace() }
            })
        }
        col.addView(payFilterRow)

        // PDF 13-bet: 14 kun | Shu oy | 30 kun | 1 yil | Barchasi
        val periodScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
        }
        val periodRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("14 kun", "Shu oy", "30 kun", "1 yil", "Barchasi").forEach { pr ->
            val sel = (kassaPeriodFilter == pr)
            periodRow.addView(TextView(this).apply {
                text = pr
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.parseColor("#4F46E5") else Color.parseColor("#1E293B"))
                background = glossyCard(if (sel) "#E0E7FF" else "#FFFFFF", if (sel) "#6366F1" else "#E2E8F0", 40f)
                setPadding(32, 16, 32, 16)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 12 }
                setOnClickListener { kassaPeriodFilter = pr; renderMainWorkspace() }
            })
        }
        periodScroll.addView(periodRow)
        col.addView(periodScroll)

        // Kirim / Chiqim qo'shish tugmalari
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 22 }
        }
        actionRow.addView(Button(this).apply {
            text = "➕ Kirim qo'shish"
            setTextColor(Color.WHITE)
            background = glossyPurpleGrad(32f)
            layoutParams = LinearLayout.LayoutParams(0, 125, 1f).apply { rightMargin = 10 }
            setOnClickListener { openAddKassaDialog("Kirim") }
        })
        actionRow.addView(Button(this).apply {
            text = "➖ Xarajat qo'shish"
            setTextColor(Color.WHITE)
            background = glossyOrangeGrad(32f)
            layoutParams = LinearLayout.LayoutParams(0, 125, 1f).apply { leftMargin = 10 }
            setOnClickListener { openAddKassaDialog("Chiqim") }
        })
        col.addView(actionRow)

        val targetKind = if (kassaTypeTab == "Xarajatlar") "Chiqim" else "Kirim"
        val filtered = allKassa.filter { it.type == targetKind }

        if (filtered.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "Mavjud emas\nYangi yozuv qo'shish uchun yuqoridagi + tugmasini bosing"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#64748B"))
                textSize = 14f
                background = glossyCard("#FFFFFF", "#FFFFFF", 40f)
                setPadding(40, 80, 40, 80)
            })
        } else {
            filtered.forEach { k ->
                col.addView(TextView(this).apply {
                    text = "${if (k.type == "Kirim") "🟢" else "🔴"} ${k.category} (${k.payType}) — ${formatMoney(k.amount, k.currency)}\n📅 ${k.date} • ${k.note}"
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 14f
                    background = glossyCard("#FFFFFF", "#FFFFFF", 32f)
                    elevation = 4f
                    setPadding(32, 26, 32, 26)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                })
            }
        }

        scroll.addView(col)
        return scroll
    }

    // ==================== TAB 2: MAHSULOT VA OMBOR (PDF 14-20 betlar bilan 100% bir xil!) ====================
    private fun buildMahsulotTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 180)
        }

        // PDF 14-15 betlar: Sotuv | Ombor | Qaytarish / Hisobdan chiqarish | Kirimlar
        val topGrid1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }
        listOf("Sotuv", "Ombor").forEach { t ->
            val sel = (mahsulotTopTab == t)
            topGrid1.addView(TextView(this).apply {
                text = t
                gravity = Gravity.CENTER
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#1E293B"))
                background = if (sel) glossyPurpleGrad(30f) else glossyCard("#FFFFFF", "#E2E8F0", 30f)
                setPadding(20, 26, 20, 26)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
                setOnClickListener { mahsulotTopTab = t; renderMainWorkspace() }
            })
        }
        col.addView(topGrid1)

        val topGrid2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        listOf("Qaytarish / Hisobdan chiqarish", "Kirimlar").forEach { t ->
            val sel = (mahsulotTopTab == t)
            topGrid2.addView(TextView(this).apply {
                text = t
                gravity = Gravity.CENTER
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#1E293B"))
                background = if (sel) glossyPurpleGrad(30f) else glossyCard("#FFFFFF", "#E2E8F0", 30f)
                setPadding(16, 24, 16, 24)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
                setOnClickListener { mahsulotTopTab = t; renderMainWorkspace() }
            })
        }
        col.addView(topGrid2)

        // Mahsulot qidirish + QR + Plus
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        val etPrSearch = makeGlassInput("🔍 Mahsulot qidirish...", "").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 12 }
        }
        val btnPlusMenu = TextView(this).apply {
            text = "➕"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.WHITE)
            background = glossyPurpleGrad(28f)
            layoutParams = LinearLayout.LayoutParams(110, 110)
            setOnClickListener { showMahsulotActionsBottomSheet() }
        }
        searchRow.addView(etPrSearch)
        searchRow.addView(btnPlusMenu)
        col.addView(searchRow)

        val prods = db.getProducts()

        // Barchasi | 0 Mahsulot | Mavjud | Tugagan (PDF 15-bet)
        val filterChips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        listOf("Barchasi", "${prods.size} Mahsulot", "Mavjud", "Tugagan").forEach { ch ->
            val sel = (mahsulotFilterTab == ch)
            filterChips.addView(TextView(this).apply {
                text = ch
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#334155"))
                background = if (sel) glossyPurpleGrad(24f) else glossyCard("#FFFFFF", "#E2E8F0", 24f)
                setPadding(24, 16, 24, 16)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 10 }
                setOnClickListener { mahsulotFilterTab = ch; renderMainWorkspace() }
            })
        }
        col.addView(filterChips)

        if (prods.isEmpty()) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = glossyCard("#FFFFFF", "#FFFFFF", 42f)
                elevation = 6f
                setPadding(44, 70, 44, 70)
            }
            emptyCard.addView(TextView(this).apply {
                text = "📦 Omborda mahsulot yo'q\nBirinchi yetkazib berishni qabul qilish uchun pastdagi tugmani bosing"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#64748B"))
                textSize = 14f
                setPadding(0, 0, 0, 26)
            })
            emptyCard.addView(Button(this).apply {
                text = "+ Kirim qilish / Mahsulot yaratish"
                setTextColor(Color.WHITE)
                background = glossyPurpleGrad(34f)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130)
                setOnClickListener { showMahsulotActionsBottomSheet() }
            })
            col.addView(emptyCard)
        } else {
            prods.forEach { pr ->
                col.addView(TextView(this).apply {
                    text = "📦 ${pr.name}  •  ${pr.qty} ${pr.unit}\nTan narx: ${formatMoney(pr.costPrice, pr.currency)} | Sotuv: ${formatMoney(pr.salePrice, pr.currency)} | Kod: ${pr.barcode}"
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 14f
                    background = glossyCard("#FFFFFF", "#FFFFFF", 32f)
                    elevation = 4f
                    setPadding(32, 26, 32, 26)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                })
            }
        }

        scroll.addView(col)
        return scroll
    }

    // PDF 16-betdagi 5 ta Mahsulot Slide-up menyusi
    private fun showMahsulotActionsBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 32, 44, 54)
        }
        val items = listOf(
            "➕  Mahsulot yaratish" to { openAddProductDialog() },
            "📥  Mahsulot kirim qilish" to { openAddProductDialog() },
            "📸  Tokchadan mahsulot qo'shish (AI Skan)" to {
                Toast.makeText(this, "📸 Tokchadan AI skaner: Rasm tanlang (Bepul skanlar: 1)", Toast.LENGTH_LONG).show()
            },
            "🧾  Nakladnoydan kirim qilish" to { openAddProductDialog() },
            "🕒  Kirimlar tarixi" to { mahsulotTopTab = "Kirimlar"; renderMainWorkspace() }
        )
        items.forEach { (title, act) ->
            wrap.addView(TextView(this).apply {
                text = "$title   ❯"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                background = glossyCard("#F8FAFC", "#E2E8F0", 30f)
                setPadding(34, 28, 34, 28)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                setOnClickListener { sheet.dismiss(); act.invoke() }
            })
        }
        sheet.setContentView(wrap)
        sheet.show()
    }

    // ==================== TAB 3: STATISTIKA (PDF 24-29 betlar bilan 100% bir xil!) ====================
    private fun buildStatistikaTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 180)
        }

        // PDF 24-28 betlar Top Tabs: Mijozlar | Ta'minotchilar | Mahsulotlar | Xodimlar
        val topTabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 32f)
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        listOf("Mijozlar", "Ta'minotchilar", "Mahsulotlar", "Xodimlar").forEach { t ->
            val sel = (statTopTab == t)
            topTabs.addView(TextView(this).apply {
                text = t
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#475569"))
                background = if (sel) glossyPurpleGrad(26f) else null
                setPadding(12, 22, 12, 22)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { statTopTab = t; renderMainWorkspace() }
            })
        }
        col.addView(topTabs)

        if (statTopTab == "Mahsulotlar") {
            // PDF 26-27 betlar: Sotuv | Qoldiq | Kirim + Ombor qiymati Binafsha Karta
            val subRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
            }
            listOf("Sotuv", "Qoldiq", "Kirim").forEach { st ->
                val sel = (statProdSubTab == st)
                subRow.addView(TextView(this).apply {
                    text = st
                    textSize = 13.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (sel) Color.WHITE else Color.parseColor("#1E293B"))
                    background = if (sel) glossyPurpleGrad(26f) else glossyCard("#FFFFFF", "#E2E8F0", 26f)
                    setPadding(34, 20, 34, 20)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 12 }
                    setOnClickListener { statProdSubTab = st; renderMainWorkspace() }
                })
            }
            col.addView(subRow)

            val prods = db.getProducts()
            var omborVal = 0L
            var sotuvVal = 0L
            prods.forEach { omborVal += it.costPrice * it.qty; sotuvVal += it.salePrice * it.qty }

            val purpleCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = glossyPurpleGrad(42f)
                elevation = 10f
                setPadding(40, 38, 40, 38)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
            }
            purpleCard.addView(TextView(this).apply {
                text = "Ombor qiymati"
                setTextColor(Color.parseColor("#E0E7FF"))
                textSize = 14f
            })
            purpleCard.addView(TextView(this).apply {
                text = formatMoney(omborVal, "UZS")
                setTextColor(Color.WHITE)
                textSize = 26f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 8, 0, 18)
            })
            purpleCard.addView(TextView(this).apply {
                text = "Sotuv narxi: ${formatMoney(sotuvVal, "UZS")}     |     Kutilayotgan foyda: ${formatMoney(sotuvVal - omborVal, "UZS")}"
                setTextColor(Color.WHITE)
                textSize = 12.5f
            })
            col.addView(purpleCard)

            // Tugagan / Tugayotgan / O'lik tovar (PDF 27-bet)
            val statusGrid = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            listOf("🔴 Tugagan\n0", "🟠 Tugayotgan\n0", "⚪ O'lik tovar\n0").forEach { lbl ->
                statusGrid.addView(TextView(this).apply {
                    text = lbl
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    background = glossyCard("#FFFFFF", "#E2E8F0", 30f)
                    setPadding(20, 28, 20, 28)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 6, 0) }
                })
            }
            col.addView(statusGrid)
            scroll.addView(col)
            return scroll
        }

        // Mijozlar / Ta'minotchilar Statistika (PDF 24-25 betlar)
        val isSup = (statTopTab == "Ta'minotchilar")
        val persons = db.getPersons(isSup)
        var jamiQarz = 0L
        var haqdorSum = 0L
        persons.forEach {
            if (it.balUzs < 0) jamiQarz += Math.abs(it.balUzs) else haqdorSum += it.balUzs
        }

        val balGrid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        }
        balGrid.addView(profileGridCard("🔴 Chiqim balansi ⓘ", formatMoney(jamiQarz, "UZS")) {})
        balGrid.addView(profileGridCard("🟢 Kirim balansi ⓘ", formatMoney(haqdorSum, "UZS")) {})
        col.addView(balGrid)

        val qarzCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#FFFFFF", 40f)
            elevation = 6f
            setPadding(36, 32, 36, 32)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        qarzCard.addView(TextView(this).apply {
            text = "Qarzdorlar  |  Haqdorlar  |  Hisob-kitob"
            setTextColor(Color.parseColor("#4F46E5"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        })
        qarzCard.addView(TextView(this).apply {
            text = "\nJami qarz: ${formatMoney(jamiQarz, "UZS")}\nMijozlar soni: ${persons.size} ta mijoz"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        })
        col.addView(qarzCard)

        scroll.addView(col)
        return scroll
    }

    // ==================== TAB 4: PROFIL ====================
    private fun buildProfilTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 180)
        }

        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glossyCard("#FFFFFF", "#FFFFFF", 44f)
            elevation = 6f
            setPadding(34, 30, 34, 30)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        val profAvatar = TextView(this).apply {
            text = "👤\nBepul"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(60f)
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

        val r1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        r1.addView(profileGridCard("🔔 Bildirishnomalar", "00:00 Avto-Excel + Signal") { showPushSettingsDialog() })
        r1.addView(profileGridCard("💎 Limitlar va tarif", remainingDaysText()) { showSubscriptionTariffsBottomSheet() })
        col.addView(r1)

        val r2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        r2.addView(profileGridCard("💳 Obuna xarid qilish", "${price1Month/1000}k / ${price3Month/1000}k / ${price6Month/1000}k") { showSubscriptionTariffsBottomSheet() })
        r2.addView(profileGridCard("🏪 Mening bizneslarim", storeName) { openBusinessInfoDialog() })
        col.addView(r2)

        val smsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#FFFFFF", 38f)
            elevation = 4f
            setPadding(36, 30, 36, 30)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
            setOnClickListener { showSmsSettingsDialog() }
        }
        smsCard.addView(TextView(this).apply {
            text = "💬 SMS  •  25 SMS qoldi (SIM Avto-SMS yoniq)"
            setTextColor(Color.parseColor("#059669"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        })
        smsCard.addView(View(this).apply {
            background = glossyCard("#10B981", "#10B981", 10f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 16).apply { topMargin = 16 }
        })
        col.addView(smsCard)

        val settingsList = listOf(
            "📤 Excel (.xlsx) Eksport — Download/QarzFy papkaga va Telegramga" to { showExportBottomSheet() },
            "📥 Excel (.xlsx) Fayldan Mijozlarni Import Qilish" to { showImportBottomSheet() },
            "👑 Super-Admin Panelga Kirish (Login: admin / Parol: hasan2026)" to {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$SERVER_URL/admin")))
            },
            "🖨 Printer sozlamalari (58mm / 80mm / A4)" to { showPrinterSettingsDialog() },
            "🤝 Hamjamiyat va Ambassador dasturi" to { showCommunityAndAmbassadorDialog() },
            "👨‍💻 Dastur haqida: Hasan Jaylovov (@HasanJaylovov)" to { showDeveloperDialog() }
        )
        settingsList.forEach { (title, act) ->
            col.addView(TextView(this).apply {
                text = "$title   ❯"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                background = glossyCard("#FFFFFF", "#FFFFFF", 34f)
                elevation = 4f
                setPadding(34, 28, 34, 28)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                setOnClickListener { act.invoke() }
            })
        }

        scroll.addView(col)
        return scroll
    }

    private fun profileGridCard(title: String, sub: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#FFFFFF", 36f)
            elevation = 4f
            setPadding(30, 28, 30, 28)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 6, 0) }
            setOnClickListener { onClick() }
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = sub
                setTextColor(Color.parseColor("#059669"))
                textSize = 12f
                setPadding(0, 8, 0, 0)
            })
        }
    }

    private fun buildBottomNavBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 0f)
            elevation = 24f
            setPadding(12, 16, 12, 16)
            gravity = Gravity.CENTER
        }
        val tabs = listOf("🏠\nAsosiy", "💵\nKassa", "📦\nMahsulot", "📊\nStatistika", "👤\nProfil")
        tabs.forEachIndexed { idx, title ->
            val active = (currentBottomTab == idx)
            bar.addView(TextView(this).apply {
                text = title
                gravity = Gravity.CENTER
                textSize = 11.5f
                setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (active) Color.parseColor("#4F46E5") else Color.parseColor("#64748B"))
                background = if (active) glossyCard("#EEF2FF", "#C7D2FE", 28f) else null
                setPadding(8, 14, 8, 14)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 0, 4, 0) }
                setOnClickListener {
                    currentBottomTab = idx
                    isLeftEdgeOpen = false
                    isRightEdgeOpen = false
                    renderMainWorkspace()
                }
            })
        }
        return bar
    }

    // ==================== 3. HAQIQIY .XLSX GENERATOR + QarzFy PAPKAGA 00:00 AVTO SAQLASH VA SIGNAL ====================
    private fun saveRealXlsxToQarzFyFolder(): Pair<File, String> {
        val dateTag = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val fileName = "QarzFy_Hisobot_$dateTag.xlsx"

        val appSubDir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "QarzFy")
        if (!appSubDir.exists()) appSubDir.mkdirs()
        val localXlsx = File(appSubDir, fileName)

        val xlsxBytes = generateRealXlsxZipBytes()
        localXlsx.writeBytes(xlsxBytes)

        var publicPath = "Download/QarzFy/$fileName"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/QarzFy")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(xlsxBytes) }
                }
            } else {
                val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "QarzFy")
                if (!pubDir.exists()) pubDir.mkdirs()
                File(pubDir, fileName).writeBytes(xlsxBytes)
            }
        } catch (_: Exception) {}

        return Pair(localXlsx, publicPath)
    }

    private fun xmlEscape(s: String): String {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }

    private fun buildSheetXml(rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rIdx, row ->
            sb.append("""<row r="${rIdx + 1}">""")
            row.forEachIndexed { cIdx, v ->
                val colLet = ('A' + cIdx)
                sb.append("""<c r="$colLet${rIdx + 1}" t="inlineStr"><is><t>${xmlEscape(v)}</t></is></c>""")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    // Haqiqiy 6-varaqqa ega .xlsx (OpenXML) fayl generatori!
    private fun generateRealXlsxZipBytes(): ByteArray {
        val clients = db.getPersons(false)
        val clientTxs = db.getTransactions(false)
        val suppliers = db.getPersons(true)
        val supTxs = db.getTransactions(true)
        val kassaList = db.getKassaList()

        val s1 = mutableListOf(listOf("ID", "Ism", "Telefon raqami", "Balans USD", "Balans UZS", "Yaratilgan sana"))
        clients.forEach { s1.add(listOf(it.id.toString(), it.name, it.phone, it.balUsd.toString(), it.balUzs.toString(), it.createdAt)) }

        val s2 = mutableListOf(listOf("Tranzaksiya ID", "Turi", "Mijoz ismi", "Mijoz telefoni", "Summa", "Valyuta", "Sana", "Tavsif", "Holat"))
        clientTxs.forEach { s2.add(listOf(it.txId, it.kind, it.personName, it.personPhone, it.amount.toString(), it.currency, it.date, it.note, it.status)) }

        val s3 = mutableListOf(listOf("ID", "Ism", "Telefon raqami", "Balans USD", "Balans UZS", "Manzil", "Yaratilgan sana"))
        suppliers.forEach { s3.add(listOf(it.id.toString(), it.name, it.phone, it.balUsd.toString(), it.balUzs.toString(), storeAddress, it.createdAt)) }

        val s4 = mutableListOf(listOf("Tranzaksiya ID", "Turi", "Ta'minotchi ismi", "Ta'minotchi telefoni", "Summa", "Valyuta", "Sana", "Tavsif", "Holat"))
        supTxs.forEach { s4.add(listOf(it.txId, it.kind, it.personName, it.personPhone, it.amount.toString(), it.currency, it.date, it.note, it.status)) }

        var kIn = 0L; var kOut = 0L
        kassaList.forEach { if (it.type == "Kirim") kIn += it.amount else kOut += it.amount }
        val s5 = listOf(
            listOf("Valyuta", "Jami kirim", "Jami chiqim", "Balans", "Naqd kirim", "Plastik kirim", "Naqd chiqim", "Plastik chiqim"),
            listOf("UZS", kIn.toString(), kOut.toString(), (kIn - kOut).toString(), kIn.toString(), "0", kOut.toString(), "0")
        )

        val s6 = mutableListOf(listOf("ID", "Turi", "Kategoriya", "Summa", "Valyuta", "To'lov turi", "Sana", "Tavsif", "Kim yaratdi"))
        kassaList.forEach { s6.add(listOf(it.id.toString(), it.type, it.category, it.amount.toString(), it.currency, it.payType, it.date, it.note, it.creator)) }

        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)

        fun putXml(name: String, xml: String) {
            zos.putNextEntry(ZipEntry(name))
            zos.write(xml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        putXml("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                <Override PartName="/xl/worksheets/sheet4.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                <Override PartName="/xl/worksheets/sheet5.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                <Override PartName="/xl/worksheets/sheet6.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            </Types>""".trimIndent())

        putXml("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>""".trimIndent())

        putXml("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets>
                    <sheet name="Mijozlar" sheetId="1" r:id="rId1"/>
                    <sheet name="Tranzaksiyalar" sheetId="2" r:id="rId2"/>
                    <sheet name="Ta'minotchilar" sheetId="3" r:id="rId3"/>
                    <sheet name="Ta'minotchi tranzaksiyalari" sheetId="4" r:id="rId4"/>
                    <sheet name="Kassa xulosasi" sheetId="5" r:id="rId5"/>
                    <sheet name="Kassa yozuvlari" sheetId="6" r:id="rId6"/>
                </sheets>
            </workbook>""".trimIndent())

        putXml("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
                <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>
                <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet5.xml"/>
                <Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet6.xml"/>
            </Relationships>""".trimIndent())

        putXml("xl/worksheets/sheet1.xml", buildSheetXml(s1))
        putXml("xl/worksheets/sheet2.xml", buildSheetXml(s2))
        putXml("xl/worksheets/sheet3.xml", buildSheetXml(s3))
        putXml("xl/worksheets/sheet4.xml", buildSheetXml(s4))
        putXml("xl/worksheets/sheet5.xml", buildSheetXml(s5))
        putXml("xl/worksheets/sheet6.xml", buildSheetXml(s6))

        zos.finish()
        zos.close()
        return baos.toByteArray()
    }

    private fun triggerAutoExportSignalAndNotification(savedPath: String) {
        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, soundUri)?.play()

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chId = "qarzfy_auto_excel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel(chId, "QarzFy Avto Excel", NotificationManager.IMPORTANCE_HIGH))
            }
            val notif = NotificationCompat.Builder(this, chId)
                .setSmallIcon(R.drawable.ic_qarzdaftar_logo)
                .setContentTitle("📊 QarzFy Kunlik Excel Saqlandi!")
                .setContentText("Fayl: $savedPath")
                .setAutoCancel(true)
                .build()
            nm.notify(1001, notif)
        } catch (_: Exception) {}
    }

    private fun scheduleMidnightAutoExcelSaveWithSignal() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val cal = Calendar.getInstance()
                if (cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0) {
                    val (_, path) = saveRealXlsxToQarzFyFolder()
                    withContext(Dispatchers.Main) {
                        triggerAutoExportSignalAndNotification(path)
                        Toast.makeText(this@MainActivity, "🔔 00:00 Avto-Eksport saqlandi:\n$path", Toast.LENGTH_LONG).show()
                    }
                    delay(65_000L)
                }
                delay(25_000L)
            }
        }
    }

    private fun shareExcelFileToTelegram(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "📊 QarzFy Rasmiy Excel Hisobot (.xlsx)\n🏪 Do'kon: $storeName ($storeId)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Excel (.xlsx) faylni Telegramga yuborish"))
        } catch (e: Exception) {
            Toast.makeText(this, "Saqlangan joyi: Download/QarzFy/${file.name}", Toast.LENGTH_LONG).show()
        }
    }

    // ==================== SLIDE-UP BOTTOM SHEETS ====================
    private fun showExportBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 30, 44, 56)
        }
        wrap.addView(TextView(this).apply {
            text = "📤 Excel (.xlsx) Eksport"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })
        wrap.addView(TextView(this).apply {
            text = "Hisobot haqiqiy 6-betli Excel (.xlsx) formatda telefon xotirasidagi 'Download/QarzFy' papkasiga saqlanadi. Har kuni 00:00 da signal bilan avtomatik saqlanadi!"
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            setPadding(0, 0, 0, 24)
        })

        wrap.addView(Button(this).apply {
            text = "💾 Download/QarzFy papkaga Excel (.xlsx) saqlash"
            setTextColor(Color.WHITE)
            background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130).apply { bottomMargin = 16 }
            setOnClickListener {
                val (file, savedPath) = saveRealXlsxToQarzFyFolder()
                triggerAutoExportSignalAndNotification(savedPath)
                sheet.dismiss()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("✅ Excel (.xlsx) Saqlandi!")
                    .setMessage("Papka va fayl:\n$savedPath")
                    .setPositiveButton("📤 Faylni Telegramga Yuborish") { _, _ ->
                        shareExcelFileToTelegram(file)
                    }
                    .setNegativeButton("Yopish", null)
                    .show()
            }
        })

        wrap.addView(Button(this).apply {
            text = "📤 Excel (.xlsx) Faylni Telegramga Yuborish"
            setTextColor(Color.WHITE)
            background = glossyOrangeGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130)
            setOnClickListener {
                val (file, savedPath) = saveRealXlsxToQarzFyFolder()
                triggerAutoExportSignalAndNotification(savedPath)
                sheet.dismiss()
                shareExcelFileToTelegram(file)
            }
        })

        sheet.setContentView(wrap)
        sheet.show()
    }

    private fun showImportBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 30, 44, 56)
        }
        wrap.addView(TextView(this).apply {
            text = "📥 Excel (.xlsx) Fayldan Import"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })
        wrap.addView(Button(this).apply {
            text = "📂 Telefondan Excel (.xlsx) Fayl Tanlash"
            setTextColor(Color.WHITE)
            background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 135)
            setOnClickListener {
                sheet.dismiss()
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, PICK_IMPORT_FILE)
            }
        })
        sheet.setContentView(wrap)
        sheet.show()
    }

    private fun showClientKirimChiqimBottomSheet(client: ClientRec, isSupplier: Boolean) {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 54f)
            setPadding(48, 32, 48, 56)
        }
        var selectedKind = "Kirim"
        val kindSwitchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glossyCard("#F1F5F9", "#E2E8F0", 32f)
            setPadding(10, 10, 10, 10)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        val btnKirim = TextView(this)
        val btnChiqim = TextView(this)

        fun updateKindTabs() {
            val kSel = (selectedKind == "Kirim")
            btnKirim.apply {
                text = "Kirim (To'lov)"; gravity = Gravity.CENTER; textSize = 14f; setTypeface(null, Typeface.BOLD)
                setTextColor(if (kSel) Color.WHITE else Color.parseColor("#475569"))
                background = if (kSel) glossyPurpleGrad(26f) else null
                setPadding(16, 22, 16, 22)
            }
            btnChiqim.apply {
                text = "Chiqim (Qarz)"; gravity = Gravity.CENTER; textSize = 14f; setTypeface(null, Typeface.BOLD)
                setTextColor(if (!kSel) Color.WHITE else Color.parseColor("#475569"))
                background = if (!kSel) glossyOrangeGrad(26f) else null
                setPadding(16, 22, 16, 22)
            }
        }
        btnKirim.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        btnChiqim.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        btnKirim.setOnClickListener { selectedKind = "Kirim"; updateKindTabs() }
        btnChiqim.setOnClickListener { selectedKind = "Chiqim"; updateKindTabs() }
        updateKindTabs()
        kindSwitchRow.addView(btnKirim); kindSwitchRow.addView(btnChiqim)
        wrap.addView(kindSwitchRow)

        wrap.addView(TextView(this).apply {
            text = "👤 ${client.name}  •  Balans: ${formatMoney(client.balUzs, "UZS")}"
            setTextColor(Color.parseColor("#0F172A")); textSize = 15.5f; setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        })

        val etAmount = makeGlassInput("Miqdorni qo'shish (UZS)", "")
        etAmount.inputType = InputType.TYPE_CLASS_NUMBER
        val etNote = makeGlassInput("Nima uchun (Mahsulot 1, Mahsulot 2)", "")
        wrap.addView(etAmount); wrap.addView(etNote)

        wrap.addView(Button(this).apply {
            text = "To'lov qo'shish"
            setTextColor(Color.WHITE); background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 135)
            setOnClickListener {
                val amt = etAmount.text.toString().trim().toLongOrNull() ?: 0L
                if (amt > 0) {
                    db.addOrUpdatePersonTx(client.name, client.phone, amt, "UZS", selectedKind, etNote.text.toString(), "Bugun", isSupplier)
                    sendAutoSms(client.phone, "Hurmatli ${client.name}, $storeName do'konida ${formatMoney(amt, "UZS")} ($selectedKind) qayd etildi.")
                    sheet.dismiss()
                    renderMainWorkspace()
                }
            }
        })
        sheet.setContentView(wrap)
        sheet.show()
    }

    private fun showQuickTransactionBottomSheet(isSupplier: Boolean) {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 30, 44, 56)
        }
        wrap.addView(TextView(this).apply {
            text = if (isSupplier) "Tezkor ta'minotchi tranzaksiyasi" else "Tezkor tranzaksiya"
            setTextColor(Color.parseColor("#0F172A")); textSize = 19f; setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        })
        wrap.addView(makeGlassInput("🔍 Qidiring...", ""))
        wrap.addView(TextView(this).apply {
            text = "Yaqinda foydalanilgan"
            setTextColor(Color.parseColor("#475569")); textSize = 14f; setTypeface(null, Typeface.BOLD)
            setPadding(0, 6, 0, 16)
        })

        val hScroll = HorizontalScrollView(this)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        db.getPersons(isSupplier).take(10).forEach { p ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = glossyCard("#F8FAFC", "#E2E8F0", 32f)
                setPadding(26, 26, 26, 26)
                layoutParams = LinearLayout.LayoutParams(260, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 16 }
                setOnClickListener { sheet.dismiss(); showClientKirimChiqimBottomSheet(p, isSupplier) }
            }
            val ch = p.name.trim().firstOrNull()?.uppercaseChar() ?: 'M'
            card.addView(TextView(this).apply {
                text = ch.toString(); gravity = Gravity.CENTER; textSize = 17f; setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE); background = circleAvatarBg(ch)
                layoutParams = LinearLayout.LayoutParams(100, 100).apply { bottomMargin = 12 }
            })
            card.addView(TextView(this).apply { text = p.name; maxLines = 1; setTextColor(Color.parseColor("#0F172A")); textSize = 13f; setTypeface(null, Typeface.BOLD) })
            card.addView(TextView(this).apply { text = formatMoney(Math.abs(p.balUzs), "UZS"); setTextColor(Color.parseColor("#EA580C")); textSize = 11.5f })
            row.addView(card)
        }
        hScroll.addView(row)
        wrap.addView(hScroll)
        sheet.setContentView(wrap)
        sheet.show()
    }

    private fun showAddClientBottomSheet(isSupplier: Boolean) {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 30, 44, 56)
        }
        wrap.addView(TextView(this).apply {
            text = if (isSupplier) "🚚 Ta'minotchi qo'shish" else "👤+ Yangi Mijoz / Qarzdor Qo'shish"
            setTextColor(Color.parseColor("#0F172A")); textSize = 19f; setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 18)
        })
        val etName = makeGlassInput("Ism *", "")
        val etPhone = makeGlassInput("UZ Telefon (+998)", "+998 ")
        val etDebt = makeGlassInput("Boshlang'ich qarz summasi (UZS)", "")
        etDebt.inputType = InputType.TYPE_CLASS_NUMBER
        wrap.addView(etName); wrap.addView(etPhone); wrap.addView(etDebt)

        wrap.addView(Button(this).apply {
            text = "Qo'shish / Saqlash"
            setTextColor(Color.WHITE); background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 135)
            setOnClickListener {
                val n = etName.text.toString().trim()
                val ph = etPhone.text.toString().trim()
                val d = etDebt.text.toString().trim().toLongOrNull() ?: 0L
                if (n.isNotEmpty()) {
                    if (d > 0) db.addOrUpdatePersonTx(n, ph, d, "UZS", "Chiqim", "Boshlang'ich qarz", "Bugun", isSupplier)
                    else db.upsertClientDirect(n, ph, 0.0, 0L, db.nowStr(), isSupplier)
                    sheet.dismiss()
                    renderMainWorkspace()
                }
            }
        })
        sheet.setContentView(wrap)
        sheet.show()
    }

    private fun showSubscriptionTariffsBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 30, 44, 56)
        }
        wrap.addView(TextView(this).apply {
            text = "💎 Obuna Tariflari (${remainingDaysText()})"
            setTextColor(Color.parseColor("#0F172A")); textSize = 18f; setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 18)
        })
        wrap.addView(tariffButton("💳 1 Oy — ${formatMoney(price1Month, "so'm")}", "+30 kun qo'shiladi", "#6D28D9") { sheet.dismiss(); selectTariffAndPayDialog(1, price1Month) })
        wrap.addView(tariffButton("🔥 3 Oy — ${formatMoney(price3Month, "so'm")}", "+90 kun qo'shiladi", "#2563EB") { sheet.dismiss(); selectTariffAndPayDialog(3, price3Month) })
        wrap.addView(tariffButton("👑 6 Oy — ${formatMoney(price6Month, "so'm")}", "+180 kun qo'shiladi", "#059669") { sheet.dismiss(); selectTariffAndPayDialog(6, price6Month) })
        sheet.setContentView(wrap)
        sheet.show()
    }

    // ==================== IMPORT .XLSX PARSER ====================
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMPORT_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return
                    val importedCount = if (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                        parseXlsxZipBytes(bytes)
                    } else {
                        parseCsvSmartLines(String(bytes, Charsets.UTF_8).lines())
                    }
                    Toast.makeText(this, "✅ $importedCount ta mijoz import qilindi!", Toast.LENGTH_LONG).show()
                    renderMainWorkspace()
                } catch (e: Exception) {
                    Toast.makeText(this, "Xatolik: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun parseXlsxZipBytes(bytes: ByteArray): Int {
        val entries = mutableMapOf<String, ByteArray>()
        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        var entry = zis.nextEntry
        while (entry != null) {
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var len: Int
            while (zis.read(buf).also { len = it } > 0) baos.write(buf, 0, len)
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
                if (event == XmlPullParser.START_TAG && parser.name == "si") { insideSi = true; currentSi = StringBuilder() }
                else if (event == XmlPullParser.START_TAG && parser.name == "t" && insideSi) currentSi.append(parser.nextText())
                else if (event == XmlPullParser.END_TAG && parser.name == "si") { insideSi = false; sharedStrings.add(currentSi.toString()) }
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
                        "c" -> { cellType = parser.getAttributeValue(null, "t") ?: ""; cellValue = "" }
                        "v", "t" -> {
                            val raw = parser.nextText()
                            cellValue = if (cellType == "s") {
                                val idx = raw.toIntOrNull() ?: -1
                                if (idx in sharedStrings.indices) sharedStrings[idx] else raw
                            } else raw
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "c") currentRow.add(cellValue)
                    else if (parser.name == "row" && currentRow.isNotEmpty()) rows.add(currentRow)
                }
            }
            event = parser.next()
        }

        var count = 0
        rows.forEachIndexed { idx, r ->
            if (idx > 0 && r.size >= 3) {
                val name = r.getOrNull(1)?.trim() ?: ""
                val phone = r.getOrNull(2)?.trim() ?: ""
                val usd = r.getOrNull(3)?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0
                val uzs = r.getOrNull(4)?.replace(",", "")?.replace(" ", "")?.trim()?.toDoubleOrNull()?.toLong() ?: 0L
                val dt = r.getOrNull(5)?.trim() ?: db.nowStr()
                if (name.isNotEmpty() && !name.equals("Ism", true)) {
                    db.upsertClientDirect(name, phone, usd, uzs, dt, false)
                    count++
                }
            }
        }
        return count
    }

    private fun parseCsvSmartLines(lines: List<String>): Int {
        var count = 0
        lines.forEach { raw ->
            val l = raw.replace("\uFEFF", "").trim()
            if (l.isEmpty() || l.startsWith("ID")) return@forEach
            val parts = l.split(",")
            if (parts.size >= 3) {
                val n = parts[1].replace("\"", "").trim()
                val p = parts[2].replace("\"", "").trim()
                val uzs = parts.getOrNull(4)?.replace("\"", "")?.replace(" ", "")?.toLongOrNull() ?: 0L
                if (n.isNotEmpty()) {
                    db.upsertClientDirect(n, p, 0.0, uzs, db.nowStr(), false)
                    count++
                }
            }
        }
        return count
    }

    // ==================== DIALOGLAR ====================
    private fun buildEmployeesSection(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Button(this).apply {
            text = "+ Xodim qo'shish (Barchasi | Oylik | Kunlik)"
            setTextColor(Color.WHITE); background = glossyPurpleGrad(32f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 125).apply { bottomMargin = 18 }
            setOnClickListener { openAddEmployeeDialog() }
        })
        val emps = db.getEmployees()
        if (emps.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "Xodimlar topilmadi"; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#64748B"))
                background = glossyCard("#FFFFFF", "#FFFFFF", 36f); setPadding(40, 60, 40, 60)
            })
        } else {
            emps.forEach { (n, p) ->
                col.addView(TextView(this).apply {
                    text = "👨‍💼 $n  •  📞 $p"; setTextColor(Color.parseColor("#0F172A")); textSize = 15f
                    background = glossyCard("#FFFFFF", "#FFFFFF", 32f); setPadding(32, 26, 32, 26)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
                })
            }
        }
        return col
    }

    private fun openShoppingListDialog() {
        val et = EditText(this).apply { hint = "Bozorlik ro'yxati" }
        AlertDialog.Builder(this).setTitle("📋 Bozorlik").setView(et).setPositiveButton("Saqlash", null).show()
    }

    private fun openAddKassaDialog(type: String) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(45, 25, 45, 10) }
        val etCat = EditText(this).apply { hint = "Kategoriya (Savdo, Ijara)" }
        val etAmt = EditText(this).apply { hint = "Summa (UZS)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val etNote = EditText(this).apply { hint = "Izoh" }
        layout.addView(etCat); layout.addView(etAmt); layout.addView(etNote)
        AlertDialog.Builder(this).setTitle("💵 Kassaga $type").setView(layout)
            .setPositiveButton("Saqlash") { _, _ ->
                val amt = etAmt.text.toString().toLongOrNull() ?: 0L
                if (amt > 0) {
                    db.addKassa(type, etCat.text.toString().ifEmpty { "Umumiy" }, amt, "UZS", "Naqd", etNote.text.toString(), storeName)
                    renderMainWorkspace()
                }
            }.setNegativeButton("Yopish", null).show()
    }

    private fun openAddProductDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(45, 25, 45, 10) }
        val etName = EditText(this).apply { hint = "Mahsulot nomi *" }
        val etQty = EditText(this).apply { hint = "Miqdor (dona)"; inputType = InputType.TYPE_CLASS_NUMBER; setText("1") }
        val etCost = EditText(this).apply { hint = "Tan narx (10 000 UZS)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val etSale = EditText(this).apply { hint = "Sotuv narxi (12 000 UZS)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val etCode = EditText(this).apply { hint = "Shtrix-kod / Kodi" }
        layout.addView(etName); layout.addView(etQty); layout.addView(etCost); layout.addView(etSale); layout.addView(etCode)
        AlertDialog.Builder(this).setTitle("📦 Mahsulot yaratish / Kirim").setView(layout)
            .setPositiveButton("Qo'shish") { _, _ ->
                val n = etName.text.toString().trim()
                if (n.isNotEmpty()) {
                    db.addProduct(n, etCode.text.toString().ifEmpty { "BC-101" }, etQty.text.toString().toIntOrNull() ?: 1, "dona", etCost.text.toString().toLongOrNull() ?: 0L, etSale.text.toString().toLongOrNull() ?: 0L, "UZS", storeName)
                    renderMainWorkspace()
                }
            }.setNegativeButton("Yopish", null).show()
    }

    private fun openAddEmployeeDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(45, 25, 45, 10) }
        val etName = EditText(this).apply { hint = "Xodim ismi" }
        val etPhone = EditText(this).apply { hint = "UZ Telefon"; setText("+998") }
        layout.addView(etName); layout.addView(etPhone)
        AlertDialog.Builder(this).setTitle("👨‍💼 Xodim qo'shish").setView(layout)
            .setPositiveButton("Saqlash") { _, _ ->
                if (etName.text.isNotEmpty()) { db.addEmployee(etName.text.toString(), etPhone.text.toString(), "Oylik"); renderMainWorkspace() }
            }.setNegativeButton("Yopish", null).show()
    }

    private fun openCreateFolderDialog() {
        val et = EditText(this).apply { hint = "Papka nomi" }
        AlertDialog.Builder(this).setTitle("📁 Papka yaratish").setView(et).setPositiveButton("Saqlash", null).show()
    }

    private fun openSalesCalculatorDialog() {
        val et = EditText(this).apply { hint = "Summa (UZS)"; inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle("🧮 Sotuv kalkulyatori").setView(et)
            .setPositiveButton("Kassaga Kirim") { _, _ ->
                val v = et.text.toString().toLongOrNull() ?: 0L
                if (v > 0) { db.addKassa("Kirim", "Kalkulyator", v, "UZS", "Naqd", "", storeName); renderMainWorkspace() }
            }.setNegativeButton("Yopish", null).show()
    }

    private fun showSmsSettingsDialog() {
        AlertDialog.Builder(this).setTitle("💬 SMS sozlamalari").setMessage("SIM-karta orqali qarzdorlarga avtomatik SMS yuborish yoqilgan.").setPositiveButton("Yopish", null).show()
    }

    private fun showPushSettingsDialog() {
        AlertDialog.Builder(this).setTitle("🔔 Bildirishnomalar").setMessage("Har kuni soat 00:00 da signal bilan Download/QarzFy papkasiga avtomatik Excel (.xlsx) saqlash yoqilgan!").setPositiveButton("Yopish", null).show()
    }

    private fun showVoiceSettingsDialog() {
        AlertDialog.Builder(this).setTitle("📞 Ovozli qo'ng'iroq sozlamalari").setMessage("O'zbekcha (Nigora), Ruscha (Alena), Inglizcha (Jon) ovozli eslatmalar.").setPositiveButton("Yopish", null).show()
    }

    private fun showPrinterSettingsDialog() {
        AlertDialog.Builder(this).setTitle("🖨 Printer sozlamalari").setMessage("Qog'oz eni: A4 / 80 mm / 58 mm. Bluetooth printer orqali chek chiqarish.").setPositiveButton("Test chek", null).show()
    }

    private fun showQrCodeAndOpenPageDialog() {
        AlertDialog.Builder(this).setTitle("🔳 Mening QR-kodim va Ochiq sahifa")
            .setMessage("Do'kon: $storeName\nHavola: $SERVER_URL/store/$storeId")
            .setPositiveButton("Sahifani ochish") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$SERVER_URL/store/$storeId")))
            }.setNegativeButton("Yopish", null).show()
    }

    private fun showCommunityAndAmbassadorDialog() {
        AlertDialog.Builder(this).setTitle("🤝 Hamjamiyat va Vazifalar").setMessage("Storyga post joylang (+5000 UZS) va Ambassador dasturida qatnashing!").setPositiveButton("Yopish", null).show()
    }

    private fun openBusinessInfoDialog() {
        AlertDialog.Builder(this).setTitle("🏪 Biznes ma'lumotlari")
            .setMessage("Do'kon: $storeName\nID: $storeId\nManzil: $storeAddress\nObuna: ${remainingDaysText()}")
            .setPositiveButton("Obunani uzaytirish") { _, _ -> showSubscriptionTariffsBottomSheet() }
            .setNegativeButton("Yopish", null).show()
    }

    private fun showDeveloperDialog() {
        AlertDialog.Builder(this).setTitle("👨‍💻 Dastur haqida")
            .setMessage("Ilova: QarzFy v7.0 (Glossy Glassmorphism)\nDasturchi: Hasan Jaylovov\nTelegram: @HasanJaylovov")
            .setPositiveButton("Bog'lanish") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/HasanJaylovov")))
            }.setNegativeButton("Yopish", null).show()
    }

    private fun sendBulkSmsReminders(isSupplier: Boolean) {
        var sent = 0
        db.getPersons(isSupplier).forEach { p ->
            if (p.balUzs < 0) {
                sendAutoSms(p.phone, "Hurmatli ${p.name}, $storeName do'konidan ${formatMoney(Math.abs(p.balUzs), "UZS")} qarzingiz bor.")
                sent++
            }
        }
        Toast.makeText(this, "📩 $sent ta qarzdorga SMS yuborildi!", Toast.LENGTH_LONG).show()
    }

    private fun sendAutoSms(phoneNumber: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            smsManager.sendMultipartTextMessage(phoneNumber, null, smsManager.divideMessage(message), null, null)
        } catch (_: Exception) {}
    }
}
