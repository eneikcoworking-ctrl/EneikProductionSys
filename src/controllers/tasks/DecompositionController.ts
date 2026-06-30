/**
 * @file DecompositionController.ts
 * @agent TAG-09 (Moral Dilemma)
 * @description Logic for decomposing business requirements into atomic tasks.
 */

import { Task } from '../../models/domain/Task';

export class DecompositionController {
  /**
   * Decomposes a raw requirement string into a set of tasks.
   * In a real scenario, this might involve LLM calls or complex parsing.
   * Here we implement a rule-based pragmatic approach.
   */
  public static decompose(requirement: string): Task[] {
    const tasks: Task[] = [];
    const idPrefix = Math.random().toString(36).substring(2, 9);

    // Rule 1: Always needs a Tech Lead / Reviewer
    tasks.push(new Task(`${idPrefix}-00`, "Architecture review and final code guardian", "BARCAN-TAG-00"));

    // Rule 2: If mentions "UI", "frontend", "window", "screen" -> TAG-03 and TAG-11
    if (/ui|frontend|window|screen|page|интерфейс|окно/i.test(requirement)) {
      tasks.push(new Task(`${idPrefix}-03`, "Design the UI components and states in Figma", "BARCAN-TAG-03"));
      tasks.push(new Task(`${idPrefix}-11`, "Implement the frontend React components", "BARCAN-TAG-11"));
    }

    // Rule 3: If mentions "data", "database", "save", "storage" -> TAG-08
    if (/data|database|save|storage|бд|данные/i.test(requirement)) {
      tasks.push(new Task(`${idPrefix}-08`, "Design and implement persistence layer / schema", "BARCAN-TAG-08"));
    }

    // Rule 4: If mentions "API", "backend", "server" -> TAG-02
    if (/api|backend|server|бэкенд|сервер/i.test(requirement)) {
      tasks.push(new Task(`${idPrefix}-02`, "Implement API endpoints and integration logic", "BARCAN-TAG-02"));
    }

    // Rule 5: If mentions "secure", "auth", "login" -> TAG-07
    if (/secure|auth|login|безопасность|вход/i.test(requirement)) {
      tasks.push(new Task(`${idPrefix}-07`, "Security audit and AppSec implementation", "BARCAN-TAG-07"));
    }

    // Default: Solution Architect review
    tasks.push(new Task(`${idPrefix}-01`, "Ontological verification of the solution", "BARCAN-TAG-01"));

    return tasks;
  }
}
