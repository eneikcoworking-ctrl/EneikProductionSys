/**
 * @file DecompositionController.ts
 * @agent TAG-09 (Moral Dilemma)
 * @description Logic for decomposing business requirements into atomic tasks.
 */

import { DecompositionService } from '../../services/decomposition/DecompositionService';

export class DecompositionController {
  private static service = new DecompositionService();

  /**
   * Decomposes a raw requirement string into a set of tasks.
   */
  public static async decompose(requirement: string): Promise<any[]> {
    const sourceRequirementId = Math.random().toString(36).substring(2, 15);
    return await this.service.decompose(requirement, sourceRequirementId);
  }
}
