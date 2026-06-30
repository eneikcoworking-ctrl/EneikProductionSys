/**
 * @file task_decomposition.test.ts
 * @agent TAG-06 (Deontic Consistency)
 * @description Integration test for task decomposition logic.
 */

import { DecompositionController } from '../../src/controllers/tasks/DecompositionController';

describe('Task Decomposition Logic', () => {
  it('should decompose UI requirements into frontend tasks', () => {
    const requirement = "Create a new UI window for settings";
    const tasks = DecompositionController.decompose(requirement);

    const hasUIAgent = tasks.some(t => t.agentTag === 'BARCAN-TAG-11');
    const hasDesignAgent = tasks.some(t => t.agentTag === 'BARCAN-TAG-03');

    expect(hasUIAgent).toBe(true);
    expect(hasDesignAgent).toBe(true);
  });

  it('should always include a tech lead (TAG-00)', () => {
    const tasks = DecompositionController.decompose("Anything");
    const hasTechLead = tasks.some(t => t.agentTag === 'BARCAN-TAG-00');
    expect(hasTechLead).toBe(true);
  });
});
