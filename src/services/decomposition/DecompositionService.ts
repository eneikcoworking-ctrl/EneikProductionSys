import { Task, TaskStatus } from '../../models/domain/Task';
import { RULES, ALWAYS_REVIEW_TAGS } from './rules';

export class DecompositionService {
  public async decompose(requirementText: string, sourceRequirementId: string): Promise<Task[]> {
    const matchedTags = new Set<string>();
    const lowerText = requirementText.toLowerCase();
    let ruleMatched = false;

    for (const rule of RULES) {
      if (rule.keywords.some(k => lowerText.includes(k))) {
        rule.tags.forEach(t => matchedTags.add(t));
        ruleMatched = true;
      }
    }

    if (!ruleMatched) {
      console.warn(`не удалось классифицировать, нужна ручная разметка: ${requirementText}`);
    }

    ALWAYS_REVIEW_TAGS.forEach(t => matchedTags.add(t));

    const tasks = [...matchedTags].map(tag => {
      const task = new Task(
        Math.random().toString(36).substring(2, 9),
        `[${tag}] ${requirementText}`,
        tag
      );
      // In TS model TaskStatus is TODO/IN_PROGRESS/DONE/FAILED, but the prompt says status: 'queued'
      // We'll use a local mapping or just cast if needed, but the prompt example used:
      // status: 'queued', payload: { requirementText, sourceRequirementId }
      // The Task class doesn't have payload in current Task.ts
      return {
        ...task,
        status: 'queued',
        payload: { requirementText, sourceRequirementId }
      } as any;
    });

    return tasks;
  }
}
