"""API و سرو صفحهٔ وب برای شمارش صلوات بر امام زمان (عج)."""

from __future__ import annotations

import os
import sqlite3
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from . import db

WEB_DIR = Path(__file__).resolve().parent.parent / "web"

app = FastAPI(title="صلوات برای امام زمان (عج)", version="1.0.0")


class CountIn(BaseModel):
    amount: int = Field(ge=1, le=db.MAX_PER_REQUEST)
    intention: str | None = None


class IntentionIn(BaseModel):
    intention: str


class GoalIn(BaseModel):
    goal: int = Field(ge=1)


def get_conn():
    conn = db.connect()
    try:
        yield conn
    finally:
        conn.close()


Conn = Annotated[sqlite3.Connection, Depends(get_conn)]


def current_user(conn: Conn, x_token: Annotated[str | None, Header()] = None) -> dict:
    user = db.get_user_by_token(conn, x_token or "")
    if user is None:
        raise HTTPException(status_code=401, detail="ابتدا یک جلسه بسازید.")
    return user


CurrentUser = Annotated[dict, Depends(current_user)]


@app.exception_handler(db.SalawatError)
async def salawat_error_handler(_, exc: db.SalawatError):
    status = 429 if isinstance(exc, db.DailyCapExceeded) else 400
    return JSONResponse(status_code=status, content={"detail": str(exc)})


@app.get("/api/health")
def health():
    return {"status": "ok"}


@app.get("/api/intentions")
def intentions():
    return {"intentions": db.INTENTIONS}


@app.post("/api/session", status_code=201)
def create_session(conn: Conn):
    """ساخت کاربر ناشناس. توکن باید در مرورگر ذخیره شود."""
    user = db.create_user(conn)
    return {"token": user["token"], "current_intention": user["current_intention"]}


@app.post("/api/me/intention")
def set_intention(body: IntentionIn, conn: Conn, user: CurrentUser):
    db.set_intention(conn, user["id"], body.intention)
    return {"current_intention": body.intention}


@app.post("/api/count")
def count(body: CountIn, conn: Conn, user: CurrentUser):
    return db.add_count(conn, user["id"], body.amount, body.intention)


@app.get("/api/me/stats")
def me_stats(conn: Conn, user: CurrentUser):
    return db.user_stats(conn, user["id"])


@app.get("/api/stats/global")
def global_stats(conn: Conn):
    return db.global_stats(conn)


@app.post("/api/admin/goal")
def admin_goal(body: GoalIn, conn: Conn, x_admin_key: Annotated[str | None, Header()] = None):
    """تنظیم هدف روزانهٔ جمعی؛ فقط با کلید مدیر (متغیر SALAWAT_ADMIN_KEY)."""
    expected = os.environ.get("SALAWAT_ADMIN_KEY")
    if not expected or x_admin_key != expected:
        raise HTTPException(status_code=403, detail="دسترسی مجاز نیست.")
    db.set_daily_goal(conn, body.goal)
    return {"daily_goal": body.goal}


# صفحهٔ وب باید آخر ثبت شود تا مسیرهای /api بازنویسی نشوند.
app.mount("/", StaticFiles(directory=WEB_DIR, html=True), name="web")
