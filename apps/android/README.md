# SLEEK Android

Native Android client for the SLEEK messaging platform.
Built with Kotlin + Jetpack Compose.

## Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **DI**: Hilt
- **Network**: Retrofit + OkHttp + Socket.IO
- **Local storage**: Room + DataStore
- **Images**: Coil

## Opening in Android Studio
1. Open **Android Studio Panda 2025.3.4**
2. `File → Open` → select `d:\SLEEK\apps\android`
3. Wait for Gradle sync (~2 min first time)
4. Run on emulator or physical device (API 26+)

## Adding Inter Font (optional, recommended)
1. Go to https://fonts.google.com/specimen/Inter → Download family
2. Rename and copy into `app/src/main/res/font/`:
   - `inter_regular.ttf`
   - `inter_medium.ttf`
   - `inter_semibold.ttf`
   - `inter_bold.ttf`
3. In `ui/theme/Type.kt`, replace `FontFamily.SansSerif` with `InterFamily`
   (uncomment the FontFamily block at the top of the file)

## Backend
- REST API: `https://sleek.up.railway.app/api`
- Socket.IO: `https://sleek.up.railway.app`

## Build APK
```
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture
```
ui/
  auth/        LoginScreen, RegisterScreen, AuthViewModel
  chatlist/    ChatListScreen, ChatListViewModel
  chat/        ChatScreen, ChatViewModel
  navigation/  NavGraph, Screen
  theme/       Color, Type, Shape, Theme
data/
  model/       User, Chat, Message
  remote/      ApiService, NetworkClient, SocketManager
  local/       TokenDataStore
di/            NetworkModule
```
