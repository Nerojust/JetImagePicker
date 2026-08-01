# 📸 JetImagePicker

[![CI](https://github.com/nerojust/JetImagePicker/actions/workflows/android-ci.yml/badge.svg)](https://github.com/nerojust/JetImagePicker/actions/workflows/android-ci.yml)
[![JitPack](https://jitpack.io/v/Nerojust/JetImagePicker.svg)](https://jitpack.io/#Nerojust/JetImagePicker)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A modern, Jetpack Compose-ready image picker library for Android.

---

## Features

1. Works seamlessly with Jetpack Compose, XML + Kotlin, or both.
2. Supports Camera and Gallery.
3. Gallery picking needs **no runtime permission at all** (Android's Photo Picker) — camera capture is the only permission ever requested
4. Supports multiple image selection and compression
5. Provides structured result callbacks for success and error handling
6. Just works – no hidden setup, no ActivityResultContracts, and no more permission nightmares!

---

## 🛠️ Setup

**1. Add the JitPack repository** to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**2. Add the dependency**, using a released tag from the [Releases](https://github.com/Nerojust/JetImagePicker/releases) page (or see the badge above for the latest):

```kotlin
dependencies {
    implementation("com.github.nerojust:JetImagePicker:v1.0.0")
}
```

Or, if you're working inside this repo as a module:

```kotlin
implementation(project(":JetImagePicker"))
```

---

## 🧱 Usage

### 1. Configure your `AndroidManifest.xml`

Gallery picking uses Android's Photo Picker and needs **no storage permission**. Only camera capture needs a runtime permission:

```xml
<uses-permission android:name="android.permission.CAMERA" />

<application>
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.provider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
</application>
```

### 2. Create `file_paths.xml` in `res/xml/`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="images" path="." />
</paths>
```

---

<img width="1080" height="2340" alt="JetImagePicker screenshot" src="https://github.com/user-attachments/assets/c7ed5901-1666-48db-92b2-5cb5153e588c" />

## 🧩 Example

### ✅ Image Picker Screen

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.result.ImagePickerResult
import com.nerojust.jetimagepicker.state.rememberJetImagePickerState
import com.nerojust.jetimagepicker.ui.ImagePreview
import com.nerojust.jetimagepicker.ui.MultiImagePreview

@Composable
fun ImagePickerScreen() {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    val pickerState = rememberJetImagePickerState(
        context = context,
        config = JetImagePickerConfig(
            enableCompression = true,
            compressionQuality = 70,
            allowMultiple = true,
            targetWidth = 1024,
            targetHeight = 1024
        )
    ) { result ->
        when (result) {
            is ImagePickerResult.Success -> message = null
            is ImagePickerResult.PermissionDenied -> {
                message = "Permission denied: ${result.permission}"
                //go ahead, all good
            }
            is ImagePickerResult.PermissionPermanentlyDenied -> {
                message = "Permanently denied: ${result.permission}"
                //do something
            }
            is ImagePickerResult.ShowRationale -> {
                message = "Please allow ${result.permission} to proceed."
                //do some business logic here
            }
        }
    }

    Column(Modifier.padding(16.dp)) {
        Button(onClick = pickerState.pickFromGallery, enabled = !pickerState.isLoading) {
            Text("Pick from Gallery")
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = pickerState.captureWithCamera, enabled = !pickerState.isLoading) {
            Text("Capture with Camera")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = pickerState.clearSelection,
            enabled = pickerState.selectedImageUris.isNotEmpty()
        ) {
            Text("Clear Selection")
        }

        Spacer(Modifier.height(16.dp))

        if (pickerState.isLoading) {
            CircularProgressIndicator()
        }

        when (pickerState.selectedImageUris.size) {
            1 -> ImagePreview(uri = pickerState.selectedImageUris.first())
            in 2..Int.MAX_VALUE -> MultiImagePreview(imageUris = pickerState.selectedImageUris)
        }

        message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
```

> 💡 The `app` module in this repo is a fuller interactive demo — toggle single vs. multiple
> selection and compression on/off live, and see each image's file size update accordingly.

---

## 📦 Configuration Options

```kotlin
JetImagePickerConfig(
    enableCompression = true,
    compressionQuality = 70, // 0–100
    allowMultiple = true,
    targetWidth = 1024,
    targetHeight = 1024
)
```

---

## 📤 Result Handling

Use the `ImagePickerResult` sealed class:

```kotlin
sealed class ImagePickerResult {
    data class Success(val uris: List<Uri>) : ImagePickerResult()
    data class PermissionDenied(val permission: String) : ImagePickerResult()
    data class PermissionPermanentlyDenied(val permission: String) : ImagePickerResult()
    data class ShowRationale(val permission: String) : ImagePickerResult()
}
```

---

## 📋 `JetImagePickerState` Reference

| Member | Description |
|---|---|
| `selectedImageUris` | All currently selected/captured image URIs. |
| `selectedImageUri` | The first URI in `selectedImageUris`, or `null` if nothing is selected. |
| `isLoading` | `true` while picked images are being compressed. |
| `pickFromGallery` | Launches the gallery picker (Photo Picker on supported devices). |
| `captureWithCamera` | Launches the system camera to capture a new photo. |
| `clearSelection` | Resets `selectedImageUris`/`selectedImageUri` back to empty. |

---

## 🐛 Common Gotchas

### "Why do I get three different permission results instead of just `Denied`?"

Because Android's permission model has main-character energy and never keeps it simple. Here's what's actually happening, step by step:

1. **First-ever ask, user taps "Deny"** → you get `ShowRationale`. Android's take: *"They said no, but you can ask again — maybe explain yourself first this time."* Show a small dialog explaining why you need the permission, then call `pickFromGallery`/`captureWithCamera` again.
2. **Asked before, denied again, OS still willing to listen** → `PermissionDenied`. Same energy as above — not fatal, just mildly rude. Let the user retry.
3. **User checked "Don't ask again," or the OS has simply had enough of this conversation** → `PermissionPermanentlyDenied`. This is Android saying *"we are not doing this a third time."* Your only move now is Settings:

```kotlin
is ImagePickerResult.PermissionPermanentlyDenied -> {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    )
}
```

This whole dance is **camera-only**, by the way — gallery picking goes through Android's Photo Picker, which asks for no permission at all. One less state to lose sleep over.

### "I set `targetWidth` but nothing got resized"

`targetWidth` and `targetHeight` are a matched set — set one without the other and the library quietly does nothing (no crash, no log, no resizing, just vibes). Bring both or bring neither:

```kotlin
JetImagePickerConfig(
    targetWidth = 1024,
    targetHeight = 1024 // <- skip this and targetWidth silently does nothing
)
```

---

## ❤️ Contributions

Contributions are welcome! Open issues, submit PRs, or suggest ideas.

---

## 🧑‍💻 Author

Made with 💙 by [Nerojust](https://github.com/Nerojust)

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
