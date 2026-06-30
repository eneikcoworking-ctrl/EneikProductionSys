/**
 * @file hello.ts
 * @agent TAG-02 (Rigid Designator)
 * @description Fixed route for the Hello World greeting.
 */

import { Greeting } from '../models/domain/Landing';

/**
 * @route GET /api/hello
 * @returns {Greeting} - The fixed designator for the landing page.
 */
export const getHello = async () => {
  // Rigidly designated ID and Message
  const fixedId = "00000000-0000-0000-0000-000000000001";
  const message = "Hello World: The Agency is Operational.";

  return new Greeting(fixedId, message);
};
