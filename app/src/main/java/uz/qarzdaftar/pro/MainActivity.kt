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
    fun updatePersonProfile(oldName: String, newName: String, newPhone: String, isSupplier: Boolean) {
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        val cleanNewName = newName.trim().ifEmpty { oldName }
        val cleanNewPhone = newPhone.trim()
        db.execSQL(
            "UPDATE clients SET name = ?, phone = ? WHERE name = ? AND is_supplier = ?",
            arrayOf<Any>(cleanNewName, cleanNewPhone, oldName, supFlag)
        )
        db.execSQL(
            "UPDATE transactions SET person_name = ?, person_phone = ? WHERE person_name = ? AND is_supplier = ?",
            arrayOf<Any>(cleanNewName, cleanNewPhone, oldName, supFlag)
        )
        onDataChangedCallback?.invoke()
    }

    fun insertOrUpdatePerson(name: String, phone: String, balUzs: Long, balUsd: Double, isSupplier: Boolean, dueDate: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        val c = db.rawQuery(
            "SELECT id FROM clients WHERE LOWER(name) = LOWER(?) AND is_supplier = ? LIMIT 1",
            arrayOf(cleanName, supFlag.toString())
        )
        val exists = c.moveToFirst()
        val existingId = if (exists) c.getInt(0) else -1
        c.close()

        val cv = ContentValues().apply {
            put("name", cleanName)
            put("phone", phone)
            put("bal_uzs", balUzs)
            put("bal_usd", balUsd)
            put("is_supplier", supFlag)
            put("due_date", dueDate.ifEmpty { "Muddat yo'q" })
        }
        if (exists && existingId != -1) {
            db.update("clients", cv, "id = ?", arrayOf(existingId.toString()))
        } else {
            db.insert("clients", null, cv)
        }
    }

    fun insertTransactionDirect(
        txId: String,
        personName: String,
        personPhone: String,
        kind: String,
        amount: Long,
        currency: String,
        note: String,
        date: String,
        dueDate: String,
        status: String,
        isSupplier: Boolean
    ) {
        val cleanName = personName.trim()
        if (cleanName.isEmpty()) return
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        val finalTxId = txId.ifEmpty { "D-" + (100000 + Random().nextInt(900000)) }

        val c = db.rawQuery("SELECT id FROM transactions WHERE tx_id = ? LIMIT 1", arrayOf(finalTxId))
        val exists = c.moveToFirst()
        c.close()
        if (exists) return

        val cv = ContentValues().apply {
            put("tx_id", finalTxId)
            put("kind", kind)
            put("person_name", cleanName)
            put("person_phone", personPhone)
            put("amount", Math.abs(amount))
            put("currency", currency.ifEmpty { "UZS" })
            put("note", note)
            put("date", date)
            put("due_date", dueDate)
            put("status", status.ifEmpty { "Faol" })
            put("is_supplier", supFlag)
        }
        db.insert("transactions", null, cv)
    }

    fun insertKassaDirect(type: String, category: String, amount: Long, currency: String, payType: String, date: String, note: String, creator: String) {
        if (amount <= 0L) return
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("type", type.ifEmpty { "Kirim" })
            put("category", category)
            put("amount", amount)
            put("currency", currency.ifEmpty { "UZS" })
            put("pay_type", payType.ifEmpty { "Naqd" })
            put("date", date)
            put("note", note)
            put("creator", creator.ifEmpty { "Avto" })
        }
        db.insert("kassa", null, cv)
    }

    fun deletePersonFromRegistryCompletely(personName: String, isSupplier: Boolean) {
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        db.execSQL(
            "DELETE FROM transactions WHERE person_name = ? AND is_supplier = ?",
            arrayOf<Any>(personName, supFlag)
        )
        db.execSQL(
            "DELETE FROM clients WHERE name = ? AND is_supplier = ?",
            arrayOf<Any>(personName, supFlag)
        )
        onDataChangedCallback?.invoke()
    }

    fun wipePersonHistoryCompletely(personName: String, isSupplier: Boolean) {
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        // Mijozning o'zini o'chirmasdan balansini 0 qilamiz va barcha kirim/chiqim/o'chirilgan tarixini tozalaymiz
        db.execSQL(
            "UPDATE clients SET bal_uzs = 0, bal_usd = 0, due_date = '' WHERE name = ? AND is_supplier = ?",
            arrayOf<Any>(personName, supFlag)
        )
        db.execSQL(
            "DELETE FROM transactions WHERE person_name = ? AND is_supplier = ?",
            arrayOf<Any>(personName, supFlag)
        )
        onDataChangedCallback?.invoke()
    }

    fun clearAllDebtsForPerson(personName: String, personPhone: String, isSupplier: Boolean, targetCurrency: String = "ALL"): Pair<Long, Long> {
        val db = writableDatabase
        val supFlag = if (isSupplier) 1 else 0
        val delTime = nowStr()

        var clearedUzs = 0L
        var clearedUsd = 0L
        val c = db.rawQuery("SELECT id, bal_uzs, bal_usd FROM clients WHERE name = ? AND is_supplier = ?", arrayOf(personName, supFlag.toString()))
        if (c.moveToFirst()) {
            val clientId = c.getInt(0)
            val balUzs = c.getLong(1)
            val balUsd = c.getDouble(2).toLong()
            if (targetCurrency == "UZS") {
                clearedUzs = Math.abs(balUzs)
                db.execSQL("UPDATE clients SET bal_uzs = 0 WHERE id = ?", arrayOf<Any>(clientId))
            } else if (targetCurrency == "USD") {
                clearedUsd = Math.abs(balUsd)
                db.execSQL("UPDATE clients SET bal_usd = 0 WHERE id = ?", arrayOf<Any>(clientId))
            } else {
                clearedUzs = Math.abs(balUzs)
                clearedUsd = Math.abs(balUsd)
                db.execSQL("UPDATE clients SET bal_usd = 0, bal_uzs = 0 WHERE id = ?", arrayOf<Any>(clientId))
            }
        }
        c.close()

        val newStatus = "To'liq to'landi / O'chirildi ($delTime)"
        if (targetCurrency == "ALL") {
            db.execSQL(
                "UPDATE transactions SET status = ? WHERE person_name = ? AND is_supplier = ? AND status = 'Faol'",
                arrayOf<Any>(newStatus, personName, supFlag)
            )
        } else {
            db.execSQL(
                "UPDATE transactions SET status = ? WHERE person_name = ? AND is_supplier = ? AND currency = ? AND status = 'Faol'",
                arrayOf<Any>(newStatus, personName, supFlag, targetCurrency)
            )
        }

        if (clearedUzs > 0L) {
            val payTxId = (if (isSupplier) "ST-FULL-" else "D-FULL-") + (100000 + Random().nextInt(900000))
            insertTxDirect(payTxId, "Kirim", personName, personPhone, clearedUzs, "UZS", delTime, "", "To'liq hamma summani berdi (UZS yopildi)", "To'liq yopildi ($delTime)", isSupplier)
            val catName = if (isSupplier) "Ta'minotchi to'liq yopildi: $personName" else "Qarz to'liq yopildi: $personName"
            db.execSQL("INSERT INTO kassa (type, category, amount, currency, pay_type, date, note, creator) VALUES ('Kirim', ?, ?, 'UZS', 'Naqd', ?, 'To''liq qarzni berdi (UZS)', 'Avto')", arrayOf<Any>(catName, clearedUzs, delTime))
        }

        if (clearedUsd > 0L) {
            val payTxIdUsd = (if (isSupplier) "ST-USD-" else "D-USD-") + (100000 + Random().nextInt(900000))
            insertTxDirect(payTxIdUsd, "Kirim", personName, personPhone, clearedUsd, "USD", delTime, "", "To'liq hamma summani berdi (USD yopildi)", "To'liq yopildi ($delTime)", isSupplier)
            val catNameUsd = if (isSupplier) "Ta'minotchi yopildi (USD): $personName" else "Qarz yopildi (USD): $personName"
            db.execSQL("INSERT INTO kassa (type, category, amount, currency, pay_type, date, note, creator) VALUES ('Kirim', ?, ?, 'USD', 'Naqd', ?, 'To''liq qarzni berdi (USD)', 'Avto')", arrayOf<Any>(catNameUsd, clearedUsd, delTime))
        }

        onDataChangedCallback?.invoke()
        return Pair(clearedUzs, clearedUsd)
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

    fun getAllTransactionsBoth(): List<TxRec> {
        return getTransactions(false) + getTransactions(true)
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
    private var adminTelegramId = "@QarzFy_Admin"
    private var trialDaysLimit = 5L
    private var isVipLifetime = false
    private var subscriptionEndMillis = 0L
    private var storeCountry = "O'zbekiston"
    private var trialStartMillis = 0L
    private fun speakDebtReminderUzbek(clientName: String, amount: Long, dueDate: String) {
        // Ovozli eslatma sintezi
    }

    private var isAutoSmsEnabled = true
    private var isPinSecurityEnabled = true
    private var isBiometricEnabled = true
    private var isPinUnlockedThisSession = false

    private var ownerName = ""

    private val SERVER_URL = "https://hasanjaylovov.pythonanywhere.com"
    private val BOT_USERNAME = "qarzdaftar_pay_bot"
    private val PICK_IMPORT_FILE = 701

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var db: QarzFyDatabase
    private lateinit var rootLayout: LinearLayout
    private var ttsEngine: TextToSpeech? = null

    private var isLoggedIn = false
    private var storeId = "QF-101"
    private var storeName = ""
    private var storeAddress = "O'zbekiston, Surxondaryo"
    private var storeCategory = "Qurilish mollari"
    private var mainCurrency = "UZS"
    private var userPhone = ""
    private var userPassword = ""
    private var subscriptionExpiryMillis = 0L
    private var isAutoVoiceCallEnabled = true
    private var appPinCode = ""
    private var tempPinBuffer = ""
    private var appPatternCode = ""
    private var tempPatternBuffer = ""
    private var lockAuthType = "PIN"
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
    private var personSelectedCurrency = "UZS"
    private var personModeTab = "Oddiy"
    private var personTopSectionTab = "Hisob-kitob"
    private var personViewStyle = "Ro'yxat"
    private var statSelectedCurrency = "UZS"

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
            try { savePermanentBackupToDownloadsQarzFy() } catch (_: Exception) {}
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
        if (isLoggedIn && isSubscriptionExpiredNow()) {
            renderSubscriptionBlockScreen()
            return
        }
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
        storeName = prefs.getString("store_name", "") ?: ""
        storeAddress = prefs.getString("store_address", "O'zbekiston") ?: "O'zbekiston"
        storeCategory = prefs.getString("store_category", "Qurilish mollari") ?: "Qurilish mollari"
        mainCurrency = prefs.getString("main_currency", "UZS") ?: "UZS"
        subscriptionExpiryMillis = prefs.getLong("sub_expiry_millis", 0L)
        isAutoVoiceCallEnabled = prefs.getBoolean("auto_voice_call", true)
        appPinCode = prefs.getString("app_pin_code", "") ?: ""
        appPatternCode = prefs.getString("app_pattern_code", "") ?: ""
        lockAuthType = prefs.getString("lock_auth_type", "PIN") ?: "PIN" 
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
                parseXlsxZipBytes(backupFile.readBytes())
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
                val msg = "Hurmatli ${c.name}, ${storeTitleWithLocativeSuffix()}n $amtStr qarzingizni to'lash muddati (${c.dueDate}) keldi. Iltimos, to'lovni amalga oshiring."
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








    private fun showStoreCategoryQuickSelectorDialog(onSelected: () -> Unit = {}) {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 42f)
            setPadding(32, 28, 32, 44)
        }
        wrap.addView(TextView(this).apply {
            text = "🏢 Faoliyat turi va Do'kon ma'lumotlari"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 18.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 6)
        })
        wrap.addView(TextView(this).apply {
            text = "Tanlangan faoliyat turiga qarab mijozlarga boradigan SMS xabarlar avtomatik moslashadi:"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12.5f
            setPadding(0, 0, 0, 16)
        })

        var chosenCat = detectEffectiveStoreCategory()
        val cardsHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }

        val items = listOf(
            Triple("Do'kon", "🏪  Do'kon (Market / Savdo)", "SMSda: «... do'konida sizga qarz yozildi»"),
            Triple("Dorixona", "💊  Dorixona (Apteka)", "SMSda: «... dorixonasida sizga qarz yozildi»"),
            Triple("Qurilish mollari do'koni", "🧱  Qurilish mollari do'koni", "SMSda: «... qurilish mollari do'konida qarz yozildi»")
        )

        fun rebuildCards() {
            cardsHolder.removeAllViews()
            items.forEach { (key, title, sub) ->
                val sel = (chosenCat == key)
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        cornerRadius = 24f
                        setColor(Color.parseColor(if (sel) "#EDE9FE" else "#F8FAFC"))
                        setStroke(if (sel) 4 else 2, Color.parseColor(if (sel) "#5946E8" else "#CBD5E1"))
                    }
                    setPadding(22, 16, 22, 16)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 }
                    setOnClickListener {
                        chosenCat = key
                        rebuildCards()
                    }
                }
                box.addView(TextView(this).apply {
                    text = if (sel) "✅  $title" else "⚪  $title"
                    setTextColor(Color.parseColor(if (sel) "#4338CA" else "#0F172A"))
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                })
                box.addView(TextView(this).apply {
                    text = sub
                    setTextColor(Color.parseColor("#64748B"))
                    textSize = 11.5f
                    setPadding(0, 4, 0, 0)
                })
                cardsHolder.addView(box)
            }
        }
        rebuildCards()
        wrap.addView(cardsHolder)

        val etName = makeGlassInput("🏢 Muassasa nomi (masalan: Shifo / Baraka)", storeName)
        val etOwner = makeGlassInput("👤 Do'konchi ism-familiyasi", ownerName)
        wrap.addView(etName)
        wrap.addView(etOwner)

        wrap.addView(TextView(this).apply {
            text = "💾  Saqlash"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(28f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
            setOnClickListener {
                val newSt = etName.text.toString().trim()
                val newOw = etOwner.text.toString().trim()
                storeCategory = chosenCat
                if (newSt.isNotEmpty()) storeName = newSt
                ownerName = newOw.ifEmpty { storeName }
                prefs.edit()
                    .putString("store_category", storeCategory)
                    .putString("store_name", storeName)
                    .putString("owner_name", ownerName)
                    .apply()
                Toast.makeText(this@MainActivity, "✅ Saqlandi: $storeName ($storeCategory) — $ownerName", Toast.LENGTH_LONG).show()
                sheet.dismiss()
                onSelected()
                renderMainWorkspace()
            }
        })

        sheet.setContentView(wrap)
        sheet.show()
    }

    private fun triggerBackgroundSmsAndVoiceCall(
        clientName: String,
        phone: String,
        amount: Long,
        kind: String,
        note: String,
        dueDate: String,
        isSupplier: Boolean,
        currency: String = "UZS"
    ) {
        if (phone.isBlank()) return
        if (!prefs.getBoolean("sms_enabled_$clientName", true)) return

        val personRecord = db.getPersonByName(clientName, isSupplier)
        val remUzs = if ((personRecord?.balUzs ?: 0L) < 0L) Math.abs(personRecord?.balUzs ?: 0L) else 0L
        val remUsd = if ((personRecord?.balUsd ?: 0.0) < 0.0) Math.abs((personRecord?.balUsd ?: 0.0).toLong()) else 0L

        val combinedRemaining = when {
            remUzs > 0L && remUsd > 0L -> "${formatMoney(remUzs, "so'm")} va ${formatMoney(remUsd, "USD")}"
            remUsd > 0L -> formatMoney(remUsd, "USD")
            remUzs > 0L -> formatMoney(remUzs, "so'm")
            else -> "0 so'm (Qarzingiz qolmadi)"
        }

        val curLabel = if (currency.equals("USD", ignoreCase = true)) "USD" else "so'm"
        val fmtAmt = formatMoney(Math.abs(amount), curLabel)
        val duePart = if (dueDate.isNotBlank() && dueDate != "Bugun") " To'lov muddati: $dueDate." else ""
        val cleanNote = note.trim()
        val prodPart = if (cleanNote.isNotEmpty() && !cleanNote.equals("Izohsiz", ignoreCase = true)) "Olingan mahsulot/izoh: $cleanNote. " else ""

        // Avtomatik ravishda: "... dorixonasida", "... qurilish mollari do'konida" yoki "... do'konida"
        val placeLocative = storeTitleWithLocativeSuffix()
        val placeAblative = storeTitleWithAblativeSuffix()

        val smsText = when (kind) {
            "Kirim" -> {
                "Hurmatli $clientName, $placeLocative $fmtAmt to'lovingiz qabul qilindi. ${prodPart}Qolgan umumiy qarzingiz: $combinedRemaining."
            }
            "Yopildi", "Ochirildi" -> {
                "Hurmatli $clientName, $placeAblative $fmtAmt qarzingiz yopildi ($cleanNote). Qolgan umumiy qarzingiz: $combinedRemaining."
            }
            "ToliqYopildi" -> {
                "Hurmatli $clientName, $placeAblative barcha qarzlaringiz ($fmtAmt) to'liq yopildi. Hozirgi qarzingiz: 0 so'm. Xaridingiz uchun rahmat!"
            }
            else -> {
                "Hurmatli $clientName, $placeLocative sizga $fmtAmt yangi qarz yozildi. ${prodPart}Jami umumiy qarzingiz: $combinedRemaining.$duePart"
            }
        }

        if (isAutoSmsEnabled) {
            sendAutoSms(phone, smsText)
        }
        if (isAutoVoiceCallEnabled && (kind == "Chiqim" || kind == "Eslatma")) {
            speakDebtReminderUzbek(clientName, Math.abs(amount), dueDate)
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
            text = "💬 Admin bilan suhbat ($ownerName)"
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

        // OBUNA MUDDATI TUGAGAN BO'LSA AVTOMATIK BLOKLASH VA OBUNA SO'RASH:
        if (isSubscriptionExpiredNow()) {
            renderSubscriptionBlockScreen()
            return
        }

        if (!isLoggedIn) {
            renderAuthScreen()
            return
        }

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

        
        try {
            if (mainFrame is LinearLayout) {
                (mainFrame as LinearLayout).addView(buildTopSubscriptionAndAdminBanner(), 0)
            }
        } catch (e: Exception) {}
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
                    text = "${tx.personName} • ${formatMoney(tx.amount, "so'm")}\n${tx.date}"
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

        // Rasmdagi "Barchasi 141  [+]" qatori
        val barchasiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
        }
        barchasiRow.addView(TextView(this).apply {
            text = "Barchasi"
            setTextColor(Color.parseColor("#5946E8"))
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
        })
        barchasiRow.addView(TextView(this).apply {
            text = "${allClients.size}"
            setTextColor(Color.parseColor("#5946E8"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#EDE9FE"))
            }
            setPadding(18, 6, 18, 6)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = 10; rightMargin = 16 }
        })
        barchasiRow.addView(TextView(this).apply {
            text = "+"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#475569"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            background = glossyRoundBtnBg("#F1F5F9", "#E2E8F0")
            layoutParams = LinearLayout.LayoutParams(68, 68)
            setOnClickListener { showAddClientBottomSheet(isSup) }
        })
        col.addView(barchasiRow)

        val etSearch = makeGlassInput("🔍 Ism yoki telefon raqam qidirish...", searchQuery)
        etSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE

        // Rasmdagi Sortirovka Bar: [ 🕒 Faol ] [ ⚠️ Muddat ] [ 💳 Summa ↓ ] [ 🔍 ] [ A-Z ] [ ▽ ]
        val sortBarCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 28f)
            setPadding(12, 10, 12, 10)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }

        fun sortChip(label: String, modeKey: String): View {
            val active = (currentSortMode == modeKey)
            return TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 11.5f
                setSingleLine(true)
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (active) Color.parseColor("#5946E8") else Color.parseColor("#1E293B"))
                background = if (active) GradientDrawable().apply {
                    cornerRadius = 22f
                    setColor(Color.parseColor("#EDE9FE"))
                } else null
                setPadding(16, 14, 16, 14)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 4 }
                setOnClickListener {
                    currentSortMode = modeKey
                    renderMainWorkspace()
                }
            }
        }

        sortBarCard.addView(sortChip("🕒 Faol", "Faol"))
        sortBarCard.addView(sortChip("⚠️ Muddat", "Muddat"))
        sortBarCard.addView(sortChip("💳 Summa ↓", "Summa"))
        sortBarCard.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })

        // 🔍 Qidiruv tugmasi
        sortBarCard.addView(TextView(this).apply {
            text = "🔍"
            gravity = Gravity.CENTER
            textSize = 14f
            setPadding(14, 12, 14, 12)
            setOnClickListener {
                etSearch.visibility = if (etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (etSearch.visibility == View.VISIBLE) etSearch.requestFocus()
            }
        })

        // [ A-Z ] tugmasi
        val isAz = (currentSortMode == "A-Z")
        sortBarCard.addView(TextView(this).apply {
            text = "A-Z"
            gravity = Gravity.CENTER
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (isAz) Color.WHITE else Color.parseColor("#0F172A"))
            background = if (isAz) glossyPurpleGrad(20f) else glossyCard("#FFFFFF", "#CBD5E1", 20f)
            setPadding(18, 12, 18, 12)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = 6; rightMargin = 6 }
            setOnClickListener {
                currentSortMode = if (currentSortMode == "A-Z") "Faol" else "A-Z"
                renderMainWorkspace()
            }
        })

        // [ ▽ ] Binafsha Filtr tugmasi
        sortBarCard.addView(TextView(this).apply {
            text = "▽"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(20f)
            layoutParams = LinearLayout.LayoutParams(72, 72)
            setOnClickListener {
                val nextMode = when (currentSortMode) {
                    "Faol" -> "Summa"
                    "Summa" -> "Muddat"
                    "Muddat" -> "A-Z"
                    else -> "Faol"
                }
                currentSortMode = nextMode
                renderMainWorkspace()
            }
        })

        col.addView(sortBarCard)
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
    private fun daysAgoText(dateStr: String): String {
        if (dateStr.isBlank()) return "Yaqinda qo'shilgan"
        return try {
            val clean = dateStr.take(10)
            val sdf = if (clean.contains("-")) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) else SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val d = sdf.parse(clean) ?: return dateStr
            val diffDays = ((System.currentTimeMillis() - d.time) / (1000L * 60L * 60L * 24L)).toInt()
            when {
                diffDays <= 0 -> "Bugun xarid qilgan"
                diffDays == 1 -> "1 kun oldin xarid qilgan"
                else -> "$diffDays kun oldin xarid qilgan"
            }
        } catch (_: Exception) {
            "Faol mijoz"
        }
    }

    // ✋ 1) MIJOZGA LIMIT QO'YISH OYNASI (3-rasmdagi dizayn)
    private fun showClientLimitBottomSheet(client: ClientRec, isSupplier: Boolean, onUpdated: () -> Unit) {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 42f)
            setPadding(34, 24, 34, 48)
        }
        wrap.addView(View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 10f
                setColor(Color.parseColor("#CBD5E1"))
            }
            layoutParams = LinearLayout.LayoutParams(110, 10).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 22
            }
        })
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        topRow.addView(TextView(this).apply {
            text = "Mijozga Limit qo'yish"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 18.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topRow.addView(TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 18f
            setPadding(16, 8, 16, 8)
            setOnClickListener { sheet.dismiss() }
        })
        wrap.addView(topRow)

        wrap.addView(TextView(this).apply {
            text = "Mijoz: ${client.name}"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 14f
            setPadding(0, 0, 0, 18)
        })

        wrap.addView(TextView(this).apply {
            text = "Limit miqdori"
            setTextColor(Color.parseColor("#1E293B"))
            textSize = 13.5f
            setPadding(0, 0, 0, 8)
        })

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 }
        }
        val savedLim = prefs.getLong("limit_${client.name}", 0L)
        val etLimit = EditText(this).apply {
            hint = "Masalan: 1000000"
            if (savedLim > 0L) setText(savedLim.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 14.5f
            background = glossyCard("#FFFFFF", "#CBD5E1", 24f)
            setPadding(28, 24, 28, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 10 }
        }
        var limCur = prefs.getString("limit_cur_${client.name}", "UZS") ?: "UZS"
        val tvCurBtn = TextView(this).apply {
            text = "$limCur   ▾"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            background = glossyCard("#F8FAFC", "#E2E8F0", 24f)
            setPadding(32, 24, 32, 24)
            setOnClickListener {
                limCur = if (limCur == "UZS") "USD" else "UZS"
                text = "$limCur   ▾"
            }
        }
        inputRow.addView(etLimit)
        inputRow.addView(tvCurBtn)
        wrap.addView(inputRow)

        wrap.addView(TextView(this).apply {
            text = "ⓘ  Limit qo'yilgandan so'ng, mijoz limitdan ko'proq qarz ololmaydi."
            setTextColor(Color.parseColor("#5946E8"))
            textSize = 12.5f
            background = GradientDrawable().apply {
                cornerRadius = 22f
                setColor(Color.parseColor("#EDE9FE"))
            }
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 22 }
        })

        wrap.addView(TextView(this).apply {
            text = "Saqlash"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(30f)
            setPadding(24, 26, 24, 26)
            setOnClickListener {
                val v = etLimit.text.toString().trim().toLongOrNull() ?: 0L
                prefs.edit().putLong("limit_${client.name}", v).putString("limit_cur_${client.name}", limCur).apply()
                Toast.makeText(this@MainActivity, "✅ ${client.name} uchun limit saqlandi: ${formatMoney(v, limCur)}", Toast.LENGTH_SHORT).show()
                sheet.dismiss()
                onUpdated()
            }
        })
        sheet.setContentView(wrap)
        sheet.show()
    }

    // 🏁 2) MIJOZ QR-KOD OYNASI (4-rasmdagi dizayn: QR-kod + PDF yuklab olish + Ulashish)
    private fun showClientQrCodeBottomSheet(client: ClientRec) {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 42f)
            setPadding(34, 24, 34, 48)
        }
        wrap.addView(View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 10f
                setColor(Color.parseColor("#CBD5E1"))
            }
            layoutParams = LinearLayout.LayoutParams(110, 10).apply { bottomMargin = 20 }
        })
        wrap.addView(TextView(this).apply {
            text = "QR-kod"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
        })
        wrap.addView(TextView(this).apply {
            text = client.name
            setTextColor(Color.parseColor("#64748B"))
            textSize = 14f
            setPadding(0, 4, 0, 20)
        })

        // Haqiqiy chizilgan QR-kod Bitmap
        val qrSize = 520
        val bmp = android.graphics.Bitmap.createBitmap(qrSize, qrSize, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = android.graphics.Paint().apply { color = Color.BLACK; style = android.graphics.Paint.Style.FILL }
        val grid = 21
        val cell = qrSize / (grid + 4)
        val offset = cell * 2

        fun drawFinder(gx: Int, gy: Int) {
            for (r in 0..6) {
                for (c in 0..6) {
                    val border = (r == 0 || r == 6 || c == 0 || c == 6)
                    val inner = (r in 2..4 && c in 2..4)
                    if (border || inner) {
                        canvas.drawRect(
                            (offset + (gx + c) * cell).toFloat(),
                            (offset + (gy + r) * cell).toFloat(),
                            (offset + (gx + c + 1) * cell).toFloat(),
                            (offset + (gy + r + 1) * cell).toFloat(),
                            paint
                        )
                    }
                }
            }
        }
        drawFinder(0, 0)
        drawFinder(grid - 7, 0)
        drawFinder(0, grid - 7)

        val seed = (client.name + client.phone + storeId).hashCode()
        val rnd = Random(seed.toLong())
        for (r in 0 until grid) {
            for (c in 0 until grid) {
                val inFinder = (r < 8 && c < 8) || (r < 8 && c >= grid - 8) || (r >= grid - 8 && c < 8)
                if (!inFinder && rnd.nextBoolean()) {
                    canvas.drawRect(
                        (offset + c * cell).toFloat(),
                        (offset + r * cell).toFloat(),
                        (offset + (c + 1) * cell).toFloat(),
                        (offset + (r + 1) * cell).toFloat(),
                        paint
                    )
                }
            }
        }

        val qrBox = FrameLayout(this).apply {
            background = glossyCard("#FFFFFF", "#E2E8F8", 32f)
            elevation = 6f
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(580, 580).apply { bottomMargin = 18 }
        }
        qrBox.addView(ImageView(this).apply {
            setImageBitmap(bmp)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        })
        wrap.addView(qrBox)

        wrap.addView(TextView(this).apply {
            text = "Mijoz hisob-kitob tarixini ko'rish uchun skanerlasin"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        })

        wrap.addView(TextView(this).apply {
            text = "⬇️   PDF / Excel yuklab olish"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(20, 18, 20, 22)
            setOnClickListener {
                val (_, path, _) = saveRealXlsxAndGetShareableUri()
                Toast.makeText(this@MainActivity, "✅ Saqlandi: $path", Toast.LENGTH_LONG).show()
            }
        })

        wrap.addView(TextView(this).apply {
            text = "📤   Ulashish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(30f)
            setPadding(24, 26, 24, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                sheet.dismiss()
                val shareText = "📊 ${client.name} — Hisob-kitob ma'lumoti\n🏪 Do'kon: $storeName\n📞 Tel: ${client.phone}\n🇺🇿 UZS Balans: ${formatMoney(client.balUzs, "so'm")}\n🇺🇸 USD Balans: ${formatMoney(client.balUsd.toLong(), "USD")}"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                startActivity(Intent.createChooser(intent, "Ulashish"))
            }
        })

        sheet.setContentView(wrap)
        sheet.show()
    }

    // ⚙️ 3) BILDIRISHNOMALAR SOZLAMALAR OYNASI (5-rasmdagi dizayn)
    private fun showClientNotificationSettingsDialog(client: ClientRec, isSupplier: Boolean) {
        val dlg = android.app.Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#F5F7FF"))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 30, 26, 48)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        topBar.addView(TextView(this).apply {
            text = "←"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(88, 88).apply { rightMargin = 12 }
            setOnClickListener { dlg.dismiss() }
        })
        topBar.addView(TextView(this).apply {
            text = "Bildirishnomalar Sozlamalar"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 18.5f
            setTypeface(null, Typeface.BOLD)
        })
        col.addView(topBar)

        fun settingSwitchCard(iconTxt: String, title: String, sub: String, prefKey: String, defaultVal: Boolean): View {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = glossyCard("#FFFFFF", "#E2E8F8", 28f)
                setPadding(24, 22, 24, 22)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
            }
            card.addView(TextView(this).apply {
                text = iconTxt
                textSize = 18f
                setPadding(0, 0, 18, 0)
            })
            val txtCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            txtCol.addView(TextView(this).apply {
                text = title
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
            })
            if (sub.isNotEmpty()) {
                txtCol.addView(TextView(this).apply {
                    text = sub
                    setTextColor(Color.parseColor("#64748B"))
                    textSize = 12f
                    setPadding(0, 4, 0, 0)
                })
            }
            card.addView(txtCol)
            val sw = Switch(this).apply {
                isChecked = prefs.getBoolean(prefKey, defaultVal)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean(prefKey, isChecked).apply()
                }
            }
            card.addView(sw)
            return card
        }

        col.addView(settingSwitchCard(
            "🟣",
            "Balanslarni valyutalar bo'yicha ko'rsatish",
            "Ro'yxatda ikkinchi balans, mijoz sahifasida har bir valyuta alohida ko'rinadi",
            "show_multi_currency_${client.name}",
            true
        ))

        col.addView(settingSwitchCard(
            "💱",
            "SMSda barcha valyuta balanslari",
            "Shu mijoz uchun yoqadi. O'chiq bo'lsa biznes sozlamasi amal qiladi. SMS narxi oshishi mumkin.",
            "sms_all_currencies_${client.name}",
            true
        ))

        // SMS xabarlar kartasi
        val smsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 28f)
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        smsCard.addView(TextView(this).apply {
            text = "Eslatmalar belgilangan kuni soat 09:00 da SIM-karta (yoki Avto-SMS) nomidan yuboriladi"
            setTextColor(Color.parseColor("#334155"))
            textSize = 12f
            setPadding(0, 0, 0, 12)
        })
        val smsToggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        smsToggleRow.addView(TextView(this).apply {
            text = "💬   SMS xabarlar"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        smsToggleRow.addView(Switch(this).apply {
            isChecked = prefs.getBoolean("sms_enabled_${client.name}", true)
            setOnCheckedChangeListener { _, chk ->
                prefs.edit().putBoolean("sms_enabled_${client.name}", chk).apply()
            }
        })
        smsCard.addView(smsToggleRow)
        col.addView(smsCard)

        // Telegram xabarlar kartasi
        val tgCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 28f)
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        tgCard.addView(TextView(this).apply {
            text = "Har bir tranzaksiya (Kirim-Chiqim, Eslatma va boshqa xabarlar) Telegram-Bot orqali yuboriladi."
            setTextColor(Color.parseColor("#334155"))
            textSize = 12f
            setPadding(0, 0, 0, 12)
        })
        val tgRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }
        tgRow.addView(TextView(this).apply {
            text = "✈️   Telegram xabarlar"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        tgRow.addView(Switch(this).apply {
            isChecked = isAutoVoiceCallEnabled
            setOnCheckedChangeListener { _, chk ->
                isAutoVoiceCallEnabled = chk
                prefs.edit().putBoolean("auto_voice_call", chk).apply()
            }
        })
        tgCard.addView(tgRow)

        val botBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 22f
                setColor(Color.parseColor("#F0F9FF"))
            }
            setPadding(22, 18, 22, 18)
        }
        botBox.addView(TextView(this).apply {
            text = "ⓘ Mijozlarga avtomatik xabar va eslatmalar yuborish uchun maxsus botimiz:"
            setTextColor(Color.parseColor("#0369A1"))
            textSize = 11.5f
            setPadding(0, 0, 0, 12)
        })
        val botShareRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        botShareRow.addView(TextView(this).apply {
            text = "@QarzFyBot"
            setTextColor(Color.parseColor("#0284C7"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        botShareRow.addView(TextView(this).apply {
            text = "📤 Ulashish"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#0284C7"))
            }
            setPadding(24, 14, 24, 14)
            setOnClickListener {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Assalomu alaykum ${client.name}! Qarzlaringiz va to'lovlaringiz haqida avtomatik xabar olish uchun @pDaftarClientBot ga kiring.")
                }
                startActivity(Intent.createChooser(i, "Bot havolasini ulashish"))
            }
        })
        botBox.addView(botShareRow)
        tgCard.addView(botBox)
        col.addView(tgCard)

        col.addView(TextView(this).apply {
            text = "Ushbu mijoz uchun SMS va Telegram xabarnomalar sozlamalari"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
            setPadding(4, 0, 4, 18)
        })

        val isBlocked = prefs.getBoolean("blocked_${client.name}", false)
        col.addView(TextView(this).apply {
            text = if (isBlocked) "🔓 Mijozni blokdan chiqarish" else "Mijozni blok qilish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(Color.parseColor("#B91C1C"))
            }
            setPadding(24, 26, 24, 26)
            setOnClickListener {
                val newState = !prefs.getBoolean("blocked_${client.name}", false)
                prefs.edit().putBoolean("blocked_${client.name}", newState).apply()
                Toast.makeText(this@MainActivity, if (newState) "🚫 ${client.name} bloklandi!" else "✅ Blokdan chiqarildi!", Toast.LENGTH_SHORT).show()
                dlg.dismiss()
            }
        })

        scroll.addView(col)
        dlg.setContentView(scroll)
        dlg.show()
    }

    // ✏️ 4) QALAMCHA BOSILGANDA: MIJOZ PROFILINI TAHRIRLASH (Rasm/Avatar, Ism-familiya, Raqam + Saqlash va SMS yuborish)
    private fun showEditClientProfileSheet(client: ClientRec, isSupplier: Boolean, onSaved: (ClientRec) -> Unit) {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 42f)
            setPadding(32, 26, 32, 48)
        }
        wrap.addView(TextView(this).apply {
            text = "✏️ Xaridor profilini tahrirlash"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 14)
        })

        val avatarEmojis = listOf("👤", "👨🏻‍💼", "👩🏻‍💼", "🧔🏻", "🧕🏻", "🏪", "🚚", "⭐")
        var chosenAvatar = prefs.getString("avatar_${client.name}", "👤") ?: "👤"

        val avatarPreview = TextView(this).apply {
            text = chosenAvatar
            gravity = Gravity.CENTER
            textSize = 36f
            background = glossyRoundBtnBg("#EDE9FE", "#6C4CF1")
            layoutParams = LinearLayout.LayoutParams(150, 150).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 12
            }
        }
        wrap.addView(avatarPreview)

        wrap.addView(TextView(this).apply {
            text = "📷 Profil belgisini (rasmini) tanlang:"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        })

        val emojiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        avatarEmojis.forEach { em ->
            emojiRow.addView(TextView(this).apply {
                text = em
                gravity = Gravity.CENTER
                textSize = 16f
                background = glossyRoundBtnBg("#F8FAFC", "#CBD5E1")
                layoutParams = LinearLayout.LayoutParams(78, 78).apply { setMargins(6, 0, 6, 0) }
                setOnClickListener {
                    chosenAvatar = em
                    avatarPreview.text = em
                }
            })
        }
        wrap.addView(emojiRow)

        val etNewName = makeGlassInput("👤 Ism va familiyasi", client.name)
        val etNewPhone = makeGlassInput("📞 Telefon raqami (+998...)", client.phone.ifEmpty { "+998" })
        etNewPhone.inputType = InputType.TYPE_CLASS_PHONE

        wrap.addView(etNewName)
        wrap.addView(etNewPhone)

        wrap.addView(TextView(this).apply {
            text = "💾  Saqlash va SMS Yuborish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(30f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
            setOnClickListener {
                val newNm = etNewName.text.toString().trim().ifEmpty { client.name }
                val newPh = etNewPhone.text.toString().trim()
                if (newPh.length < 7) {
                    Toast.makeText(this@MainActivity, "Telefon raqamni to'g'ri kiriting!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Barcha eski qarz va kirimlarni saqlagan holda ism va raqamni yangilaymiz
                db.updatePersonProfile(client.name, newNm, newPh, isSupplier)
                prefs.edit().putString("avatar_$newNm", chosenAvatar).apply()

                val remUzs = if (client.balUzs < 0) Math.abs(client.balUzs) else 0L
                val remUsd = if (client.balUsd < 0.0) Math.abs(client.balUsd.toLong()) else 0L
                val balText = if (remUzs > 0L || remUsd > 0L) {
                    "Joriy umumiy qarzingiz: ${formatMoney(remUzs, "so'm")}${if (remUsd > 0L) " va ${formatMoney(remUsd, "USD")}" else ""}."
                } else {
                    "Joriy qarzingiz: 0 so'm."
                }
                val smsMsg = "Hurmatli $newNm, ${storeTitleWithAblativeSuffix()} mijoz ma'lumotlaringiz yangilandi. $balText"
                sendAutoSms(newPh, smsMsg)

                Toast.makeText(this@MainActivity, "✅ Mijoz ma'lumotlari saqlandi va $newPh raqamiga SMS yuborildi!", Toast.LENGTH_LONG).show()
                sheet.dismiss()
                val updated = db.getPersonByName(newNm, isSupplier) ?: client.copy(name = newNm, phone = newPh)
                onSaved(updated)
            }
        })

        sheet.setContentView(wrap)
        sheet.show()
    }

    private fun showPersonFullHistoryAndActionSheet(initialClient: ClientRec, isSupplier: Boolean) {
        val client = db.getPersonByName(initialClient.name, isSupplier) ?: initialClient
        val dialog = android.app.Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen)

        val rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F8F9FE"))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 28, 26, 120)
        }

        val allPersonTxs = db.getTransactionsForPerson(client.name, isSupplier)
        val latestTxDate = allPersonTxs.firstOrNull()?.date ?: client.createdAt

        // 🗑 SAVAT BOSILGANDA: Mijozning barcha qarzlarini, kirim/chiqim/o'chirilgan tarixini 100% nolga tushirib, yangi xaridordek bo'm-bo'sh qilish!
        fun confirmWipePersonCompletelyClean() {
            val curUzsAbs = Math.abs(client.balUzs)
            val curUsdAbs = Math.abs(client.balUsd.toLong())
            AlertDialog.Builder(this)
                .setTitle("🗑 Barcha qarzlarni va tarixni tozalash")
                .setMessage("${client.name} olgan barcha qarzlar, kirimlar va chiqimlar butunlay o'chirilib, yangi xaridordek bo'm-bo'sh (0 UZS / 0 USD) holatga keltirilsinmi?")
                .setPositiveButton("✅ Ha, bo'm-bo'sh qilib tozalash") { _, _ ->
                    if (curUzsAbs > 0L || curUsdAbs > 0L) {
                        val repAmt = if (curUzsAbs > 0L) curUzsAbs else curUsdAbs
                        val repCur = if (curUzsAbs > 0L) "UZS" else "USD"
                        // Avval balansni 0 qilib tarixni butunlay tozalaymiz
                        db.wipePersonHistoryCompletely(client.name, isSupplier)
                        triggerBackgroundSmsAndVoiceCall(client.name, client.phone, repAmt, "ToliqYopildi", "Barcha qarzlar yopildi va tozalandi", "", isSupplier, repCur)
                    } else {
                        db.wipePersonHistoryCompletely(client.name, isSupplier)
                    }
                    Toast.makeText(this, "✅ ${client.name} tarixi to'liq tozalandi (0 UZS)!", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    renderMainWorkspace()
                    val freshClient = db.getPersonByName(client.name, isSupplier)
                    if (freshClient != null) showPersonFullHistoryAndActionSheet(freshClient, isSupplier)
                }
                .setNeutralButton("❌ Ro'yxatdan butunlay o'chirish") { _, _ ->
                    AlertDialog.Builder(this)
                        .setTitle("⚠️ Umumiy ro'yxatdan o'chirish")
                        .setMessage("${client.name} umumiy ro'yxatdan va bazadan butunlay o'chirib tashlansinmi?")
                        .setPositiveButton("❌ Ha, ro'yxatdan o'chirilsin") { _, _ ->
                            db.deletePersonFromRegistryCompletely(client.name, isSupplier)
                            Toast.makeText(this, "🗑 ${client.name} umumiy ro'yxatdan butunlay o'chirildi!", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                            renderMainWorkspace()
                        }
                        .setNegativeButton("Yo'q", null)
                        .show()
                }
                .setNegativeButton("Bekor qilish", null)
                .show()
        }

        // 1. YUQORI PANEL: [ ← ]          [ ✋ ] [ 🏁 ] [ ⚙️ ] [ ✏️ ] [ 🗑 ]
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        topBar.addView(TextView(this).apply {
            text = "←"
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(86, 86)
            setOnClickListener { dialog.dismiss(); renderMainWorkspace() }
        })
        topBar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })

        fun topActionIcon(symbol: String, bgHex: String, fgHex: String, onClick: () -> Unit): View {
            return TextView(this).apply {
                text = symbol
                gravity = Gravity.CENTER
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(fgHex))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(bgHex))
                }
                layoutParams = LinearLayout.LayoutParams(84, 84).apply { leftMargin = 10 }
                setOnClickListener { onClick() }
            }
        }

        // ✋ Limit qo'yish (3-rasm)
        topBar.addView(topActionIcon("✋", "#FEF3C7", "#D97706") {
            showClientLimitBottomSheet(client, isSupplier) {
                dialog.dismiss()
                showPersonFullHistoryAndActionSheet(client, isSupplier)
            }
        })
        // 🏁 QR-kod oynasi (4-rasm)
        topBar.addView(topActionIcon("⊞", "#EDE9FE", "#6C4CF1") {
            showClientQrCodeBottomSheet(client)
        })
        // ⚙️ Bildirishnomalar Sozlamalar (5-rasm)
        topBar.addView(topActionIcon("⚙️", "#EDE9FE", "#6C4CF1") {
            showClientNotificationSettingsDialog(client, isSupplier)
        })
        // ✏️ Profilni tahrirlash (Ism, rasm, raqam + SMS)
        topBar.addView(topActionIcon("✎", "#F1F5F9", "#0F172A") {
            showEditClientProfileSheet(client, isSupplier) { updatedClient ->
                dialog.dismiss()
                renderMainWorkspace()
                showPersonFullHistoryAndActionSheet(updatedClient, isSupplier)
            }
        })
        // 🗑 Savat (Barcha qarzlarni va tarixni to'liq o'chirib bo'm-bo'sh qilish)
        topBar.addView(topActionIcon("🗑", "#FEE2E2", "#B91C1C") {
            confirmWipePersonCompletelyClean()
        })
        wrap.addView(topBar)

        // 2. MARKAZIY AVATAR + ISM + OXIRGI XARID VAQTI + TELEFON
        val centerProfileBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        val customAvatar = prefs.getString("avatar_${client.name}", "👤") ?: "👤"
        val avatarFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(190, 190).apply { bottomMargin = 8 }
            setOnClickListener {
                showEditClientProfileSheet(client, isSupplier) { updatedClient ->
                    dialog.dismiss()
                    renderMainWorkspace()
                    showPersonFullHistoryAndActionSheet(updatedClient, isSupplier)
                }
            }
        }
        avatarFrame.addView(TextView(this).apply {
            text = customAvatar
            gravity = Gravity.CENTER
            textSize = 40f
            setTextColor(Color.parseColor("#1D4ED8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CBD5E1"))
            }
            layoutParams = FrameLayout.LayoutParams(170, 170, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        })
        avatarFrame.addView(TextView(this).apply {
            text = "📷"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#5946E8"))
                setStroke(4, Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(64, 64, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        })
        centerProfileBox.addView(avatarFrame)

        centerProfileBox.addView(TextView(this).apply {
            text = client.name
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        val limAmount = prefs.getLong("limit_${client.name}", 0L)
        val limCur = prefs.getString("limit_cur_${client.name}", "UZS") ?: "UZS"
        centerProfileBox.addView(TextView(this).apply {
            text = if (limAmount > 0L) "${daysAgoText(latestTxDate)} • Limit: ${formatMoney(limAmount, limCur)}" else daysAgoText(latestTxDate)
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 4)
        })
        centerProfileBox.addView(TextView(this).apply {
            text = client.phone.ifEmpty { "+998 -- --- -- --" }
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        wrap.addView(centerProfileBox)

        // 3. 6 TA DUMALOQ ALOQA TUGMALARI (6-rasmdagi kabi bir xil rang va ikonlar)
        val actionCirclesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        fun contactCircle(symbol: String, hexBg: String, fgHex: String = "#FFFFFF", onClick: () -> Unit): View {
            return TextView(this).apply {
                text = symbol
                gravity = Gravity.CENTER
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(fgHex))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hexBg))
                }
                layoutParams = LinearLayout.LayoutParams(94, 94).apply { setMargins(8, 0, 8, 0) }
                setOnClickListener { onClick() }
            }
        }
        actionCirclesRow.addView(contactCircle("📞", "#22C55E") {
            try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client.phone}"))) } catch (_: Exception) {}
        })
        actionCirclesRow.addView(contactCircle("💬", "#F97316") {
            val remUzs = Math.abs(client.balUzs)
            val remUsd = Math.abs(client.balUsd.toLong())
            val msg = "Hurmatli ${client.name}, ${storeTitleWithLocativeSuffix()} qolgan umumiy qarzingiz: ${formatMoney(remUzs, "so'm")}${if (remUsd > 0) " va ${formatMoney(remUsd, "USD")}" else ""}."
            sendAutoSms(client.phone, msg)
            Toast.makeText(this, "📩 SMS yuborildi!", Toast.LENGTH_SHORT).show()
        })
        actionCirclesRow.addView(contactCircle("✈️", "#0EA5E9") {
            try {
                val cleanDigits = client.phone.filter { it.isDigit() }
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+$cleanDigits")))
            } catch (_: Exception) {}
        })
        actionCirclesRow.addView(contactCircle("➕", "#E0F2FE", "#0284C7") {
            dialog.dismiss()
            showClientKirimChiqimBottomSheet(client, isSupplier, "Chiqim", personSelectedCurrency)
        })
        actionCirclesRow.addView(contactCircle("📥", "#6C4CF1") {
            dialog.dismiss()
            showExportBottomSheet()
        })
        actionCirclesRow.addView(contactCircle("📄", "#F59E0B") {
            showClientQrCodeBottomSheet(client)
        })
        wrap.addView(actionCirclesRow)

        // 4. [ 💱 Hisob-kitob ] [ 📄 Hisobotlar ] [ 🔔 Bildirishnomalar ]
        val threeTabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }
        listOf("💱 Hisob-kitob" to "Hisob-kitob", "📄 Hisobotlar" to "Hisobotlar", "🔔 Bildirishnomalar" to "Bildirishnomalar").forEach { (label, key) ->
            val active = (personTopSectionTab == key)
            threeTabsRow.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (active) Color.parseColor("#5946E8") else Color.parseColor("#475569"))
                background = GradientDrawable().apply {
                    cornerRadius = 22f
                    setColor(Color.parseColor(if (active) "#EDE9FE" else "#FFFFFF"))
                    setStroke(3, Color.parseColor(if (active) "#6C4CF1" else "#E2E8F0"))
                }
                setPadding(10, 18, 10, 18)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 0, 4, 0) }
                setOnClickListener {
                    personTopSectionTab = key
                    if (key == "Hisobotlar") {
                        showExportBottomSheet()
                    } else if (key == "Bildirishnomalar") {
                        showClientNotificationSettingsDialog(client, isSupplier)
                    } else {
                        dialog.dismiss()
                        showPersonFullHistoryAndActionSheet(client, isSupplier)
                    }
                }
            })
        }
        wrap.addView(threeTabsRow)

        // 5. [ Oddiy ] [ Bo'lib to'lash ] [ Ijara ]
        val modePillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 34f)
            setPadding(6, 6, 6, 6)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }
        listOf("Oddiy", "Bo'lib to'lash", "Ijara").forEach { m ->
            val sel = (personModeTab == m)
            modePillsRow.addView(TextView(this).apply {
                text = m
                gravity = Gravity.CENTER
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#1E293B"))
                background = if (sel) glossyPurpleGrad(28f) else null
                setPadding(12, 16, 12, 16)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    personModeTab = m
                    dialog.dismiss()
                    showPersonFullHistoryAndActionSheet(client, isSupplier)
                }
            })
        }
        wrap.addView(modePillsRow)

        // 6. SANA/VAQT VA SUMMA BO'YICHA MUKAMMAL SORTIROVKA QATORI
        val innerSortBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 26f)
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }
        fun innerSortChip(lbl: String, modeKey: String): View {
            val active = (currentSortMode == modeKey)
            return TextView(this).apply {
                text = lbl
                gravity = Gravity.CENTER
                textSize = 11f
                setSingleLine(true)
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (active) Color.parseColor("#5946E8") else Color.parseColor("#1E293B"))
                background = if (active) GradientDrawable().apply {
                    cornerRadius = 18f
                    setColor(Color.parseColor("#EDE9FE"))
                } else null
                setPadding(12, 10, 12, 10)
                setOnClickListener {
                    currentSortMode = modeKey
                    dialog.dismiss()
                    showPersonFullHistoryAndActionSheet(client, isSupplier)
                }
            }
        }
        innerSortBar.addView(innerSortChip("📅 Yangi", "Faol"))
        innerSortBar.addView(innerSortChip("📅 Eski", "Muddat"))
        innerSortBar.addView(innerSortChip("💳 Summa ↓", "Summa"))
        innerSortBar.addView(innerSortChip("💳 Summa ↑", "SummaAsc"))
        innerSortBar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        innerSortBar.addView(TextView(this).apply {
            text = "▽"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(18f)
            layoutParams = LinearLayout.LayoutParams(64, 64)
            setOnClickListener {
                currentSortMode = when (currentSortMode) {
                    "Faol" -> "Muddat"
                    "Muddat" -> "Summa"
                    "Summa" -> "SummaAsc"
                    else -> "Faol"
                }
                dialog.dismiss()
                showPersonFullHistoryAndActionSheet(client, isSupplier)
            }
        })
        wrap.addView(innerSortBar)

        // 7. BEVOSITA IZOH MAYDONI ("Izoh (Nima oldi?)") + YASHIL KIRIM / QIZIL CHIQIM TUGMALARI (6-rasm)
        val etQuickInlineNote = EditText(this).apply {
            hint = "Izoh (Nima oldi?)"
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#64748B"))
            textSize = 14.5f
            setSingleLine(true)
            background = glossyCard("#FFFFFF", "#CBD5E1", 24f)
            setPadding(28, 22, 28, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }

        val kirimChiqimRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }
        val kirimGreenBtn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#16A34A"))
            }
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
            setOnClickListener {
                val preNote = etQuickInlineNote.text.toString().trim()
                dialog.dismiss()
                showClientKirimChiqimBottomSheet(client, isSupplier, "Kirim", personSelectedCurrency, preNote)
            }
        }
        kirimGreenBtn.addView(TextView(this).apply {
            text = "$ +  Kirim"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        })
        kirimGreenBtn.addView(TextView(this).apply {
            text = "Qarz o'chirish"
            setTextColor(Color.parseColor("#DCFCE7"))
            textSize = 12f
        })

        val chiqimRedBtn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#B91C1C"))
            }
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 8 }
            setOnClickListener {
                val preNote = etQuickInlineNote.text.toString().trim()
                dialog.dismiss()
                showClientKirimChiqimBottomSheet(client, isSupplier, "Chiqim", personSelectedCurrency, preNote)
            }
        }
        chiqimRedBtn.addView(TextView(this).apply {
            text = "$ -  Chiqim"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        })
        chiqimRedBtn.addView(TextView(this).apply {
            text = "Qarz yozish"
            setTextColor(Color.parseColor("#FEE2E2"))
            textSize = 12f
        })

        kirimChiqimRow.addView(kirimGreenBtn)
        kirimChiqimRow.addView(chiqimRedBtn)
        wrap.addView(kirimChiqimRow)

        // 8. VALYUTA (UZS | USD), IZOH MAYDONI VA BALANS KARTASI (6-rasmdagi tartibda)
        val balanceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 28f)
            setPadding(22, 18, 22, 18)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        balanceCard.addView(etQuickInlineNote)

        val curAndBalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("UZS", "USD").forEach { cur ->
            val isCurSel = (personSelectedCurrency == cur)
            curAndBalRow.addView(TextView(this).apply {
                text = cur
                gravity = Gravity.CENTER
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isCurSel) Color.WHITE else Color.parseColor("#1E293B"))
                background = if (isCurSel) glossyPurpleGrad(30f) else GradientDrawable().apply {
                    cornerRadius = 30f
                    setColor(Color.WHITE)
                    setStroke(2, Color.parseColor("#CBD5E1"))
                }
                setPadding(32, 14, 32, 14)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 10 }
                setOnClickListener {
                    personSelectedCurrency = cur
                    dialog.dismiss()
                    showPersonFullHistoryAndActionSheet(client, isSupplier)
                }
            })
        }
        curAndBalRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        val activeBalVal: Long = if (personSelectedCurrency == "USD") client.balUsd.toLong() else client.balUzs
        curAndBalRow.addView(TextView(this).apply {
            text = "Balans: ${formatMoney(activeBalVal, personSelectedCurrency)}"
            setTextColor(if (activeBalVal < 0L) Color.parseColor("#B91C1C") else Color.parseColor("#15803D"))
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
        })
        balanceCard.addView(curAndBalRow)
        wrap.addView(balanceCard)

        // 9. [ ☷ Ro'yxat ] [ ⊞ Jadval ]   va   [ ▽ ]
        val viewToggleCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 26f)
            setPadding(14, 10, 20, 10)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        listOf("☷ Ro'yxat" to "Ro'yxat", "⊞ Jadval" to "Jadval").forEach { (lbl, key) ->
            val sel = (personViewStyle == key)
            viewToggleCard.addView(TextView(this).apply {
                text = lbl
                gravity = Gravity.CENTER
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.parseColor("#5946E8") else Color.parseColor("#1E293B"))
                background = if (sel) GradientDrawable().apply {
                    cornerRadius = 22f
                    setColor(Color.parseColor("#EDE9FE"))
                } else null
                setPadding(24, 12, 24, 12)
                setOnClickListener {
                    personViewStyle = key
                    dialog.dismiss()
                    showPersonFullHistoryAndActionSheet(client, isSupplier)
                }
            })
        }
        viewToggleCard.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        viewToggleCard.addView(TextView(this).apply {
            text = "▽"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                personSelectedCurrency = if (personSelectedCurrency == "UZS") "USD" else "UZS"
                dialog.dismiss()
                showPersonFullHistoryAndActionSheet(client, isSupplier)
            }
        })
        wrap.addView(viewToggleCard)

        // 10. TRANZAKSIYALAR RO'YXATI (Saralash bilan)
        val rawFiltered = allPersonTxs.filter { it.currency.equals(personSelectedCurrency, ignoreCase = true) }
        val filteredTxs = when (currentSortMode) {
            "Summa" -> rawFiltered.sortedByDescending { Math.abs(it.amount) }
            "SummaAsc" -> rawFiltered.sortedBy { Math.abs(it.amount) }
            "Muddat" -> rawFiltered.sortedBy { it.date } // Eski -> Yangi (Sana bo'yicha)
            else -> rawFiltered.sortedByDescending { it.date } // Yangi -> Eski (Sana bo'yicha)
        }

        if (filteredTxs.isEmpty()) {
            wrap.addView(TextView(this).apply {
                text = "Hozircha $personSelectedCurrency da qarz yoki to'lov yo'q (Top-toza)."
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#64748B"))
                textSize = 13f
                background = glossyCard("#FFFFFF", "#E2E8F8", 24f)
                setPadding(24, 36, 24, 36)
            })
        } else {
            filteredTxs.forEach { tx ->
                val isDeleted = tx.status.startsWith("O'chirildi") || tx.status.startsWith("To'liq")
                val isDebt = (tx.kind == "Chiqim")

                wrap.addView(TextView(this).apply {
                    text = tx.date.take(10)
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#64748B"))
                    textSize = 11.5f
                    background = glossyCard("#FFFFFF", "#E2E8F8", 20f)
                    setPadding(24, 6, 24, 6)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        bottomMargin = 8
                    }
                })

                val txCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = glossyCard("#FFFFFF", "#E2E8F8", 26f)
                    setPadding(22, 18, 22, 18)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
                }

                val topTxRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val signPrefix = if (isDebt) "-" else "+"
                topTxRow.addView(TextView(this).apply {
                    text = "$signPrefix${formatMoney(Math.abs(tx.amount), tx.currency)}"
                    setTextColor(if (isDeleted) Color.parseColor("#64748B") else if (isDebt) Color.parseColor("#B91C1C") else Color.parseColor("#16A34A"))
                    textSize = 17.5f
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                if (!isDeleted && isDebt) {
                    topTxRow.addView(TextView(this).apply {
                        text = "🗑 Qarzni yopish"
                        setTextColor(Color.WHITE)
                        textSize = 11f
                        setTypeface(null, Typeface.BOLD)
                        background = glossyRedGrad(18f)
                        setPadding(18, 10, 18, 10)
                        setOnClickListener {
                            db.markTransactionDeletedAndRevertBalance(tx)
                            val targetPh = if (tx.personPhone.isNotBlank()) tx.personPhone else client.phone
                            triggerBackgroundSmsAndVoiceCall(client.name, targetPh, tx.amount, "Yopildi", tx.note.ifEmpty { "Qarz yopildi" }, tx.dueDate, isSupplier, tx.currency)
                            Toast.makeText(this@MainActivity, "✅ Qarz yopildi va SMS yuborildi!", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            renderMainWorkspace()
                            val updatedPerson = db.getPersonByName(client.name, isSupplier)
                            if (updatedPerson != null) showPersonFullHistoryAndActionSheet(updatedPerson, isSupplier)
                        }
                    })
                }
                txCard.addView(topTxRow)

                // Mijoz olgan narsasi (izohi), vaqti va berishi kerak bo'lgan muddati alohida aniq ko'rinadi
                val itemNote = tx.note.ifEmpty { "Oddiy xarid" }
                val dueDisplay = if (tx.dueDate.isNotBlank() && tx.dueDate != "Bugun") "  |  ⏳ Muddat: ${tx.dueDate}" else ""
                
                txCard.addView(TextView(this).apply {
                    text = "🛒 Olgan narsasi (Izoh): $itemNote"
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 13.5f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 4, 0, 4)
                })

                txCard.addView(TextView(this).apply {
                    text = "🕒 Vaqti: ${tx.date}$dueDisplay"
                    setTextColor(Color.parseColor("#475569"))
                    textSize = 12f
                    setPadding(0, 2, 0, 6)
                })

                wrap.addView(txCard)
            }
        }

        // Mijozni umumiy ro'yxatdan butunlay o'chirish tugmasi
        wrap.addView(TextView(this).apply {
            text = "❌  Mijozni umumiy ro'yxatdan butunlay o'chirish"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#DC2626"))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(Color.parseColor("#FFF5F5"))
                setStroke(2, Color.parseColor("#FECACA"))
            }
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("⚠️ Umumiy ro'yxatdan o'chirish")
                    .setMessage("${client.name} va uning barcha ma'lumotlari umumiy ro'yxatdan butunlay o'chirib tashlansinmi?")
                    .setPositiveButton("❌ Ha, butunlay o'chirilsin") { _, _ ->
                        db.deletePersonFromRegistryCompletely(client.name, isSupplier)
                        Toast.makeText(this@MainActivity, "🗑 ${client.name} umumiy ro'yxatdan butunlay o'chirildi!", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        renderMainWorkspace()
                    }
                    .setNegativeButton("Bekor qilish", null)
                    .show()
            }
        })

        scroll.addView(wrap)
        rootFrame.addView(scroll)

        // Talabingizga binoan pastki o'ng burchakdagi 3 ta ortiqcha suzuvchi tugma (🤖 Robot, 🧮 Kalkulyator, 📦 Sotuv) butunlay olib tashlandi!

        dialog.setContentView(rootFrame)
        dialog.show()
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

    // ==================== TAB 3: STATISTIKA EKRANI (UZS VA USD DOLLAR HISOBI BILAN) ====================
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
            text = "Moliyaviy tahlil (UZS so'm va USD dollar hisoboti)"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
            setPadding(0, 2, 0, 14)
        })

        // UZS | USD Valyuta tanlash qatori
        val curRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }
        listOf("UZS (So'm)" to "UZS", "USD (Dollar $)" to "USD").forEach { (label, curKey) ->
            val sel = (statSelectedCurrency == curKey)
            curRow.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) Color.WHITE else Color.parseColor("#475569"))
                background = if (sel) glossyPurpleGrad(24f) else glossyCard("#FFFFFF", "#E2E8F8", 24f)
                setPadding(16, 16, 16, 16)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 0, 4, 0) }
                setOnClickListener { statSelectedCurrency = curKey; renderMainWorkspace() }
            })
        }
        col.addView(curRow)

        val isSup = (statTopTab == "Ta'minotchilar")
        val allTxs = db.getTransactions(isSup)
        val persons = db.getPersons(isSup)

        var kirimUzs = 0L
        var chiqimUzs = 0L
        var kirimUsd = 0L
        var chiqimUsd = 0L

        allTxs.forEach { tx ->
            val activeTx = !tx.status.startsWith("O'chirildi") && !tx.status.startsWith("To'liq")
            if (tx.currency.equals("USD", ignoreCase = true)) {
                if (tx.kind == "Kirim") kirimUsd += Math.abs(tx.amount)
                else if (activeTx) chiqimUsd += Math.abs(tx.amount)
            } else {
                if (tx.kind == "Kirim") kirimUzs += Math.abs(tx.amount)
                else if (activeTx) chiqimUzs += Math.abs(tx.amount)
            }
        }

        val qarzUzs = persons.filter { it.balUzs < 0 }.sumOf { Math.abs(it.balUzs) }
        val qarzUsd = persons.filter { it.balUsd < 0.0 }.sumOf { Math.abs(it.balUsd.toLong()) }

        val activeKirim = if (statSelectedCurrency == "USD") kirimUsd else kirimUzs
        val activeChiqim = if (statSelectedCurrency == "USD") chiqimUsd else chiqimUzs
        val activeQarz = if (statSelectedCurrency == "USD") qarzUsd else qarzUzs
        val activeAylanma = activeKirim + activeChiqim

        val chartHero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyPurpleGrad(34f)
            elevation = 10f
            setPadding(28, 26, 28, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        chartHero.addView(TextView(this).apply {
            text = "Umumiy aylanma ($statSelectedCurrency)"
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 12.5f
        })
        chartHero.addView(TextView(this).apply {
            text = formatMoney(activeAylanma, statSelectedCurrency)
            setTextColor(Color.WHITE)
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 6, 0, 10)
        })
        chartHero.addView(TextView(this).apply {
            text = "💵 UZS Kirim: ${formatMoney(kirimUzs, "so'm")}  |  💲 USD Kirim: ${formatMoney(kirimUsd, "USD")}"
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

        // UZS va USD batafsil kartochkalari
        val summaryCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 30f)
            setPadding(24, 22, 24, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        summaryCard.addView(TextView(this).apply {
            text = "📊 Valyutalar bo'yicha to'liq hisobot ($statTopTab)"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })
        summaryCard.addView(TextView(this).apply {
            text = "🇺🇿 SO'M (UZS) HISOBI:\n• Kirim (Qaytgan): +${formatMoney(kirimUzs, "so'm")}\n• Chiqim (Berilgan qarz): -${formatMoney(chiqimUzs, "so'm")}\n• Hozirgi qolgan qarz: ${formatMoney(qarzUzs, "so'm")}\n\n🇺🇸 DOLLAR (USD $) HISOBI:\n• Kirim (Qaytgan USD): +${formatMoney(kirimUsd, "USD")}\n• Chiqim (Berilgan USD qarz): -${formatMoney(chiqimUsd, "USD")}\n• Hozirgi qolgan USD qarz: ${formatMoney(qarzUsd, "USD")}"
            setTextColor(Color.parseColor("#334155"))
            textSize = 13f
        })
        col.addView(summaryCard)

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

    private fun xmlUnescape(s: String): String {
        return s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private fun detectEffectiveStoreCategory(): String {
        val savedCat = (prefs.getString("store_category", storeCategory) ?: storeCategory).trim()
        val lowerName = storeName.lowercase(Locale.getDefault())
        return when {
            savedCat.contains("Dorixona", ignoreCase = true) || lowerName.contains("dorixona") || lowerName.contains("apteka") || lowerName.contains("farm") -> "Dorixona"
            savedCat.contains("Qurilish", ignoreCase = true) || lowerName.contains("qurilish") || lowerName.contains("stroy") || lowerName.contains("sement") -> "Qurilish mollari do'koni"
            savedCat.contains("Oshxona", ignoreCase = true) || lowerName.contains("oshxona") || lowerName.contains("kafe") || lowerName.contains("choyxona") || lowerName.contains("restoran") -> "Oshxona"
            else -> "Do'kon"
        }
    }

    private fun storeTitleWithLocativeSuffix(): String {
        val cat = detectEffectiveStoreCategory()
        val rawName = storeName.trim().ifEmpty { cat }
        val low = rawName.lowercase(Locale.getDefault())
        return when (cat) {
            "Dorixona" -> {
                when {
                    low.endsWith("dorixona") -> "$rawName" + "sida"
                    low.endsWith("dorixonasi") -> "$rawName" + "da"
                    else -> "$rawName dorixonasida"
                }
            }
            "Qurilish mollari do'koni" -> {
                when {
                    low.contains("qurilish") && low.endsWith("do'koni") -> "$rawName" + "da"
                    low.contains("qurilish") -> "$rawName do'konida"
                    else -> "$rawName qurilish mollari do'konida"
                }
            }
            "Oshxona" -> {
                when {
                    low.endsWith("oshxona") -> "$rawName" + "sida"
                    low.endsWith("oshxonasi") -> "$rawName" + "da"
                    low.endsWith("kafe") || low.endsWith("restoran") -> "$rawName" + "da"
                    else -> "$rawName oshxonasida"
                }
            }
            else -> {
                when {
                    low.endsWith("do'kon") || low.endsWith("dokon") -> "$rawName" + "ida"
                    low.endsWith("do'koni") || low.endsWith("dokoni") -> "$rawName" + "da"
                    else -> "$rawName do'konida"
                }
            }
        }
    }

    private fun storeTitleWithAblativeSuffix(): String {
        val cat = detectEffectiveStoreCategory()
        val rawName = storeName.trim().ifEmpty { cat }
        val low = rawName.lowercase(Locale.getDefault())
        return when (cat) {
            "Dorixona" -> {
                when {
                    low.endsWith("dorixona") -> "$rawName" + "sidagi"
                    low.endsWith("dorixonasi") -> "$rawName" + "dagi"
                    else -> "$rawName dorixonasidagi"
                }
            }
            "Qurilish mollari do'koni" -> {
                when {
                    low.contains("qurilish") -> "$rawName do'konidagi"
                    else -> "$rawName qurilish mollari do'konidagi"
                }
            }
            "Oshxona" -> {
                when {
                    low.endsWith("oshxona") -> "$rawName" + "sidagi"
                    low.endsWith("oshxonasi") -> "$rawName" + "dagi"
                    else -> "$rawName oshxonasidagi"
                }
            }
            else -> {
                when {
                    low.endsWith("do'kon") || low.endsWith("dokon") -> "$rawName" + "idagi"
                    low.endsWith("do'koni") || low.endsWith("dokoni") -> "$rawName" + "dagi"
                    else -> "$rawName do'konidagi"
                }
            }
        }
    }

    private fun parseXlsxZipBytes(bytes: ByteArray): Boolean {
        return try {
            val zis = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zis.nextEntry
            val sheetMap = mutableMapOf<String, MutableList<List<String>>>()
            val sharedStrings = mutableListOf<String>()

            val allEntries = mutableMapOf<String, ByteArray>()
            while (entry != null) {
                allEntries[entry.name] = zis.readBytes()
                entry = zis.nextEntry
            }
            zis.close()

            val ssBytes = allEntries["xl/sharedStrings.xml"]
            if (ssBytes != null) {
                val ssXml = String(ssBytes, Charsets.UTF_8)
                val matcher = java.util.regex.Pattern.compile("<t[^>]*>(.*?)</t>", java.util.regex.Pattern.DOTALL).matcher(ssXml)
                while (matcher.find()) {
                    sharedStrings.add(xmlUnescape(matcher.group(1) ?: ""))
                }
            }

            val wbBytes = allEntries["xl/workbook.xml"]
            val sheetNames = mutableListOf<String>()
            if (wbBytes != null) {
                val wbXml = String(wbBytes, Charsets.UTF_8)
                val mSheet = java.util.regex.Pattern.compile("<sheet[^>]*name=\"([^\"]+)\"", java.util.regex.Pattern.DOTALL).matcher(wbXml)
                while (mSheet.find()) {
                    sheetNames.add(xmlUnescape(mSheet.group(1) ?: ""))
                }
            }

            for (i in 1..6) {
                val sBytes = allEntries["xl/worksheets/sheet$i.xml"] ?: continue
                val sName = if (i - 1 < sheetNames.size) sheetNames[i - 1] else "Sheet$i"
                val sXml = String(sBytes, Charsets.UTF_8)
                val rows = mutableListOf<List<String>>()

                val rowMatcher = java.util.regex.Pattern.compile("<row[^>]*>(.*?)</row>", java.util.regex.Pattern.DOTALL).matcher(sXml)
                while (rowMatcher.find()) {
                    val rowContent = rowMatcher.group(1) ?: ""
                    val rowVals = mutableListOf<String>()
                    val cellMatcher = java.util.regex.Pattern.compile("<c[^>]*r=\"([A-Z]+)[0-9]+\"[^>]*>(.*?)</c>", java.util.regex.Pattern.DOTALL).matcher(rowContent)
                    val cellMap = mutableMapOf<String, String>()
                    while (cellMatcher.find()) {
                        val col = cellMatcher.group(1) ?: ""
                        val cBody = cellMatcher.group(2) ?: ""
                        var cellVal = ""
                        if (cBody.contains("<is><t>")) {
                            val tm = java.util.regex.Pattern.compile("<t[^>]*>(.*?)</t>", java.util.regex.Pattern.DOTALL).matcher(cBody)
                            if (tm.find()) cellVal = xmlUnescape(tm.group(1) ?: "")
                        } else if (cBody.contains("<v>")) {
                            val vm = java.util.regex.Pattern.compile("<v>(.*?)</v>").matcher(cBody)
                            if (vm.find()) {
                                val rawNum = vm.group(1) ?: ""
                                cellVal = if (cellMatcher.group(0)?.contains("t=\"s\"") == true) {
                                    val idx = rawNum.toIntOrNull() ?: 0
                                    if (idx < sharedStrings.size) sharedStrings[idx] else rawNum
                                } else {
                                    rawNum
                                }
                            }
                        }
                        cellMap[col] = cellVal
                    }
                    val colLetters = arrayOf("A", "B", "C", "D", "E", "F", "G", "H", "I")
                    for (cl in colLetters) {
                        rowVals.add(cellMap[cl] ?: "")
                    }
                    rows.add(rowVals)
                }
                sheetMap[sName] = rows
            }

            // 1. Mijozlar
            val clientRows = sheetMap["Mijozlar"] ?: sheetMap.values.firstOrNull()
            if (clientRows != null && clientRows.size > 1) {
                for (r in 1 until clientRows.size) {
                    val row = clientRows[r]
                    val name = row.getOrNull(1)?.trim() ?: ""
                    val phone = row.getOrNull(2)?.trim() ?: ""
                    val balUsd = row.getOrNull(3)?.toDoubleOrNull() ?: 0.0
                    val balUzs = row.getOrNull(4)?.toDoubleOrNull()?.toLong() ?: 0L
                    val dateStr = row.getOrNull(5)?.trim() ?: ""
                    if (name.isNotEmpty() && !name.equals("Ism", ignoreCase = true)) {
                        db.insertOrUpdatePerson(name, phone, balUzs, balUsd, false, dateStr)
                    }
                }
            }

            // 2. Tranzaksiyalar (Mijozlar)
            val txRows = sheetMap["Tranzaksiyalar"]
            if (txRows != null && txRows.size > 1) {
                for (r in 1 until txRows.size) {
                    val row = txRows[r]
                    val txId = row.getOrNull(0)?.trim() ?: ""
                    val kind = row.getOrNull(1)?.trim() ?: "Chiqim"
                    val pName = row.getOrNull(2)?.trim() ?: ""
                    val pPhone = row.getOrNull(3)?.trim() ?: ""
                    val amt = row.getOrNull(4)?.toDoubleOrNull()?.toLong() ?: 0L
                    val cur = row.getOrNull(5)?.trim() ?: "UZS"
                    val date = row.getOrNull(6)?.trim() ?: ""
                    val rawDesc = row.getOrNull(7)?.trim() ?: ""
                    val st = row.getOrNull(8)?.trim() ?: "Faol"

                    var dueDate = ""
                    var cleanNote = rawDesc
                    if (rawDesc.contains("(Muddat:")) {
                        val mDue = java.util.regex.Pattern.compile("\\(Muddat:\\s*([^\\)]+)\\)").matcher(rawDesc)
                        if (mDue.find()) {
                            dueDate = mDue.group(1)?.trim() ?: ""
                        }
                        cleanNote = rawDesc.substringBefore("(Muddat:").trim()
                    }
                    if (pName.isNotEmpty() && amt != 0L && !pName.equals("Mijoz ismi", ignoreCase = true)) {
                        db.insertTransactionDirect(txId, pName, pPhone, kind, amt, cur, cleanNote, date, dueDate, st, false)
                    }
                }
            }

            // 3. Ta'minotchilar
            val supRows = sheetMap["Ta'minotchilar"] ?: sheetMap["Taminotchilar"]
            if (supRows != null && supRows.size > 1) {
                for (r in 1 until supRows.size) {
                    val row = supRows[r]
                    val name = row.getOrNull(1)?.trim() ?: ""
                    val phone = row.getOrNull(2)?.trim() ?: ""
                    val balUsd = row.getOrNull(3)?.toDoubleOrNull() ?: 0.0
                    val balUzs = row.getOrNull(4)?.toDoubleOrNull()?.toLong() ?: 0L
                    val dateStr = row.getOrNull(6)?.trim() ?: ""
                    if (name.isNotEmpty() && !name.equals("Ism", ignoreCase = true)) {
                        db.insertOrUpdatePerson(name, phone, balUzs, balUsd, true, dateStr)
                    }
                }
            }

            // 4. Ta'minotchi tranzaksiyalari
            val supTxRows = sheetMap["Ta'minotchi tranzaksiyalari"] ?: sheetMap["Taminotchi tranzaksiyalar"]
            if (supTxRows != null && supTxRows.size > 1) {
                for (r in 1 until supTxRows.size) {
                    val row = supTxRows[r]
                    val txId = row.getOrNull(0)?.trim() ?: ""
                    val kind = row.getOrNull(1)?.trim() ?: "Chiqim"
                    val pName = row.getOrNull(2)?.trim() ?: ""
                    val pPhone = row.getOrNull(3)?.trim() ?: ""
                    val amt = row.getOrNull(4)?.toDoubleOrNull()?.toLong() ?: 0L
                    val cur = row.getOrNull(5)?.trim() ?: "UZS"
                    val date = row.getOrNull(6)?.trim() ?: ""
                    val desc = row.getOrNull(7)?.trim() ?: ""
                    val st = row.getOrNull(8)?.trim() ?: "Faol"
                    if (pName.isNotEmpty() && amt != 0L) {
                        db.insertTransactionDirect(txId, pName, pPhone, kind, amt, cur, desc, date, "", st, true)
                    }
                }
            }

            // 5. Kassa yozuvlari
            val kassaRows = sheetMap["Kassa yozuvlari"]
            if (kassaRows != null && kassaRows.size > 1 && db.getKassaList().isEmpty()) {
                for (r in (kassaRows.size - 1) downTo 1) {
                    val row = kassaRows[r]
                    val kType = row.getOrNull(1)?.trim() ?: "Kirim"
                    val kCat = row.getOrNull(2)?.trim() ?: ""
                    val kAmt = row.getOrNull(3)?.toDoubleOrNull()?.toLong() ?: 0L
                    val kCur = row.getOrNull(4)?.trim() ?: "UZS"
                    val kPay = row.getOrNull(5)?.trim() ?: "Naqd"
                    val kDate = row.getOrNull(6)?.trim() ?: ""
                    val kNote = row.getOrNull(7)?.trim() ?: ""
                    val kCreator = row.getOrNull(8)?.trim() ?: "Avto"
                    if (kAmt > 0L) {
                        db.insertKassaDirect(kType, kCat, kAmt, kCur, kPay, kDate, kNote, kCreator)
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun generateRealXlsxZipBytes(): ByteArray {
        val clients = db.getPersons(false)
        val suppliers = db.getPersons(true)
        val clientTxs = db.getTransactions(false)
        val supplierTxs = db.getTransactions(true)
        val kassaList = db.getKassaList()
        val nowTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // 1-SAHIFA: Mijozlar
        val s1 = mutableListOf<List<String>>()
        s1.add(listOf("ID", "Ism", "Telefon raqami", "Balans USD", "Balans UZS", "Yaratilgan sana"))
        clients.forEach { c ->
            val dateStr = c.dueDate.ifEmpty { nowTime }
            s1.add(listOf(c.id.toString(), c.name, c.phone, c.balUsd.toString(), c.balUzs.toString(), dateStr))
        }

        // 2-SAHIFA: Tranzaksiyalar
        val s2 = mutableListOf<List<String>>()
        s2.add(listOf("Tranzaksiya ID", "Turi", "Mijoz ismi", "Mijoz telefoni", "Summa", "Valyuta", "Sana", "Tavsif", "Holat"))
        clientTxs.forEach { tx ->
            val desc = if (tx.note.isNotBlank() && tx.note != "Izohsiz") {
                if (tx.dueDate.isNotBlank() && tx.dueDate != "Bugun") "${tx.note} (Muddat: ${tx.dueDate})" else tx.note
            } else {
                if (tx.dueDate.isNotBlank() && tx.dueDate != "Bugun") "Tranzaksiya (Muddat: ${tx.dueDate})" else "Tranzaksiya"
            }
            s2.add(listOf(tx.txId, tx.kind, tx.personName, tx.personPhone, tx.amount.toString(), tx.currency, tx.date, desc, tx.status))
        }

        // 3-SAHIFA: Ta'minotchilar
        val s3 = mutableListOf<List<String>>()
        s3.add(listOf("ID", "Ism", "Telefon raqami", "Balans USD", "Balans UZS", "Manzil", "Yaratilgan sana"))
        suppliers.forEach { s ->
            val dateStr = s.dueDate.ifEmpty { nowTime }
            s3.add(listOf(s.id.toString(), s.name, s.phone, s.balUsd.toString(), s.balUzs.toString(), "O'zbekiston", dateStr))
        }

        // 4-SAHIFA: Ta'minotchi tranzaksiyalari
        val s4 = mutableListOf<List<String>>()
        s4.add(listOf("Tranzaksiya ID", "Turi", "Ta'minotchi ismi", "Ta'minotchi telefoni", "Summa", "Valyuta", "Sana", "Tavsif", "Holat"))
        supplierTxs.forEach { tx ->
            val desc = if (tx.note.isNotBlank() && tx.note != "Izohsiz") tx.note else "Tranzaksiya"
            s4.add(listOf(tx.txId, tx.kind, tx.personName, tx.personPhone, tx.amount.toString(), tx.currency, tx.date, desc, tx.status))
        }

        // 5-SAHIFA: Kassa xulosasi
        var kassaKirimUzs = 0L
        var kassaChiqimUzs = 0L
        var kassaKirimUsd = 0L
        var kassaChiqimUsd = 0L
        kassaList.forEach { k ->
            if (k.currency.equals("USD", ignoreCase = true)) {
                if (k.type == "Kirim") kassaKirimUsd += k.amount else kassaChiqimUsd += k.amount
            } else {
                if (k.type == "Kirim") kassaKirimUzs += k.amount else kassaChiqimUzs += k.amount
            }
        }
        val s5 = mutableListOf<List<String>>()
        s5.add(listOf("Valyuta", "Jami kirim", "Jami chiqim", "Balans", "Naqd kirim", "Plastik kirim", "Naqd chiqim", "Plastik chiqim"))
        s5.add(listOf(
            "UZS",
            kassaKirimUzs.toString(),
            kassaChiqimUzs.toString(),
            (kassaKirimUzs - kassaChiqimUzs).toString(),
            kassaKirimUzs.toString(),
            "0",
            kassaChiqimUzs.toString(),
            "0"
        ))
        if (kassaKirimUsd > 0L || kassaChiqimUsd > 0L) {
            s5.add(listOf(
                "USD",
                kassaKirimUsd.toString(),
                kassaChiqimUsd.toString(),
                (kassaKirimUsd - kassaChiqimUsd).toString(),
                kassaKirimUsd.toString(),
                "0",
                kassaChiqimUsd.toString(),
                "0"
            ))
        }

        // 6-SAHIFA: Kassa yozuvlari
        val s6 = mutableListOf<List<String>>()
        s6.add(listOf("ID", "Turi", "Kategoriya", "Summa", "Valyuta", "To'lov turi", "Sana", "Tavsif", "Kim yaratdi"))
        kassaList.forEachIndexed { idx, k ->
            s6.add(listOf(
                (kassaList.size - idx).toString(),
                k.type,
                k.category,
                k.amount.toString(),
                k.currency,
                k.payType.ifEmpty { "Naqd" },
                k.date,
                k.note.ifEmpty { "Tranzaksiya" },
                k.creator.ifEmpty { "Avto" }
            ))
        }

        val allSheets = listOf(
            "Mijozlar" to s1,
            "Tranzaksiyalar" to s2,
            "Ta'minotchilar" to s3,
            "Ta'minotchi tranzaksiyalari" to s4,
            "Kassa xulosasi" to s5,
            "Kassa yozuvlari" to s6
        )

        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)

        fun addZipEntry(name: String, content: String) {
            zos.putNextEntry(ZipEntry(name))
            zos.write(content.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val overridesXml = StringBuilder()
        for (i in 1..allSheets.size) {
            overridesXml.append("  <Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n")
        }

        addZipEntry(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
$overridesXml</Types>"""
        )

        addZipEntry(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
        )

        val wbRelsXml = StringBuilder()
        for (i in 1..allSheets.size) {
            wbRelsXml.append("  <Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>\n")
        }

        addZipEntry(
            "xl/_rels/workbook.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$wbRelsXml</Relationships>"""
        )

        val sheetsXml = StringBuilder()
        allSheets.forEachIndexed { idx, (sheetName, _) ->
            val sNum = idx + 1
            sheetsXml.append("    <sheet name=\"${xmlEscape(sheetName)}\" sheetId=\"$sNum\" r:id=\"rId$sNum\"/>\n")
        }

        addZipEntry(
            "xl/workbook.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
$sheetsXml  </sheets>
</workbook>"""
        )

        val colLetters = arrayOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")
        allSheets.forEachIndexed { idx, (_, rows) ->
            val sNum = idx + 1
            val sb = StringBuilder()
            sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")

            rows.forEachIndexed { rIdx, rowVals ->
                val rNum = rIdx + 1
                sb.append("<row r=\"$rNum\">")
                for (cIdx in 0 until minOf(rowVals.size, colLetters.size)) {
                    val cellRef = "${colLetters[cIdx]}$rNum"
                    val rawVal = rowVals[cIdx]
                    val numVal = rawVal.toLongOrNull()
                    val doubleVal = rawVal.toDoubleOrNull()
                    if (rIdx >= 1 && numVal != null && !rawVal.startsWith("+") && rawVal.length < 15) {
                        sb.append("<c r=\"$cellRef\"><v>$numVal</v></c>")
                    } else if (rIdx >= 1 && doubleVal != null && !rawVal.startsWith("+") && rawVal.contains(".") && rawVal.length < 15) {
                        sb.append("<c r=\"$cellRef\"><v>$doubleVal</v></c>")
                    } else {
                        sb.append("<c r=\"$cellRef\" t=\"inlineStr\"><is><t>${xmlEscape(rawVal)}</t></is></c>")
                    }
                }
                sb.append("</row>")
            }

            sb.append("</sheetData></worksheet>")
            addZipEntry("xl/worksheets/sheet$sNum.xml", sb.toString())
        }

        zos.finish()
        zos.close()
        return baos.toByteArray()
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

    // ==================== SLIDE-UP BOTTOM SHEETS (UZS VA USD DOLLAR TANLOVI + KALENDAR + AVTO-SMS) ====================
    private fun buildCurrencySelectorRow(initialCur: String, onSelect: (String) -> Unit): LinearLayout {
        var selected = initialCur
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        fun renderBtns() {
            row.removeAllViews()
            listOf("🇺🇿 UZS (So'm)" to "UZS", "🇺🇸 USD (Dollar $)" to "USD").forEach { (lbl, code) ->
                val active = (selected == code)
                row.addView(TextView(this).apply {
                    text = lbl
                    gravity = Gravity.CENTER
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (active) Color.WHITE else Color.parseColor("#475569"))
                    background = if (active) glossyPurpleGrad(24f) else glossyCard("#F5F7FF", "#CBD5E1", 24f)
                    setPadding(16, 18, 16, 18)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 6, 0) }
                    setOnClickListener {
                        selected = code
                        onSelect(code)
                        renderBtns()
                    }
                })
            }
        }
        renderBtns()
        return row
    }

    private fun showAddClientBottomSheet(isSupplier: Boolean) {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 40f)
            setPadding(32, 26, 32, 44)
        }
        wrap.addView(TextView(this).apply {
            text = if (isSupplier) "➕ Yangi Ta'minotchi Qo'shish" else "➕ Yangi Qarzdor (Mijoz) Qo'shish"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 14)
        })

        var chosenCurrency = "UZS"
        wrap.addView(buildCurrencySelectorRow(chosenCurrency) { chosenCurrency = it })

        val etName = makeGlassInput("👤 Ism va familiyasi")
        val etPhone = makeGlassInput("📞 Telefon raqami (+998...)", "+998")
        val etInitDebt = makeGlassInput("💵 Boshlang'ich qarz summasi (UZS yoki USD)")
        etInitDebt.inputType = InputType.TYPE_CLASS_NUMBER
        val etDue = makeCalendarInput("📅 Qarzni qaytarish sanasi (Kalendar)")
        val etNote = makeGlassInput("📝 Izoh (Nima oldi?)")

        wrap.addView(etName)
        wrap.addView(etPhone)
        wrap.addView(etInitDebt)
        wrap.addView(etDue)
        wrap.addView(etNote)

        wrap.addView(TextView(this).apply {
            text = "💾  Saqlash va SMS Yuborish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(30f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
            setOnClickListener {
                val nm = etName.text.toString().trim()
                val ph = etPhone.text.toString().trim()
                val debt = etInitDebt.text.toString().trim().toLongOrNull() ?: 0L
                val due = etDue.text.toString().trim().ifEmpty { "Bugun" }
                val note = etNote.text.toString().trim().ifEmpty { "Yangi qarz ($chosenCurrency)" }
                if (nm.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Ismni kiriting!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                db.addOrUpdatePersonTx(nm, ph, debt, chosenCurrency, "Chiqim", due, note, isSupplier)
                if (debt > 0) {
                    triggerBackgroundSmsAndVoiceCall(nm, ph, debt, "Chiqim", note, due, isSupplier, chosenCurrency)
                }
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
            background = glossyCard("#FFFFFF", "#E2E8F8", 40f)
            setPadding(32, 26, 32, 44)
        }
        wrap.addView(TextView(this).apply {
            text = "⚡ Tezkor Qarz / To'lov (UZS yoki USD)"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 14)
        })

        var chosenCurrency = "UZS"
        wrap.addView(buildCurrencySelectorRow(chosenCurrency) { chosenCurrency = it })

        val etName = makeGlassInput("👤 Mijoz ismi")
        val etPhone = makeGlassInput("📞 Telefon raqami (+998...)", "+998")
        val etAmt = makeGlassInput("💵 Summa (UZS yoki USD)")
        etAmt.inputType = InputType.TYPE_CLASS_NUMBER
        val etDue = makeCalendarInput("📅 Qaytarish sanasi (Kalendar)")
        val etNote = makeGlassInput("📝 Izoh")

        wrap.addView(etName)
        wrap.addView(etPhone)
        wrap.addView(etAmt)
        wrap.addView(etDue)
        wrap.addView(etNote)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(TextView(this).apply {
            text = "🔴 Qarz Yozish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = glossyRedGrad(26f)
            setPadding(16, 22, 16, 22)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
            setOnClickListener {
                val nm = etName.text.toString().trim()
                val ph = etPhone.text.toString().trim()
                val amt = etAmt.text.toString().trim().toLongOrNull() ?: 0L
                val due = etDue.text.toString().trim()
                val nt = etNote.text.toString().trim().ifEmpty { "Tezkor qarz ($chosenCurrency)" }
                if (nm.isNotEmpty() && amt > 0) {
                    db.addOrUpdatePersonTx(nm, ph, amt, chosenCurrency, "Chiqim", due, nt, isSupplier)
                    triggerBackgroundSmsAndVoiceCall(nm, ph, amt, "Chiqim", nt, due, isSupplier, chosenCurrency)
                    sheet.dismiss()
                    renderMainWorkspace()
                }
            }
        })
        btnRow.addView(TextView(this).apply {
            text = "🟢 To'lov Olish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = glossyGreenGrad(26f)
            setPadding(16, 22, 16, 22)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 8 }
            setOnClickListener {
                val nm = etName.text.toString().trim()
                val ph = etPhone.text.toString().trim()
                val amt = etAmt.text.toString().trim().toLongOrNull() ?: 0L
                val due = etDue.text.toString().trim()
                val nt = etNote.text.toString().trim().ifEmpty { "To'lov qabul qilindi ($chosenCurrency)" }
                if (nm.isNotEmpty() && amt > 0) {
                    db.addOrUpdatePersonTx(nm, ph, amt, chosenCurrency, "Kirim", due, nt, isSupplier)
                    triggerBackgroundSmsAndVoiceCall(nm, ph, amt, "Kirim", nt, due, isSupplier, chosenCurrency)
                    sheet.dismiss()
                    renderMainWorkspace()
                }
            }
        })
        wrap.addView(btnRow)
        sheet.setContentView(wrap)
        sheet.show()
    }

    private fun showClientKirimChiqimBottomSheet(client: ClientRec, isSupplier: Boolean, defaultKind: String = "Chiqim", defaultCurrency: String = "UZS", prefilledNote: String = "") {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 40f)
            setPadding(32, 26, 32, 44)
        }
        val isChiqim = (defaultKind == "Chiqim")
        wrap.addView(TextView(this).apply {
            text = "${client.name} — ${if (isChiqim) "🔴 Qarz Yozish (Chiqim)" else "🟢 Qarz O'chirish (Kirim)"}"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 6)
        })
        wrap.addView(TextView(this).apply {
            text = "Joriy balans: ${formatMoney(client.balUzs, "UZS")}  |  ${formatMoney(client.balUsd.toLong(), "USD")}"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12.5f
            setPadding(0, 0, 0, 14)
        })

        var chosenCurrency = defaultCurrency
        wrap.addView(buildCurrencySelectorRow(chosenCurrency) { chosenCurrency = it })

        val etAmt = makeGlassInput("💵 Summa ($chosenCurrency da)")
        etAmt.inputType = InputType.TYPE_CLASS_NUMBER
        val etDue = makeCalendarInput("📅 Muddat sanasi (Kalendar)", client.dueDate)
        val etNote = makeGlassInput("📝 Izoh (Nima oldi / qanday mahsulot?)", prefilledNote)
        wrap.addView(etAmt)
        wrap.addView(etDue)
        wrap.addView(etNote)

        wrap.addView(TextView(this).apply {
            text = if (isChiqim) "➕  Qarz Yozish va SMS Yuborish" else "✅  To'lov Qabul Qilish va SMS"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            background = if (isChiqim) glossyRedGrad(28f) else glossyGreenGrad(28f)
            setPadding(22, 24, 22, 24)
            setOnClickListener {
                val amt = etAmt.text.toString().trim().toLongOrNull() ?: 0L
                val due = etDue.text.toString().trim()
                val nt = etNote.text.toString().trim().ifEmpty { if (isChiqim) "Qo'shimcha qarz" else "Qarz to'lovi" }
                if (amt > 0) {
                    db.addOrUpdatePersonTx(client.name, client.phone, amt, chosenCurrency, defaultKind, due, nt, isSupplier)
                    triggerBackgroundSmsAndVoiceCall(client.name, client.phone, amt, defaultKind, nt, due, isSupplier, chosenCurrency)
                    Toast.makeText(this@MainActivity, "✅ Saqlandi va $chosenCurrency qoldig'i bilan SMS yuborildi!", Toast.LENGTH_SHORT).show()
                    sheet.dismiss()
                    renderMainWorkspace()
                    val updated = db.getPersonByName(client.name, isSupplier)
                    if (updated != null) {
                        personSelectedCurrency = chosenCurrency
                        showPersonFullHistoryAndActionSheet(updated, isSupplier)
                    }
                }
            }
        })

        sheet.setContentView(wrap)
        sheet.show()
    }

    private fun showExportBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 38f)
            setPadding(32, 26, 32, 44)
        }
        wrap.addView(TextView(this).apply {
            text = "📤 Rasmiy Excel (.xlsx) Eksport va Ulashish"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        })
        wrap.addView(TextView(this).apply {
            text = "Barcha mijozlar (UZS va USD balanslari), qarzlar, to'lovlar va kassa to'liq jadval ko'rinishida Download/QarzFy papkasiga saqlanadi:"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12.5f
            setPadding(0, 0, 0, 18)
        })
        wrap.addView(TextView(this).apply {
            text = "📥  1. Telefon xotirasiga saqlash (Download/QarzFy)"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(26f)
            setPadding(20, 22, 20, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
            setOnClickListener {
                sheet.dismiss()
                val (_, path, _) = saveRealXlsxAndGetShareableUri()
                triggerAutoExportSignalAndNotification(path)
                Toast.makeText(this@MainActivity, "✅ To'liq Excel saqlandi:\n$path", Toast.LENGTH_LONG).show()
            }
        })
        wrap.addView(TextView(this).apply {
            text = "✈️  2. Telegram / Bluetooth orqali yuborish (.xlsx)"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyOrangeGrad(26f)
            setPadding(20, 22, 20, 22)
            setOnClickListener {
                sheet.dismiss()
                val (file, _, shareUri) = saveRealXlsxAndGetShareableUri()
                shareExcelUriToTelegramOrBluetooth(shareUri, file.name)
            }
        })
        sheet.setContentView(wrap)
        sheet.show()
    }


    private fun makeCalendarInput(hintText: String, initialValue: String = ""): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(initialValue)
            isFocusable = false
            isClickable = true
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            background = glossyCard("#F5F7FF", "#E2E8F8", 24f)
            setPadding(28, 22, 28, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
            setOnClickListener {
                val cal = Calendar.getInstance()
                android.app.DatePickerDialog(
                    this@MainActivity,
                    { _, year, month, dayOfMonth ->
                        val formatted = String.format(Locale.getDefault(), "%02d.%02d.%04d", dayOfMonth, month + 1, year)
                        setText(formatted)
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }
    }

    private fun triggerAutoExportSignalAndNotification(savedPath: String) {
        try {
            val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 85)
            toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 220)
        } catch (_: Exception) {}
        try {
            val chId = "qarzfy_export_channel"
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(chId, "QarzFy Excel Avto-Eksport", NotificationManager.IMPORTANCE_DEFAULT)
                nm.createNotificationChannel(ch)
            }
            val notif = NotificationCompat.Builder(this@MainActivity, chId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("📊 QarzDaftar Pro — Excel Saqlandi")
                .setContentText(savedPath)
                .setAutoCancel(true)
                .build()
            nm.notify(9019, notif)
        } catch (_: Exception) {}
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
        wrap.addView(TextView(this).apply {
            text = "👑"
            gravity = Gravity.CENTER
            textSize = 36f
            background = glossyPurpleGrad(65f)
            elevation = 10f
            layoutParams = LinearLayout.LayoutParams(130, 130).apply { bottomMargin = 18 }
        })
        val feats = listOf(
            "☑️  Cheksiz qarzlar (UZS va USD)",
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

    private fun showImportBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 38f)
            setPadding(32, 26, 32, 44)
        }
        wrap.addView(TextView(this).apply {
            text = "📥 Excel (.xlsx / .csv) Fayldan Import"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 14)
        })
        wrap.addView(TextView(this).apply {
            text = "📂  Excel (.xlsx) Faylni Tanlash"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(26f)
            setPadding(20, 22, 20, 22)
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

    // ==================== 4. .XLSX VA .CSV IMPORT PARSER ====================
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

    inner class FullPatternLockView(
        context: Context,
        private val onPatternCompleted: (String) -> Unit
    ) : View(context) {

        private val selectedNodes = mutableListOf<Int>()
        private var currentX = 0f
        private var currentY = 0f
        private var isDrawing = false
        private var isErrorState = false
        private var isProcessingResult = false

        private val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5946E8")
            strokeWidth = 16f
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }

        private val nodeOuterPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }

        private val nodeRingPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 6f
        }

        private val nodeInnerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }

        private fun getNodeCenter(index: Int): Pair<Float, Float> {
            val w = width.coerceAtLeast(300)
            val h = height.coerceAtLeast(300)
            val row = index / 3
            val col = index % 3
            val side = minOf(w, h).toFloat()
            val offsetX = (w - side) / 2f
            val offsetY = (h - side) / 2f
            val pad = side * 0.18f
            val step = ((side - 2f * pad) / 2f).coerceAtLeast(60f)
            return Pair(offsetX + pad + col * step, offsetY + pad + row * step)
        }

        fun showErrorAndReset() {
            isErrorState = true
            isProcessingResult = false
            invalidate()
            postDelayed({
                selectedNodes.clear()
                isErrorState = false
                invalidate()
            }, 400)
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return

            val activeColor = if (isErrorState) Color.parseColor("#EF4444") else Color.parseColor("#5946E8")
            linePaint.color = activeColor

            if (selectedNodes.isNotEmpty()) {
                val path = android.graphics.Path()
                val first = getNodeCenter(selectedNodes[0])
                path.moveTo(first.first, first.second)
                for (i in 1 until selectedNodes.size) {
                    val pt = getNodeCenter(selectedNodes[i])
                    path.lineTo(pt.first, pt.second)
                }
                if (isDrawing) {
                    path.lineTo(currentX, currentY)
                }
                canvas.drawPath(path, linePaint)
            }

            val side = minOf(width, height).toFloat().coerceAtLeast(300f)
            val outerRad = (side * 0.095f).coerceIn(32f, 58f)
            val innerRad = outerRad * 0.42f

            for (i in 0 until 9) {
                val (cx, cy) = getNodeCenter(i)
                val isSelected = selectedNodes.contains(i)
                if (isSelected) {
                    nodeOuterPaint.color = if (isErrorState) Color.parseColor("#FEE2E2") else Color.parseColor("#E0E7FF")
                    nodeRingPaint.color = activeColor
                    nodeInnerPaint.color = activeColor
                } else {
                    nodeOuterPaint.color = Color.WHITE
                    nodeRingPaint.color = Color.parseColor("#CBD5E1")
                    nodeInnerPaint.color = Color.parseColor("#64748B")
                }
                canvas.drawCircle(cx, cy, outerRad, nodeOuterPaint)
                canvas.drawCircle(cx, cy, outerRad, nodeRingPaint)
                canvas.drawCircle(cx, cy, innerRad, nodeInnerPaint)
            }
        }

        private fun checkHitNode(x: Float, y: Float): Int {
            val side = minOf(width, height).toFloat().coerceAtLeast(300f)
            val hitRadius = (side * 0.13f).coerceAtLeast(64f)
            for (i in 0 until 9) {
                val (cx, cy) = getNodeCenter(i)
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                    return i
                }
            }
            return -1
        }

        private fun addNodeWithIntermediate(targetNode: Int) {
            if (selectedNodes.isEmpty()) {
                selectedNodes.add(targetNode)
                return
            }
            val lastNode = selectedNodes.last()
            val r1 = lastNode / 3
            val c1 = lastNode % 3
            val r2 = targetNode / 3
            val c2 = targetNode % 3
            if ((r1 + r2) % 2 == 0 && (c1 + c2) % 2 == 0) {
                val midNode = ((r1 + r2) / 2) * 3 + ((c1 + c2) / 2)
                if (!selectedNodes.contains(midNode)) {
                    selectedNodes.add(midNode)
                }
            }
            if (!selectedNodes.contains(targetNode)) {
                selectedNodes.add(targetNode)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isErrorState || isProcessingResult) return true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    selectedNodes.clear()
                    isDrawing = true
                    currentX = event.x
                    currentY = event.y
                    val hit = checkHitNode(currentX, currentY)
                    if (hit != -1) selectedNodes.add(hit)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDrawing) return true
                    currentX = event.x
                    currentY = event.y
                    val hit = checkHitNode(currentX, currentY)
                    if (hit != -1 && !selectedNodes.contains(hit)) {
                        addNodeWithIntermediate(hit)
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    isDrawing = false
                    invalidate()
                    if (selectedNodes.isNotEmpty()) {
                        val patternStr = selectedNodes.joinToString("-")
                        isProcessingResult = true
                        // MUHIM: View hierarchy o'zgarishi onTouchEvent ichida emas, keyingi UI frame da bajariladi (Crash bo'lmaydi!)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            isProcessingResult = false
                            onPatternCompleted(patternStr)
                        }, 60)
                    }
                    return true
                }
            }
            return true
        }
    }

    private fun renderOnboardingAndLogin() {
        renderAuthScreen()
    }

    private fun renderAuthScreen() {
        if (isRegisterMode) {
            renderSeparateRegisterPage()
        } else {
            renderSeparateLoginPage()
        }
    }

    // ==================== 1. ALOHIDA KIRISH (LOGIN) SAHIFASI ====================
    private fun renderSeparateLoginPage() {
        isRegisterMode = false
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#F5F7FF"))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(34, 48, 34, 56)
        }

        // Yuqori brend kartasi
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glossyPurpleGrad(42f)
            elevation = 12f
            setPadding(34, 38, 34, 38)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }
        heroCard.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(115, 115).apply { bottomMargin = 10 }
        })
        heroCard.addView(TextView(this).apply {
            text = "Tizimga kirish (Login)"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        heroCard.addView(TextView(this).apply {
            text = "Mavjud hisobingizga telefon raqam va parol orqali kiring"
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 0)
        })
        container.addView(heroCard)

        val formCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 36f)
            elevation = 8f
            setPadding(28, 28, 28, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Ro'yxatdan o'tishda tanlangan Do'kon nomi, Sohasi va Davlati avtomatik chiqib turadi
        val savedStore = (prefs.getString("store_name", storeName) ?: storeName).trim()
        val savedOwner = (prefs.getString("owner_name", ownerName) ?: ownerName).trim()
        val savedCat = (prefs.getString("store_category", storeCategory) ?: storeCategory).trim().ifEmpty { "Do'kon" }
        val savedCountry = (prefs.getString("store_country", storeCountry) ?: storeCountry).trim().ifEmpty { "O'zbekiston" }

        val catEmoji = when {
            savedCat.contains("Dorixona", true) -> "💊"
            savedCat.contains("Qurilish", true) -> "🧱"
            savedCat.contains("Oshxona", true) -> "🍽"
            else -> "🏪"
        }

        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#EEF2FF"))
                setStroke(3, Color.parseColor("#6366F1"))
            }
            setPadding(22, 18, 22, 18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 18 }
        }

        if (savedStore.isNotEmpty()) {
            infoCard.addView(TextView(this).apply {
                text = "$catEmoji  Nomi: $savedStore"
                setTextColor(Color.parseColor("#1E1B4B"))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            })
            if (savedOwner.isNotEmpty()) {
                infoCard.addView(TextView(this).apply {
                    text = "👤  Mas'ul: $savedOwner"
                    setTextColor(Color.parseColor("#312E81"))
                    textSize = 13f
                    setPadding(0, 4, 0, 0)
                })
            }
            infoCard.addView(TextView(this).apply {
                text = "📌  Sohasi: $savedCat   |   🌍  Davlat: $savedCountry"
                setTextColor(Color.parseColor("#4338CA"))
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 6, 0, 0)
            })
        } else {
            infoCard.addView(TextView(this).apply {
                text = "ℹ️ Hali muassasa ro'yxatdan o'tkazilmagan.\nPastdagi «Ro'yxatdan o'tish» tugmasini bosing."
                setTextColor(Color.parseColor("#4338CA"))
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
            })
        }
        formCard.addView(infoCard)

        val etPhone = makeGlassInput("📞 Telefon raqamingiz (+998...)", userPhone.ifEmpty { "+998" })
        etPhone.inputType = InputType.TYPE_CLASS_PHONE
        val etPass = makeGlassInput("🔒 Parol", "")
        etPass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        formCard.addView(etPhone)
        formCard.addView(etPass)

        // [ 🔓 Tizimga kirish ] tugmasi
        formCard.addView(TextView(this).apply {
            text = "🔓  Tizimga kirish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(28f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10; bottomMargin = 16 }
            setOnClickListener {
                val ph = etPhone.text.toString().trim()
                val pw = etPass.text.toString().trim()
                if (ph.length < 7 || pw.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Telefon raqam va parolni kiriting!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (savedStore.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Avval Ro'yxatdan o'tish bo'limida muassasa va sohani tanlang!", Toast.LENGTH_LONG).show()
                    renderSeparateRegisterPage()
                    return@setOnClickListener
                }

                // Ro'yxatdan o'tishda tanlangan soha, do'kon nomi va davlat avtomatik olinadi (qayta so'ralmaydi!)
                storeName = savedStore
                ownerName = savedOwner.ifEmpty { savedStore }
                storeCategory = savedCat
                storeCountry = savedCountry
                userPhone = ph
                if (storeId.isEmpty()) storeId = "QF-" + (10000 + Random().nextInt(90000))
                if (trialStartMillis == 0L) trialStartMillis = System.currentTimeMillis()
                isLoggedIn = true

                prefs.edit()
                    .putBoolean("is_logged_in", true)
                    .putBoolean("has_selected_store_category_v21", true)
                    .putString("user_phone", userPhone)
                    .apply()

                Toast.makeText(this@MainActivity, "✅ Kirildi: $storeName ($storeCategory • $storeCountry)", Toast.LENGTH_SHORT).show()
                renderMainWorkspace()
            }
        })

        // Alohida Ro'yxatdan o'tish oynasiga o'tish tugmasi
        formCard.addView(TextView(this).apply {
            text = "Yangi hisob ochish / Sohani tanlash  ➔  📝 Ro'yxatdan o'tish"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#4338CA"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = glossyCard("#F1F5F9", "#CBD5E1", 24f)
            setPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                renderSeparateRegisterPage()
            }
        })

        container.addView(formCard)
        scroll.addView(container)
        rootLayout.addView(scroll)
    }

    // ==================== 2. ALOHIDA RO'YXATDAN O'TISH SAHIFASI ====================
    private fun renderSeparateRegisterPage() {
        isRegisterMode = true
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#F5F7FF"))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 38, 32, 56)
        }

        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glossyPurpleGrad(38f)
            elevation = 10f
            setPadding(28, 30, 28, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        heroCard.addView(TextView(this).apply {
            text = "📝 Ro'yxatdan o'tish"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        heroCard.addView(TextView(this).apply {
            text = "Faoliyat sohangizni, davlatingizni va muassasa nomini kiriting"
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 0)
        })
        container.addView(heroCard)

        val formCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 36f)
            elevation = 8f
            setPadding(26, 24, 26, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. FAOLIYAT SOHASINI TANLASH (4 ta soha: Do'kon, Dorixona, Qurilish mollari do'koni, Oshxona)
        formCard.addView(TextView(this).apply {
            text = "1️⃣  Faoliyat sohasini tanlang (SMS shunga moslashadi):"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(4, 0, 4, 10)
        })

        var selectedCat = storeCategory.ifEmpty { "Do'kon" }
        var selectedCountry = (prefs.getString("store_country", storeCountry) ?: storeCountry).ifEmpty { "O'zbekiston" }
        var etStoreInput: EditText? = null

        val catBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14 }
        }

        val catOptions = listOf(
            Triple("Do'kon", "🏪  Do'kon (Market / Savdo)", "SMSda: «... do'konida sizga qarz yozildi»"),
            Triple("Dorixona", "💊  Dorixona (Apteka / Farmatsevtika)", "SMSda: «... dorixonasida sizga qarz yozildi»"),
            Triple("Qurilish mollari do'koni", "🧱  Qurilish mollari do'koni", "SMSda: «... qurilish mollari do'konida qarz yozildi»"),
            Triple("Oshxona", "🍽  Oshxona (Kafe / Choyxona / Restoran)", "SMSda: «... oshxonasida sizga qarz yozildi»")
        )

        fun refreshCategoryCards() {
            catBox.removeAllViews()
            catOptions.forEach { (catKey, title, subtitle) ->
                val isSel = (selectedCat == catKey)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        cornerRadius = 22f
                        setColor(Color.parseColor(if (isSel) "#EDE9FE" else "#F8FAFC"))
                        setStroke(if (isSel) 4 else 2, Color.parseColor(if (isSel) "#5946E8" else "#CBD5E1"))
                    }
                    setPadding(20, 14, 20, 14)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 8 }
                    setOnClickListener {
                        selectedCat = catKey
                        storeCategory = catKey
                        refreshCategoryCards()
                        etStoreInput?.hint = when (catKey) {
                            "Dorixona" -> "💊 Dorixona nomi (masalan: Shifo Farm)"
                            "Qurilish mollari do'koni" -> "🧱 Qurilish do'koni nomi (masalan: Stroy Market)"
                            "Oshxona" -> "🍽 Oshxona nomi (masalan: Rayhon Milliy Taomlar)"
                            else -> "🏪 Do'kon nomi (masalan: Baraka Market)"
                        }
                    }
                }
                card.addView(TextView(this).apply {
                    text = if (isSel) "✅  $title" else "⚪  $title"
                    setTextColor(Color.parseColor(if (isSel) "#4338CA" else "#0F172A"))
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                })
                card.addView(TextView(this).apply {
                    text = subtitle
                    setTextColor(Color.parseColor("#64748B"))
                    textSize = 11.5f
                    setPadding(0, 3, 0, 0)
                })
                catBox.addView(card)
            }
        }
        refreshCategoryCards()
        formCard.addView(catBox)

        // 2. DAVLATNI TANLASH
        formCard.addView(TextView(this).apply {
            text = "2️⃣  Davlatingizni tanlang:"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(4, 2, 4, 8)
        })

        val countriesList = listOf("O'zbekiston", "Qozog'iston", "Qirg'iziston", "Tojikiston", "Rossiya")
        val countryRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14 }
        }

        fun refreshCountryButtons() {
            countryRow.removeAllViews()
            val wrapHoriz = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            countriesList.take(3).forEach { cName ->
                val active = (selectedCountry == cName)
                wrapHoriz.addView(TextView(this).apply {
                    text = if (active) "🌍 $cName ✓" else cName
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (active) Color.WHITE else Color.parseColor("#1E293B"))
                    background = if (active) glossyPurpleGrad(20f) else glossyCard("#F8FAFC", "#CBD5E1", 20f)
                    setPadding(12, 16, 12, 16)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 6 }
                    setOnClickListener {
                        selectedCountry = cName
                        refreshCountryButtons()
                    }
                })
            }
            countryRow.addView(wrapHoriz)

            val wrapHoriz2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 6 }
            }
            countriesList.drop(3).forEach { cName ->
                val active = (selectedCountry == cName)
                wrapHoriz2.addView(TextView(this).apply {
                    text = if (active) "🌍 $cName ✓" else cName
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (active) Color.WHITE else Color.parseColor("#1E293B"))
                    background = if (active) glossyPurpleGrad(20f) else glossyCard("#F8FAFC", "#CBD5E1", 20f)
                    setPadding(12, 16, 12, 16)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 6 }
                    setOnClickListener {
                        selectedCountry = cName
                        refreshCountryButtons()
                    }
                })
            }
            countryRow.addView(wrapHoriz2)
        }
        refreshCountryButtons()
        formCard.addView(countryRow)

        // 3. MUASSASA NOMI, MAS'UL SHAXS, TELEFON VA PAROL
        val storeHintTxt = when (selectedCat) {
            "Dorixona" -> "💊 Dorixona nomi (masalan: Shifo Farm)"
            "Qurilish mollari do'koni" -> "🧱 Qurilish do'koni nomi (masalan: Stroy Market)"
            "Oshxona" -> "🍽 Oshxona nomi (masalan: Rayhon Milliy Taomlar)"
            else -> "🏪 Do'kon nomi (masalan: Baraka Market)"
        }

        etStoreInput = makeGlassInput(storeHintTxt, storeName)
        val etOwnerInput = makeGlassInput("👤 Mas'ul shaxs (Ism-familiyangiz)", ownerName)
        val etPhone = makeGlassInput("📞 Telefon raqamingiz (+998...)", userPhone.ifEmpty { "+998" })
        etPhone.inputType = InputType.TYPE_CLASS_PHONE
        val etPass = makeGlassInput("🔒 Parol yarating", "")
        etPass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        formCard.addView(etStoreInput!!)
        formCard.addView(etOwnerInput)
        formCard.addView(etPhone)
        formCard.addView(etPass)

        // [ 🚀 Ro'yxatdan o'tish va Saqlash ]
        formCard.addView(TextView(this).apply {
            text = "🚀  Ro'yxatdan o'tish va Saqlash"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(28f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10; bottomMargin = 14 }
            setOnClickListener {
                val stName = etStoreInput?.text?.toString()?.trim() ?: ""
                val owName = etOwnerInput.text.toString().trim()
                val ph = etPhone.text.toString().trim()
                val pw = etPass.text.toString().trim()

                if (stName.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Iltimos, $selectedCat nomini kiriting!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (ph.length < 7 || pw.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Telefon raqam va parolni kiriting!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                storeCategory = selectedCat
                storeCountry = selectedCountry
                storeName = stName
                ownerName = owName.ifEmpty { stName }
                userPhone = ph
                if (storeId.isEmpty()) storeId = "QF-" + (10000 + Random().nextInt(90000))
                if (trialStartMillis == 0L) trialStartMillis = System.currentTimeMillis()
                isLoggedIn = true
                isRegisterMode = false

                prefs.edit()
                    .putBoolean("is_logged_in", true)
                    .putBoolean("has_selected_store_category_v21", true)
                    .putString("store_category", storeCategory)
                    .putString("store_country", storeCountry)
                    .putString("store_name", storeName)
                    .putString("owner_name", ownerName)
                    .putString("user_phone", userPhone)
                    .putString("store_id", storeId)
                    .putLong("trial_start_ms", trialStartMillis)
                    .apply()

                Toast.makeText(this@MainActivity, "✅ Ro'yxatdan o'tildi: $storeName ($storeCategory • $storeCountry)", Toast.LENGTH_LONG).show()
                renderMainWorkspace()
            }
        })

        // Orqaga — Login oynasiga qaytish
        formCard.addView(TextView(this).apply {
            text = "⬅️  Hisobingiz bormi? Tizimga kirish (Login)"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = glossyCard("#F1F5F9", "#CBD5E1", 24f)
            setPadding(18, 18, 18, 18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                renderSeparateLoginPage()
            }
        })

        container.addView(formCard)
        scroll.addView(container)
        rootLayout.addView(scroll)
    }

    // ==================== OBUNA MUDDATINI TEKSHIRISH VA AVTOMATIK BLOKLASH TIZIMI ====================
    // ==================== ADMIN KARTA VA ID SINXRONIZATSIYASI + 5 KUNLIK QAT'IY OBUNA ====================
    // ==================== ADMIN KARTA VA ID SINXRONIZATSIYASI + 5 KUNLIK QAT'IY OBUNA ====================
    // ==================== V22.0: 5 KUNLIK QAT'IY SINOV, AVTO-BLOK VA ADMIN PANEL ALOQASI ====================
    private fun getEffectiveAdminCardNumber(): String {
        val keys = listOf("admin_card_number", "admin_card", "card_number", "payment_card", "sub_card_number", "karta_raqam")
        for (k in keys) {
            val v = (prefs.getString(k, "") ?: "").trim()
            if (v.isNotEmpty() && !v.contains("****")) return v
        }
        for (k in keys) {
            val v = (prefs.getString(k, "") ?: "").trim()
            if (v.isNotEmpty()) return v
        }
        return adminCardNumber.ifEmpty { "9860 / 8600 (Admin panelda kiriting)" }
    }

    private fun getEffectiveAdminCardOwner(): String {
        val keys = listOf("admin_card_owner", "admin_card_holder", "card_owner", "card_holder", "admin_name")
        for (k in keys) {
            val v = (prefs.getString(k, "") ?: "").trim()
            if (v.isNotEmpty()) return v
        }
        return adminCardOwner.ifEmpty { "ADMIN" }
    }

    private fun getEffectiveAdminId(): String {
        val keys = listOf("admin_id", "admin_tg", "admin_telegram", "admin_username", "support_tg", "admin_contact")
        for (k in keys) {
            val v = (prefs.getString(k, "") ?: "").trim()
            if (v.isNotEmpty()) return v
        }
        return adminTelegramId.ifEmpty { "@QarzFy_Admin" }
    }

    private fun saveAdminPaymentConfig(newCard: String, newOwner: String, newAdminId: String) {
        val c = newCard.trim()
        val o = newOwner.trim()
        val a = newAdminId.trim()
        if (c.isNotEmpty()) adminCardNumber = c
        if (o.isNotEmpty()) adminCardOwner = o
        if (a.isNotEmpty()) adminTelegramId = a

        val ed = prefs.edit()
        if (c.isNotEmpty()) {
            listOf("admin_card_number", "admin_card", "card_number", "payment_card", "sub_card_number", "karta_raqam").forEach {
                ed.putString(it, c)
            }
        }
        if (o.isNotEmpty()) {
            listOf("admin_card_owner", "admin_card_holder", "card_owner", "card_holder", "admin_name").forEach {
                ed.putString(it, o)
            }
        }
        if (a.isNotEmpty()) {
            listOf("admin_id", "admin_tg", "admin_telegram", "admin_username", "support_tg", "admin_contact").forEach {
                ed.putString(it, a)
            }
        }
        ed.apply()
    }

    // Eski ro'yxatdan o'tgan foydalanuvchilarda obuna kodi kiritilmagan bo'lsa,
    // eski 15/30 kunlik sanalarni tozalab, 5 kunlik sinov muddatiga majburiy o'tkazamiz:
    private fun enforceStrict5DayMigrationForOldUsers() {
        val hasRealCode = prefs.getBoolean("activated_by_real_code_v22", false)
        if (!prefs.getBoolean("migrated_strict_5day_v22", false)) {
            val ed = prefs.edit()
            ed.putBoolean("migrated_strict_5day_v22", true)
            if (!hasRealCode) {
                isVipLifetime = false
                subscriptionEndMillis = 0L
                ed.putBoolean("is_vip_lifetime", false)
                ed.putLong("sub_end_ms", 0L)
            }
            ed.apply()
        }
    }

    private fun resolveEarliestAccountStartMillis(): Long {
        enforceStrict5DayMigrationForOldUsers()
        val now = System.currentTimeMillis()
        var earliest = prefs.getLong("trial_start_ms", trialStartMillis)
        if (earliest <= 0L || earliest > now) {
            earliest = now
        }
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val allClients = db.getPersons(false) + db.getPersons(true)
            for (p in allClients) {
                val rawD = p.dueDate.trim()
                if (rawD.length >= 10) {
                    val d10 = rawD.take(10)
                    if (d10.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        val t = sdf.parse(d10)?.time ?: 0L
                        if (t in 1577836800000L..earliest) earliest = t
                    }
                }
            }
            val allTxs = db.getTransactions(false) + db.getTransactions(true)
            for (tx in allTxs) {
                val rawD = tx.date.trim()
                if (rawD.length >= 10) {
                    val d10 = rawD.take(10)
                    if (d10.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        val t = sdf.parse(d10)?.time ?: 0L
                        if (t in 1577836800000L..earliest) earliest = t
                    }
                }
            }
            // Agar foydalanuvchi eski bo'lib, bazasida mijozlari ko'p bo'lsa-yu, ammo sanalar bugungi bo'lib qolgan bo'lsa
            if (allClients.isNotEmpty() && prefs.getLong("first_install_ anchor_v22", 0L) == 0L) {
                prefs.edit().putLong("first_install_anchor_v22", earliest).apply()
            }
        } catch (e: Exception) {}

        trialStartMillis = earliest
        prefs.edit().putLong("trial_start_ms", earliest).apply()
        return earliest
    }

    private fun getRemainingSubscriptionMillis(): Long {
        enforceStrict5DayMigrationForOldUsers()
        val hasRealCode = prefs.getBoolean("activated_by_real_code_v22", false)
        if (hasRealCode && (isVipLifetime || prefs.getBoolean("is_vip_lifetime", false))) {
            return Long.MAX_VALUE
        }
        val now = System.currentTimeMillis()
        val savedSubEnd = if (hasRealCode) prefs.getLong("sub_end_ms", subscriptionEndMillis) else 0L
        val fiveDaysMs = 5L * 24L * 60L * 60L * 1000L
        val startMs = resolveEarliestAccountStartMillis()

        val effectiveDeadline = if (savedSubEnd > 0L) {
            savedSubEnd
        } else {
            startMs + fiveDaysMs
        }
        return effectiveDeadline - now
    }

    private fun isSubscriptionExpiredNow(): Boolean {
        val hasRealCode = prefs.getBoolean("activated_by_real_code_v22", false)
        if (hasRealCode && (isVipLifetime || prefs.getBoolean("is_vip_lifetime", false))) return false
        return getRemainingSubscriptionMillis() <= 0L
    }

    private fun verifyAndActivateSubscriptionKey(rawKey: String): Pair<Boolean, String> {
        val code = rawKey.trim().uppercase(Locale.getDefault())
        if (code.isEmpty()) return Pair(false, "Aktivatsiya kodini kiriting!")

        val cleanDigits = storeId.filter { it.isDigit() }.ifEmpty { "12345" }
        val numId = cleanDigits.toLongOrNull() ?: 12345L
        val sig1M = ((numId * 31L + 777L) % 9000L + 1000L).toString()
        val sig6M = ((numId * 47L + 888L) % 9000L + 1000L).toString()
        val sig1Y = ((numId * 73L + 999L) % 9000L + 1000L).toString()
        val sigVip = ((numId * 97L + 555L) % 9000L + 1000L).toString()

        val now = System.currentTimeMillis()
        val oneDayMs = 24L * 60L * 60L * 1000L

        return when {
            code == "QF1M-$sig1M" || code == "QARZ-30KUN" || code == "OBUNA-1OY" -> {
                subscriptionEndMillis = now + (30L * oneDayMs)
                isVipLifetime = false
                prefs.edit()
                    .putBoolean("activated_by_real_code_v22", true)
                    .putLong("sub_end_ms", subscriptionEndMillis)
                    .putBoolean("is_vip_lifetime", false)
                    .apply()
                Pair(true, "✅ 1 oylik (30 kun) obuna muvaffaqiyatli faollashtirildi!")
            }
            code == "QF6M-$sig6M" || code == "QARZ-6OY" || code == "OBUNA-6OY" -> {
                subscriptionEndMillis = now + (180L * oneDayMs)
                isVipLifetime = false
                prefs.edit()
                    .putBoolean("activated_by_real_code_v22", true)
                    .putLong("sub_end_ms", subscriptionEndMillis)
                    .putBoolean("is_vip_lifetime", false)
                    .apply()
                Pair(true, "✅ 6 oylik (180 kun) obuna muvaffaqiyatli faollashtirildi!")
            }
            code == "QF1Y-$sig1Y" || code == "QARZ-365KUN" || code == "OBUNA-1YIL" -> {
                subscriptionEndMillis = now + (365L * oneDayMs)
                isVipLifetime = false
                prefs.edit()
                    .putBoolean("activated_by_real_code_v22", true)
                    .putLong("sub_end_ms", subscriptionEndMillis)
                    .putBoolean("is_vip_lifetime", false)
                    .apply()
                Pair(true, "✅ 1 yillik (365 kun) obuna muvaffaqiyatli faollashtirildi!")
            }
            code == "QFVIP-$sigVip" || code == "QARZFY-VIP-2026" || code == "PREMIUM-VIP" -> {
                isVipLifetime = true
                subscriptionEndMillis = now + (3650L * oneDayMs)
                prefs.edit()
                    .putBoolean("activated_by_real_code_v22", true)
                    .putBoolean("is_vip_lifetime", true)
                    .putLong("sub_end_ms", subscriptionEndMillis)
                    .apply()
                Pair(true, "👑 VIP (Cheksiz) obuna muvaffaqiyatli faollashtirildi!")
            }
            else -> Pair(false, "❌ Aktivatsiya kodi noto'g'ri! Admin ($adminTelegramId) bilan bog'lanib ID ($storeId) uchun kod oling.")
        }
    }

    // ASOSIY EKRANNING ENG YUQORISIDA DOIM KO'RINIB TURADIGAN OBUNA VA ADMIN PANEL BANNERI:
    private fun buildTopSubscriptionAndAdminBanner(): View {
        if (storeId.isEmpty()) {
            storeId = prefs.getString("store_id", "") ?: ""
            if (storeId.isEmpty()) {
                storeId = "QF-" + (10000 + Random().nextInt(90000))
                prefs.edit().putString("store_id", storeId).apply()
            }
        }
        val remMs = getRemainingSubscriptionMillis()
        val hasRealCode = prefs.getBoolean("activated_by_real_code_v22", false)
        val remDays = Math.ceil(remMs.toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)).toLong().coerceAtLeast(0L)
        val remHours = ((remMs / (60L * 60L * 1000L)) % 24L).coerceAtLeast(0L)

        val bannerText = when {
            hasRealCode && isVipLifetime -> "👑 VIP Cheksiz Faol  •  ID: $storeId"
            hasRealCode && remDays > 0L -> "💎 Obuna faol: $remDays kun qoldi  •  ID: $storeId"
            remDays > 0L -> "⏳ Sinov muddati (5 kunlik): $remDays kun ${remHours}s qoldi"
            else -> "🔒 Sinov muddati tugagan! Obuna olish uchun bosing"
        }

        val bgColors = when {
            hasRealCode -> intArrayOf(Color.parseColor("#059669"), Color.parseColor("#0284C7"))
            remDays <= 1L -> intArrayOf(Color.parseColor("#DC2626"), Color.parseColor("#EA580C"))
            else -> intArrayOf(Color.parseColor("#4F46E5"), Color.parseColor("#7C3AED"))
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, bgColors).apply {
                cornerRadius = 26f
            }
            elevation = 8f
            setPadding(24, 16, 18, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24, 14, 24, 6)
            }
        }

        bar.addView(TextView(this).apply {
            text = bannerText
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                showSubscriptionManageDialog()
            }
        })

        // [ 💎 Obuna ] tugmasi
        bar.addView(TextView(this).apply {
            text = "💎 Obuna"
            setTextColor(Color.parseColor("#1E1B4B"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyCard("#FFFFFF", "#E0E7FF", 18f)
            setPadding(18, 10, 18, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = 8 }
            setOnClickListener {
                showSubscriptionManageDialog()
            }
        })

        // [ ⚙️ Admin ] tugmasi
        bar.addView(TextView(this).apply {
            text = "⚙️ Admin"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = 18f
                setColor(Color.parseColor("#33FFFFFF"))
                setStroke(2, Color.WHITE)
            }
            setPadding(18, 10, 18, 10)
            setOnClickListener {
                showAdminPaymentSettingsDialog {
                    renderMainWorkspace()
                }
            }
        })

        return bar
    }

    // ADMIN PANEL: Karta raqam, Admin ID o'zgartirish va mijoz ID raqami uchun Obuna Kodi chiqarish
    private fun showAdminPaymentSettingsDialog(onSaved: () -> Unit = {}) {
        val sheet = BottomSheetDialog(this)
        val scroll = ScrollView(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 42f)
            setPadding(30, 26, 30, 42)
        }

        wrap.addView(TextView(this).apply {
            text = "⚙️ Admin Panel: Karta, Admin ID va Kod Generator"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 17.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 6)
        })
        wrap.addView(TextView(this).apply {
            text = "Bu yerda kiritilgan Karta raqami va Admin ID dasturning barcha obuna va blok oynalarida darhol o'zgaradi:"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
            setPadding(0, 0, 0, 14)
        })

        val etCard = makeGlassInput("💳 To'lov karta raqami (9860 / 8600...)", getEffectiveAdminCardNumber())
        val etOwner = makeGlassInput("👤 Karta egasi (Ism-familiya)", getEffectiveAdminCardOwner())
        val etAdminId = makeGlassInput("✈️ Admin Telegram yoki Tel (@username / +998...)", getEffectiveAdminId())

        wrap.addView(etCard)
        wrap.addView(etOwner)
        wrap.addView(etAdminId)

        wrap.addView(TextView(this).apply {
            text = "💾  Karta va Admin ID ni saqlash"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(26f)
            setPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4; bottomMargin = 16 }
            setOnClickListener {
                val c = etCard.text.toString().trim()
                val o = etOwner.text.toString().trim()
                val a = etAdminId.text.toString().trim()
                if (c.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Karta raqamini kiriting!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                saveAdminPaymentConfig(c, o, a)
                Toast.makeText(this@MainActivity, "✅ Saqlandi: $c | Admin: $a", Toast.LENGTH_LONG).show()
                sheet.dismiss()
                onSaved()
            }
        })

        // Mijozning Do'kon ID (masalan QF-12345) raqami asosida aktivatsiya kodi generatsiya qilish
        val genBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#F8FAFC", "#CBD5E1", 26f)
            setPadding(20, 16, 20, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        genBox.addView(TextView(this).apply {
            text = "🔑 Mijoz Do'kon ID raqami uchun Obuna Kodi yaratish:"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        })
        val etTargetId = makeGlassInput("Mijoz ID raqami (masalan: $storeId)", storeId)
        genBox.addView(etTargetId)

        val tvGeneratedCodes = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#1E3A8A"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(4, 6, 4, 6)
        }

        fun updateGeneratedCodes(targetRaw: String) {
            val cleanD = targetRaw.filter { it.isDigit() }.ifEmpty { "12345" }
            val num = cleanD.toLongOrNull() ?: 12345L
            val c1m = "QF1M-" + ((num * 31L + 777L) % 9000L + 1000L)
            val c6m = "QF6M-" + ((num * 47L + 888L) % 9000L + 1000L)
            val c1y = "QF1Y-" + ((num * 73L + 999L) % 9000L + 1000L)
            val cvip = "QFVIP-" + ((num * 97L + 555L) % 9000L + 1000L)
            tvGeneratedCodes.text = "📅 1 Oylik:  $c1m\n📆 6 Oylik:  $c6m\n👑 1 Yillik:  $c1y\n💎 VIP Cheksiz:  $cvip"
        }
        updateGeneratedCodes(storeId)
        genBox.addView(tvGeneratedCodes)

        genBox.addView(TextView(this).apply {
            text = "🔄  Shu ID uchun kodlarni hisoblash va nusxalash"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#4338CA"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyCard("#EEF2FF", "#C7D2FE", 20f)
            setPadding(16, 14, 16, 14)
            setOnClickListener {
                val idVal = etTargetId.text.toString().trim()
                updateGeneratedCodes(idVal)
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("SubCodes", tvGeneratedCodes.text.toString()))
                Toast.makeText(this@MainActivity, "📋 Kodlar nusxalandi!", Toast.LENGTH_SHORT).show()
            }
        })
        wrap.addView(genBox)

        scroll.addView(wrap)
        sheet.setContentView(scroll)
        sheet.show()
    }

    private fun renderSubscriptionBlockScreen() {
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#0F172A"))

        if (storeId.isEmpty()) {
            storeId = prefs.getString("store_id", "") ?: ""
            if (storeId.isEmpty()) {
                storeId = "QF-" + (10000 + Random().nextInt(90000))
                prefs.edit().putString("store_id", storeId).apply()
            }
        }

        val currentCard = getEffectiveAdminCardNumber()
        val currentCardOwner = getEffectiveAdminCardOwner()
        val currentAdminId = getEffectiveAdminId()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(30, 44, 30, 56)
        }

        val alertHero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#DC2626"), Color.parseColor("#7C2D12"))
            ).apply { cornerRadius = 38f }
            elevation = 14f
            setPadding(30, 32, 30, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        alertHero.addView(TextView(this).apply {
            text = "🔒"
            textSize = 38f
            gravity = Gravity.CENTER
        })
        alertHero.addView(TextView(this).apply {
            text = "5 KUNLIK SINOV MUDDATI TUGADI!"
            setTextColor(Color.WHITE)
            textSize = 19.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 6)
        })
        alertHero.addView(TextView(this).apply {
            text = "Dastur avtomatik bloklandi.\nIshni davom ettirish uchun obuna oling.\nBarcha ma'lumotlaringiz bazada 100% saqlangan!"
            setTextColor(Color.parseColor("#FEE2E2"))
            textSize = 12.5f
            gravity = Gravity.CENTER
        })
        container.addView(alertHero)

        val mainCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 36f)
            elevation = 10f
            setPadding(24, 24, 24, 28)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val idBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 22f
                setColor(Color.parseColor("#EEF2FF"))
                setStroke(3, Color.parseColor("#6366F1"))
            }
            setPadding(18, 14, 18, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("StoreID", storeId))
                Toast.makeText(this@MainActivity, "📋 Do'kon ID nusxalandi: $storeId", Toast.LENGTH_SHORT).show()
            }
        }
        idBox.addView(TextView(this).apply {
            text = "🏪 ${storeName.ifEmpty { "QarzDaftar" }} (${detectEffectiveStoreCategory()})"
            setTextColor(Color.parseColor("#1E1B4B"))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
        })
        idBox.addView(TextView(this).apply {
            text = "🆔 Sizning Do'kon ID: $storeId  (Nusxalash 📋)"
            setTextColor(Color.parseColor("#4338CA"))
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 4, 0, 0)
        })
        mainCard.addView(idBox)

        // ADMIN KARTA VA ADMIN ID QUTISI
        val cardBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#ECFDF5"))
                setStroke(4, Color.parseColor("#10B981"))
            }
            setPadding(20, 16, 20, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14 }
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("AdminCard", currentCard))
                Toast.makeText(this@MainActivity, "💳 Karta raqami nusxalandi: $currentCard", Toast.LENGTH_SHORT).show()
            }
        }
        cardBox.addView(TextView(this).apply {
            text = "💳 TO'LOV UCHUN ADMIN KARTASI (Nusxalash 📋):"
            setTextColor(Color.parseColor("#065F46"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
        })
        cardBox.addView(TextView(this).apply {
            text = currentCard
            setTextColor(Color.parseColor("#047857"))
            textSize = 18.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 4, 0, 2)
        })
        cardBox.addView(TextView(this).apply {
            text = "👤 Egasi: $currentCardOwner   |   ✈️ Admin ID: $currentAdminId"
            setTextColor(Color.parseColor("#065F46"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
        })
        mainCard.addView(cardBox)

        val etKey = makeGlassInput("🔑 Aktivatsiya kodi (QF1M-... yoki OBUNA-1OY)", "")
        mainCard.addView(etKey)

        mainCard.addView(TextView(this).apply {
            text = "🚀  Obunani faollashtirish va Dasturni ochish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(26f)
            setPadding(22, 22, 22, 22)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6; bottomMargin = 10 }
            setOnClickListener {
                val (ok, msg) = verifyAndActivateSubscriptionKey(etKey.text.toString())
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                if (ok) {
                    renderMainWorkspace()
                }
            }
        })

        mainCard.addView(TextView(this).apply {
            text = "💬  Admin bilan bog'lanish ($currentAdminId)"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#0284C7"), Color.parseColor("#2563EB"))
            ).apply { cornerRadius = 24f }
            setPadding(18, 18, 18, 18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
            setOnClickListener {
                val cleanTg = currentAdminId.removePrefix("@").removePrefix("https://t.me/").trim()
                try {
                    val uri = if (cleanTg.startsWith("+") || cleanTg.all { it.isDigit() }) {
                        Uri.parse("tel:$cleanTg")
                    } else {
                        Uri.parse("https://t.me/$cleanTg")
                    }
                    val intent = if (cleanTg.startsWith("+") || cleanTg.all { it.isDigit() }) {
                        Intent(Intent.ACTION_DIAL, uri)
                    } else {
                        Intent(Intent.ACTION_VIEW, uri)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Admin: $currentAdminId | ID: $storeId", Toast.LENGTH_LONG).show()
                }
            }
        })

        mainCard.addView(TextView(this).apply {
            text = "📥  Mavjud bazani Excel (.xlsx) qilib saqlab olish"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyCard("#F1F5F9", "#CBD5E1", 22f)
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            setOnClickListener {
                exportRealExcelToDownloads(showToast = true)
            }
        })

        mainCard.addView(TextView(this).apply {
            text = "⚙️  Admin sozlamalari (Karta va Admin ID)"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11.5f
            setPadding(12, 12, 12, 6)
            setOnClickListener {
                showAdminPaymentSettingsDialog {
                    renderSubscriptionBlockScreen()
                }
            }
        })

        container.addView(mainCard)
        scroll.addView(container)
        rootLayout.addView(scroll)
    }

    private fun showSubscriptionManageDialog() {
        val sheet = BottomSheetDialog(this)
        val scroll = ScrollView(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glossyCard("#FFFFFF", "#E2E8F8", 42f)
            setPadding(28, 24, 28, 38)
        }

        val remMs = getRemainingSubscriptionMillis()
        val hasRealCode = prefs.getBoolean("activated_by_real_code_v22", false)
        val remDays = Math.ceil(remMs.toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)).toLong().coerceAtLeast(0L)
        val statusTxt = when {
            hasRealCode && isVipLifetime -> "👑 VIP Cheksiz obuna faol"
            hasRealCode && remDays > 0L -> "💎 Pullik obuna faol: $remDays kun qoldi"
            remMs <= 0L -> "🔒 5 kunlik sinov muddati tugagan (Bloklangan)"
            else -> "⏳ 5 kunlik sinov muddati: $remDays kun qoldi"
        }

        val currentCard = getEffectiveAdminCardNumber()
        val currentOwner = getEffectiveAdminCardOwner()
        val currentAdmin = getEffectiveAdminId()

        wrap.addView(TextView(this).apply {
            text = "💎 Obuna, Sinov muddati va Admin to'lov"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 17.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 4)
        })
        wrap.addView(TextView(this).apply {
            text = "$statusTxt  |  🆔 ID: $storeId"
            setTextColor(Color.parseColor("#4338CA"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })

        val payBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#ECFDF5"))
                setStroke(3, Color.parseColor("#10B981"))
            }
            setPadding(18, 14, 18, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("AdminCard", currentCard))
                Toast.makeText(this@MainActivity, "💳 Karta raqami nusxalandi: $currentCard", Toast.LENGTH_SHORT).show()
            }
        }
        payBox.addView(TextView(this).apply {
            text = "💳 To'lov uchun Admin kartasi (Bosib nusxalang 📋):"
            setTextColor(Color.parseColor("#065F46"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
        })
        payBox.addView(TextView(this).apply {
            text = currentCard
            setTextColor(Color.parseColor("#047857"))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 4, 0, 2)
        })
        payBox.addView(TextView(this).apply {
            text = "👤 Egasi: $currentOwner   |   ✈️ Admin: $currentAdmin"
            setTextColor(Color.parseColor("#065F46"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
        })
        wrap.addView(payBox)

        val etCode = makeGlassInput("🔑 Aktivatsiya kodi (masalan: OBUNA-1OY)", "")
        wrap.addView(etCode)

        wrap.addView(TextView(this).apply {
            text = "✅  Kodni faollashtirish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(26f)
            setPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
            setOnClickListener {
                val (ok, msg) = verifyAndActivateSubscriptionKey(etCode.text.toString())
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                if (ok) {
                    sheet.dismiss()
                    renderMainWorkspace()
                }
            }
        })

        wrap.addView(TextView(this).apply {
            text = "⚙️  Admin Panel: Karta raqami va Admin ID ni o'zgartirish"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#1E293B"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyCard("#F1F5F9", "#CBD5E1", 22f)
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            setOnClickListener {
                sheet.dismiss()
                showAdminPaymentSettingsDialog {
                    renderMainWorkspace()
                }
            }
        })

        wrap.addView(TextView(this).apply {
            text = "🔒  5 kunlik sinovni hozir tugatib Avto-Blokni tekshirish"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#DC2626"))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyCard("#FEF2F2", "#FECACA", 22f)
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                sheet.dismiss()
                isVipLifetime = false
                subscriptionEndMillis = 0L
                trialStartMillis = System.currentTimeMillis() - (6L * 24L * 60L * 60L * 1000L)
                prefs.edit()
                    .putBoolean("activated_by_real_code_v22", false)
                    .putBoolean("is_vip_lifetime", false)
                    .putLong("sub_end_ms", 0L)
                    .putLong("trial_start_ms", trialStartMillis)
                    .apply()
                Toast.makeText(this@MainActivity, "🔒 5 kunlik muddat tugadi — dastur bloklandi!", Toast.LENGTH_SHORT).show()
                renderMainWorkspace()
            }
        })

        scroll.addView(wrap)
        sheet.setContentView(scroll)
        sheet.show()
    }

    private fun exportRealExcelToDownloads(showToast: Boolean = true) {
        try {
            val bytes = generateRealXlsxZipBytes()
            val fileName = "QarzFy_Hisobot_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date()) + ".xlsx"
            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                if (showToast) {
                    Toast.makeText(this, "✅ Excel fayl Downloads papkasiga saqlandi!", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (showToast) {
                Toast.makeText(this, "Xatolik: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun renderPinLockScreen(mode: String = "UNLOCK") {
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.parseColor("#F5F7FF"))

        val dm = resources.displayMetrics
        val screenH = dm.heightPixels
        val screenW = dm.widthPixels

        val isPatternActive = when (mode) {
            "SETUP_PATTERN", "CONFIRM_PATTERN" -> true
            "SETUP_NEW", "CONFIRM_NEW" -> false
            else -> (lockAuthType == "PATTERN" && appPatternCode.isNotEmpty())
        }

        val fullScreenPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#F5F7FF"))
            setPadding(32, (screenH * 0.025f).toInt(), 32, (screenH * 0.02f).toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }



        val logoSz = (screenW * 0.15f).toInt().coerceIn(90, 145)
        fullScreenPage.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_qarzdaftar_logo)
            layoutParams = LinearLayout.LayoutParams(logoSz, logoSz).apply { bottomMargin = 8 }
        })

        val titleTxt = when (mode) {
            "SETUP_NEW" -> "Yangi PIN-kod o'rnating"
            "CONFIRM_NEW" -> "PIN-kodni tasdiqlang"
            "SETUP_PATTERN" -> "Yangi Chizma parol chizing"
            "CONFIRM_PATTERN" -> "Chizma parolni tasdiqlang"
            else -> if (isPatternActive) "Chizma parolni chizing" else "PIN-kodni kiriting"
        }
        fullScreenPage.addView(TextView(this).apply {
            text = titleTxt
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 20.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        val subTxt = when (mode) {
            "SETUP_NEW" -> "Dasturga kirish uchun 6 xonali kod tering"
            "CONFIRM_NEW" -> "Kiritilgan 6 xonali kodni qaytadan tering"
            "SETUP_PATTERN" -> "Kamida 4 ta nuqtani barmoqni uzmasdan ulang"
            "CONFIRM_PATTERN" -> "Chizilgan grafik kalitni qaytadan chizing"
            else -> if (isPatternActive) "Dasturga kirish uchun grafik kalitni chizing" else "Dasturga kirish uchun 6 xonali kodni tering"
        }
        fullScreenPage.addView(TextView(this).apply {
            text = subTxt
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(8, 4, 8, 10)
        })

        // [ 🔢 PIN-kod ]  |  [ 🕸 Chizma parol ]
        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glossyCard("#FFFFFF", "#CBD5E1", 30f)
            setPadding(6, 6, 6, 6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
        }

        switchRow.addView(TextView(this).apply {
            text = "🔢  PIN-kod"
            gravity = Gravity.CENTER
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (!isPatternActive) Color.WHITE else Color.parseColor("#1E293B"))
            background = if (!isPatternActive) glossyPurpleGrad(24f) else null
            setPadding(14, 15, 14, 15)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                if (mode.startsWith("SETUP") || mode.startsWith("CONFIRM")) {
                    tempPinBuffer = ""
                    renderPinLockScreen("SETUP_NEW")
                } else if (appPinCode.length == 6) {
                    lockAuthType = "PIN"
                    prefs.edit().putString("lock_auth_type", "PIN").apply()
                    renderPinLockScreen("UNLOCK")
                } else {
                    tempPinBuffer = ""
                    renderPinLockScreen("SETUP_NEW")
                }
            }
        })

        switchRow.addView(TextView(this).apply {
            text = "🕸  Chizma parol"
            gravity = Gravity.CENTER
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (isPatternActive) Color.WHITE else Color.parseColor("#1E293B"))
            background = if (isPatternActive) glossyPurpleGrad(24f) else null
            setPadding(14, 15, 14, 15)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                if (mode.startsWith("SETUP") || mode.startsWith("CONFIRM")) {
                    tempPatternBuffer = ""
                    renderPinLockScreen("SETUP_PATTERN")
                } else if (appPatternCode.isNotEmpty()) {
                    lockAuthType = "PATTERN"
                    prefs.edit().putString("lock_auth_type", "PATTERN").apply()
                    renderPinLockScreen("UNLOCK")
                } else {
                    Toast.makeText(this@MainActivity, "Yangi Chizma parol chizib o'rnating!", Toast.LENGTH_SHORT).show()
                    tempPatternBuffer = ""
                    renderPinLockScreen("SETUP_PATTERN")
                }
            }
        })
        fullScreenPage.addView(switchRow)

        if (isPatternActive) {
            val patternHolder = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply { topMargin = 4; bottomMargin = 10 }
            }

            lateinit var patternView: FullPatternLockView
            patternView = FullPatternLockView(this) { drawnPattern ->
                val count = drawnPattern.split("-").filter { it.isNotEmpty() }.size
                if (count < 4) {
                    Toast.makeText(this@MainActivity, "⚠️ Kamida 4 ta nuqtani ulang!", Toast.LENGTH_SHORT).show()
                    patternView.showErrorAndReset()
                    return@FullPatternLockView
                }
                when (mode) {
                    "SETUP_PATTERN" -> {
                        tempPatternBuffer = drawnPattern
                        renderPinLockScreen("CONFIRM_PATTERN")
                    }
                    "CONFIRM_PATTERN" -> {
                        if (drawnPattern == tempPatternBuffer) {
                            appPatternCode = drawnPattern
                            lockAuthType = "PATTERN"
                            isPinSecurityEnabled = true
                            prefs.edit()
                                .putString("app_pattern_code", appPatternCode)
                                .putString("lock_auth_type", "PATTERN")
                                .putBoolean("pin_security_enabled", true)
                                .apply()
                            Toast.makeText(this@MainActivity, "✅ Chizma parol muvaffaqiyatli saqlandi!", Toast.LENGTH_SHORT).show()
                            isPinUnlockedThisSession = true
                            renderMainWorkspace()
                        } else {
                            Toast.makeText(this@MainActivity, "❌ Chizmalar mos kelmadi! Qaytadan chizing.", Toast.LENGTH_LONG).show()
                            tempPatternBuffer = ""
                            renderPinLockScreen("SETUP_PATTERN")
                        }
                    }
                    else -> {
                        if (drawnPattern == appPatternCode) {
                            isPinUnlockedThisSession = true
                            renderMainWorkspace()
                        } else {
                            Toast.makeText(this@MainActivity, "❌ Chizma parol noto'g'ri!", Toast.LENGTH_SHORT).show()
                            patternView.showErrorAndReset()
                        }
                    }
                }
            }.apply {
                background = glossyCard("#FFFFFF", "#E2E8F8", 44f)
                elevation = 6f
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            }
            patternHolder.addView(patternView)
            fullScreenPage.addView(patternHolder)

            val bioBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = glossyPurpleGrad(30f)
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { triggerBiometricPromptForUnlock() }
            }
            bioBar.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_fingerprint_vec)
                layoutParams = LinearLayout.LayoutParams(50, 50).apply { rightMargin = 14 }
            })
            bioBar.addView(TextView(this).apply {
                text = "Barmoq izi / Face ID orqali ochish"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            })
            fullScreenPage.addView(bioBar)

        } else {
            val dotsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4; bottomMargin = 12 }
            }
            val dotViews = mutableListOf<View>()
            for (i in 0 until 6) {
                val dot = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#E2E8F0"))
                        setStroke(3, Color.parseColor("#94A3B8"))
                    }
                    layoutParams = LinearLayout.LayoutParams(42, 42).apply { setMargins(14, 0, 14, 0) }
                }
                dotViews.add(dot)
                dotsRow.addView(dot)
            }
            fullScreenPage.addView(dotsRow)

            var enteredPin = ""

            fun updateDots() {
                for (i in 0 until 6) {
                    val filled = (i < enteredPin.length)
                    dotViews[i].background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        if (filled) {
                            setColor(Color.parseColor("#5946E8"))
                            setStroke(3, Color.parseColor("#3677F5"))
                        } else {
                            setColor(Color.parseColor("#E2E8F0"))
                            setStroke(3, Color.parseColor("#94A3B8"))
                        }
                    }
                }
            }

            fun handlePinDone() {
                if (mode == "SETUP_NEW") {
                    tempPinBuffer = enteredPin
                    renderPinLockScreen("CONFIRM_NEW")
                } else if (mode == "CONFIRM_NEW") {
                    if (enteredPin == tempPinBuffer) {
                        appPinCode = enteredPin
                        lockAuthType = "PIN"
                        isPinSecurityEnabled = true
                        prefs.edit()
                            .putString("app_pin_code", appPinCode)
                            .putString("lock_auth_type", "PIN")
                            .putBoolean("pin_security_enabled", true)
                            .apply()
                        Toast.makeText(this@MainActivity, "✅ PIN-kod saqlandi!", Toast.LENGTH_SHORT).show()
                        isPinUnlockedThisSession = true
                        renderMainWorkspace()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Kodlar mos kelmadi! Qaytadan tering.", Toast.LENGTH_LONG).show()
                        tempPinBuffer = ""
                        renderPinLockScreen("SETUP_NEW")
                    }
                } else {
                    if (enteredPin == appPinCode) {
                        isPinUnlockedThisSession = true
                        renderMainWorkspace()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ PIN-kod noto'g'ri!", Toast.LENGTH_SHORT).show()
                        enteredPin = ""
                        updateDots()
                    }
                }
            }

            val keypadGrid = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("BIO", "0", "⌫")
            )

            val btnDiameter = (minOf(screenW / 3.7f, screenH / 8.3f)).toInt().coerceIn(135, 225)

            rows.forEach { rVals ->
                val rowLin = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                }
                rVals.forEach { keyStr ->
                    val cellFrame = FrameLayout(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    }
                    if (keyStr == "BIO") {
                        val bioBtn = FrameLayout(this).apply {
                            background = glossyRoundBtnBg("#5946E8", "#3677F5")
                            elevation = 8f
                            layoutParams = FrameLayout.LayoutParams(btnDiameter, btnDiameter, Gravity.CENTER)
                            addView(ImageView(this@MainActivity).apply {
                                setImageResource(R.drawable.ic_fingerprint_vec)
                                layoutParams = FrameLayout.LayoutParams(
                                    (btnDiameter * 0.48f).toInt(),
                                    (btnDiameter * 0.48f).toInt(),
                                    Gravity.CENTER
                                )
                            })
                            setOnClickListener { triggerBiometricPromptForUnlock() }
                        }
                        cellFrame.addView(bioBtn)
                    } else {
                        val numBtn = TextView(this).apply {
                            text = keyStr
                            gravity = Gravity.CENTER
                            textSize = if (keyStr == "⌫") 22f else 25f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#0F172A"))
                            background = glossyRoundBtnBg("#FFFFFF", "#CBD5E1")
                            elevation = 6f
                            layoutParams = FrameLayout.LayoutParams(btnDiameter, btnDiameter, Gravity.CENTER)
                            setOnClickListener {
                                if (keyStr == "⌫") {
                                    if (enteredPin.isNotEmpty()) {
                                        enteredPin = enteredPin.dropLast(1)
                                        updateDots()
                                    }
                                } else {
                                    if (enteredPin.length < 6) {
                                        enteredPin += keyStr
                                        updateDots()
                                        if (enteredPin.length == 6) {
                                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                handlePinDone()
                                            }, 120)
                                        }
                                    }
                                }
                            }
                        }
                        cellFrame.addView(numBtn)
                    }
                    rowLin.addView(cellFrame)
                }
                keypadGrid.addView(rowLin)
            }
            fullScreenPage.addView(keypadGrid)
        }

        rootLayout.addView(fullScreenPage)
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
            background = glossyCard("#FFFFFF", "#E2E8F8", 42f)
            setPadding(32, 28, 32, 44)
        }
        wrap.addView(TextView(this).apply {
            text = "🔐 Xavfsizlik: PIN-kod, Chizma parol va Biometrika"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 14)
        })

        val swPin = Switch(this).apply {
            text = "Dasturga kirishda Qulfni (PIN / Chizma) yoqish"
            isChecked = isPinSecurityEnabled
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 14f
            setPadding(8, 16, 8, 16)
            setOnCheckedChangeListener { _, chk ->
                isPinSecurityEnabled = chk
                prefs.edit().putBoolean("pin_security_enabled", chk).apply()
            }
        }
        wrap.addView(swPin)

        val swBio = Switch(this).apply {
            text = "Barmoq izi / Face ID orqali kirish"
            isChecked = isBiometricEnabled
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 14f
            setPadding(8, 16, 8, 22)
            setOnCheckedChangeListener { _, chk ->
                isBiometricEnabled = chk
                prefs.edit().putBoolean("biometric_enabled", chk).apply()
            }
        }
        wrap.addView(swBio)

        wrap.addView(TextView(this).apply {
            text = "🔢  6-xonali PIN-kod o'rnatish / o'zgartirish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyPurpleGrad(28f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
            setOnClickListener {
                sheet.dismiss()
                tempPinBuffer = ""
                renderPinLockScreen("SETUP_NEW")
            }
        })

        wrap.addView(TextView(this).apply {
            text = "🕸  3x3 Chizma parol (Grafik kalit) o'rnatish"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = glossyOrangeGrad(28f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
            setOnClickListener {
                sheet.dismiss()
                tempPatternBuffer = ""
                renderPinLockScreen("SETUP_PATTERN")
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
            .setMessage("Ilova: QarzFy v11.0 (Glossy Glassmorphism)\nDasturchi: $ownerName\nTelegram: @HasanJaylovov")
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
