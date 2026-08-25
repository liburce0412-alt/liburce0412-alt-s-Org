# Caesar Adaptive Field · V1 design contract

Status: approved by the owner on 2026-08-23 through the request for a global,
non-rigid redesign and the explicit allowance for purposeful visual flourish.

This document is a delta to `design/approved-spec.md`. Existing SPECTRA brand
assets, typography and safety rules remain valid. The former assumption that
every destination must share one fixed atmosphere is replaced by the adaptive
system below.

## Product promise

Caesar∞ should feel like one authored private instrument whose atmosphere
changes with its owner's task. Structure, type, interaction semantics and
safety stay predictable; colour, depth and event motion may adapt to the
current domain. Product copy must not frame the app as a campus platform.

## Invariants

- Tomorrow is the display/metric face; IBM Plex Sans is the body/UI face.
- The brushed-metal infinity mark is the product mark. Achievement artwork is
  separate and never substitutes for it.
- Every interactive target is at least 48 dp and has an explicit semantic role,
  label, selected/disabled state and recovery path where applicable.
- State is never inferred from colour or from the selected tab. Model, health,
  band and network surfaces render a shared capability state.
- Ordinary content surfaces use neutral separation. Spectrum belongs to active
  selectors, progress, live signals, the shared field and rare event feedback.
- Mutually-exclusive choices use one direct-manipulation sliding selector:
  tap to snap, drag across choices, announce the selected state, and provide a
  single haptic tick only when crossing a choice boundary. A CTA remains a
  button and is never disguised as a selector.
- `OpticalGlass` is a renderer-owned material, not a higher-alpha card. SPECTRA
  renders to a half-resolution scene texture, then at most three registered
  regions re-sample it with rounded-rect lens warp and edge-local RGB
  dispersion. Compose draws tint, optical edges, text and icons afterwards so
  content remains sharp. LOW quality, Motion Off and framebuffer failure use a
  solid Compose fallback without pretending to refract the background.
- Motion Off does not instantiate decorative infinite transitions. High
  contrast replaces translucent surfaces with near-solid ones.
- Destructive irreversible actions remain native-confirmed or slide-confirmed;
  reversible actions execute optimistically with Undo.

## Destination moods

| Destination | Mood | Signature moment |
| --- | --- | --- |
| Home | warm energy growth | goal ring sweep and a single threshold particle convergence |
| Time | cyan focus rail | atmosphere narrows into one track when focus begins |
| Owner's tree hollow | violet editorial social | local caustic/haptic acknowledgement for like and save |
| Wish wall | warm, high-clarity catalogue | controlled gallery depth and order-state rail advance |
| Profile | personal canvas | cover-derived atmosphere with a fixed graphite readability scrim |
| Caesar | silver intelligence | tool trace, first-token transition and staged surface reveal |
| Health | teal biological signal | freshness shimmer, live pulse and source transition |

Semantic colours do not follow destination moods: success, processing, live,
attention and failure retain one meaning across the app.

## Motion budget

- Micro feedback: 100–120 ms.
- State changes and page chrome: 180–240 ms.
- Data/chart/event reveal: 420 ms, one shot per real data change.
- Springs are reserved for direct manipulation: Liquid Dock, draggable sheets
  and snap interactions. Ordinary state transitions do not bounce.
- At most one continuous foreground animation is active on a screen. During MNN
  generation, camera, speech recognition, focus mode, thermal pressure or power
  save, the shared GL field freezes or drops to its idle policy.
- A flourish must communicate state, causality or achievement. Removing it must
  make the transition less understandable or less memorable; otherwise cut it.
- Streaming uses a compact silver state signal and a restrained breathing seam.
  A thick rainbow marquee travelling around the composer is forbidden.

## Shared capability surfaces

Model, Health Connect and BandBridge use the same deterministic pattern:

1. short state label;
2. what is true now;
3. evidence (model ID, source, last record/sync, actual route);
4. one primary recovery action and at most one secondary action;
5. observed time and freshness.

Static Ready never uses a looping orb. Loading and true Live may animate. Empty
health metrics render as `无数据`, never `0`; stale records show their actual
observation time and are never described as current.

## Shared components

The Compose design system owns:

- adaptive spacing, radii, motion, alpha, component-size and breakpoint tokens;
- page scaffold, top/section headers and safe Dock/IME insets;
- neutral surface, action chip, status chip and 48 dp icon action;
- loading/empty/error/offline state pane;
- capability card and event-driven progress visuals;
- compact bottom Dock, medium rail and expanded two-pane affordances.
- OpticalGlass region registration and a shared draggable sliding selector.

## Caesar composer

- Gallery, camera and speech are one 48 dp optical tool cluster with equal
  visual weight and explicit labels/semantics; none may collapse below the
  minimum touch target.
- On-device speech recognition is preferred. A system recognition service may
  be used only after explicit consent that it can use the network. Camera URI
  state survives recreation, and returning from camera must restore the shared
  renderer before the first visible frame.
- Image attachments, transcript and text can coexist in one request. The send
  control communicates send/stop as one stable silhouette.

## Private product language

- Product name: `Caesar∞` (the infinity character is part of the name).
- The social destination is `{ownerName}的树洞`; its compact Dock label is
  `树洞`.
- The exchange destination is `心愿墙`; listings are described as wishes or
  items only where the underlying transaction meaning must stay explicit.
- Package names, database table names and migration identifiers may retain
  historical Campus naming; they are implementation details and never leak to
  owner-facing copy.

Pages consume these components instead of inventing local surface alpha,
spacing or state semantics. Legacy `GlassPanel` and `TelemetryChip` remain as
compatibility wrappers until their call sites are migrated.

## Verification gates

- 360/600/840 dp; light/dark; 100%/200% font; Motion On/Off.
- No bottom content hidden beneath the Dock or duplicated IME/navigation inset.
- Model setting, Caesar header and actual routed request agree.
- Health and Band states expose source, freshness and recovery without an LLM.
- Agent-device verifies the five destinations, Caesar, model modes, health
  diagnostics, TalkBack semantics, keyboard/IME and process restoration.
- Thirty-minute MNN use does not leave background GL or multiple loaders at
  full frame rate.
