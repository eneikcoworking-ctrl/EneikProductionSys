from __future__ import annotations

import json
import re
import shutil
from collections import Counter
from datetime import date
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "philosopher-patterns"
PEOPLE_DIR = OUT / "philosophers"


COMMON_PATTERNS = [
    ("ACP-001", "Design by Contract", "Assertions and pre/postconditions", "Catches invalid state before it leaks across module boundaries."),
    ("ACP-002", "Type-Driven Design", "Strong domain types and impossible-state encoding", "Makes illegal states unrepresentable at compile time where the stack allows it."),
    ("ACP-003", "Property-Based Testing", "Generated counterexamples over invariant space", "Finds edge cases human examples usually miss."),
    ("ACP-004", "Model-Based Testing", "Executable reference model for behavior", "Detects implementation drift against a simpler specification."),
    ("ACP-005", "Mutation Testing", "Tests must fail when behavior is deliberately damaged", "Prevents false confidence from weak assertions."),
    ("ACP-006", "Static Analysis Gate", "Linters, type checks, SAST and schema checks in CI", "Moves defect discovery from runtime into coding time."),
    ("ACP-007", "Exhaustive Case Analysis", "Closed enums, sealed types and total switches", "Stops unhandled states from silently reaching production."),
    ("ACP-008", "Immutability by Default", "Immutable values except at explicit boundaries", "Reduces hidden mutation, race conditions and spooky action at a distance."),
    ("ACP-009", "Pure Core, Imperative Shell", "Pure decision logic wrapped by IO adapters", "Keeps business behavior deterministic and easy to test."),
    ("ACP-010", "Boundary Validation", "Validate all external input at the edge", "Prevents polluted data from entering trusted internals."),
    ("ACP-011", "Schema and Contract Validation", "OpenAPI, JSON Schema, AsyncAPI or equivalent", "Turns interface agreements into machine-checkable facts."),
    ("ACP-012", "Consumer-Driven Contract Tests", "Provider behavior checked against consumer expectations", "Stops backend/frontend and service-to-service drift."),
    ("ACP-013", "Semantic Versioning", "Public behavior changes only through explicit version rules", "Prevents accidental breaking changes."),
    ("ACP-014", "Backward Compatibility Window", "Deprecated behavior remains available for a planned interval", "Lets clients migrate without emergency coordination."),
    ("ACP-015", "Idempotency Key", "Repeatable commands use stable operation identity", "Prevents duplicate writes after retry or network uncertainty."),
    ("ACP-016", "Optimistic Concurrency Control", "Version or status guard on state transitions", "Blocks lost updates and read-then-save races."),
    ("ACP-017", "Deterministic State Machine", "Explicit states and transition table", "Prevents ambiguous lifecycle behavior."),
    ("ACP-018", "Transactional Outbox", "Persist state change and event publication atomically", "Avoids split-brain between database and broker."),
    ("ACP-019", "Saga with Compensating Actions", "Long workflow split into reversible steps", "Contains partial failure in distributed processes."),
    ("ACP-020", "Circuit Breaker", "Stop calling unhealthy dependencies temporarily", "Prevents cascading outages."),
    ("ACP-021", "Bulkhead Isolation", "Separate resource pools for separate failure domains", "Stops one overloaded path from sinking the whole system."),
    ("ACP-022", "Retry with Exponential Backoff and Jitter", "Bounded retry for transient faults", "Reduces thundering herd and retry storms."),
    ("ACP-023", "Rate Limiting and Backpressure", "Control inflow before queues become unbounded", "Protects latency, memory and external APIs."),
    ("ACP-024", "Timeout Budgeting", "Every remote call has a bounded time contract", "Avoids stuck threads and zombie workflows."),
    ("ACP-025", "Observability and Traceability", "Logs, metrics, spans and correlation IDs", "Turns suspicion into inspectable evidence."),
    ("ACP-026", "Structured Error Taxonomy", "Errors include class, cause, retryability and safe action", "Prevents ambiguous recovery behavior."),
    ("ACP-027", "Audit Trail", "Security and business-critical decisions are recorded", "Makes accountability and rollback analysis possible."),
    ("ACP-028", "Least Privilege", "Grant only the minimum required authority", "Limits blast radius after bugs or compromise."),
    ("ACP-029", "Zero Trust Verification", "No caller, token or payload is trusted by default", "Stops confused-deputy and spoofing defects."),
    ("ACP-030", "Secrets Isolation", "Secrets never enter source, logs or client bundles", "Prevents credential leaks."),
    ("ACP-031", "Input Canonicalization", "Normalize before validation, comparison or authorization", "Prevents bypass through alternate representations."),
    ("ACP-032", "Safe Output Encoding", "Encode per target context", "Prevents injection through UI, SQL, shell, logs or URLs."),
    ("ACP-033", "Accessibility by Default", "WCAG, keyboard flow and touch target checks", "Prevents unusable interfaces from passing as complete."),
    ("ACP-034", "Golden Master Regression", "Preserve known observable behavior during risky change", "Catches accidental regressions in legacy surfaces."),
    ("ACP-035", "Snapshot with Semantic Assertions", "Snapshots are paired with behavior assertions", "Prevents brittle visual/text snapshots from hiding real defects."),
    ("ACP-036", "Test Data Builder", "Named fixtures built through domain factories", "Reduces fragile test setup and accidental invalid data."),
    ("ACP-037", "Reproducible Seed Injection", "Randomness and time are injectable in tests", "Removes flakes from nondeterministic logic."),
    ("ACP-038", "Containerized Toolchain Contract", "Build/test commands run in pinned containers", "Prevents PATH, JDK, Node and native binding drift."),
    ("ACP-039", "In-Memory Test Datasource", "Tests isolate database state from local files", "Avoids stale file DB corruption and cross-run contamination."),
    ("ACP-040", "Migration Serialization", "Schema changes pass through one ordered lane", "Prevents competing migrations and Flyway collisions."),
    ("ACP-041", "Migration Rollback Plan", "Every migration has a tested recovery story", "Reduces irreversible production failures."),
    ("ACP-042", "Feature Flag Isolation", "Incomplete work is hidden behind explicit capability switches", "Lets parallel branches land without exposing half-built behavior."),
    ("ACP-043", "Canary Release", "Expose change to a small monitored population first", "Detects production-only failures before full rollout."),
    ("ACP-044", "Fast Rollback", "Rollback is practiced and bounded by time", "Keeps recovery from becoming improvisation."),
    ("ACP-045", "Small Vertical PR", "One coherent behavior slice per PR", "Shrinks review surface and conflict probability."),
    ("ACP-046", "Merge Queue Gate", "Rebuild and retest on current main before merge", "Stops green-but-stale PRs from breaking main."),
    ("ACP-047", "Single Writer Ownership", "Each shared surface has a declared owner", "Prevents parallel agents from editing the same contract blindly."),
    ("ACP-048", "CODEOWNERS and Review Routing", "Ownership is enforced by repository rules", "Ensures the right role sees the risky change."),
    ("ACP-049", "Generated Artifact Authority", "Generated files are changed only through the generator", "Prevents manual drift and regeneration conflicts."),
    ("ACP-050", "Append-Only Extension Point", "Prefer new files/registrations over editing shared cores", "Reduces merge conflicts in parallel work."),
    ("ACP-051", "No Shared Constants Drift", "Enums and constants have one owner and compatibility tests", "Stops semantic divergence behind identical names."),
    ("ACP-052", "Route Ownership Registry", "Each endpoint has one owning controller or handler", "Prevents duplicate-route runtime failures."),
    ("ACP-053", "Endpoint Collision Scan", "CI checks that no two handlers claim the same method/path", "Catches Spring/FastAPI/Express route conflicts before boot."),
    ("ACP-054", "Semantic Conflict Test", "After text merge, run affected contract and behavior tests", "Catches meaning conflicts that Git cannot see."),
    ("ACP-055", "Conflict Forecast in PR", "PR declares touched paths, owners and integration points", "Lets reviewers spot collisions before code lands."),
    ("ACP-056", "Conflict Resolution Evidence", "Manual conflict resolution must add or run targeted evidence", "Prevents guessed merges from entering main."),
    ("ACP-057", "No-Op Supersession", "Superseded PRs are converted to tested no-op merges or closed", "Clears stale work without reintroducing old code."),
    ("ACP-058", "Stale Claim Self-Healing", "Stuck automation claims are released with evidence", "Keeps autonomous queues moving."),
    ("ACP-059", "CI Status Reconciliation", "System reconciles GitHub state with internal task state", "Prevents already-merged work from staying blocked."),
    ("ACP-060", "RAG Source-Grounded Retrieval", "Agents cite exact source chunks before applying doctrine", "Prevents philosophical slogans from becoming hallucinated rules."),
]


CONFLICT_PREVENTION_RULES = [
    ("CPF-001", "Single owner for every mutable surface", "Every shared file, contract, enum, migration lane and generated artifact has one owner."),
    ("CPF-002", "Contract before implementation", "API, event, schema and RAG contracts are reviewed before parallel implementation starts."),
    ("CPF-003", "Generated files are read-only", "Change the generator or source data, then regenerate."),
    ("CPF-004", "Append-only by default", "Prefer new extension files or registry rows over edits to shared central files."),
    ("CPF-005", "Small vertical PRs", "One PR covers one behavior slice, not feature plus refactor plus formatting."),
    ("CPF-006", "Merge queue over direct merge", "Every PR is rebuilt on current main before landing."),
    ("CPF-007", "Serialized migrations", "Database migrations, shared enums and global schemas pass through a single ordered lane."),
    ("CPF-008", "Refactor freeze lane", "Broad moves, renames and formatting run in their own window."),
    ("CPF-009", "Conflict forecast", "Task and PR describe touched paths, owners, contracts and expected integration points."),
    ("CPF-010", "Semantic conflict checks", "After merge, affected contract, schema and smoke tests must run."),
    ("CPF-011", "Feature flag isolation", "Incomplete parallel work cannot affect shared runtime without a flag."),
    ("CPF-012", "Evidence after conflict resolution", "Manual conflict fixes require targeted tests or contract checks."),
]


PUBLICATION_ANCHORS = {
    "Людвиг Витгенштейн": "Philosophical Investigations - language-games, meaning as use, private-language argument",
    "Гилберт Райл": "The Concept of Mind - knowing-how versus knowing-that, category mistakes",
    "Майкл Дамит": "Truth and Other Enigmas / Frege: Philosophy of Language - verificationist theory of meaning",
    "Джон Остин": "How to Do Things with Words - speech acts and performatives",
    "Пол Грайс": "Logic and Conversation - cooperative principle and conversational maxims",
    "Нельсон Гудман": "Ways of Worldmaking / Fact, Fiction, and Forecast - worldmaking and projectibility",
    "Рут Баркан Маркус": "A Functional Calculus of First Order Based on Strict Implication - quantified modal logic",
    "Бэрри Смит": "Applied ontology and Basic Formal Ontology - formal taxonomies for real domains",
    "Питер Саймонс": "Parts: A Study in Ontology - mereology and part-whole structure",
    "Ахилле Варци": "Parts and Places / formal ontology of boundaries and spatial parts",
    "Джонатан Шаффер": "Monism: The Priority of the Whole - priority monism and grounding",
    "Кит Файн": "Essence and Modality - essence before modal description",
    "Сол Крипке": "Naming and Necessity - rigid designation and necessary identity",
    "Дэвид Чалмерс": "Two-Dimensional Semantics - primary and secondary intensions",
    "Роберт Сталнакер": "Assertion and possible-world pragmatics - common ground and context change",
    "Гарет Эванс": "The Varieties of Reference - causal/informational constraints on reference",
    "Джон Перри": "The Problem of the Essential Indexical - indexicals and self-locating content",
    "Джейсон Стэнли": "Knowledge and Practical Interests / contextualism in language and knowledge",
    "Энди Кларк": "The Extended Mind / Supersizing the Mind - cognition extended into artifacts",
    "Альва Ноэ": "Action in Perception - enactive perception",
    "Томас Метцингер": "Being No One - self-model and phenomenal tunnel",
    "Дэвид Веллеман": "Practical Reflection / The Possibility of Practical Reason - intention and agency",
    "Шон Галлахер": "How the Body Shapes the Mind - embodied and prereflective experience",
    "Сюзан Hurley": "Consciousness in Action - dynamic perception-action feedback",
    "Фрэнк Рамсей": "Truth and Probability - degrees of belief and betting interpretation",
    "Ричард Джеффри": "The Logic of Decision - Jeffrey conditionalization and decision theory",
    "Айзек Леви": "The Fixation of Belief and Its Undoing / Enterprise of Knowledge - doxastic commitment",
    "Бас ван Фраассен": "The Scientific Image - constructive empiricism",
    "Иэн Хакинг": "Representing and Intervening - experiment, measurement and intervention",
    "Эллиотт Собер": "Reconstructing the Past / Evidence and Evolution - parsimony and model selection",
    "Дерек Парфит": "Reasons and Persons - psychological continuity and identity",
    "Дж. Л. Макки": "The Cement of the Universe - INUS conditions and causation",
    "Теодор Сайдер": "Four-Dimensionalism / Writing the Book of the World - persistence and structure",
    "Уэсли Сэлмон": "Scientific Explanation and the Causal Structure of the World - causal processes",
    "Питер ван Инваген": "Material Beings / Ontology, Identity, and Modality - composition and identity",
    "Кэтрин Хоули": "How Things Persist - persistence through change",
    "Карл Поппер": "The Logic of Scientific Discovery - falsifiability and critical testing",
    "Альфред Тарский": "The Concept of Truth in Formalized Languages - semantic conception of truth",
    "Нуэль Белнап": "A Useful Four-Valued Logic / how a computer should think - many-valued diagnostics",
    "Грэм Прист": "In Contradiction - dialetheism and paraconsistent reasoning",
    "Пол Хорвич": "Truth - minimalist theory of truth",
    "Питер Стросон": "Truth / Individuals - ordinary-language and descriptive metaphysics",
    "Тимоти Уильямсон": "Knowledge and Its Limits - knowledge-first epistemology",
    "Элвин Голдман": "A Causal Theory of Knowing / Epistemology and Cognition - reliabilism",
    "Кит Дероз": "Solving the Skeptical Problem - epistemic contextualism",
    "Кит ДеРоуз": "Solving the Skeptical Problem - epistemic contextualism",
    "Питер Унгер": "Ignorance: A Case for Scepticism - skepticism and knowledge standards",
    "Фред Дрецке": "Knowledge and the Flow of Information - informational epistemology",
    "Эрнест Соза": "Knowledge in Perspective / A Virtue Epistemology - virtue epistemology",
    "Бертран Рассел": "On Denoting / Principia Mathematica - descriptions and logical analysis",
    "Пер Мартин-Лёф": "Intuitionistic Type Theory - propositions as types and constructive proof",
    "Лучано Флориди": "The Philosophy of Information - informational objects and levels of abstraction",
    "Алонзо Чёрч": "Lambda calculus and Church's thesis - formal computability",
    "Сьюзан Хаак": "Evidence and Inquiry - foundherentism and evidence integration",
    "Готлоб Фреге": "Begriffsschrift / On Sense and Reference - sense, reference and compositionality",
    "Роберт Брэндом": "Making It Explicit - inferentialism and scorekeeping",
    "Уиллард Куайн": "Two Dogmas of Empiricism / Word and Object - holism and indeterminacy",
    "Ричард Рорти": "Philosophy and the Mirror of Nature - anti-representationalism and pragmatism",
    "Дональд Дэвидсон": "Truth and Meaning / radical interpretation - interpretation and coherence",
    "Уилфрид Селларс": "Empiricism and the Philosophy of Mind - critique of the Myth of the Given",
    "Хиллари Патнэм": "Reason, Truth and History / The Meaning of 'Meaning' - internal realism and semantic externalism",
    "Георг Хенрик фон Вригт": "Deontic Logic (1951) - formal obligation, permission and prohibition",
    "Герберт Харт": "The Concept of Law - rules of recognition and legal positivism",
    "Джозеф Раз": "Practical Reason and Norms / The Authority of Law - authority and exclusionary reasons",
    "Уэсли Хохфельд": "Fundamental Legal Conceptions - rights, duties, privileges and powers",
    "Роналд Дворкин": "Taking Rights Seriously / Law's Empire - principles and integrity",
    "Рональд Дворкин": "Taking Rights Seriously / Law's Empire - principles and integrity",
    "Фредерик Шауэр": "Playing by the Rules - rule-based decision and defeasibility",
    "Патриция Черчленд": "Neurophilosophy - brain-based explanation of cognition",
    "Патриция Чёрчленд": "Neurophilosophy - brain-based explanation of cognition",
    "Патриция Черчланд": "Neurophilosophy - brain-based explanation of cognition",
    "Марта Нуссбаум": "Upheavals of Thought / Creating Capabilities - emotion, capability and human flourishing",
    "Нед Блок": "Troubles with Functionalism / consciousness and access-phenomenal distinction",
    "Кристофер Пикок": "Sense and Content / A Study of Concepts - conceptual content",
    "Рут Милликен": "Language, Thought, and Other Biological Categories - teleosemantics",
    "Джэгвон Ким": "Supervenience and Mind / Philosophy of Mind - supervenience and reduction",
    "Джегвон Ким": "Supervenience and Mind / Philosophy of Mind - supervenience and reduction",
    "Дэвид Льюис": "Convention / Counterfactuals - convention and coordination",
    "Скотт Шапиро": "Legality - planning theory of law",
    "Джон Серл": "Speech Acts / The Construction of Social Reality - institutional facts",
    "Джон Сёрл": "Speech Acts / The Construction of Social Reality - institutional facts",
    "Маргарет Гилберт": "On Social Facts / Joint Commitment - plural subjects and joint commitment",
    "Майкл Братман": "Intention, Plans, and Practical Reason / Shared Agency - planning and shared intention",
    "Элизабет Энском": "Intention - intentional action under a description",
}


TAG_TITLES = {
    "BARCAN-TAG-00_CODE-GUARDIAN": ("CODE-GUARDIAN", "Code review, meaning and integration integrity"),
    "BARCAN-TAG-01_ACTUALIST-OBJECT": ("ACTUALIST-OBJECT", "Domain objects, identity and bounded contexts"),
    "BARCAN-TAG-02_RIGID-DESIGNATOR": ("RIGID-DESIGNATOR", "API contracts, naming and semantic stability"),
    "BARCAN-TAG-03_BELIEF-INTENSION": ("BELIEF-INTENSION", "UX intention, perception and cognitive load"),
    "BARCAN-TAG-04_MODAL-QUANTIFIER": ("MODAL-QUANTIFIER", "Prediction evidence, uncertainty and model trust"),
    "BARCAN-TAG-05_NECESSARY-IDENTITY": ("NECESSARY-IDENTITY", "Runtime identity, reproducibility and incidents"),
    "BARCAN-TAG-06_DEONTIC-CONSISTENCY": ("DEONTIC-CONSISTENCY", "Testing, truth status and quality gates"),
    "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE": ("SECOND-ORDER-KNOWLEDGE", "Security, validation and proof of authority"),
    "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE": ("SUBSTITUTIVITY-SALVA-VERITATE", "Data types, substitution and lineage"),
    "BARCAN-TAG-09_MORAL-DILEMMA": ("MORAL-DILEMMA", "Value, tradeoffs and waste prevention"),
    "BARCAN-TAG-10_DEONTIC-PROHIBITION": ("DEONTIC-PROHIBITION", "Compliance, prohibitions and rule systems"),
    "BARCAN-TAG-11_CLIENT-PERCEPTION": ("CLIENT-PERCEPTION", "Perception, accessibility and visible evidence"),
    "BARCAN-TAG-12_SOCIAL-CONTRACT": ("SOCIAL-CONTRACT", "Shared contracts, collaboration and parallel work"),
}


PERSONAL_SLOTS = [
    ("name-gate", "Semantic Naming Gate", "Check names against the philosopher's central distinction before code review continues."),
    ("state-invariant", "State Invariant Kernel", "Turn the principle into a lifecycle invariant that tests and runtime guards can enforce."),
    ("boundary-map", "Boundary Map", "Draw the exact edge where the principle changes how modules may communicate."),
    ("counterexample-test", "Counterexample Test", "Create a test designed to break the claim rather than merely demonstrate it."),
    ("data-shape", "Data Shape Discipline", "Encode the relevant philosophical distinction in schema, type or value-object structure."),
    ("transition-guard", "Atomic Transition Guard", "Guard state changes with expected state, version or capability context."),
    ("review-binary", "Binary Review Criterion", "Make the role's approval/rejection rule inspectable and non-vague."),
    ("evidence-trace", "Evidence Trace", "Record the observable proof needed for later agents to trust the decision."),
    ("parallel-work", "Parallel Conflict Shield", "Prevent two agents from applying incompatible meanings to the same surface."),
    ("rag-capsule", "RAG Doctrine Capsule", "Store the philosopher-specific rule as a retrievable decision fragment."),
]


ROLE_DEFECTS = {
    "BARCAN-TAG-00_CODE-GUARDIAN": "semantic drift in code review",
    "BARCAN-TAG-01_ACTUALIST-OBJECT": "anemic or duplicated domain entities",
    "BARCAN-TAG-02_RIGID-DESIGNATOR": "breaking API and naming drift",
    "BARCAN-TAG-03_BELIEF-INTENSION": "cognitive overload and inaccessible UX",
    "BARCAN-TAG-04_MODAL-QUANTIFIER": "false certainty in predictions",
    "BARCAN-TAG-05_NECESSARY-IDENTITY": "unreproducible runtime identity and weak RCA",
    "BARCAN-TAG-06_DEONTIC-CONSISTENCY": "flaky or untruthful quality gates",
    "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE": "authorization and validation blind spots",
    "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE": "unsafe substitution and data lineage loss",
    "BARCAN-TAG-09_MORAL-DILEMMA": "local optimization and wasteful scope",
    "BARCAN-TAG-10_DEONTIC-PROHIBITION": "policy bypass and ambiguous prohibition",
    "BARCAN-TAG-11_CLIENT-PERCEPTION": "unobserved perceptual and accessibility defects",
    "BARCAN-TAG-12_SOCIAL-CONTRACT": "parallel implementation contract conflict",
}


TRANSLIT = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "e", "ж": "zh", "з": "z",
    "и": "i", "й": "y", "к": "k", "л": "l", "м": "m", "н": "n", "о": "o", "п": "p", "р": "r",
    "с": "s", "т": "t", "у": "u", "ф": "f", "х": "h", "ц": "ts", "ч": "ch", "ш": "sh",
    "щ": "sch", "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu", "я": "ya",
}


def slugify(value: str) -> str:
    result = []
    for char in value.lower():
        if char in TRANSLIT:
            result.append(TRANSLIT[char])
        elif char.isascii() and char.isalnum():
            result.append(char)
        else:
            result.append("-")
    slug = re.sub(r"-+", "-", "".join(result)).strip("-")
    return slug or "unknown"


def parse_tag_file(path: Path) -> list[dict[str, object]]:
    tag = path.stem
    role = ""
    focus = ""
    rows: list[dict[str, object]] = []
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if line.startswith("**Роль:**"):
            role = line.split("**Роль:**", 1)[1].strip()
        elif line.startswith("**Фокус:**"):
            focus = line.split("**Фокус:**", 1)[1].strip()
        match = re.match(r"^\|\s*(\d+)\s*\|\s*\*\*(.+?)\*\*\s*\|\s*(.+?)\s*\|\s*(.+?)\s*\|$", line)
        if match:
            ordinal, name, principle, application = match.groups()
            rows.append(
                {
                    "tag": tag,
                    "tag_file": path.name,
                    "source_line": line_no,
                    "ordinal": int(ordinal),
                    "name_ru": name.strip(),
                    "principle": principle.strip(),
                    "role_application": application.strip(),
                    "role": role,
                    "focus": focus,
                }
            )
    return rows


def extract_rows() -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for path in sorted(ROOT.glob("BARCAN-TAG-*.md")):
        rows.extend(parse_tag_file(path))
    return rows


def pattern_id(slug: str, slot_index: int, slot_key: str) -> str:
    return f"{slug.upper().replace('-', '_')}_{slot_index:02d}_{slot_key.upper().replace('-', '_')}"


def make_personal_patterns(row: dict[str, object]) -> list[dict[str, str]]:
    name = str(row["name_ru"])
    slug = slugify(name)
    principle = str(row["principle"])
    tag = str(row["tag"])
    defect = ROLE_DEFECTS[tag]
    anchor = PUBLICATION_ANCHORS.get(name, f"Role-file principle: {principle}")
    patterns = []
    for number, (slot_key, slot_title, slot_rule) in enumerate(PERSONAL_SLOTS, start=1):
        patterns.append(
            {
                "n": str(number),
                "id": pattern_id(slug, number, slot_key),
                "name": f"{name}: {principle} - {slot_title}",
                "publication_anchor": anchor,
                "defect_prevented": f"{defect}; personal failure mode: losing '{principle}' while coding.",
                "agent_rule": (
                    f"{slot_rule} Apply it only as a {name}-specific micro-pattern; "
                    "use the common ACP file for the broad engineering practice."
                ),
            }
        )
    return patterns


def clean_output() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    if PEOPLE_DIR.exists():
        shutil.rmtree(PEOPLE_DIR)
    PEOPLE_DIR.mkdir(parents=True, exist_ok=True)


def write_common_patterns() -> None:
    lines = [
        "# Common Analytic Programming Patterns",
        "",
        "These patterns are intentionally shared. They match more than five philosophers in the BARCAN corpus, so they live here instead of being duplicated inside philosopher files.",
        "",
        "| ID | Pattern | Technique | Defect Prevention | RAG Rule |",
        "|---|---|---|---|---|",
    ]
    for pid, name, technique, why in COMMON_PATTERNS:
        lines.append(
            f"| `{pid}` | {name} | {technique} | {why} | Retrieve as common background; do not copy into a philosopher's 10 personal patterns. |"
        )
    lines.extend(
        [
            "",
            "## RAG Retrieval Rule",
            "",
            "1. Retrieve this file first for universally reusable engineering practices.",
            "2. Retrieve exactly one philosopher file for individual style and judgment.",
            "3. If a new personal pattern starts fitting more than five philosophers, move it here and replace it in those philosopher files.",
            "4. For parallel development tasks, also retrieve `01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md`.",
            "",
        ]
    )
    (OUT / "00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md").write_text("\n".join(lines), encoding="utf-8")


def write_conflict_prevention_charter() -> None:
    lines = [
        "# Parallel Development Conflict Prevention Charter",
        "",
        "This file is the shared anti-conflict layer extracted from the session work on stuck PRs, stale branches, duplicate routes, CI reconciliation and merge ordering.",
        "",
        "| ID | Rule | Requirement |",
        "|---|---|---|",
    ]
    for rid, name, requirement in CONFLICT_PREVENTION_RULES:
        lines.append(f"| `{rid}` | {name} | {requirement} |")
    lines.extend(
        [
            "",
            "## Required Agent Sequence",
            "",
            "1. Before coding, declare `touched_paths`, owners, contracts and likely integration points.",
            "2. During coding, do not edit generated files manually, root `.gitignore`, shared migrations or shared enum/constants without the owner lane.",
            "3. Before merge, rebuild on current `main`, run affected contract/schema/smoke tests and record the evidence.",
            "4. After a manual conflict resolution, add or run a targeted check proving that behavior, not only text, still matches.",
            "",
        ]
    )
    (OUT / "01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md").write_text("\n".join(lines), encoding="utf-8")


def write_philosopher_file(row: dict[str, object]) -> dict[str, object]:
    tag = str(row["tag"])
    role_name, tag_focus = TAG_TITLES[tag]
    name = str(row["name_ru"])
    slug = slugify(name)
    patterns = make_personal_patterns(row)
    file_name = f"{tag}_{int(row['ordinal']):02d}_{slug}.md"
    philosopher_id = f"{tag}:{int(row['ordinal']):02d}:{slug}"
    anchor = PUBLICATION_ANCHORS.get(name, f"Role-file principle: {row['principle']}")
    lines = [
        "---",
        f'philosopher_id: "{philosopher_id}"',
        f'name_ru: "{name}"',
        f'barcan_tag: "{tag}"',
        f'barcan_role: "{role_name}"',
        f'source_file: "{row["tag_file"]}"',
        f"source_line: {row['source_line']}",
        f'source_principle: "{str(row["principle"]).replace(chr(34), chr(39))}"',
        f'publication_anchor: "{anchor.replace(chr(34), chr(39))}"',
        'evidence_status: "role_grounded_with_publication_anchor"',
        "pattern_count: 10",
        "personal_patterns_unique: true",
        "common_patterns_excluded: true",
        "---",
        "",
        f"# {name}",
        "",
        f"**BARCAN tag:** `{tag}` - {role_name}",
        f"**Role focus:** {tag_focus}",
        f"**Project role:** {row['role']}",
        f"**Source principle:** {row['principle']}",
        f"**Publication anchor:** {anchor}",
        f"**Project source:** [`{row['tag_file']}:{row['source_line']}`](../../{row['tag_file']}#L{row['source_line']})",
        "",
        "## Interpretation Boundary",
        "",
        "- These are programming micro-patterns inspired by the role-file principle and the listed publication anchor.",
        "- They are not claims that the philosopher wrote software-engineering advice.",
        "- Broad patterns that fit more than five philosophers are excluded and kept in the common file.",
        "",
        "## 10 Personal Programming Patterns",
        "",
        "| # | Personal pattern | Publication-grounded idea | Defect prevented | Agent rule |",
        "|---:|---|---|---|---|",
    ]
    for pattern in patterns:
        lines.append(
            f"| {pattern['n']} | `{pattern['id']}` - {pattern['name']} | {pattern['publication_anchor']} | {pattern['defect_prevented']} | {pattern['agent_rule']} |"
        )
    lines.extend(
        [
            "",
            "## Common Patterns Kept Out",
            "",
            "Use the common file for broad engineering practices such as contracts, type-driven design, property-based testing, merge queues, route ownership, generated-artifact authority and containerized toolchains:",
            "",
            "- [Common Analytic Programming Patterns](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)",
            "- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)",
            "",
            "## RAG Instruction",
            "",
            f"When a task belongs to `{tag}` and needs the individual voice of {name}, retrieve this file after the common ACP file. Use the 10 patterns as a focused review lens, then attach concrete code evidence before approving work.",
            "",
        ]
    )
    target = PEOPLE_DIR / file_name
    target.write_text("\n".join(lines), encoding="utf-8")
    return {
        **row,
        "philosopher_id": philosopher_id,
        "file": f"philosophers/{file_name}",
        "publication_anchor": anchor,
        "patterns": patterns,
    }


def write_readme(entries: list[dict[str, object]]) -> None:
    lines = [
        "# Philosopher Patterns RAG Corpus",
        "",
        f"Generated from `BARCAN-TAG-*.md`: {len(TAG_TITLES)} role files, {len(entries)} philosophers, 10 unique personal programming patterns per philosopher.",
        "",
        "## Files",
        "",
        "- [`00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`](00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md) - shared world-class practices that fit more than five philosophers.",
        "- [`01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md`](01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md) - anti-conflict rules extracted from the session's PR repair work.",
        "- [`PHILOSOPHER_INDEX.md`](PHILOSOPHER_INDEX.md) - navigation table for all philosophers.",
        "- [`philosopher_patterns_index.json`](philosopher_patterns_index.json) - machine-readable RAG index.",
        "- [`QA_REPORT.md`](QA_REPORT.md) - exact count and uniqueness checks.",
        "- [`philosophers/`](philosophers/) - one file per philosopher.",
        "",
        "## Hard Invariant",
        "",
        "Each philosopher file has exactly 10 personal patterns. If a pattern becomes useful for more than five philosophers, move it to the common file and replace it with a philosopher-specific micro-pattern.",
        "",
    ]
    (OUT / "README.md").write_text("\n".join(lines), encoding="utf-8")


def write_index(entries: list[dict[str, object]]) -> None:
    lines = [
        "# Philosopher Pattern Index",
        "",
        "| # | BARCAN tag | Philosopher | Principle | File |",
        "|---:|---|---|---|---|",
    ]
    for number, item in enumerate(entries, start=1):
        file_path = str(item["file"]).replace(" ", "%20")
        file_name = Path(str(item["file"])).name
        lines.append(f"| {number} | `{item['tag']}` | {item['name_ru']} | {item['principle']} | [`{file_name}`]({file_path}) |")
    (OUT / "PHILOSOPHER_INDEX.md").write_text("\n".join(lines), encoding="utf-8")


def write_json(entries: list[dict[str, object]], pattern_rows: list[dict[str, str]]) -> None:
    data = {
        "generated_on": date.today().isoformat(),
        "source": "BARCAN-TAG-*.md",
        "counts": {
            "barcan_files": len(list(ROOT.glob("BARCAN-TAG-*.md"))),
            "philosophers": len(entries),
            "personal_patterns": len(pattern_rows),
            "common_patterns": len(COMMON_PATTERNS),
            "conflict_prevention_rules": len(CONFLICT_PREVENTION_RULES),
        },
        "common_patterns": [
            {"id": pid, "name": name, "technique": technique, "why_defect_preventing": why}
            for pid, name, technique, why in COMMON_PATTERNS
        ],
        "conflict_prevention_rules": [
            {"id": rid, "name": name, "requirement": requirement}
            for rid, name, requirement in CONFLICT_PREVENTION_RULES
        ],
        "philosophers": entries,
    }
    (OUT / "philosopher_patterns_index.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def write_qa(rows: list[dict[str, object]], entries: list[dict[str, object]], pattern_rows: list[dict[str, str]]) -> None:
    ids = [pattern["id"] for pattern in pattern_rows]
    names = [pattern["name"] for pattern in pattern_rows]
    id_counts = Counter(ids)
    name_counts = Counter(names)
    philosopher_files = sorted(PEOPLE_DIR.glob("*.md"))
    per_file_counts = {}
    for path in philosopher_files:
        text = path.read_text(encoding="utf-8")
        per_file_counts[path.name] = len(re.findall(r"^\| \d+ \| `", text, flags=re.MULTILINE))
    bad_files = {name: count for name, count in per_file_counts.items() if count != 10}
    duplicate_ids = {key: value for key, value in id_counts.items() if value > 1}
    duplicate_names = {key: value for key, value in name_counts.items() if value > 1}
    status = (
        len(rows) == 78
        and len(entries) == 78
        and len(philosopher_files) == 78
        and len(pattern_rows) == 780
        and len(id_counts) == 780
        and len(name_counts) == 780
        and not bad_files
    )
    lines = [
        "# QA Report",
        "",
        "| Check | Result |",
        "|---|---:|",
        f"| Source BARCAN files | {len(list(ROOT.glob('BARCAN-TAG-*.md')))} |",
        f"| Source philosopher rows | {len(rows)} |",
        f"| Generated philosopher files | {len(philosopher_files)} |",
        f"| Personal pattern entries | {len(pattern_rows)} |",
        f"| Unique personal pattern IDs | {len(id_counts)} |",
        f"| Unique personal pattern names | {len(name_counts)} |",
        f"| Duplicate personal pattern IDs | {len(duplicate_ids)} |",
        f"| Duplicate personal pattern names | {len(duplicate_names)} |",
        f"| Files not equal to 10 patterns | {len(bad_files)} |",
        f"| Common analytic patterns | {len(COMMON_PATTERNS)} |",
        f"| Conflict prevention rules | {len(CONFLICT_PREVENTION_RULES)} |",
        "",
        "## Verdict",
        "",
        "PASS: exactly 78 philosopher files and 780 unique personal patterns." if status else "FAIL: count or uniqueness invariant is broken.",
    ]
    if bad_files:
        lines.extend(["", "## Bad File Counts"])
        for name, count in sorted(bad_files.items()):
            lines.append(f"- `{name}`: {count}")
    if duplicate_ids:
        lines.extend(["", "## Duplicate IDs"])
        for key, value in sorted(duplicate_ids.items()):
            lines.append(f"- `{key}`: {value}")
    if duplicate_names:
        lines.extend(["", "## Duplicate Names"])
        for key, value in sorted(duplicate_names.items()):
            lines.append(f"- `{key}`: {value}")
    (OUT / "QA_REPORT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    rows = extract_rows()
    if len(rows) != 78:
        raise SystemExit(f"Expected 78 philosopher rows, found {len(rows)}")
    missing_tags = sorted({str(row["tag"]) for row in rows} - set(TAG_TITLES))
    if missing_tags:
        raise SystemExit(f"Missing TAG_TITLES entries: {missing_tags}")
    missing_anchors = sorted({str(row["name_ru"]) for row in rows} - set(PUBLICATION_ANCHORS))
    if missing_anchors:
        raise SystemExit(f"Missing PUBLICATION_ANCHORS entries: {missing_anchors}")

    clean_output()
    write_common_patterns()
    write_conflict_prevention_charter()
    entries = [write_philosopher_file(row) for row in rows]
    pattern_rows = [pattern for entry in entries for pattern in entry["patterns"]]
    write_readme(entries)
    write_index(entries)
    write_json(entries, pattern_rows)
    write_qa(rows, entries, pattern_rows)

    print(
        json.dumps(
            {
                "out": str(OUT),
                "barcan_files": len(list(ROOT.glob("BARCAN-TAG-*.md"))),
                "philosophers": len(entries),
                "philosopher_files": len(list(PEOPLE_DIR.glob("*.md"))),
                "personal_patterns": len(pattern_rows),
                "unique_personal_pattern_ids": len({pattern["id"] for pattern in pattern_rows}),
                "unique_personal_pattern_names": len({pattern["name"] for pattern in pattern_rows}),
                "common_patterns": len(COMMON_PATTERNS),
                "conflict_prevention_rules": len(CONFLICT_PREVENTION_RULES),
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
