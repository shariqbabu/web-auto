# Replit Auto Runner - Android App (APK)

Ultra-lightweight Android App to keep Replit workspaces (like OmniRoute server) running 24/7.

## Key Features
- **Customizable Run Button (`▶ Custom Run`):** Direct manual trigger button + customizable CSS selector (defaults to `[action="run_button_used"]`).
- **24/7 Foreground Service:** Keeps running smoothly in the background even when your phone screen is locked.
- **Persistent Session & Login:** Login to Replit once in the app WebView; all cookies and sessions remain permanently saved.
- **No Cloudflare Blocks:** Android runs on your mobile/home residential IP (Jio/Airtel/Wi-Fi), completely immune to datacenter bot blocks!
- **Zero Configuration:** Pre-configured for `https://replit.com/@shrqbabu/Gemini-Hub`.

---

## How to Build the APK on GitHub Actions (1-Click)

1. Create a new repository on GitHub (e.g. `replit-android-app`).
2. Push this entire folder to your repository:
   ```bash
   git init
   git add .
   git commit -m "Initial commit for Android Auto Runner"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/replit-android-app.git
   git push -u origin main
   ```
3. Once pushed, go to the **Actions** tab on your GitHub repository.
4. You will see the **Build Android APK** workflow running automatically.
5. In ~2-3 minutes, the build will finish. Click on the completed run and scroll down to **Artifacts** to download your **`Replit-Auto-Runner-APK.zip`**!
6. Extract the zip and install `app-debug.apk` on your Android phone.

---

## App Controls
- **▶ Custom Run:** Manually forces an instant search and click on the run button inside the Replit workspace.
- **⚙ Config:** Change the Target URL, custom selector, or click heartbeat interval (default: 8s).
- **24/7 Run Switch:** Toggle background persistent service on/off.
