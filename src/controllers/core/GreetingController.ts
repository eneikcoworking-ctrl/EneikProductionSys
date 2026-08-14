/**
 * @file GreetingController.ts
 * @agent TAG-01 (Actualist Object)
 * @description Orchestrates Greeting domain logic with simulated persistence.
 */

import { Greeting } from '../../models/domain/Landing';

export class GreetingController {
  /**
   * Retrieves the fixed "Hello World" greeting (Rigid Designator).
   */
  public async getHello(): Promise<Greeting> {
    const fixedId = "00000000-0000-0000-0000-000000000001";
    const message = "Hello World: The Agency is Operational.";
    return new Greeting(fixedId, message);
  }

  /**
   * Retrieves a greeting by its unique identifier.
   * Simulates retrieval from the persistence layer (TAG-08).
   */
  public async getGreetingById(id: string): Promise<Greeting> {
    // Rigid Boundary: Ensure ID is valid UUID format
    if (!this.isValidUuid(id)) {
      throw new Error(`Invalid Identity: ${id}. Only rigidly designated UUIDs are accepted.`);
    }

    // In a real system, this would execute: SELECT * FROM greetings WHERE id = ?
    // Ref: src/models/persistence/LandingSchema.sql
    // For now, we return an Actualist representation of the requested ID.
    return new Greeting(id, `Actualist Greeting for Entity ${id}`);
  }

  private isValidUuid(id: string): boolean {
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    return uuidRegex.test(id);
  }
}
