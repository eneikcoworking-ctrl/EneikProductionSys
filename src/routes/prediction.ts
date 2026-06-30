/**
 * @file prediction.ts
 * @agent TAG-02 (Rigid Designator)
 * @description Routes for satisfaction predictions.
 */

import { PredictionController } from '../controllers/core/PredictionController';

const controller = new PredictionController();

/**
 * @route POST /api/predict
 */
export const predict = async (userContext: string) => {
  return await controller.predictSatisfaction(userContext);
};
