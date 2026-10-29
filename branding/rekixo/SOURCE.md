# Rekixo launch branding source

First-party asset from https://github.com/rekixo-lab/rekixo-site/blob/5e52ab17d0d6d8b4975e567cf2dc6b43adae096a/public/rekixo-logo-static.svg

Pinned commit: 5e52ab17d0d6d8b4975e567cf2dc6b43adae096a
Git blob: 75d2e1197f8925061ca5b36cf67bac5d5e1dce9a

The checked-in SVG preserves the upstream UTF-8 BOM and artwork, with one redundant final blank line removed. The generator restores that newline in memory to verify the pinned upstream Git blob before conversion. The generator preserves the original path geometry, path translations, even-odd fills, gradients and CSS shine opacity. Native VectorDrawable has no SVG filter support, so the small drop shadow is omitted. It adds padding to fit the complete wordmark in Android's launch-icon safe circle; it does not crop or stretch the logo. Gradients require API 24; the app minimum is API 28.

Regenerate: `python3 scripts/generate-rekixo-launch.py`
Verify: `python3 scripts/generate-rekixo-launch.py --check`
