---
name: web-design-system
description: Use when building or restyling the website pages (HTML, CSS, templates). Keeps the website visually identical in spirit to the desktop app, responsive, fast, accessible, and photo led.
---

# Website design system

The website and the desktop app must feel like one product. Read the colour tokens and brand name from ONE place only (css/tokens.css and the brand config). Never type the app name or a colour code inside a page.

## Structure
- css/tokens.css: colours (light and dark), radius, spacing scale (4, 8, 12, 16, 24, 32, 48), font sizes, shadows.
- css/base.css: reset, typography, forms, buttons.
- css/layout.css: grid, header, footer, cards.
- One template per page. Pages contain plain HTML and CSS classes only. Template placeholders are marked with a comment so beginners know not to edit them.

## Look
- Photo led hero (full width, 60 to 70 vh), dark overlay gradient so text passes contrast 4.5:1.
- Cards 12px radius, soft shadow, 24px padding. Buttons 8px radius, one gold primary per screen.
- Max two typefaces: one display serif or semi serif for headings, one clean sans for text. Load locally or from Google Fonts with display=swap.
- Real content: real route names, real prices from the database, real seat counts. No lorem ipsum, no stock claims we cannot prove.

## Responsive
- Mobile first. Breakpoints 640, 900, 1200. Search form becomes a vertical stack on phones. Tap targets at least 44px.
- Images: width and height attributes set, loading="lazy" below the fold, JPG or WebP under 300 KB, srcset for hero.

## Motion (CSS only)
- transition 160 ms on hover and focus. Reveal on scroll with a tiny script is allowed only on the home page, 300 ms fade and 16px rise.
- Respect prefers-reduced-motion: turn all animation off.

## Accessibility
- Every input has a label. Visible focus ring. Alt text on every photo. Colour is never the only signal (seat taken also has a cross icon or label).
- Light and dark through prefers-color-scheme plus a manual toggle that stores the choice.

## Do not
- Do not add JavaScript frameworks. Do not put secrets or database code in the pages. Do not invent facts (awards, passenger counts, encryption claims).
