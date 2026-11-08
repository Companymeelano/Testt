"""ربات روبیکا برای شمارش صلوات بر امام زمان (عج).

ارتباط با API رسمی روبیکا با POST به:
    https://botapi.rubika.ir/v3/<token>/<method>
و دریافت به‌روزرسانی‌ها با روش getUpdates (polling).

نکتهٔ مهم: نام فیلدهای به‌روزرسانی در `parse_update` بر پایهٔ مستندات عمومی
و کتابخانه‌های جامعه نوشته شده است. قبل از استقرار، با یک پیام واقعی
در روبیکا آن را تأیید کنید (لاگ با سطح DEBUG فیلدها را نشان می‌دهد).
"""

from __future__ import annotations

import logging
import os
import time
from dataclasses import dataclass
from typing import Any

import httpx

from . import db

API_BASE = "https://botapi.rubika.ir/v3"
logger = logging.getLogger("salawat.rubika")

PERSIAN_DIGITS = str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "0123456789" * 2)


class RubikaError(RuntimeError):
    pass


class RubikaClient:
    def __init__(self, token: str, base_url: str = API_BASE, http: httpx.Client | None = None):
        self.url = f"{base_url}/{token}"
        self.http = http or httpx.Client(timeout=20)

    def call(self, method: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
        resp = self.http.post(f"{self.url}/{method}", json=payload or {})
        resp.raise_for_status()
        body = resp.json()
        if body.get("status") != "OK":
            raise RubikaError(f"{method}: {body}")
        return body.get("data", {})

    def send_message(self, chat_id: str, text: str, keypad: dict | None = None) -> dict:
        payload: dict[str, Any] = {"chat_id": chat_id, "text": text}
        if keypad:
            payload["inline_keypad"] = keypad
        return self.call("sendMessage", payload)

    def get_updates(self, offset_id: str | None = None, limit: int = 100) -> dict:
        payload: dict[str, Any] = {"limit": limit}
        if offset_id:
            payload["offset_id"] = offset_id
        return self.call("getUpdates", payload)


@dataclass
class Incoming:
    kind: str            # start | text | button | other
    chat_id: str | None
    text: str | None = None
    button_id: str | None = None


def parse_update(update: dict[str, Any]) -> Incoming:
    msg = update.get("new_message") or update.get("inline_message") or {}
    chat_id = update.get("chat_id") or msg.get("chat_id")
    aux = msg.get("aux_data") or update.get("aux_data") or {}
    button_id = aux.get("button_id")
    text = msg.get("text")

    if update.get("type") == "StartedBot":
        return Incoming("start", chat_id)
    if button_id:
        return Incoming("button", chat_id, text=text, button_id=button_id)
    if text:
        return Incoming("text", chat_id, text=text)
    return Incoming("other", chat_id)


def _btn(button_id: str, label: str) -> dict:
    return {"id": button_id, "type": "Simple", "button_text": label}


def main_keypad() -> dict:
    return {
        "rows": [
            {"buttons": [_btn("add:1", "+۱"), _btn("add:10", "+۱۰"), _btn("add:100", "+۱۰۰")]},
            {"buttons": [_btn("intent:health", "نیت: سلامتی امام"),
                         _btn("intent:zohoor", "نیت: تعجیل در ظهور")]},
            {"buttons": [_btn("intent:yari", "نیت: یاری منتظران"), _btn("me", "کارنامه من")]},
            {"buttons": [_btn("global", "آمار جمعی"), _btn("help", "راهنما")]},
        ]
    }


WELCOME = (
    "سلام و ارادتمند شما.\n"
    "به ربات «صلوات برای امام زمان (عج)» خوش آمدید.\n"
    "با دکمه‌های زیر صلوات بشمارید یا عدد دلخواه (۱ تا ۱۰۰۰) را بفرستید.\n"
    "نیت پیش‌فرض: سلامتی و طول عمر امام زمان (عج)."
)
HELP = (
    "راهنما:\n"
    "• دکمه‌های +۱ / +۱۰ / +۱۰۰ را بزنید یا عدد دلخواه بفرستید.\n"
    "• با دکمه‌های «نیت» مشخص کنید صلوات برای کدام هدف ثبت شود.\n"
    "• «کارنامه من» مجموع شما را نشان می‌دهد."
)
INTENT_LABELS = {item["slug"]: item["title"] for item in db.INTENTIONS}


def _reply(client: RubikaClient, chat_id: str | None, text: str) -> None:
    if chat_id:
        client.send_message(chat_id, text, keypad=main_keypad())


def _format_me(stats: dict) -> str:
    lines = [f"کارنامه شما:", f"مجموع کل: {stats['total']}", f"امروز: {stats['today']}"]
    for item in stats["by_intention"]:
        lines.append(f"• {item['title']}: {item['total']}")
    return "\n".join(lines)


def _format_global(stats: dict) -> str:
    lines = [
        "آمار جمعی:",
        f"مجموع کل: {stats['total']}",
        f"امروز: {stats['today']} از هدف {stats['daily_goal']} "
        f"({stats['goal_progress_percent']}٪)",
        f"شرکت‌کنندگان امروز: {stats['participants_today']}",
    ]
    for item in stats["by_intention"]:
        lines.append(f"• {item['title']}: {item['total']}")
    return "\n".join(lines)


def handle_update(update: dict[str, Any], client: RubikaClient, conn) -> None:
    logger.debug("update: %s", update)
    inc = parse_update(update)
    if not inc.chat_id:
        return
    user = db.get_or_create_rubika_user(conn, inc.chat_id)

    if inc.kind == "start":
        _reply(client, inc.chat_id, WELCOME)
        return

    if inc.kind == "button":
        bid = inc.button_id or ""
        if bid.startswith("add:"):
            _count(client, conn, user, int(bid.split(":", 1)[1]))
        elif bid.startswith("intent:"):
            slug = bid.split(":", 1)[1]
            db.set_intention(conn, user["id"], slug)
            _reply(client, inc.chat_id, f"نیت شما تنظیم شد:\n{INTENT_LABELS.get(slug, slug)}")
        elif bid == "me":
            _reply(client, inc.chat_id, _format_me(db.user_stats(conn, user["id"])))
        elif bid == "global":
            _reply(client, inc.chat_id, _format_global(db.global_stats(conn)))
        elif bid == "help":
            _reply(client, inc.chat_id, HELP)
        return

    if inc.kind == "text" and inc.text:
        raw = inc.text.strip().translate(PERSIAN_DIGITS)
        if raw.isdigit():
            _count(client, conn, user, int(raw))
        elif raw in ("/start", "شروع"):
            _reply(client, inc.chat_id, WELCOME)
        elif raw in ("/help", "راهنما"):
            _reply(client, inc.chat_id, HELP)
        else:
            _reply(client, inc.chat_id, "عدد بفرستید (۱ تا ۱۰۰۰) یا از دکمه‌ها استفاده کنید.")


def _count(client: RubikaClient, conn, user: dict, amount: int) -> None:
    try:
        result = db.add_count(conn, user["id"], amount)
    except db.SalawatError as exc:
        _reply(client, user["rubika_chat_id"], str(exc))
        return
    _reply(
        client,
        user["rubika_chat_id"],
        f"ثبت شد: +{result['added']}\nامروز: {result['today']}\n"
        f"اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَ آلِ مُحَمَّدٍ وَ عَجِّلْ فَرَجَهُمْ",
    )


def run_forever(token: str, poll_interval: float = 1.5) -> None:  # pragma: no cover - حلقهٔ شبکه
    client = RubikaClient(token)
    offset: str | None = None
    logger.info("ربات روبیکا شروع به کار کرد.")
    while True:
        try:
            conn = db.connect()
            data = client.get_updates(offset_id=offset, limit=100)
            for update in data.get("updates", []):
                try:
                    handle_update(update, client, conn)
                except Exception:
                    logger.exception("خطا در پردازش به‌روزرسانی")
            offset = data.get("next_offset_id") or offset
            conn.close()
        except Exception:
            logger.exception("خطا در دریافت به‌روزرسانی‌ها؛ تلاش مجدد")
            time.sleep(5)
        time.sleep(poll_interval)


if __name__ == "__main__":  # pragma: no cover
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"))
    token = os.environ.get("RUBIKA_BOT_TOKEN", "").strip()
    if not token:
        raise SystemExit("متغیر RUBIKA_BOT_TOKEN تنظیم نشده است.")
    run_forever(token)
