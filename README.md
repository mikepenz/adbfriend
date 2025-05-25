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

## Install

The installation can be performed using Homebrew. First you'll require the custom tap:

```bash
brew tap mikepenz/tap
```

Next, install the AdbFriend CLI:

```bash
brew install mikepenz/tap/adbfriend
```

Alternatively, you can download prebuild binaries for release page.

## Usage

ADB Friend is a command line tool, which can be used in a terminal.

```bash
# Get started with the `--help` command, to get information and an overview on the various features offered.
adbfriend --help
```

## MCP Server

ADB Friend (Since version 1.4.0) also provides
a [Model Context Protocol (MCP) Server](https://modelcontextprotocol.io/introduction), that can be configured in popular
AI Tools
like [Claude Desktop](https://modelcontextprotocol.io/quickstart/user), [GitHub Copilot](https://docs.github.com/en/copilot/customizing-copilot/extending-copilot-chat-with-mcp), [RayCast](https://manual.raycast.com/model-context-protocol), ...

### Configuration

The location and details of the configuration file will depend on the used tool. Below example
for [Claude Desktop](https://modelcontextprotocol.io/quickstart/user#2-add-the-filesystem-mcp-server).

```json
{
  "mcpServers": {
    "adb-friend": {
      "command": "/opt/homebrew/bin/adbfriend",
      "args": [
        "mcp",
        "server",
        "--sse",
        "true"
      ]
    }
  }
}
```

### Supported tools

- get-connected-devices
- get-installed-packages
- check-adb-speed
- clear-installed-package
- set-immersive-full-for-package
- force-stop-process
- uninstall-package

With many more tools planned for the future.

### Example Prompts

```text
uninstall the 'sample' app, but keep its data
```

```text
force stop the 'sample' app
```

```text
Do I have a browser installed on the connected emulator?
```

![claude-desktop-sample-prompt](.github/readme/claude-desktop-sample-prompt.png)

## Release

The project uses shadow to package the tool in a fat-jar, and also do minimal minification.

```bash
./gradlew adbfriend-cli:shadowDistZip
```

## Other

### AboutLibraries

## Generate `aboutlibraries.json` for `adbfriend`

```bash
./gradlew adbfriend:exportLibraryDefinitions
```

## Generate `aboutlibraries.json` for `adbfriend-cli`

```bash
./gradlew adbfriend-cli:exportLibraryDefinitions
```

### Credits

This project uses the amazing [adam](https://github.com/Malinskiy/adam) library
from [Malinskiy](https://github.com/Malinskiy) to interact with `adb`.
The CLI is set-up using the impressive [clikt](https://github.com/ajalt/clikt) library
from [ajalt](https://github.com/ajalt/).

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
