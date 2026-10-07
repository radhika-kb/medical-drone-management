# AI demo datasets

`medical_demand_forecasting_dataset.csv` is generated locally by `ml/generate_demand_dataset.py`. Its 400-day series is deterministic synthetic data for model development and demos. It is not collected from the listed hospitals and contains no real requests, patients, deliveries, donor materials, or inventory. Institution labels and IDs must match `../../sql/bengaluru-demo-institutions.json`.

`bengaluru_medical_drone_weather_dataset.csv` is an illustrative model dataset, not a record of an actual flight or patient delivery. Production weather decisions use the active weather service path; the dataset is for model training/evaluation only.

Predictions trained on these datasets are experimental decision support. They are not medical advice or operational authorization.
