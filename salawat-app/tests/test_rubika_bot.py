from backend import db, rubika_bot as rb


class FakeClient:
    def __init__(self):
        self.sent = []

    def send_message(self, chat_id, text, keypad=None):
        self.sent.append({"chat_id": chat_id, "text": text, "keypad": keypad})
        return {}


def _conn():
    return db.connect()


def test_parse_start_and_button_and_text():
    assert rb.parse_update({"type": "StartedBot", "chat_id": "b0x"}).kind == "start"
    inc = rb.parse_update({"type": "inline_message", "chat_id": "b0x",
                           "inline_message": {"aux_data": {"button_id": "add:10"}}})
    assert inc.kind == "button" and inc.button_id == "add:10"
    inc = rb.parse_update({"type": "NewMessage", "chat_id": "b0x",
                           "new_message": {"text": "۳۳"}})
    assert inc.kind == "text" and inc.text == "۳۳"


def test_button_add_and_persian_digits_text():
    conn = _conn()
    client = FakeClient()
    rb.handle_update({"type": "inline_message", "chat_id": "b0r1",
                      "inline_message": {"aux_data": {"button_id": "add:10"}}}, client, conn)
    rb.handle_update({"type": "NewMessage", "chat_id": "b0r1",
                      "new_message": {"text": "۳۳"}}, client, conn)
    user = db.get_or_create_rubika_user(conn, "b0r1")
    assert db.user_stats(conn, user["id"])["total"] == 43
    assert "ثبت شد" in client.sent[0]["text"]


def test_intention_button_changes_intention():
    conn = _conn()
    client = FakeClient()
    rb.handle_update({"type": "inline_message", "chat_id": "b0r2",
                      "inline_message": {"aux_data": {"button_id": "intent:zohoor"}}}, client, conn)
    user = db.get_or_create_rubika_user(conn, "b0r2")
    assert user["current_intention"] == "zohoor"


def test_start_sends_welcome_with_keypad():
    conn = _conn()
    client = FakeClient()
    rb.handle_update({"type": "StartedBot", "chat_id": "b0r3"}, client, conn)
    assert client.sent[0]["keypad"]["rows"]
    assert "امام زمان" in client.sent[0]["text"]


def test_client_builds_correct_url_and_checks_status():
    import httpx

    seen = {}

    def handler(request: httpx.Request):
        seen["url"] = str(request.url)
        return httpx.Response(200, json={"status": "OK", "data": {"ok": True}})

    client = rb.RubikaClient("TOKEN", http=httpx.Client(transport=httpx.MockTransport(handler)))
    assert client.call("getUpdates", {"limit": 5}) == {"ok": True}
    assert seen["url"] == "https://botapi.rubika.ir/v3/TOKEN/getUpdates"
