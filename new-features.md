# Features to implement

- [x] Host device (where the MCP server is run) allowed paths, which can host apks or similar
- [x] Install APK on the device, provided path on host system (within host system allowed path)
- [x] Capture screenshot and move to host
- [ ] Read logcat from the device, with various `adb logcat` related functionalities like filtering, x amount of log lines, ...
  - [ ] Find logs associated with a specific package name
- [ ] Tool to extract stacktraces and/or backtraces or other crashes from a log
- [x] Update `get-installed-packages` adding a flag to only get third party applications
