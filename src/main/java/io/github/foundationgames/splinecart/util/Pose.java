package io.github.foundationgames.splinecart.util;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public record Pose(Vector3dc translation, Matrix3dc basis) {
    public void interpolate(Pose other, double t, Vector3d translationOut, Matrix3d basisInOut) {
        double factor = 1.41 * this.translation().distance(other.translation());
        interpolate(other, t, factor, translationOut, basisInOut);
    }

    public void interpolate(Pose other, double t, double factor, Vector3d translationOut, Matrix3d basisInOut) {
        var point0 = translationOut.set(this.translation());
        var point1 = new Vector3d(other.translation());

        // Z axis of basis is the gradient (tangent vector)
        var grad0 = new Vector3d(0, 0, 1).mul(this.basis());
        var grad1 = new Vector3d(0, 0, 1).mul(other.basis());

        var gradT = new Vector3d();
        interpolate(t, factor, point0, grad0, point1, grad1, translationOut, gradT);
        gradT.normalize();

        var rot0 = this.basis().getNormalizedRotation(new Quaterniond());
        var rot1 = other.basis().getNormalizedRotation(new Quaterniond());

        var rotT = rot0.nlerp(rot1, t, new Quaterniond()).normalize();
        basisInOut.set(rotT);

        var basisGrad = new Vector3d(0, 0, 1).mul(basisInOut);
        var axis = gradT.cross(basisGrad, new Vector3d()).normalize();

        if (axis.length() > 0) {
            double angleToNewBasis = basisGrad.angleSigned(gradT, axis);
            if (angleToNewBasis > 0) basisInOut.rotate(angleToNewBasis, axis).normal();
        }
    }

    // https://en.wikipedia.org/wiki/Cubic_Hermite_spline#Interpolation_on_a_single_interval
    public static Vector3d interpolate(double t, double factor, Vector3dc p0, Vector3dc m0, Vector3dc p1, Vector3dc m1,
                                       Vector3d pOut, @Nullable Vector3d mOut) {
        var temp = new Vector3d();

        pOut.set(temp.set(p0).mul(2*t*t*t - 3*t*t + 1))
                .add(temp.set(m0).mul((t*t*t - 2*t*t + t) * factor))
                .add(temp.set(p1).mul(-2*t*t*t + 3*t*t))
                .add(temp.set(m1).mul((t*t*t - t*t) * factor));

        if (mOut != null) mOut.set(temp.set(p0).mul(6*t*t - 6*t))
                .add(temp.set(m0).mul((3*t*t - 4*t + 1) * factor))
                .add(temp.set(p1).mul(-6*t*t + 6*t))
                .add(temp.set(m1).mul((3*t*t - 2*t) * factor));

        return pOut;
    }
}
