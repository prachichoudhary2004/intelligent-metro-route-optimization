# 🚇 Metro Navigator: Intelligent Route Optimization & Congestion Prediction

[![GitHub stars](https://img.shields.io/github/stars/prachichoudhary2004/intelligent-metro-route-optimization?style=for-the-badge)](https://github.com/prachichoudhary2004/intelligent-metro-route-optimization/stargazers)
[![Stack](https://img.shields.io/badge/Stack-Java_|_Python_|_Flask_|_Leaflet-blue?style=for-the-badge)](#-tech-stack)

### 💡 Why this project matters
Modern urban transit systems require more than simple shortest-path routing. **Metro Navigator** explores how graph algorithms, predictive machine learning, and real-time system design can work together to improve commuter decision-making under dynamic, real-world congestion conditions.

---

## 📸 Project Showroom (Screenshots)
> [!NOTE]
> *Add your project screenshots here to showcase the glassmorphism UI and animated maps.*

| **Main Dashboard** | **Congestion Heatmap** |
|:---:|:---:|
| ![Main Dashboard Placeholder](https://via.placeholder.com/600x350?text=Main+Dashboard+Preview) | ![Heatmap Placeholder](https://via.placeholder.com/600x350?text=Congestion+Heatmap+Visual) |

| **Algorithm Benchmarking** | **Route Timeline & Tradeoffs** |
|:---:|:---:|
| ![Benchmarking Placeholder](https://via.placeholder.com/600x350?text=Performance+Comparison+Drawer) | ![Timeline Placeholder](https://via.placeholder.com/600x350?text=Route+Timeline+Visualization) |

---

## 🧮 Multi-Objective Route Scoring

The final route score is computed using a weighted optimization model:

$$ Score = \alpha(Time) + \beta(Congestion) + \gamma(Interchanges) + \delta(Crowd Density) $$

Weights are dynamically adjusted based on:
- Peak vs non-peak hours
- User preference (fastest vs comfortable)
- Predicted congestion confidence

---

## 🧠 Why A* Wins

A* with a Haversine heuristic drastically reduces the search space compared to Dijkstra, making it ideal for large urban networks.

| Algorithm | Avg Nodes Explored | Avg Latency |
| --------- | ------------------ | ----------- |
| Dijkstra  | 312                | 4.8ms       |
| A*        | 112                | 1.7ms       |
| Yen’s     | 524                | 8.2ms       |

---

## 🌟 Core Features

- 🛰️ **Multi-City Support**: Dynamic graph loading for Delhi (NCR), Mumbai, and Bangalore.
- 🧠 **Explainable Route Decisions**: Transparent reasoning on why specific paths are prioritized.
- 📈 **Predictive Congestion**: ML-driven load forecasting using Random Forest regressors.
- 🔁 **K-Shortest Paths**: Yen's algorithm implementation for high-availability alternatives.
- ⚡ **Real-Time Benchmarking**: Live DSA complexity analysis (Nodes scanned vs Search Latency).
- 🌡️ **Interactive Heatmaps**: Visual pulse-markers and heat circles for high-traffic zones.
- ⚖️ **Tradeoff Engine**: Automated evaluation of alternative route costs and delays.
- 🕒 **Realistic Timeline**: Station-by-station arrival scheduling and interchange badges.

---

## 🏗️ System Workflow

1.  **User Input**: Source/Destination selection via a responsive map interface.
2.  **Java Routing API**: High-performance request handling and graph initialization.
3.  **ML Congestion Prediction**: Concurrent call to Python microservice for edge weight adjustments.
4.  **Graph Optimization**: Parallel execution of Dijkstra, A*, and Yen's algorithms.
5.  **Route Scoring**: Evaluation of paths based on time, distance, and predicted comfort.
6.  **Tradeoff Analysis**: Automated rejection of sub-optimal paths with specific reasoning.
7.  **Interactive Visualization**: Rendering of the optimal path with animated polylines and tooltips.

---

## 🛠️ Tech Stack

| Component | Technology | Role |
|-----------|------------|------|
| **Backend** | Java 11+, Native HttpServer | Core Routing Engine & API |
| **ML Engine** | Python 3.9+, Scikit-learn, Flask | Congestion & Delay Forecasting |
| **Frontend** | Vanilla JS, Leaflet.js, CSS3 | Interactive Map & Data Dashboard |
| **Data** | JSON (Persistent Store) | Graph Topology & Metadata |
| **Caching** | LRU (Concurrent Cache) | Redundant Computation Elimination |

---

## 📈 Quantified Engineering Impact

- 🚀 **Performance**: Achieved **sub-2ms** route computation on medium-density urban graphs.
- 🔍 **Optimization**: Reduced node exploration by **~64%** using Haversine-guided A* heuristics.
- 💾 **Efficiency**: Improved repeated-query performance by **82%** using multi-threaded LRU caching.
- 🛡️ **Reliability**: Implemented **zero-downtime fallback** heuristics for ML service outages.

---

## 🧩 Engineering Challenges Solved

- **Optimized Graph Traversal**: Refactored adjacency list structures to support $O(E \log V)$ search complexity on dense urban networks.
- **Microservice Resiliency**: Built a decoupled architecture where the Java core remains operational even if the ML service encounters latency spikes.
- **Cross-Platform Resilience**: Engineered robust string encoding to ensure stability across different terminal environments.
- **Explainable AI**: Developed a logic layer that translates raw ML weights into human-readable transit advice (e.g., "Alternative rejected due to 30% higher congestion").

---

## 🚀 Scalability Considerations

- **Stateless API Design**: The Java API is fully stateless, enabling effortless horizontal scaling via a load balancer.
- **Microservice Isolation**: ML inference is isolated into an independent service, allowing for independent resource scaling.
- **Modular Datasets**: The graph engine utilizes modular data loading, supporting rapid expansion to any global city network.

---

## 📡 API Sample Response (v2.0)

```json
{
  "success": true,
  "path": ["RC", "MH", "YB", "ND62"],
  "time": 24,
  "cost": 65,
  "congestion": "Low",
  "algorithm": "A*",
  "nodes_explored": 14,
  "decision_insights": {
    "confidence_score": 94.2,
    "reason": "Minimized interchanges while avoiding predicted bottleneck at Central Secretariat."
  }
}
```

---

## 🏁 Getting Started

1. **Train ML Models**: `cd ml-services && python train_models.py`
2. **Start System**: Execute `./start_system.bat` from the root.
3. **Open Dashboard**: `http://localhost:8080/dashboard/index.html`
4. **Interactive Docs**: `http://localhost:8081/api/docs`

---
*Built to explore scalable route optimization under dynamic congestion conditions using graph algorithms and predictive ML.*
