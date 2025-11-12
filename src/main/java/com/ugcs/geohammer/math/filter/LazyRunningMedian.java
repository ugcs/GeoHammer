package com.ugcs.geohammer.math.filter;

import com.ugcs.geohammer.util.Check;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

public class LazyRunningMedian {

    // smallest half
    private PriorityQueue<Number> maxHeap
            = new PriorityQueue<>(new NumberComparator().reversed());
    // largest half
    private PriorityQueue<Number> minHeap
            = new PriorityQueue<>(new NumberComparator());
    private int maxHeapSize;
    private int minHeapSize;
    // track of the removed elements
    // for a lazy removal from the heaps
    private Map<Number, Integer> numRemovals = new HashMap<>();

    // window
    private Queue<Number> window = new LinkedList<>();
    private int windowLimit;

    public LazyRunningMedian(int windowLimit) {
        Check.condition(windowLimit > 0);
        this.windowLimit = windowLimit;
    }

    public void growWindow(int delta) {
        Check.condition(windowLimit + delta > 0);
        windowLimit += delta;

        // fit window to limit
        purgeWindow();
        // rebalance min-max heaps
        balanceHeaps();
    }

    public void add(Number value) {
        // inv: roots of the heaps are valid (not removed)
        // do not add nulls to heaps but keep them in a window
        if (value != null) {
            if (maxHeapSize == 0 || value.doubleValue() <= maxHeap.peek().doubleValue()) {
                maxHeap.offer(value);
                maxHeapSize++;
            } else {
                minHeap.offer(value);
                minHeapSize++;
            }
        }
        // add to window
        window.offer(value);
        // fit window to limit
        purgeWindow();
        // rebalance min-max heaps
        balanceHeaps();
    }

    private void purgeRemovedRoots(PriorityQueue<Number> heap) {
        while (!heap.isEmpty()) {
            Number root = heap.peek();
            // removals counter for the root value
            Integer n = numRemovals.get(root);
            if (n == null) {
                break;
            }
            if (n == 0) {
                numRemovals.remove(root);
                break;
            }
            heap.poll();
            if (n > 1) {
                numRemovals.put(root, n - 1);
            } else {
                numRemovals.remove(root);
            }
        }
    }

    private void purgeWindow() {
        while (window.size() > windowLimit) {
            Number removed = window.poll();
            if (removed != null) {
                numRemovals.put(removed, numRemovals.getOrDefault(removed, 0) + 1);
                if (maxHeapSize > 0 && removed.doubleValue() <= maxHeap.peek().doubleValue()) {
                    maxHeapSize--;
                    purgeRemovedRoots(maxHeap);
                } else {
                    minHeapSize--;
                    purgeRemovedRoots(minHeap);
                }
            }
        }
    }

    private void balanceHeaps() {
        while (maxHeapSize < minHeapSize) {
            maxHeap.offer(minHeap.poll());
            maxHeapSize++;
            minHeapSize--;
            purgeRemovedRoots(minHeap);
        }
        while (maxHeapSize > minHeapSize + 1) {
            minHeap.offer(maxHeap.poll());
            maxHeapSize--;
            minHeapSize++;
            purgeRemovedRoots(maxHeap);
        }
    }

    public Number median() {
        if (maxHeapSize == 0) {
            return null;
        }
        return maxHeapSize == minHeapSize
                ? 0.5 * (maxHeap.peek().doubleValue() + minHeap.peek().doubleValue())
                : maxHeap.peek();
    }
}
