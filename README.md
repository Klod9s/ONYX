# ONYX Performance Monitor

Lightweight, real-time frame pacing, telemetry, and battery power overlay for Android.

## Features

- **Comprehensive Performance Metrics**: Measures everything you need for deep analysis, including FPS, frametime graph, 1% & 0.1% lows, and stutter counts.
- **Hardware Telemetry**: Live CPU/GPU load, battery temperature, and power state monitoring to identify throttling.
- **Smart Foreground Refresh**: The overlay and UI only update when actively in the foreground, ensuring zero background battery drain.
- **Shizuku Integration**: Privileged telemetry access without requiring full root (Root also supported).
- **Highly Customizable**: Adjustable scaling, drag-to-position, and multiple accent color themes (Onyx, Ocean, Violet, Crimson, or Custom).

## Requirements

- Android 8.0 (API 26) or higher.
- Android 14 or higher recommended.
- [Shizuku](https://shizuku.rikka.app/) installed and authorized (or a rooted device)

## Installation

1. Download the latest APK from the [Releases](../../releases) page.
2. Install [Shizuku](https://shizuku.rikka.app/) from the Play Store or F-Droid.
3. Start Shizuku and grant it permissions via Wireless Debugging or Root.
4. Open ONYX and grant the necessary overlay and Shizuku permissions.

## Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/Klod9s/onyx.git


## License

Copyright © 2026 Klod9s

ONYX Performance Monitor is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

See the [LICENSE](LICENSE) file for details.
