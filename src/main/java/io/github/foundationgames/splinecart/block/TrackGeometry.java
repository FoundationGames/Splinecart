package io.github.foundationgames.splinecart.block;

import java.util.function.Function;

public class TrackGeometry implements AutoCloseable {
    public static Function<TrackTiesBlockEntity, TrackGeometry> CONSTRUCTOR = TrackGeometry::new;

    public final TrackTiesBlockEntity trackTies;
    public boolean needsRebuild = false;

    public int minSectionX, minSectionY, minSectionZ, maxSectionX, maxSectionY, maxSectionZ;

    public TrackGeometry(TrackTiesBlockEntity trackTies) {
        this.trackTies = trackTies;
    }

    public void resetBounds() {
        minSectionX = minSectionY = minSectionZ = 0;
        maxSectionX = maxSectionY = maxSectionZ = -1;
    }

    public boolean isInChunk(int sx, int sy, int sz) {
        return sx >= minSectionX && sx <= maxSectionX &&
                sy >= minSectionY && sy <= maxSectionY &&
                sz >= minSectionZ && sz <= maxSectionZ;
    }

    @Override
    public void close() {
    }
}
