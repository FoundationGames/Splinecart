# Changelog

## 0.4.0+26.1.2

- Added regularly spaced wooden sleepers to horizontal spline rails.
- Added oriented steel cross-links and rail clamps to non-horizontal spline rails.
- Added server-authoritative placement for all vanilla minecart items on straight,
  horizontal spline sections, including centerline snapping and collision checks.

## 0.3.3+26.1.2

- Fixed spline follower network updates snapping every two ticks instead of
  using Minecraft 26.1.2's entity interpolation system.
- Decoupled smooth orientation updates from follower position updates.

## 0.3.2+26.1.2

- Fixed speed-dependent lateral minecart shaking on spline tracks by separating
  render interpolation correction from the passenger's physical track offset.
