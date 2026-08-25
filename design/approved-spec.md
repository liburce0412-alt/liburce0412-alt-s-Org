# CampusAI SPECTRA approved specification

Status: frozen on 2026-08-22. Visual changes require a written design delta and user approval before implementation.

## Product hierarchy

- Android is the flagship product; the responsive web application is an administration console.
- The primary journey is: record today, see growth, receive AI guidance, join campus activity.
- Mobile navigation is Home, Time, Campus, Market, Profile. Portrait orientation is locked.
- Phone navigation uses one detached 64dp glass dock; expanded foldables and tablets use a left rail and two-pane layout.

## SPECTRA material

- Light space `#F8F9FD`, light ink `#162033`, dark space `#0D1422`.
- Spectrum: cyan `#16C5DC`, violet `#7562F5`, warm orange `#FF8B43`, rose `#FF79B9` with blue/red produced by mixing.
- Semantic: success `#159763`, warning `#FFB020`, error `#D33F65`, focus `#5A7DFF`, silver `#DBE3ED`.
- Ordinary glass is highly transparent with 14–18px backdrop blur and 140–160% saturation.
- Glass edges use a bright hairline, soft inner highlight, cool contact and ambient shadows, and localized spectral refraction at corners and lit edges.
- No opaque milky glass, full neon outline, generic aurora blobs, purple-only AI treatment, emoji decoration, or equal KPI-card grid.
- The shared volumetric field keeps a slow macro form cycle but must also expose clearly visible directional sand-grain light and moving particulate caustics. It responds strongly to pointer/touch without intercepting input; a smooth static-looking gradient does not pass.
- Motion off, WebGL loss, or unsupported rendering uses solid glass. High contrast uses near-solid surfaces without chromatic refraction.
- Environments: Original, Ocean, Ultraviolet, Ember. Environment changes affect atmosphere and refraction only.

## Geometry and type

- Page gutter 20dp, ordinary gap 12–16dp, card padding 16dp.
- Input radius 12dp, card radius 16dp, hero/sheet radius 24dp, primary buttons are pills.
- Tomorrow 600 for headings and metrics, IBM Plex Sans for body, platform Chinese fallbacks.
- Hero metric 40sp, page title 24sp, body 16sp.
- Material Symbols Rounded is the only icon language.
- The launcher icon is the approved brushed-metal infinity loop on warm ivory glass. The optical C with five connected nodes remains the default-avatar and achievement family rather than the launcher mark.

## Motion and feedback

- Button press: scale 0.98 plus 1dp depression and tightened refraction, 100–140ms.
- Card press: scale 0.99 with highlight movement toward the touch.
- Page transition: 180–240ms fade and 4–8dp movement.
- Chart reveal: one 420ms path reveal per data/range change.
- Loading uses breathing glass skeletons; success uses a glass Snackbar above the dock.
- Errors explain the cause and recovery. Low-risk errors may be lightly humorous; security and trade errors stay direct.
- Destructive actions use slide-to-confirm on Android and web.

## Screen decisions

- Home: date/greeting/avatar, one dominant goal ring, three recent category capsules, graphite Start button; Growth, AI, Announcement, Campus follow in that order.
- Time: day timeline first, day/week/month control, spectral glass add button, 25/50/90 focus presets, full-screen timer with minimizable dock capsule.
- AI: insight cards lead to a full conversation page, Fast/Deep segmented glass control, real chunk streaming, public progress stages, expandable evidence capsules.
- Campus: single-column feed, full-width in-card media, immersive composer, fixed comment composer.
- Market: two-column square-image grid, paged gallery, fixed glass action bar, node order progress.
- Profile: custom cover with bottom graphite fade and localized glass identity area, spectral avatar ring, energy streak, optical achievements.
- Admin: full-strength SPECTRA, collapsible glass rail, continuous metrics band, one main trend, review queue, compact table and floating bulk toolbar.

## Accessibility and performance

- Minimum touch target 48dp, visible focus contrast at least 3:1, WCAG AA text contrast, 200% text support.
- Color is never the only state signal.
- One shared GL/WebGL renderer, capped ambient frame rate, hidden/offscreen suspension, context-loss and solid fallback support.
- Deterministic visual tests freeze time, pointer, environment, resolution and quality.

## Approved refinement · 2026-08-22

- The phone Dock remains a single 64dp glass layer, but its active state is now one continuously draggable liquid selection body. It follows the finger across all five destinations, stretches in the travel direction, leaves a restrained trailing droplet, snaps to the nearest destination, and emits one light haptic tick when crossing a destination boundary. Taps remain supported.
- Light SPECTRA pages must retain obvious ice-paper breathing room. Volumetric lobes may pass behind content, but must not tint the whole viewport as one salmon, violet, or cyan wash. Use weighted color mixing so a later lobe cannot overwrite the full field.
- Glass must show a transparent body, bright outer hairline, quiet inset highlight, cool two-level separation shadow, and localized cyan/violet/warm refraction. The chromatic edge stays partial rather than becoming a full neon outline.
- Remove instructional text from the Profile cover. The pencil icon and whole identity hero remain the edit affordances.
- The AI page exposes exactly two primary runtime choices: `DeepSeek` and `本地模型`. FAST/DEEP is a secondary control and DEEP is unavailable for the local model. Quick tasks live in a gesture sheet instead of six permanent chips.
- The AI empty/processing surface uses one compact silver liquid-metal bead with localized caustics plus a thin breathing marquee. Do not use a rainbow-ring sphere, four generic bouncing bars, or a large instructional prompt card.
- On Android the activity uses `adjustNothing`; the full-screen AI root owns exactly one bottom inset equal to `IME union navigationBars`, while the composer adds no second system inset. This measured arrangement keeps the composer immediately above the keyboard on the connected device and prevents the former dead gap.
- The Android implementation adapts seven mechanisms from the personal component library: FloatingDock, AnimatedTabs, GlassPanel, IridescentBorder, StatusOrb, SignalLoader, and GestureSheet. ShaderGradient contributes renderer lifecycle and fallback rules. All are reimplemented in Compose under the SPECTRA tokens; no WebView or second React runtime is introduced.

## Approved correction · 2026-08-22 · device screenshot review

- In light mode, selected Dock and AI tabs use a shadowless pearl-glass liquid body with graphite content and localized spectral caustics. Large opaque graphite-black selection pills are rejected. Dark mode may retain restrained graphite glass.
- Both the five-item Dock selector and the DeepSeek/local-model selector accept direct horizontal drag, tick once per crossed option, and snap without leaving a cast shadow.
- The atmosphere combines narrower volumetric lobes with two directional particle layers and fine temporal grain. Macro color must leave ice-paper gaps; micro particles must visibly travel rather than merely flicker in place.
- The launcher uses `design/brand/campusai-infinity-icon.png`, generated from the approved user reference and wired as both adaptive and legacy Android icon content.
- Local AI accepts a clean plain answer without requiring a custom `<final>` envelope. It buffers unmarked output until completion, strips a complete private thinking block, streams explicit final sections when available, and rejects internal prompts or structured private JSON.
- SPECTRA particles use neighbouring-cell advection so a grain remains continuous across grid boundaries. The verified Original-light implementation combines one readable directional light-streak layer, one finer sand layer, continuous macro-volume motion, and ice-paper clearing; frame-to-frame random reseeding is rejected.
- The AI status object has a fixed circular silver shell: its silhouette, diameter and edge never deform. Dark silver liquid, moving reflections, local spectral caustics and embedded hot grain flow only inside the circle. External droplets, orbiting dots, squash/stretch and a static radial-gradient marble are rejected.

## Approved AI experience and personal context · 2026-08-22

- AI history is local-only Room data. One stable conversation ID is updated as the conversation continues; history shows title, final-reply summary, provider, model and update time. Delete is immediate with an undo Snackbar. APK replacement preserves history; uninstalling or clearing application data removes it.
- Ordinary chat stays conversational. The prompt receives only identity plus data relevant to the actual question; learning facts, courses and posts are not injected into unrelated greetings or general questions. Preset analysis tasks keep their deterministic `analysis + action` presentation.
- The evidence sheet controls time records, courses, the user's own posts and explicitly selected public posts per conversation. Private messages, orders, security information and other users' private data never enter model context.
- The Home greeting includes the shared profile name. Its subtitle is generated once per local calendar day by the currently selected AI engine, then cached on device. The copy may vary creatively but must pass a deterministic grounding check: no invented course, clock time, weather, campus state or completed activity. A local curated sentence is shown only while AI is unavailable or its output fails validation.
- The streaming composer adapts the curated Composer Loader as one broad optical segment constrained to the input's own 56–132dp bounds. It runs around the input perimeter only, with a bright refractive core and wider soft spectral halo; a viewport-sized path or a clipped hairline does not pass. The loader stops at the first completed response/cancellation and does not change IME ownership.
- The first valid answer character removes the single `思考中` orb/rail within 160ms. The composer remains directly attached to the IME in every streaming state.
- The old optical C is no longer used as the general product mark. All ordinary in-app brand-mark placements and launcher surfaces use the approved brushed-metal infinity mark; achievement-specific legacy assets may remain only until their separate badge artwork is replaced.

## Approved material correction · 2026-08-23

- Ordinary glass panels no longer use chromatic corner or lit-edge refraction. Their separation comes from a neutral bright hairline, a quiet neutral inner highlight and restrained cool depth; this rule overrides the earlier localized-refraction wording for ordinary panel edges.
- Spectrum remains available in the shared atmospheric field, progress/state graphics, active liquid selectors and the AI composer loader. These functional accents must not be copied back onto every card edge.
- The AI composer loader uses a narrower, brighter partial optical segment: a crisp white-leading core with a controlled spectral halo, inset tightly to the input perimeter rather than a dim full outline.
