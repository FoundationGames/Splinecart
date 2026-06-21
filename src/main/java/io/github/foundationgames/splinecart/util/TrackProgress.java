package io.github.foundationgames.splinecart.util;

import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public record TrackProgress(BlockPos startBlock, Pose startPose, BlockPos endBlock, Pose endPose, boolean orientationOnly, double t) {
    public static final StreamCodec<ByteBuf, TrackProgress> PACKET_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TrackProgress::startBlock,
            Pose.PACKET_CODEC, TrackProgress::startPose,
            BlockPos.STREAM_CODEC, TrackProgress::endBlock,
            Pose.PACKET_CODEC, TrackProgress::endPose,
            ByteBufCodecs.BOOL, TrackProgress::orientationOnly,
            ByteBufCodecs.DOUBLE, TrackProgress::t,
            TrackProgress::new
    );

    public static final EntityDataSerializer<TrackProgress> DATA_HANDLER = EntityDataSerializer.forValueType(PACKET_CODEC);

    public static TrackProgress of(TrackTiesBlockEntity e, double t) {
        var next = e.next();

        var nextPos = e.getBlockPos();
        var nextPose = e.pose();

        if (next != null) {
            nextPos = next.getBlockPos();
            nextPose = next.pose();
        }

        return new TrackProgress(e.getBlockPos(), e.pose(), nextPos, nextPose, false, t);
    }

    public static TrackProgress empty(Vec3 pos) {
        var bpos = BlockPos.containing(pos);
        var pose = new Pose(new Vector3d(pos.x(), pos.y(), pos.z()), new Matrix3d());
        return new TrackProgress(bpos, pose, bpos, pose, true, 0);
    }

    public boolean getOrientation(TrackProgress prev, double delta, Vector3d pos, Quaternionf rotation) {
        if (orientationOnly()) {
            return false;
        }

        double t = Mth.lerp(delta, prev.t(), t());

        if (!prev.orientationOnly()) {
            if (prev.startBlock().equals(startBlock()) && prev.endBlock().equals(endBlock())) {
                var mat = new Matrix3d().rotate(rotation);
                startPose().interpolate(endPose(), t, pos, mat, new Vector3d());
                mat.getNormalizedRotation(rotation);
            }
        } else {
            var endPos = new Vector3d();
            startPose().interpolateTranslation(endPose(), t, endPos, new Vector3d());
            pos.lerp(endPos, delta);
        }

        return true;
    }
}
