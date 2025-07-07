# 📸 JetImagePicker

A modern, Jetpack Compose-ready image picker library for Android.

---

## 🚀 Features

✅ Works seamlessly with Jetpack Compose, XML + Kotlin, or both.
✅ Supports Camera and Gallery
✅ Clean handling of runtime permissions – even on Android 13+
✅ Supports multiple image selection and compression
✅ Provides structured result callbacks for success and error handling
✅ Just works – no hidden setup, no ActivityResultContracts, and no more permission nightmares!

---

## 🛠️ Setup

1. **Add to your project**:

If using as a module:
```kotlin
implementation(project(":JetImagePicker"))

OR

implementation("com.github.nerojust:JetImagePicker:v1")


```
2. **Add to your settings.gradle**:

If using as a module:
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```
---

## 🧱 Usage

### 1. Configure your `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" /> <!-- for Android 13+ -->

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

## 🧩 Example

### ✅ Image Picker Screen

```kotlin
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

## ❤️ Contributions

Contributions are welcome! Open issues, submit PRs, or suggest ideas.

---

## 🧑‍💻 Author

Made with 💙 by [Nerojust](https://github.com/Nerojust)

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
