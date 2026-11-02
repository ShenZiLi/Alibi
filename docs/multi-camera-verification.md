# Multi-camera verification

Verified on 2026-11-02 using the connected RMX5010 device and a separately
installed debug build. The existing release installation was not replaced.

Camera selection now uses the opaque Camera2 ID, independently of lens facing.
The preparation preview, recording service, and small-window preview all use
the selected camera. Lens names are inferred from reported focal length and
sensor size; the camera ID and approximate 35 mm equivalent remain visible.

The device exposes its main camera both as logical camera 0 and physical camera
2. The list retains camera 0 and hides the duplicate physical main sensor after
checking the reported parent/child relationship and matching field of view.
Independent cameras and the ultra-wide/telephoto children remain available.

| Camera ID | Display | Device result |
| --- | --- | --- |
| 0 | Rear main, approximately 25 mm | Live preview and recorded video passed |
| 1 | Front, approximately 25 mm | Listed with correct facing |
| 2 | Duplicate physical main | Hidden from selection |
| 3 | Rear ultra-wide, approximately 17 mm | Live preview and recorded video passed |
| 4 | Rear telephoto, approximately 76 mm | Live preview and recorded video passed |

Validation: debug and instrumentation APK builds passed; four local unit tests
and four device tests passed. The lens device test verifies the selected Camera2
ID, a streaming preview, and a finalized video longer than one second without
recording errors for every listed rear camera. The duplicate-main regression
test failed with `0 and [2]` before the fix and passed afterward. UI inspection
confirmed one main, one ultra-wide, one telephoto, and one front camera entry.

The user also confirmed small-window operation and ultra-wide/telephoto operation
on the device. Test video files are removed after the recording checks.
