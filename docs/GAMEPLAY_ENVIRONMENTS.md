# Board-matched gameplay environments

Full-screen environments are restricted to the actual offline/online gameplay surface. Home, Store (including its gameplay-finish plywood surface), setup and online waiting screens keep their existing background. The completed match keeps its board environment until the gameplay screen is left.

| Selected board | Full-screen scenery | Promotion accents |
| --- | --- | --- |
| Classic | Existing jungle habitat, ferns, palms and moss | Leaf green / cream |
| Autumn Forest | Warm forest floor and scattered autumn leaves | Amber / copper |
| Blossom Garden | Soft green garden, pink flowers and petals | Blossom pink / ivory |
| Winter Haven | Snow banks, snow-covered evergreens and icy ground | Ice blue / white |
| Monsoon Mire | Damp soil, muddy puddles, ripples and rain marks | Rain-washed teal |
| Cloud Lagoon | Mint lagoon, cloud clusters and water rings | Mint / aqua |
| Astral Garden | Deep blue sky, stars and soft nebula glow | Starlight gold |

`GameplayBoardEnvironment` is the shared palette source for the backdrop and gameplay promotion text panels. It resolves the **selected playing board**, not the currently advertised cosmetic. Unknown IDs resolve through the normal BoardRegistry fallback. Other registered boards inherit their definition's surface/preview colors until a dedicated environment is authored.

The scenery is drawn with native Canvas primitives and recorded into a per-view Picture when the drawable bounds change. The same display content is reused; no bitmap/photo downloads, new animation ticker or per-frame scene generation are introduced. These environmental weather marks are static scenery; existing board animations, including Monsoon effects, remain unchanged.

Board selection, offline selection, inventory ownership and account changes refresh both scenery and promotion colors. Listeners and queued refreshes are removed on detach. Ordinary online state updates do not rebuild an unchanged environment. Geometry, player panels, hit testing, ad safe-space checks, promotion eligibility and entitlement rules remain unchanged.

No build, test, lint, install or deployment is run for this change, per the requested workflow. Real-device visual verification is left to the operator.
