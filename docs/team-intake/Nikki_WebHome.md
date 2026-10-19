# Team Intake Specification: Web Landing & Home Page
**Assigned Member:** Nikki  
**Module Focus:** Web Landing Page, Hero Section, Feature Highlights & Value Propositions

---

## 1. Mapping to Shadow Repository Files

| Shadow Component | File Path |
|---|---|
| **Thymeleaf Template**| `web/src/main/resources/templates/home.html` |
| **Shared Web Styles** | `web/src/main/resources/public/css/style.css` |
| **Hero Photography**  | `web/src/main/resources/public/photos/cape-town.jpg` |
| **Destination Assets**| `web/src/main/resources/public/photos/durban.jpg`, `johannesburg.jpg` |
| **Web Server Handler**| `web/src/main/java/com/group2/flightres/web/WebApplication.java` (`handleHome`) |

---

## 2. What the Teammate Must Provide

1. **Semantic HTML5 Template (`home.html`)**:
   - Server-rendered Thymeleaf template (no client-side SPA or fetch frameworks).
   - Marked template placeholders in comments (`<!-- TEMPLATE PLACEHOLDER: ... -->`) so HTML authors know what not to break.
   - Hero section with photography overlay, brand title (`th:text="${appName}"`), search card form (`action="/flights"`).
   - Nikki's human-centred value props: 24/7 Concierge Support, 256-bit Secure Booking, Best Price Guarantee, and South African Domestic Corridors.
   - Popular route destination cards linking directly to pre-filtered search queries.
2. **Design Tokens & Responsive CSS (`style.css`)**:
   - Shared CSS file using Elevate design tokens (`--sv-navy-dark`, `--sv-gold-primary`, etc.).
   - Responsive CSS Grid and media queries ensuring flawless presentation on desktop (1200px) and phones (390px).
   - Automatic light and dark mode switching via `@media (prefers-color-scheme: dark)`.

---

## 3. Comparison Criteria & Evaluation Rubric

- **No Framework Requirement**: Is the page strictly server-rendered HTML/CSS without client-side React/Vue/Angular or asynchronous fetch calls?
- **Mobile Usability**: Does the search card and grid stack into a comfortable single-column touch experience on 390px mobile viewports?
- **Human-Centred Copy**: Does the copy follow warm, premium airline tone (inspired by Nikki's ideas) rather than generic placeholder text?
- **Brand Consistency**: Does the brand name, phone, and email come from server model attributes (`BrandConfig`)?

---

## 4. Shadow Baseline (What We Keep If Incoming Is Weaker)

- If incoming page introduces client-side JavaScript frameworks or fetch calls, we retain the shadow's **pure server-rendered Javalin + Thymeleaf pipeline**.
- If incoming CSS breaks on mobile viewports or lacks dark mode, we keep the shadow's **responsive `style.css`**.
- If destination links are non-functional, we keep the shadow's **parameterized flight search route links**.
