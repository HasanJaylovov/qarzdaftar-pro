import os
import sqlite3
import threading
import time
from datetime import datetime, timedelta
from flask import Flask, request, jsonify, redirect, render_template_string
import telebot
from telebot.types import InlineKeyboardMarkup, InlineKeyboardButton

BOT_TOKEN = os.environ.get("BOT_TOKEN", "TOKEN_KIRITILMAGAN")
BOT_USERNAME = "qarzdaftar_pay_bot"
ADMIN_TG_ID = 924436054
CARD_NUMBER = "9860 1606 3127 2954"
CARD_HOLDER = "HASAN JAYLOVOV"
MONTHLY_PRICE = 39000

app = Flask(__name__)
bot = telebot.TeleBot(BOT_TOKEN, parse_mode="HTML")

def init_db():
    conn = sqlite3.connect("qarzdaftar.db")
    c = conn.cursor()
    c.execute("""
        CREATE TABLE IF NOT EXISTS stores (
            store_id TEXT PRIMARY KEY,
            store_name TEXT,
            phone TEXT,
            is_active INTEGER DEFAULT 0,
            expires_at TEXT
        )
    """)
    c.execute("INSERT OR IGNORE INTO stores VALUES ('QD-101', 'Baraka Minimarket', '+998901234567', 0, '2000-01-01')")
    conn.commit()
    conn.close()

init_db()

def get_store(store_id):
    conn = sqlite3.connect("qarzdaftar.db")
    c = conn.cursor()
    c.execute("SELECT store_id, store_name, phone, is_active, expires_at FROM stores WHERE store_id = ?", (store_id.upper(),))
    row = c.fetchone()
    conn.close()
    return row

def activate_store(store_id, days=30):
    expires = (datetime.now() + timedelta(days=days)).strftime("%Y-%m-%d")
    conn = sqlite3.connect("qarzdaftar.db")
    c = conn.cursor()
    c.execute("UPDATE stores SET is_active = 1, expires_at = ? WHERE store_id = ?", (expires, store_id.upper()))
    conn.commit()
    conn.close()
    return expires

@app.route("/api/register", methods=["POST"])
def api_register():
    data = request.json or {}
    store_name = data.get("store_name", "Yangi Do'kon")
    phone = data.get("phone", "+998900000000")
    conn = sqlite3.connect("qarzdaftar.db")
    c = conn.cursor()
    c.execute("SELECT COUNT(*) FROM stores")
    next_num = c.fetchone()[0] + 101
    store_id = f"QD-{next_num}"
    c.execute("INSERT INTO stores VALUES (?, ?, ?, 0, '2000-01-01')", (store_id, store_name, phone))
    conn.commit()
    conn.close()
    return jsonify({
        "status": "ok",
        "store_id": store_id,
        "bot_url": f"https://t.me/{BOT_USERNAME}?start={store_id}",
        "price": MONTHLY_PRICE + next_num
    })

@app.route("/api/status/<store_id>", methods=["GET"])
def api_status(store_id):
    row = get_store(store_id)
    if not row:
        return jsonify({"active": False, "error": "Topilmadi"}), 404
    sid, name, phone, is_active, expires_at = row
    today = datetime.now().strftime("%Y-%m-%d")
    active_now = bool(is_active == 1 and expires_at >= today)
    return jsonify({
        "store_id": sid,
        "store_name": name,
        "active": active_now,
        "expires_at": expires_at,
        "card_number": CARD_NUMBER,
        "card_holder": CARD_HOLDER,
        "price": MONTHLY_PRICE,
        "bot_url": f"https://t.me/{BOT_USERNAME}?start={sid}"
    })

@app.route("/admin", methods=["GET"])
def admin_panel():
    conn = sqlite3.connect("qarzdaftar.db")
    c = conn.cursor()
    c.execute("SELECT store_id, store_name, phone, is_active, expires_at FROM stores ORDER BY store_id DESC")
    stores = c.fetchall()
    conn.close()
    html = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8">
        <title>QarzDaftar Pro — Admin Panel</title>
        <style>
            body { font-family: -apple-system, sans-serif; background: #0f172a; color: #f8fafc; padding: 25px; }
            .card { background: #1e293b; padding: 20px; border-radius: 12px; margin-bottom: 20px; border: 1px solid #334155; }
            table { width: 100%; border-collapse: collapse; background: #1e293b; border-radius: 12px; overflow: hidden; }
            th, td { padding: 14px; text-align: left; border-bottom: 1px solid #334155; }
            th { background: #020617; color: #94a3b8; }
            .btn { padding: 8px 14px; border-radius: 8px; border: none; color: white; font-weight: bold; cursor: pointer; }
            .btn-green { background: #10b981; }
            .btn-red { background: #f43f5e; }
            input { padding: 8px 12px; border-radius: 6px; border: 1px solid #475569; background: #0f172a; color: white; }
        </style>
    </head>
    <body>
        <h2>🛡️ QarzDaftar Pro — Boshqaruv Paneli</h2>
        <div class="card">
            <b>💳 Karta:</b> {{ card }} ({{ holder }}) &nbsp;|&nbsp;
            <b>💰 Obuna narxi:</b> {{ price }} so'm &nbsp;|&nbsp;
            <b>🤖 Bot:</b> <a href="https://t.me/{{ bot_user }}" style="color:#38bdf8;" target="_blank">@{{ bot_user }}</a>
        </div>
        <div class="card">
            <form method="POST" action="/admin/add">
                <b>➕ Yangi do'kon qo'shish:</b> &nbsp;
                <input type="text" name="store_name" placeholder="Do'kon nomi" required>
                <input type="text" name="phone" placeholder="+998901234567" required>
                <button type="submit" class="btn btn-green">Qo'shish</button>
            </form>
        </div>
        <table>
            <tr><th>Do'kon ID</th><th>Do'kon nomi</th><th>Telefon</th><th>Holati</th><th>Muddati</th><th>Amal</th></tr>
            {% for s in stores %}
            <tr>
                <td><b>#{{ s[0] }}</b></td>
                <td>{{ s[1] }}</td>
                <td>{{ s[2] }}</td>
                <td>
                    {% if s[3] == 1 %}
                        <span style="color:#10b981;font-weight:bold;">✅ Faol</span>
                    {% else %}
                        <span style="color:#f43f5e;font-weight:bold;">🔒 Qulflangan</span>
                    {% endif %}
                </td>
                <td>{{ s[4] }}</td>
                <td>
                    <form method="POST" action="/admin/toggle" style="display:inline;">
                        <input type="hidden" name="store_id" value="{{ s[0] }}">
                        {% if s[3] == 1 %}
                            <button type="submit" class="btn btn-red">Bloklash</button>
                        {% else %}
                            <button type="submit" class="btn btn-green">+30 kun yoqish</button>
                        {% endif %}
                    </form>
                </td>
            </tr>
            {% endfor %}
        </table>
    </body>
    </html>
    """
    return render_template_string(html, stores=stores, card=CARD_NUMBER, holder=CARD_HOLDER, price=f"{MONTHLY_PRICE:,}", bot_user=BOT_USERNAME)

@app.route("/admin/add", methods=["POST"])
def admin_add_store():
    name = request.form.get("store_name")
    phone = request.form.get("phone")
    conn = sqlite3.connect("qarzdaftar.db")
    c = conn.cursor()
    c.execute("SELECT COUNT(*) FROM stores")
    next_num = c.fetchone()[0] + 101
    store_id = f"QD-{next_num}"
    c.execute("INSERT INTO stores VALUES (?, ?, ?, 0, '2000-01-01')", (store_id, name, phone))
    conn.commit()
    conn.close()
    return redirect("/admin")

@app.route("/admin/toggle", methods=["POST"])
def admin_toggle():
    store_id = request.form.get("store_id")
    row = get_store(store_id)
    if row:
        if row[3] == 1:
            conn = sqlite3.connect("qarzdaftar.db")
            conn.execute("UPDATE stores SET is_active = 0 WHERE store_id = ?", (store_id,))
            conn.commit()
            conn.close()
        else:
            activate_store(store_id, 30)
    return redirect("/admin")

def send_payment_info(chat_id, store_id):
    store_id = store_id.upper().replace("#", "").strip()
    row = get_store(store_id)
    if not row:
        conn = sqlite3.connect("qarzdaftar.db")
        conn.execute("INSERT OR IGNORE INTO stores VALUES (?, ?, ?, 0, '2000-01-01')", (store_id, f"Do'kon {store_id}", "Noma'lum"))
        conn.commit()
        conn.close()
        store_name = f"Do'kon {store_id}"
    else:
        store_name = row[1]

    digits = "".join(filter(str.isdigit, store_id)) or "101"
    unique_amount = MONTHLY_PRICE + int(digits)

    kb = InlineKeyboardMarkup()
    kb.add(InlineKeyboardButton(text="✅ To'lov qildim (Tasdiqlash)", callback_data=f"paid:{store_id}:{unique_amount}"))

    text = (
        f"🏪 <b>Do'kon:</b> {store_name} (#{store_id})\n\n"
        f"💳 <b>To'lov uchun karta (Uzcard):</b>\n"
        f"<code>{CARD_NUMBER}</code>\n"
        f"👤 <b>Karta egasi:</b> {CARD_HOLDER}\n"
        f"💰 <b>Oylik obuna summasi:</b> <code>{unique_amount:,}</code> so'm\n\n"
        f"☝️ Yuqoridagi karta raqamiga <b>{unique_amount:,} so'm</b> o'tkazgach, pastdagi <b>«✅ To'lov qildim»</b> tugmasini bosing yoki chek skrinshotini shu yerga yuboring:"
    )
    bot.send_message(chat_id, text, reply_markup=kb)

@bot.message_handler(commands=["start"])
def handle_start(message):
    parts = message.text.split()
    if len(parts) > 1:
        send_payment_info(message.chat.id, parts[1])
    else:
        bot.send_message(
            message.chat.id,
            "👋 Assalomu alaykum! <b>QarzDaftar Pro</b> rasmiy to'lov botiga xush kelibsiz.\n\n"
            "📲 Ilovada ko'rsatilgan <b>Do'kon ID</b> raqamingizni yozib yuboring (masalan: <code>QD-101</code>):"
        )

@bot.message_handler(content_types=["photo"])
def handle_receipt_photo(message):
    caption = message.caption or "QD-101"
    kb = InlineKeyboardMarkup()
    kb.add(InlineKeyboardButton(text="✅ Tasdiqlash (+30 kun)", callback_data=f"approve:{caption}:{message.chat.id}"))
    bot.forward_message(ADMIN_TG_ID, message.chat.id, message.message_id)
    bot.send_message(
        ADMIN_TG_ID,
        f"🧾 <b>Yangi to'lov cheki keldi!</b>\nFoydalanuvchi: @{message.from_user.username or message.from_user.first_name}\nIzoh/ID: <b>{caption}</b>",
        reply_markup=kb
    )
    bot.reply_to(message, "⏳ Chekingiz qabul qilindi va Adminga yuborildi. Tasdiqlanishi bilan dasturingiz avtomatik ochiladi!")

@bot.message_handler(func=lambda m: True)
def handle_text(message):
    send_payment_info(message.chat.id, message.text)

@bot.callback_query_handler(func=lambda call: call.data.startswith("paid:"))
def callback_paid(call):
    _, store_id, amount = call.data.split(":")
    kb = InlineKeyboardMarkup()
    kb.add(InlineKeyboardButton(text=f"✅ Tasdiqlash (#{store_id} — 30 kun)", callback_data=f"approve:{store_id}:{call.message.chat.id}"))
    bot.send_message(
        ADMIN_TG_ID,
        f"🔔 <b>YANGI TO'LOV SO'ROVI!</b>\n\n"
        f"🏪 Do'kon ID: <b>#{store_id}</b>\n"
        f"💰 Summa: <b>{int(amount):,} so'm</b>\n"
        f"👤 Mijoz: @{call.from_user.username or call.from_user.first_name}\n\n"
        f"Kartangizga ({CARD_NUMBER}) pul tushgan bo'lsa, pastdagi tugmani bosing:",
        reply_markup=kb
    )
    bot.answer_callback_query(call.id, "So'rov adminga yuborildi!")
    bot.send_message(call.message.chat.id, "⏳ To'lov so'rovingiz adminga yuborildi. Tasdiqlanishi bilan sizga xabar keladi va dastur ochiladi!")

@bot.callback_query_handler(func=lambda call: call.data.startswith("approve:"))
def callback_approve(call):
    if call.from_user.id != ADMIN_TG_ID:
        bot.answer_callback_query(call.id, "Siz admin emassiz!", show_alert=True)
        return
    _, store_id, user_chat_id = call.data.split(":")
    expires = activate_store(store_id, 30)
    bot.send_message(
        int(user_chat_id),
        f"🎉 <b>TABRIKLAYMIZ!</b>\n\n"
        f"✅ <b>#{store_id}</b> do'koningiz uchun 30 kunlik obuna faollashtirildi (Muddat: <b>{expires}</b> gacha).\n"
        f"📲 Endi QarzDaftar ilovasiga kirib bemalol foydalanishingiz mumkin!"
    )
    bot.edit_message_text(
        f"✅ <b>#{store_id}</b> do'koni tasdiqlandi! (Muddat: {expires} gacha)",
        call.message.chat.id,
        call.message.message_id
    )

def run_bot():
    while True:
        try:
            bot.polling(non_stop=True, interval=1)
        except Exception as e:
            print("Bot xatosi:", e)
            time.sleep(3)

if __name__ == "__main__":
    print(f"1. Telegram bot (@{BOT_USERNAME}) ishga tushdi!")
    threading.Thread(target=run_bot, daemon=True).start()
    print("2. Admin panel: http://127.0.0.1:5050/admin")
    app.run(host="0.0.0.0", port=5050)
