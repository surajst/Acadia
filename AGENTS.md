## Testing Rules
- Every new page added to the app must be added to page_health.spec.js
- Every new API endpoint must have at least one Playwright assertion
- Run full suite before reporting any task complete
- All new Playwright waitForURL calls must use 90000ms timeout
- All form submission steps must include waitForLoadState('networkidle') before assertions

## Feature Coverage Rule
Every new UI action (button click, form submit, approval flow)
must have a corresponding Playwright test. Page health tests
check pages load — they do not replace feature tests.
New features are not complete until a Playwright test covers
their core action.

## Thymeleaf Template Rules
- NEVER pass strings or UUIDs into th:onclick or any th:on* attribute
- ALWAYS use th:data-* attributes and read via element.dataset in JS
- Scan all new templates for th:on* patterns before submitting
- JavaScript inside Thymeleaf templates: never escape backticks or ${} in template literals. Use raw backticks and ${} directly. Thymeleaf only processes th: attributes — not inside script blocks.

## Security Rules
- Never add @PreAuthorize without verifying @EnableMethodSecurity is active in SecurityConfig.java
- All form submissions must include CSRF token
- Never expose raw UUIDs in visible UI elements

## Card and Empty State Standards
Empty state cards (no data available):
- Max height: 100px
- Padding: py-4 px-6
- Icon size: text-2xl or 24px max
- One line message + one line subtext only
- Never use full-height empty state illustrations

Form cards:
- Padding: p-4 (never p-6 or p-8)
- Gap between rows: gap-3
- Input height: py-2
- Full form must fit in viewport without scrolling

Data table cards:
- Header: py-3 px-4
- Row height: py-2 px-4
- Max visible rows before scroll: 8
- Table scrolls within its container, never the whole page

## Viewport Architecture Rule (MANDATORY)
There are two classes of page, and the rule differs between them. Getting
them the wrong way round is invisible until someone opens the page on a
short window.

### Signed-in portal pages (sidebar layout)
The outer layout fills the screen and does not scroll. Only the main content
area scrolls; the sidebar and header stay put.
<html style="height:100vh; height:100dvh; overflow:hidden">
<body style="height:100vh; height:100dvh; overflow:hidden; display:flex; flex-direction:column">
  <header> -- shrink-0, fixed height --</header>
  <main style="flex:1; overflow-y:auto; min-height:0"> -- only this scrolls --</main>
</body>
`100vh` first, `100dvh` second: the second wins where supported, and the
first is the fallback for browsers that do not.

### Standalone pages (login, signup, onboarding/setup wizard, error pages)
The page scrolls normally. Use `min-height: 100dvh` (with `100vh` as a
fallback) to centre short content; never `overflow: hidden` on html/body.
Every field and button must be reachable at 200% zoom and on a 360x640
screen with the keyboard open.

These have no sidebar to keep still, so locking them buys nothing and costs
the bottom of the form: a scrollbar inside the card hid the password field
on signup.

### Never nest scroll areas in a form card
No scrollbar inside a scrollbar. A data table may scroll inside its own
container (see the data table standard); a form card may not.

PageRenderSmokeTest enforces both classes mechanically. Agents must verify
scroll compliance before marking any page task complete.
