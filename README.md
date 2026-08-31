# Steam Controller for Google TV

A native background service that bridges the Valve Steam Controller (SC2026 / BLE) to Android TV and Google TV devices as a full virtual Xbox 360 gamepad.

## Why this exists

Android TV does not natively support the Steam Controller over Bluetooth Low Energy. Without a dedicated driver, the controller remains locked in "Lizard Mode" (acting as a basic mouse cursor and keyboard) rather than a system-wide gamepad.

This app solves that by:
1. Connecting directly to the Steam Controller's proprietary Valve GATT BLE service.
2. Unlocking raw gamepad reports (`0x85`) and running a lightweight keepalive heartbeat to prevent the controller from dropping back into Lizard Mode.
3. Injecting native Linux kernel input events through a compiled native `uinput` daemon listening locally on `127.0.0.1:4455`.

Because the inputs are injected directly into `/dev/uinput` as a virtual Microsoft Xbox 360 controller, games and the Google TV interface recognize it as a real physical controller across the entire OS.

## Requirements

- Google TV Streamer, Chromecast with Google TV, or any Android TV device (Android 10+).
- Developer Options and Wireless Debugging enabled on your TV.
- Steam Controller (2026) in BLE mode.

## Setup

1. Install the APK from the Releases page.
2. Pair your Steam Controller to your Google TV under Settings > Remotes & Accessories.
3. Enable Wireless Debugging on your TV (Settings > System > Developer Options > Wireless Debugging).
4. Open the app, pair with your local Wireless Debugging port, and click **Start Gamepad Service**.

## Built-in Web Tester

The app runs a local status server on port 8080. You can view real-time button presses and stick inputs from any phone or computer on your network:

```
http://<your-tv-ip>:8080
```

## Architecture

- **Driver & Backend**: Custom Kotlin BLE GATT driver, sequential CCCD subscription, keepalive loop, and native Linux kernel uinput daemon pipeline.
- **UI & Presentation**: Android TV Compose visualizer, pairing screens, and documentation written with AI assistance.
- **Input Engine**: Configurable profile system with radial deadzones, trackball inertia, and capacitive grip touch isolation.

## Credits & Acknowledgements

- Backend architecture, BLE driver, and Linux uinput integration built by me (Adadeane).
- TV user interface and documentation assisted made fully with antigravity because I suck at ui and writing.
- Bluetooth Low Energy protocol research and GATT characteristic tables reference the [SteamlessController](https://github.com/ynsta/steamcontroller) and SteamController-Android open source projects.

## License

MIT
