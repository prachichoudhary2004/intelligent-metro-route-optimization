package core;

/**
 * Represents a connection between two metro stations
 */
public class MetroEdge {
    private String from;
    private String to;
    private double distance;
    private int time;
    private String line;
    private double congestionFactor;
    
    public MetroEdge(String from, String to, double distance, int time, String line) {
        this.from = from;
        this.to = to;
        this.distance = distance;
        this.time = time;
        this.line = line;
        this.congestionFactor = 1.0; // Default no congestion
    }
    
    // Getters
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public double getDistance() { return distance; }
    public int getTime() { return time; }
    public String getLine() { return line; }
    public double getCongestionFactor() { return congestionFactor; }
    
    // Setters
    public void setCongestionFactor(double factor) { this.congestionFactor = factor; }
    
    /**
     * Get adjusted distance based on congestion
     */
    public double getAdjustedDistance() {
        return distance * congestionFactor;
    }
    
    /**
     * Get adjusted time based on congestion
     */
    public int getAdjustedTime() {
        return (int) Math.ceil(time * congestionFactor);
    }
    
    @Override
    public String toString() {
        return from + " -> " + to + " (" + distance + "km, " + time + "min)";
    }
}
