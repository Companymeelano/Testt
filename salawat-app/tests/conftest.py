import os
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))


@pytest.fixture(autouse=True)
def tmp_db(tmp_path, monkeypatch):
    monkeypatch.setenv("SALAWAT_DB", str(tmp_path / "test.db"))
    monkeypatch.setenv("SALAWAT_ADMIN_KEY", "test-admin")
    monkeypatch.delenv("SALAWAT_DAILY_CAP", raising=False)
    yield tmp_path / "test.db"
