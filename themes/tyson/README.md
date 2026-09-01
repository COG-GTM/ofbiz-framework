# Tyson Trade theme

Back-office visual theme branded for the internal "Tyson Foods Trade" application.
It extends the `rainbowstone` theme and only overrides colors, fonts, and the
header/footer/login branding.

## Where things live

- `widget/Theme.xml` — theme definition; extends `component://rainbowstone/widget/Theme.xml`
  and overrides the header, footer, top app bar, and login templates.
- `webapp/tyson/TYSON.less` — all colors and fonts. The Tyson brand palette is declared
  at the top of the file (`@tyson-red`, `@tyson-burgundy`, `@tyson-khaki`, ...). Change a
  hex value there to retune the whole UI. The file imports the rainbowstone main LESS and
  is compiled client-side by less.js.
- `template/` — copies of the rainbowstone templates with the "TYSON | Trade" wordmark
  and Tyson Trade footer/login branding.
- `data/TysonThemeData.xml` — the `VisualTheme` seed record.

## Default theme

The theme is set as default in two places:

- `framework/common/config/general.properties` — `VISUAL_THEME=TYSON`
- `framework/common/data/CommonSystemPropertyData.xml` — `SystemProperty` `general/VISUAL_THEME`

Fonts: Tyson's proprietary Sentinel/Proxima Nova are substituted with
Century Schoolbook/Georgia (headings) and Helvetica/Arial (body).
