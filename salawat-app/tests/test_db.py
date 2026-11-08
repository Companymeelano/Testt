import pytest

from backend import db


def test_add_count_uses_current_intention():
    conn = db.connect()
    user = db.create_user(conn)
    db.set_intention(conn, user["id"], "zohoor")
    r = db.add_count(conn, user["id"], 10)
    assert r["intention"] == "zohoor"
    assert r["added"] == 10
    assert r["today"] == 10


@pytest.mark.parametrize("bad", [0, -1, db.MAX_PER_REQUEST + 1])
def test_amount_out_of_range_rejected(bad):
    conn = db.connect()
    user = db.create_user(conn)
    with pytest.raises(db.SalawatError):
        db.add_count(conn, user["id"], bad)


def test_invalid_intention_rejected():
    conn = db.connect()
    user = db.create_user(conn)
    with pytest.raises(db.SalawatError):
        db.set_intention(conn, user["id"], "unknown")
    with pytest.raises(db.SalawatError):
        db.add_count(conn, user["id"], 1, intention="unknown")


def test_daily_cap(monkeypatch):
    monkeypatch.setenv("SALAWAT_DAILY_CAP", "50")
    conn = db.connect()
    user = db.create_user(conn)
    db.add_count(conn, user["id"], 50)
    with pytest.raises(db.DailyCapExceeded):
        db.add_count(conn, user["id"], 1)


def test_stats_split_by_intention_and_global():
    conn = db.connect()
    a = db.create_user(conn)
    b = db.create_user(conn)
    db.add_count(conn, a["id"], 100, intention="health")
    db.add_count(conn, a["id"], 5, intention="yari")
    db.add_count(conn, b["id"], 20, intention="health")

    s = db.user_stats(conn, a["id"])
    assert s["total"] == 105
    by = {i["slug"]: i["total"] for i in s["by_intention"]}
    assert by == {"health": 100, "zohoor": 0, "yari": 5}
    assert len(s["last_days"]) == 7

    g = db.global_stats(conn)
    assert g["total"] == 125
    assert g["participants_today"] == 2
    assert g["daily_goal"] == db.DEFAULT_DAILY_GOAL
    assert g["goal_progress_percent"] == round(125 * 100 / db.DEFAULT_DAILY_GOAL, 1)


def test_goal_update():
    conn = db.connect()
    db.set_daily_goal(conn, 500)
    assert db.get_daily_goal(conn) == 500
    with pytest.raises(db.SalawatError):
        db.set_daily_goal(conn, 0)


def test_rubika_user_is_stable_per_chat():
    conn = db.connect()
    u1 = db.get_or_create_rubika_user(conn, "b0abc")
    u2 = db.get_or_create_rubika_user(conn, "b0abc")
    assert u1["id"] == u2["id"]
