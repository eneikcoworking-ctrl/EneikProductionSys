/**
 * @file hello.ts
 * @agent TAG-02 (Rigid Designator)
 * @description Fixed route for the Hello World greeting, now using GreetingController.
 */

import { GreetingController } from '../controllers/core/GreetingController';

const controller = new GreetingController();

/**
 * @route GET /api/hello
 * @returns {Greeting} - The fixed designator for the landing page.
 */
export const getHello = async () => {
  return await controller.getHello();
};
