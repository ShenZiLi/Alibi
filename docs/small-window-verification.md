# Recording small window

The video recording screen offers **Small window** using a custom floating
window. The first use requests permission to display over other apps. It displays
the running recorder's camera stream without a toolbar or close button.
Double-tapping the window closes only the preview; recording continues.
The button is disabled while the window is visible, until the camera is ready, while recording is paused, or
when the camera cannot provide preview alongside the selected recording quality.

## Device checks

Grant camera permission and the permission to display over other apps.

The window starts at 104 dp wide. Touch and drag to move it without waiting for a
long press. The centered rectangle covering 80% of the window's area always moves
the window, taking priority over resizing. The gesture mode is fixed at touch-down.
Drag the bottom-right edge to resize it, keeping the camera image's aspect ratio.
The resize target is invisible and extends up to 48 dp inward from each edge of
the bottom-right corner, capped at half the window's width and height and excluding
the central movement area. There is no separate handle. The selected width
is remembered across openings. Dragging can reduce the width to 72 dp; maximum
size and position are constrained to the available screen area.

The latest gesture and UI changes were built successfully and installed on the
connected RMX5010 device. Functional checks are left to the user, as requested.
The following checks are for the user's verification.

1. Start a video recording. Tap **Small window** and confirm that a live camera
   image appears above the home screen or another application.
2. Move the camera, touch and immediately drag from different points in the central
   80% area (including near its corners), and drag the bottom-right edge to resize.
   Confirm that the picture updates,
   has the correct orientation, and is not stretched. Repeat with the front camera
   and with the phone initially in landscape orientation.
3. Confirm that no toolbar, close button, or resize handle is visible. Reopen Alibi
   and confirm that its recording controls and timer continue to work.
   Double-tap the window and confirm that it closes while recording continues.
   Reopen it using **Small window**. Confirm that a single tap, dragging,
   and resizing do not close it, including when a drag follows a tap.
4. Save the recording and confirm that the window disappears. Start another
   recording, open the small window, and confirm that the chosen width is remembered.
   Check that the saved video includes footage from the small-window period.
5. Leave the window open across at least two recording intervals, then save.
   Confirm that the saved video contains all intervals.
6. Pause recording. Confirm that opening a new small window is disabled. Resume
   recording and confirm that it becomes available again.
7. Disable Alibi's display-over-other-apps permission in system settings. Tap the
   button and confirm that the permission page opens and recording continues.
   Grant permission and return: the window should appear automatically.
8. Check an audio-only recording: it should not offer the small-window button.
9. Save or delete the recording and confirm that its floating window closes.

`FloatingVideoPreviewTest` covers the required permission declaration. It requires
a connected Android device.
