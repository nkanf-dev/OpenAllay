# Project media

## OpenAllay banner

- **Editable source:** [`openallay-banner.svg`](openallay-banner.svg)
- **PNG fallback:** [`openallay-banner.png`](openallay-banner.png)
- **Canvas:** 1600 × 560 px, opaque midnight-blue background.
- **Content:** OpenAllay · Your AI companion in Minecraft · Explore / Build / Create.

The SVG is the source of truth. The companion, floating island, castle, garden,
and feature icons are original vector geometry. The file contains no embedded
raster images, external resources, scripts, or linked fonts. Named groups keep
the illustration, wordmark, tagline, and feature labels separate for editing.

The lettering was set in Avenir Next and converted to editable paths with
HarfBuzz and FontTools. Rendering the saved SVG does **not** need those tools or
the font. The text remains available in accessible SVG titles and labels. To
change the wording, replace the relevant lettering group in a vector editor,
then convert new lettering to paths before saving.

### Export

From the repository root, with [librsvg](https://wiki.gnome.org/Projects/LibRsvg)
installed (`brew install librsvg` on macOS):

```sh
rsvg-convert --output=docs/media/openallay-banner.png docs/media/openallay-banner.svg
```

This exports the native 1600 × 560 canvas. Export the PNG from the SVG after every
banner change; do not edit the two formats separately. A README-size preview can
be rendered outside the repository with:

```sh
rsvg-convert --width=850 --output=/tmp/openallay-banner-preview.png docs/media/openallay-banner.svg
```

`openallay-icon.png` is a separate, unchanged asset. The banner does not use an
official Minecraft logo or replace the project icon. Screenshots are kept in
[`screenshots/`](screenshots/).
