# AndroDitto

AndroDitto connects an Android phone to [Ditto Clipboard Manager](https://ditto-cp.sourceforge.io/) over your Tailscale network. It transfers text and files between the phone and a Windows PC running Ditto.

## Features

- Send typed or pasted text from Android to the PC.
- Send selected Android text using the **Send to PC** selection action.
- Send one or more files using Android's **Share** menu or the file picker.
- Receive PC text into the Android clipboard, ready to paste in another app.
- Receive files into **Internal storage → Download → Ditto** and share the latest received file with another Android app.
- Browse recent transfers in the History tab.

## Setup

1. Connect both devices to Tailscale.
2. In Ditto on the PC, open **Options → Friends**, add the phone's Tailscale IP, and enable **Send All Copies** if PC clipboard changes should be sent automatically.
3. In AndroDitto, enter the PC's Tailscale IP and Ditto network password.
4. Turn on **Background Receive** to receive clipboard text and files while AndroDitto is in the background.

Android does not provide a general-purpose file clipboard. Files received from the PC are saved in `Download/Ditto`; use **Share Last** to send one to another phone app.

## Build

Requirements: JDK 17 and Android SDK Platform 35.

```sh
./gradlew assembleDebug
```

On Windows, use `gradlew.bat assembleDebug`. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

