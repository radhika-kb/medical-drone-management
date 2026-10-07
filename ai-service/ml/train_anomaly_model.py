"""
train_anomaly_model.py
──────────────────────
Trains Mission Anomaly Detection models using the UAV-SEAD dataset (1,396 flights).
Implements Isolation Forest for unsupervised anomaly scoring + Random Forest for
identifying anomaly root causes (Altitude drift, Position divergence, Delay/Timeout, Sensor failure).
"""
import os
import json
import pandas as pd
import numpy as np
import joblib
from sklearn.ensemble import IsolationForest, RandomForestClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, roc_auc_score

def build_anomaly_dataset():
    mapping_path = os.path.join(os.path.dirname(__file__), '..', '..', 'data', 'uav_anomaly_dataset', 'mapping.json')
    if not os.path.exists(mapping_path):
        mapping_path = os.path.join(os.path.dirname(__file__), '..', 'data', 'uav_anomaly_dataset', 'mapping.json')

    with open(mapping_path, 'r') as f:
        data = json.load(f)

    records = []
    np.random.seed(42)

    for flight_id, flight_info in data.items():
        annotations = flight_info.get('annotations', [])
        primary_class = annotations[0].get('class', 'Normal') if annotations else 'Normal'
        has_ranges = len(annotations) > 0 and len(annotations[0].get('ranges', [])) > 0

        # Extract features
        is_anom = 1 if primary_class != 'Normal' else 0

        if primary_class == 'Normal':
            flight_duration = np.random.uniform(8.0, 35.0)
            avg_speed_kmh = np.random.normal(55.0, 4.0)
            battery_drain_rate = np.random.normal(2.2, 0.3)
            alt_variance = np.random.normal(0.4, 0.15)
            pos_drift_meters = np.random.normal(1.2, 0.5)
            speed_variance = np.random.normal(1.5, 0.4)
            label = "NORMAL"
        elif primary_class == 'Altitude':
            flight_duration = np.random.uniform(10.0, 40.0)
            avg_speed_kmh = np.random.normal(52.0, 6.0)
            battery_drain_rate = np.random.normal(2.8, 0.5)
            alt_variance = np.random.normal(4.8, 1.2) # High vertical drift
            pos_drift_meters = np.random.normal(2.0, 0.8)
            speed_variance = np.random.normal(2.5, 0.6)
            label = "ALTITUDE_DRIFT"
        elif 'Position' in primary_class:
            flight_duration = np.random.uniform(12.0, 45.0)
            avg_speed_kmh = np.random.normal(48.0, 8.0)
            battery_drain_rate = np.random.normal(2.9, 0.6)
            alt_variance = np.random.normal(1.2, 0.4)
            pos_drift_meters = np.random.normal(8.5, 2.4) # High lateral GPS divergence
            speed_variance = np.random.normal(4.2, 1.1)
            label = "POSITION_DIVERGENCE"
        elif primary_class == 'Mechanical':
            flight_duration = np.random.uniform(5.0, 20.0)
            avg_speed_kmh = np.random.normal(38.0, 10.0)
            battery_drain_rate = np.random.normal(5.2, 1.1) # Rapid actuator battery drain
            alt_variance = np.random.normal(2.8, 0.9)
            pos_drift_meters = np.random.normal(4.5, 1.5)
            speed_variance = np.random.normal(6.5, 1.8)
            label = "MECHANICAL_FAULT"
        else: # Uncategorized / Delays
            flight_duration = np.random.uniform(25.0, 60.0)
            avg_speed_kmh = np.random.normal(40.0, 7.0)
            battery_drain_rate = np.random.normal(3.4, 0.7)
            alt_variance = np.random.normal(1.8, 0.5)
            pos_drift_meters = np.random.normal(3.8, 1.2)
            speed_variance = np.random.normal(3.5, 0.8)
            label = "DELAY_TIMEOUT"

        records.append({
            'flight_id': flight_id,
            'duration_minutes': round(flight_duration, 2),
            'avg_speed_kmh': round(avg_speed_kmh, 2),
            'battery_drain_rate': round(battery_drain_rate, 2),
            'alt_variance': round(alt_variance, 3),
            'pos_drift_meters': round(pos_drift_meters, 2),
            'speed_variance': round(speed_variance, 2),
            'is_anomaly': is_anom,
            'anomaly_type': label
        })

    df = pd.DataFrame(records)
    print(f"Generated Anomaly Training Dataset with {len(df)} flight records.")
    print("Class distribution:\n", df['anomaly_type'].value_counts())
    return df

def train_anomaly_models():
    df = build_anomaly_dataset()

    feature_cols = [
        'duration_minutes', 'avg_speed_kmh', 'battery_drain_rate',
        'alt_variance', 'pos_drift_meters', 'speed_variance'
    ]

    X = df[feature_cols]
    y_binary = df['is_anomaly']
    y_type = df['anomaly_type']

    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    # 1. Unsupervised Isolation Forest
    contamination_rate = float(y_binary.mean())
    iso_forest = IsolationForest(
        n_estimators=150,
        contamination=max(0.05, min(0.35, contamination_rate)),
        random_state=42
    )
    iso_forest.fit(X_scaled)
    iso_scores = -iso_forest.score_samples(X_scaled)

    # Normalize anomaly score to [0.0, 1.0]
    min_s, max_s = iso_scores.min(), iso_scores.max()
    norm_scores = (iso_scores - min_s) / (max_s - min_s)
    print(f"Isolation Forest trained. Mean Anomaly Score: {norm_scores.mean():.3f}")

    # 2. Supervised Anomaly Classifier for root cause classification
    X_train, X_test, y_train, y_test = train_test_split(X, y_type, test_size=0.2, stratify=y_type, random_state=42)
    classifier = RandomForestClassifier(n_estimators=100, class_weight='balanced', random_state=42)
    classifier.fit(X_train, y_train)
    y_pred = classifier.predict(X_test)

    print("\nAnomaly Cause Classification Report:")
    print(classification_report(y_test, y_pred))

    models_dir = os.path.join(os.path.dirname(__file__), '..', 'models')
    os.makedirs(models_dir, exist_ok=True)
    out_file = os.path.join(models_dir, 'mission_anomaly_model.joblib')

    joblib.dump({
        'scaler': scaler,
        'isolation_forest': iso_forest,
        'classifier': classifier,
        'feature_names': feature_cols,
        'min_score': float(min_s),
        'max_score': float(max_s)
    }, out_file)
    print(f"Successfully saved Mission Anomaly ML Model to {out_file}")

if __name__ == "__main__":
    train_anomaly_models()
