import os
import pandas as pd
import numpy as np
import joblib
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.compose import ColumnTransformer
from sklearn.preprocessing import StandardScaler, OneHotEncoder, LabelEncoder
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, f1_score, recall_score, confusion_matrix, ConfusionMatrixDisplay
from xgboost import XGBClassifier
from sklearn.utils.class_weight import compute_sample_weight
import matplotlib.pyplot as plt

def main():
    # 1. Load the dataset
    data_path = os.path.join(os.path.dirname(__file__), '..', 'data', 'bengaluru_medical_drone_weather_dataset.csv')
    df = pd.read_csv(data_path)
    
    # 2. Define target and features
    target_col = 'flight_condition'
    
    # CRITICAL: Exclude target leakage variables and identifiers
    leakage_cols = ['flight_condition', 'flight_risk_score', 'prediction_confidence']
    exclude_cols = [col for col in leakage_cols if col in df.columns]
    
    # Also exclude non-feature identifiers
    identifiers = ['timestamp', 'location', 'latitude', 'longitude']
    exclude_cols.extend([col for col in identifiers if col in df.columns])
    
    X = df.drop(columns=exclude_cols)
    y = df[target_col]
    
    print("Feature columns:", X.columns.tolist())
    
    # 3. Train / Test Split (Stratified)
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, stratify=y, random_state=42)
    
    # Encode target labels explicitly to numbers for XGBoost
    label_encoder = LabelEncoder()
    y_train_encoded = label_encoder.fit_transform(y_train)
    y_test_encoded = label_encoder.transform(y_test)
    
    # Compute sample weights for XGBoost due to extreme imbalance
    sample_weights = compute_sample_weight(class_weight='balanced', y=y_train_encoded)
    
    # 4. Preprocessing
    categorical_cols = ['weather_condition']
    numeric_cols = [col for col in X.columns if col not in categorical_cols]
    
    preprocessor = ColumnTransformer(
        transformers=[
            ('num', StandardScaler(), numeric_cols),
            ('cat', OneHotEncoder(handle_unknown='ignore'), categorical_cols)
        ]
    )
    
    # 5. Define Models
    models = {
        'Logistic Regression': LogisticRegression(class_weight='balanced', max_iter=1000, random_state=42),
        'Random Forest': RandomForestClassifier(class_weight='balanced', random_state=42, n_jobs=-1),
        'XGBoost': XGBClassifier(random_state=42, use_label_encoder=False, eval_metric='mlogloss')
    }
    
    best_model_name = None
    best_model_pipeline = None
    best_score = -1
    
    print("\n--- Model Training & Evaluation ---")
    for name, model in models.items():
        pipeline = Pipeline(steps=[
            ('preprocessor', preprocessor),
            ('classifier', model)
        ])
        
        # XGBoost handles weights slightly differently in pipeline fit
        if name == 'XGBoost':
            pipeline.fit(X_train, y_train_encoded, classifier__sample_weight=sample_weights)
            y_pred_encoded = pipeline.predict(X_test)
            y_pred = label_encoder.inverse_transform(y_pred_encoded)
        else:
            pipeline.fit(X_train, y_train)
            y_pred = pipeline.predict(X_test)
            
        # Evaluate
        macro_f1 = f1_score(y_test, y_pred, average='macro')
        unsafe_recall = recall_score(y_test, y_pred, labels=['UNSAFE'], average='micro') if 'UNSAFE' in y_test.values else 0.0
        
        print(f"\nModel: {name}")
        print(f"Macro F1 Score: {macro_f1:.4f}")
        print(f"UNSAFE Recall: {unsafe_recall:.4f}")
        print(classification_report(y_test, y_pred, zero_division=0))
        
        # Selection logic: Maximize macro-F1, use UNSAFE recall as tie-breaker/primary filter
        score = (macro_f1 * 0.4) + (unsafe_recall * 0.6) # Weighted score favoring unsafe recall
        
        if score > best_score:
            best_score = score
            best_model_name = name
            best_model_pipeline = pipeline
            
    print(f"\nBest Model Selected: {best_model_name}")
    
    # 6. Save Confusion Matrix for Best Model
    if best_model_name == 'XGBoost':
        y_pred_encoded = best_model_pipeline.predict(X_test)
        y_pred = label_encoder.inverse_transform(y_pred_encoded)
    else:
        y_pred = best_model_pipeline.predict(X_test)
        
    cm = confusion_matrix(y_test, y_pred, labels=label_encoder.classes_)
    disp = ConfusionMatrixDisplay(confusion_matrix=cm, display_labels=label_encoder.classes_)
    
    models_dir = os.path.join(os.path.dirname(__file__), '..', 'models')
    os.makedirs(models_dir, exist_ok=True)
    
    disp.plot(cmap='Blues')
    plt.title(f"{best_model_name} Confusion Matrix")
    plt.savefig(os.path.join(models_dir, 'confusion_matrix.png'))
    print(f"Saved confusion matrix to {models_dir}/confusion_matrix.png")
    
    # 7. Package and Persist Pipeline
    final_artifacts = {
        'pipeline': best_model_pipeline,
        'label_encoder': label_encoder if best_model_name == 'XGBoost' else None,
        'model_name': best_model_name
    }
    
    model_path = os.path.join(models_dir, 'weather_flight_condition.joblib')
    joblib.dump(final_artifacts, model_path)
    print(f"\nSuccessfully saved ML pipeline to {model_path}")

if __name__ == "__main__":
    main()
