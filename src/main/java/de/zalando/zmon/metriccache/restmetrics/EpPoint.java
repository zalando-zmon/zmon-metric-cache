package de.zalando.zmon.metriccache.restmetrics;

/**
 * Represents a cross instance result for read queries
 */
public class EpPoint {

    public boolean partial; // missing data in series
    public double rate; // average rate cross instances
    public double maxRate; // max rate for single instance
    public double latency; // average 99th latency cross instances
    public double latencyMedian; // average median latency cross instance
    public double latency75th; // average 75th latency cross instances
    public double maxLatency;  // max 99th cross instance
    public double minLatency; // min 99th cross instance
    public long ts;

    public EpPoint(long t, double r, double l, double l75th, double lMedian, double rMax, double lMax, double lMin, boolean partial) {
        ts = t;
        rate = r;
        latency = l;
        latencyMedian = lMedian;
        latency75th = l75th;
        this.partial = partial;
        this.maxRate = rMax;
        this.maxLatency= lMax;
        this.minLatency = lMin;
        this.latency75th = l75th;
        this.latencyMedian = lMedian;
    }
}
