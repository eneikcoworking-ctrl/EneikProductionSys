// Internal role tags (e.g. "BARCAN-TAG-02") and doctrine codenames are the
// system's internal role architecture and must never be shown to end users -
// they're confusing to non-technical viewers and expose internal design.
// This maps them to plain pseudo job titles for any user-facing display.
const ROLE_TITLES: Record<string, string> = {
  'BARCAN-TAG-00': 'AI Code Reviewer',
  'BARCAN-TAG-01': 'AI Solutions Architect',
  'BARCAN-TAG-02': 'AI Backend Engineer',
  'BARCAN-TAG-03': 'AI Product Designer',
  'BARCAN-TAG-04': 'AI Prompt Engineer',
  'BARCAN-TAG-05': 'AI DevOps Engineer',
  'BARCAN-TAG-06': 'AI QA Engineer',
  'BARCAN-TAG-07': 'AI Security Engineer',
  'BARCAN-TAG-08': 'AI Database Engineer',
  'BARCAN-TAG-09': 'AI Delivery Manager',
  'BARCAN-TAG-10': 'AI Compliance Analyst',
  'BARCAN-TAG-11': 'AI Frontend Engineer',
  'BARCAN-TAG-12': 'AI API Architect'
};

export function roleDisplayName(roleTag: string | null | undefined): string {
  if (!roleTag) {
    return 'AI Specialist';
  }
  return ROLE_TITLES[roleTag] || 'AI Specialist';
}
