package com.proximitysearch;

import java.util.*;

/**
 * Point quadtree for proximity search (restaurants, ATMs, POIs).
 *
 * - Leaf: holds up to CAPACITY places.
 * - Internal node: holds 4 children (SW, SE, NW, NE) and no places.
 * - Supports insert, box search, radius search, and nearest-k.
 *
 * Child index: +1 if lng >= midLng (east), +2 if lat >= midLat (north)
 *   0 = SW, 1 = SE, 2 = NW, 3 = NE
 */
public class QuadTree {

    // ---------- Data types ----------

    public record Place(long id, double lat, double lng) {}

    /** Axis-aligned rectangle in lat/lng space. */
    private record Bounds(double minLat, double maxLat, double minLng, double maxLng) {
        double midLat() { return (minLat + maxLat) / 2; }
        double midLng() { return (minLng + maxLng) / 2; }

        boolean contains(double lat, double lng) {
            return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
        }

        boolean intersects(Bounds o) {
            return !(o.minLat > maxLat || o.maxLat < minLat
                  || o.minLng > maxLng || o.maxLng < minLng);
        }

        Bounds child(int idx) {
            double mLat = midLat(), mLng = midLng();
            boolean north = (idx & 2) != 0, east = (idx & 1) != 0;
            return new Bounds(north ? mLat : minLat, north ? maxLat : mLat,
                              east  ? mLng : minLng, east  ? maxLng : mLng);
        }

        /** Distance (km) from a point to the nearest point of this rectangle. */
        double minDistanceKm(double lat, double lng) {
            double cLat = Math.max(minLat, Math.min(lat, maxLat));
            double cLng = Math.max(minLng, Math.min(lng, maxLng));
            return haversineKm(lat, lng, cLat, cLng);
        }
    }

    private static final class Node {
        final Bounds bounds;
        final int depth;
        List<Place> places = new ArrayList<>(); // non-null only for leaves
        Node[] children;                        // non-null only for internal nodes

        Node(Bounds bounds, int depth) {
            this.bounds = bounds;
            this.depth = depth;
        }

        boolean isLeaf() { return children == null; }
    }

    // ---------- Tree ----------

    private final Node root;
    private final int capacity;
    private final int maxDepth;
    private int size = 0;

    public QuadTree(int capacity, int maxDepth) {
        this.capacity = capacity;
        this.maxDepth = maxDepth;
        this.root = new Node(new Bounds(-90, 90, -180, 180), 0);
    }

    public int size() { return size; }

    // ---------- Insert ----------

    public boolean insert(Place p) {
        if (!root.bounds.contains(p.lat(), p.lng())) return false;
        insert(root, p);
        size++;
        return true;
    }

    private void insert(Node node, Place p) {
        while (!node.isLeaf()) {
            node = node.children[childIndex(node.bounds, p.lat(), p.lng())];
        }
        // maxDepth guards against many points at (nearly) identical coordinates
        if (node.places.size() < capacity || node.depth >= maxDepth) {
            node.places.add(p);
            return;
        }
        split(node);
        insert(node, p); // node is now internal; descends to the right child
    }

    private void split(Node node) {
        node.children = new Node[4];
        for (int i = 0; i < 4; i++) {
            node.children[i] = new Node(node.bounds.child(i), node.depth + 1);
        }
        for (Place q : node.places) {
            node.children[childIndex(node.bounds, q.lat(), q.lng())].places.add(q);
        }
        node.places = null; // internal nodes hold no points
    }

    private static int childIndex(Bounds b, double lat, double lng) {
        return (lat >= b.midLat() ? 2 : 0) + (lng >= b.midLng() ? 1 : 0);
    }

    // ---------- Box search ----------

    /** All places inside the given lat/lng box. */
    public List<Place> searchBox(double minLat, double maxLat, double minLng, double maxLng) {
        List<Place> out = new ArrayList<>();
        searchBox(root, new Bounds(minLat, maxLat, minLng, maxLng), out);
        return out;
    }

    private void searchBox(Node node, Bounds box, List<Place> out) {
        if (!node.bounds.intersects(box)) return; // prune whole subtree
        if (node.isLeaf()) {
            for (Place p : node.places) {
                if (box.contains(p.lat(), p.lng())) out.add(p);
            }
            return;
        }
        for (Node c : node.children) searchBox(c, box, out);
    }

    // ---------- Radius search ----------

    /** Places within radiusKm of (lat, lng), sorted by distance. */
    public List<Place> searchRadius(double lat, double lng, double radiusKm) {
        // 1. Radius -> bounding box (1 degree of latitude ~ 111 km)
        double dLat = radiusKm / 111.0;
        double dLng = radiusKm / (111.0 * Math.max(0.01, Math.cos(Math.toRadians(lat))));
        List<Place> candidates =
            searchBox(lat - dLat, lat + dLat, lng - dLng, lng + dLng);

        // 2. Box corners are farther than the radius: filter by true distance
        List<Place> result = new ArrayList<>();
        for (Place p : candidates) {
            if (haversineKm(lat, lng, p.lat(), p.lng()) <= radiusKm) result.add(p);
        }
        result.sort(Comparator.comparingDouble(p -> haversineKm(lat, lng, p.lat(), p.lng())));
        return result;
    }

    // ---------- Nearest-k (best-first search) ----------

    private record Entry(double distKm, Node node, Place place) {}

    /**
     * k nearest places. A priority queue holds both nodes (keyed by the minimum
     * possible distance to their region) and places (keyed by exact distance).
     * The first k places popped are the k nearest, regardless of cell boundaries.
     */
    public List<Place> nearestK(double lat, double lng, int k) {
        PriorityQueue<Entry> pq = new PriorityQueue<>(Comparator.comparingDouble(Entry::distKm));
        pq.add(new Entry(0, root, null));
        List<Place> result = new ArrayList<>();

        while (!pq.isEmpty() && result.size() < k) {
            Entry e = pq.poll();
            if (e.place() != null) {
                result.add(e.place());
            } else if (e.node().isLeaf()) {
                for (Place p : e.node().places) {
                    pq.add(new Entry(haversineKm(lat, lng, p.lat(), p.lng()), null, p));
                }
            } else {
                for (Node c : e.node().children) {
                    pq.add(new Entry(c.bounds.minDistanceKm(lat, lng), c, null));
                }
            }
        }
        return result;
    }

    // ---------- Distance ----------

    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    // ---------- Demo ----------

    public static void main(String[] args) {
        QuadTree tree = new QuadTree(4, 20); // capacity 4 to force splits

        tree.insert(new Place(10482, 12.9716, 77.6411)); // Indiranagar
        tree.insert(new Place(10483, 12.9784, 77.6408));
        tree.insert(new Place(10484, 12.9352, 77.6245)); // Koramangala
        tree.insert(new Place(10485, 12.9698, 77.7500)); // Whitefield
        tree.insert(new Place(10486, 12.2958, 76.6394)); // Mysuru
        tree.insert(new Place(10487, 13.0827, 80.2707)); // Chennai
        tree.insert(new Place(10488, 12.9141, 74.8560)); // Mangaluru

        double lat = 12.9716, lng = 77.6411;

        System.out.println("Within 5 km of Indiranagar:");
        for (Place p : tree.searchRadius(lat, lng, 5)) {
            System.out.printf("  %d  %.2f km%n", p.id(), haversineKm(lat, lng, p.lat(), p.lng()));
        }

        System.out.println("3 nearest:");
        for (Place p : tree.nearestK(lat, lng, 3)) {
            System.out.printf("  %d  %.2f km%n", p.id(), haversineKm(lat, lng, p.lat(), p.lng()));
        }

        // Sanity check against brute force on random data
        Random rnd = new Random(42);
        QuadTree big = new QuadTree(50, 20);
        List<Place> all = new ArrayList<>();
        for (long i = 0; i < 100_000; i++) {
            Place p = new Place(i, 8 + rnd.nextDouble() * 28, 68 + rnd.nextDouble() * 30);
            all.add(p);
            big.insert(p);
        }
        List<Place> viaTree = big.nearestK(12.97, 77.64, 10);
        List<Place> viaScan = new ArrayList<>(all);
        viaScan.sort(Comparator.comparingDouble(p -> haversineKm(12.97, 77.64, p.lat(), p.lng())));
        System.out.println("nearestK matches brute force: "
            + viaTree.equals(viaScan.subList(0, 10)));
    }
}
