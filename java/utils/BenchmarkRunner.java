package utils;

import algorithms.AStar;
import algorithms.Dijkstra;
import algorithms.YensAlgorithm;
import core.DynamicGraph;
import java.util.*;

/**
 * Utility to benchmark different routing algorithms and generate performance reports.
 */
public class BenchmarkRunner {
    
    private final DynamicGraph graph;
    private final Dijkstra dijkstra;
    private final AStar astar;
    private final YensAlgorithm yens;
    private static final String COMPONENT = "BENCHMARK";

    public BenchmarkRunner(DynamicGraph graph) {
        this.graph = graph;
        this.dijkstra = new Dijkstra(graph);
        this.astar = new AStar(graph);
        this.yens = new YensAlgorithm(graph);
    }

    public Map<String, Object> runFullBenchmark(String source, String destination) {
        Logger.info(COMPONENT, "Starting benchmark for " + source + " -> " + destination);
        
        Map<String, Object> report = new HashMap<>();
        
        // Benchmark Dijkstra
        Dijkstra.DijkstraResult dRes = dijkstra.findPath(source, destination);
        Map<String, Object> dMetrics = new HashMap<>();
        dMetrics.put("time_us", dRes.executionTimeNs / 1000.0);
        dMetrics.put("nodes_explored", dRes.nodesExplored);
        dMetrics.put("distance_km", dRes.distance);
        report.put("dijkstra", dMetrics);

        // Benchmark A*
        AStar.AStarResult aRes = astar.findPath(source, destination);
        Map<String, Object> aMetrics = new HashMap<>();
        aMetrics.put("time_us", aRes.executionTimeNs / 1000.0);
        aMetrics.put("nodes_explored", aRes.nodesExplored);
        aMetrics.put("distance_km", aRes.distance);
        report.put("astar", aMetrics);

        // Efficiency Analysis
        double timeImprovement = ((dRes.executionTimeNs - aRes.executionTimeNs) / (double) dRes.executionTimeNs) * 100;
        double nodesReduction = ((dRes.nodesExplored - aRes.nodesExplored) / (double) dRes.nodesExplored) * 100;
        
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("time_improvement_percent", Math.round(timeImprovement * 100.0) / 100.0);
        analysis.put("nodes_reduction_percent", Math.round(nodesReduction * 100.0) / 100.0);
        report.put("analysis", analysis);
        
        Logger.info(COMPONENT, String.format("Benchmark complete. A* is %.2f%% faster than Dijkstra.", timeImprovement));
        
        return report;
    }
}
