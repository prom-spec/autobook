# 📖 OpenLoud — Auto Audiobook App

## Concept
Upload any book (PDF, EPUB, TXT, DOCX) → AI parses it into chapters/sections → high-quality TTS reads it like a real audiobook. Fully free, runs on-device.

---

## Core Problem
Audiobooks cost $15-30 each. Many books don't have audiobook versions at all. Existing TTS apps sound robotic and don't handle book structure (chapters, footnotes, headers, page numbers) — they just read everything linearly including garbage.

## Solution
Smart parsing + quality TTS. The app understands book structure, skips junk (page numbers, headers, footers, references), and reads content naturally with proper pauses between chapters.

---

## Tech Stack

### Platform
- **Kotlin + Jetpack Compose** (native Android)
- Min SDK 26 (Android 8.0) — covers 95%+ of devices
- **No backend server needed** — everything runs on-device

### TTS Engine (the key differentiator)
**Primary: Piper TTS (on-device, free, MIT license)**
- Neural TTS, sounds significantly better than Google TTS
- ~50MB per voice model (one-time download)
- Runs via ONNX Runtime on-device
- Multiple voices/languages available
- No API calls, no costs, works offline

**Fallback: Android built-in TTS**
- Google's on-device engine (pre-installed)
- Lower quality but zero setup

**Optional premium feel: Edge TTS**
- Microsoft's free TTS API (used by Edge browser)
- Studio-quality voices, free unlimited use
- Needs internet connection
- No official API but well-documented unofficial access

### Document Parsing
| Format | Library | Notes |
|--------|---------|-------|
| PDF | **MuPDF** (AGPL) or **PdfBox-Android** | Best PDF text extraction |
| EPUB | **Epublib** or custom XML parser | Native chapter structure |
| TXT | Built-in | Simple line parsing |
| DOCX | **Apache POI (lite)** or **docx4j-android** | Heading-based chapters |
| MOBI/AZW | **KindleUnpack** port | Convert to EPUB first |

### Smart Parsing (the AI layer — free)
**Gemini Nano (on-device, free)**
- Available on Pixel 6+ and Samsung S24+ via ML Kit
- Use for: chapter detection, content cleanup, footnote identification

**Fallback: Rule-based parser**
- Regex + heuristics for chapter detection
- Pattern matching for page numbers, headers, footers
- Works on ALL devices, no AI needed
- Honestly covers 80% of cases

---

## App Architecture

```
┌─────────────────────────────────────┐
│           UI Layer (Compose)         │
│  Library │ Player │ Settings │ Import│
├─────────────────────────────────────┤
│          ViewModel Layer             │
│  BookVM  │ PlayerVM │ ImportVM       │
├─────────────────────────────────────┤
│          Domain Layer                │
│  Parser  │ ChapterDetector │ TTS    │
├─────────────────────────────────────┤
│          Data Layer                  │
│  Room DB │ File Storage │ Prefs     │
└─────────────────────────────────────┘
```

### Data Model
```kotlin
@Entity
data class Book(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String?,
    val coverPath: String?,      // extracted or generated
    val filePath: String,        // original file
    val format: BookFormat,      // PDF, EPUB, TXT, DOCX
    val totalChapters: Int,
    val totalDuration: Long?,    // estimated reading time
    val lastPosition: ReadPosition,
    val addedAt: Long,
    val lastReadAt: Long?
)

@Entity
data class Chapter(
    @PrimaryKey val id: String,
    val bookId: String,
    val index: Int,
    val title: String,
    val textContent: String,     // cleaned, ready for TTS
    val startOffset: Int,        // character offset in full text
    val estimatedDuration: Long  // ms
)

data class ReadPosition(
    val chapterIndex: Int,
    val charOffset: Int,         // within chapter
    val timestamp: Long
)
```

---

## Screens

### 1. Library (Home)
```
┌──────────────────────────┐
│ 📚 OpenLoud              │
│ ┌────┐ ┌────┐ ┌────┐    │
│ │    │ │    │ │    │    │
│ │ 📕 │ │ 📗 │ │ 📘 │    │
│ │    │ │    │ │    │    │
│ └────┘ └────┘ └────┘    │
│ Sapiens  Atomic  Deep    │
│ 45% ━━━  12%━   New     │
│                          │
│ Currently Reading:       │
│ ┌──────────────────────┐ │
│ │ 📕 Sapiens            │ │
│ │ Ch 7: The Flood      │ │
│ │ ▶ ━━━━━━━━○──── 45%  │ │
│ └──────────────────────┘ │
│                          │
│        [+ Import Book]   │
└──────────────────────────┘
```

- Grid/list toggle for library
- Long-press for delete/details
- Search bar (hidden, pull down)
- Sort: recent, title, author, progress

### 2. Import
```
┌──────────────────────────┐
│ ← Import Book            │
│                          │
│  ┌────────────────────┐  │
│  │                    │  │
│  │   📄 Drop file or  │  │
│  │   tap to browse    │  │
│  │                    │  │
│  │  PDF EPUB TXT DOCX │  │
│  └────────────────────┘  │
│                          │
│  Processing...           │
│  ━━━━━━━━━━━░░░ 67%     │
│  Detecting chapters...   │
│                          │
│  Found: 12 chapters      │
│  Est. reading time: 8h   │
│                          │
│  ☑ Skip footnotes        │
│  ☑ Skip page numbers     │
│  ☐ Include references    │
│                          │
│  [Start Listening]       │
└──────────────────────────┘
```

### 3. Player (main experience)
```
┌──────────────────────────┐
│ ← Sapiens                │
│                          │
│         ┌──────┐         │
│         │      │         │
│         │  📕  │         │
│         │      │         │
│         └──────┘         │
│                          │
│  Chapter 7: The Flood    │
│  of Memory               │
│                          │
│  "...the Agricultural    │
│   Revolution was one of  │
│   the most controversial │
│   events in history..."  │
│                          │
│  ━━━━━━━━━━○──────────── │
│  12:34          38:21    │
│                          │
│   ⏪15  ◀  ▶▶  ▶  15⏩   │
│                          │
│  🔊━━━━━○━━━━  1.0x  💤  │
│                          │
│ [≡ Chapters] [Aa] [⚙️]   │
└──────────────────────────┘
```

**Player controls:**
- Play/pause (obvious)
- Skip ±15 seconds
- Previous/next chapter
- Speed: 0.5x → 3.0x (0.25 steps)
- Sleep timer: 15/30/45/60 min, end of chapter
- Volume
- Text display toggle (follow-along mode)

### 4. Chapter List
```
┌──────────────────────────┐
│ ← Chapters               │
│                          │
│  1. Prologue        3:21 │
│  2. The Tree of...  8:45 │
│  3. A Day in the... 12:11│
│  4. The Flood      ▶     │
│     ━━━━━○──── 34%       │
│  5. History's Big...15:30│
│  6. Building Pyra...9:22 │
│  ...                     │
└──────────────────────────┘
```

### 5. Settings
```
┌──────────────────────────┐
│ ← Settings               │
│                          │
│ Voice                    │
│  Selected: Amy (English) │
│  [Download more voices]  │
│                          │
│ TTS Engine               │
│  ◉ Piper (high quality)  │
│  ○ System TTS            │
│  ○ Edge TTS (online)     │
│                          │
│ Reading                  │
│  Default speed: 1.0x     │
│  Skip footnotes: ON      │
│  Skip page numbers: ON   │
│  Pause between ch: 2s    │
│                          │
│ Storage                  │
│  Cache: 234 MB           │
│  [Clear audio cache]     │
│                          │
│ Theme                    │
│  ◉ System ○ Light ○ Dark │
└──────────────────────────┘
```

---

## Smart Parsing Pipeline

```
Input File
    │
    ▼
[Format Detection] → appropriate parser
    │
    ▼
[Raw Text Extraction]
    │
    ▼
[Structure Detection]
    ├─ EPUB: use built-in TOC/chapters
    ├─ PDF: heuristic chapter detection
    │   ├─ Large font + "Chapter X" pattern
    │   ├─ TOC page parsing
    │   ├─ Page break + title patterns
    │   └─ Gemini Nano fallback (if available)
    └─ TXT/DOCX: heading-based splitting
    │
    ▼
[Content Cleanup]
    ├─ Remove page numbers (regex: standalone \d+)
    ├─ Remove headers/footers (repeated per-page text)
    ├─ Remove/tag footnotes ([1], *, †)
    ├─ Handle hyphenation (recon-\nstruct → reconstruct)
    ├─ Normalize whitespace
    └─ Fix encoding issues
    │
    ▼
[Chapter Objects] → Room DB
    │
    ▼
[Pre-generate audio] (optional, background)
```

### Chapter Detection Heuristics (no AI needed)
```kotlin
fun detectChapters(text: String): List<ChapterBreak> {
    val patterns = listOf(
        // "Chapter 1", "CHAPTER ONE", "Chapter I"
        Regex("(?m)^\\s*(CHAPTER|Chapter)\\s+[\\dIVXLCDM]+[.:]?\\s*(.*)$"),
        // "Part 1", "PART ONE"
        Regex("(?m)^\\s*(PART|Part)\\s+[\\dIVXLCDM]+[.:]?\\s*(.*)$"),
        // Numbered sections: "1.", "1.1", "I."
        Regex("(?m)^\\s*[\\dIVX]+\\.\\s+[A-Z].*$"),
        // ALL CAPS TITLE (likely chapter heading)
        Regex("(?m)^\\s*[A-Z][A-Z\\s]{10,}$"),
        // "* * *" or "---" section breaks
        Regex("(?m)^\\s*([*]{3}|[-]{3}|[_]{3})\\s*$")
    )
    // Score each candidate, merge nearby breaks, return chapters
}
```

---

## Audio Generation Strategy

### On-the-fly vs Pre-generated
- **Default: On-the-fly** — generate audio as you listen (sentence by sentence)
- **Background pre-generation** — when on WiFi + charging, pre-generate upcoming chapters
- **Cache management** — keep last 2 + next 3 chapters cached, evict old ones

### Piper TTS Integration
```kotlin
class PiperTTSEngine(context: Context) {
    private var piperModel: PiperModel? = null
    
    // Download voice model on first use (~50MB)
    suspend fun ensureVoiceReady(voiceId: String) {
        if (!isDownloaded(voiceId)) {
            downloadVoice(voiceId) // from GitHub releases
        }
        piperModel = PiperModel.load(getModelPath(voiceId))
    }
    
    // Generate audio for a sentence
    suspend fun synthesize(text: String): ShortArray {
        return piperModel!!.synthesize(text)
    }
    
    // Stream: generate sentence by sentence
    fun streamChapter(chapter: Chapter): Flow<AudioChunk> = flow {
        val sentences = splitSentences(chapter.textContent)
        for (sentence in sentences) {
            val audio = synthesize(sentence)
            emit(AudioChunk(audio, sentence))
        }
    }
}
```

### Sentence-level streaming
1. Split chapter into sentences
2. Generate audio for sentence N
3. While playing sentence N, pre-generate sentence N+1
4. Seamless playback with no gaps
5. Track position at sentence level for bookmarking

---

## Notifications & Background Play

```kotlin
// Foreground service for background playback
class AudioPlaybackService : Service() {
    // MediaSession for lock screen controls
    // Notification with play/pause/skip
    // Audio focus handling
    // Bluetooth/headphone controls
}
```

Standard Android media notification:
```
┌──────────────────────────────┐
│ 📕 Sapiens                    │
│ Chapter 7: The Flood          │
│ ◀◀  ▶▶  ▶  ━━━━━━○───  1.0x │
└──────────────────────────────┘
```

---

## Voice Models (Free, Downloadable)

| Voice | Language | Size | Quality |
|-------|----------|------|---------|
| Amy | English (US) | 48MB | ★★★★★ |
| Jenny | English (US) | 52MB | ★★★★★ |
| Lessac | English (US) | 45MB | ★★★★☆ |
| Alba | English (UK) | 47MB | ★★★★☆ |
| Thorsten | German | 50MB | ★★★★☆ |
| Siwis | French | 49MB | ★★★★☆ |
| 30+ more... | Various | ~50MB each | ★★★-★★★★★ |

All from [Piper voices](https://github.com/rhasspy/piper) — MIT/Apache licensed, free forever.

---

## Monetization (keeping it free)

**The app is free. Period.** No ads, no premium tier.

Revenue if ever needed:
1. **Donation button** — "Buy me a coffee" style
2. **Premium voices** — partner with voice actors for exclusive high-quality voices ($2-5 one-time)
3. **Cloud sync** — optional Google Drive backup of library/progress (free tier generous)

But honestly — keep it free. It's a utility. Build reputation.

---

## Development Phases

### Phase 1 — MVP (2-3 weeks)
- [ ] PDF + TXT import
- [ ] Basic chapter detection (regex)
- [ ] Piper TTS integration (1 English voice)
- [ ] Player screen with basic controls
- [ ] Library with progress tracking
- [ ] Background playback + notification
- [ ] Bookmarks (auto-save position)

### Phase 2 — Polish (2 weeks)
- [ ] EPUB support (with native TOC)
- [ ] DOCX support
- [ ] Multiple voices (downloadable)
- [ ] Speed control (0.5x-3.0x)
- [ ] Sleep timer
- [ ] Text follow-along mode
- [ ] Dark/light theme
- [ ] Chapter list navigation

### Phase 3 — Smart (2 weeks)
- [ ] Gemini Nano integration (better chapter detection)
- [ ] Edge TTS option (online, premium quality)
- [ ] Audio pre-caching (background generation)
- [ ] Landscape/tablet layout
- [ ] Widget for home screen
- [ ] Android Auto support

### Phase 4 — Community
- [ ] Share processed books (chapter structure, not copyrighted text)
- [ ] Voice model marketplace
- [ ] Multi-language auto-detection
- [ ] Accessibility (TalkBack support)

---

## Competitive Analysis

| Feature | Google Play Books | Voice Aloud | @Voice | **OpenLoud** |
|---------|------------------|-------------|--------|-------------|
| Price | Free (books cost $) | Free+ads | $4 | **Free** |
| TTS Quality | Google TTS | System TTS | System TTS | **Piper Neural** |
| PDF Support | No | Yes | Yes | **Yes** |
| EPUB Support | Yes (purchased) | No | Yes | **Yes** |
| Smart Parsing | N/A | Basic | Basic | **AI-powered** |
| Skip Junk | No | No | Partial | **Yes** |
| Chapter Detection | N/A | No | Manual | **Auto** |
| Offline | Partial | Yes | Yes | **Yes** |
| Open Source | No | No | No | **Yes** |

**Key differentiator: Smart parsing + quality TTS + actually free.**

---

## App Name Options
1. **OpenLoud** — simple, clear
2. **ReadAloud** — descriptive
3. **Narrator** — elegant
4. **BookVox** — catchy
5. **PageSpeak** — unique

---

## Technical Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Piper model size (50MB) | Storage on old devices | Offer lite voices (15MB), download on demand |
| PDF parsing quality | Bad chapter detection | Fallback to manual chapter markers, let user edit |
| Battery drain (on-device TTS) | User complaints | Pre-generate when charging, optimize inference |
| Scanned PDFs (no text) | Can't extract text | OCR via ML Kit (free, on-device) → then TTS |
| Complex layouts (tables, math) | Garbled reading | Detect and skip/summarize non-prose content |
| DRM-protected EPUBs | Can't read | Show clear error, explain DRM limitation |

---

## File Structure
```
app/
├── src/main/kotlin/com/openloud/
│   ├── ui/
│   │   ├── library/         # Library screen
│   │   ├── player/          # Player screen
│   │   ├── import_/         # Import flow
│   │   ├── settings/        # Settings
│   │   ├── chapters/        # Chapter list
│   │   └── theme/           # Material3 theme
│   ├── domain/
│   │   ├── parser/          # Format parsers
│   │   │   ├── PdfParser.kt
│   │   │   ├── EpubParser.kt
│   │   │   ├── TxtParser.kt
│   │   │   └── DocxParser.kt
│   │   ├── chapter/         # Chapter detection
│   │   │   ├── ChapterDetector.kt
│   │   │   └── ContentCleaner.kt
│   │   └── tts/             # TTS engines
│   │       ├── PiperEngine.kt
│   │       ├── SystemEngine.kt
│   │       └── EdgeEngine.kt
│   ├── data/
│   │   ├── db/              # Room database
│   │   ├── repository/      # Data repositories
│   │   └── preferences/     # DataStore prefs
│   └── service/
│       └── PlaybackService.kt  # Foreground service
├── src/main/res/
└── build.gradle.kts
```
