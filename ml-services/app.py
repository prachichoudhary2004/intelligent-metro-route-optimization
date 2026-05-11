from flask import Flask, request, jsonify
from flask_cors import CORS
import numpy as np
import pandas as pd
from datetime import datetime
import joblib
import os

app = Flask(__name__)
CORS(app)

# Load trained models
MODELS_PATH = 'models'
try:
    congestion_model = joblib.load(os.path.join(MODELS_PATH, 'congestion_model.joblib'))
    delay_model = joblib.load(os.path.join(MODELS_PATH, 'delay_model.joblib'))
    demand_model = joblib.load(os.path.join(MODELS_PATH, 'demand_model.joblib'))
    MODELS_LOADED = True
    print("[SUCCESS] ML Models loaded successfully.")
except Exception as e:
    MODELS_LOADED = False
    print(f"[WARNING] Failed to load ML models: {e}. Falling back to heuristics.")

def get_station_type(station_id):
    """Helper to determine station type based on ID or metadata (simplified)."""
    # 0: Residential, 1: Commercial, 2: Junction
    junctions = ['RC', 'YB', 'NDLS', 'KMG', 'Rajiv Chowk', 'Yamuna Bank', 'New Delhi', 'Kashmere Gate']
    commercial = ['ND62', 'BG', 'HCC', 'SS', 'NSP', 'Noida Sector 62', 'Botanical Garden', 'Huda City Center', 'Shivaji Stadium', 'Netaji Subhash Place']
    if station_id in junctions: return 2
    if station_id in commercial: return 1
    return 0

@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({
        "status": "healthy",
        "models_loaded": MODELS_LOADED,
        "timestamp": datetime.now().isoformat()
    })

@app.route('/api/predict/congestion', methods=['POST'])
def predict_congestion():
    data = request.json
    stations = data.get('stations', [])
    hour = data.get('time', datetime.now().hour)
    day_of_week = datetime.now().weekday()
    
    predictions = {}
    
    for station_id in stations:
        station_type = get_station_type(station_id)
        
        if MODELS_LOADED:
            # Prepare features
            features = pd.DataFrame([[hour, day_of_week, station_type]], 
                                 columns=['hour', 'day_of_week', 'station_type'])
            
            # Predict
            cong = congestion_model.predict(features)[0]
            delay = delay_model.predict(features)[0]
            demand = int(demand_model.predict(features)[0])
            
            # Confidence estimation (simplified: based on distance from peak hours)
            peak_dist = min(abs(hour - 9), abs(hour - 18))
            confidence = max(0.7, 0.95 - (peak_dist * 0.02))
            
            predictions[station_id] = {
                "congestion": round(float(cong), 2),
                "delay": round(float(delay), 2),
                "demand": int(demand),
                "confidence": round(float(confidence), 2),
                "fallback_used": False
            }
        else:
            # Heuristic Fallback
            is_peak = (8 <= hour <= 10) or (17 <= hour <= 19)
            cong = 3.0 + (5.0 if is_peak else 0.0) + (2.0 if station_type == 2 else 0.0)
            predictions[station_id] = {
                "congestion": cong,
                "delay": cong * 0.8,
                "demand": 100 + int(cong * 50),
                "confidence": 0.5,
                "fallback_used": True
            }
            
    return jsonify({
        "predictions": predictions,
        "model_info": "RandomForestRegressor_v1" if MODELS_LOADED else "HeuristicFallback"
    })

if __name__ == '__main__':
    print("[SYSTEM] Starting Production ML Prediction Services...")
    app.run(port=5000, debug=False)
