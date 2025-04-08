# ScribeAI - Intelligent Multimodal Note-Taking Application

ScribeAI bridges the gap between analog and digital productivity by combining traditional note-taking with AI-powered features. Built for Android with an offline-first approach, it enables users to capture information through multiple input methods while maintaining privacy with on-device processing.

## Overview
- **Platform:** Android (Minimum SDK 24)
- **Core Value:** Seamless hybrid workspace combining handwritten, typed, and photo-captured notes
- **Target Users:** Students, professionals, and creatives
- **Privacy Focus:** Offline-first with optional cloud enhancements

## Tech Stack

### Core Technologies
- **Language:** Kotlin
- **Minimum SDK:** 24
- **Target SDK:** 35
- **Build System:** Gradle (Kotlin DSL)

### Architecture & Design Patterns
- **Architecture Pattern:** MVVM (Model-View-ViewModel)
- **Database:** Room Persistence Library
- **Repository Pattern** for data operations
- **ViewBinding** for UI binding
- **Coroutines & Flow** for asynchronous operations
- **LifecycleScope** for structured concurrency

### AI & Machine Learning
- **Google Gemini AI** (v2.0-flash model)
  - Text processing and analysis
  - OCR capabilities with structured Markdown output
  - Content safety filtering
- **ML Kit** for text recognition

### Image Processing & Drawing
- **CameraX** for modern camera integration
- **Custom DrawingView** implementation
- **Bitmap processing** with compatibility for Android P and above
- **FileProvider** for secure file sharing

### UI Components
- **Material Design 3** components and themes
- **RecyclerView** with swipe actions
- **Custom dialogs** for user interactions
- **ConstraintLayout** for responsive layouts
- **ViewBinding** for safe view access
- **Chip groups** for tag management

### Data Management
- **Room Database** with Type Converters
- **Kotlin Serialization** for JSON processing
- **Markdown support** via Markwon library
- **URI-based file management** for images and drawings

## Features

### Smart Note Management
- **Organization**
  - AI-powered automatic tagging system
  - Rich text formatting with Markdown support
  - Advanced filtering and search capabilities
  - Real-time content filtering
  - Swipe actions for quick note management

### Intelligent Search
- Full-text search across all note types
- Filter by date, tags, and note type
- Semantic search capabilities
- Fast response (<1s for 100 notes)

### Input Methods
- Rich text editor for typed notes
- Camera integration for photo-to-text conversion
- Drawing interface for digital handwriting
- Support for image annotations

### Design Philosophy
- **Color System:** Based on Zinc tones for accessibility
- **Typography:** Funnel Display font for modern, clean aesthetic
- **UI Elements:** 
  - Subtle rounded corners (8dp)
  - Clean lines with defined borders
  - Clear visual hierarchy

### AI-Powered Features
- OCR processing using Gemini AI
- Intelligent text extraction from images
- Automatic Markdown formatting of extracted text
- Content safety filtering for AI responses

### Image & Drawing
- Integrated camera support
- Custom drawing interface with bitmap handling
- Image annotation capabilities
- Multiple input modes (text, camera, drawing)

### UI/UX
- Material Design theming
- Dark mode support
- Custom formatting toolbar
- Tag filtering system
- Responsive layout design
- Preview mode for notes

## Development Tools

### IDE & Development
- Android Studio
- Gradle build system with Kotlin DSL
- ViewBinding for compile-time safe view access

### Testing & Quality
- JUnit for unit testing
- Instrumentation tests support
- ProGuard rules for release optimization

### Version Control
- Git for version control
- .gitignore configured for Android development

## Performance Targets

### Response Times
- App launch time: <500ms
- Note list loading: <1s for 100 notes
- OCR processing: <3s for high-res images

### Quality Metrics
- OCR accuracy: >95% for printed text
- UI rendering: Smooth 60fps
- Test coverage: Minimum 80% unit tests

## Development Timeline

### Current Progress (April 2024)
- ✅ Database Setup
- 🟡 Note Creation UI
- 📅 OCR Integration
- 📅 Search Functionality
- 📅 Theming
- 📅 Performance Tuning

## Project Structure

```
app/src/main/
├── java/com/example/scribeai/
│   ├── core/
│   │   ├── data/          # Database, Repository, DAOs
│   │   └── ui/views/      # Custom View implementations
│   └── features/
│       ├── drawing/       # Drawing functionality
│       ├── noteedit/      # Note editing & AI processing
│       ├── notelist/      # Note listing & filtering
│       └── notepreview/   # Note preview functionality
└── res/
    ├── layout/           # XML layouts
    ├── drawable/         # Icons and graphics
    ├── values/           # Strings, colors, and themes
    └── xml/             # Configuration files
