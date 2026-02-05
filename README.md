# SimpleShell-Mobile

[中文版](README_zh.md)

A simple and elegant SSH/SFTP client for Android.

## Features

### Connection Management
- Create, edit, and delete SSH connections
- Support for password and private key authentication
- Encrypted local storage of credentials
- Connection grouping and organization
- Import connections from SimpleShell PC

### Terminal
- Interactive SSH terminal with real-time I/O
- ANSI color code support
- Pinch-to-zoom font scaling (0.5x - 2.5x)
- Quick keyboard shortcuts panel
- Background connection persistence via foreground service

### SFTP File Browser
- Browse remote file systems
- Create and delete files/folders
- View file details (size, modification date)
- Persistent connection support

### Customization
- Theme modes: System, Light, Dark
- Dynamic color support (Android 12+)
- Custom theme colors
- Multi-language: English, 简体中文

## Requirements

- Android 8.0 (API 26) or higher

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Hilt DI
- **Database**: Room
- **SSH Library**: SSHJ
- **Cryptography**: BouncyCastle

## Building

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 35

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/SimpleShellMobile.git
   ```

2. Open the project in Android Studio

3. Sync Gradle and build:
   ```bash
   ./gradlew assembleDebug
   ```

4. Install on device:
   ```bash
   ./gradlew installDebug
   ```

## Project Structure

```
app/src/main/java/com/example/simpleshell/
├── data/                 # Data layer
│   ├── importing/        # PC config import
│   ├── local/            # Local data sources
│   │   ├── database/     # Room database
│   │   └── preferences/  # DataStore
│   ├── remote/           # Remote data (updates)
│   └── repository/       # Repositories
├── di/                   # Hilt modules
├── domain/model/         # Domain models
├── service/              # Android services
├── ssh/                  # SSH management
└── ui/                   # Presentation layer
    ├── navigation/       # Navigation
    ├── screens/          # UI screens
    ├── theme/            # Theming
    └── util/             # UI utilities
```

## License

Apache License Version 2.0

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
