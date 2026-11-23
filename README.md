# SmartAttendance – AI-Based Face Recognition Attendance System

An Android attendance management application that uses Artificial Intelligence and facial recognition to automate student attendance, reduce manual effort, and provide organized attendance reports.

## Project Overview

SmartAttendance is designed for educational institutions where teachers can register students, enroll their face samples, and automatically record attendance through face recognition.

The application also supports manual attendance management, attendance reports, filtering, and PDF/CSV exports.

## Features

### 1. Student Management
- Register students with name, roll number, and class.
- Input validation for required fields.
- Duplicate roll number checking.
- Student consent management.

### 2. AI Face Recognition
- Face detection using Google ML Kit.
- Face embeddings generated using MobileFaceNet (TensorFlow Lite).
- Face matching using cosine similarity.
- Multiple face enrollment samples supported.
- Unknown face detection.
- Recognition confidence/similarity display.
- Duplicate attendance prevention.

### 3. Attendance Management
- Automatic attendance marking through face recognition.
- Live camera recognition pipeline using CameraX.
- Manual attendance marking.
- Update existing attendance records.
- Present and absent status support.
- Prevention of duplicate daily attendance records.

### 4. Attendance Dashboard
- Daily attendance reports.
- Monthly attendance summaries.
- Filter by date and month.
- Filter by class.
- Filter by student name or roll number.
- Attendance records with timestamps and marking method.

### 5. Report Export
- Export attendance reports as CSV.
- Generate PDF attendance reports.
- Include attendance statistics and student information.

### 6. Privacy and Security
- Consent-based face enrollment.
- Face embeddings encrypted using Android Keystore.
- Local database storage.
- Face recognition processing using on-device ML components.

## Technology Stack

| Technology | Purpose |
|---|---|
| Kotlin | Main programming language |
| Android Studio | Android development |
| Jetpack Compose | User interface |
| Google ML Kit | Face detection |
| MobileFaceNet | Face embedding generation |
| TensorFlow Lite | On-device ML inference |
| CameraX | Camera preview and image analysis |
| Room Database | Local data persistence |
| Android Keystore | Encryption key protection |
| OpenCSV | CSV report generation |
| Android PdfDocument | PDF report generation |
| Gradle | Build automation |

## Face Recognition Workflow

1. A teacher registers a student.
2. The student provides consent for face enrollment.
3. A face image is selected and processed.
4. ML Kit detects and crops the face.
5. MobileFaceNet generates a numerical face embedding.
6. The embedding is encrypted and stored locally.
7. During recognition, a new face embedding is generated.
8. Cosine similarity compares the new embedding against enrolled samples.
9. If a sufficiently similar match is found, attendance is marked.
10. Duplicate attendance for the same student and date is prevented.

## Project Structure

```text
SmartAttendance/
│
├── app/
│   └── src/
│       └── main/
│           ├── assets/
│           │   ├── mobile_facenet.tflite
│           │   └── mobile_facenet_metadata.json
│           │
│           ├── java/
│           │   └── com/example/smartattendance/
│           │       ├── data/
│           │       ├── recognition/
│           │       ├── ui/
│           │       └── MainActivity.kt
│           │
│           └── AndroidManifest.xml
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── README.md
```

*The structure above highlights the main project components.*

## Requirements

- Android Studio
- JDK compatible with the project's Gradle configuration
- Android SDK 37
- Gradle wrapper included in the repository
- Android device or emulator
- Camera permission for live camera functionality

## How to Run

1. Clone the repository:

```bash
git clone https://github.com/abdul99088/SmartAttendance.git
```

2. Open the `SmartAttendance` folder in Android Studio.

3. Allow Gradle to synchronize the project dependencies.

4. Connect an Android device or start an Android emulator.

5. Grant camera permission when requested if using live recognition.

6. Run the application using Android Studio.

## Download APK

The compiled Android APK is available through Google Drive:

[Download SmartAttendance APK](https://drive.google.com/file/d/192UsxgvbCtxxjuemLXNjhQBNPpUH6LzD/view?usp=drive_link)

## Testing

The following functionality has been tested:

- Student registration and input validation.
- Face enrollment.
- Recognition of an enrolled face.
- Unknown face rejection.
- Duplicate attendance prevention.
- No-face and multiple-face rejection.
- Daily and monthly attendance filtering.
- Class and student filtering.
- CSV export.
- PDF export.
- Manual attendance creation and updating.
- Invalid and future date validation.

**Testing note:** The CameraX live recognition pipeline is implemented, but physical camera testing remains limited by the unavailable webcam/device. Gallery-based face recognition was tested successfully.

## Future Improvements

- Improve recognition accuracy using a larger and more diverse face dataset.
- Calibrate the similarity threshold using real-world validation.
- Add
