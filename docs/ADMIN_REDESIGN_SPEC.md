# Redesign Specification: Admin Dashboard (Integrations & Jules Accounts)

## Role: BARCAN-TAG-03 (Design)
**Principles Applied:** Gestalt (Proximity), Miller's Law (Information Density), Fitts's Law (Target Size), WCAG 2.1 (Contrast).

---

## 1. Visual Hierarchy & Grouping (Gestalt Principle of Proximity)
Current issues: Confusing boundaries between "Existing Accounts" and "Add Account" form.
**Solution:**
- Encapsulate the "Add New Jules Account" form into a distinct card with a secondary background or a clear border.
- Add a H3 heading: "Добавить новый Jules-аккаунт".
- Separate the list of active tokens from the management form using `space-8` (32px) margin.

## 2. Component: Integration Cards (Consistency & Perception)
Current issues: GitHub/Linear cards look empty; Jules card is overloaded.
**Solution:**
- Unified template for all three:
    - **Header:** Icon + Title + Status Badge (Circle + Text) + Toggle.
    - **Content Area:** Input fields with labels. Consistent height using `min-height`.
    - **Footer:** Metrics (CI Status, Issues Count, Sessions) with neutral-500 color.
- **Status Badges:**
    - `success`: Checked & Working (Green)
    - `neutral-400`: No check yet (Gray)
    - `error`: Error/Degraded (Red)

## 3. Jules Account "Capabilities" (Gestalt & Information Density)
Current issues: Uppercase comma-separated string (unreadable).
**Solution:**
- Change `text-transform: none`.
- Map the string to an array and render as **Vertical Chips** (Small boxes with neutral-100 background).
- Ensures each repository is a distinct visual unit.

## 4. Warning Component: Pending Invitations (Miller's Law)
Current issues: Raw log-like text.
**Solution:**
- Background: `warning-50` (soft amber).
- Border: `warning-200`.
- Icon: `alert-triangle`.
- Header: "Ожидание подтверждения приглашений" (bold).
- Content: Collapsible section using `<details>` or Svelte state, containing the long instruction text.

## 5. UI Labels & Ambiguity (Biosemantics of Signals)
Current issues: "GITHUB" used for both integration name and username field.
**Solution:**
- Rename internal Jules field to "GitHub username".
- Label "API Key" for Jules should be distinct from "Token" for GitHub.

## 6. Project Status "Waiting" (Requirement 7)
- Add "waiting" state to project status indicators.
- Color: `accent` (Violet) or `warning` (Amber) depending on the context of "holding pattern".

---

## Technical Specs (TAG-11)
- **Grid:** 8pt grid via `space-x` tokens.
- **Typography:** h3 for sections, body for labels, caption for footers.
- **Colors:** Use variables from `docs/DESIGN_SYSTEM.md`.
- **Responsive:** Stack cards on mobile (375px), ensure 44px touch targets for buttons.
