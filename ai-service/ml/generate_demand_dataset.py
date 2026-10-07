"""
generate_demand_dataset.py
──────────────────────────
Generates fully synthetic illustrative demand records for verified Bengaluru institutions.
These are not historical hospital data, real requests, patient data, or inventory records.
The synthetic institution and item labels align with the controlled demo directory. Schema:
date, hospital_id, medicine_id, medicine_category, quantity_requested, quantity_delivered,
emergency_requests, day_of_week, month, holiday, temperature, rainfall,
previous_day_demand, previous_week_demand, demand_quantity
"""
import os
import pandas as pd
import numpy as np
from datetime import datetime, timedelta

def generate_demand_data():
    np.random.seed(42)
    start_date = datetime(2025, 1, 1)
    days = 400

    hospitals = [
        {"id": 1, "name": "Aster CMI Hospital", "zone": "NORTH_ZONE", "base_mult": 1.2},
        {"id": 2, "name": "Bangalore Baptist Hospital", "zone": "NORTH_ZONE", "base_mult": 0.9},
        {"id": 3, "name": "Ramaiah Medical College Hospital", "zone": "NORTH_ZONE", "base_mult": 1.3},
        {"id": 6, "name": "Manipal Hospital Whitefield", "zone": "EAST_ZONE", "base_mult": 1.4},
        {"id": 7, "name": "Vydehi Institute of Medical Sciences", "zone": "EAST_ZONE", "base_mult": 1.1},
        {"id": 9, "name": "Sakra World Hospital", "zone": "EAST_ZONE", "base_mult": 1.2},
        {"id": 11, "name": "Mazumdar Shaw Medical Centre (Narayana Health)", "zone": "SOUTH_ZONE", "base_mult": 1.8},
        {"id": 12, "name": "Fortis Hospital, Bannerghatta Road", "zone": "SOUTH_ZONE", "base_mult": 1.3},
        {"id": 15, "name": "St. John's Medical College Hospital", "zone": "SOUTH_ZONE", "base_mult": 1.6},
        {"id": 16, "name": "Victoria Hospital", "zone": "WEST_ZONE", "base_mult": 2.0},
        {"id": 17, "name": "Bowring Hospital", "zone": "WEST_ZONE", "base_mult": 1.4},
        {"id": 18, "name": "K.C. General Hospital", "zone": "WEST_ZONE", "base_mult": 1.2}
    ]

    medicines = [
        {"id": 1, "code": "MED-BLD-OPOS", "name": "O Positive Whole Blood", "category": "BLOOD", "base_demand": 35},
        {"id": 2, "code": "MED-BLD-ONEG", "name": "O Negative Whole Blood", "category": "BLOOD", "base_demand": 20},
        {"id": 3, "code": "MED-BLD-APOS", "name": "A Positive Blood", "category": "BLOOD", "base_demand": 25},
        {"id": 4, "code": "MED-PLT-SDP", "name": "Single Donor Platelets", "category": "BLOOD", "base_demand": 15},
        {"id": 5, "code": "MED-ORG-KID", "name": "Simulated kidney transport container", "category": "ORGAN", "base_demand": 2},
        {"id": 6, "code": "MED-ORG-COR", "name": "Simulated cornea transport container", "category": "ORGAN", "base_demand": 3},
        {"id": 7, "code": "MED-VAC-RAB", "name": "Anti-Rabies Vaccine", "category": "VACCINE", "base_demand": 18},
        {"id": 8, "code": "MED-VAC-COV", "name": "COVID-19 Booster Vaccine", "category": "VACCINE", "base_demand": 30},
        {"id": 9, "code": "MED-DRG-AVM", "name": "Polyvalent Snake Antivenom", "category": "ANTIVENOM", "base_demand": 8},
        {"id": 10, "code": "MED-DRG-EPI", "name": "Epinephrine Auto-Injectors", "category": "EMERGENCY_MEDICINE", "base_demand": 22},
        {"id": 11, "code": "MED-DEV-AED", "name": "Defibrillator Pads", "category": "DEFIBRILLATOR", "base_demand": 12}
    ]

    records = []

    # Pre-compute time-series demands per (hospital, medicine)
    for h in hospitals:
        for m in medicines:
            prev_day = m["base_demand"] * h["base_mult"]
            prev_week = m["base_demand"] * h["base_mult"]

            for day_idx in range(days):
                curr_date = start_date + timedelta(days=day_idx)
                month = curr_date.month
                day_of_week = curr_date.weekday() # 0 = Monday, 6 = Sunday
                is_weekend = 1 if day_of_week >= 5 else 0
                is_holiday = 1 if (curr_date.day == 1 and month == 1) or (curr_date.day == 15 and month == 8) or (curr_date.day == 26 and month == 1) or (curr_date.day == 2 and month == 10) else 0

                # Bengaluru Weather seasonal baseline
                if month in [3, 4, 5]: # Summer
                    temp = np.random.normal(32.0, 2.5)
                    rain = max(0.0, np.random.normal(1.0, 3.0))
                elif month in [6, 7, 8, 9]: # Monsoon
                    temp = np.random.normal(26.0, 2.0)
                    rain = max(0.0, np.random.exponential(12.0))
                else: # Winter
                    temp = np.random.normal(24.0, 2.5)
                    rain = max(0.0, np.random.normal(0.5, 1.5))

                # Demand calculation with seasonality & weekend/weather surge
                weekday_factor = 1.25 if day_of_week in [0, 4] else (0.85 if is_weekend else 1.0)
                weather_factor = 1.35 if (m["category"] in ["ANTIVENOM", "EMERGENCY_MEDICINE", "BLOOD"] and rain > 15.0) else 1.0
                holiday_factor = 1.2 if (is_holiday and m["category"] in ["BLOOD", "EMERGENCY_MEDICINE"]) else 1.0

                expected_demand = (m["base_demand"] * h["base_mult"] * weekday_factor * weather_factor * holiday_factor)
                noise = np.random.normal(0, max(1.0, expected_demand * 0.12))
                actual_demand = max(0, int(round(expected_demand + noise)))

                emergency_pct = 0.25 if m["category"] in ["BLOOD", "ORGAN", "ANTIVENOM"] else 0.10
                emergency_requests = int(round(actual_demand * emergency_pct * np.random.uniform(0.8, 1.3)))
                quantity_delivered = min(actual_demand, int(actual_demand * np.random.uniform(0.88, 1.0)))

                records.append({
                    "date": curr_date.strftime("%Y-%m-%d"),
                    "hospital_id": h["id"],
                    "hospital_name": h["name"],
                    "zone": h["zone"],
                    "medicine_id": m["id"],
                    "medicine_code": m["code"],
                    "medicine_category": m["category"],
                    "quantity_requested": actual_demand,
                    "quantity_delivered": quantity_delivered,
                    "emergency_requests": emergency_requests,
                    "day_of_week": day_of_week,
                    "month": month,
                    "holiday": is_holiday,
                    "temperature": round(temp, 1),
                    "rainfall": round(rain, 1),
                    "previous_day_demand": int(round(prev_day)),
                    "previous_week_demand": int(round(prev_week)),
                    "demand_quantity": actual_demand
                })

                prev_day = actual_demand
                if day_idx >= 7:
                    prev_week = records[-7]["demand_quantity"]

    df = pd.DataFrame(records)
    out_dir = os.path.join(os.path.dirname(__file__), '..', 'data')
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, 'medical_demand_forecasting_dataset.csv')
    df.to_csv(out_path, index=False)
    print(f"Successfully generated medical demand dataset with {len(df)} rows at {out_path}")
    return out_path

if __name__ == "__main__":
    generate_demand_data()
