from __future__ import annotations

import json
import hashlib
import re
import shutil
from collections import Counter
from datetime import date
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "philosopher-patterns"
PEOPLE_DIR = OUT / "philosophers"

EXPECTED_BARCAN_FILES = 13
EXPECTED_PHILOSOPHERS = 78
MIN_PERSONAL_PATTERNS = 20
COMMON_THRESHOLD_PHILOSOPHERS = 5


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
    ("ACP-061", "Hoare Triple Review", "State precondition, command and postcondition before touching critical code", "Prevents code that is locally plausible but globally unproved."),
    ("ACP-062", "Temporal Specification", "Describe safety and liveness properties for workflows", "Catches impossible lifecycle promises and stuck-state defects early."),
    ("ACP-063", "Model Checking", "Explore finite state spaces with tools such as TLA+, Alloy or equivalent", "Finds interleavings and corner states missed by example tests."),
    ("ACP-064", "State-Space Reduction", "Collapse irrelevant states before verification", "Keeps formal checks tractable without losing the defect class under review."),
    ("ACP-065", "Algebraic Data Types", "Represent alternatives as tagged sums and products", "Prevents invalid combinations from being representable."),
    ("ACP-066", "Refinement Types", "Attach predicates to values where the language or tooling supports it", "Moves range, format and permission defects into type or check time."),
    ("ACP-067", "Dependent Type Boundary", "Use proof-carrying values at the highest-risk interfaces", "Prevents consumers from assuming facts that were never established."),
    ("ACP-068", "SMT Constraint Check", "Encode conflicting rules as satisfiability constraints", "Finds inconsistent requirements before implementation spreads them."),
    ("ACP-069", "Abstract Interpretation", "Approximate program behavior over safe abstract domains", "Detects classes of runtime errors without executing every path."),
    ("ACP-070", "Separation Logic Ownership", "Prove mutable resources have non-overlapping owners", "Prevents aliasing bugs, leaks and hidden shared mutation."),
    ("ACP-071", "Linear Resource Discipline", "Use single-use or affine ownership for scarce effects", "Stops duplicate sends, double frees and repeated irreversible actions."),
    ("ACP-072", "Lock Ordering Protocol", "Acquire shared locks in one global order", "Prevents deadlocks during parallel execution."),
    ("ACP-073", "Lease Fencing Token", "Every worker mutation carries a monotonic lease token", "Prevents stale workers from overwriting newer authority."),
    ("ACP-074", "Monotonic State Design", "Prefer state transitions that only move forward in a lattice", "Prevents rollback races and non-convergent replicas."),
    ("ACP-075", "CRDT Convergence", "Use commutative, associative and idempotent merge operations", "Lets distributed edits converge without central locking."),
    ("ACP-076", "Lattice-Based Merge", "Represent partial knowledge with join/meet operations", "Prevents ad hoc conflict resolution from losing information."),
    ("ACP-077", "Deterministic Replay", "Persist inputs, time and decisions enough to replay incidents", "Turns intermittent production failures into reproducible traces."),
    ("ACP-078", "Event Sourcing with Projection Tests", "Derive current state from an append-only event log", "Prevents silent history loss and makes state reconstruction auditable."),
    ("ACP-079", "Metamorphic Testing", "Assert relations between transformed inputs and outputs", "Finds bugs when no single oracle is available."),
    ("ACP-080", "Differential Testing", "Compare independent implementations or versions on the same cases", "Finds semantic drift across adapters, clients and runtimes."),
    ("ACP-081", "Coverage-Guided Fuzzing", "Generate hostile inputs guided by executed paths", "Finds parser, validation and memory defects missed by examples."),
    ("ACP-082", "Combinatorial Interaction Testing", "Cover pairwise or t-wise parameter interactions", "Reduces configuration defects without exhaustive test explosion."),
    ("ACP-083", "Symbolic Execution", "Explore path conditions instead of concrete examples only", "Finds branch-specific defects before production traffic finds them."),
    ("ACP-084", "Taint Tracking", "Mark untrusted data and verify every sink is protected", "Prevents injection and authorization bypass through hidden data flow."),
    ("ACP-085", "Information Flow Control", "Enforce allowed movement between confidentiality and integrity levels", "Prevents sensitive or untrusted data from crossing forbidden boundaries."),
    ("ACP-086", "Capability-Based Authority", "Pass explicit unforgeable permissions instead of ambient authority", "Prevents confused-deputy and over-permission defects."),
    ("ACP-087", "Formal Grammar Boundary", "Parse external languages with an explicit grammar", "Prevents partial parser acceptance and ambiguous syntax handling."),
    ("ACP-088", "Parser Serializer Round Trip", "Parse, serialize and parse again with semantic equality checks", "Prevents lossy transformations and migration corruption."),
    ("ACP-089", "Canonical Intermediate Representation", "Normalize equivalent forms before comparison and transformation", "Prevents duplicate identities and representation-dependent behavior."),
    ("ACP-090", "Total Function Interface", "Every public function defines behavior for every input class", "Prevents implicit undefined behavior at module boundaries."),
    ("ACP-091", "Option and Result Types", "Represent absence and failure explicitly", "Prevents null dereferences and swallowed errors."),
    ("ACP-092", "Resource Scope Guard", "Bind acquisition and release to lexical or transaction scope", "Prevents leaks after exceptions and early returns."),
    ("ACP-093", "Compatibility Matrix", "Track supported producer/consumer and schema version pairs", "Prevents accidental deployment of incompatible components."),
    ("ACP-094", "Ontology Registry", "Centralize domain vocabulary, ownership and canonical meanings", "Prevents duplicate concepts with different operational semantics."),
    ("ACP-095", "Trace Context Propagation", "Carry correlation context through every async and remote boundary", "Prevents orphaned logs and untraceable workflow failures."),
    ("ACP-096", "SLO Error Budget Gate", "Use service objectives to decide release and rollback policy", "Prevents local feature progress from consuming reliability silently."),
    ("ACP-097", "Chaos Experiment", "Inject controlled dependency, latency and infrastructure faults", "Finds resilience gaps before real incidents compound them."),
    ("ACP-098", "Kill Switch", "Every risky external effect has a fast disable path", "Prevents prolonged damage from a bad deployment or dependency change."),
    ("ACP-099", "Shadow Traffic Verification", "Run new behavior beside old behavior before user-visible cutover", "Finds semantic differences without exposing clients."),
    ("ACP-100", "Canary Invariant Monitor", "Bind rollout progression to live invariant checks", "Prevents a canary from advancing after hidden correctness drift."),
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


TAG_AXES = {
    "BARCAN-TAG-00_CODE-GUARDIAN": {"language", "review", "meaning", "integration", "evidence", "conflict"},
    "BARCAN-TAG-01_ACTUALIST-OBJECT": {"ontology", "identity", "boundary", "composition", "types", "domain"},
    "BARCAN-TAG-02_RIGID-DESIGNATOR": {"reference", "identity", "api", "naming", "compatibility", "context"},
    "BARCAN-TAG-03_BELIEF-INTENSION": {"cognitive", "ux", "agency", "perception", "feedback", "context"},
    "BARCAN-TAG-04_MODAL-QUANTIFIER": {"uncertainty", "evidence", "model", "probability", "causal", "prediction"},
    "BARCAN-TAG-05_NECESSARY-IDENTITY": {"identity", "causal", "runtime", "history", "replay", "composition"},
    "BARCAN-TAG-06_DEONTIC-CONSISTENCY": {"logic", "truth", "testing", "counterexample", "verification", "status"},
    "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE": {"epistemic", "security", "authority", "information", "validation", "evidence"},
    "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE": {"logic", "types", "substitution", "data", "lineage", "computation"},
    "BARCAN-TAG-09_MORAL-DILEMMA": {"pragmatic", "value", "tradeoff", "coherence", "interpretation", "cost"},
    "BARCAN-TAG-10_DEONTIC-PROHIBITION": {"normative", "policy", "law", "authority", "exception", "permission"},
    "BARCAN-TAG-11_CLIENT-PERCEPTION": {"cognitive", "perception", "ux", "information", "identity", "accessibility"},
    "BARCAN-TAG-12_SOCIAL-CONTRACT": {"coordination", "commitment", "institution", "planning", "contract", "parallel"},
}


AXIS_KEYWORDS = [
    (("language", "meaning", "speech", "conversation", "sense", "reference", "denoting", "interpretation"), "language"),
    (("ontology", "being", "object", "parts", "whole", "composition", "essence", "places"), "ontology"),
    (("identity", "necessity", "persistence", "continuity", "supervenience"), "identity"),
    (("modal", "possible", "world", "intension", "necessity"), "modality"),
    (("reference", "designation", "indexical", "externalism"), "reference"),
    (("probability", "decision", "belief", "uncertainty", "forecast"), "uncertainty"),
    (("causal", "causation", "intervention", "experiment", "measurement"), "causal"),
    (("truth", "logic", "formalized", "paraconsistent", "four-valued", "minimalist"), "logic"),
    (("knowledge", "epistemology", "evidence", "knowing", "skepticism", "reliabilism"), "epistemic"),
    (("information", "computability", "lambda", "type theory", "propositions as types"), "information"),
    (("law", "deontic", "obligation", "permission", "rights", "duties", "authority", "rules"), "normative"),
    (("perception", "mind", "consciousness", "body", "cognition", "concepts", "phenomenal"), "cognitive"),
    (("action", "intention", "agency", "plan", "practical"), "agency"),
    (("social", "contract", "convention", "joint", "institution", "shared"), "coordination"),
    (("pragmatism", "value", "capabilities", "reasons", "coherence"), "pragmatic"),
]


DEFECT_TAXONOMY = [
    ("D001", "Semantic drift", "A name, rule or interface keeps the old spelling but changes meaning."),
    ("D002", "Invalid state", "The code permits a domain state the role principle forbids."),
    ("D003", "Contract drift", "Producer and consumer assumptions diverge."),
    ("D004", "Concurrency conflict", "Two agents or workers claim the same mutable surface."),
    ("D005", "Partial distributed failure", "One side effect lands while the matching state/event does not."),
    ("D006", "Authorization ambiguity", "Authority, permission or prohibition is inferred instead of proven."),
    ("D007", "Evidence gap", "The agent cannot cite code, test, trace or source evidence for a claim."),
    ("D008", "False green", "A check reports success without covering the relevant behavior."),
    ("D009", "Substitution failure", "Replacement preserves shape but not semantics."),
    ("D010", "Data lineage loss", "A value loses origin, identity or transformation history."),
    ("D011", "Perception failure", "The UI is technically present but not usable, visible or accessible."),
    ("D012", "Policy contradiction", "Two rules can require incompatible actions."),
    ("D013", "Runtime drift", "The deployed runtime no longer corresponds to the trusted repository state."),
    ("D014", "RAG hallucination", "The agent applies doctrine without exact retrieved grounding."),
]


PERSONAL_SLOT_POOL = [
    {
        "key": "anchor-bound-name",
        "title": "Anchor-Bound Name",
        "axes": {"language", "reference", "naming", "api"},
        "rule": "Treat every exported name as a promise bound to the philosopher's source principle and publication anchor.",
        "proof": "Show that renamed or newly introduced symbols preserve the same referent across caller contexts.",
        "defect": "D001",
    },
    {
        "key": "sense-reference-split",
        "title": "Sense Reference Split",
        "axes": {"language", "reference", "context"},
        "rule": "Separate what a value is called, what it denotes and how clients are expected to understand it.",
        "proof": "Add a contract or test proving display label, persisted identifier and API identity cannot be confused.",
        "defect": "D009",
    },
    {
        "key": "category-error-scan",
        "title": "Category Error Scan",
        "axes": {"language", "review", "ontology", "types"},
        "rule": "Reject code that treats a process as an object, an observation as authority or a policy as data without an adapter.",
        "proof": "Point to the type, schema or adapter that preserves the category boundary.",
        "defect": "D002",
    },
    {
        "key": "performative-commit",
        "title": "Performative Commit",
        "axes": {"language", "commitment", "coordination", "contract"},
        "rule": "When code declares a status, event or approval, require the matching operational consequence to exist.",
        "proof": "Trace the declaration to the database transition, emitted event or review gate it performs.",
        "defect": "D003",
    },
    {
        "key": "conversation-maxim",
        "title": "Conversation Maxim Check",
        "axes": {"language", "review", "evidence", "ux"},
        "rule": "Make agent output sufficiently informative, true, relevant and non-ambiguous for the next worker.",
        "proof": "Show the minimal status fields, evidence links and next-action fields consumed downstream.",
        "defect": "D007",
    },
    {
        "key": "world-version-map",
        "title": "World Version Map",
        "axes": {"ontology", "context", "compatibility", "history"},
        "rule": "Represent competing runtime, schema or client worlds explicitly instead of pretending one world covers all clients.",
        "proof": "Provide a version matrix or migration path covering each supported world.",
        "defect": "D003",
    },
    {
        "key": "actual-object-register",
        "title": "Actual Object Register",
        "axes": {"ontology", "domain", "identity", "types"},
        "rule": "Create code only for actual domain objects with owner, identity, lifecycle and deletion semantics.",
        "proof": "Link the object to its aggregate, repository boundary or canonical registry entry.",
        "defect": "D002",
    },
    {
        "key": "part-whole-ownership",
        "title": "Part Whole Ownership",
        "axes": {"ontology", "composition", "boundary", "ownership"},
        "rule": "Make part ownership and whole invariants explicit before splitting modules, tables or services.",
        "proof": "Show which aggregate or service is allowed to mutate each part.",
        "defect": "D004",
    },
    {
        "key": "boundary-topology",
        "title": "Boundary Topology",
        "axes": {"ontology", "boundary", "api", "domain"},
        "rule": "Define the exact boundary where validation, authorization, persistence or ownership changes hands.",
        "proof": "Add a boundary test or diagram-backed code reference for the handoff.",
        "defect": "D006",
    },
    {
        "key": "essence-before-option",
        "title": "Essence Before Option",
        "axes": {"ontology", "identity", "modality", "types"},
        "rule": "Before adding a configurable option, state the invariant that must remain true in every permitted mode.",
        "proof": "Show that configuration branches preserve the invariant or fail closed.",
        "defect": "D002",
    },
    {
        "key": "rigid-api-referent",
        "title": "Rigid API Referent",
        "axes": {"reference", "api", "identity", "compatibility"},
        "rule": "Keep public identifiers tied to the same behavior across versions unless a formal migration says otherwise.",
        "proof": "Run or add consumer contract evidence for the old and new referent.",
        "defect": "D003",
    },
    {
        "key": "intension-compatibility",
        "title": "Intension Compatibility Split",
        "axes": {"reference", "context", "modality", "compatibility"},
        "rule": "Separate what a feature means to existing clients from what it means in the new implementation context.",
        "proof": "Show compatibility tests for the old interpretation and targeted tests for the new one.",
        "defect": "D001",
    },
    {
        "key": "indexical-context-lock",
        "title": "Indexical Context Lock",
        "axes": {"reference", "context", "ux", "identity"},
        "rule": "Never let terms like current, owner, user, latest or active float without an explicit context object.",
        "proof": "Point to the context source and tests that prevent cross-user or stale-context leakage.",
        "defect": "D006",
    },
    {
        "key": "extended-cognition-tool",
        "title": "Extended Cognition Tool",
        "axes": {"cognitive", "ux", "information", "feedback"},
        "rule": "Treat the UI, dashboard or RAG chunk as part of the agent's thinking loop, not passive decoration.",
        "proof": "Show that the tool exposes the state, uncertainty and next action needed for correct decisions.",
        "defect": "D011",
    },
    {
        "key": "perception-action-loop",
        "title": "Perception Action Loop",
        "axes": {"cognitive", "perception", "agency", "feedback"},
        "rule": "Every user or agent action must produce perceivable feedback that closes the loop.",
        "proof": "Verify visible state, loading, success and failure transitions.",
        "defect": "D011",
    },
    {
        "key": "self-model-sanity",
        "title": "Self Model Sanity Check",
        "axes": {"cognitive", "identity", "runtime", "evidence"},
        "rule": "Make the system state it is acting from explicit before it makes an irreversible decision.",
        "proof": "Capture project id, branch, commit, runtime SHA and task state in the decision record.",
        "defect": "D013",
    },
    {
        "key": "belief-update-ledger",
        "title": "Belief Update Ledger",
        "axes": {"epistemic", "uncertainty", "evidence", "model"},
        "rule": "Record what evidence changed the agent's belief and what uncertainty remains.",
        "proof": "Attach before/after confidence, source evidence and unresolved hypotheses.",
        "defect": "D007",
    },
    {
        "key": "decision-expected-loss",
        "title": "Expected Loss Gate",
        "axes": {"uncertainty", "probability", "prediction", "value"},
        "rule": "Choose a repair or merge action by minimizing expected defect cost, not by local convenience.",
        "proof": "State failure probability, blast radius and rollback cost for the selected action.",
        "defect": "D005",
    },
    {
        "key": "falsification-harness",
        "title": "Falsification Harness",
        "axes": {"logic", "testing", "counterexample", "verification"},
        "rule": "Write the check that would refute the agent's claim before accepting the claim.",
        "proof": "Show a failing counterexample or a targeted passing test that would fail under the defect.",
        "defect": "D008",
    },
    {
        "key": "truth-status-table",
        "title": "Truth Status Table",
        "axes": {"logic", "truth", "status", "evidence"},
        "rule": "Represent true, false, unknown and inconsistent states explicitly in orchestration status.",
        "proof": "Show how each state is displayed, stored and resolved.",
        "defect": "D012",
    },
    {
        "key": "paraconsistent-quarantine",
        "title": "Paraconsistent Quarantine",
        "axes": {"logic", "truth", "conflict", "status"},
        "rule": "If evidence conflicts, isolate the contradiction and keep safe operations moving around it.",
        "proof": "Show the quarantined claim, blocked action and allowed independent action.",
        "defect": "D012",
    },
    {
        "key": "knowledge-first-gate",
        "title": "Knowledge First Gate",
        "axes": {"epistemic", "security", "validation", "evidence"},
        "rule": "Do not authorize a risky action from belief or intention alone; require knowledge-grade evidence.",
        "proof": "Attach the check, trace or permission source that makes the claim knowledge-grade.",
        "defect": "D006",
    },
    {
        "key": "reliability-chain",
        "title": "Reliability Chain",
        "axes": {"epistemic", "information", "evidence", "validation"},
        "rule": "Trust data only when its acquisition process is reliable for this defect class.",
        "proof": "Show source, timestamp, freshness rule and validation path.",
        "defect": "D010",
    },
    {
        "key": "substitution-oracle",
        "title": "Substitution Oracle",
        "axes": {"logic", "substitution", "types", "data"},
        "rule": "Before replacing code, dependency, model or schema, prove preservation under the relevant observations.",
        "proof": "Run equivalence, contract or golden-master evidence for the replacement.",
        "defect": "D009",
    },
    {
        "key": "constructive-proof-object",
        "title": "Constructive Proof Object",
        "axes": {"logic", "types", "computation", "verification"},
        "rule": "Represent successful completion as a value carrying the evidence needed by the next step.",
        "proof": "Show the typed result or artifact that cannot exist without satisfying the preconditions.",
        "defect": "D007",
    },
    {
        "key": "level-of-abstraction-lock",
        "title": "Level Of Abstraction Lock",
        "axes": {"information", "data", "ontology", "context"},
        "rule": "Keep claims, metrics and identifiers at one declared abstraction level until an explicit transform changes level.",
        "proof": "Show source and target abstraction levels plus the transformation contract.",
        "defect": "D010",
    },
    {
        "key": "lambda-core-reduction",
        "title": "Lambda Core Reduction",
        "axes": {"computation", "logic", "types", "verification"},
        "rule": "Reduce complex behavior to a small pure core before adding effects, adapters or orchestration.",
        "proof": "Show the pure decision function and tests independent from IO.",
        "defect": "D008",
    },
    {
        "key": "inferential-scoreboard",
        "title": "Inferential Scoreboard",
        "axes": {"pragmatic", "coherence", "review", "evidence"},
        "rule": "Track what each code claim commits the agent to and what would count against it.",
        "proof": "Show commitments, entitlement evidence and incompatibility checks.",
        "defect": "D001",
    },
    {
        "key": "holism-impact-map",
        "title": "Holism Impact Map",
        "axes": {"pragmatic", "coherence", "integration", "cost"},
        "rule": "Assess a local change by its effect on the surrounding web of tests, contracts and user workflows.",
        "proof": "List the affected neighbors and evidence for each integration boundary.",
        "defect": "D003",
    },
    {
        "key": "anti-mirror-telemetry",
        "title": "Anti Mirror Telemetry",
        "axes": {"pragmatic", "evidence", "runtime", "interpretation"},
        "rule": "Prefer operational telemetry over the agent's internal story about what the system is doing.",
        "proof": "Cite logs, metrics, health checks or dashboard state for the actual runtime.",
        "defect": "D013",
    },
    {
        "key": "prohibition-as-code",
        "title": "Prohibition As Code",
        "axes": {"normative", "policy", "law", "permission"},
        "rule": "Turn every forbidden action into an executable denial path with an explainable reason.",
        "proof": "Show the policy rule, denial test and user-visible or agent-visible explanation.",
        "defect": "D006",
    },
    {
        "key": "defeasible-exception-ledger",
        "title": "Defeasible Exception Ledger",
        "axes": {"normative", "exception", "policy", "evidence"},
        "rule": "Allow exceptions only when the defeating reason is explicit, scoped and audited.",
        "proof": "Show expiry, scope, approver and compensating check for the exception.",
        "defect": "D012",
    },
    {
        "key": "rights-duties-matrix",
        "title": "Rights Duties Matrix",
        "axes": {"normative", "authority", "permission", "contract"},
        "rule": "Map every actor's claim right, duty, privilege and power before implementing permissions.",
        "proof": "Show the matrix and tests for at least one allowed and one denied action per relation.",
        "defect": "D006",
    },
    {
        "key": "principled-integrity",
        "title": "Principled Integrity Check",
        "axes": {"normative", "coherence", "policy", "review"},
        "rule": "Reject local fixes that satisfy a rule text while violating the system's declared principle.",
        "proof": "Show the higher-level principle and the concrete behavior that preserves it.",
        "defect": "D012",
    },
    {
        "key": "capability-floor",
        "title": "Capability Floor",
        "axes": {"value", "ux", "accessibility", "cognitive"},
        "rule": "Treat user capability loss as a correctness defect, not a cosmetic issue.",
        "proof": "Show keyboard, screen-reader, error-recovery or low-resource evidence for the workflow.",
        "defect": "D011",
    },
    {
        "key": "teleosemantic-feedback",
        "title": "Teleosemantic Feedback",
        "axes": {"cognitive", "information", "ux", "feedback"},
        "rule": "A signal is valid only if it helps the user or agent perform the function it is meant to support.",
        "proof": "Show how the signal changes the next action and prevents a mistaken action.",
        "defect": "D011",
    },
    {
        "key": "supervenience-watch",
        "title": "Supervenience Watch",
        "axes": {"identity", "runtime", "information", "cognitive"},
        "rule": "If visible state changes, require a corresponding lower-level state change that explains it.",
        "proof": "Trace UI, API, database and event state for the same entity.",
        "defect": "D013",
    },
    {
        "key": "convention-stability",
        "title": "Convention Stability",
        "axes": {"coordination", "contract", "parallel", "compatibility"},
        "rule": "Preserve shared conventions until all participants can switch together or compatibility is proven.",
        "proof": "Show the migration path, owner approval and compatibility evidence.",
        "defect": "D004",
    },
    {
        "key": "planning-consistency",
        "title": "Planning Consistency",
        "axes": {"coordination", "planning", "commitment", "parallel"},
        "rule": "Ensure task plans, PRs, branches and agent claims form one consistent plan state.",
        "proof": "Show dashboard, GitHub and runtime state agreeing on the next action.",
        "defect": "D004",
    },
    {
        "key": "joint-commitment-lock",
        "title": "Joint Commitment Lock",
        "axes": {"coordination", "commitment", "institution", "parallel"},
        "rule": "Once agents share a contract, no agent may silently reinterpret its obligations.",
        "proof": "Show the shared contract, claimant, touched paths and review evidence.",
        "defect": "D004",
    },
    {
        "key": "institutional-fact-register",
        "title": "Institutional Fact Register",
        "axes": {"coordination", "institution", "contract", "status"},
        "rule": "Treat statuses like approved, merged, blocked or done as institutional facts backed by rules.",
        "proof": "Show the rule that creates the status and the audit event that records it.",
        "defect": "D007",
    },
    {
        "key": "causal-process-trace",
        "title": "Causal Process Trace",
        "axes": {"causal", "runtime", "history", "evidence"},
        "rule": "Explain incidents through causal chains, not adjacent symptoms.",
        "proof": "Trace trigger, mechanism, state change and observable effect.",
        "defect": "D013",
    },
    {
        "key": "inus-factor-check",
        "title": "INUS Factor Check",
        "axes": {"causal", "logic", "history", "evidence"},
        "rule": "Treat a suspected cause as one factor in a sufficient package until alternatives are eliminated.",
        "proof": "List required co-factors and the evidence that each was present or absent.",
        "defect": "D007",
    },
    {
        "key": "persistence-snapshot",
        "title": "Persistence Snapshot",
        "axes": {"identity", "history", "runtime", "replay"},
        "rule": "For long-lived entities, preserve identity across snapshots, migrations and incident reconstruction.",
        "proof": "Show stable identifiers and replay evidence across time points.",
        "defect": "D010",
    },
    {
        "key": "rag-grounding-capsule",
        "title": "RAG Grounding Capsule",
        "axes": {"evidence", "information", "review", "context"},
        "rule": "Store the philosopher-specific rule as a small retrievable chunk with source, score and defect class.",
        "proof": "Cite the exact source row, publication anchor and selected defect taxonomy item.",
        "defect": "D014",
    },
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


def normalize_text(value: object) -> str:
    return str(value).lower()


def axes_from_text(*values: object) -> set[str]:
    text = " ".join(normalize_text(value) for value in values)
    axes: set[str] = set()
    for keywords, axis in AXIS_KEYWORDS:
        if any(keyword in text for keyword in keywords):
            axes.add(axis)
    return axes


def stable_jitter(philosopher_key: str, slot_key: str) -> float:
    digest = hashlib.sha256(f"{philosopher_key}:{slot_key}".encode("utf-8")).hexdigest()
    return int(digest[:6], 16) / 16_777_215_000


def role_axes(row: dict[str, object]) -> set[str]:
    return axes_from_text(row.get("role", ""), row.get("focus", ""), row.get("principle", ""))


def philosopher_axes(row: dict[str, object], anchor: str) -> tuple[set[str], set[str], set[str]]:
    tag = str(row["tag"])
    tag_axis_set = set(TAG_AXES[tag])
    anchor_axis_set = axes_from_text(anchor)
    role_axis_set = role_axes(row)
    return tag_axis_set, anchor_axis_set, role_axis_set


def score_slot(row: dict[str, object], anchor: str, slot: dict[str, object]) -> float:
    tag_axis_set, anchor_axis_set, role_axis_set = philosopher_axes(row, anchor)
    slot_axes = set(slot["axes"])
    philosopher_key = f"{row['tag']}:{row['ordinal']}:{row['name_ru']}"
    return (
        3.0 * len(slot_axes & tag_axis_set)
        + 2.0 * len(slot_axes & anchor_axis_set)
        + 1.0 * len(slot_axes & role_axis_set)
        + stable_jitter(str(philosopher_key), str(slot["key"]))
    )


def theme_from_anchor(anchor: str) -> str:
    if " - " in anchor:
        theme = anchor.split(" - ", 1)[1]
    else:
        theme = anchor
    cleaned = re.sub(r"[^A-Za-z0-9 /-]+", " ", theme)
    words = [word for word in re.split(r"[\s/-]+", cleaned) if word]
    return " ".join(words[:4]).title() or "Role Principle"


def select_personal_slots(row: dict[str, object], anchor: str) -> list[dict[str, object]]:
    ranked = sorted(
        PERSONAL_SLOT_POOL,
        key=lambda slot: (-score_slot(row, anchor, slot), str(slot["key"])),
    )
    if len(ranked) < MIN_PERSONAL_PATTERNS:
        raise SystemExit(
            f"PERSONAL_SLOT_POOL has {len(ranked)} slots, below MIN_PERSONAL_PATTERNS={MIN_PERSONAL_PATTERNS}"
        )
    return ranked[:MIN_PERSONAL_PATTERNS]


def taxonomy_name(defect_id: str) -> str:
    for item_id, name, _description in DEFECT_TAXONOMY:
        if item_id == defect_id:
            return name
    return "Unclassified defect"


def make_personal_patterns(row: dict[str, object]) -> list[dict[str, str]]:
    name = str(row["name_ru"])
    slug = slugify(name)
    principle = str(row["principle"])
    tag = str(row["tag"])
    defect = ROLE_DEFECTS[tag]
    anchor = PUBLICATION_ANCHORS.get(name, f"Role-file principle: {principle}")
    theme = theme_from_anchor(anchor)
    patterns = []
    for number, slot in enumerate(select_personal_slots(row, anchor), start=1):
        slot_key = str(slot["key"])
        slot_title = str(slot["title"])
        slot_score = score_slot(row, anchor, slot)
        slot_axes = ", ".join(sorted(set(slot["axes"])))
        defect_id = str(slot["defect"])
        patterns.append(
            {
                "n": str(number),
                "id": pattern_id(slug, number, slot_key),
                "name": f"{name}: {theme} - {slot_title}",
                "slot_key": slot_key,
                "axes": slot_axes,
                "fit_score": f"{slot_score:.6f}",
                "defect_class": f"{defect_id} {taxonomy_name(defect_id)}",
                "publication_anchor": anchor,
                "defect_prevented": f"{defect}; personal failure mode: losing '{principle}' while coding.",
                "agent_rule": (
                    f"{slot['rule']} Proof obligation: {slot['proof']} "
                    f"Apply it only as a {name}-specific micro-pattern selected by deterministic RAG score; "
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
        f"These patterns are intentionally shared. A concrete reusable practice belongs here when it fits more than {COMMON_THRESHOLD_PHILOSOPHERS} philosophers in the BARCAN corpus, so it is not duplicated inside philosopher files.",
        "",
        "| ID | Pattern | Technique | Defect Prevention | RAG Rule |",
        "|---|---|---|---|---|",
    ]
    for pid, name, technique, why in COMMON_PATTERNS:
        lines.append(
            f"| `{pid}` | {name} | {technique} | {why} | Retrieve as common background; do not copy into a philosopher's personal patterns. |"
        )
    lines.extend(
        [
            "",
            "## RAG Retrieval Rule",
            "",
            "1. Retrieve this file first for universally reusable engineering practices.",
            "2. Retrieve exactly one philosopher file for individual style and judgment.",
            f"3. If a new concrete personal pattern starts fitting more than {COMMON_THRESHOLD_PHILOSOPHERS} philosophers, move it here and replace it in those philosopher files.",
            "4. For mathematical assignment and QA rules, retrieve `02_RAG_MATHEMATICAL_ASSIGNMENT_MODEL.md`.",
            "5. For parallel development tasks, also retrieve `01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md`.",
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


def write_rag_assignment_model() -> None:
    taxonomy_rows = [
        f"| `{item_id}` | {name} | {description} |" for item_id, name, description in DEFECT_TAXONOMY
    ]
    axis_rows = [
        f"| `{tag}` | {', '.join(sorted(axes))} |" for tag, axes in sorted(TAG_AXES.items())
    ]
    lines = [
        "# RAG Mathematical Assignment Model",
        "",
        "This file defines the reproducible assignment rule for the philosopher-pattern corpus. It is the authority for generators, QA and retrieval policy.",
        "",
        "## Sets",
        "",
        f"- `F`: philosopher rows extracted from `BARCAN-TAG-*.md`; expected cardinality is `{EXPECTED_PHILOSOPHERS}`.",
        "- `C`: common analytic programming patterns, stored in `00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`.",
        "- `S`: parameterized philosopher-specific micro-pattern slots in the generator.",
        "- `D`: defect taxonomy used as the target prevention space.",
        "",
        "## Common Pattern Rule",
        "",
        f"A concrete reusable engineering practice is common iff `fit_count(pattern) > {COMMON_THRESHOLD_PHILOSOPHERS}`. Common practices stay in `C` and must not be copied into philosopher files as personal patterns.",
        "",
        "## Personal Assignment Score",
        "",
        "For philosopher `f` and slot `s`:",
        "",
        "```text",
        "score(f, s) =",
        "  3 * |axes(tag(f)) intersect axes(s)|",
        "  + 2 * |axes(publication_anchor(f)) intersect axes(s)|",
        "  + 1 * |axes(role/focus/principle(f)) intersect axes(s)|",
        "  + stable_hash_jitter(f, s)",
        "```",
        "",
        "The hash term is deterministic and smaller than `0.001`; it only breaks ties without changing substantive ranking.",
        "",
        "## Selection Rule",
        "",
        f"For each philosopher, sort `S` by descending `score(f, s)` and then by slot key. Select the first `{MIN_PERSONAL_PATTERNS}` slots. This is a lower-bound invariant: future generators may select more, but QA fails if a philosopher receives fewer.",
        "",
        "## RAG Retrieval Policy",
        "",
        "1. Retrieve `00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md` for reusable defect-prevention techniques.",
        "2. Retrieve this model file when the agent needs to explain why a philosopher received a pattern.",
        "3. Retrieve exactly one philosopher file for individual style, principle and defect lens.",
        "4. For parallel development, also retrieve `01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md`.",
        "5. A generated answer must cite the source row, publication anchor, selected pattern ID and defect class before applying a doctrine.",
        "",
        "## QA Invariants",
        "",
        f"- Exactly `{EXPECTED_BARCAN_FILES}` source BARCAN files.",
        f"- Exactly `{EXPECTED_PHILOSOPHERS}` philosopher source rows and generated philosopher files.",
        f"- At least `{MIN_PERSONAL_PATTERNS}` personal patterns per philosopher.",
        "- Personal pattern IDs are globally unique.",
        "- Personal pattern names are globally unique.",
        "- Common patterns and personal patterns are separated by the common-threshold rule.",
        "",
        "## Tag Axes",
        "",
        "| Tag | Axes |",
        "|---|---|",
        *axis_rows,
        "",
        "## Defect Taxonomy",
        "",
        "| ID | Defect class | Description |",
        "|---|---|---|",
        *taxonomy_rows,
        "",
    ]
    (OUT / "02_RAG_MATHEMATICAL_ASSIGNMENT_MODEL.md").write_text("\n".join(lines), encoding="utf-8")


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
        f"pattern_count: {len(patterns)}",
        f"minimum_personal_patterns: {MIN_PERSONAL_PATTERNS}",
        "personal_patterns_unique: true",
        "common_patterns_excluded: true",
        f"assignment_model: \"score_top_{MIN_PERSONAL_PATTERNS}_deterministic_axes\"",
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
        f"- Broad patterns that fit more than {COMMON_THRESHOLD_PHILOSOPHERS} philosophers are excluded and kept in the common file.",
        "- Pattern selection follows the deterministic scoring model in `02_RAG_MATHEMATICAL_ASSIGNMENT_MODEL.md`.",
        "",
        f"## Personal Programming Patterns ({len(patterns)}, minimum {MIN_PERSONAL_PATTERNS})",
        "",
        "| # | Personal pattern | Axes / score | Publication-grounded idea | Defect prevented | Agent rule |",
        "|---:|---|---|---|---|---|",
    ]
    for pattern in patterns:
        lines.append(
            f"| {pattern['n']} | `{pattern['id']}` - {pattern['name']} | {pattern['axes']}; score `{pattern['fit_score']}`; {pattern['defect_class']} | {pattern['publication_anchor']} | {pattern['defect_prevented']} | {pattern['agent_rule']} |"
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
            "- [RAG Mathematical Assignment Model](../02_RAG_MATHEMATICAL_ASSIGNMENT_MODEL.md)",
            "",
            "## RAG Instruction",
            "",
            f"When a task belongs to `{tag}` and needs the individual voice of {name}, retrieve this file after the common ACP file. Use these {len(patterns)} patterns as a focused review lens, then attach concrete code evidence before approving work.",
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
        f"Generated from `BARCAN-TAG-*.md`: {len(TAG_TITLES)} role files, {len(entries)} philosophers, at least {MIN_PERSONAL_PATTERNS} unique personal programming patterns per philosopher.",
        "",
        "## Files",
        "",
        f"- [`00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`](00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md) - shared world-class practices that fit more than {COMMON_THRESHOLD_PHILOSOPHERS} philosophers.",
        "- [`01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md`](01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md) - anti-conflict rules extracted from the session's PR repair work.",
        "- [`02_RAG_MATHEMATICAL_ASSIGNMENT_MODEL.md`](02_RAG_MATHEMATICAL_ASSIGNMENT_MODEL.md) - deterministic scoring, threshold and QA contract for RAG.",
        "- [`PHILOSOPHER_INDEX.md`](PHILOSOPHER_INDEX.md) - navigation table for all philosophers.",
        "- [`philosopher_patterns_index.json`](philosopher_patterns_index.json) - machine-readable RAG index.",
        "- [`QA_REPORT.md`](QA_REPORT.md) - exact count and uniqueness checks.",
        "- [`philosophers/`](philosophers/) - one file per philosopher.",
        "",
        "## Hard Invariant",
        "",
        f"Each philosopher file has at least {MIN_PERSONAL_PATTERNS} personal patterns. If a concrete pattern becomes useful for more than {COMMON_THRESHOLD_PHILOSOPHERS} philosophers, move it to the common file and replace it with a philosopher-specific micro-pattern.",
        "",
        "## Retrieval Order",
        "",
        "1. Retrieve common ACP patterns.",
        "2. Retrieve the mathematical assignment model when explanation or audit is needed.",
        "3. Retrieve the one philosopher file matching the active BARCAN role.",
        "4. Retrieve the parallel-development charter for merge, PR, queue, schema or route work.",
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
        "assignment_model": {
            "minimum_personal_patterns": MIN_PERSONAL_PATTERNS,
            "common_threshold_philosophers": COMMON_THRESHOLD_PHILOSOPHERS,
            "score_formula": "3*tag_axis_overlap + 2*publication_anchor_axis_overlap + 1*role_focus_principle_axis_overlap + stable_hash_jitter",
            "common_rule": "concrete reusable practice moves to common layer when fit_count > common_threshold_philosophers",
            "personal_rule": "select top minimum_personal_patterns slots by deterministic score per philosopher",
        },
        "counts": {
            "barcan_files": len(list(ROOT.glob("BARCAN-TAG-*.md"))),
            "philosophers": len(entries),
            "personal_patterns": len(pattern_rows),
            "minimum_personal_patterns": MIN_PERSONAL_PATTERNS,
            "common_patterns": len(COMMON_PATTERNS),
            "conflict_prevention_rules": len(CONFLICT_PREVENTION_RULES),
            "defect_taxonomy_classes": len(DEFECT_TAXONOMY),
        },
        "defect_taxonomy": [
            {"id": item_id, "name": name, "description": description}
            for item_id, name, description in DEFECT_TAXONOMY
        ],
        "tag_axes": {tag: sorted(axes) for tag, axes in TAG_AXES.items()},
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
    bad_files = {name: count for name, count in per_file_counts.items() if count < MIN_PERSONAL_PATTERNS}
    duplicate_ids = {key: value for key, value in id_counts.items() if value > 1}
    duplicate_names = {key: value for key, value in name_counts.items() if value > 1}
    expected_min_patterns = len(entries) * MIN_PERSONAL_PATTERNS
    status = (
        len(list(ROOT.glob("BARCAN-TAG-*.md"))) == EXPECTED_BARCAN_FILES
        and len(rows) == EXPECTED_PHILOSOPHERS
        and len(entries) == EXPECTED_PHILOSOPHERS
        and len(philosopher_files) == EXPECTED_PHILOSOPHERS
        and len(pattern_rows) >= expected_min_patterns
        and len(id_counts) == len(pattern_rows)
        and len(name_counts) == len(pattern_rows)
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
        f"| Minimum required personal patterns | {expected_min_patterns} |",
        f"| Unique personal pattern IDs | {len(id_counts)} |",
        f"| Unique personal pattern names | {len(name_counts)} |",
        f"| Duplicate personal pattern IDs | {len(duplicate_ids)} |",
        f"| Duplicate personal pattern names | {len(duplicate_names)} |",
        f"| Files below {MIN_PERSONAL_PATTERNS} patterns | {len(bad_files)} |",
        f"| Common analytic patterns | {len(COMMON_PATTERNS)} |",
        f"| Conflict prevention rules | {len(CONFLICT_PREVENTION_RULES)} |",
        f"| Defect taxonomy classes | {len(DEFECT_TAXONOMY)} |",
        f"| Common threshold philosophers | > {COMMON_THRESHOLD_PHILOSOPHERS} |",
        "",
        "## Verdict",
        "",
        (
            f"PASS: exactly {EXPECTED_PHILOSOPHERS} philosopher files and at least {expected_min_patterns} globally unique personal patterns."
            if status
            else "FAIL: count, lower-bound or uniqueness invariant is broken."
        ),
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
    if len(rows) != EXPECTED_PHILOSOPHERS:
        raise SystemExit(f"Expected {EXPECTED_PHILOSOPHERS} philosopher rows, found {len(rows)}")
    barcan_file_count = len(list(ROOT.glob("BARCAN-TAG-*.md")))
    if barcan_file_count != EXPECTED_BARCAN_FILES:
        raise SystemExit(f"Expected {EXPECTED_BARCAN_FILES} BARCAN files, found {barcan_file_count}")
    missing_tags = sorted({str(row["tag"]) for row in rows} - set(TAG_TITLES))
    if missing_tags:
        raise SystemExit(f"Missing TAG_TITLES entries: {missing_tags}")
    missing_anchors = sorted({str(row["name_ru"]) for row in rows} - set(PUBLICATION_ANCHORS))
    if missing_anchors:
        raise SystemExit(f"Missing PUBLICATION_ANCHORS entries: {missing_anchors}")

    clean_output()
    write_common_patterns()
    write_conflict_prevention_charter()
    write_rag_assignment_model()
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
                "barcan_files": barcan_file_count,
                "philosophers": len(entries),
                "philosopher_files": len(list(PEOPLE_DIR.glob("*.md"))),
                "personal_patterns": len(pattern_rows),
                "minimum_personal_patterns": MIN_PERSONAL_PATTERNS,
                "unique_personal_pattern_ids": len({pattern["id"] for pattern in pattern_rows}),
                "unique_personal_pattern_names": len({pattern["name"] for pattern in pattern_rows}),
                "common_patterns": len(COMMON_PATTERNS),
                "conflict_prevention_rules": len(CONFLICT_PREVENTION_RULES),
                "defect_taxonomy_classes": len(DEFECT_TAXONOMY),
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
