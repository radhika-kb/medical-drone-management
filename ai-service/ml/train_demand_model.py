"""
train_demand_model.py
─────────────────────
Trains Random Forest and XGBoost regressors on historical medical item demand
to predict future demand quantities and evaluate inventory shortage risk.
"""
import os
import pandas as pd
import numpy as np
import joblib
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.compose import ColumnTransformer
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.ensemble import RandomForestRegressor
from xgboost import XGBRegressor
from sklearn.metrics import mean_squared_error, mean_absolute_error, r2_score

def train_demand_model():
    data_path = os.path.join(os.path.dirname(__file__), '..', 'data', 'medical_demand_forecasting_dataset.csv')
    df = pd.read_csv(data_path)

    feature_cols = [
        'hospital_id', 'medicine_id', 'medicine_category', 'day_of_week',
        'month', 'holiday', 'temperature', 'rainfall',
        'previous_day_demand', 'previous_week_demand', 'emergency_requests'
    ]
    target_col = 'demand_quantity'

    X = df[feature_cols]
    y = df[target_col]

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    cat_features = ['medicine_category']
    num_features = [c for c in feature_cols if c not in cat_features]

    preprocessor = ColumnTransformer(
        transformers=[
            ('num', StandardScaler(), num_features),
            ('cat', OneHotEncoder(handle_unknown='ignore'), cat_features)
        ]
    )

    models = {
        "Random Forest Regressor": RandomForestRegressor(n_estimators=100, max_depth=15, random_state=42, n_jobs=-1),
        "XGBoost Regressor": XGBRegressor(n_estimators=120, max_depth=6, learning_rate=0.08, random_state=42)
    }

    best_model_name = None
    best_pipeline = None
    best_r2 = -100.0

    print("--- Training Demand Forecasting ML Models ---")
    for name, model in models.items():
        pipe = Pipeline(steps=[
            ('preprocessor', preprocessor),
            ('regressor', model)
        ])
        pipe.fit(X_train, y_train)
        y_pred = pipe.predict(X_test)

        mae = mean_absolute_error(y_test, y_pred)
        rmse = np.sqrt(mean_squared_error(y_test, y_pred))
        r2 = r2_score(y_test, y_pred)

        print(f"Model: {name} | MAE: {mae:.2f} | RMSE: {rmse:.2f} | R²: {r2:.4f}")

        if r2 > best_r2:
            best_r2 = r2
            best_model_name = name
            best_pipeline = pipe

    print(f"\nSelected Best Model: {best_model_name} (R² = {best_r2:.4f})")

    models_dir = os.path.join(os.path.dirname(__file__), '..', 'models')
    os.makedirs(models_dir, exist_ok=True)
    out_file = os.path.join(models_dir, 'demand_forecast_model.joblib')

    joblib.dump({
        'pipeline': best_pipeline,
        'model_name': best_model_name,
        'feature_names': feature_cols,
        'r2_score': best_r2
    }, out_file)
    print(f"Saved Demand Forecast ML Pipeline to {out_file}")

if __name__ == "__main__":
    train_demand_model()
