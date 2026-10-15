from fastapi.testclient import TestClient

from backend.main import app

client = TestClient(app)


def _session():
    r = client.post("/api/session")
    assert r.status_code == 201
    return {"X-Token": r.json()["token"]}


def test_health_and_intentions():
    assert client.get("/api/health").json() == {"status": "ok"}
    slugs = [i["slug"] for i in client.get("/api/intentions").json()["intentions"]]
    assert slugs == ["health", "zohoor", "yari"]


def test_count_requires_token():
    r = client.post("/api/count", json={"amount": 1})
    assert r.status_code == 401


def test_count_and_stats_flow():
    h = _session()
    r = client.post("/api/count", json={"amount": 10, "intention": "zohoor"}, headers=h)
    assert r.status_code == 200
    assert r.json()["today"] == 10

    me = client.get("/api/me/stats", headers=h).json()
    assert me["total"] == 10
    assert {i["slug"]: i["total"] for i in me["by_intention"]}["zohoor"] == 10

    g = client.get("/api/stats/global").json()
    assert g["total"] >= 10


def test_invalid_amount_is_400():
    h = _session()
    assert client.post("/api/count", json={"amount": 5000}, headers=h).status_code == 422
    r = client.post("/api/count", json={"amount": 1, "intention": "x"}, headers=h)
    assert r.status_code == 400


def test_set_intention():
    h = _session()
    r = client.post("/api/me/intention", json={"intention": "yari"}, headers=h)
    assert r.status_code == 200
    assert client.get("/api/me/stats", headers=h).json()["current_intention"] == "yari"


def test_admin_goal_requires_key():
    assert client.post("/api/admin/goal", json={"goal": 10}).status_code == 403
    r = client.post("/api/admin/goal", json={"goal": 10},
                    headers={"X-Admin-Key": "test-admin"})
    assert r.status_code == 200
    assert client.get("/api/stats/global").json()["daily_goal"] == 10


def test_web_page_served():
    r = client.get("/")
    assert r.status_code == 200
    assert 'dir="rtl"' in r.text
