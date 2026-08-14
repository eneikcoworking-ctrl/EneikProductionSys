# Market corpus

What software of a given kind must contain, independent of what any one client thought to ask for.

The compiler builds only what the brief describes. Clients know their business, not what products of
their class are expected to contain - so briefs arrive with predictable holes, and the flow faithfully
reproduces them. This corpus is the missing half: knowledge about the market, versioned as files so it can
be reviewed, argued with and corrected, exactly like the BARCAN-TAG role charters.

## Every entry carries where it came from

The single rule that keeps this from becoming folklore. `source` is mandatory, and `status` decides what an
entry is allowed to do:

| status | meaning | may block work | may add work |
|---|---|---|---|
| `statutory` | a legal requirement, cited to act and article | yes | yes |
| `standard` | a technical standard, cited to clause | yes | yes |
| `observed` | a measured share across real products, with date and sample size | yes | yes |
| `literature` | a published result, cited, with its method stated | no | yes |
| `hypothesis` | plausible, unverified - including anything an AI wrote from general knowledge | no | no |

`hypothesis` entries exist to be promoted or refuted, never to decide anything. This is what separates the
corpus from guessing: an unverified belief is stored and labelled rather than acted upon.

## Files

- `capabilities.json` - capabilities a product can have, the jobs each one serves, and what makes each
  mandatory or merely nice. Capabilities, not product types: an online shop and a booking system overlap by
  most of their content, and describing each type whole would duplicate that overlap and let the copies
  drift apart.
- `profiles.json` - product types as weights over capabilities. A real product is rarely one type; it gets
  a mix, and an uncertain classification weakens every downstream decision instead of silently guessing.

## A product has value chains, plural (schema v2)

Value multiplies along the chain a product exists to complete, so the weakest link sets the worth of the
whole thing and a broken link zeroes it. Schema v1 recorded one chain per profile. That was wrong for a
whole class of products, and wrong in a way the completeness check could not see:

- a **marketplace** has a buyer chain and a seller chain, and a flawless buyer journey with no workable way
  to list an item is a dead product;
- **stock and order handling** has a material chain and a money chain, and correct books do not keep the
  line running when the stock figures lie;
- a **subscription** has a purchase chain and a subscription-life chain, and it is nearly always the second
  one - renewal failures, plan changes, cancelling without a fight - that is missing.

Since each chain multiplies internally, the chains multiply with each other too: one intact chain does not
rescue a product whose other chain is broken. `valuePaths` is therefore a list, each entry naming the actor
or flow it belongs to, and some carry `appliesWhen` because they exist only under a condition (a game only
has a monetisation chain if it sells something).

## Profiles are detected from the plan's own words

Most statutory duties are scoped to a kind of product. Each profile carries `detectionKeywords` so the
compliance gate can tell which kind a plan describes from its own text, by the same evidential method it
uses for coverage. Without this the gate reports a game's age-rating duty against every shop plan - and a
check that reports obvious nonsense is one people stop reading, which costs more than the duty it catches.

Finding no profile is **not** the same as finding that none applies: with no evidence either way, every
duty is considered, because narrowing scope on absent evidence is exactly how an obligation goes missing.

## Games are the exception to completeness

Every other profile here is transactional: the user already wants the outcome and will complain when a step
breaks, and a complete working chain is most of the job. A game is neither. Nobody owes it their attention,
so a broken link produces silent abandonment rather than a report - which makes outcome measurement the only
channel through which failure is observable at all. And fun does not decompose into features: a game can
satisfy every requirement in this corpus and still be worthless.

So for games the floors are a minimum that says nothing about whether the product is good, and no
completeness check may be read as evidence that it is.

## Kano class belongs to a pair, not to a capability

Stock control is Must-Be for a wholesaler and irrelevant for a landing page. So a Kano class is always
stated for a (capability, profile) pair - never for a capability on its own.

Classes are also not permanent. Kano observed that attributes decay: what delights becomes expected, then
mandatory. Wi-Fi in hotels made that trip in a decade. Therefore an `observed` entry records **when** it was
measured and **what share** of real products had it - without those, an entry cannot be re-checked later and
becomes dogma.
