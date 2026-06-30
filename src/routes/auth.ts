/**
 * @file auth.ts
 * @agent TAG-02 (Rigid Designator)
 * @description Routes for authentication.
 */

import { AuthController } from '../controllers/auth/AuthController';

const controller = new AuthController();

/**
 * @route POST /api/auth
 */
export const authenticate = async (token: string) => {
  return await controller.authenticate(token);
};
