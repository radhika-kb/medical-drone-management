from app.api.endpoints import ai_decision


def test_monitoring_model_failure_is_explicit_and_fails_safe(monkeypatch):
    monkeypatch.setattr(ai_decision, "get_model", lambda _name: None)
    req = ai_decision.MonitorRequest(
        mission_id=7,
        elapsed_minutes=2,
        distance_flown_km=1,
        total_distance_km=10,
        expected_duration_minutes=20,
        battery_used_pct=2,
    )

    result = ai_decision.run_monitoring_anomaly_agent(req)

    assert result["monitoring_available"] is False
    assert result["is_anomaly"] is True
    assert result["anomaly_type"] == "MONITORING_UNAVAILABLE"
    assert result["severity"] == "CRITICAL"


def test_normal_monitoring_model_result_is_available_and_nominal(monkeypatch):
    class Scaler:
        def transform(self, frame):
            return frame

    class IsolationForest:
        def score_samples(self, _features):
            return [-0.1]

    class Classifier:
        def predict(self, _features):
            return ["NORMAL"]

    monkeypatch.setattr(ai_decision, "get_model", lambda _name: {
        "scaler": Scaler(), "isolation_forest": IsolationForest(),
        "classifier": Classifier(), "min_score": 0.0, "max_score": 1.0,
    })
    req = ai_decision.MonitorRequest(
        mission_id=8,
        elapsed_minutes=2,
        distance_flown_km=1,
        total_distance_km=10,
        expected_duration_minutes=20,
        battery_used_pct=2,
    )

    result = ai_decision.run_monitoring_anomaly_agent(req)

    assert result["monitoring_available"] is True
    assert result["is_anomaly"] is False


def test_anomalous_monitoring_sample_is_classified(monkeypatch):
    class Scaler:
        def transform(self, frame):
            return frame

    class IsolationForest:
        def score_samples(self, _features):
            return [-0.95]

    class Classifier:
        def predict(self, _features):
            return ["BATTERY_DRAIN"]

    monkeypatch.setattr(ai_decision, "get_model", lambda _name: {
        "scaler": Scaler(), "isolation_forest": IsolationForest(),
        "classifier": Classifier(), "min_score": 0.0, "max_score": 1.0,
    })
    req = ai_decision.MonitorRequest(
        mission_id=10,
        elapsed_minutes=2,
        distance_flown_km=1,
        total_distance_km=10,
        expected_duration_minutes=20,
        battery_used_pct=40,
    )

    result = ai_decision.run_monitoring_anomaly_agent(req)

    assert result["monitoring_available"] is True
    assert result["is_anomaly"] is True
    assert result["anomaly_type"] == "BATTERY_DRAIN"


def test_monitoring_inference_error_is_not_reported_as_normal(monkeypatch):
    class BrokenScaler:
        def transform(self, _frame):
            raise RuntimeError("inference unavailable")

    monkeypatch.setattr(ai_decision, "get_model", lambda _name: {
        "scaler": BrokenScaler(), "isolation_forest": object(), "classifier": object(),
        "min_score": 0.0, "max_score": 1.0,
    })
    req = ai_decision.MonitorRequest(
        mission_id=9,
        elapsed_minutes=2,
        distance_flown_km=1,
        total_distance_km=10,
        expected_duration_minutes=20,
        battery_used_pct=2,
    )

    result = ai_decision.run_monitoring_anomaly_agent(req)

    assert result["monitoring_available"] is False
    assert result["is_anomaly"] is True
    assert result["anomaly_type"] == "MONITORING_UNAVAILABLE"
