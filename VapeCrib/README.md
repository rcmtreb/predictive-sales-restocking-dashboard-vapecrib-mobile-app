# VapeCrib

VapeCrib is the mobile version of a web-based app, developed as an Android application. This repository contains the source code, build scripts, and configuration files for the app.

## Tech Stack
- **Languages:** Kotlin, Java
- **Build System:** Gradle (Kotlin DSL)
- **Frameworks/Libraries:**
  - Android SDK
  - AndroidX
  - Data Binding
  - Navigation Component
- **Tools:**
  - Android Studio
  - JDK 8+
  - Gradle Wrapper
  - Visual Studio Code

## Features
- Modular Android app structure
- Gradle Kotlin DSL build configuration
- Data binding and navigation components
- Organized source directories for main, test, and androidTest

## Project Structure
```
VapeCrib/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── test/
│   │   └── androidTest/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
└── ...
```

## Getting Started

### Prerequisites
- Android Studio (latest recommended)
- Java Development Kit (JDK) 8+
- Gradle (wrapper included)

### Build & Run
1. Clone the repository:
   ```sh
   git clone <repo-url>
   ```
2. Open the project in Android Studio.
3. Let Gradle sync and build the project.
4. Run the app on an emulator or physical device.

### Useful Commands
- Build the project:
  ```sh
  ./gradlew build
  ```
- Clean the project:
  ```sh
  ./gradlew clean
  ```

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## License
This project is licensed under the MIT License.

## Contact
For questions or support, please contact the repository owner.
