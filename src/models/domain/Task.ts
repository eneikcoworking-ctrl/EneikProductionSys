/**
 * @file Task.ts
 * @agent TAG-01 (Actualist Object)
 * @description Defines the Ontological Status of a Task within the Agency.
 */

import { IEntity } from './Landing';

export enum TaskStatus {
  TODO = 'TODO',
  IN_PROGRESS = 'IN_PROGRESS',
  DONE = 'DONE',
  FAILED = 'FAILED',
}

export interface ITask extends IEntity {
  description: string;
  agentTag: string; // e.g., BARCAN-TAG-01
  status: TaskStatus;
  createdAt: Date;
}

export class Task implements ITask {
  public readonly id: string;
  public readonly description: string;
  public readonly agentTag: string;
  public status: TaskStatus;
  public readonly createdAt: Date;

  constructor(id: string, description: string, agentTag: string) {
    if (!description || description.trim().length === 0) {
      throw new Error("Task must have a description (Actualist Principle)");
    }
    if (!agentTag || !agentTag.startsWith("BARCAN-TAG-")) {
      throw new Error("Task must be assigned to a valid BARCAN agent");
    }
    this.id = id;
    this.description = description;
    this.agentTag = agentTag;
    this.status = TaskStatus.TODO;
    this.createdAt = new Date();
  }
}
