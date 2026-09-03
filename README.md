# battery-meter

A simple battery meter drawn around your device's hole-punch camera.

battery-meter is a lightweight Android overlay that displays your battery level as a thin ring (or arc) surrounding the hole-punch/front-camera cutout. It gives a clean, always-visible battery indicator without taking extra screen space or relying on the status bar.

## Features
- Minimal, unobtrusive battery ring around the hole-punch camera
- Percentage and charging state indicator
- Customizable colors (idle / charging / low)
- Adjustable thickness and size to fit different camera cutout sizes
- Low-battery visual warning
- Works in portrait and landscape orientations
- Lightweight and battery-friendly (small memory and CPU footprint)
- Optional Bluetooth device support — display battery status for paired/connected Bluetooth devices (e.g., earbuds, headphones) when available
- Visibility & behavior settings — control when and where the overlay is shown (always or hide in fullscreen apps)
- Open-source — inspect and modify to suit your needs

## Why use this app?
- Clean visibility: See battery level at a glance without pulling down the status bar or adding a status bar icon.
- Designed for modern phones: Turns the hole-punch camera area into a useful HUD element instead of wasted space.
- Non-intrusive: The overlay is subtle and keeps your normal UI uncluttered.
- Helpful for accessories: With Bluetooth device support you can optionally surface battery info for connected accessories (headphones/earbuds) alongside your phone battery.
- Customizable visibility: Configure when the overlay appears so it doesn't interfere with fullscreen apps, videos, or presentations.
- Privacy-friendly: No account, no telemetry — the app only uses local system info (battery levels, charging state, and optionally connected Bluetooth device info).

## How it works (high level)
- The app draws an always-on overlay anchored to the screen area around the hole-punch camera.
- It listens to Android battery broadcasts (battery level and charging status) and updates the ring's color/length in real-time.
- If Bluetooth device support is enabled, the app queries the battery level of supported connected Bluetooth accessories and can optionally show that device's battery instead of (or in addition to) the phone's battery.
- Visibility settings let you select automatic rules (always on, only when charging, only on home/lock screen, hide in fullscreen apps, schedule by time or battery level, or show only when specific Bluetooth devices are connected).
- The overlay is implemented as a lightweight view so performance and power impact are minimal.

## Installation / Build
1. Clone the repository:
   git clone https://github.com/RemagOfficial/battery-meter.git
2. Open the project in Android Studio.
3. Build and run on a device (Android 8.0+ recommended).
4. Alternatively, if prebuilt APKs are provided in Releases, sideload the APK to your device.

Note: The app requires the "Display over other apps" permission so it can render the overlay. If you enable Bluetooth device support, the app may also request Bluetooth-related permissions to detect and read battery information from connected devices.

## Usage
- Grant the overlay (draw over other apps) permission when prompted.
- Optionally enable Bluetooth device support in Settings to allow the app to surface accessory battery levels (you may need to grant Bluetooth/connect permissions on newer Android releases).
- Configure colors, thickness, and position to match your device and taste.
- Configure visibility rules:
  - Always show / show only on home/lock screen
  - Hide during fullscreen apps (videos, games, presentations)
- Toggle the overlay on/off from the main screen.

## Privacy & Permissions
- Uses only system APIs to read battery level and charging status.
- Bluetooth device support uses standard Bluetooth APIs to read connected device info when enabled by the user.
- Does not collect or transmit personal data.
- Required permissions:
  - Draw over other apps (SYSTEM_ALERT_WINDOW / display over other apps)
  - Optional Bluetooth permissions when using Bluetooth device features (may vary by Android version)

## Contributing
Contributions, bug reports, and suggestions are welcome. Please open an issue or submit a pull request with a clear description and reproduction steps.

## License
Add your preferred license here (MIT/Apache-2.0/etc.). If you don't have a license yet, consider adding one so others can safely contribute.

---
If you'd like, I can commit this README.md to the repository (RemagOfficial/battery-meter). Say "commit" to proceed and I will add the file to the repo's default branch, or tell me which branch you'd prefer.
