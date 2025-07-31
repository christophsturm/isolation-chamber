# Amper Build Tool - Technical Reference

## Amper Commands

```bash
./amper init       # Create new project
./amper build      # Build all modules
./amper test       # Run tests
./amper run        # Run application
./amper clean      # Clean build outputs
./amper package    # Create distribution package
./amper publish    # Publish to repository
./amper update     # Update Amper to latest version
```

### Command Options

- `--root <path>` - Specify project root directory
- `--log-level <level>` - Set logging (debug, info, warn, error, off)
- `-v, --variant <variant>` - Build variant (debug, release)
- `-p, --platform <platform>` - Target platform
- `-m, --module <module>` - Specific module

## module.yaml Format

```yaml
# Product type (required)
product: jvm/app
# or
product:
  type: lib
  platforms: [jvm, android, iosArm64]

# Dependencies
dependencies:
  - org.jetbrains.kotlin:kotlin-stdlib:1.9.0     # Maven dependency
  - ../shared                                    # Module dependency
  - $compose.material3                           # Catalog dependency
  - io.ktor:ktor-client:2.3.0: exported          # Exported dependency
  - org.postgresql:postgresql:42.3.3: runtime-only # Runtime dependency
  - bom: io.ktor:ktor-bom:2.3.0                 # BOM import

# Platform-specific dependencies
dependencies@android:
  - androidx.activity:activity-compose:1.7.2

# Settings
settings:
  kotlin:
    languageVersion: 1.9
    serialization: json
    ksp:
      processors:
        - androidx.room:room-compiler:2.7.0
  compose: enabled
  android:
    namespace: com.example.app
    targetSdk: 35

# Apply templates
apply:
  - ../common.module-template.yaml

# Test dependencies
test-dependencies:
  - io.mockk:mockk:1.13.10

# Test settings
test-settings:
  kotlin:
    languageVersion: 2.0
```

## project.yaml Format

```yaml
modules:
  - ./app
  - ./lib
  - ./plugins/*  # Glob patterns supported
```

## Product Types

- `lib` - Library for any platform
- `jvm/app` - JVM application
- `android/app` - Android application
- `ios/app` - iOS application
- `linux/app` - Native Linux application
- `windows/app` - Native Windows application
- `macos/app` - Native macOS application

## Dependency Formats

```yaml
# Maven dependency
- group:artifact:version

# Module dependency (starts with ./ or ../)
- ../shared

# Catalog dependency
- $catalog.entry

# With attributes
- dependency: exported
- dependency: compile-only
- dependency: runtime-only

# Full form
- dependency:
    scope: compile-only
    exported: true

# BOM
- bom: group:artifact:version
```

## Platform Qualifiers

Use `@platform` suffix for platform-specific configuration:

```yaml
dependencies@android:
  - androidx.core:core-ktx:1.9.0

settings@ios:
  kotlin:
    languageVersion: 1.9
```

Source folders:
```
|-src/           # Common code
|-src@android/   # Android-specific
|-src@ios/       # iOS-specific
|-test/          # Common tests
|-test@android/  # Android tests
```

## Key Settings

### Kotlin
```yaml
settings:
  kotlin:
    languageVersion: 1.9
    apiVersion: 1.9
    allWarningsAsErrors: true
    suppressWarnings: false
    optIns: [kotlin.ExperimentalStdlibApi]
    freeCompilerArgs: [-Xcontext-receivers]
    serialization: json
    ksp:
      processors: [processor.coordinates]
      processorOptions:
        option: value
```

### JVM
```yaml
settings:
  jvm:
    release: 17
    mainClass: com.example.MainKt
```

### Android
```yaml
settings:
  android:
    namespace: com.example
    applicationId: com.example.app
    compileSdk: 35
    targetSdk: 35
    minSdk: 21
    signing:
      enabled: true
    parcelize: enabled
```

### Frameworks
```yaml
settings:
  compose: enabled
  springBoot: enabled
  ktor: enabled
  lombok: enabled
  junit: junit-5  # or junit-4, none
```

## Repository Configuration

```yaml
repositories:
  - https://jitpack.io
  - url: https://my.repo/
    credentials:
      file: ../local.properties
      usernameKey: username
      passwordKey: password
```

## Templates

Create `name.module-template.yaml`:
```yaml
settings:
  kotlin:
    languageVersion: 1.9
test-dependencies:
  - org.jetbrains.kotlin:kotlin-test
```

Apply in module.yaml:
```yaml
apply:
  - ../common.module-template.yaml
```

## Directory Structure

```
project-root/
├── module.yaml          # Single module project
├── src/                 # Source code
├── test/               # Tests
├── resources/          # Resources
├── amper               # Amper wrapper script
└── amper.bat           # Windows wrapper

# Multi-module:
├── project.yaml        # Lists modules
├── app/
│   ├── module.yaml
│   └── src/
└── lib/
    ├── module.yaml
    └── src/

# Android-specific:
├── src/AndroidManifest.xml
├── res/                # Android resources
├── assets/             # Android assets
├── proguard-rules.pro  # R8 rules
└── google-services.json # Firebase config

# iOS-specific:
├── module.xcodeproj    # Xcode project (required)
└── src/*.swift         # Swift sources
```

## Minimal Examples

### JVM Application
```yaml
product: jvm/app
```

### Android Application
```yaml
product: android/app
settings:
  android:
    namespace: com.example
```

### Multiplatform Library
```yaml
product:
  type: lib
  platforms: [jvm, android]
```

### With Dependencies
```yaml
product: jvm/app
dependencies:
  - org.jetbrains.kotlin:kotlin-stdlib
```

## Build Outputs

- Build artifacts: `.amper/build/`
- Caches: `~/.amper/` (user home)
- Logs: `.amper/build/logs/`
- Generated Gradle files: `.amper/`

## Common Issues

1. **Module not found**: Ensure path in project.yaml starts with `./` or `../`
2. **Dependency not resolved**: Check repository configuration
3. **Platform not supported**: Verify platform name matches exactly
4. **Xcode project missing**: Required for ios/app, created on first build
5. **Android namespace required**: Must set `settings.android.namespace`

## Platform Names

**Exact names to use:**
- `jvm`
- `android`
- `iosArm64`, `iosSimulatorArm64`, `iosX64`
- `macosArm64`, `macosX64`
- `linuxX64`, `linuxArm64`
- `mingwX64`

**Platform families (for @qualifier):**
- `ios` (all iOS platforms)
- `macos` (all macOS platforms)
- `apple` (iOS + macOS)
- `native` (all native platforms)

## Environment Variables

- `AMPER_WRAPPER_PATH` - Path to amper wrapper (set in Xcode)
- Standard Java/Gradle env vars apply

## Quick Debugging

```bash
# See what Amper is doing
./amper build --log-level debug

# Check generated Gradle files
ls -la .amper/

# Force clean rebuild
./amper clean
./amper clean-shared-caches
./amper build
```