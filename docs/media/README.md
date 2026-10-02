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
