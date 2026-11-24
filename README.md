# ONYX Performance Monitor

Lightweight, real-time frame pacing, telemetry, and battery power overlay for Android.

## Features

- **Comprehensive Performance Metrics**: Measures everything you need for deep analysis, including FPS, frametime graph, 1% & 0.1% lows, and stutter counts.
- **Hardware Telemetry**: Live CPU/GPU load, battery temperature, and power state monitoring to identify throttling.
- **Smart Foreground Refresh**: The overlay and UI only update when actively in the foreground, no polling when idle; sampling only while recording or the overlay is visible.
- **Shizuku Integration**: Privileged telemetry access without requiring full root (Root also supported).
- **Highly Customizable**: Adjustable scaling, drag-to-position, and multiple accent color themes (Onyx, Ocean, Violet, Crimson, or Custom).

## Requirements

- Android 8.0 (API 26) or higher.
- Android 14 or higher recommended.
- [Shizuku](https://shizuku.rikka.app/) installed and authorized (or a rooted device)

## Installation

ONYX supports two installation methods: **Shizuku** and **Root**.

### Method 1: Shizuku
1. Install and set up [Shizuku](https://shizuku.rikka.app/).
2. Start the Shizuku service.
3. Open ONYX.
4. Select **Shizuku** as the access method.
5. Grant ONYX permission when prompted.
6. Done! ONYX is ready to use.

### Method 2: Root
1. Install ONYX.
2. Open ONYX and grant it root access when prompted by your root manager (e.g., Magisk).
3. Done! ONYX is ready to use.

## Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/Klod9s/onyx.git


## License

Copyright © 2026 Klod9s

ONYX Performance Monitor is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

See the [LICENSE](LICENSE) file for details.
