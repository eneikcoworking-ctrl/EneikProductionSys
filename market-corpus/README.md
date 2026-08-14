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

## Kano class belongs to a pair, not to a capability

Stock control is Must-Be for a wholesaler and irrelevant for a landing page. So a Kano class is always
stated for a (capability, profile) pair - never for a capability on its own.

Classes are also not permanent. Kano observed that attributes decay: what delights becomes expected, then
mandatory. Wi-Fi in hotels made that trip in a decade. Therefore an `observed` entry records **when** it was
measured and **what share** of real products had it - without those, an entry cannot be re-checked later and
becomes dogma.
