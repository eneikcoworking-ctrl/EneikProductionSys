/**
 * @file Landing.ts
 * @agent TAG-01 (Actualist Object)
 * @description Defines the Ontological Status of the Landing Page Greeting.
 */

export interface IEntity {
  id: string;
}

export class Greeting implements IEntity {
  public readonly id: string;
  public readonly message: string;
  public readonly timestamp: Date;

  constructor(id: string, message: string) {
    if (!message || message.trim().length === 0) {
      throw new Error("Greeting must have a real, existing message (Actualist Principle)");
    }
    this.id = id;
    this.message = message;
    this.timestamp = new Date();
  }

  public getFormattedGreeting(): string {
    return `${this.message} (Verified at ${this.timestamp.toISOString()})`;
  }
}
