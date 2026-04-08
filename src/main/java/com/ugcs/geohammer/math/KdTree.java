package com.ugcs.geohammer.math;

import com.ugcs.geohammer.util.Check;
import javafx.geometry.Point2D;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

/**
 * NB: Implementation is based on a generic binary search tree
 * and thus is well-suited for the random input sources only.
 */
public class KdTree<V> {

    private Node<V> root;

    private int size;

    private static int compare(Point2D p1, Point2D p2, int level) {
        int k = level % 2;
        return k == 0
                ? Double.compare(p1.getX(), p2.getX())
                : Double.compare(p1.getY(), p2.getY());
    }

    private static double axisDistance2(Point2D p1, Point2D p2, int level) {
        int k = level % 2;
        double d = k == 0
                ? p1.getX() - p2.getX()
                : p1.getY() - p2.getY();
        return d * d;
    }

    private static double distance2(Point2D p1, Point2D p2) {
        double dx = p1.getX() - p2.getX();
        double dy = p1.getY() - p2.getY();
        return dx * dx + dy * dy;
    }

    public static <V> KdTree<V> buildTree(List<Point2D> points, List<V> values) {
        Check.notNull(points);
        Check.notNull(values);
        Check.condition(points.size() == values.size());

        KdTree<V> tree = new KdTree<>();
        int n = points.size();
        if (n > 0) {
            List<Node<V>> nodes = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                Node<V> node = new Node<>();
                node.point = points.get(i);
                node.value = values.get(i);
                nodes.add(node);
            }
            tree.root = buildTree(nodes, 0, n, 0);
            tree.size = n;
        }
        return tree;
    }

    private static <V> Node<V> buildTree(List<Node<V>> nodes, int from, int to, int level) {
        int k = medianSelect(nodes, from, to, level);
        Node<V> node = nodes.get(k);
        // left sub-tree
        int left = k - from;
        if (left > 0) {
            node.left = buildTree(nodes, from, k, level + 1);
        } else {
            node.left = null;
        }
        // right sub-tree
        int right = to - k - 1;
        if (right > 0) {
            node.right = buildTree(nodes, k + 1, to, level + 1);
        } else {
            node.right = null;
        }
        return node;
    }

    private static <V> int medianSelect(List<Node<V>> values, int from, int to, int level) {
        // split [from, to) array into the two equal by size parts
        // and return index of the median;
        // all values from left are less and values from right are
        // greater than the median value;
        // items comparison is level-based

        int n = from + (to - from) / 2;
        while (from < to) {
            if (from == to - 1) {
                return from;
            }
            int i = partition(values, from, to, level);
            if (i == n) {
                return n;
            }
            if (n < i) {
                to = i;
            } else {
                from = i + 1;
            }
        }
        assert (false);
        return -1;
    }

    private static <V> int partition(List<Node<V>> nodes, int from, int to, int level) {
        assert (from < to);

        Point2D pivot = nodes.get(to - 1).point; // last element is a pivot
        int i = from;
        for (int j = from; j < to - 1; ++j) {
            Point2D l = nodes.get(j).point;
            if (compare(l, pivot, level) < 0) {
                // swap will not occur for sorted sequence
                Collections.swap(nodes, i, j);
                i++;
            }
        }
        // place pivot in the middle
        Collections.swap(nodes, i, to - 1);
        return i;
    }

    public int size() {
        return size;
    }

    public void insert(Point2D point, V value) {
        Node<V> y = null;
        Node<V> x = root;
        int level = 0;
        int cmp = -1;
        while (x != null) {
            cmp = compare(point, x.point, level);
            y = x;
            x = cmp < 0 ? x.left : x.right;
            ++level;
        }

        Node<V> z = new Node<V>();
        z.point = point;
        z.value = value;

        if (y == null) {
            root = z;
        } else {
            if (cmp < 0) {
                y.left = z;
            } else {
                y.right = z;
            }
        }
        ++size;
    }

    public Neighbor<V> nearestNeighbor(Point2D point) {
        if (root == null) {
            return null;
        }

        Neighbor<V> nearest = new Neighbor<>();
        nearest.distance2 = Double.MAX_VALUE;
        NearestIterator it = new NearestIterator(point, nearest.distance2);
        while (it.hasNext()) {
            Node<V> x = it.next();
            double distance2 = distance2(point, x.point);
            if (distance2 < nearest.distance2) {
                nearest.distance2 = distance2;
                nearest.point = x.point;
                nearest.value = x.value;
                it.updateWorst2(distance2);
            }
        }
        return nearest;
    }

    public List<Neighbor<V>> kNearestNeighbors(Point2D point, int k) {
        if (root == null) {
            return List.of();
        }

        // farthest neighbor at top
        PriorityQueue<Neighbor<V>> heap = new PriorityQueue<>(k,
                (a, b) -> Double.compare(b.distance2, a.distance2));
        NearestIterator it = new NearestIterator(point);
        while (it.hasNext()) {
            Node<V> x = it.next();
            double distance2 = distance2(point, x.point);
            if (heap.size() < k) {
                Neighbor<V> neighbor = new Neighbor<>();
                neighbor.point = x.point;
                neighbor.value = x.value;
                neighbor.distance2 = distance2;
                heap.offer(neighbor);
            } else if (distance2 < heap.peek().distance2) {
                heap.poll();
                Neighbor<V> neighbor = new Neighbor<>();
                neighbor.point = x.point;
                neighbor.value = x.value;
                neighbor.distance2 = distance2;
                heap.offer(neighbor);
                it.updateWorst2(heap.peek().distance2);
            }
        }
        // sort result
        List<Neighbor<V>> neighbors = new ArrayList<>(heap);
        neighbors.sort(Comparator.comparingDouble(n -> n.distance2));
        return neighbors;
    }

    public List<Neighbor<V>> neighborsInRange(Point2D point, double radius) {
        if (root == null)
            return Collections.emptyList();

        List<Neighbor<V>> neighbors = new ArrayList<>();
        NearestIterator it = new NearestIterator(point, radius * radius);
        while (it.hasNext()) {
            Node<V> x = it.next();
            double distance2 = distance2(point, x.point);
            if (distance2 <= it.worst2()) {
                Neighbor<V> neighbor = new Neighbor<>();
                neighbor.distance2 = distance2;
                neighbor.point = x.point;
                neighbor.value = x.value;
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    /* nearest iterator */

    private static class NodeHolder<V> {

        private final Node<V> node;

        private final int level;

        private final double parentAxisDistance2;

        NodeHolder(Node<V> node, int level) {
            this.node = node;
            this.level = level;
            this.parentAxisDistance2 = Double.NaN;
        }

        NodeHolder(Node<V> node, int level, double parentAxisDistance2) {
            this.node = node;
            this.level = level;
            this.parentAxisDistance2 = parentAxisDistance2;
        }
    }

    private class NearestIterator implements Iterator<Node<V>> {

        private final Point2D point;

        private double worst2;

        private final Deque<NodeHolder<V>> stack;

        private Node<V> next;

        public NearestIterator(Point2D point) {
            this(point, Double.MAX_VALUE);
        }

        public NearestIterator(Point2D point, double worst2) {
            this.point = point;
            this.worst2 = worst2;
            this.stack = new ArrayDeque<>();
            if (root != null) {
                this.stack.addFirst(new NodeHolder<>(root, 0));
            }
            next = nextMatch();
        }

        public double worst2() {
            return worst2;
        }

        public void updateWorst2(double worst2) {
            this.worst2 = worst2;
        }

        private Node<V> nextMatch() {
            while (!stack.isEmpty()) {
                NodeHolder<V> holder = stack.removeFirst();
                // cut by the parent axis distance
                if (!Double.isNaN(holder.parentAxisDistance2) && holder.parentAxisDistance2 > worst2) {
                    continue;
                }

                Node<V> x = holder.node;
                Node<V> next;
                Node<V> opposite;
                int level = holder.level;
                int cmp = compare(point, x.point, level);
                if (cmp < 0) {
                    next = x.left;
                    opposite = x.right;
                } else {
                    next = x.right;
                    opposite = x.left;
                }
                if (opposite != null) {
                    if (next == null) {
                        stack.addFirst(new NodeHolder<>(opposite, level + 1));
                    } else {
                        double axisDistance2 = axisDistance2(point, x.point, level);
                        if (axisDistance2 <= worst2) {
                            stack.addFirst(new NodeHolder<>(opposite, level + 1, axisDistance2));
                        }
                    }
                }
                if (next != null) {
                    stack.addFirst(new NodeHolder<>(next, level + 1));
                }

                // match
                if (distance2(point, x.point) <= worst2) {
                    return x;
                }
            }
            return null;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Node<V> next() {
            Node<V> e = next;
            if (e == null) {
                throw new NoSuchElementException();
            }
            next = nextMatch();
            return e;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /* nearest iterator (end) */

    private static class Node<V> {

        private Point2D point;

        private V value;

        private Node<V> left;

        private Node<V> right;
    }

    public static class Neighbor<V> {

        private Point2D point;

        private V value;

        private double distance2;

        public Point2D point() {
            return point;
        }

        public V value() {
            return value;
        }

        public double distance2() {
            return distance2;
        }
    }
}