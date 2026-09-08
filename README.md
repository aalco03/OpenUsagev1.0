# OpenUsage v1.0

## Overview

OpenUsage was created on top of coding architecture provided by the Human Screenome Project at Stanford University, as a project of the Stanford Digital Economy Lab. The purpose of the application is to gather data on user behavior, to develop studies related to the economic evaluation of digital goods. 

It runs continuously in the background on a participant's phone, capturing a range of behavioral and device signals, and then uploads that data to Firebase and Google Drive for research analysis. The signals it collects include screenshots, app usage, on-screen interactions (taps), location, physical activity, battery state, power events, network connectivity, and device specifications.

In more technical terms, the project is organized as a multi-module Gradle build (see `settings.gradle`) split into three tiers. The `app` tier is the only application module and owns the user interface, permission onboarding, the always-on background services, alarm scheduling, and the data-upload pipeline; it depends on every other module. The core tier consists of the `c_*` library modules, which provide shared functionality: `c_SharedResources` holds common resources and utilities, `c_DatabaseManager` handles persistence, Firebase settings, login preferences, event data models, and content-policy scanning, and `c_ModuleManager` acts as the orchestration layer that all data modules depend on. Finally, the data-collection tier is made up of the `m01` through `m09` modules, each a self-contained library responsible for capturing one specific signal. Although this version of the application has been heavily tailored to Stanford DEL's research motives, the application has preserved the majority of the architecture provided by the Screenomics team. We thank them for providing guidance on the application's development.

## Module Layout

### Application tier

- `app` — The only `com.android.application` module (everything else is an Android library). Owns the UI (activities and permission onboarding), always-on foreground services, alarms, the data-upload pipeline, and overall app lifecycle. Its entry point is `ScreenomicsApplication`, and the launcher activity is `EntryActivity`.

### Core / shared tier

- `c_SharedResources` — Common resources and utilities shared across all modules.
- `c_DatabaseManager` — Persistence, Firebase settings, login and preference storage, event data models (split into `TextBasedEventData` and `NonTextBasedEventData`), and a content-policy scanner (for example `StructuredDataScanner`).
- `c_ModuleManager` — The orchestration layer. Every `m*` module depends on it. Two classes are central here: `ModuleController`, which holds global on/off switches for each module, and `ModuleCharacteristics`, a singleton that defines the metadata and schema for every event type the app emits.

### Data-collection tier

| Module | Signal captured |
|--------|-----------------|
| `m01_Screenshots` | Screenshots |
| `m02_Apps` | Foreground app and screen on/off |
| `m03_Interactions` | On-screen interactions (taps) |
| `m04_Locations` | GPS location |
| `m05_Activites` | Physical activity / step count |
| `m06_Battery` | Battery state |
| `m07_Power` | System power and screen on/off events |
| `m08_Network` | Network connectivity and Wi-Fi state |
| `m09_Specs` | Device specifications (always enabled) |

## Data Flow

The data-collection modules register their capture logic as `BroadcastReceiver`s and `Service`s declared in the app's `AndroidManifest.xml`, even though the implementing classes live in the library modules. At a high level, the flow is:

```
System events / timers  ->  m* capture classes  ->  event objects (c_DatabaseManager models)
   ->  DataPipeline (DataPipelineManager, DataExporter, GoogleDriveUploader)
   ->  Firebase / Google Drive
```

Continuous collection is driven by foreground services (`ScreenMonitorService`, `CaptureUploadService`), an accessibility service (`ScreenomicsAccessService`), a notification listener service (`SeekForNotification`), `AlarmManager`-based scheduling, and boot-time auto-start (`AutostartService`).
