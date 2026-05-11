import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
import joblib
import os
from datetime import datetime, timedelta

def generate_synthetic_data(num_samples=5000):
    """Generate realistic metro station data for training."""
    np.random.seed(42)
    
    data = []
    # Hours: 0-23
    # Day of week: 0-6 (Mon-Sun)
    # Station type: 0: Residential, 1: Commercial, 2: Junction
    
    for _ in range(num_samples):
        hour = np.random.randint(0, 24)
        day_of_week = np.random.randint(0, 7)
        station_type = np.random.randint(0, 3)
        
        # Base congestion logic
        congestion = 2.0
        
        # Rush hours (8-10, 17-19)
        if (8 <= hour <= 10) or (17 <= hour <= 19):
            congestion += 5.0
            if station_type == 2: # Junctions are worse in rush hour
                congestion += 2.0
        
        # Weekends are different
        if day_of_week >= 5:
            congestion -= 1.0
            if 12 <= hour <= 20: # Afternoon shopping/leisure
                congestion += 3.0
                
        # Add noise
        congestion = np.clip(congestion + np.random.normal(0, 1.0), 0, 10)
        
        # Delay is correlated with congestion
        delay = np.clip(congestion * 0.8 + np.random.normal(0, 0.5), 0, 15)
        
        # Demand
        demand = 100 + congestion * 50 + np.random.normal(0, 20)
        
        data.append([hour, day_of_week, station_type, congestion, delay, demand])
        
    return pd.DataFrame(data, columns=['hour', 'day_of_week', 'station_type', 'congestion', 'delay', 'demand'])

def train_and_save_models():
    print("Generating synthetic training data...")
    df = generate_synthetic_data()
    
    X = df[['hour', 'day_of_week', 'station_type']]
    
    # Models directory
    if not os.path.exists('models'):
        os.makedirs('models')
        
    # 1. Congestion Model
    print("Training Congestion Predictor (Random Forest)...")
    congestion_pipe = Pipeline([
        ('scaler', StandardScaler()),
        ('rf', RandomForestRegressor(n_estimators=100, random_state=42))
    ])
    congestion_pipe.fit(X, df['congestion'])
    joblib.dump(congestion_pipe, 'models/congestion_model.joblib')
    
    # 2. Delay Model
    print("Training Delay Forecaster...")
    delay_pipe = Pipeline([
        ('scaler', StandardScaler()),
        ('rf', RandomForestRegressor(n_estimators=100, random_state=42))
    ])
    delay_pipe.fit(X, df['delay'])
    joblib.dump(delay_pipe, 'models/delay_model.joblib')
    
    # 3. Demand Model
    print("Training Demand Analyzer...")
    demand_pipe = Pipeline([
        ('scaler', StandardScaler()),
        ('rf', RandomForestRegressor(n_estimators=100, random_state=42))
    ])
    demand_pipe.fit(X, df['demand'])
    joblib.dump(demand_pipe, 'models/demand_model.joblib')
    
    print("All models trained and saved to ml-services/models/")

if __name__ == "__main__":
    train_and_save_models()
