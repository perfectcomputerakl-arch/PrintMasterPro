# PRINT MASTER PRO

Android universal printing app starter project. Package: `com.perfectcomputer.printmasterpro`.

## Current starter features
- Select PDF/Word/Excel/text documents and open them in a compatible Android viewer for printing.
- Select and print photos through Android Print Framework.
- Entry point to Android system print settings for compatible inkjet/laser/network printers.
- Basic ESC/POS text printing to already-paired Bluetooth thermal printers.
- Basic ESC/POS text printing to Wi-Fi thermal printers by IP and TCP port (default 9100).
- Branded app name and generated logo asset.

## Build
Open this folder in Android Studio (JDK 17, Android SDK 35), allow Gradle sync, then Build > Build APK(s).

## GitHub Actions
The workflow at `.github/workflows/android-build.yml` builds a debug APK on push and pull request and uploads it as an artifact.

## Important compatibility notes
"Universal" does not mean every printer model uses the same protocol. Regular inkjet/laser printers are accessed through Android Print Framework and require a compatible OEM/Mopria/IPP print service. The thermal implementation currently sends basic ESC/POS text over Bluetooth Classic SPP or Wi-Fi TCP (commonly port 9100). Bluetooth BLE-only devices, USB OTG thermal devices, proprietary protocols, receipt images, barcodes, cash-drawer pulses, cutter commands, paper width and code-page configuration require device-specific implementation and real-hardware testing. Do not advertise all models as supported until tested.

## Reference app
The user-provided reference APK is NokoPrint 4.2.0 (`com.nokoprint`), distributed as an APKM bundle. This project is an original implementation and does not copy its code or assets.
