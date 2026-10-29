# Rekixo launch branding source

First-party asset from https://github.com/rekixo-lab/rekixo-site/blob/5e52ab17d0d6d8b4975e567cf2dc6b43adae096a/public/rekixo-logo-static.svg

Pinned commit: 5e52ab17d0d6d8b4975e567cf2dc6b43adae096a
Git blob: 75d2e1197f8925061ca5b36cf67bac5d5e1dce9a

The checked-in SVG preserves the upstream UTF-8 BOM and artwork, with one redundant final blank line removed. The generator restores that newline in memory to verify the pinned upstream Git blob before conversion. The generator preserves the original path geometry, path translations, even-odd fills, gradients and CSS shine opacity. Native VectorDrawable has no SVG filter support, so the small drop shadow is omitted. It preserves centering/aspect ratio. Android <=30 uses a 216dp wordmark (20% above the former 180dp). The drawable-v31 override uses 187dp, fitting the complete wordmark within Android 12+'s 192dp circular mask; a full 216dp wordmark would be clipped by that OS mask. Gradients require API 24; the app minimum is API 28.

Regenerate: `python3 scripts/generate-rekixo-launch.py`
Verify: `python3 scripts/generate-rekixo-launch.py --check`
