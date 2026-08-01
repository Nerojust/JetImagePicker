# 📸 JetImagePicker

[![CI](https://github.com/nerojust/JetImagePicker/actions/workflows/android-ci.yml/badge.svg)](https://github.com/nerojust/JetImagePicker/actions/workflows/android-ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.nerojust/jetimagepicker.svg)](https://central.sonatype.com/artifact/io.github.nerojust/jetimagepicker)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A modern, Jetpack Compose-ready image picker library for Android.

---

## Features

1. Works seamlessly with Jetpack Compose, XML + Kotlin, or both.
2. Supports Camera and Gallery.
3. Clean handling of runtime permissions – even on Android 13+
4. Supports multiple image selection and compression
5. Provides structured result callbacks for success and error handling
6. Just works – no hidden setup, no ActivityResultContracts, and no more permission nightmares!

---

## 🛠️ Setup

**Add the dependency:**

```kotlin
dependencies {
    implementation("io.github.nerojust:jetimagepicker:1.0.0")
}
```

Or, if you're working inside this repo as a module:

```kotlin
implementation(project(":JetImagePicker"))
```

No extra repository declaration is needed — the artifact is published to Maven Central.

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

![jetimagepicker](https://github.com/user-attachments/assets/5b2b47a9-506a-4ba0-b980-091302e94ca0)


## 🧩 Example

### ✅ Image Picker Screen

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
        Button(onClick = pickerState.pickFromGallery) {
            Text("Pick from Gallery")
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = pickerState.captureWithCamera) {
            Text("Capture with Camera")
        }

        Spacer(Modifier.height(16.dp))

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

## ⏳ Loading State & Reset

`pickerState.isLoading` is `true` while picked images are being compressed — use it to show a progress indicator:

```kotlin
if (pickerState.isLoading) {
    CircularProgressIndicator()
}
```

Call `pickerState.clearSelection()` to reset the current selection back to empty.

---

## ❤️ Contributions

Contributions are welcome! Open issues, submit PRs, or suggest ideas.

---

## 🧑‍💻 Author

Made with 💙 by [Nerojust](https://github.com/Nerojust)

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
