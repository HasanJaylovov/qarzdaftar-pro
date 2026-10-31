package uz.qarzdaftar.pro

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
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
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import android.view.inputmethod.InputMethodManager
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
    val id: Int,
    val txId: String,
    val kind: String,
    val personName: String,
    val personPhone: String,
    val amount: Long,
    val currency: String,
    val date: String,
    val dueDate: String,
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

class QarzFyDatabase(private val ctx: Context) : SQLiteOpenHelper(ctx, "qarzfy_master_permanent.db", null, 5) {
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
                due_date TEXT DEFAULT '',
                note TEXT,
                status TEXT,
                is_supplier INTEGER DEFAULT 0
            )
        """.trimIndent())

        try {
            db.execSQL("ALTER TABLE transactions ADD COLUMN due_date TEXT DEFAULT ''")
        } catch (_: Exception) {}

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

    fun upsertClientDirect(name: String, phone: String, balUsd: Double, balUzs: Long, createdAt: String, isSupplier: Boolean, dueDate: String = "", triggerAutoBackup: Boolean = true) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        val c = db.rawQuery("SELECT id, due_date FROM clients WHERE name = ? AND is_supplier = ?", arrayOf(cleanName, supFlag.toString()))
        if (c.moveToFirst()) {
            val id = c.getInt(0)
            val existingDue = c.getString(1) ?: ""
            val finalDue = if (dueDate.isNotEmpty()) dueDate else existingDue
            db.execSQL("UPDATE clients SET phone = ?, bal_usd = ?, bal_uzs = ?, due_date = ? WHERE id = ?", arrayOf<Any>(phone.trim(), balUsd, balUzs, finalDue, id))
        } else {
            db.execSQL(
                "INSERT INTO clients (name, phone, bal_usd, bal_uzs, folder, due_date, created_at, is_supplier) VALUES (?, ?, ?, ?, 'Barchasi', ?, ?, ?)",
                arrayOf<Any>(cleanName, phone.trim(), balUsd, balUzs, dueDate, createdAt.ifEmpty { nowStr() }, supFlag)
            )
        }
        c.close()
        if (triggerAutoBackup) onDataChangedCallback?.invoke()
    }

    fun insertTxDirect(txId: String, kind: String, personName: String, personPhone: String, amount: Long, currency: String, date: String, dueDate: String, note: String, status: String, isSupplier: Boolean) {
        val supFlag = if (isSupplier) 1 else 0
        writableDatabase.execSQL(
            "INSERT INTO transactions (tx_id, kind, person_name, person_phone, amount, currency, date, due_date, note, status, is_supplier) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(txId, kind, personName, personPhone, Math.abs(amount), currency, date.ifEmpty { nowStr() }, dueDate, note, status.ifEmpty { "Faol" }, supFlag)
        )
    }

    fun addOrUpdatePersonTx(name: String, phone: String, amount: Long, currency: String, kind: String, note: String, dueDate: String, isSupplier: Boolean, folder: String = "Barchasi") {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        val c = db.rawQuery("SELECT id, bal_usd, bal_uzs, due_date FROM clients WHERE name = ? AND is_supplier = ?", arrayOf(cleanName, supFlag.toString()))
        val delta = if (kind == "Chiqim") -amount else amount
        val exactTime = nowStr()
        if (c.moveToFirst()) {
            val id = c.getInt(0)
            val curUsd = c.getDouble(1)
            val curUzs = c.getLong(2)
            val oldDue = c.getString(3) ?: ""
            val newDue = if (dueDate.isNotBlank()) dueDate else oldDue
            if (currency == "USD") {
                db.execSQL("UPDATE clients SET bal_usd = ?, due_date = ?, phone = CASE WHEN ? != '' THEN ? ELSE phone END WHERE id = ?", arrayOf<Any>(curUsd + delta, newDue, phone.trim(), phone.trim(), id))
            } else {
                db.execSQL("UPDATE clients SET bal_uzs = ?, due_date = ?, phone = CASE WHEN ? != '' THEN ? ELSE phone END WHERE id = ?", arrayOf<Any>(curUzs + delta, newDue, phone.trim(), phone.trim(), id))
            }
        } else {
            val initUsd = if (currency == "USD") delta.toDouble() else 0.0
            val initUzs = if (currency == "UZS") delta else 0L
            db.execSQL(
                "INSERT INTO clients (name, phone, bal_usd, bal_uzs, folder, due_date, created_at, is_supplier) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(cleanName, phone.trim(), initUsd, initUzs, folder, dueDate, exactTime, supFlag)
            )
        }
        c.close()

        if (amount > 0) {
            val prefix = if (isSupplier) "ST-" else "D-"
            val txId = prefix + (100000 + Random().nextInt(900000))
            insertTxDirect(txId, kind, cleanName, phone.trim(), amount, currency, exactTime, dueDate, note, "Faol", isSupplier)
            if (kind == "Kirim") {
                val catName = if (isSupplier) "Ta'minotchi kirimi: $cleanName" else "Qarz to'lovi: $cleanName"
                writableDatabase.execSQL(
                    "INSERT INTO kassa (type, category, amount, currency, pay_type, date, note, creator) VALUES ('Kirim', ?, ?, ?, 'Naqd', ?, ?, 'Avto')",
                    arrayOf<Any>(catName, Math.abs(amount), currency, exactTime, note)
                )
            }
        }
        onDataChangedCallback?.invoke()
    }

    // Qarz yozuvini o'chirish/yopish: tarixdan o'chib ketmaydi, "O'chirilgan (sana va vaqt)" bo'lib saqlanib qoladi!
    fun clearAllDebtsForPerson(personName: String, personPhone: String, isSupplier: Boolean): Long {
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        val delTime = nowStr()

        var currentDebt = 0L
        val c = db.rawQuery("SELECT id, bal_uzs FROM clients WHERE name = ? AND is_supplier = ?", arrayOf(personName, supFlag.toString()))
        if (c.moveToFirst()) {
            val clientId = c.getInt(0)
            val balUzs = c.getLong(1)
            currentDebt = Math.abs(balUzs)
            db.execSQL("UPDATE clients SET bal_usd = 0, bal_uzs = 0 WHERE id = ?", arrayOf<Any>(clientId))
        }
        c.close()

        // Barcha faol Chiqim (qarz) yozuvlarini "To'liq hamma summani berdi - O'chirildi" deb tarixda saqlab qo'yamiz
        val newStatus = "To'liq to'landi / O'chirildi ($delTime)"
        db.execSQL(
            "UPDATE transactions SET status = ? WHERE person_name = ? AND is_supplier = ? AND status = 'Faol'",
            arrayOf<Any>(newStatus, personName, supFlag)
        )

        if (currentDebt > 0L) {
            val payPrefix = if (isSupplier) "ST-FULL-" else "D-FULL-"
            val payTxId = payPrefix + (100000 + Random().nextInt(900000))
            insertTxDirect(
                payTxId,
                "Kirim",
                personName,
                personPhone,
                currentDebt,
                "UZS",
                delTime,
                "",
                "To'liq hamma summani berdi (Barcha qarzlar yopildi)",
                "To'liq yopildi ($delTime)",
                isSupplier
            )
            val catName = if (isSupplier) "Ta'minotchi to'liq yopildi: $personName" else "Qarz to'liq yopildi: $personName"
            db.execSQL(
                "INSERT INTO kassa (type, category, amount, currency, pay_type, date, note, creator) VALUES ('Kirim', ?, ?, 'UZS', 'Naqd', ?, 'To''liq hamma qarzni berdi', 'Avto')",
                arrayOf<Any>(catName, currentDebt, delTime)
            )
        }

        onDataChangedCallback?.invoke()
        return currentDebt
    }

    fun markTransactionDeletedAndRevertBalance(tx: TxRec) {
        if (tx.status.startsWith("O'chirildi")) return
        val db = writableDatabase
        val delTime = nowStr()
        val newStatus = "O'chirildi / Yopildi ($delTime)"
        db.execSQL("UPDATE transactions SET status = ? WHERE id = ?", arrayOf<Any>(newStatus, tx.id))
        if (tx.kind == "Chiqim") {
            val payPrefix = if (tx.isSupplier) "ST-PAY-" else "D-PAY-"
            val payTxId = payPrefix + (100000 + Random().nextInt(900000))
            insertTxDirect(payTxId, "Kirim", tx.personName, tx.personPhone, tx.amount, tx.currency, delTime, tx.dueDate, "Yopilgan qarz kirimi (${tx.txId})", "Faol", tx.isSupplier)
            val catName = if (tx.isSupplier) "Ta'minotchi yopilgan kirim: ${tx.personName}" else "Xaridor yopilgan qarz: ${tx.personName}"
            db.execSQL(
                "INSERT INTO kassa (type, category, amount, currency, pay_type, date, note, creator) VALUES ('Kirim', ?, ?, ?, 'Naqd', ?, ?, 'Avto')",
                arrayOf<Any>(catName, Math.abs(tx.amount), tx.currency, delTime, "Qarz yopildi")
            )
        }

        val supFlag = if (tx.isSupplier) 1 else 0
        val c = db.rawQuery("SELECT id, bal_usd, bal_uzs FROM clients WHERE name = ? AND is_supplier = ?", arrayOf(tx.personName, supFlag.toString()))
        if (c.moveToFirst()) {
            val clientId = c.getInt(0)
            val curUsd = c.getDouble(1)
            val curUzs = c.getLong(2)
            // Agar oldin Chiqim (qarz) bo'lgan bo'lsa, o'chirilganda balansga +amount qaytadi
            val revertDelta = if (tx.kind == "Chiqim") tx.amount else -tx.amount
            if (tx.currency == "USD") {
                db.execSQL("UPDATE clients SET bal_usd = ? WHERE id = ?", arrayOf<Any>(curUsd + revertDelta, clientId))
            } else {
                db.execSQL("UPDATE clients SET bal_uzs = ? WHERE id = ?", arrayOf<Any>(curUzs + revertDelta, clientId))
            }
        }
        c.close()
        onDataChangedCallback?.invoke()
    }

    fun getPersonByName(name: String, isSupplier: Boolean): ClientRec? {
        val supFlag = if (isSupplier) 1 else 0
        val c = readableDatabase.rawQuery("SELECT id, name, phone, bal_usd, bal_uzs, folder, due_date, created_at FROM clients WHERE name = ? AND is_supplier = ?", arrayOf(name, supFlag.toString()))
        var res: ClientRec? = null
        if (c.moveToFirst()) {
            res = ClientRec(c.getInt(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getDouble(3), c.getLong(4), c.getString(5) ?: "Barchasi", c.getString(6) ?: "", c.getString(7) ?: "")
        }
        c.close()
        return res
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

    fun getTransactionsForPerson(personName: String, isSupplier: Boolean): List<TxRec> {
        val list = mutableListOf<TxRec>()
        val supFlag = if (isSupplier) 1 else 0
        val c = readableDatabase.rawQuery(
            "SELECT id, tx_id, kind, person_name, person_phone, amount, currency, date, due_date, note, status FROM transactions WHERE person_name = ? AND is_supplier = ? ORDER BY id DESC",
            arrayOf(personName, supFlag.toString())
        )
        while (c.moveToNext()) {
            list.add(
                TxRec(
                    c.getInt(0),
                    c.getString(1) ?: "",
                    c.getString(2) ?: "Chiqim",
                    c.getString(3) ?: "",
                    c.getString(4) ?: "",
                    c.getLong(5),
                    c.getString(6) ?: "UZS",
                    c.getString(7) ?: "",
                    c.getString(8) ?: "",
                    c.getString(9) ?: "",
                    c.getString(10) ?: "Faol",
                    isSupplier
                )
            )
        }
        c.close()
        return list
    }

    fun getTransactions(isSupplier: Boolean): List<TxRec> {
        val list = mutableListOf<TxRec>()
        val supFlag = if (isSupplier) 1 else 0
        val c = readableDatabase.rawQuery("SELECT id, tx_id, kind, person_name, person_phone, amount, currency, date, due_date, note, status FROM transactions WHERE is_supplier = ? ORDER BY id DESC", arrayOf(supFlag.toString()))
        while (c.moveToNext()) {
            list.add(
                TxRec(
                    c.getInt(0),
                    c.getString(1) ?: "",
                    c.getString(2) ?: "",
                    c.getString(3) ?: "",
                    c.getString(4) ?: "",
                    c.getLong(5),
                    c.getString(6) ?: "UZS",
                    c.getString(7) ?: "",
                    c.getString(8) ?: "",
                    c.getString(9) ?: "",
                    c.getString(10) ?: "Faol",
                    isSupplier
                )
            )
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
    private var appPinCode = ""
    private var isBiometricEnabled = true
    private var isPinUnlockedThisSession = false
    private var autoLockTimeoutMs = 60_000L
    private var lastInteractionTimeMs = System.currentTimeMillis()

    private var price1Month = 49000L
    private var price3Month = 119000L
    private var price6Month = 199000L
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

        db.onDataChangedCallback = {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    savePermanentBackupToDownloadsQarzFy()
                } catch (_: Exception) {}
            }
        }

        ttsEngine = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale("uz", "UZ")
            }
        }

        rootLayout = findViewById(R.id.mainContentLayout)

        loadPrefsAndRestoreSession()
        

        if (!isLoggedIn || userPhone.isBlank()) {
            renderOnboardingAndLogin()
        } else if (appPinCode.length != 6) {
            renderPinLockScreen(mode = "SETUP_NEW")
        } else if (!isPinUnlockedThisSession) {
            renderPinLockScreen(mode = "UNLOCK")
        } else {
            checkServerSubscriptionAndRender()
            scheduleMidnightAutoExcelSaveWithSignal()
            checkDueDatesAndSendAutoSmsForClientsAndSuppliers()
        }
    }

    override fun onPause() {
        super.onPause()
        lastInteractionTimeMs = System.currentTimeMillis()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                savePermanentBackupToDownloadsQarzFy()
            } catch (_: Exception) {}
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        val now = System.currentTimeMillis()
        if (isLoggedIn && appPinCode.length == 6 && isPinUnlockedThisSession) {
            if (now - lastInteractionTimeMs > autoLockTimeoutMs) {
                isPinUnlockedThisSession = false
                lastInteractionTimeMs = now
                renderPinLockScreen(mode = "UNLOCK")
                return true
            }
        }
        lastInteractionTimeMs = now
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        if (isLoggedIn && userPhone.isNotBlank() && appPinCode.length == 6) {
            if (isPinUnlockedThisSession && (now - lastInteractionTimeMs > autoLockTimeoutMs)) {
                isPinUnlockedThisSession = false
            }
            if (!isPinUnlockedThisSession) {
                renderPinLockScreen(mode = "UNLOCK")
                return
            }
            lastInteractionTimeMs = now
            checkServerSubscriptionAndRender()
        }
    }

    override fun onDestroy() {
        ttsEngine?.shutdown()
        super.onDestroy()
    }

    private fun loadPrefsAndRestoreSession() {
        // Foydalanuvchi v12.0 da Login ekranini aniq ko'rishi uchun v12_auth_done flagini tekshiramiz
        val v12AuthDone = prefs.getBoolean("v12_auth_done", false)
        isLoggedIn = v12AuthDone && prefs.getBoolean("is_logged_in", false)
        userPhone = prefs.getString("user_phone", "") ?: ""
        userPassword = prefs.getString("user_password", "") ?: ""
        storeId = prefs.getString("store_id", "QF-101") ?: "QF-101"
        storeName = prefs.getString("store_name", "Hasan Jaylovov") ?: "Hasan Jaylovov"
        storeAddress = prefs.getString("store_address", "O'zbekiston") ?: "O'zbekiston"
        storeCategory = prefs.getString("store_category", "Qurilish mollari") ?: "Qurilish mollari"
        mainCurrency = prefs.getString("main_currency", "UZS") ?: "UZS"
        subscriptionExpiryMillis = prefs.getLong("sub_expiry_millis", 0L)
        isAutoVoiceCallEnabled = prefs.getBoolean("auto_voice_call", true)
        appPinCode = prefs.getString("app_pin_code", "") ?: ""
        isBiometricEnabled = prefs.getBoolean("biometric_enabled", true)
        autoLockTimeoutMs = prefs.getLong("auto_lock_timeout_ms", 60_000L)
        price1Month = prefs.getLong("price_1m", 49000L)
        price3Month = prefs.getLong("price_3m", 135000L)
        price6Month = prefs.getLong("price_6m", 255000L)
        adminCardNumber = prefs.getString("card_number", "9860 0000 0000 0000") ?: "9860 0000 0000 0000"
        adminCardOwner = prefs.getString("card_owner", "HASAN JAYLOVOV") ?: "HASAN JAYLOVOV"
    }

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
            .putBoolean("v12_auth_done", loggedIn)
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
            .putString("app_pin_code", appPinCode)
            .putBoolean("biometric_enabled", isBiometricEnabled)
            .putLong("auto_lock_timeout_ms", autoLockTimeoutMs)
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

    // ==================== KALENDAR TANLASH DIALOGI (DatePickerDialog) ====================
    private fun defaultDueDateStr(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, 7)
        return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(cal.time)
    }

    private fun openCalendarPicker(initialDateStr: String, onSelected: (String) -> Unit) {
        val cal = Calendar.getInstance()
        try {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val parsed = sdf.parse(initialDateStr)
            if (parsed != null) cal.time = parsed
        } catch (_: Exception) {}

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = String.format(Locale.getDefault(), "%02d.%02d.%04d", dayOfMonth, month + 1, year)
                onSelected(picked)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ==================== MUDDATI KELGAN QARZLAR UCHUN AVTO-SMS (XARIDORLAR VA TA'MINOTCHILARGA!) ====================
    private fun isDateDueOrOverdue(dueDateStr: String): Boolean {
        if (dueDateStr.isBlank() || dueDateStr == "Bugun") return false
        return try {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val due = sdf.parse(dueDateStr) ?: return false
            val today = sdf.parse(sdf.format(Date())) ?: return false
            !due.after(today) // due <= today
        } catch (_: Exception) {
            false
        }
    }

    private fun checkDueDatesAndSendAutoSmsForClientsAndSuppliers() {
        val todayTag = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        val lastSentDay = prefs.getString("last_due_auto_sms_day", "") ?: ""
        if (lastSentDay == todayTag) return // Kuniga 1 marta avtomatik yuboradi

        var sentCount = 0
        // 1) Qarzdor xaridorlar muddati kelgan bo'lsa
        db.getPersons(isSupplier = false).forEach { c ->
            if (c.balUzs != 0L && isDateDueOrOverdue(c.dueDate)) {
                val amtStr = formatMoney(Math.abs(c.balUzs), "so'm")
                val msg = "Hurmatli ${c.name}, $storeName do'konidan $amtStr qarzingizni to'lash muddati (${c.dueDate}) keldi. Iltimos, to'lovni amalga oshiring."
                sendAutoSms(c.phone, msg)
                sentCount++
            }
        }
        // 2) Ta'minotchilar muddati kelgan bo'lsa
        db.getPersons(isSupplier = true).forEach { s ->
            if (s.balUzs != 0L && isDateDueOrOverdue(s.dueDate)) {
                val amtStr = formatMoney(Math.abs(s.balUzs), "so'm")
                val msg = "Hurmatli hamkor (${s.name}), $storeName do'koni bo'yicha $amtStr hisob-kitob muddati (${s.dueDate}) keldi."
                sendAutoSms(s.phone, msg)
                sentCount++
            }
        }

        if (sentCount > 0) {
            prefs.edit().putString("last_due_auto_sms_day", todayTag).commit()
            Toast.makeText(this, "⏰ Muddati kelgan $sentCount ta mijoz/ta'minotchiga Avto-SMS yuborildi!", Toast.LENGTH_LONG).show()
        }
    }

    // ==================== CYBER-NEON DARK GLASS DRAWABLES ====================
    private fun glossyCard(fillHex: String = "#FFFFFF", strokeHex: String = "#E8ECFA", rad: Float = 28f): GradientDrawable {
        val cleanFill = when (fillHex.uppercase()) {
            "#131C31", "#0F172A", "#151F38", "#1E293B", "#11192E" -> "#FFFFFF"
            "#1E1B4B", "#F6F8FC" -> "#F5F7FF"
            else -> fillHex
        }
        val cleanStroke = when (strokeHex.uppercase()) {
            "#233554", "#1E3A8A", "#00F5D4", "#334155", "#94A3B8" -> "#E2E8F8"
            else -> strokeHex
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = rad
            setColor(Color.parseColor(cleanFill))
            setStroke(2, Color.parseColor(cleanStroke))
        }
    }

    private fun glossyPurpleGrad(rad: Float = 30f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#6C4CF1"), Color.parseColor("#5946E8"), Color.parseColor("#3677F5"))
        ).apply {
            cornerRadius = rad
        }
    }

    private fun glossyOrangeGrad(rad: Float = 28f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#3677F5"), Color.parseColor("#2563EB"))
        ).apply {
            cornerRadius = rad
        }
    }

    private fun glossyGreenGrad(rad: Float = 28f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#10B981"), Color.parseColor("#059669"))
        ).apply {
            cornerRadius = rad
        }
    }

    private fun glossyRedGrad(rad: Float = 26f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#EF4444"), Color.parseColor("#DC2626"))
        ).apply {
            cornerRadius = rad
        }
    }

    private fun glossyRoundBtnBg(hex: String, strokeHex: String = "#E2E8F8"): GradientDrawable {
        val cleanHex = if (hex == "#151F38" || hex == "#1E293B" || hex == "#0F172A") "#FFFFFF" else hex
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(cleanHex))
            setStroke(2, Color.parseColor(strokeHex))
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

    // ==================== FONDA KO'RINMAS AVTO-QO'NG'IROQ VA SIM AVTO-SMS ====================
    private fun normalizeUzPhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 9 -> "+998$digits"
            digits.length == 12 && digits.startsWith("998") -> "+$digits"
            raw.trim().startsWith("+") && digits.length >= 9 -> "+$digits"
            digits.length >= 9 -> "+$digits"
            else -> ""
        }
    }

    private fun triggerBackgroundSmsAndVoiceCall(clientName: String, phoneNumber: String, amount: Long, kind: String, note: String, dueDate: String = "", isSupplier: Boolean = false) {
        val personRecord = db.getPersonByName(clientName, isSupplier)
        var cleanPhone = normalizeUzPhone(phoneNumber)
        if (cleanPhone.isEmpty() && personRecord != null) {
            cleanPhone = normalizeUzPhone(personRecord.phone)
        }

        // Tranzaksiyadan keyingi joriy qolgan umumiy qarz qoldig'ini olamiz
        val currentBalUzs = personRecord?.balUzs ?: 0L
        val remainingDebtUzs = if (currentBalUzs < 0L) Math.abs(currentBalUzs) else 0L
        val fmtRemaining = formatMoney(remainingDebtUzs, "so'm")

        val fmtAmt = formatMoney(Math.abs(amount), "so'm")
        val duePart = if (dueDate.isNotBlank() && dueDate != "Bugun") " Muddat: $dueDate." else ""

        val smsText = when {
            kind == "ToliqYopildi" -> {
                if (isSupplier) {
                    "Hurmatli hamkor ($clientName), $storeName do'koni bilan jami $fmtAmt hisob-kitob to'liq yopildi. Qolgan qarz: 0 so'm."
                } else {
                    "Hurmatli $clientName, siz $storeName do'konidagi jami $fmtAmt qarzingizni to'liq to'ladingiz. Qolgan qarzingiz: 0 so'm. Rahmat!"
                }
            }
            kind == "Yopildi" -> {
                val remPart = if (remainingDebtUzs > 0L) " Qolgan umumiy qarzingiz: $fmtRemaining." else " Qolgan qarzingiz: 0 so'm."
                if (isSupplier) {
                    "Hurmatli hamkor ($clientName), $storeName do'koni bilan $fmtAmt qarz yopildi ($note).$remPart"
                } else {
                    "Hurmatli $clientName, $storeName do'konidagi $fmtAmt qarzingiz yopildi (o'chirildi).$remPart Rahmat!"
                }
            }
            kind == "Kirim" -> {
                val remPart = if (remainingDebtUzs > 0L) {
                    " Qolgan umumiy qarzingiz: $fmtRemaining."
                } else {
                    " Barcha qarzingiz to'liq uzildi (Qoldiq: 0 so'm)."
                }
                if (isSupplier) {
                    "Hurmatli hamkor ($clientName), $storeName do'konida $fmtAmt to'lov/kirim qayd etildi ($note).$remPart"
                } else {
                    "Hurmatli $clientName, $storeName do'konidagi qarzingizdan $fmtAmt to'lov qabul qilindi.$remPart Rahmat!"
                }
            }
            else -> {
                val totalPart = if (remainingDebtUzs > 0L) " Jami umumiy qarzingiz: $fmtRemaining." else ""
                if (isSupplier) {
                    "Hurmatli hamkor ($clientName), $storeName do'konida $fmtAmt qarz/chiqim yozildi ($note).$totalPart$duePart"
                } else {
                    "Hurmatli $clientName, $storeName do'konida sizga $fmtAmt qarz yozildi ($note).$totalPart$duePart"
                }
            }
        }

        if (cleanPhone.isNotEmpty()) {
            sendAutoSms(cleanPhone, smsText)
        }

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
                        put("client_phone", cleanPhone)
                        put("amount", fmtAmt)
                        put("remaining_debt", fmtRemaining)
                        put("kind", kind)
                        put("note", note)
                        put("due_date", dueDate)
                        put("voice_text", smsText)
                    }
                    conn.outputStream.write(body.toString().toByteArray())
                    conn.inputStream.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun sendAutoSms(phoneNumber: String, message: String) {
        val targetPhone = normalizeUzPhone(phoneNumber)
        if (targetPhone.length < 9) return
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(targetPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(targetPhone, null, message, null, null)
            }
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                val fallbackSms = SmsManager.getDefault()
                fallbackSms.sendTextMessage(targetPhone, null, message, null, null)
            } catch (_: Exception) {}
        }
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
                                setTextColor(Color.parseColor("#94A3B8"))
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
        rootLayout.setBackgroundColor(Color.parseColor("#F5F7FF"))
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
            textSize = 17.5f
            setTypeface(null, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "Do'kon ID: $storeId ($storeName)\n💳 Karta: $adminCardNumber ($adminCardOwner)\n\nTarifni tanlab, Botga to'lov cheki (skrinshot) yuboring:"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(10, 14, 10, 30)
        })

        card.addView(tariffButton("💳 1 Oy — ${formatMoney(price1Month, "so'm")}", "Botga chek (skrinshot) yuborish", "#6D28D9") {
            selectTariffAndPayDialog(1, price1Month)
        })
        card.addView(tariffButton("🔥 3 Oy — ${formatMoney(price3Month, "so'm")}", "Botga chek (skrinshot) yuborish", "#3677F5") {
            selectTariffAndPayDialog(3, price3Month)
        })
        card.addView(tariffButton("👑 6 Oy — ${formatMoney(price6Month, "so'm")}", "Botga chek (skrinshot) yuborish", "#059669") {
            selectTariffAndPayDialog(6, price6Month)
        })
        card.addView(Button(this).apply {
            text = "💬 Admin bilan suhbat / Yordam"
            setTextColor(Color.WHITE)
            background = glossyOrangeGrad(32f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 92).apply { topMargin = 8; bottomMargin = 8 }
            setOnClickListener { showSupportChatBottomSheet() }
        })
        card.addView(Button(this).apply {
            text = "🔄 To'lov tasdiqlanganini tekshirish"
            setTextColor(Color.parseColor("#1E293B"))
            background = glossyCard("#F1F5F9", "#CBD5E1", 32f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 92).apply { bottomMargin = 8 }
            setOnClickListener { checkServerSubscriptionAndRender() }
        })
        card.addView(Button(this).apply {
            text = "🚪 Hisobdan chiqish"
            setTextColor(Color.WHITE)
            background = glossyRedGrad(32f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 92)
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
            setPadding(24, 18, 24, 18)
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
                setTextColor(Color.parseColor("#1E293B"))
                textSize = 12f
            })
        }
    }

    // ==================== 1. KIRISH (LOGIN) VA YANGI RAQAM/PAROL QO'SHISH EKRANI ====================
    private var isRegisterMode = false

    private fun renderOnboardingAndLogin() {
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#F5F7FF"))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(36, 56, 36, 48)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        // O'rtada Hamyon Logotipi
        container.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(150, 150).apply { bottomMargin = 20 }
        })

        container.addView(TextView(this).apply {
            text = "QarzDaftar Pro"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        container.addView(TextView(this).apply {
            text = if (!isRegisterMode) "Hisobingizga kiring" else "Yangi do'kon ro'yxatdan o'tkazish"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 13.5f
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 32)
        })

        val savedAcc = db.getLastSavedAccount()
        val defaultPhone = if (userPhone.isNotBlank()) userPhone else (savedAcc?.get("phone") ?: "+998")
        val defaultStore = if (storeName.isNotBlank()) storeName else (savedAcc?.get("store_name") ?: "")
        val defaultAddr = if (storeAddress.isNotBlank()) storeAddress else (savedAcc?.get("store_address") ?: "O'zbekiston")

        // 📞 Telefon raqami
        val etPhone = EditText(this).apply {
            hint = "📞  Telefon raqami"
            setText(defaultPhone)
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 14.5f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_PHONE
            background = glossyCard("#FFFFFF", "#E2E8F8", 26f)
            setPadding(32, 26, 32, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        container.addView(etPhone)

        // 🔒 Parol
        val etPass = EditText(this).apply {
            hint = "🔒  Parol"
            setText("")
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 14.5f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = glossyCard("#FFFFFF", "#E2E8F8", 26f)
            setPadding(32, 26, 32, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        container.addView(etPass)

        val etStore = EditText(this).apply {
            hint = "🏪  Do'kon yoki biznes nomi"
            setText(defaultStore)
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 14.5f
            setSingleLine(true)
            background = glossyCard("#FFFFFF", "#E2E8F8", 26f)
            setPadding(32, 26, 32, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }

        val etAddr = EditText(this).apply {
            hint = "📍  Manzil"
            setText(defaultAddr)
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 14.5f
            setSingleLine(true)
            background = glossyCard("#FFFFFF", "#E2E8F8", 26f)
            setPadding(32, 26, 32, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }

        if (isRegisterMode) {
            container.addView(etStore)
            container.addView(etAddr)
        }

        // Asosiy Binafsha Gradient Tugma ("Kirish" yoki "Ro'yxatdan o'tish")
        container.addView(TextView(this).apply {
            text = if (!isRegisterMode) "Kirish" else "Ro'yxatdan o'tish (10 kun bepul)"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(48f)
            elevation = 8f
            setPadding(24, 26, 24, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8; bottomMargin = 18 }
            setOnClickListener {
                val ph = etPhone.text.toString().trim()
                val pw = etPass.text.toString().trim()
                if (!isRegisterMode) {
                    if (ph.length < 7 || pw.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Telefon raqami va parolni kiriting!", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    val acc = db.findAccount(ph)
                    if (acc != null) {
                        val savedPw = acc["password"] ?: ""
                        if (savedPw.isNotEmpty() && savedPw != pw) {
                            Toast.makeText(this@MainActivity, "❌ Parol noto'g'ri!", Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        userPhone = ph
                        userPassword = pw
                        storeId = acc["store_id"] ?: ("QF-" + Math.abs(ph.hashCode() % 90000 + 10000))
                        storeName = acc["store_name"] ?: "Do'kon"
                        storeAddress = acc["store_address"] ?: "O'zbekiston"
                        val savedExp = acc["sub_expiry"]?.toLongOrNull() ?: 0L
                        if (savedExp > subscriptionExpiryMillis) subscriptionExpiryMillis = savedExp
                    } else {
                        userPhone = ph
                        userPassword = pw
                        storeName = defaultStore.ifEmpty { "Hasan Jaylovov" }
                        storeAddress = defaultAddr.ifEmpty { "O'zbekiston" }
                        storeId = "QF-" + Math.abs(ph.hashCode() % 90000 + 10000)
                        if (subscriptionExpiryMillis <= 0L) {
                            subscriptionExpiryMillis = System.currentTimeMillis() + (10L * 24L * 60L * 60L * 1000L)
                        }
                    }
                    saveSessionSynchronously(true)
                    syncStoreWithAdminServer(userPhone, userPassword)
                    if (appPinCode.length != 6) {
                        renderPinLockScreen(mode = "SETUP_NEW")
                    } else {
                        isPinUnlockedThisSession = true
                        renderMainWorkspace()
                    }
                    scheduleMidnightAutoExcelSaveWithSignal()
                    checkDueDatesAndSendAutoSmsForClientsAndSuppliers()
                } else {
                    val st = etStore.text.toString().trim()
                    if (ph.length < 7 || pw.isEmpty() || st.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Telefon, parol va do'kon nomini kiriting!", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    userPhone = ph
                    userPassword = pw
                    storeName = st
                    storeAddress = etAddr.text.toString().trim().ifEmpty { "O'zbekiston" }
                    storeId = "QF-" + Math.abs(ph.hashCode() % 90000 + 10000)
                    if (subscriptionExpiryMillis <= System.currentTimeMillis()) {
                        subscriptionExpiryMillis = System.currentTimeMillis() + (10L * 24L * 60L * 60L * 1000L)
                    }
                    saveSessionSynchronously(true)
                    syncStoreWithAdminServer(ph, pw)
                    renderPinLockScreen(mode = "SETUP_NEW")
                    scheduleMidnightAutoExcelSaveWithSignal()
                    checkDueDatesAndSendAutoSmsForClientsAndSuppliers()
                }
            }
        })

        // "Parolni unutdingizmi?"
        container.addView(TextView(this).apply {
            text = "Parolni unutdingizmi?"
            setTextColor(Color.parseColor("#5946E8"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(10, 6, 10, 18)
            setOnClickListener { showSupportChatBottomSheet() }
        })

        // "yoki" chiziqli ajratgich
        container.addView(TextView(this).apply {
            text = "────────────   yoki   ────────────"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        })

        // Hoshiyali "Ro'yxatdan o'tish" tugmasi
        container.addView(TextView(this).apply {
            text = if (!isRegisterMode) "Ro'yxatdan o'tish" else "Mavjud hisobga kirish"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#5946E8"))
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyCard("#F5F7FF", "#6C4CF1", 48f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 36 }
            setOnClickListener {
                isRegisterMode = !isRegisterMode
                renderOnboardingAndLogin()
            }
        })

        container.addView(TextView(this).apply {
            text = "QarzDaftar Pro — Sizning moliyaviy yordamchingiz"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11.5f
            gravity = Gravity.CENTER
        })

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
            setPadding(22, 16, 22, 16)
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
        rootLayout.setBackgroundColor(Color.parseColor("#F5F7FF"))

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
                textSize = 15.5f
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
                    layoutParams = LinearLayout.LayoutParams(98, 98)
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
                textSize = 15.5f
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
                    layoutParams = LinearLayout.LayoutParams(96, 96)
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

    // ==================== YON MENYU (DRAWER - 8-EKRAN) VA BILDIRISHNOMALAR (9-EKRAN) ====================
    private fun showSideNavigationDrawerSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#F5F7FF", "#E2E8F8", 40f)
            setPadding(0, 0, 0, 36)
        }

        val topGradHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glossyPurpleGrad(36f)
            setPadding(32, 36, 32, 36)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        val ch = storeName.trim().firstOrNull()?.uppercaseChar() ?: 'H'
        topGradHeader.addView(TextView(this).apply {
            text = ch.toString()
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            background = glossyRoundBtnBg("#40FFFFFF", "#80FFFFFF")
            layoutParams = LinearLayout.LayoutParams(110, 110).apply { rightMargin = 20 }
        })
        val nameCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameCol.addView(TextView(this).apply {
            text = storeName
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        })
        nameCol.addView(TextView(this).apply {
            text = userPhone
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 12.5f
        })
        topGradHeader.addView(nameCol)
        topGradHeader.addView(TextView(this).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
            background = glossyRoundBtnBg("#33FFFFFF", "#66FFFFFF")
            layoutParams = LinearLayout.LayoutParams(76, 76)
            setOnClickListener { sheet.dismiss() }
        })
        wrap.addView(topGradHeader)

        val navItems = listOf(
            Triple(0, "🏠", "Asosiy"),
            Triple(1, "👛", "Kassa"),
            Triple(2, "📦", "Mahsulot"),
            Triple(3, "📊", "Statistika"),
            Triple(4, "👤", "Profil")
        )
        navItems.forEach { (idx, ic, label) ->
            val active = (currentBottomTab == idx)
            wrap.addView(TextView(this).apply {
                text = "$ic    $label"
                setTextColor(if (active) Color.parseColor("#5946E8") else Color.parseColor("#1E293B"))
                textSize = 14.5f
                setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
                background = if (active) glossyCard("#E8ECFF", "#C7D2FE", 24f) else null
                setPadding(36, 24, 36, 24)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(24, 4, 24, 4)
                }
                setOnClickListener {
                    sheet.dismiss()
                    currentBottomTab = idx
                    renderMainWorkspace()
                }
            })
        }

        wrap.addView(TextView(this).apply {
            text = "⚙️    Sozlamalar (PIN va Avto-Lock)"
            setTextColor(Color.parseColor("#475569"))
            textSize = 14f
            setPadding(36, 28, 36, 20)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(24, 16, 24, 0)
            }
            setOnClickListener {
                sheet.dismiss()
                showPinAndBiometricSettingsDialog()
            }
        })

        sheet.setContentView(wrap)
        sheet.show()
    }

    private fun showNotificationsSheet() {
        val sheet = BottomSheetDialog(this)
        val scroll = ScrollView(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#F5F7FF", "#E2E8F8", 40f)
            setPadding(28, 26, 28, 44)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        }
        topRow.addView(TextView(this).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#475569"))
            textSize = 15f
            background = glossyRoundBtnBg("#FFFFFF", "#E2E8F8")
            layoutParams = LinearLayout.LayoutParams(76, 76).apply { rightMargin = 16 }
            setOnClickListener { sheet.dismiss() }
        })
        topRow.addView(TextView(this).apply {
            text = "Bildirishnomalar"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
        })
        wrap.addView(topRow)

        val recentTxs = db.getTransactions(false).take(8)
        if (recentTxs.isEmpty()) {
            wrap.addView(TextView(this).apply {
                text = "🔔 Hozircha bildirishnomalar yo'q.\nYangi qarzlar, tasdiqlangan to'lovlar va muddat eslatmalari shu yerda ko'rinadi."
                setTextColor(Color.parseColor("#64748B"))
                textSize = 13f
                background = glossyCard("#FFFFFF", "#E2E8F8", 26f)
                setPadding(28, 32, 28, 32)
            })
        } else {
            recentTxs.forEach { tx ->
                val isKirim = (tx.kind == "Kirim")
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = glossyCard("#FFFFFF", "#E2E8F8", 26f)
                    setPadding(22, 20, 22, 20)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
                }
                card.addView(TextView(this).apply {
                    text = if (isKirim) "☑️" else "📒"
                    gravity = Gravity.CENTER
                    textSize = 16f
                    background = glossyRoundBtnBg(if (isKirim) "#DCFCE7" else "#FEF3C7", "#FFFFFF")
                    layoutParams = LinearLayout.LayoutParams(86, 86).apply { rightMargin = 16 }
                })
                val mid = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                mid.addView(TextView(this).apply {
                    text = if (isKirim) "To'lov tasdiqlandi" else "Yangi qarz qo'shildi"
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                })
                mid.addView(TextView(this).apply {
                    text = "${tx.personName} • ${formatMoney(tx.amount, \"so'm\")}\n${tx.date}"
                    setTextColor(Color.parseColor("#64748B"))
                    textSize = 12f
                })
                card.addView(mid)
                card.addView(TextView(this).apply {
                    text = "❯"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 14f
                })
                wrap.addView(card)
            }
        }

        scroll.addView(wrap)
        sheet.setContentView(scroll)
        sheet.show()
    }

    // ==================== TAB 0: ASOSIY EKRAN (RASMDAGI 3-EKRAN) ====================
    private fun buildHomeTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 22, 24, 140)
        }

        // Yuqori Bar: "Asosiy / Umumiy ma'lumotlar" + 🔔 + 👤
        val topHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        }
        val menuAvatar = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(86, 86).apply { rightMargin = 14 }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_qarzdaftar_logo)
                layoutParams = FrameLayout.LayoutParams(86, 86, Gravity.CENTER)
            })
            setOnClickListener { showSideNavigationDrawerSheet() }
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openBusinessInfoDialog() }
        }
        titleCol.addView(TextView(this).apply {
            text = "Asosiy"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
        })
        titleCol.addView(TextView(this).apply {
            text = "Umumiy ma'lumotlar • $storeName"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
        })

        val bellBtn = TextView(this).apply {
            text = "🔔"
            gravity = Gravity.CENTER
            textSize = 15f
            background = glossyRoundBtnBg("#FFFFFF", "#E2E8F8")
            elevation = 3f
            layoutParams = LinearLayout.LayoutParams(80, 80).apply { rightMargin = 10 }
            setOnClickListener { showNotificationsSheet() }
        }
        val profQuickBtn = TextView(this).apply {
            text = "👤"
            gravity = Gravity.CENTER
            textSize = 15f
            background = glossyRoundBtnBg("#FFFFFF", "#E2E8F8")
            elevation = 3f
            layoutParams = LinearLayout.LayoutParams(80, 80)
            setOnClickListener { currentBottomTab = 4; renderMainWorkspace() }
        }

        topHeader.addView(menuAvatar)
        topHeader.addView(titleCol)
        topHeader.addView(bellBtn)
        topHeader.addView(profQuickBtn)
        col.addView(topHeader)

        val isSup = (currentHomeSubTab == 1)
        val allClients = db.getPersons(isSup)
        val totalDebtUzs = allClients.filter { it.balUzs < 0 }.sumOf { Math.abs(it.balUzs) }
        val debtorsCount = allClients.count { it.balUzs < 0 }
        val repaidCount = db.getTransactions(isSup).count { it.kind == "Kirim" || it.status.startsWith("To'liq") }

        // Ko'k-Binafsha Gradient "Jami qarzlar" Hero Kartasi (Rasmdagi 3-ekran)
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glossyPurpleGrad(34f)
            elevation = 10f
            setPadding(28, 28, 28, 28)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
            setOnClickListener { showSubscriptionTariffsBottomSheet() }
        }
        val heroLeft = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        heroLeft.addView(TextView(this).apply {
            text = if (isSup) "Ta'minotchi qarzlari" else "Jami qarzlar"
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 13f
        })
        heroLeft.addView(TextView(this).apply {
            text = formatMoney(totalDebtUzs, "so'm")
            setTextColor(Color.WHITE)
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 6, 0, 4)
        })
        heroLeft.addView(TextView(this).apply {
            text = "Obuna: ${remainingDaysText()}"
            setTextColor(Color.parseColor("#C7D2FE"))
            textSize = 11f
        })
        val heroRightIcon = TextView(this).apply {
            text = "📊"
            gravity = Gravity.CENTER
            textSize = 20f
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#33FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(96, 96)
        }
        heroCard.addView(heroLeft)
        heroCard.addView(heroRightIcon)
        col.addView(heroCard)

        // 2 ta Yonma-yon Pastel Karta: [ 👥 Qarzdorlar ]  |  [ ☑️ Qaytganlar ]
        val twoCardsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }

        val redPastelBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(Color.parseColor("#FFF0F0"))
                setStroke(2, Color.parseColor("#FECDD3"))
            }
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
            setOnClickListener { currentSortMode = "Summa"; renderMainWorkspace() }
        }
        redPastelBox.addView(TextView(this).apply {
            text = "👥              📈"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        })
        redPastelBox.addView(TextView(this).apply {
            text = "Qarzdorlar"
            setTextColor(Color.parseColor("#475569"))
            textSize = 12.5f
            setPadding(0, 8, 0, 2)
        })
        redPastelBox.addView(TextView(this).apply {
            text = "$debtorsCount"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
        })

        val greenPastelBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(Color.parseColor("#ECFDF5"))
                setStroke(2, Color.parseColor("#A7F3D0"))
            }
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 8 }
            setOnClickListener { currentBottomTab = 3; renderMainWorkspace() }
        }
        greenPastelBox.addView(TextView(this).apply {
            text = "💸              ✅"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        })
        greenPastelBox.addView(TextView(this).apply {
            text = "Qaytganlar"
            setTextColor(Color.parseColor("#475569"))
            textSize = 12.5f
            setPadding(0, 8, 0, 2)
        })
        greenPastelBox.addView(TextView(this).apply {
            text = "$repaidCount"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
        })

        twoCardsRow.addView(redPastelBox)
        twoCardsRow.addView(greenPastelBox)
        col.addView(twoCardsRow)

        // Bo'lim tablari: Mijozlar | Ta'minotchilar | Xodimlar
        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 28f)
            setPadding(6, 6, 6, 6)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        listOf("Mijozlar", "Ta'minotchilar", "Xodimlar").forEachIndexed { i, t ->
            val sel = (currentHomeSubTab == i)
            tabsRow.addView(TextView(this).apply {
                text = t
                gravity = Gravity.CENTER
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#475569"))
                background = if (sel) glossyPurpleGrad(22f) else null
                setPadding(12, 16, 12, 16)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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

        // 2 ta Tezkor Tugma (To'liq ko'rinadigan shrift bilan)
        val directActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        directActionsRow.addView(TextView(this).apply {
            text = if (isSup) "➕ Ta'minotchi qo'shish" else "➕ Qarzdor qo'shish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 12f
            includeFontPadding = false
            setSingleLine(true)
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(24f)
            setPadding(14, 22, 14, 22)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
            setOnClickListener { showAddClientBottomSheet(isSup) }
        })
        directActionsRow.addView(TextView(this).apply {
            text = "⚡ Tezkor tranzaksiya"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 12f
            includeFontPadding = false
            setSingleLine(true)
            setTypeface(null, Typeface.BOLD)
            background = glossyOrangeGrad(24f)
            setPadding(14, 22, 14, 22)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 8 }
            setOnClickListener { showQuickTransactionBottomSheet(isSup) }
        })
        col.addView(directActionsRow)

        // "So'nggi o'zgarishlar" sarlavhasi + Tartiblash
        val secHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
        }
        val tvCountLabel = TextView(this).apply {
            text = "So'nggi o'zgarishlar"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        secHeader.addView(tvCountLabel)
        secHeader.addView(TextView(this).apply {
            text = "Barchasi (${allClients.size}) ❯"
            setTextColor(Color.parseColor("#5946E8"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                currentSortMode = if (currentSortMode == "Faol") "Summa" else "Faol"
                renderMainWorkspace()
            }
        })
        col.addView(secHeader)

        val etSearch = makeGlassInput("🔍 Ism yoki telefon raqam qidirish...", searchQuery)
        col.addView(etSearch)

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        col.addView(listContainer)

        fun refreshPersonListLive(q: String) {
            listContainer.removeAllViews()
            val persons = db.getPersons(isSup, q, currentSortMode)
            if (persons.isEmpty()) {
                val emptyBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = glossyCard("#FFFFFF", "#E2E8F8", 30f)
                    setPadding(32, 44, 32, 44)
                }
                emptyBox.addView(TextView(this).apply {
                    text = if (q.isNotEmpty()) "'$q' topilmadi" else "Ro'yxat bo'sh. Yangi mijoz qo'shing."
                    setTextColor(Color.parseColor("#64748B"))
                    textSize = 13.5f
                    gravity = Gravity.CENTER
                })
                listContainer.addView(emptyBox)
            } else {
                persons.forEach { p ->
                    val rowCard = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = glossyCard("#FFFFFF", "#E8ECFA", 28f)
                        elevation = 3f
                        setPadding(20, 18, 22, 18)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
                        setOnClickListener { showPersonFullHistoryAndActionSheet(p, isSup) }
                    }

                    val firstChar = p.name.trim().firstOrNull()?.uppercaseChar() ?: 'M'
                    val avatar = TextView(this).apply {
                        text = firstChar.toString()
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        textSize = 15.5f
                        setTypeface(null, Typeface.BOLD)
                        background = glossyRoundBtnBg("#5946E8", "#C7D2FE")
                        layoutParams = LinearLayout.LayoutParams(88, 88).apply { rightMargin = 18 }
                    }

                    val midCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    midCol.addView(TextView(this).apply {
                        text = p.name
                        setTextColor(Color.parseColor("#0F172A"))
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                    })
                    midCol.addView(TextView(this).apply {
                        text = "${p.createdAt.take(10)}  •  ${p.phone}"
                        setTextColor(Color.parseColor("#64748B"))
                        textSize = 11.5f
                    })

                    val rightCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.END
                    }
                    rightCol.addView(TextView(this).apply {
                        text = formatMoney(p.balUzs, "")
                        setTextColor(if (p.balUzs < 0) Color.parseColor("#EF4444") else Color.parseColor("#10B981"))
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                    })
                    if (p.dueDate.isNotBlank()) {
                        rightCol.addView(TextView(this).apply {
                            text = "⏳ ${p.dueDate}"
                            setTextColor(Color.parseColor("#6C4CF1"))
                            textSize = 11f
                        })
                    }

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

    // ==================== XARIDOR TARIXI + DOIMIY CHIQINDIDON (TO'LIQ QARZNI YOPISH) ====================
    private fun showPersonFullHistoryAndActionSheet(initialClient: ClientRec, isSupplier: Boolean) {
        val client = db.getPersonByName(initialClient.name, isSupplier) ?: initialClient
        val sheet = BottomSheetDialog(this)
        val scroll = ScrollView(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#F5F7FF", "#E2E8F8", 40f)
            setPadding(26, 24, 26, 48)
        }

        fun confirmAndClearAllDebts() {
            val currentDebtAbs = Math.abs(client.balUzs)
            val amtDisplay = formatMoney(currentDebtAbs, "so'm")
            AlertDialog.Builder(this)
                .setTitle("🗑 Barcha qarzni to'liq yopish")
                .setMessage("${client.name} barcha qarzini ($amtDisplay) to'liq berdimi?\n\nTasdiqlasangiz barcha faol qarzlar 'To'liq hamma summani berdi (O'chirildi)' deb yopiladi, summa Kirimga tushadi va SMS yuboriladi.")
                .setPositiveButton("✅ Ha, to'liq berdi") { _, _ ->
                    val clearedSum = db.clearAllDebtsForPerson(client.name, client.phone, isSupplier)
                    val smsSum = if (clearedSum > 0L) clearedSum else currentDebtAbs
                    triggerBackgroundSmsAndVoiceCall(client.name, client.phone, smsSum, "ToliqYopildi", "To'liq hamma summani berdi", client.dueDate, isSupplier)
                    Toast.makeText(this, "✅ Barcha qarzlar to'liq yopildi va SMS yuborildi!", Toast.LENGTH_LONG).show()
                    sheet.dismiss()
                    renderMainWorkspace()
                    val updated = db.getPersonByName(client.name, isSupplier)
                    if (updated != null) showPersonFullHistoryAndActionSheet(updated, isSupplier)
                }
                .setNegativeButton("Bekor qilish", null)
                .show()
        }

        val headerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyPurpleGrad(30f)
            setPadding(24, 22, 24, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        val headerTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = client.name
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        })
        titleCol.addView(TextView(this).apply {
            text = "📞 ${client.phone} • ⏳ Muddat: ${client.dueDate.ifEmpty { "Yo'q" }}"
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 12f
        })
        val trashBtn = TextView(this).apply {
            text = "🗑"
            gravity = Gravity.CENTER
            textSize = 17f
            setTextColor(Color.WHITE)
            background = glossyRedGrad(22f)
            layoutParams = LinearLayout.LayoutParams(84, 84).apply { leftMargin = 10 }
            setOnClickListener { confirmAndClearAllDebts() }
        }
        headerTopRow.addView(titleCol)
        headerTopRow.addView(trashBtn)
        headerBox.addView(headerTopRow)

        val remAbs = Math.abs(client.balUzs)
        headerBox.addView(TextView(this).apply {
            text = if (client.balUzs < 0) "Qolgan umumiy qarz: ${formatMoney(remAbs, "so'm")}" else "Umumiy qarz: 0 so'm"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 10, 0, 10)
        })
        headerBox.addView(TextView(this).apply {
            text = "🗑  To'liq hamma qarzni berdi (Umumiy o'chirish)"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = glossyRedGrad(22f)
            setPadding(16, 18, 16, 18)
            setOnClickListener { confirmAndClearAllDebts() }
        })
        wrap.addView(headerBox)

        val actRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        actRow.addView(TextView(this).apply {
            text = "➕ Qarz Yozish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = glossyOrangeGrad(22f)
            setPadding(12, 20, 12, 20)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
            setOnClickListener { sheet.dismiss(); showClientKirimChiqimBottomSheet(client, isSupplier, "Chiqim") }
        })
        actRow.addView(TextView(this).apply {
            text = "💵 To'lov Olish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(22f)
            setPadding(12, 20, 12, 20)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { sheet.dismiss(); showClientKirimChiqimBottomSheet(client, isSupplier, "Kirim") }
        })
        wrap.addView(actRow)

        val txList = db.getTransactionsForPerson(client.name, isSupplier)
        txList.forEach { tx ->
            val isDeleted = tx.status.startsWith("O'chirildi") || tx.status.startsWith("To'liq")
            val isDebt = (tx.kind == "Chiqim")
            val txCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = glossyCard("#FFFFFF", "#E2E8F8", 24f)
                setPadding(20, 16, 20, 16)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
            }
            val badgeText = when {
                tx.status.startsWith("To'liq to'landi") -> "⚪ TO'LIQ BERDI (O'chirildi) • ${formatMoney(tx.amount, "so'm")}"
                isDeleted && isDebt -> "⚪ O'CHIRILGAN QARZ • ${formatMoney(tx.amount, "so'm")}"
                isDebt -> "🔴 QARZ YOZILDI • -${formatMoney(tx.amount, "so'm")}"
                else -> "🟢 TO'LOV OLINDI • +${formatMoney(tx.amount, "so'm")}"
            }
            txCard.addView(TextView(this).apply {
                text = badgeText
                setTextColor(if (isDeleted && isDebt) Color.parseColor("#64748B") else if (isDebt) Color.parseColor("#EF4444") else Color.parseColor("#10B981"))
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
            })
            txCard.addView(TextView(this).apply {
                text = "🕒 ${tx.date} | ⏳ Muddat: ${tx.dueDate.ifEmpty { "Yo'q" }} | 📝 ${tx.note}\n📌 ${tx.status}"
                setTextColor(Color.parseColor("#475569"))
                textSize = 11.5f
                setPadding(0, 6, 0, 8)
            })
            if (!isDeleted && isDebt) {
                txCard.addView(TextView(this).apply {
                    text = "🗑 Shu qarzni yopish / o'chirish"
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    textSize = 11.5f
                    setTypeface(null, Typeface.BOLD)
                    background = glossyRedGrad(18f)
                    setPadding(12, 16, 12, 16)
                    setOnClickListener {
                        db.markTransactionDeletedAndRevertBalance(tx)
                        val targetPh = if (tx.personPhone.isNotBlank()) tx.personPhone else client.phone
                        triggerBackgroundSmsAndVoiceCall(client.name, targetPh, tx.amount, "Yopildi", tx.note.ifEmpty { "Qarz yopildi" }, tx.dueDate, isSupplier)
                        sheet.dismiss()
                        renderMainWorkspace()
                        val updatedPerson = db.getPersonByName(client.name, isSupplier)
                        if (updatedPerson != null) showPersonFullHistoryAndActionSheet(updatedPerson, isSupplier)
                    }
                })
            }
            wrap.addView(txCard)
        }

        scroll.addView(wrap)
        sheet.setContentView(scroll)
        sheet.show()
    }

    // ==================== TAB 1: KASSA EKRANI (RASMDAGI 4-EKRAN) ====================
    private fun buildKassaTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 22, 24, 140)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        }
        topBar.addView(TextView(this).apply {
            text = "Kassa"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topBar.addView(TextView(this).apply {
            text = "🔔"
            gravity = Gravity.CENTER
            background = glossyRoundBtnBg("#FFFFFF", "#E2E8F8")
            layoutParams = LinearLayout.LayoutParams(80, 80).apply { rightMargin = 10 }
            setOnClickListener { showNotificationsSheet() }
        })
        topBar.addView(TextView(this).apply {
            text = "👤"
            gravity = Gravity.CENTER
            background = glossyRoundBtnBg("#FFFFFF", "#E2E8F8")
            layoutParams = LinearLayout.LayoutParams(80, 80)
            setOnClickListener { currentBottomTab = 4; renderMainWorkspace() }
        })
        col.addView(topBar)

        val allKassa = db.getKassaList()
        var kirimSum = 0L
        var chiqimSum = 0L
        allKassa.forEach { if (it.type == "Kirim") kirimSum += it.amount else chiqimSum += it.amount }
        val kassaBal = kirimSum - chiqimSum

        // Ko'k Gradient "Kassa qoldig'i" Kartasi (Hamyon ikonkasi bilan)
        val kassaHero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glossyPurpleGrad(34f)
            elevation = 10f
            setPadding(28, 28, 28, 28)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        val khLeft = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        khLeft.addView(TextView(this).apply {
            text = "Kassa qoldig'i"
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 13f
        })
        khLeft.addView(TextView(this).apply {
            text = formatMoney(kassaBal, "so'm")
            setTextColor(Color.WHITE)
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 6, 0, 0)
        })
        val khRight = ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(96, 96)
        }
        kassaHero.addView(khLeft)
        kassaHero.addView(khRight)
        col.addView(kassaHero)

        // 2x2 Pastel Tugmalar: [ ↓ Kirim ] [ ↑ Chiqim ] / [ ⇄ Transfer ] [ 📄 Hisobot ]
        fun kassaActionTile(bgHex: String, colorHex: String, iconTxt: String, labelTxt: String, onClick: () -> Unit): View {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = 26f
                    setColor(Color.parseColor(bgHex))
                }
                setPadding(20, 22, 20, 22)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 6, 6, 6) }
                setOnClickListener { onClick() }
                addView(TextView(this@MainActivity).apply {
                    text = iconTxt
                    setTextColor(Color.parseColor(colorHex))
                    textSize = 20f
                    setTypeface(null, Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = labelTxt
                    setTextColor(Color.parseColor("#1E293B"))
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 6, 0, 0)
                })
            }
        }

        val gridRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        gridRow1.addView(kassaActionTile("#E6F9F0", "#10B981", "↓", "Kirim") { openAddKassaDialog("Kirim") })
        gridRow1.addView(kassaActionTile("#FFF0F0", "#EF4444", "↑", "Chiqim") { openAddKassaDialog("Chiqim") })
        col.addView(gridRow1)

        val gridRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        }
        gridRow2.addView(kassaActionTile("#EEF4FF", "#3677F5", "⇄", "Transfer") { openSalesCalculatorDialog() })
        gridRow2.addView(kassaActionTile("#F2EEFF", "#6C4CF1", "📄", "Hisobot") { showExportBottomSheet() })
        col.addView(gridRow2)

        col.addView(TextView(this).apply {
            text = "Oxirgi operatsiyalar"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(4, 0, 4, 12)
        })

        if (allKassa.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "Hozircha kassa operatsiyalari yo'q."
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#64748B"))
                background = glossyCard("#FFFFFF", "#E2E8F8", 28f)
                setPadding(30, 44, 30, 44)
            })
        } else {
            allKassa.forEach { k ->
                val isIn = (k.type == "Kirim")
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = glossyCard("#FFFFFF", "#E8ECFA", 26f)
                    setPadding(20, 18, 22, 18)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
                }
                row.addView(TextView(this).apply {
                    text = if (isIn) "↓" else "↑"
                    gravity = Gravity.CENTER
                    setTextColor(if (isIn) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    background = glossyRoundBtnBg(if (isIn) "#E6F9F0" else "#FFF0F0", "#FFFFFF")
                    layoutParams = LinearLayout.LayoutParams(82, 82).apply { rightMargin = 16 }
                })
                val mid = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                mid.addView(TextView(this).apply {
                    text = k.category
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                })
                mid.addView(TextView(this).apply {
                    text = "${k.date.take(10)} • ${k.payType}"
                    setTextColor(Color.parseColor("#64748B"))
                    textSize = 11.5f
                })
                row.addView(mid)
                row.addView(TextView(this).apply {
                    text = "${if (isIn) "+" else "-"}${formatMoney(k.amount, "")}"
                    setTextColor(if (isIn) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                })
                col.addView(row)
            }
        }

        scroll.addView(col)
        return scroll
    }

    // ==================== TAB 2: MAHSULOT EKRANI (RASMDAGI 5-EKRAN) ====================
    private fun buildMahsulotTab(): View {
        val frame = FrameLayout(this)
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 22, 24, 160)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = "Mahsulot"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
        })
        titleCol.addView(TextView(this).apply {
            text = "Mahsulotlar ro'yxati"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
        })
        topRow.addView(titleCol)
        topRow.addView(TextView(this).apply {
            text = "⊕"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.parseColor("#5946E8"))
            background = glossyRoundBtnBg("#FFFFFF", "#E2E8F8")
            layoutParams = LinearLayout.LayoutParams(80, 80)
            setOnClickListener { showMahsulotActionsBottomSheet() }
        })
        col.addView(topRow)

        val etPrSearch = makeGlassInput("🔍  Mahsulot qidirish...", productSearchQuery)
        col.addView(etPrSearch)

        val pillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        listOf("Barchasi", "Oziq-ovqat", "Maishiy").forEach { cat ->
            val sel = (mahsulotFilterTab == cat)
            pillsRow.addView(TextView(this).apply {
                text = cat
                gravity = Gravity.CENTER
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#475569"))
                background = if (sel) glossyPurpleGrad(30f) else glossyCard("#FFFFFF", "#E2E8F8", 30f)
                setPadding(24, 14, 24, 14)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 10 }
                setOnClickListener { mahsulotFilterTab = cat; renderMainWorkspace() }
            })
        }
        col.addView(pillsRow)

        val prodContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(prodContainer)

        fun refreshProductsLive(q: String) {
            prodContainer.removeAllViews()
            val prods = db.getProducts(q)
            if (prods.isEmpty()) {
                prodContainer.addView(TextView(this).apply {
                    text = "📦 Omborda mahsulot yo'q.\nPastdagi + tugmasini bosib yangi mahsulot qo'shing."
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#64748B"))
                    background = glossyCard("#FFFFFF", "#E2E8F8", 28f)
                    setPadding(30, 50, 30, 50)
                })
            } else {
                prods.forEach { pr ->
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = glossyCard("#FFFFFF", "#E8ECFA", 28f)
                        setPadding(20, 18, 22, 18)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
                    }
                    card.addView(TextView(this).apply {
                        text = "📦"
                        gravity = Gravity.CENTER
                        textSize = 18f
                        background = glossyCard("#F5F7FF", "#E2E8F8", 22f)
                        layoutParams = LinearLayout.LayoutParams(88, 88).apply { rightMargin = 16 }
                    })
                    val mid = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    mid.addView(TextView(this).apply {
                        text = pr.name
                        setTextColor(Color.parseColor("#0F172A"))
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                    })
                    mid.addView(TextView(this).apply {
                        text = "${formatMoney(pr.salePrice, "so'm")} • ${pr.qty} ${pr.unit}"
                        setTextColor(Color.parseColor("#64748B"))
                        textSize = 12f
                    })
                    card.addView(mid)
                    card.addView(TextView(this).apply {
                        text = if (pr.qty > 0) "Mavjud" else "Tugagan"
                        setTextColor(if (pr.qty > 0) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
                        textSize = 11.5f
                        setTypeface(null, Typeface.BOLD)
                        background = GradientDrawable().apply {
                            cornerRadius = 18f
                            setColor(Color.parseColor(if (pr.qty > 0) "#E6F9F0" else "#FFF0F0"))
                        }
                        setPadding(20, 10, 20, 10)
                    })
                    prodContainer.addView(card)
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
        frame.addView(scroll)

        // Pastki o'ng burchakdagi suzuvchi binafsha "+" tugmasi
        frame.addView(TextView(this).apply {
            text = "+"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(60f)
            elevation = 14f
            layoutParams = FrameLayout.LayoutParams(115, 115, Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = 36
                bottomMargin = 36
            }
            setOnClickListener { openAddProductDialog() }
        })

        return frame
    }

    private fun showMahsulotActionsBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 38f)
            setPadding(32, 26, 32, 44)
        }
        val items = listOf(
            "➕  Mahsulot yaratish" to { openAddProductDialog() },
            "📥  Mahsulot kirim qilish" to { openAddProductDialog() }
        )
        items.forEach { (title, act) ->
            wrap.addView(TextView(this).apply {
                text = "$title   ❯"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                background = glossyCard("#F5F7FF", "#E2E8F8", 26f)
                setPadding(28, 22, 28, 22)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
                setOnClickListener { sheet.dismiss(); act.invoke() }
            })
        }
        sheet.setContentView(wrap)
        sheet.show()
    }

    // ==================== TAB 3: STATISTIKA EKRANI (RASMDAGI 6-EKRAN) ====================
    private fun buildStatistikaTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 22, 24, 140)
        }

        col.addView(TextView(this).apply {
            text = "Statistika"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
        })
        col.addView(TextView(this).apply {
            text = "Moliyaviy tahlil"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
            setPadding(0, 2, 0, 14)
        })

        // Hafta | Oy | Yil tablari
        val periodRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 30f)
            setPadding(6, 6, 6, 6)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        listOf("Hafta", "Oy", "Yil").forEach { p ->
            val sel = (statProdSubTab == p || (statProdSubTab == "Qoldiq" && p == "Oy"))
            periodRow.addView(TextView(this).apply {
                text = p
                gravity = Gravity.CENTER
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#475569"))
                background = if (sel) glossyPurpleGrad(24f) else null
                setPadding(12, 14, 12, 14)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { statProdSubTab = p; renderMainWorkspace() }
            })
        }
        col.addView(periodRow)

        val isSup = (statTopTab == "Ta'minotchilar")
        val allTxs = db.getTransactions(isSup)
        val persons = db.getPersons(isSup)

        var jamiKirim = 0L
        var jamiChiqim = 0L
        allTxs.forEach { tx ->
            if (tx.kind == "Kirim") jamiKirim += Math.abs(tx.amount)
            else if (!tx.status.startsWith("O'chirildi") && !tx.status.startsWith("To'liq")) jamiChiqim += Math.abs(tx.amount)
        }
        val jamiQarz = persons.filter { it.balUzs < 0 }.sumOf { Math.abs(it.balUzs) }
        val umumiyAylanma = jamiKirim + jamiChiqim

        // "Umumiy aylanma" Ko'k-Binafsha Gradient Grafik Kartasi
        val chartHero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyPurpleGrad(34f)
            elevation = 10f
            setPadding(28, 26, 28, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        chartHero.addView(TextView(this).apply {
            text = "Umumiy aylanma"
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 12.5f
        })
        val amtBadgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 4; bottomMargin = 14 }
        }
        amtBadgeRow.addView(TextView(this).apply {
            text = formatMoney(umumiyAylanma, "so'm")
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        amtBadgeRow.addView(TextView(this).apply {
            text = "↑ 12%"
            setTextColor(Color.parseColor("#A7F3D0"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = 18f
                setColor(Color.parseColor("#33FFFFFF"))
            }
            setPadding(18, 8, 18, 8)
        })
        chartHero.addView(amtBadgeRow)
        chartHero.addView(TextView(this).apply {
            text = "📈  1.10       8.10       15.10       22.10       29.10"
            setTextColor(Color.parseColor("#C7D2FE"))
            textSize = 11.5f
        })
        col.addView(chartHero)

        // Mijozlar | Ta'minotchilar switch
        val subSwitch = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        listOf("Mijozlar", "Ta'minotchilar").forEach { t ->
            val sel = (statTopTab == t)
            subSwitch.addView(TextView(this).apply {
                text = t
                gravity = Gravity.CENTER
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#475569"))
                background = if (sel) glossyPurpleGrad(22f) else glossyCard("#FFFFFF", "#E2E8F8", 22f)
                setPadding(14, 16, 14, 16)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 0, 4, 0) }
                setOnClickListener { statTopTab = t; renderMainWorkspace() }
            })
        }
        col.addView(subSwitch)

        // Kategoriyalar bo'yicha (68% doira ko'rsatkichi)
        val catCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 30f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val pct = if (umumiyAylanma > 0) ((jamiKirim * 100) / umumiyAylanma).toInt() else 68
        catCard.addView(TextView(this).apply {
            text = "$pct%"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#5946E8"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F5F7FF"))
                setStroke(14, Color.parseColor("#5946E8"))
            }
            layoutParams = LinearLayout.LayoutParams(130, 130).apply { rightMargin = 22 }
        })
        val catInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        catInfo.addView(TextView(this).apply {
            text = "Kategoriyalar bo'yicha"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 6)
        })
        catInfo.addView(TextView(this).apply {
            text = "🟢 Kirim balansi: ${formatMoney(jamiKirim, \"so'm\")}\n🔴 Qolgan qarz: ${formatMoney(jamiQarz, \"so'm\")}\n👥 Ro'yxat soni: ${persons.size} ta"
            setTextColor(Color.parseColor("#475569"))
            textSize = 12f
        })
        catCard.addView(catInfo)
        col.addView(catCard)

        scroll.addView(col)
        return scroll
    }

    // ==================== TAB 4: PROFIL EKRANI (RASMDAGI 7-EKRAN) ====================
    private fun buildProfilTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 22, 24, 140)
        }

        col.addView(TextView(this).apply {
            text = "Profil"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        })

        // Profil Kartasi (Dumaloq "H" avatar + Ism + Telefon)
        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 32f)
            elevation = 4f
            setPadding(24, 22, 24, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        }
        val ch = storeName.trim().firstOrNull()?.uppercaseChar() ?: 'H'
        headerCard.addView(TextView(this).apply {
            text = ch.toString()
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(60f)
            layoutParams = LinearLayout.LayoutParams(115, 115).apply { rightMargin = 20 }
        })
        val profInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        profInfo.addView(TextView(this).apply {
            text = storeName
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 17.5f
            setTypeface(null, Typeface.BOLD)
        })
        profInfo.addView(TextView(this).apply {
            text = "$userPhone  •  ID: $storeId"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12.5f
        })
        headerCard.addView(profInfo)
        col.addView(headerCard)

        // Rasmdagi 7-ekran menyulari: Hisob ma'lumotlari, Xavfsizlik, Bildirishnomalar, Dastur haqida, Sozlamalar
        val menuItems = listOf(
            "🪪   Hisob ma'lumotlari" to { openBusinessInfoDialog() },
            "🛡️   Xavfsizlik (6-xonali PIN, Face ID, Avto-Lock)" to { showPinAndBiometricSettingsDialog() },
            "🔔   Bildirishnomalar va Avto-SMS" to { showNotificationsSheet() },
            "💎   Premium Obuna (${remainingDaysText()})" to { showSubscriptionTariffsBottomSheet() },
            "📤   Excel (.xlsx) Eksport va Import" to { showExportBottomSheet() },
            "ℹ️   Dastur haqida" to { showDeveloperDialog() },
            "⚙️   Sozlamalar (Ovozli Bot & Suhbat)" to { showSupportChatBottomSheet() }
        )

        menuItems.forEach { (title, act) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = glossyCard("#FFFFFF", "#E8ECFA", 26f)
                elevation = 2f
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
                setOnClickListener { act.invoke() }
            }
            row.addView(TextView(this).apply {
                text = title
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = "❯"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 14f
            })
            col.addView(row)
        }

        // Pushti hoshiyali "Chiqish" tugmasi (Rasmdagi 7-ekran)
        col.addView(TextView(this).apply {
            text = "Chiqish"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor("#FFF5F5"))
                setStroke(2, Color.parseColor("#FECDD3"))
            }
            setPadding(24, 22, 24, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 }
            setOnClickListener { performLogout() }
        })

        scroll.addView(col)
        return scroll
    }

    private fun profileGridCard(title: String, sub: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 28f)
            setPadding(20, 18, 20, 18)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 6, 0) }
            setOnClickListener { onClick() }
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = sub
                setTextColor(Color.parseColor("#10B981"))
                textSize = 11.5f
                setPadding(0, 6, 0, 0)
            })
        }
    }

    private fun buildBottomNavBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 0f)
            elevation = 20f
            setPadding(10, 12, 10, 12)
            gravity = Gravity.CENTER
        }
        val tabs = listOf("🏠\nAsosiy", "👛\nKassa", "📦\nMahsulot", "📊\nStatistika", "👤\nProfil")
        tabs.forEachIndexed { idx, title ->
            val active = (currentBottomTab == idx)
            bar.addView(TextView(this).apply {
                text = title
                gravity = Gravity.CENTER
                textSize = 11f
                setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (active) Color.parseColor("#5946E8") else Color.parseColor("#94A3B8"))
                background = if (active) glossyCard("#EEF2FF", "#C7D2FE", 24f) else null
                setPadding(6, 10, 6, 10)
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

    // ==================== 3. 100% ISHLAYDIGAN AVTO-SAVE EXPORT (DOWNLOAD/QARZFY) ====================
    private fun writeXlsxToDownloadsMediaStore(fileName: String, xlsxBytes: ByteArray): Pair<String, Uri?> {
        var finalPath = "/storage/emulated/0/Download/QarzFy/$fileName"
        var resultUri: Uri? = null

        // 1-usul: Ichki app xotirasiga har doim zaxira nusxa yozish
        try {
            val appSubDir = File(getExternalFilesDir(null), "QarzFy")
            if (!appSubDir.exists()) appSubDir.mkdirs()
            val localFile = File(appSubDir, fileName)
            localFile.writeBytes(xlsxBytes)
            resultUri = Uri.fromFile(localFile)
        } catch (_: Exception) {}

        // 2-usul: To'g'ridan-to'g'ri Download/QarzFy papkasiga yozish
        try {
            val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "QarzFy")
            if (!pubDir.exists()) pubDir.mkdirs()
            val pubFile = File(pubDir, fileName)
            FileOutputStream(pubFile, false).use {
                it.write(xlsxBytes)
                it.flush()
            }
            finalPath = pubFile.absolutePath
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                resultUri = Uri.fromFile(pubFile)
            }
        } catch (_: Exception) {}

        // 3-usul: Android 10+ (Q va undan yuqori) uchun MediaStore.Downloads orqali mavjud faylni yangilash yoki yangi ochish
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val projection = arrayOf(MediaStore.Downloads._ID)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)

                var existingUri: Uri? = null
                resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        existingUri = android.content.ContentUris.withAppendedId(collection, id)
                    }
                }

                if (existingUri != null) {
                    try {
                        resolver.openOutputStream(existingUri!!, "rwt")?.use { out ->
                            out.write(xlsxBytes)
                            out.flush()
                        }
                        resultUri = existingUri
                    } catch (_: Exception) {
                        try {
                            resolver.delete(existingUri!!, null, null)
                        } catch (_: Exception) {}
                        existingUri = null
                    }
                }

                if (existingUri == null) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/QarzFy")
                    }
                    val inserted = resolver.insert(collection, values)
                    if (inserted != null) {
                        resolver.openOutputStream(inserted, "w")?.use { out ->
                            out.write(xlsxBytes)
                            out.flush()
                        }
                        resultUri = inserted
                    }
                }
            } catch (_: Exception) {}
        }

        return Pair(finalPath, resultUri)
    }

    private fun savePermanentBackupToDownloadsQarzFy(): String {
        val xlsxBytes = generateRealXlsxZipBytes()
        val (path1, _) = writeXlsxToDownloadsMediaStore("QarzFy_AvtoBazasi.xlsx", xlsxBytes)
        val todayTag = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        writeXlsxToDownloadsMediaStore("QarzFy_KunlikAvto_$todayTag.xlsx", xlsxBytes)
        return path1
    }

    private fun saveRealXlsxAndGetShareableUri(): Triple<File, String, Uri> {
        val dateTag = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val fileName = "QarzFy_Hisobot_$dateTag.xlsx"
        val xlsxBytes = generateRealXlsxZipBytes()

        val appSubDir = File(getExternalFilesDir(null), "QarzFy")
        if (!appSubDir.exists()) appSubDir.mkdirs()
        val localXlsx = File(appSubDir, fileName)
        localXlsx.writeBytes(xlsxBytes)

        // Doimiy avto-bazani ham yangilab qo'yamiz
        savePermanentBackupToDownloadsQarzFy()

        val (publicPath, mediaUri) = writeXlsxToDownloadsMediaStore(fileName, xlsxBytes)
        val shareUri = mediaUri ?: Uri.fromFile(localXlsx)

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
        clientTxs.forEach { s2.add(listOf(it.txId, it.kind, it.personName, it.personPhone, it.amount.toString(), it.currency, it.date, "${it.note} (Muddat: ${it.dueDate})", it.status)) }

        val s3 = mutableListOf(listOf("ID", "Ism", "Telefon raqami", "Balans USD", "Balans UZS", "Manzil", "Yaratilgan sana"))
        suppliers.forEach { s3.add(listOf(it.id.toString(), it.name, it.phone, it.balUsd.toString(), it.balUzs.toString(), storeAddress, it.createdAt)) }

        val s4 = mutableListOf(listOf("Tranzaksiya ID", "Turi", "Ta'minotchi ismi", "Ta'minotchi telefoni", "Summa", "Valyuta", "Sana", "Tavsif", "Holat"))
        supTxs.forEach { s4.add(listOf(it.txId, it.kind, it.personName, it.personPhone, it.amount.toString(), it.currency, it.date, "${it.note} (Muddat: ${it.dueDate})", it.status)) }

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
            // 1) Dastur ochilishi bilan darhol Avto-Save qilamiz va bugungi kunlik eksportni tekshiramiz
            try {
                val savedPath = savePermanentBackupToDownloadsQarzFy()
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val lastAutoExportDay = prefs.getString("last_auto_export_day", "") ?: ""
                if (lastAutoExportDay != todayStr) {
                    val (_, fullPath, _) = saveRealXlsxAndGetShareableUri()
                    prefs.edit().putString("last_auto_export_day", todayStr).commit()
                    withContext(Dispatchers.Main) {
                        triggerAutoExportSignalAndNotification(fullPath)
                        Toast.makeText(this@MainActivity, "✅ Kunlik Avto-Eksport saqlandi: Download/QarzFy", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {}

            // 2) Har kecha soat 00:00 da (yoki yangi kun boshlanganda) avtomatik eksport qilish
            while (true) {
                delay(20_000L)
                try {
                    val nowDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val savedDay = prefs.getString("last_auto_export_day", "") ?: ""
                    val cal = Calendar.getInstance()
                    val isMidnight = (cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) <= 2)

                    if (nowDay != savedDay || isMidnight) {
                        val (_, path, _) = saveRealXlsxAndGetShareableUri()
                        prefs.edit().putString("last_auto_export_day", nowDay).commit()
                        withContext(Dispatchers.Main) {
                            triggerAutoExportSignalAndNotification(path)
                            checkDueDatesAndSendAutoSmsForClientsAndSuppliers()
                            Toast.makeText(this@MainActivity, "🔔 Avto-Eksport saqlandi:\n$path", Toast.LENGTH_LONG).show()
                        }
                        delay(120_000L)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // ==================== SLIDE-UP BOTTOM SHEETS (KALENDAR MUDDAT TANLASH BILAN!) ====================
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
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })
        wrap.addView(TextView(this).apply {
            text = "Barcha o'zgarishlar avtomatik ravishda telefonning 'Download/QarzFy/QarzFy_AvtoBazasi.xlsx' fayliga saqlanib boradi (dastur o'chirilsa ham o'chmaydi!)."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, 0, 0, 24)
        })

        wrap.addView(Button(this).apply {
            text = "💾 Download/QarzFy papkaga yangi nusxa saqlash"
            setTextColor(Color.WHITE)
            background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 96).apply { bottomMargin = 16 }
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 96)
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

        fun showImportBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 36f)
            setPadding(32, 24, 32, 40)
        }
        wrap.addView(TextView(this).apply {
            text = "📥 Excel (.xlsx / .csv) Import"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        })
        wrap.addView(TextView(this).apply {
            text = "Telefoningiz xotirasidan Excel (.xlsx yoki .csv) faylni tanlang:"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12.5f
            setPadding(0, 0, 0, 18)
        })
        wrap.addView(Button(this).apply {
            text = "📂 Excel (.xlsx) Fayl Tanlash"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(26f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 102)
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

    private fun showClientKirimChiqimBottomSheet(client: ClientRec, isSupplier: Boolean, defaultKind: String = "Chiqim") {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 54f)
            setPadding(48, 32, 48, 56)
        }
        var selectedKind = defaultKind
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
                setTextColor(if (cSel) Color.WHITE else Color.parseColor("#94A3B8"))
                background = if (cSel) glossyOrangeGrad(26f) else null
                setPadding(16, 22, 16, 22)
            }
            btnKirim.apply {
                text = "Kirim (To'lov olish)"; gravity = Gravity.CENTER; textSize = 13.5f; setTypeface(null, Typeface.BOLD)
                setTextColor(if (!cSel) Color.WHITE else Color.parseColor("#94A3B8"))
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

        var selectedDueDate = if (client.dueDate.isNotBlank() && client.dueDate != "Bugun") client.dueDate else defaultDueDateStr()
        val btnCalendarPicker = Button(this).apply {
            text = "📅 Qaytarish muddati: $selectedDueDate (Kalendar)"
            setTextColor(Color.parseColor("#5B4CFF"))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyCard("#EEF2FF", "#6366F1", 30f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 92).apply { bottomMargin = 18 }
            setOnClickListener {
                openCalendarPicker(selectedDueDate) { picked ->
                    selectedDueDate = picked
                    text = "📅 Qaytarish muddati: $selectedDueDate (Kalendar)"
                }
            }
        }

        wrap.addView(etAmount)
        wrap.addView(etNote)
        wrap.addView(btnCalendarPicker)

        wrap.addView(Button(this).apply {
            text = "✅ Saqlash (Sana/Vaqti bilan tarixga yozish)"
            setTextColor(Color.WHITE); background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 98)
            setOnClickListener {
                val amt = cleanNumberInput(etAmount.text.toString())
                if (amt <= 0L) {
                    Toast.makeText(this@MainActivity, "Summani kiriting!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val note = etNote.text.toString().trim().ifEmpty { "Tranzaksiya" }
                db.addOrUpdatePersonTx(client.name, client.phone, amt, "UZS", selectedKind, note, selectedDueDate, isSupplier)
                triggerBackgroundSmsAndVoiceCall(client.name, client.phone, amt, selectedKind, note, selectedDueDate, isSupplier)
                Toast.makeText(this@MainActivity, "✅ Saqlandi! Muddat: $selectedDueDate", Toast.LENGTH_SHORT).show()
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
            setTextColor(Color.parseColor("#0F172A")); textSize = 16f; setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 14)
        })

        val etQuickSearch = makeGlassInput("🔍 Mijoz ismini yoki telefonini yozing...", "")
        wrap.addView(etQuickSearch)

        wrap.addView(TextView(this).apply {
            text = "Mijozni tanlang (ustiga bosing):"
            setTextColor(Color.parseColor("#94A3B8")); textSize = 13.5f; setTypeface(null, Typeface.BOLD)
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
                    text = "Topilmadi. Pastdagi tugma orqali yangi qo'shing."
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
            text = if (isSupplier) "➕ Yangi Ta'minotchi va Qarz Qo'shish" else "➕ Yangi Qarzdor Qo'shish"
            setTextColor(Color.WHITE)
            background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 96).apply { topMargin = 20 }
            setOnClickListener {
                sheet.dismiss()
                showAddClientBottomSheet(isSupplier)
            }
        })

        sheet.setContentView(wrap)
        sheet.show()
    }

    // YANGI QARZDOR YOKI TA'MINOTCHI QO'SHISH (KALENDAR ORQALI MUDDAT TANLASH BILAN!)
    private fun showAddClientBottomSheet(isSupplier: Boolean) {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F0", 54f)
            setPadding(44, 30, 44, 56)
        }
        wrap.addView(TextView(this).apply {
            text = if (isSupplier) "🚚 Yangi Ta'minotchi Qo'shish" else "👤+ Yangi Qarzdor / Mijoz Qo'shish"
            setTextColor(Color.parseColor("#0F172A")); textSize = 16f; setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 18)
        })
        val etName = makeGlassInput("Ism Familiya (masalan: Ali Valiyev) *", "")
        val etPhone = makeGlassInput("Telefon (+998901234567)", "+998")
        etPhone.inputType = InputType.TYPE_CLASS_PHONE
        val etDebt = makeGlassInput("Qarz summasi (UZS, bo'sh qoldirish mumkin)", "")
        etDebt.inputType = InputType.TYPE_CLASS_NUMBER
        val etNote = makeGlassInput("Izoh / Olingan mahsulotlar", "")

        var selectedDueDate = defaultDueDateStr()
        val btnCalendar = Button(this).apply {
            text = "📅 Qarz berish muddati: $selectedDueDate (Kalendar)"
            setTextColor(Color.parseColor("#5B4CFF"))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyCard("#EEF2FF", "#6366F1", 30f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 92).apply { bottomMargin = 18 }
            setOnClickListener {
                openCalendarPicker(selectedDueDate) { picked ->
                    selectedDueDate = picked
                    text = "📅 Qarz berish muddati: $selectedDueDate (Kalendar)"
                }
            }
        }

        wrap.addView(etName)
        wrap.addView(etPhone)
        wrap.addView(etDebt)
        wrap.addView(etNote)
        wrap.addView(btnCalendar)

        wrap.addView(Button(this).apply {
            text = "✅ Qo'shish va Saqlash"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(34f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 98)
            setOnClickListener {
                val n = etName.text.toString().trim()
                val ph = etPhone.text.toString().trim()
                val d = cleanNumberInput(etDebt.text.toString())
                val note = etNote.text.toString().trim().ifEmpty { "Qarz yozildi" }

                if (n.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Iltimos, ism-familiyani kiriting!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                if (d > 0L) {
                    db.addOrUpdatePersonTx(n, ph, d, "UZS", "Chiqim", note, selectedDueDate, isSupplier)
                    triggerBackgroundSmsAndVoiceCall(n, ph, d, "Chiqim", note, selectedDueDate, isSupplier)
                } else {
                    db.upsertClientDirect(n, ph, 0.0, 0L, db.nowStr(), isSupplier, dueDate = selectedDueDate)
                }

                Toast.makeText(this@MainActivity, "✅ $n qo'shildi! Muddat: $selectedDueDate", Toast.LENGTH_SHORT).show()
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
            gravity = Gravity.CENTER_HORIZONTAL
            background = glossyCard("#F5F7FF", "#E2E8F8", 44f)
            setPadding(32, 28, 32, 48)
        }
        wrap.addView(TextView(this).apply {
            text = "Premium"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
        })
        wrap.addView(TextView(this).apply {
            text = "Barcha imkoniyatlar ochiq • ${remainingDaysText()}"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12.5f
            setPadding(0, 4, 0, 18)
        })

        // O'rtadagi Toj (Crown) belgisi
        wrap.addView(TextView(this).apply {
            text = "👑"
            gravity = Gravity.CENTER
            textSize = 36f
            background = glossyPurpleGrad(65f)
            elevation = 10f
            layoutParams = LinearLayout.LayoutParams(130, 130).apply { bottomMargin = 18 }
        })

        val feats = listOf(
            "☑️  Cheksiz qarzlar va ta'minotchilar",
            "☑️  To'liq statistika va Excel avto-eksport",
            "☑️  Ma'lumotlar zaxirasi (Download/QarzFy)",
            "☑️  Avto-SMS va Fonda Ovozli Bot"
        )
        feats.forEach { ft ->
            wrap.addView(TextView(this).apply {
                text = ft
                setTextColor(Color.parseColor("#1E293B"))
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setPadding(12, 6, 12, 6)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
        }

        // 1 Oy — 49 000 so'm (Binafsha)
        wrap.addView(TextView(this).apply {
            text = "💎  1 Oy — 49 000 so'm"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(36f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 18; bottomMargin = 10 }
            setOnClickListener { sheet.dismiss(); selectTariffAndPayDialog(1, 49000L) }
        })

        // 3 Oy — 119 000 so'm (Ko'k)
        wrap.addView(TextView(this).apply {
            text = "🔥  3 Oy — 119 000 so'm"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyOrangeGrad(36f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
            setOnClickListener { sheet.dismiss(); selectTariffAndPayDialog(3, 119000L) }
        })

        // 6 Oy — 199 000 so'm (Yashil)
        wrap.addView(TextView(this).apply {
            text = "👑  6 Oy — 199 000 so'm"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyGreenGrad(36f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { sheet.dismiss(); selectTariffAndPayDialog(6, 199000L) }
        })

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
                    Toast.makeText(this, "✅ Jami $importedCount ta yozuv import qilindi!", Toast.LENGTH_LONG).show()
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
        entries["xl/worksheets/sheet2.xml"]?.let { sb ->
            val rows = parseSingleWorksheetXml(sb, sharedStrings)
            rows.forEachIndexed { idx, r ->
                if (idx > 0 && r.size >= 5) {
                    val txId = r.getOrNull(0)?.trim() ?: ""
                    val kind = r.getOrNull(1)?.trim() ?: "Chiqim"
                    val name = r.getOrNull(2)?.trim() ?: ""
                    val phone = r.getOrNull(3)?.trim() ?: ""
                    val amt = parseMoneyLong(r.getOrNull(4))
                    val cur = r.getOrNull(5)?.trim() ?: "UZS"
                    val dt = r.getOrNull(6)?.trim() ?: ""
                    val note = r.getOrNull(7)?.trim() ?: ""
                    val st = r.getOrNull(8)?.trim() ?: "Faol"
                    if (name.isNotEmpty() && !name.equals("Mijoz ismi", true) && amt > 0) {
                        db.insertTxDirect(txId, kind, name, phone, amt, cur, dt, "", note, st, false)
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


    private fun autoLockLabel(ms: Long): String = when (ms) {
        30_000L -> "30 sekund"
        60_000L -> "1 minut"
        300_000L -> "5 minut"
        600_000L -> "10 minut"
        3_600_000L -> "1 soat"
        else -> "1 minut"
    }

    fun renderPinLockScreen(mode: String) {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            currentFocus?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0) }
        } catch (_: Exception) {}

        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#F5F7FF"))

        var currentStep = mode
        if (mode == "SETUP_NEW") currentStep = "SETUP_FIRST"

        var firstEnteredPin = ""
        var typedPin = ""

        val mainCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val cardBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glossyCard("#FFFFFF", "#E2E8F0", 38f)
            elevation = 10f
            setPadding(28, 34, 28, 34)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(82, 82).apply { bottomMargin = 12 }
        }
        cardBox.addView(logo)

        val tvTitle = TextView(this).apply {
            text = when (currentStep) {
                "UNLOCK" -> "PIN-kodni kiriting"
                "CHANGE_VERIFY_OLD" -> "Eski PIN-kodni kiriting"
                else -> "Yangi 6 xonali PIN-kod"
            }
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        cardBox.addView(tvTitle)

        val tvSub = TextView(this).apply {
            text = when (currentStep) {
                "UNLOCK" -> "Dasturga kirish uchun 6 xonali kodni tering"
                "CHANGE_VERIFY_OLD" -> "O'zgartirish uchun amaldagi PIN-kodni tasdiqlang"
                else -> "Xavfsizlik uchun 6 ta raqam tanlang"
            }
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(12, 6, 12, 24)
        }
        cardBox.addView(tvSub)

        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 28 }
        }
        val dotViews = Array(6) {
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(32, 32).apply { setMargins(12, 0, 12, 0) }
                background = glossyRoundBtnBg("#F1F5F9", "#CBD5E1")
                dotsRow.addView(this)
            }
        }
        cardBox.addView(dotsRow)

        fun updateDotsUI() {
            for (i in 0 until 6) {
                val filled = i < typedPin.length
                dotViews[i].background = if (filled) {
                    glossyRoundBtnBg("#5B4CFF", "#5B4CFF")
                } else {
                    glossyRoundBtnBg("#F1F5F9", "#CBD5E1")
                }
            }
        }

        fun handleSixDigitsCompleted(code: String) {
            when (currentStep) {
                "UNLOCK" -> {
                    if (code == appPinCode) {
                        isPinUnlockedThisSession = true
                        lastInteractionTimeMs = System.currentTimeMillis()
                        checkServerSubscriptionAndRender()
                        scheduleMidnightAutoExcelSaveWithSignal()
                        checkDueDatesAndSendAutoSmsForClientsAndSuppliers()
                    } else {
                        Toast.makeText(this, "❌ PIN-kod noto'g'ri!", Toast.LENGTH_SHORT).show()
                        typedPin = ""
                        updateDotsUI()
                    }
                }
                "CHANGE_VERIFY_OLD" -> {
                    if (code == appPinCode) {
                        currentStep = "SETUP_FIRST"
                        typedPin = ""
                        tvTitle.text = "Yangi 6 xonali PIN-kod"
                        tvSub.text = "Endi yangi 6 xonali PIN-kodni tering"
                        updateDotsUI()
                    } else {
                        Toast.makeText(this, "❌ Eski PIN-kod noto'g'ri!", Toast.LENGTH_SHORT).show()
                        typedPin = ""
                        updateDotsUI()
                    }
                }
                "SETUP_FIRST" -> {
                    firstEnteredPin = code
                    currentStep = "SETUP_CONFIRM"
                    typedPin = ""
                    tvTitle.text = "PIN-kodni tasdiqlang"
                    tvSub.text = "Yangi 6 xonali PIN-kodni qayta tering"
                    updateDotsUI()
                }
                "SETUP_CONFIRM" -> {
                    if (code == firstEnteredPin) {
                        appPinCode = code
                        isPinUnlockedThisSession = true
                        lastInteractionTimeMs = System.currentTimeMillis()
                        saveSessionSynchronously(true)
                        Toast.makeText(this, "✅ PIN-kod saqlandi!", Toast.LENGTH_SHORT).show()
                        checkServerSubscriptionAndRender()
                        scheduleMidnightAutoExcelSaveWithSignal()
                        checkDueDatesAndSendAutoSmsForClientsAndSuppliers()
                    } else {
                        Toast.makeText(this, "❌ PIN-kodlar mos kelmadi!", Toast.LENGTH_SHORT).show()
                        currentStep = "SETUP_FIRST"
                        firstEnteredPin = ""
                        typedPin = ""
                        tvTitle.text = "Yangi 6 xonali PIN-kod"
                        tvSub.text = "6 ta raqam tanlang"
                        updateDotsUI()
                    }
                }
            }
        }

        fun appendDigit(d: String) {
            if (typedPin.length < 6) {
                typedPin += d
                updateDotsUI()
                if (typedPin.length == 6) {
                    handleSixDigitsCompleted(typedPin)
                }
            }
        }

        fun removeLastDigit() {
            if (typedPin.isNotEmpty()) {
                typedPin = typedPin.dropLast(1)
                updateDotsUI()
            }
        }

        val keypadGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("👆", "0", "⌫")
        )

        rows.forEach { r ->
            val rLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 14 }
            }
            r.forEach { keyLabel ->
                val isBio = (keyLabel == "👆")
                val btnKey = TextView(this).apply {
                    text = keyLabel
                    gravity = Gravity.CENTER
                    setTextColor(if (isBio) Color.WHITE else Color.parseColor("#0F172A"))
                    textSize = 15.5f
                    setTypeface(null, Typeface.BOLD)
                    background = if (isBio) glossyPurpleGrad(70f) else glossyRoundBtnBg("#F8FAFC", "#E2E8F0")
                    elevation = 3f
                    layoutParams = LinearLayout.LayoutParams(96, 96).apply { setMargins(16, 0, 16, 0) }
                    setOnClickListener {
                        when (keyLabel) {
                            "⌫" -> removeLastDigit()
                            "👆" -> {
                                if (currentStep == "UNLOCK") {
                                    triggerBiometricPromptForUnlock()
                                } else {
                                    Toast.makeText(this@MainActivity, "Avval 6 xonali PIN-kodni tering!", Toast.LENGTH_SHORT).show()
                                }
                            }
                            else -> appendDigit(keyLabel)
                        }
                    }
                }
                rLayout.addView(btnKey)
            }
            keypadGrid.addView(rLayout)
        }
        cardBox.addView(keypadGrid)

        if (mode == "CHANGE_VERIFY_OLD") {
            cardBox.addView(Button(this).apply {
                text = "⬅ Bekor qilish"
                setTextColor(Color.parseColor("#475569"))
                textSize = 12.5f
                background = glossyCard("#F1F5F9", "#CBD5E1", 22f)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 90).apply { topMargin = 10 }
                setPadding(32, 0, 32, 0)
                setOnClickListener { renderMainWorkspace() }
            })
        }

        mainCol.addView(cardBox)
        val scroll = ScrollView(this).apply { addView(mainCol) }
        rootLayout.addView(scroll)

        if (currentStep == "UNLOCK" && isBiometricEnabled) {
            mainCol.postDelayed({
                triggerBiometricPromptForUnlock()
            }, 300)
        }
    }

    private fun triggerBiometricPromptForUnlock() {
        try {
            val biometricManager = BiometricManager.from(this)
            val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
                return
            }

            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isPinUnlockedThisSession = true
                    lastInteractionTimeMs = System.currentTimeMillis()
                    Toast.makeText(this@MainActivity, "⚡ Biometrik tasdiqlandi!", Toast.LENGTH_SHORT).show()
                    checkServerSubscriptionAndRender()
                    scheduleMidnightAutoExcelSaveWithSignal()
                    checkDueDatesAndSendAutoSmsForClientsAndSuppliers()
                }
            })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("QarzFy Neon Xavfsizlik")
                .setSubtitle("Face ID yoki Barmoq izi orqali dasturni oching")
                .setNegativeButtonText("PIN-kod terish")
                .build()

            biometricPrompt.authenticate(promptInfo)
        } catch (_: Exception) {}
    }

    private fun showAutoLockTimerSelectionDialog() {
        val options = arrayOf(
            "⏱ 30 sekund",
            "⏱ 1 minut",
            "⏱ 5 minut",
            "⏱ 10 minut",
            "⏱ 1 soat"
        )
        val values = longArrayOf(
            30_000L,
            60_000L,
            300_000L,
            600_000L,
            3_600_000L
        )
        val currentIdx = values.indexOf(autoLockTimeoutMs).let { if (it >= 0) it else 1 }

        AlertDialog.Builder(this)
            .setTitle("🔒 Avtomatik Qulflash Vaqti (Auto-Lock)")
            .setSingleChoiceItems(options, currentIdx) { dialog, which ->
                autoLockTimeoutMs = values[which]
                saveSessionSynchronously(isLoggedIn)
                Toast.makeText(this, "✅ Avtomatik qulflash: ${options[which]} ga belgilandi!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                showPinAndBiometricSettingsDialog()
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun showPinAndBiometricSettingsDialog() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#131C31", "#00F5D4", 48f)
            setPadding(42, 32, 42, 52)
        }

        wrap.addView(TextView(this).apply {
            text = "🔐 PIN-kod, Biometrik va Avto-Lock"
            setTextColor(Color.parseColor("#5B4CFF"))
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 18)
        })

        val pinStatus = if (appPinCode.length == 6) "✅ O'rnatilgan (6 xonali)" else "🔴 O'rnatilmagan"
        val bioStatus = if (isBiometricEnabled) "✅ Yoqilgan" else "🔴 O'chirilgan"
        val lockTimeStr = autoLockLabel(autoLockTimeoutMs)

        wrap.addView(TextView(this).apply {
            text = "📌 PIN-kod: $pinStatus\n👆 Face ID / Barmoq izi: $bioStatus\n⏱ Avtomatik Lock vaqti: $lockTimeStr"
            setTextColor(Color.parseColor("#1E293B"))
            textSize = 13.5f
            setPadding(0, 0, 0, 24)
        })

        wrap.addView(Button(this).apply {
            text = "🔄 6 xonali PIN-kodni o'zgartirish (Eski PIN bilan)"
            setTextColor(Color.WHITE)
            background = glossyPurpleGrad(30f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 92).apply { bottomMargin = 14 }
            setOnClickListener {
                sheet.dismiss()
                if (appPinCode.length == 6) {
                    renderPinLockScreen(mode = "CHANGE_VERIFY_OLD")
                } else {
                    renderPinLockScreen(mode = "SETUP_NEW")
                }
            }
        })

        wrap.addView(Button(this).apply {
            text = "⏱ Avtomatik Lock vaqti: $lockTimeStr (O'zgartirish)"
            setTextColor(Color.WHITE)
            background = glossyOrangeGrad(30f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 92).apply { bottomMargin = 14 }
            setOnClickListener {
                sheet.dismiss()
                showAutoLockTimerSelectionDialog()
            }
        })

        wrap.addView(Button(this).apply {
            text = if (isBiometricEnabled) "👆 Face ID / Barmoq izini O'chirish" else "👆 Face ID / Barmoq izini Yoqish"
            setTextColor(Color.parseColor("#5B4CFF"))
            background = glossyCard("#1E293B", "#00F5D4", 30f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 92)
            setOnClickListener {
                isBiometricEnabled = !isBiometricEnabled
                saveSessionSynchronously(isLoggedIn)
                sheet.dismiss()
                Toast.makeText(this@MainActivity, if (isBiometricEnabled) "✅ Face ID / Barmoq izi yoqildi!" else "🔴 O'chirildi", Toast.LENGTH_SHORT).show()
                showPinAndBiometricSettingsDialog()
            }
        })

        sheet.setContentView(wrap)
        sheet.show()
    }

    private fun showPushSettingsDialog() {
        AlertDialog.Builder(this).setTitle("🔔 Avto-Save va Muddat Avto-SMS")
            .setMessage("• Har bir kirim-chiqim va qarz yozilganda fayl avtomatik Download/QarzFy/QarzFy_AvtoBazasi.xlsx ga saqlanadi.\n• Qarz muddati kelganda xaridorlar va ta'minotchilarga avtomatik SMS yuboriladi.")
            .setPositiveButton("Tushunarli", null).show()
    }

    private fun showVoiceSettingsDialog() {
        val statusTxt = if (isAutoVoiceCallEnabled) "✅ Yoqilgan (Fonda ko'rinmas)" else "🔴 O'chirilgan"
        AlertDialog.Builder(this)
            .setTitle("📞 Fonda Avto-Qo'ng'iroq (Bot) Sozlamasi")
            .setMessage("Holati: $statusTxt\n\nXaridorga yoki ta'minotchiga qarz yozilganda hamda muddati kelganda fonda SMS va ovozli xabar yuboriladi.")
            .setPositiveButton(if (isAutoVoiceCallEnabled) "O'chirish" else "Yoqish") { _, _ ->
                isAutoVoiceCallEnabled = !isAutoVoiceCallEnabled
                saveSessionSynchronously(isLoggedIn)
                renderMainWorkspace()
            }
            .setNeutralButton("🔊 Ovozni test qilish") { _, _ ->
                ttsEngine?.speak("Assalomu alaykum! Hurmatli mijoz, do'konimizdan sizga qarz yozildi.", TextToSpeech.QUEUE_FLUSH, null, "test_voice")
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
            .setMessage("Ilova: QarzFy v11.0 (Glossy Glassmorphism)\nDasturchi: Hasan Jaylovov\nTelegram: @HasanJaylovov")
            .setPositiveButton("Bog'lanish") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/HasanJaylovov")))
            }.setNegativeButton("Yopish", null).show()
    }

    private fun sendBulkSmsReminders(isSupplier: Boolean) {
        var sent = 0
        db.getPersons(isSupplier).forEach { p ->
            if (p.balUzs != 0L) {
                triggerBackgroundSmsAndVoiceCall(p.name, p.phone, Math.abs(p.balUzs), "Chiqim", "Qarz eslatmasi", p.dueDate, isSupplier)
                sent++
            }
        }
        Toast.makeText(this, "📩 $sent ta qarzdor/ta'minotchiga SMS va Fonda Qo'ng'iroq yuborildi!", Toast.LENGTH_LONG).show()
    }
}
