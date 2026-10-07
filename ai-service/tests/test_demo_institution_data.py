"""Offline validation for institution metadata used in the demo datasets."""
import csv
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = json.loads((ROOT / "sql" / "bengaluru-demo-institutions.json").read_text(encoding="utf-8"))
INSTITUTIONS = DIRECTORY["institutions"]


def test_demo_institution_allowlist_is_bengaluru_and_sources_are_present():
    assert "simulation" in DIRECTORY["metadata"]["relationshipNotice"].lower()
    assert INSTITUTIONS
    ids = [entry["id"] for entry in INSTITUTIONS]
    names = [entry["name"] for entry in INSTITUTIONS]
    assert len(ids) == len(set(ids))
    assert len(names) == len(set(names))

    for entry in INSTITUTIONS:
        assert entry["type"] in {"HOSPITAL", "BLOOD_BANK"}
        assert "Bengaluru" in entry["address"]
        assert entry["source"].startswith("https://")
        assert not re.search(r"\b(ABC|Demo|Sample|Fictional|Placeholder)\s+(Hospital|Clinic|Blood Bank)\b", entry["name"], re.I)
        lat, lon = entry["latitude"], entry["longitude"]
        assert (lat is None) == (lon is None)
        if lat is not None:
            assert 12.7 <= lat <= 13.3
            assert 77.3 <= lon <= 78.0


def test_fresh_mysql_healthcare_seed_matches_the_controlled_directory():
    sql = (ROOT / "sql" / "init-mysql.sql").read_text(encoding="utf-8")
    seed_section = sql.split("-- 4. Verified Bengaluru healthcare directory", 1)[1].split("-- 5.", 1)[0]
    rows = re.findall(r"^\((\d+), '((?:[^'\\]|\\.)*)', '(HOSPITAL|BLOOD_BANK|TISSUE_BANK)', '([^']*)', (NULL|[-\d.]+), (NULL|[-\d.]+)", seed_section, re.M)
    seeded = {int(row[0]): {"name": row[1].replace("\\'", "'"), "type": row[2], "address": row[3], "latitude": None if row[4] == "NULL" else float(row[4]), "longitude": None if row[5] == "NULL" else float(row[5])} for row in rows}
    expected = {entry["id"]: {key: entry[key] for key in ("name", "type", "address", "latitude", "longitude")} for entry in INSTITUTIONS}
    assert seeded == expected


def test_synthetic_demand_dataset_uses_only_allowlisted_institutions():
    expected = {entry["id"]: entry["name"] for entry in INSTITUTIONS}
    csv_path = ROOT / "ai-service" / "data" / "medical_demand_forecasting_dataset.csv"
    with csv_path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            hospital_id = int(row["hospital_id"])
            assert hospital_id in expected
            assert row["hospital_name"] == expected[hospital_id]
            assert "simulat" not in row["hospital_name"].lower()


def test_demand_generator_institution_labels_are_allowlisted():
    generator = (ROOT / "ai-service" / "ml" / "generate_demand_dataset.py").read_text(encoding="utf-8").split("    medicines = [", 1)[0]
    parsed = re.findall(r'\{"id":\s*(\d+),\s*"name":\s*"([^"]+)"', generator)
    expected = {entry["id"]: entry["name"] for entry in INSTITUTIONS}
    assert parsed
    for raw_id, name in parsed:
        assert expected[int(raw_id)] == name

