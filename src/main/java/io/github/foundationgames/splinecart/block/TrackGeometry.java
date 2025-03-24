package io.github.foundationgames.splinecart.block;

import java.util.function.Function;

public class TrackGeometry implements AutoCloseable {
    public static Function<TrackTiesBlockEntity, TrackGeometry> CONSTRUCTOR = TrackGeometry::new;

    public final TrackTiesBlockEntity trackTies;
    public boolean needsRebuild = false;

    public TrackGeometry(TrackTiesBlockEntity trackTies) {
        this.trackTies = trackTies;
    }

    @Override
    public void close() {
    }
}
