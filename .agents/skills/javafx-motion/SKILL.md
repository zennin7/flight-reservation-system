---
name: javafx-motion
description: Use when adding animation or transitions to the JavaFX desktop app (screen changes, hover, selection, charts, loading). Gives timing rules and ready patterns so motion feels smooth and never blocks the UI.
---

# JavaFX motion rules

Motion must explain something (where did this come from, what did I click). Never decorate.

## Timing
- Hover and press feedback: 120 to 160 ms.
- Panel and screen transitions: 220 to 300 ms, ease out.
- Chart bars and numbers on first show: 600 to 900 ms, once only.
- Hero photo slow zoom: 18 to 25 s, very small (scale 1.00 to 1.06), loop reverse.
- Nothing else above 400 ms. Never block input while animating.

## Patterns to use (javafx.animation only, no extra libraries)
- Screen change: FadeTransition 0 to 1 plus TranslateTransition 12px up to 0 on the new root.
- Button hover: ScaleTransition to 1.02 and slightly stronger drop shadow. Press: scale 0.98.
- Seat select: ScaleTransition 1.0 to 1.12 to 1.0 (pop) and colour change through a CSS pseudo-class.
- Stepper: when the step changes, animate the connector line fill with a Timeline on a Region width.
- Counters: Timeline interpolating an IntegerProperty, bind the label to it.
- Bar chart: set data values to 0, then animate to the real values with a Timeline once the chart is visible.
- Confirmation tick: draw the check SVGPath using a Timeline on a Rectangle clip or StrokeDashOffset.
- Loading: a shimmer Region (LinearGradient moving) instead of an empty table.

## Rules
- Put every animation in a small helper class (Motion.java): fadeIn(Node), pop(Node), countUp(Label, int), reveal(Node, delayMs). Controllers call helpers, no animation code inside FXML controllers.
- Respect a global switch: Motion.ENABLED (read from prefs) so we can turn motion off for screenshots and tests. ScreenshotRunner must set it to false.
- Stop animations when a screen is removed (call stop() or use the node's scene listener) to avoid leaks.
- Test on the slowest machine we have. If a frame drops, reduce effects, not duration.
