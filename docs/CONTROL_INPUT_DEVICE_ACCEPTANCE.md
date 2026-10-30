# Control cancellation and touch cleanup device acceptance

Date: 2026-10-30. Device: Android 15, arm64-v8a.

The manual CI acceptance build at commit `437af53` contains the production fixes
from `3861a99`. Its application ID is
`io.github.lejw0925.virtualscreen.acceptance`, alongside the existing formally
signed `io.github.lejw0925.virtualscreen` 0.2.0 installation. The original app
was not replaced, uninstalled, cleared, started or reconfigured.

Build: https://github.com/whycantiusemyname/android-virtual-screen/actions/runs/41004771515

The acceptance workflow converts the existing JVM regression classes to
AndroidJUnit4 instrumentation classes without changing their assertions.
Only these two classes were selected:

- `SessionManagerTest`: 11 passed, including the concurrent old-owner
  cancellation/controller handoff regression and observation invalidation.
- `backend.RootMotionStreamTest`: 6 passed, including rejected/throwing UP,
  rejected CANCEL/POINTER_UP, cleanup after an already dispatched POINTER_UP,
  and successful release. Failure cases also verify the next gesture works.

Runner result: `OK (17 tests)`, `INSTRUMENTATION_CODE: -1`; no failures or skips.
These are real-device Android runtime regressions with a fake backend and
injected dispatch outcomes. They do not claim real InputManager dispatch or
root/Shizuku display end-to-end coverage. No virtual display or user-app input
was created by this acceptance run.

The separate experimental probe authentication change was also exercised with
a bounded app_process socket test using its production ProbeAccess class:
kernel peer UID 0 admitted, UID/GID 2000 rejected, server exit 0 and listener
closure verified. The full paused probe was not started.

The independent acceptance app and instrumentation package remain installed
for review. The original installation still reports version 0.2.0; no original
app process, owned virtual display or test socket listener remained afterward.
