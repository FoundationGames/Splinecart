package io.github.foundationgames.splinecart.util;

import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3d;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public record TrackProgress(BlockPos startBlock, Pose startPose, BlockPos endBlock, Pose endPose, boolean orientationOnly, double t) {
    public static final PacketCodec<ByteBuf, TrackProgress> PACKET_CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, TrackProgress::startBlock,
            Pose.PACKET_CODEC, TrackProgress::startPose,
            BlockPos.PACKET_CODEC, TrackProgress::endBlock,
            Pose.PACKET_CODEC, TrackProgress::endPose,
            PacketCodecs.BOOL, TrackProgress::orientationOnly,
            PacketCodecs.DOUBLE, TrackProgress::t,
            TrackProgress::new
    );

    public static final TrackedDataHandler<TrackProgress> DATA_HANDLER = TrackedDataHandler.create(PACKET_CODEC);

    public static TrackProgress of(TrackTiesBlockEntity e, double t) {
        var next = e.next();

        var nextPos = e.getPos();
        var nextPose = e.pose();

        if (next != null) {
            nextPos = next.getPos();
            nextPose = next.pose();
        }

        return new TrackProgress(e.getPos(), e.pose(), nextPos, nextPose, false, t);
    }

    public static TrackProgress empty(Vec3d pos) {
        var bpos = BlockPos.ofFloored(pos);
        var pose = new Pose(new Vector3d(pos.getX(), pos.getY(), pos.getZ()), new Matrix3d());
        return new TrackProgress(bpos, pose, bpos, pose, true, 0);
    }

    public boolean getOrientation(TrackProgress prev, double delta, Vector3d pos, Quaternionf rotation) {
        if (orientationOnly()) {
            return false;
        }

        double t = MathHelper.lerp(delta, prev.t(), t());

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
