# Ori:Dev — Feature Graphic Spec

The Play Store feature graphic is the wide banner shown above the listing on phones and at the top of the listing card on the web.

## Hard requirements (Google Play)

| Property        | Value                          |
| --------------- | ------------------------------ |
| Format          | PNG or JPEG (PNG preferred)    |
| Dimensions      | **1024 x 500 px**, exactly     |
| Max file size   | 1 MB                           |
| Color profile   | sRGB                           |
| Transparency    | Not allowed                    |
| Text safe area  | Centre 924 x 400 (50 px insets)|

## Brand spec

- **Background:** Solid Indigo `#6366F1` with a subtle 8 % radial gradient toward the top-left (lighten by 6 %).
- **Wordmark:** "Ori:Dev" set in **Inter Display SemiBold**, 132 px, color `#FFFFFF`. Letter-spacing -1 %.
- **Tagline:** "Developer tools for foldables." in **Inter Regular**, 36 px, color `#E0E7FF` (Indigo 100), placed 24 px below the wordmark.
- **Origami glyph:** White outlined origami fold mark, 280 x 280 px, anchored to the right third with -8° rotation, 70 % opacity. SVG source lives in `app/src/main/res/drawable/ic_origami.xml`.
- **Optional accent:** A single Indigo `#4F46E5` diagonal stripe behind the glyph, 12 px wide, 30° angle.

## Layout grid

```
+---------------------------------------------------+
|  50 px inset                                       |
|                                                    |
|  Ori:Dev                          [origami glyph]  |
|  Developer tools for foldables.                    |
|                                                    |
|  50 px inset                                       |
+---------------------------------------------------+
        1024 px wide  x  500 px tall
```

## Tooling

Pick one — sources must live in `store/feature-graphic/source/`:

- **Figma** (preferred): file `feature-graphic.fig`. Export at 1x via the export panel.
- **Inkscape**: file `feature-graphic.svg`. Export with `inkscape feature-graphic.svg --export-type=png --export-width=1024 --export-height=500 --export-filename=feature-graphic.png`.
- **Affinity Designer 2**: file `feature-graphic.afdesign`. Export PNG slice.

## Output

Final asset path: `store/feature-graphic/feature-graphic.png` (1024 x 500, < 200 KB after `pngcrush -rem alla -reduce`).

## QA checklist

- [ ] Pixel-exact 1024 x 500
- [ ] No transparent pixels (`identify -format "%A" feature-graphic.png` returns `Blend` or `False`)
- [ ] All text fully inside the 924 x 400 safe area
- [ ] Wordmark legible at 50 % zoom
- [ ] Color picker on background returns `#6366F1`
- [ ] File size under 1 MB
