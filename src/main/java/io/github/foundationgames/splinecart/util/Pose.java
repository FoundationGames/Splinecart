package io.github.foundationgames.splinecart.util;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public record Pose(Vector3dc translation, Matrix3dc basis) {
    public static final StreamCodec<ByteBuf, Pose> PACKET_CODEC = StreamCodec.of(
            (buf, pose) -> {
                for (int i = 0; i < 3; i++) {
                    buf.writeDouble(pose.translation().get(i));
                }
                for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
                    buf.writeDouble(pose.basis().get(j, i));
                }
            },
            (buf) -> {
                var translation = new Vector3d();
                var basis = new Matrix3d();

                for (int i = 0; i < 3; i++) {
                    translation.setComponent(i, buf.readDouble());
                }
                for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
                    basis.setRowColumn(i, j, buf.readDouble());
                }
                return new Pose(translation, basis);
            }
    );

    public double factor(Pose other) {
        return this.translation().distance(other.translation());
    }

    public void interpolate(Pose other, double t, Vector3d translationInOut, Matrix3d basisInOut, Vector3d derivOut) {
        interpolate(other, t, factor(other), translationInOut, basisInOut, derivOut);
    }

    public void interpolate(Pose other, double t, double factor, Vector3d translationInOut, Matrix3d basisInOut, Vector3d derivOut) {
        interpolateTranslation(other, t, factor, translationInOut, derivOut);
        var newForward = derivOut.normalize(new Vector3d());

        var rot0 = this.basis().getNormalizedRotation(new Quaterniond());
        var rot1 = other.basis().getNormalizedRotation(new Quaterniond());

        var rotT = rot0.nlerp(rot1, t, new Quaterniond());
        basisInOut.set(rotT);

        var currForward = basisInOut.getColumn(2, new Vector3d());
        new Matrix3d().identity().rotate(currForward.rotationTo(newForward, new Quaterniond())).mul(basisInOut, basisInOut);
    }

    public void interpolateTranslation(Pose other, double t, Vector3d translationInOut, Vector3d derivOut) {
        interpolateTranslation(other, t, factor(other), translationInOut, derivOut);
    }

    public void interpolateTranslation(Pose other, double t, double factor, Vector3d translationInOut, Vector3d derivOut) {
        var point0 = translationInOut.set(this.translation());
        var point1 = new Vector3d(other.translation());

        var deriv0 = this.basis().getColumn(2, new Vector3d());
        var deriv1 = other.basis().getColumn(2, new Vector3d());

        cubicHermiteSpline(t, factor, point0, deriv0, point1, deriv1, translationInOut, derivOut);
    }

    public static Vector3d cubicHermiteSpline(double t, double factor, Vector3dc p0, Vector3dc m0, Vector3dc p1, Vector3dc m1,
                                              Vector3d pOut, Vector3d mOut) {
        var temp = new Vector3d();
        var diff = new Vector3d(p1).sub(p0);

        mOut.set(temp.set(diff).mul(6*t - 6*t*t))
                .add(temp.set(m0).mul(3*t*t - 4*t + 1).mul(factor))
                .add(temp.set(m1).mul(3*t*t - 2*t).mul(factor));

        return pOut.set(p0)
                .add(temp.set(m0).mul(t*t*t - 2*t*t + t).mul(factor))
                .add(temp.set(diff).mul(-2*t*t*t + 3*t*t))
                .add(temp.set(m1).mul(t*t*t - t*t).mul(factor));
    }
}
