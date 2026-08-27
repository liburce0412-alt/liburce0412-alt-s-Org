# Sources and licensing

The PoC is an independent, minimal implementation of observed wire-protocol facts. It contains no
Xiaomi credentials or real health records.

Permissive upstream reference:

- `kubulashvili/mi-fitness-mcp`, commit
  `07b61900fcd0ae364cb5c668256cf0d0b2884c46`, MIT License, copyright 2026 Aleksej
  Kubulashvili. Its login state, read endpoints, fixture strategy, and RC4-drop-1024 test vector
  informed this validation artifact. The required MIT copyright/license text remains available in
  the pinned evidence repository.

- `binglua/mi-fitness-mcp-cn`, commit
  `7fcd06980f126c99c27e3a136b1ccb0f63dca8e5`, MIT License. Its China-region metric and
  response-shape additions were treated as hypotheses and checked against the APK models; no
  implementation was copied verbatim.

Independent corroboration only; no source copied into the PoC:

- Mi Fitness 3.58.0 (`com.mi.health`, version code 358000), specifically its `CloudInterceptor`,
  `ri4`, `sxk`, and `FitnessApiService` implementations.
- `mi-fitness` 0.2.0 source distribution from PyPI, SHA-256
  `0fb1bb16cbec948531e3bf7de8ac6456c2665775b059e64f441c42eb625bf369`. The package is
  GPL-3.0; it was used only to corroborate protocol behavior.
- `alexgetmancom/miband-bot`, commit
  `99a22e11bd045b18375f89e3439c120b747573bc`, GPL-3.0. It was used only as a protocol and
  endpoint reference. No GPL implementation code was copied.
- `shkyyy18/mi_fitness_data_bridge`, commit
  `304c7f2faa09291e3ab911af1d87c5789fcf900c`, AGPL-3.0-only at the inspected revision. It was
  used only as behavioral/schema evidence. No AGPL implementation code was copied into the PoC.

If code from a GPL/AGPL source is later copied, linked, or adapted into the distributed CampusAI
application, its license obligations must be handled separately. Protocol facts can instead be
implemented cleanly from the evidence and tests in this directory.

## MIT upstream attribution

MIT License

Copyright (c) 2026 Aleksej Kubulashvili

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT
OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
