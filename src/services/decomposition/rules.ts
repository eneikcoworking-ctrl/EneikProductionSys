export interface DecompositionRule {
  keywords: string[];
  tags: string[];
}

export const RULES: DecompositionRule[] = [
  { keywords: ['ui', 'форм', 'экран', 'frontend'], tags: ['BARCAN-TAG-03', 'BARCAN-TAG-11'] },
  { keywords: ['данны', 'база', 'таблиц', 'schema'], tags: ['BARCAN-TAG-08'] },
  { keywords: ['api', 'backend', 'endpoint'], tags: ['BARCAN-TAG-02'] },
  { keywords: ['auth', 'парол', 'логин', 'безопасн'], tags: ['BARCAN-TAG-07'] },
];

export const ALWAYS_REVIEW_TAGS = ['BARCAN-TAG-00', 'BARCAN-TAG-01'];
