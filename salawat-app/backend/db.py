"""لایهٔ داده: کاربران، شمارش صلوات، نیت‌ها و تنظیمات (SQLite)."""

from __future__ import annotations

import datetime as dt
import os
import secrets
import sqlite3
from pathlib import Path

# نیت‌ها: مرجع کل برنامه، سلامت و ظهور امام زمان (عج) است.
INTENTIONS: list[dict[str, str]] = [
    {
        "slug": "health",
        "title": "سلامتی و طول عمر امام زمان (عج)",
        "description": "دعا برای سلامت و عافیت وجود مبارک حضرت ولی‌عصر (عج)",
    },
    {
        "slug": "zohoor",
        "title": "تعجیل در ظهور و فرج",
        "description": "درخواست تعجیل در ظهور و فرج آن حضرت",
    },
    {
        "slug": "yari",
        "title": "یاری و آمادگی منتظران",
        "description": "یاری آن حضرت و آماده‌شدن منتظران برای ظهور",
    },
]
DEFAULT_INTENTION = "health"
VALID_INTENTIONS = {item["slug"] for item in INTENTIONS}

MAX_PER_REQUEST = 1000          # بیشترین تعداد در یک درخواست
DEFAULT_DAILY_USER_CAP = 20000  # سقف شمارش روزانهٔ هر کاربر (ضد تقلب ساده)
DEFAULT_DAILY_GOAL = 100000     # هدف جمعی روزانه

# ساعت تهران (ایران از سال ۱۴۰۱ ساعت تابستانی ندارد؛ +۳:۳۰ ثابت است)
TEHRAN_TZ = dt.timezone(dt.timedelta(hours=3, minutes=30))

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    token TEXT UNIQUE,
    rubika_chat_id TEXT UNIQUE,
    current_intention TEXT NOT NULL DEFAULT 'health',
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS counts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    intention TEXT NOT NULL,
    amount INTEGER NOT NULL,
    created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_counts_day ON counts(created_at);
CREATE INDEX IF NOT EXISTS idx_counts_user ON counts(user_id);
CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
"""


class SalawatError(ValueError):
    """خطای قابل نمایش به کاربر (ورودی نامعتبر یا محدودیت)."""


class DailyCapExceeded(SalawatError):
    pass


def db_path() -> Path:
    env = os.environ.get("SALAWAT_DB")
    if env:
        return Path(env)
    return Path(__file__).resolve().parent.parent / "data" / "salawat.db"


def now_tehran() -> dt.datetime:
    return dt.datetime.now(TEHRAN_TZ)


def today_str() -> str:
    return now_tehran().strftime("%Y-%m-%d")


def connect() -> sqlite3.Connection:
    path = db_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    conn.executescript(SCHEMA)
    return conn


def _now_iso() -> str:
    return now_tehran().strftime("%Y-%m-%dT%H:%M:%S")


def _daily_cap() -> int:
    return int(os.environ.get("SALAWAT_DAILY_CAP", DEFAULT_DAILY_USER_CAP))


# ---------- کاربران ----------

def create_user(conn: sqlite3.Connection) -> dict:
    token = secrets.token_urlsafe(24)
    cur = conn.execute(
        "INSERT INTO users (token, created_at) VALUES (?, ?)", (token, _now_iso())
    )
    conn.commit()
    return get_user_by_id(conn, cur.lastrowid)


def get_user_by_id(conn: sqlite3.Connection, user_id: int) -> dict | None:
    row = conn.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
    return dict(row) if row else None


def get_user_by_token(conn: sqlite3.Connection, token: str) -> dict | None:
    if not token:
        return None
    row = conn.execute("SELECT * FROM users WHERE token = ?", (token,)).fetchone()
    return dict(row) if row else None


def get_or_create_rubika_user(conn: sqlite3.Connection, chat_id: str) -> dict:
    row = conn.execute(
        "SELECT * FROM users WHERE rubika_chat_id = ?", (chat_id,)
    ).fetchone()
    if row:
        return dict(row)
    cur = conn.execute(
        "INSERT INTO users (token, rubika_chat_id, created_at) VALUES (?, ?, ?)",
        (secrets.token_urlsafe(24), chat_id, _now_iso()),
    )
    conn.commit()
    return get_user_by_id(conn, cur.lastrowid)


def set_intention(conn: sqlite3.Connection, user_id: int, slug: str) -> None:
    if slug not in VALID_INTENTIONS:
        raise SalawatError("نیت نامعتبر است.")
    conn.execute(
        "UPDATE users SET current_intention = ? WHERE id = ?", (slug, user_id)
    )
    conn.commit()


# ---------- شمارش ----------

def _today_total_for_user(conn: sqlite3.Connection, user_id: int) -> int:
    row = conn.execute(
        "SELECT COALESCE(SUM(amount), 0) AS total FROM counts "
        "WHERE user_id = ? AND substr(created_at, 1, 10) = ?",
        (user_id, today_str()),
    ).fetchone()
    return int(row["total"])


def add_count(
    conn: sqlite3.Connection, user_id: int, amount: int, intention: str | None = None
) -> dict:
    """ثبت شمارش؛ اگر نیت داده نشود، نیت فعلی کاربر استفاده می‌شود."""
    if not isinstance(amount, int) or isinstance(amount, bool):
        raise SalawatError("تعداد باید عدد صحیح باشد.")
    if amount < 1 or amount > MAX_PER_REQUEST:
        raise SalawatError(f"تعداد باید بین ۱ و {MAX_PER_REQUEST} باشد.")

    user = get_user_by_id(conn, user_id)
    if user is None:
        raise SalawatError("کاربر پیدا نشد.")
    slug = intention or user["current_intention"] or DEFAULT_INTENTION
    if slug not in VALID_INTENTIONS:
        raise SalawatError("نیت نامعتبر است.")

    if _today_total_for_user(conn, user_id) + amount > _daily_cap():
        raise DailyCapExceeded(
            "سقف شمارش امروز شما تکمیل شده است. فردا دوباره ادامه دهید."
        )

    conn.execute(
        "INSERT INTO counts (user_id, intention, amount, created_at) VALUES (?, ?, ?, ?)",
        (user_id, slug, amount, _now_iso()),
    )
    conn.commit()
    return {"intention": slug, "added": amount, "today": _today_total_for_user(conn, user_id)}


# ---------- آمار ----------

def _totals_by_intention(conn: sqlite3.Connection, user_id: int | None = None,
                         day: str | None = None) -> dict[str, int]:
    sql = "SELECT intention, SUM(amount) AS total FROM counts WHERE 1=1"
    params: list = []
    if user_id is not None:
        sql += " AND user_id = ?"
        params.append(user_id)
    if day is not None:
        sql += " AND substr(created_at, 1, 10) = ?"
        params.append(day)
    sql += " GROUP BY intention"
    totals = {item["slug"]: 0 for item in INTENTIONS}
    for row in conn.execute(sql, params).fetchall():
        totals[row["intention"]] = int(row["total"])
    return totals


def _intention_list(totals: dict[str, int]) -> list[dict]:
    return [
        {**item, "total": totals.get(item["slug"], 0)} for item in INTENTIONS
    ]


def _total(conn: sqlite3.Connection, user_id: int | None = None,
           day: str | None = None) -> int:
    return sum(_totals_by_intention(conn, user_id, day).values())


def user_stats(conn: sqlite3.Connection, user_id: int, days: int = 7) -> dict:
    today = now_tehran().date()
    series = []
    for offset in range(days - 1, -1, -1):
        day = (today - dt.timedelta(days=offset)).strftime("%Y-%m-%d")
        series.append({"day": day, "total": _total(conn, user_id, day)})
    user = get_user_by_id(conn, user_id)
    return {
        "total": _total(conn, user_id),
        "today": _total(conn, user_id, today_str()),
        "current_intention": user["current_intention"] if user else DEFAULT_INTENTION,
        "by_intention": _intention_list(_totals_by_intention(conn, user_id)),
        "last_days": series,
    }


def get_daily_goal(conn: sqlite3.Connection) -> int:
    row = conn.execute("SELECT value FROM settings WHERE key = 'daily_goal'").fetchone()
    return int(row["value"]) if row else DEFAULT_DAILY_GOAL


def set_daily_goal(conn: sqlite3.Connection, goal: int) -> None:
    if goal < 1:
        raise SalawatError("هدف باید بزرگ‌تر از صفر باشد.")
    conn.execute(
        "INSERT INTO settings (key, value) VALUES ('daily_goal', ?) "
        "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        (str(goal),),
    )
    conn.commit()


def global_stats(conn: sqlite3.Connection) -> dict:
    today = today_str()
    today_total = _total(conn, None, today)
    goal = get_daily_goal(conn)
    participants = conn.execute(
        "SELECT COUNT(DISTINCT user_id) AS n FROM counts WHERE substr(created_at, 1, 10) = ?",
        (today,),
    ).fetchone()["n"]
    return {
        "total": _total(conn),
        "today": today_total,
        "participants_today": int(participants),
        "daily_goal": goal,
        "goal_progress_percent": min(100, round(today_total * 100 / goal, 1)),
        "by_intention": _intention_list(_totals_by_intention(conn)),
        "today_by_intention": _intention_list(_totals_by_intention(conn, None, today)),
    }
