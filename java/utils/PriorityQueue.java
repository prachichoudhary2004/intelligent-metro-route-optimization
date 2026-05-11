package utils;

import java.util.*;

/**
 * Enhanced Priority Queue with decreaseKey optimization
 * Refactored from Heap.java with improved performance for Dijkstra/A*
 */
public class PriorityQueue<T extends Comparable<T>> {
    
    private ArrayList<T> heap;
    private Map<T, Integer> indexMap;
    
    public PriorityQueue() {
        this.heap = new ArrayList<>();
        this.indexMap = new HashMap<>();
    }
    
    public void add(T item) {
        heap.add(item);
        int index = heap.size() - 1;
        indexMap.put(item, index);
        upheapify(index);
    }
    
    public T remove() {
        if (heap.isEmpty()) {
            throw new NoSuchElementException("Priority queue is empty");
        }
        
        T min = heap.get(0);
        T last = heap.remove(heap.size() - 1);
        
        if (!heap.isEmpty()) {
            heap.set(0, last);
            indexMap.put(last, 0);
            downheapify(0);
        }
        
        indexMap.remove(min);
        return min;
    }
    
    public T peek() {
        if (heap.isEmpty()) {
            throw new NoSuchElementException("Priority queue is empty");
        }
        return heap.get(0);
    }
    
    /**
     * Decrease key operation - crucial for Dijkstra's algorithm
     * Time complexity: O(log n)
     */
    public boolean decreaseKey(T item, T newItem) {
        Integer index = indexMap.get(item);
        if (index == null) {
            return false; // Item not found
        }
        
        if (newItem.compareTo(item) > 0) {
            return false; // New item is not smaller
        }
        
        heap.set(index, newItem);
        indexMap.remove(item);
        indexMap.put(newItem, index);
        upheapify(index);
        return true;
    }
    
    /**
     * Update priority - alternative name for decreaseKey
     */
    public boolean updatePriority(T item) {
        Integer index = indexMap.get(item);
        if (index == null) {
            return false;
        }
        
        upheapify(index);
        return true;
    }
    
    /**
     * Check if item exists in the priority queue
     */
    public boolean contains(T item) {
        return indexMap.containsKey(item);
    }
    
    public int size() {
        return heap.size();
    }
    
    public boolean isEmpty() {
        return heap.isEmpty();
    }
    
    private void upheapify(int index) {
        while (index > 0) {
            int parent = (index - 1) / 2;
            if (compare(heap.get(index), heap.get(parent)) >= 0) {
                break;
            }
            swap(index, parent);
            index = parent;
        }
    }
    
    private void downheapify(int index) {
        int size = heap.size();
        while (index < size) {
            int left = 2 * index + 1;
            int right = 2 * index + 2;
            int smallest = index;
            
            if (left < size && compare(heap.get(left), heap.get(smallest)) < 0) {
                smallest = left;
            }
            
            if (right < size && compare(heap.get(right), heap.get(smallest)) < 0) {
                smallest = right;
            }
            
            if (smallest == index) {
                break;
            }
            
            swap(index, smallest);
            index = smallest;
        }
    }
    
    private void swap(int i, int j) {
        T item1 = heap.get(i);
        T item2 = heap.get(j);
        
        heap.set(i, item2);
        heap.set(j, item1);
        
        indexMap.put(item1, j);
        indexMap.put(item2, i);
    }
    
    private int compare(T item1, T item2) {
        return item1.compareTo(item2);
    }
    
    /**
     * Clear the priority queue
     */
    public void clear() {
        heap.clear();
        indexMap.clear();
    }
    
    /**
     * Get all elements (for debugging)
     */
    public List<T> getAllElements() {
        return new ArrayList<>(heap);
    }
    
    /**
     * Display the priority queue (for debugging)
     */
    public void display() {
        System.out.println("Priority Queue: " + heap);
        System.out.println("Index Map: " + indexMap);
    }
}
