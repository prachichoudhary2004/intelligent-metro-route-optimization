const API_BASE = 'http://localhost:8080/api';

// Global state
let stationMap = {};
let currentCity = '';
let currentRoute = null;

// Initialize the application
document.addEventListener('DOMContentLoaded', () => {
    initializeApp();
});

async function initializeApp() {
    setupEventListeners();
    await loadPerformanceMetrics();
    updateSystemStatus();
    setInterval(updateSystemStatus, 5000);
}

function setupEventListeners() {
    // Tab switching
    document.querySelectorAll('.tab-button').forEach(button => {
        button.addEventListener('click', () => switchTab(button.textContent.toLowerCase().replace(' ', '-')));
    });

    // Route optimization tab
    document.getElementById('citySelect').addEventListener('change', loadCityStations);
    document.getElementById('computeRoute').addEventListener('click', computeRoute);
    document.getElementById('clearRoute').addEventListener('click', clearRoute);
    document.getElementById('timeSlider').addEventListener('input', updateTimeDisplay);

    // Algorithm comparison tab
    document.getElementById('runComparison').addEventListener('click', runAlgorithmComparison);
    
    // What-if simulation
    document.getElementById('runSimulation').addEventListener('click', runWhatIfSimulation);
}

// Tab Management
function switchTab(tabName) {
    // Hide all tabs
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });

    // Remove active class from all buttons
    document.querySelectorAll('.tab-button').forEach(button => {
        button.classList.remove('active');
    });

    // Show selected tab
    const selectedTab = document.getElementById(tabName);
    if (selectedTab) {
        selectedTab.classList.add('active');
    }

    // Activate corresponding button
    const activeButton = Array.from(document.querySelectorAll('.tab-button')).find(button => 
        button.textContent.toLowerCase().includes(tabName.replace('-', ' '))
    );
    if (activeButton) {
        activeButton.classList.add('active');
    }

    // Load tab-specific data
    loadTabData(tabName);
}

async function loadTabData(tabName) {
    switch(tabName) {
        case 'route-optimization':
            // Route optimization data is loaded when city is selected
            break;
        case 'algorithm-comparison':
            await loadAlgorithmComparison();
            break;
        case 'ml-predictions':
            await loadMLPredictions();
            break;
        case 'performance':
            await loadPerformanceMetrics();
            break;
    }
}

// Route Optimization Functions
async function loadCityStations() {
    const city = document.getElementById('citySelect').value;
    if (!city) {
        clearStationData();
        return;
    }

    showLoading(true);
    currentCity = city;
    
    try {
        // Clear all previous data to prevent caching issues
        clearStationData();
        
        // Fetch fresh city data from API
        const response = await fetch(`${API_BASE}/city/${city}`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Process fresh data
        await processCityData(data, city);
        
        showStatusMessage(`Loaded ${data.stations.length} stations for ${city.toUpperCase()}`, 'success');
    } catch (error) {
        console.error('Failed to load city data:', error);
        showStatusMessage(`Failed to load ${city} data: ${error.message}`, 'error');
        clearStationData();
    } finally {
        showLoading(false);
    }
}

function clearStationData() {
    // Clear all station-related data to prevent caching
    stationMap = {};
    
    // Clear dropdowns completely
    const sourceSelect = document.getElementById('sourceStation');
    const destSelect = document.getElementById('destStation');
    
    sourceSelect.innerHTML = '<option value="">Select Source</option>';
    destSelect.innerHTML = '<option value="">Select Destination</option>';
    
    // Clear route results
    clearRoute();
    
    // Clear network panel
    const stationList = document.getElementById('stationList');
    stationList.innerHTML = '<p class="text-muted">Select a city to view stations</p>';
    
    // Update network title
    const networkTitle = document.getElementById('networkTitle');
    networkTitle.textContent = 'Metro Network';
}

async function processCityData(data, city) {
    const sourceSelect = document.getElementById('sourceStation');
    const destSelect = document.getElementById('destStation');
    
    // Rebuild station map from fresh data
    stationMap = {};
    
    // Populate dropdowns with fresh options
    data.stations.forEach(station => {
        stationMap[station.id] = station.name;
        
        // Add to source dropdown
        const sourceOption = document.createElement('option');
        sourceOption.value = station.id;
        sourceOption.textContent = station.name;
        sourceSelect.appendChild(sourceOption);
        
        // Add to destination dropdown
        const destOption = document.createElement('option');
        destOption.value = station.id;
        destOption.textContent = station.name;
        destSelect.appendChild(destOption);
    });
    
    // Update network panel with fresh data
    updateNetworkPanel(data, city);
    
    // Update graph size in metrics
    updateGraphSize(data.stations.length, data.lines ? data.lines.length * 3 : 0);
}

function updateNetworkPanel(data, city) {
    const stationList = document.getElementById('stationList');
    const networkTitle = document.getElementById('networkTitle');
    
    // Update title with current city
    const cityNames = {
        'delhi': 'Delhi NCR',
        'mumbai': 'Mumbai', 
        'bangalore': 'Bangalore',
        'kolkata': 'Kolkata'
    };
    
    networkTitle.textContent = `Metro Network - ${cityNames[city] || city}`;
    
    // Clear and rebuild station list
    stationList.innerHTML = '';
    
    if (data.lines && data.lines.length > 0) {
        // Group by lines if line data is available
        data.lines.forEach(line => {
            const lineStations = data.stations.filter(s => 
                s.line === line.name || s.lines?.includes(line.name)
            );
            
            if (lineStations.length > 0) {
                const lineDiv = createStationLineGroup(line.name, lineStations, line.color);
                stationList.appendChild(lineDiv);
            }
        });
    } else {
        // Fallback: group alphabetically
        const groupedStations = groupStationsAlphabetically(data.stations);
        Object.keys(groupedStations).forEach(group => {
            const lineDiv = createStationLineGroup(group, groupedStations[group], '#64748b');
            stationList.appendChild(lineDiv);
        });
    }
}

function createStationLineGroup(lineName, stations, lineColor = '#64748b') {
    const lineDiv = document.createElement('div');
    lineDiv.className = 'station-line';
    lineDiv.style.marginBottom = '1rem';
    
    const titleDiv = document.createElement('div');
    titleDiv.className = 'station-line-title';
    titleDiv.style.cssText = `
        font-weight: 600;
        color: #334155;
        font-size: 0.85rem;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        margin-bottom: 0.5rem;
        padding-bottom: 0.25rem;
        border-bottom: 2px solid ${lineColor};
    `;
    titleDiv.textContent = lineName;
    lineDiv.appendChild(titleDiv);
    
    const tagsDiv = document.createElement('div');
    tagsDiv.className = 'station-tags';
    tagsDiv.style.cssText = `
        display: flex;
        flex-wrap: wrap;
        gap: 0.25rem;
    `;
    
    stations.forEach(station => {
        const tag = document.createElement('div');
        tag.className = 'station-tag';
        tag.style.cssText = `
            background: #ffffff;
            border: 1px solid #cbd5e1;
            border-radius: 4px;
            padding: 0.25rem 0.5rem;
            font-size: 0.75rem;
            color: #475569;
            cursor: pointer;
            transition: all 0.2s ease;
        `;
        tag.textContent = station.name;
        tag.dataset.stationId = station.id;
        
        // Add hover effects
        tag.addEventListener('mouseenter', () => {
            tag.style.background = '#2563eb';
            tag.style.color = 'white';
            tag.style.borderColor = '#2563eb';
            tag.style.transform = 'translateY(-1px)';
        });
        
        tag.addEventListener('mouseleave', () => {
            tag.style.background = '#ffffff';
            tag.style.color = '#475569';
            tag.style.borderColor = '#cbd5e1';
            tag.style.transform = 'translateY(0)';
        });
        
        // Add click handler to select station
        tag.addEventListener('click', () => selectStationFromTag(station.id, station.name));
        
        tagsDiv.appendChild(tag);
    });
    
    lineDiv.appendChild(tagsDiv);
    return lineDiv;
}

function groupStationsAlphabetically(stations) {
    const groups = {};
    stations.forEach(station => {
        const firstLetter = station.name.charAt(0).toUpperCase();
        if (!groups[firstLetter]) {
            groups[firstLetter] = [];
        }
        groups[firstLetter].push(station);
    });
    
    // Sort groups and stations within each group
    Object.keys(groups).sort().forEach(letter => {
        groups[letter].sort((a, b) => a.name.localeCompare(b.name));
    });
    
    return groups;
}

function selectStationFromTag(stationId, stationName) {
    // Auto-fill source or destination (prefer empty destination, then empty source)
    const destSelect = document.getElementById('destStation');
    const srcSelect = document.getElementById('sourceStation');
    
    if (!destSelect.value) {
        destSelect.value = stationId;
        showStatusMessage(`Selected ${stationName} as destination`, 'success');
    } else if (!srcSelect.value) {
        srcSelect.value = stationId;
        showStatusMessage(`Selected ${stationName} as source`, 'success');
    } else {
        // Both are filled, replace destination
        destSelect.value = stationId;
        showStatusMessage(`Updated destination to ${stationName}`, 'success');
    }
}

function updateGraphSize(nodes, edges) {
    const graphSizeElement = document.getElementById('graphSize');
    const perfGraphSizeElement = document.getElementById('perfGraphSize');
    
    const sizeText = `${nodes} nodes${edges ? ` / ${edges} edges` : ''}`;
    
    if (graphSizeElement) graphSizeElement.textContent = sizeText;
    if (perfGraphSizeElement) perfGraphSizeElement.textContent = sizeText;
}

async function computeRoute() {
    const source = document.getElementById('sourceStation').value;
    const destination = document.getElementById('destStation').value;
    const algorithm = document.getElementById('algorithm').value;
    const routingMode = document.getElementById('routingMode').value;
    const time = document.getElementById('timeSlider').value;

    if (!source || !destination) {
        showStatusMessage('Please select source and destination stations', 'warning');
        return;
    }

    if (source === destination) {
        showStatusMessage('Source and destination cannot be the same', 'warning');
        return;
    }

    showLoading(true);
    const computeButton = document.getElementById('computeRoute');
    computeButton.disabled = true;
    computeButton.textContent = 'Computing...';

    try {
        const response = await fetch(`${API_BASE}/route`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                city: currentCity,
                source: source,
                destination: destination,
                algorithm: algorithm,
                mode: routingMode,
                time: parseInt(time)
            })
        });

        const data = await response.json();
        
        if (data.success) {
            displayRouteResults(data);
            updateSystemMetrics(data);
        } else {
            showStatusMessage(data.error || 'Failed to compute route', 'error');
        }
    } catch (error) {
        console.error('Route computation failed:', error);
        showStatusMessage('Failed to compute route', 'error');
    } finally {
        showLoading(false);
        computeButton.disabled = false;
        computeButton.textContent = 'Find Route';
    }
}

function displayRouteResults(data) {
    currentRoute = data;
    
    // Show route results
    document.getElementById('routeStats').style.display = 'grid';
    document.getElementById('routeSteps').style.display = 'block';
    
    // Update route statistics
    document.getElementById('resultTime').textContent = data.time;
    document.getElementById('resultDistance').textContent = data.distance.toFixed(1);
    document.getElementById('resultInterchanges').textContent = data.interchanges;
    document.getElementById('resultCost').textContent = data.cost;
    
    // Display ML comparison if available
    if (data.ml_comparison) {
        displayMLComparison(data.ml_comparison);
    }
    
    // Display step-by-step route
    displayDetailedRouteSteps(data.path, currentCity);
    
    // Show algorithm selection reasoning
    displayAlgorithmSelection(data.algorithm, data.algorithm_reason);
    
    // Display ML impacts with station-level details
    displayMLImpacts(data.ml_impact, data.decision_insights.ml_impacts);
    
    // Display route analysis if available
    if (data.route_analysis) {
        displayRouteAnalysis(data.route_analysis);
    }
    
    // Show cache status
    const cacheStatus = data.cache_hit ? 'Cache hit - saved computation' : 'Cache miss - computed fresh';
    showStatusMessage(`Route computed successfully - ${cacheStatus}`, 'success');
    
    // Update system metrics
    updateSystemMetrics(data);
    
    // Display explanation
    displayRouteExplanation(data.decision_insights);
}

function displayMLComparison(mlComparison) {
    // Create ML comparison card if it doesn't exist
    let mlCompareCard = document.getElementById('mlComparisonCard');
    if (!mlCompareCard) {
        const resultsPanel = document.querySelector('.results-panel');
        mlCompareCard = document.createElement('div');
        mlCompareCard.className = 'modern-card';
        mlCompareCard.id = 'mlComparisonCard';
        mlCompareCard.innerHTML = `
            <h3 class="card-title">ML Impact Analysis</h3>
            <div id="mlComparisonContent"></div>
        `;
        resultsPanel.insertBefore(mlCompareCard, resultsPanel.firstChild);
    }
    
    mlCompareCard.style.display = 'block';
    
    const content = document.getElementById('mlComparisonContent');
    const improvement = mlComparison.improvement;
    
    let html = `
        <div class="row mb-3">
            <div class="col-6">
                <div class="text-center p-3" style="background: #F1F5F9; border-radius: 8px;">
                    <div class="text-muted mb-1">Without ML</div>
                    <div class="h5 mb-0">${mlComparison.without_ml.time} min</div>
                    <div class="small text-muted">₹${mlComparison.without_ml.cost}</div>
                </div>
            </div>
            <div class="col-6">
                <div class="text-center p-3" style="background: #ECFDF5; border-radius: 8px; border: 2px solid #10B981;">
                    <div class="text-muted mb-1">With ML</div>
                    <div class="h5 mb-0">${mlComparison.with_ml.time} min</div>
                    <div class="small text-muted">₹${mlComparison.with_ml.cost}</div>
                </div>
            </div>
        </div>
        <div class="text-center">
            <div class="badge bg-success" style="font-size: 0.9rem;">Time saved: ${improvement.time_saved_minutes} min (${improvement.time_saved_percent}%)</div>
        </div>
    `;
    
    content.innerHTML = html;
}

function displayDetailedRouteSteps(path, city) {
    const stepsContent = document.getElementById('routeStepsContent');
    if (!stepsContent) {
        console.error('routeStepsContent element not found');
        return;
    }
    
    stepsContent.innerHTML = '';
    
    if (!path || path.length === 0) {
        stepsContent.innerHTML = '<p class="text-muted">No route details available</p>';
        return;
    }
    
    path.forEach((stationId, index) => {
        const stationName = stationMap[stationId] || stationId;
        const stepDiv = document.createElement('div');
        stepDiv.className = 'route-step-detailed';
        
        let stepNumber = index + 1;
        let lineName = 'Blue Line';
        let actionText = 'Travel';
        
        if (index === 0) {
            actionText = 'Board';
            lineName = 'Blue Line';
        } else if (index === path.length - 1) {
            actionText = 'Destination reached';
            lineName = 'Blue Line';
        } else if (index % 3 === 0) {
            actionText = 'Interchange';
            lineName = 'Yellow Line';
        }
        
        let html = `
            <div class="step-header">
                <span class="step-number">${stepNumber}</span>
                <span class="step-station-name">${stationName}</span>
            </div>
            <div class="step-details">
                <span class="step-action">${actionText}</span>
                <span class="step-line-badge">${lineName}</span>
            </div>
        `;
        
        if (index < path.length - 1) {
            const stops = index % 2 + 1;
            html += `<div class="step-travel-info">Travel ${stops} stops →</div>`;
        }
        
        stepDiv.innerHTML = html;
        stepsContent.appendChild(stepDiv);
    });
}

function displayAlgorithmSelection(algorithm, reason) {
    console.log('displayAlgorithmSelection called with:', algorithm, reason);
    const algorithmCard = document.getElementById('algorithmSelectionCard');
    if (!algorithmCard) {
        console.log('Creating new algorithmSelectionCard');
        // Create algorithm selection card if it doesn't exist
        const metricsPanel = document.querySelector('.metrics-panel');
        if (!metricsPanel) {
            console.error('metrics-panel not found');
            return;
        }
        const newCard = document.createElement('div');
        newCard.className = 'modern-card';
        newCard.id = 'algorithmSelectionCard';
        newCard.innerHTML = `
            <h3 class="card-title">Algorithm Selection</h3>
            <div id="algorithmSelectionContent"></div>
        `;
        metricsPanel.insertBefore(newCard, metricsPanel.firstChild);
    }
    
    const card = document.getElementById('algorithmSelectionCard');
    card.style.display = 'block';
    console.log('algorithmSelectionCard display set to block');
    
    const content = document.getElementById('algorithmSelectionContent');
    if (!content) {
        console.error('algorithmSelectionContent not found');
        return;
    }
    content.innerHTML = `
        <div class="mb-2">
            <strong>Algorithm:</strong> <span class="badge bg-primary">${algorithm.toUpperCase()}</span>
        </div>
        <div class="text-muted">
            <strong>Reason:</strong> ${reason}
        </div>
    `;
}

function displayRouteAnalysis(routeAnalysis) {
    console.log('displayRouteAnalysis called with:', routeAnalysis);
    const analysisCard = document.getElementById('routeAnalysisCard');
    if (!analysisCard) {
        console.log('Creating new routeAnalysisCard');
        // Create route analysis card if it doesn't exist
        const resultsPanel = document.querySelector('.results-panel');
        if (!resultsPanel) {
            console.error('results-panel not found');
            return;
        }
        const newCard = document.createElement('div');
        newCard.className = 'modern-card';
        newCard.id = 'routeAnalysisCard';
        newCard.innerHTML = `
            <h3 class="card-title">Why This Route?</h3>
            <div id="routeAnalysisContent"></div>
        `;
        resultsPanel.insertBefore(newCard, resultsPanel.firstChild);
    }
    
    const card = document.getElementById('routeAnalysisCard');
    card.style.display = 'block';
    console.log('routeAnalysisCard display set to block');
    
    const content = document.getElementById('routeAnalysisContent');
    if (!content) {
        console.error('routeAnalysisContent not found');
        return;
    }
    
    let html = '<div class="route-analysis">';
    
    // Why this route
    html += '<h6 class="mt-2 mb-2">Why this route?</h6>';
    html += '<ul class="list-unstyled">';
    routeAnalysis.why_this_route.forEach(reason => {
        html += `<li class="text-success mb-2">✓ ${reason}</li>`;
    });
    html += '</ul>';
    
    // Trade-offs
    html += '<h6 class="mt-3 mb-2">Trade-offs:</h6>';
    html += '<ul class="list-unstyled">';
    routeAnalysis.trade_offs.forEach(tradeoff => {
        html += `<li class="text-info mb-2">→ ${tradeoff}</li>`;
    });
    html += '</ul>';
    
    // Avoided stations
    if (routeAnalysis.avoided_stations.length > 0) {
        html += '<h6 class="mt-3 mb-2">High-Risk Stations Avoided:</h6>';
        html += '<div class="avoided-stations">';
        routeAnalysis.avoided_stations.forEach(station => {
            html += `<span class="badge bg-warning text-dark me-1 mb-1">⚠️ ${station}</span>`;
        });
        html += '</div>';
    }
    
    html += '</div>';
    content.innerHTML = html;
}

function displayMLImpacts(mlImpact, mlImpacts) {
    const mlImpactCard = document.getElementById('mlImpactCard');
    const mlImpactDiv = document.getElementById('mlImpact');
    
    if (!mlImpact.congestion_adjusted && !mlImpact.delay_mitigated) {
        mlImpactCard.style.display = 'none';
        return;
    }
    
    mlImpactCard.style.display = 'block';
    
    let html = '<h6>ML Decisions:</h6>';
    
    // Show ML decision chain if available
    if (mlImpact.ml_decisions && mlImpact.ml_decisions.length > 0) {
        html += '<div class="mb-2">';
        mlImpact.ml_decisions.forEach(decision => {
            html += `
                <div class="ml-decision-chain">
                    <div class="decision-feature">Feature: ${decision.feature}</div>
                    <div class="decision-value">Value: ${decision.value}</div>
                    <div class="decision-action">→ Decision: ${decision.decision}</div>
                    <div class="decision-outcome">Outcome: ${decision.outcome}</div>
                </div>
            `;
        });
        html += '</div>';
    }
    
    // Show station-level impacts if available
    if (mlImpact.station_impacts && mlImpact.station_impacts.length > 0) {
        html += '<div class="mb-2">';
        mlImpact.station_impacts.forEach(impact => {
            html += `
                <div class="ml-station-impact">
                    <div class="station-name">🚦 ${impact.station}</div>
                    <div class="impact-detail">Weight increased by ${(impact.weight_increase * 100).toFixed(0)}% due to high congestion</div>
                </div>
            `;
        });
        html += '</div>';
    }
    
    html += '<ul class="list-unstyled">';
    
    if (mlImpact.congestion_adjusted) {
        html += '<li class="text-info mb-2">🚦 Route adjusted to avoid high congestion</li>';
    }
    
    if (mlImpact.delay_mitigated) {
        html += '<li class="text-info mb-2">⏱️ Delay risk reduced through ML predictions</li>';
    }
    
    if (mlImpacts && mlImpacts.length > 0) {
        mlImpacts.forEach(impact => {
            html += `<li class="text-success mb-2">✅ ${impact}</li>`;
        });
    }
    
    html += `<li class="text-muted mt-2">Confidence: ${(mlImpact.confidence * 100).toFixed(0)}%</li>`;
    
    // Add confidence breakdown if available
    if (mlImpact.confidence_breakdown) {
        html += '<div class="confidence-breakdown mt-3">';
        html += '<h6 class="mb-2">Based on:</h6>';
        html += '<ul class="list-unstyled small">';
        
        const breakdown = mlImpact.confidence_breakdown;
        html += `<li class="mb-1">• Historical data similarity: ${(breakdown.historical_data_similarity * 100).toFixed(0)}%</li>`;
        html += `<li class="mb-1">• Time-of-day pattern: ${(breakdown.time_of_day_pattern * 100).toFixed(0)}%</li>`;
        html += `<li class="mb-1">• Station congestion variance: ${(breakdown.station_congestion_variance * 100).toFixed(0)}%</li>`;
        
        html += '</ul></div>';
    }
    
    html += '</ul>';
    
    mlImpactDiv.innerHTML = html;
    
    // Show what-if simulation card when route is computed
    const whatIfCard = document.getElementById('whatIfCard');
    if (whatIfCard && currentRoute) {
        whatIfCard.style.display = 'block';
    }
}

function calculateRouteCost(distance, interchanges) {
    // Simple cost calculation based on distance slabs
    const baseFare = 10; // Base fare
    const distanceSlab = 2; // ₹2 per km after first 2km
    const interchangePenalty = 5; // ₹5 per interchange
    
    let cost = baseFare;
    if (distance > 2) {
        cost += (distance - 2) * distanceSlab;
    }
    cost += interchanges * interchangePenalty;
    
    return Math.round(cost);
}

function displayStepByStepRoute(path) {
    const stepsContent = document.getElementById('routeStepsContent');
    stepsContent.innerHTML = '';
    
    const pathNames = path.map(stationId => stationMap[stationId] || stationId);
    
    pathNames.forEach((stationName, index) => {
        const stepDiv = document.createElement('div');
        stepDiv.className = 'route-step';
        
        let stationClass = 'step-station';
        let lineClass = 'step-line';
        let lineName = 'Unknown';
        
        // Determine line information (simplified)
        if (index === 0) {
            stationClass += ' font-weight-bold';
            lineName = 'Board - Blue Line';
        } else if (index === pathNames.length - 1) {
            stationClass += ' font-weight-bold';
            lineName = 'Destination';
        } else if (index % 3 === 0) {
            // Simulate interchange every 3 stations
            lineClass += ' step-interchange';
            lineName = 'Interchange → Yellow Line';
        } else {
            lineName = 'Blue Line';
        }
        
        let html = `
            <div class="${stationClass}">${stationName}</div>
            <div class="${lineClass}">${lineName}</div>
        `;
        
        if (index < pathNames.length - 1) {
            html += '<div class="step-arrow">↓</div>';
        }
        
        stepDiv.innerHTML = html;
        stepsContent.appendChild(stepDiv);
    });
}

function displayRouteExplanation(insights) {
    const explanationCard = document.getElementById('explanationCard');
    const explanationDiv = document.getElementById('routeExplanation');
    
    if (!insights) {
        explanationCard.style.display = 'none';
        return;
    }
    
    explanationCard.style.display = 'block';
    
    let html = `
        <div class="mb-3">
            <h6>Algorithm: ${insights.algorithm.toUpperCase()}</h6>
            <p class="text-muted mb-2">${insights.reason}</p>
        </div>
        
        <div class="mb-3">
            <h6>Trade-offs:</h6>
            <ul class="list-unstyled">
    `;
    
    insights.trade_offs.forEach(tradeoff => {
        const className = tradeoff.startsWith('+') ? 'text-success' : 
                          tradeoff.startsWith('-') ? 'text-danger' : 'text-warning';
        html += `<li class="${className} mb-1">${tradeoff}</li>`;
    });
    
    html += `</ul></div>`;
    
    if (insights.confidence_score) {
        html += `
            <div class="text-center">
                <span class="badge bg-primary">Confidence: ${insights.confidence_score}%</span>
            </div>
        `;
    }
    
    explanationDiv.innerHTML = html;
}

function displayMLImpact(insights) {
    const mlImpactCard = document.getElementById('mlImpactCard');
    const mlImpactDiv = document.getElementById('mlImpact');
    
    // Check if ML influenced the route decision
    const mlInfluences = insights?.trade_offs?.filter(tradeoff => 
        tradeoff.toLowerCase().includes('congestion') || 
        tradeoff.toLowerCase().includes('ml') ||
        tradeoff.toLowerCase().includes('prediction')
    );
    
    if (!mlInfluences || mlInfluences.length === 0) {
        mlImpactCard.style.display = 'none';
        return;
    }
    
    mlImpactCard.style.display = 'block';
    
    let html = '<h6>ML Route Adjustments:</h6><ul class="list-unstyled">';
    
    mlInfluences.forEach(influence => {
        html += `<li class="text-info mb-2">${influence}</li>`;
    });
    
    html += '</ul>';
    
    mlImpactDiv.innerHTML = html;
}

function showStatusMessage(message, type = 'success') {
    const statusDiv = document.getElementById('statusMessages');
    const messageDiv = document.createElement('div');
    messageDiv.className = `status-message status-${type}`;
    messageDiv.textContent = message;
    
    // Clear previous messages
    statusDiv.innerHTML = '';
    statusDiv.appendChild(messageDiv);
    
    // Auto-remove after 5 seconds
    setTimeout(() => {
        messageDiv.remove();
    }, 5000);
}

function updateSystemMetrics(data) {
    document.getElementById('execTime').textContent = `${data.execution_time.toFixed(2)} ms`;
    document.getElementById('nodesExplored').textContent = data.nodes_explored;
    document.getElementById('algorithmUsed').textContent = data.algorithm.toUpperCase();
    
    // Update last execution time
    const lastExecElement = document.getElementById('lastExecutionTime');
    if (lastExecElement) {
        lastExecElement.textContent = `${data.execution_time.toFixed(2)} ms`;
    }
    
    // Update ML calls counter
    const mlCallsElement = document.getElementById('mlCallsMade');
    if (mlCallsElement) {
        const currentCalls = parseInt(mlCallsElement.textContent) || 0;
        mlCallsElement.textContent = currentCalls + 1;
    }
    
    // Update cache hit rate from health endpoint
    updateCacheHitRate();
}

async function updateCacheHitRate() {
    try {
        const response = await fetch(`${API_BASE}/health`);
        const data = await response.json();
        const hitRate = (data.metrics.cache_hit_rate * 100).toFixed(1);
        document.getElementById('cacheHitRate').textContent = `${hitRate}%`;
        
        // Update performance panel
        const perfCacheElement = document.getElementById('perfCacheHitRate');
        if (perfCacheElement) {
            perfCacheElement.textContent = `${hitRate}%`;
        }
        
        // Update cache efficiency
        const cacheEffElement = document.getElementById('cacheEfficiency');
        if (cacheEffElement) {
            const efficiency = hitRate > 70 ? 'High' : hitRate > 40 ? 'Medium' : 'Low';
            cacheEffElement.textContent = efficiency;
        }
        
        // Update total requests
        const totalReqElement = document.getElementById('totalRequests');
        if (totalReqElement) {
            totalReqElement.textContent = data.metrics.cache_hits + data.metrics.cache_misses;
        }
        
        // Calculate and display cache performance comparison
        const avgTimeNoCache = 450; // Mock: 450ms without cache
        const avgTimeWithCache = 180; // Mock: 180ms with cache
        const timeSaved = avgTimeNoCache - avgTimeWithCache;
        
        const cachePerfElement = document.getElementById('cachePerformance');
        if (cachePerfElement) {
            const improvementPercent = ((timeSaved/avgTimeNoCache)*100).toFixed(0);
            cachePerfElement.innerHTML = `<span class="badge bg-success" style="font-size: 0.85rem;">Time saved: ${timeSaved}ms (${improvementPercent}%)</span>`;
        }
        
        const avgResponseElement = document.getElementById('avgResponseTime');
        if (avgResponseElement) {
            avgResponseElement.textContent = `${avgTimeWithCache} ms (with cache)`;
        }
        
        const avgNoCacheElement = document.getElementById('avgTimeNoCache');
        if (avgNoCacheElement) {
            avgNoCacheElement.textContent = `${avgTimeNoCache} ms`;
        }
    } catch (error) {
        console.error('Failed to update cache hit rate:', error);
    }
}

function clearRoute() {
    document.getElementById('routeStats').style.display = 'none';
    document.getElementById('routeSteps').style.display = 'none';
    document.getElementById('explanationCard').style.display = 'none';
    document.getElementById('mlImpactCard').style.display = 'none';
    
    // Clear new cards
    const routeAnalysisCard = document.getElementById('routeAnalysisCard');
    if (routeAnalysisCard) routeAnalysisCard.style.display = 'none';
    
    const algorithmSelectionCard = document.getElementById('algorithmSelectionCard');
    if (algorithmSelectionCard) algorithmSelectionCard.style.display = 'none';
    
    const mlCompareCard = document.getElementById('mlComparisonCard');
    if (mlCompareCard) mlCompareCard.style.display = 'none';
    
    const whatIfCard = document.getElementById('whatIfCard');
    if (whatIfCard) whatIfCard.style.display = 'none';
    
    currentRoute = null;
    
    // Clear status messages
    document.getElementById('statusMessages').innerHTML = '';
    
    // Reset metrics
    document.getElementById('execTime').textContent = '-';
    document.getElementById('nodesExplored').textContent = '-';
    document.getElementById('algorithmUsed').textContent = '-';
    document.getElementById('cacheHitRate').textContent = '-';
}

function updateTimeDisplay() {
    const hour = document.getElementById('timeSlider').value;
    document.getElementById('timeDisplay').textContent = `${hour.padStart(2, '0')}:00`;
}

// Algorithm Comparison Functions
async function loadAlgorithmComparison() {
    // This will be populated when user clicks "Run Comparison"
}

async function runWhatIfSimulation() {
    if (!currentRoute) {
        showStatusMessage('Please compute a route first', 'warning');
        return;
    }
    
    const scenario = document.getElementById('congestionScenario').value;
    const resultsDiv = document.getElementById('simulationResults');
    
    // Simulate different congestion scenarios
    let timeAdjustment = 0;
    let costAdjustment = 0;
    let scenarioDescription = '';
    
    switch(scenario) {
        case 'low':
            timeAdjustment = -0.15; // 15% faster
            costAdjustment = -0.05; // 5% cheaper
            scenarioDescription = 'Low Congestion (-30%)';
            break;
        case 'high':
            timeAdjustment = 0.25; // 25% slower
            costAdjustment = 0.10; // 10% more expensive
            scenarioDescription = 'High Congestion (+30%)';
            break;
        case 'severe':
            timeAdjustment = 0.40; // 40% slower
            costAdjustment = 0.20; // 20% more expensive
            scenarioDescription = 'Severe Congestion (+50%)';
            break;
        default:
            timeAdjustment = 0;
            costAdjustment = 0;
            scenarioDescription = 'Current Conditions';
    }
    
    const simulatedTime = Math.round(currentRoute.time * (1 + timeAdjustment));
    const simulatedCost = (currentRoute.cost * (1 + costAdjustment)).toFixed(2);
    const timeDiff = simulatedTime - currentRoute.time;
    const costDiff = (simulatedCost - currentRoute.cost).toFixed(2);
    
    let html = `
        <div class="simulation-result">
            <div class="mb-3">
                <strong>Scenario:</strong> ${scenarioDescription}
            </div>
            <div class="row mb-2">
                <div class="col-6">
                    <div class="text-muted small">Current Time</div>
                    <div class="h5 mb-0">${currentRoute.time} min</div>
                </div>
                <div class="col-6">
                    <div class="text-muted small">Simulated Time</div>
                    <div class="h5 mb-0 ${timeDiff > 0 ? 'text-danger' : 'text-success'}">${simulatedTime} min</div>
                </div>
            </div>
            <div class="row">
                <div class="col-6">
                    <div class="text-muted small">Current Cost</div>
                    <div class="h5 mb-0">₹${currentRoute.cost}</div>
                </div>
                <div class="col-6">
                    <div class="text-muted small">Simulated Cost</div>
                    <div class="h5 mb-0 ${costDiff > 0 ? 'text-danger' : 'text-success'}">₹${simulatedCost}</div>
                </div>
            </div>
            <div class="mt-3 pt-3 border-top">
                <div class="text-center">
                    ${timeDiff > 0 ? `⚠️ Time increase: +${timeDiff} min` : `✅ Time decrease: ${timeDiff} min`}
                    <br>
                    ${costDiff > 0 ? `⚠️ Cost increase: +₹${costDiff}` : `✅ Cost decrease: ₹${costDiff}`}
                </div>
            </div>
        </div>
    `;
    
    resultsDiv.innerHTML = html;
}

async function runAlgorithmComparison() {
    const source = document.getElementById('sourceStation').value;
    const destination = document.getElementById('destStation').value;
    
    if (!source || !destination) {
        showStatusMessage('Please select source and destination stations first', 'warning');
        return;
    }

    showLoading(true);

    try {
        const response = await fetch(`${API_BASE}/compare`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                city: currentCity,
                source: source,
                destination: destination
            })
        });

        const data = await response.json();
        
        // Update comparison table with enhanced data
        updateComparisonTable(data);
        
        // Update recommendation
        updateAlgorithmRecommendation(data.recommendation);
        
        showStatusMessage('Algorithm comparison completed', 'success');
    } catch (error) {
        console.error('Algorithm comparison failed:', error);
        showStatusMessage('Failed to run algorithm comparison', 'error');
    } finally {
        showLoading(false);
    }
}

function updateComparisonTable(data) {
    // Update Dijkstra row
    document.getElementById('dijkstraTime').textContent = `${data.dijkstra.execution_time.toFixed(2)} ms`;
    document.getElementById('dijkstraNodes').textContent = data.dijkstra.nodes_explored;
    document.getElementById('dijkstraCost').textContent = data.dijkstra.path_cost;
    document.getElementById('dijkstraMemory').textContent = `${data.dijkstra.memory_usage.toFixed(2)} MB`;
    
    // Update A* row
    document.getElementById('astarTime').textContent = `${data.astar.execution_time.toFixed(2)} ms`;
    document.getElementById('astarNodes').textContent = data.astar.nodes_explored;
    document.getElementById('astarCost').textContent = data.astar.path_cost;
    document.getElementById('astarMemory').textContent = `${data.astar.memory_usage.toFixed(2)} MB`;
    
    // Update Multi-Objective row
    document.getElementById('multiTime').textContent = `${data.multi_objective.execution_time.toFixed(2)} ms`;
    document.getElementById('multiNodes').textContent = data.multi_objective.nodes_explored;
    document.getElementById('multiCost').textContent = data.multi_objective.path_cost;
    document.getElementById('multiMemory').textContent = `${data.multi_objective.memory_usage.toFixed(2)} MB`;
}

function updateAlgorithmRecommendation(recommendation) {
    const recommendationDiv = document.getElementById('algorithmRecommendation');
    
    let html = `
        <h6>Recommended: ${recommendation.algorithm.toUpperCase()}</h6>
        <p class="mb-2"><strong>Reasoning:</strong> ${recommendation.reasoning}</p>
        <p class="mb-0"><strong>Confidence:</strong> ${recommendation.confidence}%</p>
    `;
    
    recommendationDiv.innerHTML = html;
    recommendationDiv.className = 'status-message status-success';
}

// ML Predictions Functions
async function loadMLPredictions() {
    showLoading(true);

    try {
        const response = await fetch(`${API_BASE}/ml/predictions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                city: currentCity || 'delhi',
                hour: new Date().getHours()
            })
        });

        const data = await response.json();
        
        displayOverallMetrics(data.route_predictions);
        displayStationPredictions(data.station_predictions);
        displayMLFeatures(data.ml_features);
        
        showStatusMessage('ML predictions loaded', 'success');
    } catch (error) {
        console.error('Failed to load ML predictions:', error);
        showStatusMessage('Failed to load ML predictions', 'error');
    } finally {
        showLoading(false);
    }
}

function displayOverallMetrics(predictions) {
    document.getElementById('overallCongestion').textContent = predictions.overall_congestion;
    document.getElementById('overallDelayRisk').textContent = predictions.peak_hour_risk;
    document.getElementById('overallDemand').textContent = predictions.event_impact;
    document.getElementById('predictionConfidence').textContent = '0.85'; // Mock confidence
}

function displayStationPredictions(predictions) {
    const container = document.getElementById('stationPredictions');
    
    let html = '';
    predictions.forEach(pred => {
        const congestionColor = pred.congestion_score < 0.3 ? 'text-success' : 
                              pred.congestion_score < 0.7 ? 'text-warning' : 'text-danger';
        const delayColor = pred.delay_risk < 0.3 ? 'text-success' : 
                          pred.delay_risk < 0.6 ? 'text-warning' : 'text-danger';
        
        html += `
            <div class="prediction-card">
                <div class="prediction-title">${pred.station_name}</div>
                <div class="prediction-metrics">
                    <div class="prediction-metric">
                        <div class="prediction-value">${pred.congestion_score}</div>
                        <div class="prediction-label">Congestion</div>
                    </div>
                    <div class="prediction-metric">
                        <div class="prediction-value">${pred.delay_risk}</div>
                        <div class="prediction-label">Delay Risk</div>
                    </div>
                    <div class="prediction-metric">
                        <div class="prediction-value">${pred.demand_score}</div>
                        <div class="prediction-label">Demand</div>
                    </div>
                    <div class="prediction-metric">
                        <div class="prediction-value">${pred.confidence_score}</div>
                        <div class="prediction-label">Confidence</div>
                    </div>
                </div>
            </div>
        `;
    });
    
    container.innerHTML = html;
}

function displayMLFeatures(features) {
    const container = document.getElementById('mlFeatures');
    
    const html = `
        <div class="mb-3">
            <h6>Model Information</h6>
            <ul class="metrics-list">
                <li class="metric-item">
                    <span class="metric-label">Training Period</span>
                    <span class="metric-value">${features.training_period}</span>
                </li>
                <li class="metric-item">
                    <span class="metric-label">Last Retrained</span>
                    <span class="metric-value">${features.last_retrained}</span>
                </li>
                <li class="metric-item">
                    <span class="metric-label">Model Version</span>
                    <span class="metric-value">${features.model_version}</span>
                </li>
            </ul>
        </div>
        <div>
            <h6>Model Performance</h6>
            <ul class="metrics-list">
                <li class="metric-item">
                    <span class="metric-label">Congestion Model</span>
                    <span class="metric-value">${features.congestion_model.type} (${(features.congestion_model.accuracy * 100).toFixed(1)}%)</span>
                </li>
                <li class="metric-item">
                    <span class="metric-label">Delay Model</span>
                    <span class="metric-value">${features.delay_model.type} (${(features.delay_model.accuracy * 100).toFixed(1)}%)</span>
                </li>
                <li class="metric-item">
                    <span class="metric-label">Demand Model</span>
                    <span class="metric-value">${features.demand_model.type} (${(features.demand_model.accuracy * 100).toFixed(1)}%)</span>
                </li>
            </ul>
        </div>
    `;
    
    container.innerHTML = html;
}

// Performance Functions
async function updateSystemMetrics(data) {
    try {
        const response = await fetch(`${API_BASE}/health`);
        const healthData = await response.json();
        
        // Update metrics
        const cacheHits = healthData.metrics.cache_hits || 0;
        const cacheMisses = healthData.metrics.cache_misses || 0;
        const totalRequests = cacheHits + cacheMisses;
        const cacheHitRate = totalRequests > 0 ? (cacheHits / totalRequests) : 0;
        
        // Calculate ML calls saved due to cache
        const mlCallsSaved = Math.round(cacheHits * 0.6); // Assume 60% of cache hits would have triggered ML calls
        const cacheReductionPercent = 60; // Mock: 60% reduction in response time
        
        // Update system design panel metrics
        const requestsHandledElement = document.getElementById('requestsHandled');
        if (requestsHandledElement) {
            requestsHandledElement.textContent = totalRequests;
        }
        
        const systemCacheHitRatioElement = document.getElementById('systemCacheHitRatio');
        if (systemCacheHitRatioElement) {
            systemCacheHitRatioElement.textContent = `${(cacheHitRate * 100).toFixed(0)}%`;
        }
        
        const mlCallsSavedElement = document.getElementById('mlCallsSaved');
        if (mlCallsSavedElement) {
            mlCallsSavedElement.textContent = mlCallsSaved;
        }
        
        // Add cache reduction impact
        const cacheReductionElement = document.getElementById('cacheReductionImpact');
        if (!cacheReductionElement) {
            const systemStatusDiv = document.querySelector('#systemDesignPanel .metrics-list');
            if (systemStatusDiv) {
                const newMetric = document.createElement('li');
                newMetric.className = 'metric-item';
                newMetric.innerHTML = `
                    <span class="metric-label">Cache Reduced Response Time</span>
                    <span class="metric-value" id="cacheReductionImpact" style="color: var(--success);">${cacheReductionPercent}%</span>
                `;
                systemStatusDiv.appendChild(newMetric);
            }
        }
        
        // Update ML calls made
        const mlCallsMadeElement = document.getElementById('mlCallsMade');
        if (mlCallsMadeElement) {
            mlCallsMadeElement.textContent = totalRequests - mlCallsSaved;
        }
        
        // Update last updated timestamp
        const lastUpdatedElement = document.getElementById('lastUpdated');
        if (lastUpdatedElement) {
            const now = new Date();
            lastUpdatedElement.textContent = now.toLocaleTimeString();
        }
        
        // Update execution time if route data available
        if (data && data.execution_time) {
            const execTimeElement = document.getElementById('lastExecutionTime');
            if (execTimeElement) {
                execTimeElement.textContent = `${data.execution_time.toFixed(0)} ms`;
            }
        }
    } catch (error) {
        console.error('Failed to update system metrics:', error);
    }
}

async function loadPerformanceMetrics() {
    try {
        const response = await fetch(`${API_BASE}/health`);
        const data = await response.json();
        
        // Update performance metrics
        const hitRate = (data.metrics.cache_hit_rate * 100).toFixed(1);
        document.getElementById('perfCacheHitRate').textContent = `${hitRate}%`;
        document.getElementById('totalRequests').textContent = data.metrics.cache_hits + data.metrics.cache_misses;
        document.getElementById('avgResponseTime').textContent = '245 ms'; // Mock data
        document.getElementById('memoryUsage').textContent = '128 MB'; // Mock data
        document.getElementById('perfGraphSize').textContent = `${data.metrics.active_nodes} nodes / ${data.metrics.active_edges} edges`;
        
        // Update last updated time
        document.getElementById('lastUpdated').textContent = new Date().toLocaleTimeString();
        
    } catch (error) {
        console.error('Failed to load performance metrics:', error);
    }
}

async function updateSystemStatus() {
    // System status is already displayed in the UI
    // All services are running in our mock setup
}

// Utility Functions
function showLoading(show) {
    const overlay = document.getElementById('loadingOverlay');
    overlay.style.display = show ? 'flex' : 'none';
}
