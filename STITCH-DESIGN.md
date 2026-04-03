# AutoBook Stitch Design Spec

## Theme: Midnight Amber (Material You / Material3)
- **Primary:** #FFBF00 (Amber/Gold)
- **Secondary:** #907335 (Muted Gold)
- **Tertiary:** #00DCFF (Cyan accent)
- **Neutral/Background:** #0A192F (Deep Navy)
- **Surface:** #0d1c32 (slightly lighter navy)
- **Surface variant:** #233148
- **On Primary:** #000000
- **On Background:** #d6e3ff (light blue-white)
- **Font:** Inter (headlines + body + labels)

## Screens (8 total)

### 1. Library Screen
- Top app bar: "AutoBook" title, search icon, settings gear icon
- "Currently Listening" card with cover art, title, author, progress bar, play button
- "Your Library" section header with sort/filter
- Grid of book cards (2 columns): cover art, title, author, progress indicator
- Bottom nav: Library (active), Browse, Settings
- FAB: "+" button to import new book

### 2. Player Screen
- Back arrow (←) to library
- Large album art / book cover (centered, rounded corners)
- Book title + author below cover
- Chapter indicator: "Chapter 5 of 23"
- Progress bar (amber filled, grey track) with current time / total time
- Controls row: shuffle, skip back 15s, play/pause (large amber circle), skip forward 15s, repeat
- Bottom row: bookmark icon, speed badge "1.0x", sleep timer (moon icon), chapter list icon, volume
- Mini waveform visualization (subtle)

### 3. Import Progress Screen
- Back arrow + "Importing Book" title
- Book filename shown
- Steps with checkmarks:
  - ✓ File loaded
  - ✓ Text extracted
  - → Detecting chapters (with progress bar, e.g., "12 chapters found")
  - ○ Cleaning content
  - ○ Preparing audio
- Cancel button at bottom

### 4. Settings Screen
- Back arrow + "Settings" title
- Sections with cards:
  - **Playback**: Default speed (slider 0.5x-3.0x), Skip forward/back duration (15s/30s/60s toggles), Auto-pause on chapter end (toggle)
  - **TTS Engine**: Voice selector dropdown, Pitch slider, Download offline voice
  - **Library**: Default sort (Recent/Title/Author), Show progress on covers (toggle), Cover art source (auto/manual)
  - **Appearance**: Theme (System/Dark/Light), Text size in reader
  - **Storage**: Cache size, Clear cache button
- About section at bottom

### 5. Select a Book (File Browser)
- Back arrow (←) + "Select a Book" title — CLEAR back navigation
- Filter chips row: All, PDF, EPUB, TXT, DOCX, BIN, ZIP, FB2, ODT, MOBI (scrollable horizontal)
- File list items:
  - File icon (color-coded by format)
  - File name (bold)
  - File size + date (subtitle)
  - Format badge (e.g., "EPUB" in small colored chip)
  - Checkbox for multi-select
- "Import Selected" button at bottom (amber, full width)
- Empty state: "No supported ebook files found" with folder icon

### 6. Book Detail Screen
- Back arrow (←) to library
- Large cover art at top (hero image, slight parallax)
- Title (large), Author, Format badge
- Stats row: "23 Chapters · ~8h 45m · PDF"
- Two CTAs:
  - "Start Listening" (amber filled button, full width)
  - "Continue from Chapter 5" (outlined button, if previously started)
- "Chapters" section header
- Chapter list: number, title, estimated duration per chapter
- Each chapter tappable to start from there

### 7. Chapters Screen (during playback)
- Back arrow (←) returns to player
- "Chapters" title
- Currently playing chapter highlighted with amber left border + playing animation icon
- Each chapter row:
  - Chapter number + title
  - Duration (e.g., "12:34")
  - Mini progress bar if partially listened (amber fill)
  - Checkmark if completed
- Tap any chapter to jump to it

### 8. Playback Options (Bottom Sheet)
- Drag handle at top
- **Sleep Timer** section:
  - Chip options: 15 min, 30 min, 45 min, 60 min, End of chapter, Custom
  - Active timer shown with countdown
- **Playback Speed** section:
  - Slider 0.5x to 3.0x with tick marks at 1.0x, 1.5x, 2.0x
  - Current speed shown large (e.g., "1.5x")
- **Narrator** section:
  - TTS engine dropdown (System Default / Google / Samsung)
  - Voice dropdown (locale-specific voices)
  - Preview button to hear sample
