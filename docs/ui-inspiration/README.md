# UI Inspiration — Gemini-Style Interface

These screenshots are reference designs from the Gemini app to inspire the Nyaya AI UI.

## Screenshots

### Batch 1 — Core UI
| File | Screen | Key Elements |
|------|--------|--------------|
| `01_home_screen_with_keyboard.png` | **Home — Keyboard open** | Star/spark logo, "Where should we start?" heading, notification card ("Add yourself to any frame"), pill input bar with `+` • text field • mic • waveform icons |
| `02_home_screen_idle.png` | **Home — Idle** | Same hero layout; input bar at bottom shows `+` • dotted field • ⬜ stop button • ⬆ send button |
| `03_voice_mode_minimal.png` | **Voice — Minimal** | Pulsing white pill orb centred, small 💬 icon left, ✕ icon right, all on white→blue gradient background |
| `04_voice_mode_full_controls.png` | **Voice — Full controls** | Same pulsing pill + bottom row: video, share, mic, ✕ icon buttons |
| `05_action_bottom_sheet.png` | **Bottom sheet — Actions** | Top icon row: Photos · Camera · Avatar; List items: Images, Videos, Music, Canvas, Deep Research, Guided Learning, Personal Intelligence (toggle) |

### Batch 2 — Navigation & History
| File | Screen | Key Elements |
|------|--------|--------------|
| `06_library_screen.png` | **Library** | "Library" title bar with edit icon; **Documents** section (code/doc items with dates); **Media** section (3×2 image grid with folder thumbnails) |
| `07_recent_chats_list.png` | **Recent chats panel** | Full-screen list of past conversation titles (plain text rows), user avatar + name + tier badge at bottom, settings gear |
| `08_sidebar_navigation.png` | **Sidebar / Drawer** | "Gemini" header with ✕; pill-shaped **New chat** button; Search chats; Images; Videos; Library; **Notebooks** section (+ New notebook + list); **Recent** chats list; user profile row + settings |
| `09_voice_mode_full_controls_v2.png` | **Voice mode v2** | Same white→blue gradient + pulsing pill orb; bottom bar: video 📹, share ⬆, mic 🎤, ✕ — matches `04` with cleaner spacing |
| `10_incognito_welcome_screen.png` | **Incognito / Temporary chat** | Light grey background; dashed-circle icon; bold "Welcome, stranger" heading; disclaimer text with dotted underline links; pill input bar (`+` · Ask Gemini · mic) |

## What was built from these (v1.9.0)

| Reference | Delivered as | Adapted, not copied |
|---|---|---|
| Gradient hero + big question | `NyayaHomeScreen` — brand mark, "How can I help?", privacy line | Suggestions are one scrolling row, not stacked cards |
| Floating pill input bar | `ui/components/NyayaInputBar.kt` | Trailing action is send **or** stop, never both |
| Four-point spark logo | `ui/components/NyayaBrandMark.kt`, drawn in Compose | Brand gradient (indigo → cornflower → teal → saffron), not Google's colours |
| Pearlescent voice orb | `ui/components/VoiceOrb.kt` | Driven by real microphone amplitude, not a timer |
| Sidebar / drawer | `ui/components/NyayaDrawer.kt` | Opens with the **mode switch** (Nyaya AI ↔ Mesh chat), since these are peer modes |
| Recent chats list | Saved conversations, searchable, plain-text rows | Encrypted on device; delete button always visible on each row |
| Actions bottom sheet | `ui/components/NyayaActionsSheet.kt` | Only what this app can do — no image/video/music generation, plus the NALSA helpline |
| Library screen | `ui/LegalLibraryScreen.kt` | Browses the 25 bundled Acts, guides first, repealed Acts badged |
| Incognito / temporary chat | Incognito chat, shown in the top bar throughout | No server to withhold data from, so it means: never written to storage, absent from the list, excluded from the Case File, gone on starting a new chat or closing the app |

Deliberately not copied: Google's four-colour palette, the "Pro" tier badge, notebooks, avatars, and the media-generation entries — none of which this app has or should imply it has.

## Design notes taken from the screenshots

- **Background**: white fading into a soft cornflower-blue gradient toward the bottom
- **Input bar**: pill-shaped, floating, with semi-transparent white fill
- **Orb / logo**: 4-pointed star in Google colours (blue, red, yellow, green)
- **Voice orb**: large rounded-rectangle pill, pearlescent white/blue shimmer, pulsing animation
- **Typography**: clean sans-serif, large heading, subtle subtext
- **Bottom sheet**: rounded top corners, light grey icons on squircle backgrounds, toggle switch for labs features
