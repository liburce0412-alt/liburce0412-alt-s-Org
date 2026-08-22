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
- The shared volumetric field completes a visible change in 24–36 seconds and responds strongly to pointer/touch without intercepting input.
- Motion off, WebGL loss, or unsupported rendering uses solid glass. High contrast uses near-solid surfaces without chromatic refraction.
- Environments: Original, Ocean, Ultraviolet, Ember. Environment changes affect atmosphere and refraction only.

## Geometry and type

- Page gutter 20dp, ordinary gap 12–16dp, card padding 16dp.
- Input radius 12dp, card radius 16dp, hero/sheet radius 24dp, primary buttons are pills.
- Tomorrow 600 for headings and metrics, IBM Plex Sans for body, platform Chinese fallbacks.
- Hero metric 40sp, page title 24sp, body 16sp.
- Material Symbols Rounded is the only icon language.
- Brand mark is a continuous optical C with five connected nodes.

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
