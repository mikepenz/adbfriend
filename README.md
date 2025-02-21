# ADB Friend

ADB Friend is a tiny cli tool, which helps you to manage your Android device via ADB.
Its features were mostly designed for developers, but it can be useful for everyone.

## Features

- Sync files from your computer to your phone
    - Designed for test data, skipping existing files on device
- Configure device for tests
    - Disable animations, Enable touches, ...
- Uninstall apps by pattern
- Packages command
    - Apply the immersive flag to all packages matching glob, force-stop, clear app data & cache
- Extra tools
    - adb-speed (Helps to identify sub-par cables)

## Usage

ADB Friend is a command line tool, which can be used in a terminal.

```bash
# Get started with the `--help` command, to get information and an overview on the various features offered.
adbfriend --help
```

## Release

The project uses shadow to package the tool in a fat-jar, and also do minimal minification.

```bash
./gradlew adbfriend-cli:shadowDistZip
```

## Other

### AboutLibraries

## Generate `aboutlibraries.json` for `adbfriend`

```bash
./gradlew adbfriend:exportLibraryDefinitions -PaboutLibraries.exportPath=src/jvmMain/composeResources/files/
```

## Generate `aboutlibraries.json` for `adbfriend-cli`

```bash
./gradlew adbfriend-cli:exportLibraryDefinitions -PaboutLibraries.exportPath=src/main/composeResources/files/
```

### License

```
Copyright 2025 Mike Penz
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
