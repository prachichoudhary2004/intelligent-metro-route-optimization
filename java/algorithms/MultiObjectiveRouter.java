import java.util.*;

public class MultiObjectiveRouter {
    private Graph_M graph;
    
    public MultiObjectiveRouter(Graph_M graph) {
        this.graph = graph;
    }
    
    public MultiObjectiveResult findOptimalRoute(int start, int goal, double timeWeight, double costWeight) {
        Map<Integer, RouteNode> nodes = new HashMap<>();
        PriorityQueue<RouteNode> openSet = new PriorityQueue<>();
        
        RouteNode startNode = new RouteNode(start, 0, 0, null);
        nodes.put(start, startNode);
        openSet.insert(startNode);
        
        int nodesExplored = 0;
        
        while (!openSet.isEmpty()) {
            RouteNode current = openSet.extract();
            nodesExplored++;
            
            if (current.id == goal) {
                return new MultiObjectiveResult(reconstructPath(current), current.time, current.cost, nodesExplored);
            }
            
            for (Graph_M.Edge edge : graph.getNeighbors(current.id)) {
                double newTime = current.time + edge.weight;
                double newCost = current.cost + calculateCost(edge.weight);
                double newScore = newTime * timeWeight + newCost * costWeight;
                
                RouteNode neighbor = nodes.get(edge.to);
                if (neighbor == null || newScore < neighbor.getScore(timeWeight, costWeight)) {
                    RouteNode newNode = new RouteNode(edge.to, newTime, newCost, current);
                    nodes.put(edge.to, newNode);
                    openSet.insert(newNode);
                }
            }
        }
        
        return new MultiObjectiveResult(new ArrayList<>(), 0, 0, nodesExplored);
    }
    
    private double calculateCost(double distance) {
        return 10 + distance * 2.5;
    }
    
    private List<Integer> reconstructPath(RouteNode node) {
        List<Integer> path = new ArrayList<>();
        while (node != null) {
            path.add(0, node.id);
            node = node.parent;
        }
        return path;
    }
    
    public static class RouteNode implements Comparable<RouteNode> {
        public int id;
        public double time;
        public double cost;
        public RouteNode parent;
        
        public RouteNode(int id, double time, double cost, RouteNode parent) {
            this.id = id;
            this.time = time;
            this.cost = cost;
            this.parent = parent;
        }
        
        public double getScore(double timeWeight, double costWeight) {
            return time * timeWeight + cost * costWeight;
        }
        
        @Override
        public int compareTo(RouteNode other) {
            return Double.compare(this.time + this.cost, other.time + other.cost);
        }
    }
    
    public static class MultiObjectiveResult {
        public List<Integer> path;
        public double time;
        public double cost;
        public int nodesExplored;
        
        public MultiObjectiveResult(List<Integer> path, double time, double cost, int nodesExplored) {
            this.path = path;
            this.time = time;
            this.cost = cost;
            this.nodesExplored = nodesExplored;
        }
    }
}
