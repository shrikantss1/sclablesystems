package com.uber;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Standalone driver location simulator.
 *
 * Every interval, publishes one JSON message per driver to a Kafka topic
 * (key = driverId, so each driver's updates stay ordered in one partition):
 *   {"driverId":"driver-1","lat":37.774912,"lng":-122.419401,"ts":1700000000000}
 *
 * Drivers start at random points around a city center and random-walk
 * with a heading that drifts slightly each tick (looks like real movement).
 *
 * Usage:
 *   java com.uber.DriverSimulator [--drivers N] [--interval SECONDS]
 *        [--lat CENTER_LAT] [--lng CENTER_LNG] [--radius-km R]
 *        [--bootstrap-servers HOST:PORT] [--topic NAME] [--print]
 *
 * Defaults: 10 drivers, 1 second, centered on San Francisco, 5 km radius,
 * Kafka at localhost:9092, topic DriverLocation. --print also echoes to stdout.
 */
public class DriverSimulator {

    private static final double KM_PER_DEG_LAT = 111.32;
    private static final double SPEED_KMH = 30;

    private static class Driver {
        final String id;
        double lat, lng, headingRad;

        Driver(String id, double lat, double lng, double headingRad) {
            this.id = id;
            this.lat = lat;
            this.lng = lng;
            this.headingRad = headingRad;
        }
    }

    private final List<Driver> drivers = new ArrayList<>();
    private final Random random = new Random();
    private final double centerLat, centerLng, radiusKm;
    private final double stepKm;
    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final boolean print;

    DriverSimulator(int count, double intervalSeconds, double centerLat, double centerLng, double radiusKm,
                    KafkaProducer<String, String> producer, String topic, boolean print) {
        this.producer = producer;
        this.topic = topic;
        this.print = print;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.radiusKm = radiusKm;
        this.stepKm = SPEED_KMH / 3600.0 * intervalSeconds;
        for (int i = 1; i <= count; i++) {
            double r = radiusKm * Math.sqrt(random.nextDouble());
            double theta = random.nextDouble() * 2 * Math.PI;
            drivers.add(new Driver("driver-" + i,
                    centerLat + r * Math.sin(theta) / KM_PER_DEG_LAT,
                    centerLng + r * Math.cos(theta) / kmPerDegLng(centerLat),
                    random.nextDouble() * 2 * Math.PI));
        }
    }

    private static double kmPerDegLng(double lat) {
        return KM_PER_DEG_LAT * Math.cos(Math.toRadians(lat));
    }

    /** Moves every driver one step and publishes their position to Kafka. */
    void tick() {
        try {
            long ts = System.currentTimeMillis();
            for (Driver d : drivers) {
                move(d);
                String json = String.format(Locale.ROOT,
                        "{\"driverId\":\"%s\",\"lat\":%.6f,\"lng\":%.6f,\"ts\":%d}",
                        d.id, d.lat, d.lng, ts);
                producer.send(new ProducerRecord<>(topic, d.id, json), (meta, err) -> {
                    if (err != null) {
                        System.err.println("Send failed: " + err.getMessage());
                    }
                });
                if (print) {
                    System.out.println(json);
                }
            }
        } catch (RuntimeException e) {
            // An uncaught exception would silently cancel the scheduled task
            System.err.println("Tick failed: " + e.getMessage());
        }
    }

    private void move(Driver d) {
        d.headingRad += random.nextGaussian() * 0.3;
        double nextLat = d.lat + stepKm * Math.sin(d.headingRad) / KM_PER_DEG_LAT;
        double nextLng = d.lng + stepKm * Math.cos(d.headingRad) / kmPerDegLng(d.lat);

        // Turn around if the driver would leave the simulation area
        double dLatKm = (nextLat - centerLat) * KM_PER_DEG_LAT;
        double dLngKm = (nextLng - centerLng) * kmPerDegLng(centerLat);
        if (Math.hypot(dLatKm, dLngKm) > radiusKm) {
            d.headingRad += Math.PI;
            return;
        }
        d.lat = nextLat;
        d.lng = nextLng;
    }

    public static void main(String[] args) throws Exception {
        int drivers = 10;
        double interval = 1;
        double lat = 37.7749, lng = -122.4194, radiusKm = 5;
        String bootstrap = "localhost:9092";
        String topic = "DriverLocation";
        boolean print = false;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--drivers" -> drivers = Integer.parseInt(args[++i]);
                    case "--interval" -> interval = Double.parseDouble(args[++i]);
                    case "--lat" -> lat = Double.parseDouble(args[++i]);
                    case "--lng" -> lng = Double.parseDouble(args[++i]);
                    case "--radius-km" -> radiusKm = Double.parseDouble(args[++i]);
                    case "--bootstrap-servers" -> bootstrap = args[++i];
                    case "--topic" -> topic = args[++i];
                    case "--print" -> print = true;
                    case "-h", "--help" -> {
                        usage();
                        return;
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }
            if (drivers < 1 || interval <= 0 || radiusKm <= 0) {
                throw new IllegalArgumentException("drivers, interval and radius-km must be positive");
            }
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
            usage();
            System.exit(1);
        }

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        DriverSimulator sim = new DriverSimulator(drivers, interval, lat, lng, radiusKm, producer, topic, print);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            producer.close();
        }));
        scheduler.scheduleAtFixedRate(sim::tick, 0, (long) (interval * 1000), TimeUnit.MILLISECONDS);
        System.err.printf(Locale.ROOT, "Simulating %d drivers every %.2fs -> %s/%s (Ctrl+C to stop)%n",
                drivers, interval, bootstrap, topic);
    }

    private static void usage() {
        System.err.println("Usage: DriverSimulator [--drivers N] [--interval SECONDS] "
                + "[--lat LAT] [--lng LNG] [--radius-km R] "
                + "[--bootstrap-servers HOST:PORT] [--topic NAME] [--print]");
    }
}
