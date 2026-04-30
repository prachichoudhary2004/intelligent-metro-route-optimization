import java.util.*;

public class AStar {
    private Graph_M graph;
    
    public AStar(Graph_M graph) {
        this.graph = graph;
    }
    
    public AStarResult findShortestPath(int start, int goal) {
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<Integer, Double> gScore = new HashMap<>();
        Map<Integer, Integer> cameFrom = new HashMap<>();
        
        gScore.put(start, 0.0);
        openSet.insert(new Node(start, heuristic(start, goal)));
        
        int nodesExplored = 0;
        
        while (!openSet.isEmpty()) {
            Node current = openSet.extract();
            nodesExplored++;
            
            if (current.id == goal) {
                return new AStarResult(reconstructPath(cameFrom, current.id), nodesExplored);
            }
            
            for (Graph_M.Edge neighbor : graph.getNeighbors(current.id)) {
                double tentativeGScore = gScore.get(current.id) + neighbor.weight;
                
                if (!gScore.containsKey(neighbor.to) || tentativeGScore < gScore.get(neighbor.to)) {
                    cameFrom.put(neighbor.to, current.id);
                    gScore.put(neighbor.to, tentativeGScore);
                    
                    double fScore = tentativeGScore + heuristic(neighbor.to, goal);
                    openSet.insert(new Node(neighbor.to, fScore));
                }
            }
        }
        
        return new AStarResult(new ArrayList<>(), nodesExplored);
    }
    
    private double heuristic(int a, int b) {
        // Simple heuristic - can be improved with actual coordinates
        return Math.abs(a - b) * 0.5;
    }
    
    private List<Integer> reconstructPath(Map<Integer, Integer> cameFrom, int current) {
        List<Integer> path = new ArrayList<>();
        path.add(current);
        
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(0, current);
        }
        
        return path;
    }
    
    public static class Node implements Comparable<Node> {
        public int id;
        public double fScore;
        
        public Node(int id, double fScore) {
            this.id = id;
            this.fScore = fScore;
        }
        
        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fScore, other.fScore);
        }
    }
    
    public static class AStarResult {
        public List<Integer> path;
        public int nodesExplored;
        
        public AStarResult(List<Integer> path, int nodesExplored) {
            this.path = path;
            this.nodesExplored = nodesExplored;
        }
    }
}
