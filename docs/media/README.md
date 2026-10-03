# Project media

## OpenAllay banner

- **README image:** [`openallay-banner.png`](openallay-banner.png), the original artwork restored without changing its pixels.
- **Hand-drawn vector:** [`openallay-banner.svg`](openallay-banner.svg), manually authored to preserve the original composition, pixel lettering, symbol, colors, cubes, and connecting line.
- **Canvas:** 1672 × 941 px in both formats.

The SVG is a redraw of the original artwork, not a redesign or an automatic
raster trace. Its named groups and geometric paths can be edited directly.
There are no embedded raster images, external resources, scripts, or linked
fonts. The lettering is hand-drawn geometry rather than a replacement font.
The project's icon is unchanged.

The PNG intentionally remains the original image. The SVG matches its design,
but subtle raster texture and edge antialiasing are not pixel-identical. Do not
overwrite the original PNG with an SVG render merely to synchronize file hashes.

### Preview the vector

With [librsvg](https://wiki.gnome.org/Projects/LibRsvg) installed
(`brew install librsvg` on macOS), render an inspection copy outside the repo:

```sh
rsvg-convert --output=/tmp/openallay-banner-vector-preview.png docs/media/openallay-banner.svg
```

The restored PNG SHA-256 is
`c1e090a2df701021127022dc76a8ef72989c2089ea51a532da7be8f9a7a14ce3`.
Its source is the banner in commit `63d4d87`, before the 0.3.0 redesign.

## OpenAllay 0.4.0 screenshots

All eight files under `screenshots/` are fresh native captures of the packaged
Fabric 0.4.0 client with bundled Builder 0.2.1 on Minecraft 26.2. The images are
unchanged mainRenderTarget PNGs, not mockups or screenshots from an older build.
The actual framebuffer is 2880 × 1672 pixels, with GUI scale 2
and a logical GUI size of 1440 × 836.

The isolated flat survival demonstration world has commands disabled. Its local
deterministic demo endpoint asks the real Tools to read the current iron-block
recipe and inventory. The endpoint does not fabricate recipe cards, inventory,
provider usage, or pricing. These examples illustrate the UI, not live model
quality. Recipe-book setup awarded the native iron-block recipe without granting
items. Voice is disabled and unconfigured in its settings screenshot; no recording
or recognition was performed.

The gallery replaces the previous chat, tool-detail, general-settings, and About
images. New views show the gameplay HUD, full HUD reader, appearance settings,
and voice settings. The original banner and icon artwork are unchanged.
