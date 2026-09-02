# Redesign Wear OS Now Playing Screen

Redesign the main player interface to follow Material Design 3 guidelines for Wear OS, featuring a minimalist centered layout and rotary input support for track navigation.

## Proposed Changes

### [Component Name] Wear UI

#### [MODIFY] [WearPlayer.kt](file:///C:/Users/ramir/AndroidStudioProjects/Metrolist/wear/src/main/kotlin/com/metrolist/music/ui/player/WearPlayer.kt)
- Add `FocusRequester` to handle rotary input.
- Use `onRotaryScrollEvent` to trigger `seekToNext()` on clockwise rotation and `seekToPrevious()` on counter-clockwise rotation.
- Implement a state for current playback position, updated via polling the `ExoPlayer`.
- Redesign `NowPlayingScreen` layout:
    - Vertical stack:
        1. Song Title (Bold, Marquee).
        2. Artist name (Smaller font, secondary color).
        3. Subtle `CircularProgressIndicator` (showing current track progress).
        4. Horizontal Row of Controls: Previous, Play/Pause, Next.
- Optimize spacing and touch targets for circular Wear OS screens.
- Keep background artwork with low opacity and blur for aesthetics.

## Verification Plan

### Manual Verification
- Deploy to a Wear OS emulator or device.
- Verify the layout is centered and elements are in the requested order.
- Test rotary input (mouse wheel in emulator or physical crown) to ensure it skips tracks correctly.
- Verify the progress indicator updates in real-time.
- Check that play/pause and metadata work as expected.
