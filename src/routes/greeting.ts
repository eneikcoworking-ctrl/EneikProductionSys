/**
 * @file greeting.ts
 * @agent TAG-02 (Rigid Designator)
 * @description Routes for greeting retrieval.
 */

import { GreetingController } from '../controllers/core/GreetingController';

const controller = new GreetingController();

/**
 * @route GET /api/greeting/:id
 */
export const getGreeting = async (id: string) => {
  return await controller.getGreetingById(id);
};
