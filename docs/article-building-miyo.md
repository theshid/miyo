# Adding AI and Seamless Chapter Navigation to Miyo

Miyo is an anime and manga companion app built with Jetpack Compose that connects to AniList for tracking, streams anime episodes, and renders manga chapters from multiple sources. In this session, we added three features: an AI chat assistant, a "Catch Me Up" recap tool, and a next chapter button for the manga reader.

---

## Miyo AI Chat

The first feature is a conversational AI assistant accessible from the home screen. Tap the "Miyo AI" card and you land in a chat interface where you can ask anything about anime and manga.

### What It Does

- **Natural language search**: "Find me something like Vinland Saga but with more politics"
- **Personalized recommendations**: "What should I watch next?" — the AI already knows your current watching and reading lists
- **Character and plot Q&A**: "How is Gon related to Ging?"
- **Series recaps**: "Catch me up on Jujutsu Kaisen"

The key differentiator from a generic ChatGPT conversation is **context**. When the chat screen opens, the ViewModel fetches the user's current anime and manga lists from AniList and injects them as system context. GPT-4o knows what you're watching, what you're reading, and how far along you are before you type a single word.

### How It's Built

The entire AI layer is three files:

**`OpenAiService.kt`** — A Ktor HTTP client that calls OpenAI's chat completions endpoint. No SDK, just raw HTTP with Kotlinx Serialization for JSON. It takes a list of `ChatMessage` objects (role + content) and returns the assistant's response. The system prompt establishes the "Miyo AI" persona: concise, enthusiastic, spoiler-aware, formatted with bullet points.

**`AiChatViewModel.kt`** — Manages the conversation as a growing list of messages. On init, it calls the AniList repository to fetch the user's current anime list (up to 15 titles with progress) and manga list, then appends that as a system message. When the user sends a message, it adds it to the history, calls `OpenAiService.chat()` with the full history, and appends the response.

**`AiChatScreen.kt`** — The Compose UI. A top bar with "Miyo AI / Powered by GPT-4o", a `LazyColumn` of message bubbles, and an input bar with a send button. User messages get a purple-tinted bubble aligned right. AI messages get a dark glass bubble aligned left, rendered through a custom `MarkdownText` component that handles bold, italic, code, bullets, numbered lists, and headings.

The welcome state shows four tappable suggestion chips ("Find me something like Attack on Titan", "Catch me up on One Piece", etc.) that send the message directly when tapped.

### API Key Management

The OpenAI API key lives in `local.properties` (gitignored) and is read into `BuildConfig` at compile time via a `buildConfigField` in `build.gradle.kts`. It never touches source control.

```kotlin
// build.gradle.kts
val props = Properties()
if (localProps.exists()) {
    localProps.inputStream().use { props.load(it) }
}
val openAiKey = (props.getProperty("OPENAI_API_KEY") ?: "")
    .trim().removeSurrounding("\"")
buildConfigField("String", "OPENAI_API_KEY", "\"$openAiKey\"")
```

The service is registered as a singleton in `AppModule` and initialized with `BuildConfig.OPENAI_API_KEY`.

---

## Catch Me Up

The second feature is a standalone recap tool on the Media Detail screen. It solves a specific problem: you haven't watched or read a series in weeks, you want to jump back in, but you've forgotten what happened.

### How It Works

1. Open any series you have progress on (e.g., One Piece at Chapter 42)
2. A sparkle icon (AutoAwesome) appears in the action row — it only shows when `userProgress > 0`
3. Tap it — a `ModalBottomSheet` opens
4. The sheet shows the series title, your progress ("Ch. 42"), and a loading state ("Recapping what you've seen so far...")
5. Behind the scenes, a prompt is built with the series title, synopsis, genres, and your exact progress point
6. GPT-4o returns a spoiler-safe recap, rendered with markdown formatting

### The Prompt

The prompt is carefully constructed to prevent spoilers:

```
The user is reading "One Piece". They are on chapters 42 out of 1100.

Series synopsis: [AniList description]
Genres: Action, Adventure, Comedy, Drama, Fantasy

Give them a concise "Catch Me Up" summary of what has happened up to chapters 42.
Cover the key plot points, character developments, and important events.
Do NOT spoil anything beyond chapters 42.
Use bullet points for major arcs or events. Keep it under 400 words.
```

The system message reinforces this: "Only summarize up to the point the user has reached. Never reveal future plot points."

### Implementation

`CatchMeUpSheet.kt` is a single composable that takes a `Media` object and handles everything inline with `LaunchedEffect`. No ViewModel needed — the sheet is ephemeral. It calls `AppModule.openAiService().chat()` directly, shows a loading spinner, then renders the result through `MarkdownText`.

---

## Markdown Rendering

Both AI features share a `MarkdownText` component that parses a subset of Markdown into Compose `AnnotatedString`:

- `**bold**` — rendered with `FontWeight.Bold`
- `*italic*` — rendered with `FontStyle.Italic`
- `` `code` `` — rendered in primary purple
- `- item` or `• item` — prefixed with a bullet character and indented
- `1. item` — numbered lists preserved
- `### heading` — bold + primary color

This is deliberately simple. No full Markdown parser, no library dependency. GPT-4o's responses use a predictable subset of formatting, and this handles it cleanly.

---

## Next Chapter Navigation

The third feature addresses a friction point in the manga reader. Previously, when you finished a chapter's last page, you had to go back to the detail screen, find the next chapter, and tap it. Now, a "Next Chapter" card appears automatically.

### Webtoon Mode

In vertical scroll mode, a card is appended as the last item in the `LazyColumn` after all pages. When you scroll past the final page, you see:

- A book icon
- "End of chapter"
- "Continue to the next chapter?"
- A **CHAPTER X** pill button

### Pager Mode (LTR & RTL)

In horizontal pager mode, one extra page is added to the `HorizontalPager`'s page count. When you swipe past the last manga page, the next "page" is the same Next Chapter card. This works for both left-to-right and right-to-left reading directions.

### Navigation

The button navigates to the next chapter using `popUpTo(inclusive = true)`, which replaces the current reader in the back stack instead of stacking:

```kotlin
onNextChapter = { nextChap ->
    navController.navigate(Screen.MangaReader.createRoute(mediaId, nextChap)) {
        popUpTo(Screen.MangaReader.route) { inclusive = true }
    }
}
```

This means pressing back from Chapter 3 takes you to the detail screen, not through Chapters 2 and 1. The source ID is preserved because the ViewModel checks reading history on init and reuses the previously resolved source.

---

## What Changed

### New Files
- `data/remote/OpenAiService.kt` — GPT-4o API client
- `screens/ai/AiChatViewModel.kt` — Chat conversation state
- `screens/ai/AiChatScreen.kt` — Chat UI
- `screens/detail/CatchMeUpSheet.kt` — Recap bottom sheet
- `components/MarkdownText.kt` — Markdown-to-AnnotatedString renderer

### Modified Files
- `build.gradle.kts` — API key wiring via `buildConfigField`
- `di/AppModule.kt` — `OpenAiService` singleton registration
- `navigation/Screen.kt` — Added `AiChat` route
- `navigation/SaikouNavHost.kt` — Wired AI chat screen and `onNextChapter` for manga reader
- `screens/home/HomeScreen.kt` — Added "Miyo AI" action card
- `screens/detail/MediaDetailScreen.kt` — Added "Catch Me Up" sparkle button and bottom sheet
- `screens/reader/MangaReaderScreen.kt` — Added `NextChapterCard`, `onNextChapter` callback, wired into both `WebtoonReader` and `PagerReader`

---

## Cost

GPT-4o runs at $2.50 per million input tokens and $10 per million output tokens. With a 1024 max_tokens cap per response, a typical Catch Me Up recap costs under $0.01. A full chat session with 10 back-and-forth exchanges runs around $0.02-0.05. For a personal app, this is negligible.

---

## What's Next

The AI foundation is in place. Future additions that build on it:

- **Mood-Based Discovery**: A prompt like "What are you in the mood for?" with mood chips that map to genre combinations
- **Smart Watch Scheduling**: Suggest viewing schedules based on watching patterns
- **Server-side proxy**: Move the API key behind an edge function before any public release
