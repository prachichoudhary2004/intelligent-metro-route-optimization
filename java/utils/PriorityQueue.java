import java.util.*;

public class PriorityQueue<T extends Comparable<T>> {
    private ArrayList<T> heap;
    
    public PriorityQueue() {
        heap = new ArrayList<>();
    }
    
    public void insert(T item) {
        heap.add(item);
        heapifyUp(heap.size() - 1);
    }
    
    public T extract() {
        if (heap.isEmpty()) return null;
        
        T min = heap.get(0);
        T last = heap.remove(heap.size() - 1);
        
        if (!heap.isEmpty()) {
            heap.set(0, last);
            heapifyDown(0);
        }
        
        return min;
    }
    
    public boolean isEmpty() {
        return heap.isEmpty();
    }
    
    public int size() {
        return heap.size();
    }
    
    public boolean contains(T item) {
        return heap.contains(item);
    }
    
    private void heapifyUp(int index) {
        while (index > 0) {
            int parent = (index - 1) / 2;
            if (heap.get(index).compareTo(heap.get(parent)) >= 0) break;
            
            swap(index, parent);
            index = parent;
        }
    }
    
    private void heapifyDown(int index) {
        while (true) {
            int left = 2 * index + 1;
            int right = 2 * index + 2;
            int smallest = index;
            
            if (left < heap.size() && heap.get(left).compareTo(heap.get(smallest)) < 0) {
                smallest = left;
            }
            
            if (right < heap.size() && heap.get(right).compareTo(heap.get(smallest)) < 0) {
                smallest = right;
            }
            
            if (smallest == index) break;
            
            swap(index, smallest);
            index = smallest;
        }
    }
    
    private void swap(int i, int j) {
        T temp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, temp);
    }
}
