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
import android.os.StrictMode
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.*
import org.json.JSONArray
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

data class ChatMsg(
    val sender: String,
    val message: String,
    val time: String
)

class QarzFyDatabase(private val ctx: Context) : SQLiteOpenHelper(ctx, "qarzfy_master_permanent.db", null, 4) {
    var onDataChangedCallback: (() -> Unit)? = null

    override fun onCreate(db: SQLiteDatabase) {
        createTables(db)
    }

    private fun createTables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS users (
                phone TEXT PRIMARY KEY,
                password TEXT,
                store_id TEXT,
                store_name TEXT,
                store_address TEXT,
                store_category TEXT,
                currency TEXT,
                sub_expiry INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS clients (
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
            CREATE TABLE IF NOT EXISTS transactions (
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
            CREATE TABLE IF NOT EXISTS products (
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
            CREATE TABLE IF NOT EXISTS kassa (
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
            CREATE TABLE IF NOT EXISTS employees (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                phone TEXT,
                role TEXT,
                salary_type TEXT
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        createTables(db)
    }

    fun nowStr(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    fun saveAccount(phone: String, pass: String, sId: String, sName: String, sAddr: String, sCat: String, cur: String, exp: Long) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO users (phone, password, store_id, store_name, store_address, store_category, currency, sub_expiry) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(phone, pass, sId, sName, sAddr, sCat, cur, exp)
        )
    }

    fun findAccount(phone: String): Map<String, String>? {
        val clean = phone.replace(" ", "").trim()
        val c = readableDatabase.rawQuery("SELECT phone, password, store_id, store_name, store_address, store_category, currency, sub_expiry FROM users WHERE REPLACE(phone, ' ', '') = ? OR phone LIKE ?", arrayOf(clean, "%$clean%"))
        var res: Map<String, String>? = null
        if (c.moveToFirst()) {
            res = mapOf(
                "phone" to (c.getString(0) ?: ""),
                "password" to (c.getString(1) ?: ""),
                "store_id" to (c.getString(2) ?: "QF-101"),
                "store_name" to (c.getString(3) ?: "Do'kon"),
                "store_address" to (c.getString(4) ?: "O'zbekiston"),
                "store_category" to (c.getString(5) ?: "Umumiy"),
                "currency" to (c.getString(6) ?: "UZS"),
                "sub_expiry" to c.getLong(7).toString()
            )
        }
        c.close()
        return res
    }

    fun getLastSavedAccount(): Map<String, String>? {
        val c = readableDatabase.rawQuery("SELECT phone, password, store_id, store_name, store_address, store_category, currency, sub_expiry FROM users LIMIT 1", null)
        var res: Map<String, String>? = null
        if (c.moveToFirst()) {
            res = mapOf(
                "phone" to (c.getString(0) ?: ""),
                "password" to (c.getString(1) ?: ""),
                "store_id" to (c.getString(2) ?: "QF-101"),
                "store_name" to (c.getString(3) ?: "Do'kon"),
                "store_address" to (c.getString(4) ?: "O'zbekiston"),
                "store_category" to (c.getString(5) ?: "Umumiy"),
                "currency" to (c.getString(6) ?: "UZS"),
                "sub_expiry" to c.getLong(7).toString()
            )
        }
        c.close()
        return res
    }

    fun upsertClientDirect(name: String, phone: String, balUsd: Double, balUzs: Long, createdAt: String, isSupplier: Boolean, triggerAutoBackup: Boolean = true) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        val c = db.rawQuery("SELECT id FROM clients WHERE name = ? AND is_supplier = ?", arrayOf(cleanName, supFlag.toString()))
        if (c.moveToFirst()) {
            val id = c.getInt(0)
            db.execSQL("UPDATE clients SET phone = ?, bal_usd = ?, bal_uzs = ? WHERE id = ?", arrayOf<Any>(phone.trim(), balUsd, balUzs, id))
        } else {
            db.execSQL(
                "INSERT INTO clients (name, phone, bal_usd, bal_uzs, folder, due_date, created_at, is_supplier) VALUES (?, ?, ?, ?, 'Barchasi', 'Bugun', ?, ?)",
                arrayOf<Any>(cleanName, phone.trim(), balUsd, balUzs, createdAt.ifEmpty { nowStr() }, supFlag)
            )
        }
        c.close()
        if (triggerAutoBackup) onDataChangedCallback?.invoke()
    }

    fun insertTxDirect(txId: String, kind: String, personName: String, personPhone: String, amount: Long, currency: String, date: String, note: String, status: String, isSupplier: Boolean) {
        val supFlag = if (isSupplier) 1 else 0
        writableDatabase.execSQL(
            "INSERT INTO transactions (tx_id, kind, person_name, person_phone, amount, currency, date, note, status, is_supplier) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(txId, kind, personName, personPhone, Math.abs(amount), currency, date.ifEmpty { nowStr() }, note, status.ifEmpty { "Faol" }, supFlag)
        )
    }

    fun addOrUpdatePersonTx(name: String, phone: String, amount: Long, currency: String, kind: String, note: String, dueDate: String, isSupplier: Boolean, folder: String = "Barchasi") {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        val c = db.rawQuery("SELECT id, bal_usd, bal_uzs FROM clients WHERE name = ? AND is_supplier = ?", arrayOf(cleanName, supFlag.toString()))
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
                arrayOf<Any>(cleanName, phone.trim(), initUsd, initUzs, folder, dueDate, nowStr(), supFlag)
            )
        }
        c.close()

        if (amount > 0) {
            val prefix = if (isSupplier) "ST-" else "D-"
            val txId = prefix + (100000 + Random().nextInt(900000))
            insertTxDirect(txId, kind, cleanName, phone.trim(), amount, currency, nowStr(), note, "Faol", isSupplier)
        }
        // HAR BIR KIRIM-CHIQIM YOZILGANDA AVTOMATIK DOWNLOAD/QarzFy GA SAQLANADI!
        onDataChangedCallback?.invoke()
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
        val qTrim = query.trim()
        val c = if (qTrim.isEmpty()) {
            readableDatabase.rawQuery("SELECT id, name, phone, bal_usd, bal_uzs, folder, due_date, created_at FROM clients WHERE is_supplier = ? ORDER BY $orderBy", arrayOf(supFlag.toString()))
        } else {
            readableDatabase.rawQuery("SELECT id, name, phone, bal_usd, bal_uzs, folder, due_date, created_at FROM clients WHERE is_supplier = ? AND (LOWER(name) LIKE LOWER(?) OR REPLACE(phone, ' ', '') LIKE ?) ORDER BY $orderBy", arrayOf(supFlag.toString(), "%$qTrim%", "%${qTrim.replace(" ", "")}%"))
        }
        while (c.moveToNext()) {
            list.add(ClientRec(c.getInt(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getDouble(3), c.getLong(4), c.getString(5) ?: "Barchasi", c.getString(6) ?: "", c.getString(7) ?: ""))
        }
        c.close()
        return list
    }

    fun getTransactions(isSupplier: Boolean): List<TxRec> {
        val list = mutableListOf<TxRec>()
        val supFlag = if (isSupplier) 1 else 0
        val c = readableDatabase.rawQuery("SELECT tx_id, kind, person_name, person_phone, amount, currency, date, note, status FROM transactions WHERE is_supplier = ? ORDER BY id DESC", arrayOf(supFlag.toString()))
        while (c.moveToNext()) {
            list.add(TxRec(c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "", c.getLong(4), c.getString(5) ?: "UZS", c.getString(6) ?: "", c.getString(7) ?: "", c.getString(8) ?: "Faol", isSupplier))
        }
        c.close()
        return list
    }

    fun addProduct(name: String, barcode: String, qty: Int, unit: String, cost: Long, sale: Long, currency: String, supplier: String) {
        writableDatabase.execSQL(
            "INSERT INTO products (name, barcode, qty, unit, cost_price, sale_price, currency, supplier) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(name.trim(), barcode.trim(), qty, unit, cost, sale, currency, supplier)
        )
        onDataChangedCallback?.invoke()
    }

    fun getProducts(query: String = ""): List<ProductRec> {
        val list = mutableListOf<ProductRec>()
        val qTrim = query.trim()
        val c = if (qTrim.isEmpty()) {
            readableDatabase.rawQuery("SELECT id, name, barcode, qty, unit, cost_price, sale_price, currency, supplier FROM products ORDER BY id DESC", null)
        } else {
            readableDatabase.rawQuery("SELECT id, name, barcode, qty, unit, cost_price, sale_price, currency, supplier FROM products WHERE LOWER(name) LIKE LOWER(?) OR LOWER(barcode) LIKE LOWER(?) ORDER BY id DESC", arrayOf("%$qTrim%", "%$qTrim%"))
        }
        while (c.moveToNext()) {
            list.add(ProductRec(c.getInt(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3), c.getString(4) ?: "dona", c.getLong(5), c.getLong(6), c.getString(7) ?: "UZS", c.getString(8) ?: ""))
        }
        c.close()
        return list
    }

    fun addKassa(type: String, category: String, amount: Long, currency: String, payType: String, note: String, creator: String) {
        writableDatabase.execSQL(
            "INSERT INTO kassa (type, category, amount, currency, pay_type, date, note, creator) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(type, category, amount, currency, payType, nowStr(), note, creator)
        )
        onDataChangedCallback?.invoke()
    }

    fun getKassaList(): List<KassaRec> {
        val list = mutableListOf<KassaRec>()
        val c = readableDatabase.rawQuery("SELECT id, type, category, amount, currency, pay_type, date, note, creator FROM kassa ORDER BY id DESC", null)
        while (c.moveToNext()) {
            list.add(KassaRec(c.getInt(0), c.getString(1) ?: "Kirim", c.getString(2) ?: "Umumiy", c.getLong(3), c.getString(4) ?: "UZS", c.getString(5) ?: "Naqd", c.getString(6) ?: "", c.getString(7) ?: "", c.getString(8) ?: ""))
        }
        c.close()
        return list
    }

    fun addEmployee(name: String, phone: String, salaryType: String) {
        writableDatabase.execSQL("INSERT INTO employees (name, phone, role, salary_type) VALUES (?, ?, 'Sotuvchi', ?)", arrayOf<Any>(name.trim(), phone.trim(), salaryType))
        onDataChangedCallback?.invoke()
    }

    fun getEmployees(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val c = readableDatabase.rawQuery("SELECT name, phone, salary_type FROM employees ORDER BY id DESC", null)
        while (c.moveToNext()) {
            list.add(Pair("${c.getString(0)} (${c.getString(2)})", c.getString(1) ?: ""))
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
    private var ttsEngine: TextToSpeech? = null

    private var isLoggedIn = false
    private var storeId = "QF-101"
    private var storeName = "Hasan Jaylovov"
    private var storeAddress = "O'zbekiston, Surxondaryo"
    private var storeCategory = "Qurilish mollari"
    private var mainCurrency = "UZS"
    private var userPhone = ""
    private var userPassword = ""
    private var subscriptionExpiryMillis = 0L
    private var isAutoVoiceCallEnabled = true

    private var price1Month = 49000L
    private var price3Month = 135000L
    private var price6Month = 255000L
    private var adminCardNumber = "9860 0000 0000 0000"
    private var adminCardOwner = "HASAN JAYLOVOV"

    private var currentBottomTab = 0
    private var currentHomeSubTab = 0
    private var currentSortMode = "Faol"
    private var searchQuery = ""
    private var productSearchQuery = ""

    private var kassaTypeTab = "Xarajatlar"
    private var kassaPayFilter = "Hammasi"
    private var kassaPeriodFilter = "Barchasi"

    private var mahsulotTopTab = "Ombor"
    private var mahsulotFilterTab = "Barchasi"

    private var statTopTab = "Mijozlar"
    private var statProdSubTab = "Qoldiq"

    private var isLeftEdgeOpen = false
    private var isRightEdgeOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())

        setContentView(R.layout.activity_main)

        val perms = mutableListOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)

        prefs = getSharedPreferences("QarzFyMasterPermanentPrefs", Context.MODE_PRIVATE)
        db = QarzFyDatabase(this)

        // HAR BIR O'ZGARISHDA AVTOMATIK DOWNLOAD/QarzFy GA SAQLASH CALLBACKI:
        db.onDataChangedCallback = {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    savePermanentBackupToDownloadsQarzFy()
                } catch (_: Exception) {}
            }
        }

        // Ovozli qo'ng'iroq/TTS dvigatelini ishga tushirish
        ttsEngine = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale("uz", "UZ")
            }
        }

        rootLayout = findViewById(R.id.mainContentLayout)

        loadPrefsAndRestoreSession()
        restoreFromPermanentDownloadsBackupIfEmpty()

        if (!isLoggedIn || userPhone.isBlank()) {
            renderOnboardingAndLogin()
        } else {
            checkServerSubscriptionAndRender()
            scheduleMidnightAutoExcelSaveWithSignal()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isLoggedIn && userPhone.isNotBlank()) {
            checkServerSubscriptionAndRender()
        }
    }

    override fun onDestroy() {
        ttsEngine?.shutdown()
        super.onDestroy()
    }

    private fun loadPrefsAndRestoreSession() {
        isLoggedIn = prefs.getBoolean("is_logged_in", false)
        userPhone = prefs.getString("user_phone", "") ?: ""
        userPassword = prefs.getString("user_password", "") ?: ""
        storeId = prefs.getString("store_id", "QF-101") ?: "QF-101"
        storeName = prefs.getString("store_name", "Hasan Jaylovov") ?: "Hasan Jaylovov"
        storeAddress = prefs.getString("store_address", "O'zbekiston") ?: "O'zbekiston"
        storeCategory = prefs.getString("store_category", "Qurilish mollari") ?: "Qurilish mollari"
        mainCurrency = prefs.getString("main_currency", "UZS") ?: "UZS"
        subscriptionExpiryMillis = prefs.getLong("sub_expiry_millis", 0L)
        isAutoVoiceCallEnabled = prefs.getBoolean("auto_voice_call", true)
        price1Month = prefs.getLong("price_1m", 49000L)
        price3Month = prefs.getLong("price_3m", 135000L)
        price6Month = prefs.getLong("price_6m", 255000L)
        adminCardNumber = prefs.getString("card_number", "9860 0000 0000 0000") ?: "9860 0000 0000 0000"
        adminCardOwner = prefs.getString("card_owner", "HASAN JAYLOVOV") ?: "HASAN JAYLOVOV"

        if (userPhone.isBlank()) {
            val savedAcc = db.getLastSavedAccount()
            if (savedAcc != null) {
                userPhone = savedAcc["phone"] ?: ""
                userPassword = savedAcc["password"] ?: ""
                storeId = savedAcc["store_id"] ?: "QF-101"
                storeName = savedAcc["store_name"] ?: "Hasan Jaylovov"
                storeAddress = savedAcc["store_address"] ?: "O'zbekiston"
                storeCategory = savedAcc["store_category"] ?: "Umumiy"
                mainCurrency = savedAcc["currency"] ?: "UZS"
                subscriptionExpiryMillis = savedAcc["sub_expiry"]?.toLongOrNull() ?: (System.currentTimeMillis() + 864000000L)
            }
        }
    }

    // Agar ilova o'chirilib qayta o'rnatilgan bo'lsa, Download/QarzFy/QarzFy_AvtoBazasi.xlsx dan avtomatik tiklaydi!
    private fun restoreFromPermanentDownloadsBackupIfEmpty() {
        if (db.getPersons(false).isNotEmpty()) return
        try {
            val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "QarzFy")
            val backupFile = File(pubDir, "QarzFy_AvtoBazasi.xlsx")
            if (backupFile.exists() && backupFile.length() > 100) {
                parseXlsxZipBytes(backupFile.readBytes(), triggerBackup = false)
            }
        } catch (_: Exception) {}
    }

    private fun saveSessionSynchronously(loggedIn: Boolean) {
        isLoggedIn = loggedIn
        prefs.edit()
            .putBoolean("is_logged_in", loggedIn)
            .putString("user_phone", userPhone)
            .putString("user_password", userPassword)
            .putString("store_id", storeId)
            .putString("store_name", storeName)
            .putString("store_address", storeAddress)
            .putString("store_category", storeCategory)
            .putString("main_currency", mainCurrency)
            .putLong("sub_expiry_millis", subscriptionExpiryMillis)
            .putBoolean("auto_voice_call", isAutoVoiceCallEnabled)
            .commit()

        if (userPhone.isNotBlank()) {
            db.saveAccount(userPhone, userPassword, storeId, storeName, storeAddress, storeCategory, mainCurrency, subscriptionExpiryMillis)
        }
    }

    private fun performLogout() {
        AlertDialog.Builder(this)
            .setTitle("🚪 Hisobdan chiqish")
            .setMessage("Haqiqatan ham hisobingizdan chiqmoqchimisiz?\n\nBarcha ma'lumotlaringiz Download/QarzFy papkasida va dastur xotirasida 100% saqlanib qoladi!")
            .setPositiveButton("Chiqish") { _, _ ->
                prefs.edit().putBoolean("is_logged_in", false).commit()
                isLoggedIn = false
                isLeftEdgeOpen = false
                isRightEdgeOpen = false
                Toast.makeText(this, "Hisobdan chiqildi!", Toast.LENGTH_SHORT).show()
                renderOnboardingAndLogin()
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
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

    private fun glossyRedGrad(rad: Float = 38f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#EF4444"), Color.parseColor("#DC2626"))
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

    private fun cleanNumberInput(s: String): Long {
        val digitsOnly = s.replace(",", "").replace(" ", "").replace(".", "").trim()
        return digitsOnly.toLongOrNull() ?: 0L
    }

    private fun remainingDaysText(): String {
        val diff = subscriptionExpiryMillis - System.currentTimeMillis()
        if (diff <= 0) return "Obuna tugagan"
        val days = (diff / (1000L * 60 * 60 * 24)) + 1
        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(subscriptionExpiryMillis))
        return "$days kun qoldi ($dateStr gacha)"
    }

    // ==================== FONDA KO'RINMAS AVTO-QO'NG'IROQ (BOT VA OVOZLI ESLATMA) VA SIM AVTO-SMS ====================
    private fun triggerBackgroundSmsAndVoiceCall(clientName: String, phoneNumber: String, amount: Long, kind: String, note: String) {
        val fmtAmt = formatMoney(amount, "so'm")
        val smsText = if (kind == "Chiqim") {
            "Hurmatli $clientName, $storeName do'konida sizga $fmtAmt qarz yozildi ($note)."
        } else {
            "Hurmatli $clientName, $storeName do'konidagi qarzingizdan $fmtAmt to'lov qabul qilindi. Rahmat!"
        }

        // 1) Fonda SIM orqali Avto-SMS (Ekranda hech narsa ko'rinmaydi)
        sendAutoSms(phoneNumber, smsText)

        // 2) Fonda Ko'rinmas Avto-Ovozli Qo'ng'iroq / Bot Voice Eslatma (Telefonda qo'ng'iroq oynasi chiqmaydi va bilinmaydi!)
        if (isAutoVoiceCallEnabled) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = URL("$SERVER_URL/api/auto_voice_call")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    val body = JSONObject().apply {
                        put("store_id", storeId)
                        put("store_name", storeName)
                        put("client_name", clientName)
                        put("client_phone", phoneNumber)
                        put("amount", fmtAmt)
                        put("kind", kind)
                        put("note", note)
                        put("voice_text", smsText)
                    }
                    conn.outputStream.write(body.toString().toByteArray())
                    conn.inputStream.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun sendAutoSms(phoneNumber: String, message: String) {
        if (phoneNumber.length < 9) return
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            smsManager.sendMultipartTextMessage(phoneNumber, null, smsManager.divideMessage(message), null, null)
        } catch (_: Exception) {}
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

                if (serverExpiry > 0L) {
                    subscriptionExpiryMillis = serverExpiry
                    editor.putLong("sub_expiry_millis", subscriptionExpiryMillis)
                }
                editor.commit()

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

    private fun openTelegramBotDirectly(startParam: String) {
        try {
            val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$BOT_USERNAME&start=$startParam"))
            startActivity(tgIntent)
        } catch (_: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$BOT_USERNAME?start=$startParam"))
                startActivity(webIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Telegram topilmadi: @$BOT_USERNAME", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun selectTariffAndPayDialog(months: Int, priceUzs: Long) {
        val fmtPrice = formatMoney(priceUzs, "so'm")
        val msg = """
            🏪 Do'kon ID: $storeId ($storeName)
            📅 Tanlangan tarif: $months Oylik obuna
            💰 To'lov summasi: $fmtPrice
            
            💳 Karta raqami (Nusxalab oling):
            $adminCardNumber
            👤 Karta egasi: $adminCardOwner
            
            📸 MUHIM QOIDA:
            1. Yuqoridagi kartaga $fmtPrice o'tkazing va to'lov chekini skrinshot (screenshot) qiling.
            2. Pastdagi "🤖 Botga o'tish va Chek yuborish" tugmasini bosing.
            3. Botga skrinshotni yuboring — Admin tasdiqlashi bilan obuna vaqtingiz avtomatik qo'shiladi!
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("💳 $months Oylik Obuna — $fmtPrice")
            .setMessage(msg)
            .setPositiveButton("🤖 Botga o'tish va Chek yuborish") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("card", adminCardNumber))
                Toast.makeText(this, "📋 Karta nusxalandi! Botga to'lov skrinshotini yuboring.", Toast.LENGTH_LONG).show()
                openTelegramBotDirectly("${storeId}_${months}oy")
            }
            .setNeutralButton("🔄 Tasdiqlanganini tekshirish") { _, _ ->
                Toast.makeText(this, "⏳ Serverdan tekshirilmoqda...", Toast.LENGTH_SHORT).show()
                checkServerSubscriptionAndRender()
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    // ==================== ADMIN BILAN JONLI SUHBAT (SUPPORT CHAT) ====================
    private fun showSupportChatBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 54f)
            setPadding(40, 30, 40, 46)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        topBar.addView(TextView(this).apply {
            text = "💬 Admin bilan suhbat (Hasan Jaylovov)"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topBar.addView(TextView(this).apply {
            text = "🤖 Botda"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(24f)
            setPadding(24, 14, 24, 14)
            setOnClickListener { openTelegramBotDirectly("${storeId}_chat") }
        })
        wrap.addView(topBar)

        val chatScroll = ScrollView(this).apply {
            background = glossyCard("#F8FAFC", "#E2E8F0", 32f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 550).apply { bottomMargin = 16 }
        }
        val msgContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        chatScroll.addView(msgContainer)
        wrap.addView(chatScroll)

        fun loadMessagesFromServer() {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = URL("$SERVER_URL/api/chat/messages/$storeId")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000
                    val txt = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(txt)
                    val list = mutableListOf<ChatMsg>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(ChatMsg(o.optString("sender", "user"), o.optString("message", ""), o.optString("time", "")))
                    }
                    withContext(Dispatchers.Main) {
                        msgContainer.removeAllViews()
                        if (list.isEmpty()) {
                            msgContainer.addView(TextView(this@MainActivity).apply {
                                text = "👋 Assalomu alaykum! Savolingiz bo'lsa pastga yozib yuboring."
                                setTextColor(Color.parseColor("#475569"))
                                textSize = 13f
                            })
                        } else {
                            list.forEach { m ->
                                val isUser = (m.sender == "user")
                                val bubble = LinearLayout(this@MainActivity).apply {
                                    orientation = LinearLayout.VERTICAL
                                    background = if (isUser) glossyPurpleGrad(28f) else glossyCard("#FFFFFF", "#CBD5E1", 28f)
                                    setPadding(28, 18, 28, 18)
                                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                        gravity = if (isUser) Gravity.END else Gravity.START
                                        bottomMargin = 12
                                    }
                                }
                                bubble.addView(TextView(this@MainActivity).apply {
                                    text = if (isUser) "Siz: ${m.message}" else "👑 Admin: ${m.message}"
                                    setTextColor(if (isUser) Color.WHITE else Color.parseColor("#0F172A"))
                                    textSize = 13.5f
                                })
                                bubble.addView(TextView(this@MainActivity).apply {
                                    text = m.time
                                    setTextColor(if (isUser) Color.parseColor("#E0E7FF") else Color.parseColor("#94A3B8"))
                                    textSize = 10f
                                    gravity = Gravity.END
                                })
                                msgContainer.addView(bubble)
                            }
                            chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        loadMessagesFromServer()

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val etMsg = EditText(this).apply {
            hint = "Xabar yozing..."
            setTextColor(Color.parseColor("#0F172A"))
            background = glossyCard("#F8FAFC", "#CBD5E1", 30f)
            setPadding(30, 22, 30, 22)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 12 }
        }
        val btnSend = Button(this).apply {
            text = "➤"
            setTextColor(Color.WHITE)
            textSize = 16f
            background = glossyPurpleGrad(30f)
            layoutParams = LinearLayout.LayoutParams(125, 115)
            setOnClickListener {
                val txt = etMsg.text.toString().trim()
                if (txt.isEmpty()) return@setOnClickListener
                etMsg.setText("")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val url = URL("$SERVER_URL/api/chat/send")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json")
                        val body = JSONObject().apply {
                            put("store_id", storeId)
                            put("store_name", storeName)
                            put("phone", userPhone)
                            put("sender", "user")
                            put("message", txt)
                        }
                        conn.outputStream.write(body.toString().toByteArray())
                        conn.inputStream.close()
                        loadMessagesFromServer()
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            openTelegramBotDirectly("${storeId}_chat")
                        }
                    }
                }
            }
        }
        inputRow.addView(etMsg)
        inputRow.addView(btnSend)
        wrap.addView(inputRow)

        sheet.setContentView(wrap)
        sheet.show()
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
            text = "Do'kon ID: $storeId ($storeName)\n💳 Karta: $adminCardNumber ($adminCardOwner)\n\nTarifni tanlab, Botga to'lov cheki (skrinshot) yuboring:"
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(10, 14, 10, 30)
        })

        card.addView(tariffButton("💳 1 Oy — ${formatMoney(price1Month, "so'm")}", "Botga chek (skrinshot) yuborish", "#6D28D9") {
            selectTariffAndPayDialog(1, price1Month)
        })
        card.addView(tariffButton("🔥 3 Oy — ${formatMoney(price3Month, "so'm")}", "Botga chek (skrinshot) yuborish", "#2563EB") {
            selectTariffAndPayDialog(3, price3Month)
        })
        card.addView(tariffButton("👑 6 Oy — ${formatMoney(price6Month, "so'm")}", "Botga chek (skrinshot) yuborish", "#059669") {
            selectTariffAndPayDialog(6, price6Month)
        })
        card.addView(Button(this).apply {
            text = "💬 Admin bilan suhbat / Yordam"
            setTextColor(Color.WHITE)
            background = glossyOrangeGrad(32f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { topMargin = 8; bottomMargin = 8 }
            setOnClickListener { showSupportChatBottomSheet() }
        })
        card.addView(Button(this).apply {
            text = "🔄 To'lov tasdiqlanganini tekshirish"
            setTextColor(Color.parseColor("#1E293B"))
            background = glossyCard("#F1F5F9", "#CBD5E1", 32f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { bottomMargin = 8 }
            setOnClickListener { checkServerSubscriptionAndRender() }
        })
        card.addView(Button(this).apply {
            text = "🚪 Hisobdan chiqish"
            setTextColor(Color.WHITE)
            background = glossyRedGrad(32f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120)
            setOnClickListener { performLogout() }
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

    // ==================== 1. KIRISH VA RO'YXATDAN O'TISH EKRANI ====================
    private fun renderOnboardingAndLogin() {
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#EEF2FF"))
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 50, 44, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glossyPurpleGrad(54f)
            setPadding(44, 44, 44, 44)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
        }
        heroCard.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(170, 170).apply { bottomMargin = 14 }
        })
        heroCard.addView(TextView(this).apply {
            text = "QarzFy Tizimiga Xush Kelibsiz!"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        heroCard.addView(TextView(this).apply {
            text = "Barcha mijozlar va hisoblar Download/QarzFy papkasida doimiy saqlanadi."
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 12.5f
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

        val savedAcc = db.getLastSavedAccount()
        val initPhone = if (userPhone.isNotBlank()) userPhone else (savedAcc?.get("phone") ?: "+998")
        val initStore = if (storeName.isNotBlank()) storeName else (savedAcc?.get("store_name") ?: "Hasan Jaylovov")
        val initAddr = if (storeAddress.isNotBlank()) storeAddress else (savedAcc?.get("store_address") ?: "O'zbekiston, Surxondaryo")

        val etPhone = makeGlassInput("Telefon raqam (+998909958297) *", initPhone)
        etPhone.inputType = InputType.TYPE_CLASS_PHONE
        val etPass = makeGlassInput("Parol *", userPassword)
        etPass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val etStore = makeGlassInput("Do'kon nomi", initStore)
        val etAddr = makeGlassInput("Manzil", initAddr)

        formCard.addView(TextView(this).apply {
            text = "🔑 Telefon va Parol:"
            setTextColor(Color.parseColor("#1E293B"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(4, 0, 4, 12)
        })
        formCard.addView(etPhone)
        formCard.addView(etPass)

        val btnLoginExisting = Button(this).apply {
            text = "🔑 Mavjud Hisobga Kirish"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(36f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130).apply { topMargin = 8; bottomMargin = 20 }
            setOnClickListener {
                val ph = etPhone.text.toString().trim()
                val pw = etPass.text.toString().trim()
                if (ph.length < 9 || pw.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Telefon raqam va parolni kiriting!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val acc = db.findAccount(ph)
                userPhone = ph
                userPassword = pw
                if (acc != null) {
                    storeId = acc["store_id"] ?: ("QF-" + Math.abs(ph.hashCode() % 90000 + 10000))
                    storeName = acc["store_name"] ?: etStore.text.toString().trim().ifEmpty { "Do'kon" }
                    storeAddress = acc["store_address"] ?: etAddr.text.toString().trim()
                    storeCategory = acc["store_category"] ?: "Qurilish mollari"
                    val savedExp = acc["sub_expiry"]?.toLongOrNull() ?: 0L
                    if (savedExp > subscriptionExpiryMillis) subscriptionExpiryMillis = savedExp
                } else {
                    storeName = etStore.text.toString().trim().ifEmpty { "Hasan Jaylovov" }
                    storeAddress = etAddr.text.toString().trim().ifEmpty { "O'zbekiston" }
                    storeId = "QF-" + Math.abs(ph.hashCode() % 90000 + 10000)
                    if (subscriptionExpiryMillis <= 0L) {
                        subscriptionExpiryMillis = System.currentTimeMillis() + (10L * 24L * 60L * 60L * 1000L)
                    }
                }
                saveSessionSynchronously(true)
                syncStoreWithAdminServer(userPhone, userPassword)
                renderMainWorkspace()
                scheduleMidnightAutoExcelSaveWithSignal()
            }
        }
        formCard.addView(btnLoginExisting)

        formCard.addView(TextView(this).apply {
            text = "🏪 Yangi Do'kon Ma'lumotlari:"
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(4, 8, 4, 10)
        })
        formCard.addView(etStore)
        formCard.addView(etAddr)

        val btnRegisterNew = Button(this).apply {
            text = "🚀 Yangi Ro'yxatdan O'tish (10 kun bepul)"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyOrangeGrad(36f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130).apply { topMargin = 8 }
            setOnClickListener {
                val ph = etPhone.text.toString().trim()
                val pw = etPass.text.toString().trim()
                val st = etStore.text.toString().trim()
                if (ph.length < 9 || pw.isEmpty() || st.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Do'kon nomi, telefon va parolni kiriting!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                userPhone = ph
                userPassword = pw
                storeName = st
                storeAddress = etAddr.text.toString().trim()
                storeId = "QF-" + Math.abs(ph.hashCode() % 90000 + 10000)
                if (subscriptionExpiryMillis <= System.currentTimeMillis()) {
                    subscriptionExpiryMillis = System.currentTimeMillis() + (10L * 24L * 60L * 60L * 1000L)
                }

                saveSessionSynchronously(true)
                syncStoreWithAdminServer(ph, pw)
                renderMainWorkspace()
                scheduleMidnightAutoExcelSaveWithSignal()
            }
        }
        formCard.addView(btnRegisterNew)

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
        mainFrame.addView(buildLeftAndRightEdgeSlideOverlays())

        rootLayout.addView(mainFrame)
        rootLayout.addView(buildBottomNavBar())
    }

    private fun buildLeftAndRightEdgeSlideOverlays(): View {
        val overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val isSup = (currentBottomTab == 0 && currentHomeSubTab == 1)

        if (isLeftEdgeOpen || isRightEdgeOpen) {
            overlay.setBackgroundColor(Color.parseColor("#550F172A"))
            overlay.setOnClickListener {
                isLeftEdgeOpen = false
                isRightEdgeOpen = false
                renderMainWorkspace()
            }
        }

        if (!isLeftEdgeOpen) {
            val leftPeekTab = TextView(this).apply {
                text = "❯"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                background = glossyPurpleGrad(60f)
                alpha = 0.85f
                elevation = 16f
                layoutParams = FrameLayout.LayoutParams(96, 150, Gravity.CENTER_VERTICAL or Gravity.START).apply {
                    leftMargin = -30
                }
                var startX = 0f
                setOnTouchListener { _, ev ->
                    when (ev.action) {
                        MotionEvent.ACTION_DOWN -> { startX = ev.rawX; true }
                        MotionEvent.ACTION_MOVE -> {
                            if (ev.rawX - startX > 20f) {
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
                        overlay.setBackgroundColor(Color.TRANSPARENT)
                        leftCol.visibility = View.GONE
                        when (labelText) {
                            "Excel Import" -> showImportBottomSheet()
                            "Excel Eksport" -> showExportBottomSheet()
                            "Tezkor tranzaksiya" -> showQuickTransactionBottomSheet(isSup)
                            else -> showAddClientBottomSheet(isSup)
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

        if (!isRightEdgeOpen) {
            val rightPeekTab = TextView(this).apply {
                text = "❮"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                background = glossyPurpleGrad(60f)
                alpha = 0.85f
                elevation = 16f
                layoutParams = FrameLayout.LayoutParams(96, 150, Gravity.CENTER_VERTICAL or Gravity.END).apply {
                    rightMargin = -30
                }
                var startX = 0f
                setOnTouchListener { _, ev ->
                    when (ev.action) {
                        MotionEvent.ACTION_DOWN -> { startX = ev.rawX; true }
                        MotionEvent.ACTION_MOVE -> {
                            if (startX - ev.rawX > 20f) {
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
                Triple("Suhbat (Admin)", R.drawable.ic_vec_sms_horn) { showSupportChatBottomSheet() },
                Triple("Kalkulyator", R.drawable.ic_vec_calc) { openSalesCalculatorDialog() },
                Triple("Bozorlik", R.drawable.ic_vec_list) { openShoppingListDialog() },
                Triple("Vazifalar", R.drawable.ic_vec_tasks) { showCommunityAndAmbassadorDialog() },
                Triple("Sotuv", R.drawable.ic_vec_sale) { currentBottomTab = 2; mahsulotTopTab = "Sotuv"; renderMainWorkspace() },
                Triple("SMS jo'natma", R.drawable.ic_vec_sms_horn) { sendBulkSmsReminders(false) },
                Triple("Avto-Qo'ng'iroq", R.drawable.ic_vec_phone) { showVoiceSettingsDialog() }
            )

            rightItems.forEach { (label, vecRes, act) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
                    setOnClickListener {
                        isRightEdgeOpen = false
                        overlay.setBackgroundColor(Color.TRANSPARENT)
                        rightCol.visibility = View.GONE
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
                    layoutParams = LinearLayout.LayoutParams(126, 126)
                    addView(ImageView(this@MainActivity).apply {
                        setImageResource(vecRes)
                        layoutParams = FrameLayout.LayoutParams(64, 64, Gravity.CENTER)
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

    // ==================== TAB 0: ASOSIY EKRAN (REAL-TIME SEARCH BILAN!) ====================
    private fun buildHomeTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 180)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        val menuIcon = TextView(this).apply {
            text = "☰"
            textSize = 20f
            setTextColor(Color.parseColor("#1E293B"))
            gravity = Gravity.CENTER
            background = glossyCard("#FFFFFF", "#FFFFFF", 30f)
            elevation = 6f
            layoutParams = LinearLayout.LayoutParams(105, 105).apply { rightMargin = 14 }
            setOnClickListener {
                isLeftEdgeOpen = !isLeftEdgeOpen
                isRightEdgeOpen = false
                renderMainWorkspace()
            }
        }
        val logoImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(90, 90).apply { rightMargin = 12 }
        }
        val storeTitle = TextView(this).apply {
            text = "🇺🇿 $storeName ❯"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openBusinessInfoDialog() }
        }
        val chatHeaderBtn = FrameLayout(this).apply {
            background = glossyPurpleGrad(28f)
            elevation = 6f
            layoutParams = LinearLayout.LayoutParams(105, 105).apply { rightMargin = 10 }
            addView(TextView(this@MainActivity).apply {
                text = "💬"
                gravity = Gravity.CENTER
                textSize = 17f
            })
            setOnClickListener { showSupportChatBottomSheet() }
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
        header.addView(chatHeaderBtn)
        header.addView(qrBtn)
        col.addView(header)

        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glossyPurpleGrad(44f)
            elevation = 12f
            setPadding(38, 28, 38, 28)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
            setOnClickListener { showSubscriptionTariffsBottomSheet() }
        }
        banner.addView(TextView(this).apply {
            text = "🎁"
            textSize = 25f
            setPadding(0, 0, 22, 0)
        })
        banner.addView(TextView(this).apply {
            text = "Obuna: ${remainingDaysText()}\nAvto-Save: Download/QarzFy • SIM SMS + Ovozli Bot faol"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        banner.addView(TextView(this).apply {
            text = "❯"
            setTextColor(Color.WHITE)
            textSize = 18f
        })
        col.addView(banner)

        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
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
                setPadding(16, 22, 16, 22)
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

        val directActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        directActionsRow.addView(Button(this).apply {
            text = if (isSup) "👤+ Ta'minotchi qo'shish" else "👤+ Qarzdor qo'shish"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(30f)
            layoutParams = LinearLayout.LayoutParams(0, 120, 1f).apply { rightMargin = 8 }
            setOnClickListener { showAddClientBottomSheet(isSup) }
        })
        directActionsRow.addView(Button(this).apply {
            text = "⚡ Tezkor tranzaksiya"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyOrangeGrad(30f)
            layoutParams = LinearLayout.LayoutParams(0, 120, 1f).apply { leftMargin = 8 }
            setOnClickListener { showQuickTransactionBottomSheet(isSup) }
        })
        col.addView(directActionsRow)

        val tvCountLabel = TextView(this).apply {
            textColor = Color.parseColor("#4F46E5")
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(16, 10, 28, 10)
        }

        val folderBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        folderBar.addView(tvCountLabel)
        folderBar.addView(TextView(this).apply {
            text = "+"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.parseColor("#475569"))
            background = glossyCard("#FFFFFF", "#CBD5E1", 24f)
            layoutParams = LinearLayout.LayoutParams(78, 78)
            setOnClickListener { openCreateFolderDialog() }
        })
        col.addView(folderBar)

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
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

        val etSearch = makeGlassInput("🔍 Ism yoki telefon raqam yozing (birdan chiqaradi)...", searchQuery)
        col.addView(etSearch)

        // JONLI NATIJALAR KONTEYNERI (Yozgan zahoti klaviatura yopilmasdan yangilanadi!)
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        col.addView(listContainer)

        fun refreshPersonListLive(q: String) {
            listContainer.removeAllViews()
            val persons = db.getPersons(isSup, q, currentSortMode)
            tvCountLabel.text = "Barchasi  ${persons.size}"

            if (persons.isEmpty()) {
                val emptyBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = glossyCard("#FFFFFF", "#FFFFFF", 42f)
                    elevation = 6f
                    setPadding(40, 60, 40, 60)
                }
                emptyBox.addView(TextView(this).apply {
                    text = if (q.isNotEmpty()) "'$q' bo'yicha hech kim topilmadi" else (if (isSup) "Ta'minotchi topilmadi" else "Qarz yuritishni boshlash uchun birinchi mijozingizni qo'shing")
                    setTextColor(Color.parseColor("#64748B"))
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 20)
                })
                emptyBox.addView(Button(this).apply {
                    text = if (isSup) "➕ Ta'minotchi qo'shish" else "➕ Qarzdor qo'shish"
                    setTextColor(Color.WHITE)
                    background = glossyPurpleGrad(32f)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 120)
                    setPadding(50, 0, 50, 0)
                    setOnClickListener { showAddClientBottomSheet(isSup) }
                })
                listContainer.addView(emptyBox)
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
                    listContainer.addView(rowCard)
                }
            }
        }

        refreshPersonListLive(searchQuery)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                refreshPersonListLive(searchQuery)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        scroll.addView(col)
        return scroll
    }

    // ==================== TAB 1: KASSA ====================
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

    // ==================== TAB 2: MAHSULOT VA OMBOR (REAL-TIME QIDIRUV BILAN!) ====================
    private fun buildMahsulotTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 180)
        }

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

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        val etPrSearch = makeGlassInput("🔍 Mahsulot qidirish...", productSearchQuery).apply {
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

        val prodContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        col.addView(prodContainer)

        fun refreshProductsLive(q: String) {
            prodContainer.removeAllViews()
            val prods = db.getProducts(q)
            if (prods.isEmpty()) {
                val emptyCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = glossyCard("#FFFFFF", "#FFFFFF", 42f)
                    elevation = 6f
                    setPadding(44, 70, 44, 70)
                }
                emptyCard.addView(TextView(this).apply {
                    text = if (q.isNotEmpty()) "'$q' bo'yicha mahsulot topilmadi" else "📦 Omborda mahsulot yo'q\nBirinchi yetkazib berishni qabul qilish uchun pastdagi tugmani bosing"
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
                prodContainer.addView(emptyCard)
            } else {
                prods.forEach { pr ->
                    prodContainer.addView(TextView(this).apply {
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
        }

        refreshProductsLive(productSearchQuery)

        etPrSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                productSearchQuery = s?.toString() ?: ""
                refreshProductsLive(productSearchQuery)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        scroll.addView(col)
        return scroll
    }

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

    // ==================== TAB 3: STATISTIKA ====================
    private fun buildStatistikaTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 180)
        }

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
            text = "👤\nFaol"
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
            text = "$userPhone  •  ID: $storeId"
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
        r1.addView(profileGridCard("🔔 Avto-Save & Signal", "Download/QarzFy doimiy") { showPushSettingsDialog() })
        r1.addView(profileGridCard("💎 Limitlar va tarif", remainingDaysText()) { showSubscriptionTariffsBottomSheet() })
        col.addView(r1)

        val r2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        r2.addView(profileGridCard("💳 Obuna xarid qilish", "${price1Month/1000}k / ${price3Month/1000}k / ${price6Month/1000}k") { showSubscriptionTariffsBottomSheet() })
        r2.addView(profileGridCard("💬 Admin bilan suhbat", "Savol va Chek yuborish") { showSupportChatBottomSheet() })
        col.addView(r2)

        val smsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#FFFFFF", 38f)
            elevation = 4f
            setPadding(36, 30, 36, 30)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
            setOnClickListener { showVoiceSettingsDialog() }
        }
        smsCard.addView(TextView(this).apply {
            text = "💬 SMS va 📞 Fonda Avto-Qo'ng'iroq Bot  •  Yoniq"
            setTextColor(Color.parseColor("#059669"))
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
        })
        smsCard.addView(View(this).apply {
            background = glossyCard("#10B981", "#10B981", 10f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 16).apply { topMargin = 16 }
        })
        col.addView(smsCard)

        val settingsList = listOf(
            "💬 Admin bilan jonli suhbat (Bot orqali murojaat)" to { showSupportChatBottomSheet() },
            "📤 Excel (.xlsx) Eksport — Download/QarzFy va Telegram/Bluetooth" to { showExportBottomSheet() },
            "📥 Excel (.xlsx) Fayldan Mijozlarni Import Qilish" to { showImportBottomSheet() },
            "📞 Fonda Ko'rinmas Avto-Qo'ng'iroq (Bot) sozlamasi" to { showVoiceSettingsDialog() },
            "👑 Super-Admin Panelga Kirish (Login: admin / Parol: hasan2026)" to {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$SERVER_URL/admin")))
            },
            "🖨 Printer sozlamalari (58mm / 80mm / A4)" to { showPrinterSettingsDialog() },
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

        val btnLogout = Button(this).apply {
            text = "🚪 Hisobdan chiqish (Log Out)"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glossyRedGrad(36f)
            elevation = 8f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 135).apply { topMargin = 16; bottomMargin = 20 }
            setOnClickListener { performLogout() }
        }
        col.addView(btnLogout)

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

    // ==================== 3. MEDIASTORE CONTENT URI BILAN .XLSX SAQLASH VA TELEGRAMGA BEXATO ULASHISH ====================
    // Dastur o'chirilsa ham telefonning Download/QarzFy papkasidan o'chmaydigan Avto-Save funksiyasi:
    private fun savePermanentBackupToDownloadsQarzFy() {
        val xlsxBytes = generateRealXlsxZipBytes()
        val fileName = "QarzFy_AvtoBazasi.xlsx"

        try {
            val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "QarzFy")
            if (!pubDir.exists()) pubDir.mkdirs()
            File(pubDir, fileName).writeBytes(xlsxBytes)
        } catch (_: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(fileName)
                )
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/QarzFy")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(xlsxBytes) }
                }
            }
        } catch (_: Exception) {}
    }

    // Telegram/Bluetooth "Unsupported attachment" demasligi uchun rasmiy content:// URI qaytaradi!
    private fun saveRealXlsxAndGetShareableUri(): Triple<File, String, Uri> {
        val dateTag = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val fileName = "QarzFy_Hisobot_$dateTag.xlsx"

        val appSubDir = File(getExternalFilesDir(null), "QarzFy")
        if (!appSubDir.exists()) appSubDir.mkdirs()
        val localXlsx = File(appSubDir, fileName)

        val xlsxBytes = generateRealXlsxZipBytes()
        localXlsx.writeBytes(xlsxBytes)

        // Doimiy zaxira faylni ham yangilaymiz
        savePermanentBackupToDownloadsQarzFy()

        var publicPath = "/storage/emulated/0/Download/QarzFy/$fileName"
        var shareUri: Uri = Uri.fromFile(localXlsx)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/QarzFy")
                }
                val insertedUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (insertedUri != null) {
                    contentResolver.openOutputStream(insertedUri)?.use { it.write(xlsxBytes) }
                    shareUri = insertedUri // Telegram va Bluetooth 100% qabul qiladigan rasmiy content:// URI!
                }
            } else {
                val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "QarzFy")
                if (!pubDir.exists()) pubDir.mkdirs()
                val pubFile = File(pubDir, fileName)
                pubFile.writeBytes(xlsxBytes)
                publicPath = pubFile.absolutePath
                shareUri = Uri.fromFile(pubFile)
            }
        } catch (_: Exception) {}

        return Triple(localXlsx, publicPath, shareUri)
    }

    private fun shareExcelUriToTelegramOrBluetooth(shareUri: Uri, fileName: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_TEXT, "📊 QarzFy Rasmiy Excel Hisobot ($fileName)\n🏪 Do'kon: $storeName ($storeId)")
                clipData = ClipData.newRawUri(fileName, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Excel (.xlsx) faylni yuborish (Telegram / Bluetooth)"))
        } catch (e: Exception) {
            Toast.makeText(this, "Saqlangan joyi: Download/QarzFy/$fileName", Toast.LENGTH_LONG).show()
        }
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
                .setContentTitle("📊 QarzFy Excel (.xlsx) Saqlandi!")
                .setContentText("Manzil: $savedPath")
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
                    val (_, path, _) = saveRealXlsxAndGetShareableUri()
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

    // ==================== SLIDE-UP BOTTOM SHEETS ====================
    private fun showExportBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 30, 44, 56)
        }
        wrap.addView(TextView(this).apply {
            text = "📤 Excel (.xlsx) Eksport va Ulashish"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })
        wrap.addView(TextView(this).apply {
            text = "Barcha o'zgarishlar avtomatik ravishda telefonning 'Download/QarzFy/QarzFy_AvtoBazasi.xlsx' fayliga saqlanib boradi (dastur o'chirilsa ham o'chmaydi!)."
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            setPadding(0, 0, 0, 24)
        })

        wrap.addView(Button(this).apply {
            text = "💾 Download/QarzFy papkaga yangi nusxa saqlash"
            setTextColor(Color.WHITE)
            background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130).apply { bottomMargin = 16 }
            setOnClickListener {
                val (file, savedPath, shareUri) = saveRealXlsxAndGetShareableUri()
                triggerAutoExportSignalAndNotification(savedPath)
                sheet.dismiss()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("✅ Excel (.xlsx) Saqlandi!")
                    .setMessage("Fayl nomi:\n${file.name}\n\nDoimiy manzil:\n$savedPath")
                    .setPositiveButton("📤 Telegram / Bluetooth orqali yuborish") { _, _ ->
                        shareExcelUriToTelegramOrBluetooth(shareUri, file.name)
                    }
                    .setNegativeButton("Yopish", null)
                    .show()
            }
        })

        wrap.addView(Button(this).apply {
            text = "📤 Telegram yoki Bluetooth orqali yuborish"
            setTextColor(Color.WHITE)
            background = glossyOrangeGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130)
            setOnClickListener {
                val (file, savedPath, shareUri) = saveRealXlsxAndGetShareableUri()
                triggerAutoExportSignalAndNotification(savedPath)
                sheet.dismiss()
                shareExcelUriToTelegramOrBluetooth(shareUri, file.name)
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
            text = "📥 Excel (.xlsx / .csv) Fayldan Import"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })
        wrap.addView(TextView(this).apply {
            text = "Download/QarzFy papkasidagi avtomatik saqlangan zaxira fayldan yoki istalgan .xlsx fayldan mijozlarni yuklang:"
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            setPadding(0, 0, 0, 20)
        })
        wrap.addView(Button(this).apply {
            text = "🔄 Download/QarzFy/QarzFy_AvtoBazasi.xlsx dan tiklash"
            setTextColor(Color.WHITE)
            background = glossyOrangeGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130).apply { bottomMargin = 14 }
            setOnClickListener {
                sheet.dismiss()
                val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "QarzFy")
                val backupFile = File(pubDir, "QarzFy_AvtoBazasi.xlsx")
                if (backupFile.exists()) {
                    val c = parseXlsxZipBytes(backupFile.readBytes(), triggerBackup = false)
                    Toast.makeText(this@MainActivity, "✅ AvtoBazadan $c ta yozuv tiklandi!", Toast.LENGTH_LONG).show()
                    renderMainWorkspace()
                } else {
                    Toast.makeText(this@MainActivity, "Fayl tanlash oynasidan tanlang", Toast.LENGTH_SHORT).show()
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                    startActivityForResult(intent, PICK_IMPORT_FILE)
                }
            }
        })
        wrap.addView(Button(this).apply {
            text = "📂 Telefondan Boshqa Excel (.xlsx) Fayl Tanlash"
            setTextColor(Color.WHITE)
            background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130)
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
        var selectedKind = "Chiqim"
        val kindSwitchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glossyCard("#F1F5F9", "#E2E8F0", 32f)
            setPadding(10, 10, 10, 10)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        val btnChiqim = TextView(this)
        val btnKirim = TextView(this)

        fun updateKindTabs() {
            val cSel = (selectedKind == "Chiqim")
            btnChiqim.apply {
                text = "Chiqim (Qarz yozish)"; gravity = Gravity.CENTER; textSize = 13.5f; setTypeface(null, Typeface.BOLD)
                setTextColor(if (cSel) Color.WHITE else Color.parseColor("#475569"))
                background = if (cSel) glossyOrangeGrad(26f) else null
                setPadding(16, 22, 16, 22)
            }
            btnKirim.apply {
                text = "Kirim (To'lov olish)"; gravity = Gravity.CENTER; textSize = 13.5f; setTypeface(null, Typeface.BOLD)
                setTextColor(if (!cSel) Color.WHITE else Color.parseColor("#475569"))
                background = if (!cSel) glossyPurpleGrad(26f) else null
                setPadding(16, 22, 16, 22)
            }
        }
        btnChiqim.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        btnKirim.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        btnChiqim.setOnClickListener { selectedKind = "Chiqim"; updateKindTabs() }
        btnKirim.setOnClickListener { selectedKind = "Kirim"; updateKindTabs() }
        updateKindTabs()
        kindSwitchRow.addView(btnChiqim); kindSwitchRow.addView(btnKirim)
        wrap.addView(kindSwitchRow)

        wrap.addView(TextView(this).apply {
            text = "👤 ${client.name}  •  Joriy balans: ${formatMoney(client.balUzs, "UZS")}"
            setTextColor(Color.parseColor("#0F172A")); textSize = 15.5f; setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        })

        val etAmount = makeGlassInput("Summani kiriting (UZS) *", "")
        etAmount.inputType = InputType.TYPE_CLASS_NUMBER
        val etNote = makeGlassInput("Izoh / Mahsulot nomi (ixtiyoriy)", "")
        wrap.addView(etAmount); wrap.addView(etNote)

        wrap.addView(Button(this).apply {
            text = "✅ Saqlash (Avto-SMS + Fonda Qo'ng'iroq + Avto-Save)"
            setTextColor(Color.WHITE); background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 135)
            setOnClickListener {
                val amt = cleanNumberInput(etAmount.text.toString())
                if (amt <= 0L) {
                    Toast.makeText(this@MainActivity, "Summani kiriting!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val note = etNote.text.toString().trim().ifEmpty { "Tranzaksiya" }
                db.addOrUpdatePersonTx(client.name, client.phone, amt, "UZS", selectedKind, note, "Bugun", isSupplier)
                triggerBackgroundSmsAndVoiceCall(client.name, client.phone, amt, selectedKind, note)
                Toast.makeText(this@MainActivity, "✅ Saqlandi! Download/QarzFy yangilandi, SMS va Fonda Ovozli xabar yuborildi!", Toast.LENGTH_SHORT).show()
                sheet.dismiss()
                renderMainWorkspace()
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
            text = if (isSupplier) "⚡ Tezkor ta'minotchi tranzaksiyasi" else "⚡ Tezkor tranzaksiya"
            setTextColor(Color.parseColor("#0F172A")); textSize = 19f; setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 14)
        })

        val etQuickSearch = makeGlassInput("🔍 Mijoz ismini yoki telefonini yozing...", "")
        wrap.addView(etQuickSearch)

        wrap.addView(TextView(this).apply {
            text = "Mijozni tanlang (ustiga bosing):"
            setTextColor(Color.parseColor("#475569")); textSize = 13.5f; setTypeface(null, Typeface.BOLD)
            setPadding(0, 4, 0, 14)
        })

        val hScroll = HorizontalScrollView(this)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hScroll.addView(row)
        wrap.addView(hScroll)

        fun populateQuickClients(q: String) {
            row.removeAllViews()
            val list = db.getPersons(isSupplier, q)
            if (list.isEmpty()) {
                row.addView(TextView(this).apply {
                    text = "Mijoz topilmadi. Pastdagi tugma orqali yangi qo'shing."
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(16, 30, 16, 30)
                })
            } else {
                list.take(15).forEach { p ->
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                        background = glossyCard("#F8FAFC", "#CBD5E1", 32f)
                        setPadding(26, 26, 26, 26)
                        layoutParams = LinearLayout.LayoutParams(260, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 16 }
                        setOnClickListener {
                            sheet.dismiss()
                            showClientKirimChiqimBottomSheet(p, isSupplier)
                        }
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
            }
        }

        populateQuickClients("")
        etQuickSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { populateQuickClients(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        wrap.addView(Button(this).apply {
            text = if (isSupplier) "➕ Yangi Ta'minotchi va Tranzaksiya" else "➕ Yangi Qarzdor Qo'shish"
            setTextColor(Color.WHITE)
            background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130).apply { topMargin = 20 }
            setOnClickListener {
                sheet.dismiss()
                showAddClientBottomSheet(isSupplier)
            }
        })

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
            text = if (isSupplier) "🚚 Yangi Ta'minotchi Qo'shish" else "👤+ Yangi Qarzdor / Mijoz Qo'shish"
            setTextColor(Color.parseColor("#0F172A")); textSize = 19f; setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 18)
        })
        val etName = makeGlassInput("Ism (masalan: Ali aka) *", "")
        val etPhone = makeGlassInput("Telefon (+998901234567)", "+998")
        etPhone.inputType = InputType.TYPE_CLASS_PHONE
        val etDebt = makeGlassInput("Qarz summasi (UZS, bo'sh qoldirish mumkin)", "")
        etDebt.inputType = InputType.TYPE_CLASS_NUMBER
        val etNote = makeGlassInput("Izoh / Olingan mahsulotlar", "")

        wrap.addView(etName)
        wrap.addView(etPhone)
        wrap.addView(etDebt)
        wrap.addView(etNote)

        wrap.addView(Button(this).apply {
            text = "✅ Qo'shish va Saqlash (SMS + Fonda Ovoz + Avto-Save)"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 135)
            setOnClickListener {
                val n = etName.text.toString().trim()
                val ph = etPhone.text.toString().trim()
                val d = cleanNumberInput(etDebt.text.toString())
                val note = etNote.text.toString().trim().ifEmpty { "Qarz yozildi" }

                if (n.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Iltimos, mijoz ismini kiriting!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                if (d > 0L) {
                    db.addOrUpdatePersonTx(n, ph, d, "UZS", "Chiqim", note, "Bugun", isSupplier)
                    triggerBackgroundSmsAndVoiceCall(n, ph, d, "Chiqim", note)
                } else {
                    db.upsertClientDirect(n, ph, 0.0, 0L, db.nowStr(), isSupplier)
                }

                Toast.makeText(this@MainActivity, "✅ $n qo'shildi va Download/QarzFy ga avto-saqlandi!", Toast.LENGTH_SHORT).show()
                searchQuery = ""
                sheet.dismiss()
                renderMainWorkspace()
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
            setPadding(0, 0, 0, 8)
        })
        wrap.addView(TextView(this).apply {
            text = "Tarifni tanlang -> Kartaga to'lov qilib skrinshot oling -> Botga skrinshotni yuboring:"
            setTextColor(Color.parseColor("#475569")); textSize = 12.5f
            setPadding(0, 0, 0, 18)
        })
        wrap.addView(tariffButton("💳 1 Oy — ${formatMoney(price1Month, "so'm")}", "Botga to'lov cheki (skrinshot) yuborish", "#6D28D9") { sheet.dismiss(); selectTariffAndPayDialog(1, price1Month) })
        wrap.addView(tariffButton("🔥 3 Oy — ${formatMoney(price3Month, "so'm")}", "Botga to'lov cheki (skrinshot) yuborish", "#2563EB") { sheet.dismiss(); selectTariffAndPayDialog(3, price3Month) })
        wrap.addView(tariffButton("👑 6 Oy — ${formatMoney(price6Month, "so'm")}", "Botga to'lov cheki (skrinshot) yuborish", "#059669") { sheet.dismiss(); selectTariffAndPayDialog(6, price6Month) })
        sheet.setContentView(wrap)
        sheet.show()
    }

    // ==================== 4. .XLSX VA .CSV IMPORT PARSER ====================
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMPORT_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return
                    val importedCount = if (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                        parseXlsxZipBytes(bytes, triggerBackup = true)
                    } else {
                        parseCsvSmartLines(String(bytes, Charsets.UTF_8).lines())
                    }
                    Toast.makeText(this, "✅ Jami $importedCount ta yozuv import qilindi va Download/QarzFy ga avto-saqlandi!", Toast.LENGTH_LONG).show()
                    renderMainWorkspace()
                } catch (e: Exception) {
                    Toast.makeText(this, "Import xatosi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun parseMoneyLong(s: String?): Long {
        if (s == null) return 0L
        val cleaned = s.replace("\"", "").replace(",", "").replace(" ", "").replace("UZS", "", true).trim()
        return cleaned.toDoubleOrNull()?.toLong() ?: 0L
    }

    private fun parseMoneyDouble(s: String?): Double {
        if (s == null) return 0.0
        val cleaned = s.replace("\"", "").replace(",", "").replace(" ", "").trim()
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    private fun colIndexFromCellRef(ref: String?): Int {
        if (ref.isNullOrEmpty()) return -1
        var col = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') col = col * 26 + (ch - 'A' + 1)
            else if (ch in 'a'..'z') col = col * 26 + (ch - 'a' + 1)
            else break
        }
        return col - 1
    }

    private fun parseSingleWorksheetXml(sheetBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(ByteArrayInputStream(sheetBytes), "UTF-8")
        var event = parser.eventType
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var cellType = ""
        var cellValue = StringBuilder()
        var currentColIdx = -1
        var insideVOrT = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> {
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            val rAttr = parser.getAttributeValue(null, "r")
                            currentColIdx = colIndexFromCellRef(rAttr)
                            cellValue = StringBuilder()
                        }
                        "v", "t" -> insideVOrT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideVOrT) cellValue.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v", "t" -> insideVOrT = false
                        "c" -> {
                            val raw = cellValue.toString()
                            val finalVal = if (cellType == "s") {
                                val idx = raw.trim().toIntOrNull() ?: -1
                                if (idx in sharedStrings.indices) sharedStrings[idx] else raw
                            } else raw
                            if (currentColIdx >= 0) {
                                while (currentRow.size < currentColIdx) currentRow.add("")
                            }
                            currentRow.add(finalVal)
                        }
                        "row" -> {
                            if (currentRow.any { it.isNotBlank() }) rows.add(currentRow)
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun parseXlsxZipBytes(bytes: ByteArray, triggerBackup: Boolean = true): Int {
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
            var insideT = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "si") { insideSi = true; currentSi = StringBuilder() }
                        else if (parser.name == "t" && insideSi) insideT = true
                    }
                    XmlPullParser.TEXT -> {
                        if (insideSi && insideT) currentSi.append(parser.text ?: "")
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "t") insideT = false
                        else if (parser.name == "si") { insideSi = false; sharedStrings.add(currentSi.toString()) }
                    }
                }
                event = parser.next()
            }
        }

        var totalImported = 0
        entries["xl/worksheets/sheet1.xml"]?.let { sb ->
            val rows = parseSingleWorksheetXml(sb, sharedStrings)
            rows.forEachIndexed { idx, r ->
                if (idx > 0 && r.size >= 2) {
                    val name = r.getOrNull(1)?.trim() ?: ""
                    val phone = r.getOrNull(2)?.trim() ?: ""
                    val usd = parseMoneyDouble(r.getOrNull(3))
                    val uzs = parseMoneyLong(r.getOrNull(4))
                    val dt = r.getOrNull(5)?.trim() ?: db.nowStr()
                    if (name.isNotEmpty() && !name.equals("Ism", true)) {
                        db.upsertClientDirect(name, phone, usd, uzs, dt, false, triggerAutoBackup = false)
                        totalImported++
                    }
                }
            }
        }
        entries["xl/worksheets/sheet3.xml"]?.let { sb ->
            val rows = parseSingleWorksheetXml(sb, sharedStrings)
            rows.forEachIndexed { idx, r ->
                if (idx > 0 && r.size >= 2) {
                    val name = r.getOrNull(1)?.trim() ?: ""
                    val phone = r.getOrNull(2)?.trim() ?: ""
                    val usd = parseMoneyDouble(r.getOrNull(3))
                    val uzs = parseMoneyLong(r.getOrNull(4))
                    val dt = r.getOrNull(6)?.trim() ?: db.nowStr()
                    if (name.isNotEmpty() && !name.equals("Ism", true)) {
                        db.upsertClientDirect(name, phone, usd, uzs, dt, true, triggerAutoBackup = false)
                        totalImported++
                    }
                }
            }
        }
        if (triggerBackup && totalImported > 0) {
            db.onDataChangedCallback?.invoke()
        }
        return totalImported
    }

    private fun parseCsvSmartLines(lines: List<String>): Int {
        var count = 0
        lines.forEach { raw ->
            val l = raw.replace("\uFEFF", "").trim()
            if (l.isEmpty() || l.startsWith("ID") || l.startsWith("===") || l.startsWith("Tranzaksiya")) return@forEach
            val cols = splitCsvRespectingQuotes(l)
            if (cols.size >= 3) {
                val n = cols.getOrNull(1)?.trim() ?: ""
                val p = cols.getOrNull(2)?.trim() ?: ""
                val usd = parseMoneyDouble(cols.getOrNull(3))
                val uzs = parseMoneyLong(cols.getOrNull(4))
                val dt = cols.getOrNull(5)?.trim() ?: db.nowStr()
                if (n.isNotEmpty() && !n.equals("Ism", true)) {
                    db.upsertClientDirect(n, p, usd, uzs, dt, false, triggerAutoBackup = false)
                    count++
                }
            }
        }
        if (count > 0) db.onDataChangedCallback?.invoke()
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
                val amt = cleanNumberInput(etAmt.text.toString())
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
                    db.addProduct(n, etCode.text.toString().ifEmpty { "BC-101" }, etQty.text.toString().toIntOrNull() ?: 1, "dona", cleanNumberInput(etCost.text.toString()), cleanNumberInput(etSale.text.toString()), "UZS", storeName)
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
                val v = cleanNumberInput(et.text.toString())
                if (v > 0) { db.addKassa("Kirim", "Kalkulyator", v, "UZS", "Naqd", "", storeName); renderMainWorkspace() }
            }.setNegativeButton("Yopish", null).show()
    }

    private fun showSmsSettingsDialog() {
        AlertDialog.Builder(this).setTitle("💬 SMS sozlamalari").setMessage("SIM-karta orqali qarzdorlarga fonda avtomatik SMS yuborish yoqilgan.").setPositiveButton("Yopish", null).show()
    }

    private fun showPushSettingsDialog() {
        AlertDialog.Builder(this).setTitle("🔔 Avto-Save va Bildirishnomalar")
            .setMessage("• Har bir kirim-chiqim va qarz yozilganda fayl avtomatik Download/QarzFy/QarzFy_AvtoBazasi.xlsx ga saqlanadi (dastur o'chirilsa ham o'chmaydi!).\n• Har kuni soat 00:00 da signal bilan kunlik hisobot saqlanadi.")
            .setPositiveButton("Tushunarli", null).show()
    }

    private fun showVoiceSettingsDialog() {
        val statusTxt = if (isAutoVoiceCallEnabled) "✅ Yoqilgan (Fonda ko'rinmas)" else "🔴 O'chirilgan"
        AlertDialog.Builder(this)
            .setTitle("📞 Fonda Avto-Qo'ng'iroq (Bot) Sozlamasi")
            .setMessage("Holati: $statusTxt\n\nXaridorga qarz yoki to'lov yozilganda, xuddi SMS kabi telefonda umuman ko'rinmasdan va bilinmasdan fonda Bot va Ovozli tizim orqali xaridorga qo'ng'iroq/ovozli ma'lumot yuboriladi.\n\nOvoz: O'zbekcha (Nigora) / Ruscha (Alena) / Inglizcha (Jon)")
            .setPositiveButton(if (isAutoVoiceCallEnabled) "O'chirish" else "Yoqish") { _, _ ->
                isAutoVoiceCallEnabled = !isAutoVoiceCallEnabled
                saveSessionSynchronously(isLoggedIn)
                Toast.makeText(this, if (isAutoVoiceCallEnabled) "📞 Fonda Avto-Qo'ng'iroq yoqildi!" else "O'chirildi", Toast.LENGTH_SHORT).show()
                renderMainWorkspace()
            }
            .setNeutralButton("🔊 Ovozni test qilish") { _, _ ->
                ttsEngine?.speak("Assalomu alaykum! Hurmatli mijoz, do'konimizdan sizga qarz yozildi.", TextToSpeech.QUEUE_FLUSH, null, "test_voice")
                Toast.makeText(this, "🔊 Ovozli xabar fonda o'qilmoqda...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Yopish", null)
            .show()
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
            .setMessage("Ilova: QarzFy v10.0 (Glossy Glassmorphism)\nDasturchi: Hasan Jaylovov\nTelegram: @HasanJaylovov")
            .setPositiveButton("Bog'lanish") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/HasanJaylovov")))
            }.setNegativeButton("Yopish", null).show()
    }

    private fun sendBulkSmsReminders(isSupplier: Boolean) {
        var sent = 0
        db.getPersons(isSupplier).forEach { p ->
            if (p.balUzs < 0) {
                triggerBackgroundSmsAndVoiceCall(p.name, p.phone, Math.abs(p.balUzs), "Chiqim", "Qarz eslatmasi")
                sent++
            }
        }
        Toast.makeText(this, "📩 $sent ta qarzdorga fonda SMS va Avto-Qo'ng'iroq buyrug'i yuborildi!", Toast.LENGTH_LONG).show()
    }
}
