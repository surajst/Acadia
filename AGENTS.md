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
Every new page must follow this exactly:
<html style="height:100vh; overflow:hidden">
<body style="height:100vh; overflow:hidden; display:flex; flex-direction:column">
  <header> -- shrink-0, fixed height --</header>
  <main style="flex:1; overflow-y:auto"> -- only this scrolls --</main>
</body>
These rules apply to every page. No exceptions.
Agents must verify scroll compliance before marking any page task complete.
