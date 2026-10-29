TESTS
npm test: 23 Node tests, no account or network required. Includes verified
engine bootstrap, corrupt cache replacement and offline failure handling.

UI integration: Python 3 with playwright, Chromium at /usr/bin/chromium.
python3 test/renderer.py
It starts its own localhost server, injects a test-only preload substitute,
and checks search display/XSS safety, favorites, actual generated WAV playback,
seek/pause, 24-second generated video midpoint clipping, queue actions,
settings, credits and three window widths. No sample tracks ship in the app.

Real Electron IPC/media protocol:
Start Electron with test/launch-electron.cjs, not the normal application:
  npx electron --remote-debugging-port=9223 test/launch-electron.cjs
  python3 test/electron.py
On Linux an X server is required (e.g. DISPLAY=:99 with a dummy Xorg server).
--no-sandbox was required for this container's Linux launcher; it is not
present in the production Windows package or BrowserWindow configuration.
The harness uses a temporary profile and deletes it on normal exit.
The normal app profile is never intentionally replaced by this harness.

Synthetic fixtures: tone.wav (12 seconds, 440 Hz, mono PCM) and canvas.webm
(24 seconds, colored test frame, no audio). Not real copyrighted songs.

Online service test in this workspace: ytsearch1 request made through the
real engine; network environment returned an access denial. Live search,
stream extraction and downloads need validation on the user's connection.
Windows EXE, resources, installer payload, branding and checksums were checked
on Linux; this workspace does not have a Windows runtime.

JAM INTEGRATION
After starting the isolated Electron harness:
  python3 test/jam-electron.py
Uses two independent browser contexts (390-pixel mobile viewport) against the
actual local Jam HTTP server and real host audio. Checks invitation/code join,
queue ownership, guest transport permissions, admission lock, kick and stop.
Node test/jam.test.cjs also checks search/add, duplicate entry identity, origins,
DNS host validation, body limits, rate limiting and server shutdown.
