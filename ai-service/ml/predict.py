import os
import joblib
import pandas as pd
import numpy as np
from typing import Dict, Any

from app.core.logging import get_logger
from ml.schemas import WeatherFeaturesInput

logger = get_logger(__name__)

# Cache the loaded artifacts globally so we don't load from disk on every API call
_ml_artifacts = None

def get_ml_artifacts() -> Dict[str, Any]:
    """Load the trained ML pipeline and associated artifacts lazily."""
    global _ml_artifacts
    if _ml_artifacts is None:
        model_path = os.path.join(os.path.dirname(__file__), '..', 'models', 'weather_flight_condition.joblib')
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"ML model not found at {model_path}. Please run train.py first.")
        
        logger.info(f"Loading ML pipeline from {model_path}")
        _ml_artifacts = joblib.load(model_path)
        
    return _ml_artifacts

def predict_flight_condition(features: WeatherFeaturesInput) -> Dict[str, Any]:
    """
    Predict flight condition based on real-time weather features.
    
    Args:
        features: WeatherFeaturesInput schema (enforces no target leakage).
        
    Returns:
        dict with flight_condition (SAFE, CAUTION, UNSAFE) and probability.
    """
    artifacts = get_ml_artifacts()
    pipeline = artifacts['pipeline']
    label_encoder = artifacts.get('label_encoder')
    
    # Convert Pydantic model to dictionary, then to DataFrame (1 row)
    # This guarantees the input matches the schema exactly
    feature_dict = features.model_dump()
    df_input = pd.DataFrame([feature_dict])
    
    # Get probabilities
    probas = pipeline.predict_proba(df_input)[0]
    
    # Get prediction class
    if label_encoder is not None:
        # For XGBoost
        pred_encoded = pipeline.predict(df_input)
        flight_condition = label_encoder.inverse_transform(pred_encoded)[0]
        max_proba = np.max(probas)
    else:
        # For Logistic Regression / Random Forest
        flight_condition = pipeline.predict(df_input)[0]
        max_proba = np.max(probas)
        
    return {
        "flight_condition": flight_condition,
        "probability": float(max_proba)
    }

if __name__ == "__main__":
    # Quick manual test
    mock_input = WeatherFeaturesInput(
        temperature_c=28.5,
        humidity_pct=85.0,
        precipitation_mm=12.5,  # High rain should trigger UNSAFE
        wind_speed_kmh=15.0,
        wind_gust_kmh=22.0,
        wind_direction_deg=180,
        visibility_km=1.5,      # Low visibility
        pressure_hpa=1005.0,
        cloud_cover_pct=95.0,
        dew_point_c=26.0,
        uv_index=2.0,
        hour=14,
        month=8,
        day_of_week=3,
        weather_condition="Heavy Rain",
        rain_event=1
    )
    
    result = predict_flight_condition(mock_input)
    print(f"Prediction Result: {result}")
