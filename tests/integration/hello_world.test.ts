/**
 * @file hello_world.test.ts
 * @agent TAG-06 (Deontic Consistency)
 * @description Integration test for the Hello World pipeline.
 */

import { getHello } from '../../src/routes/hello';
import { PrivacyFilter } from '../../src/controllers/policy/PrivacyFilter';

describe('Hello World Pipeline Integration', () => {
  it('should return a valid, unmasked greeting for authorized users', async () => {
    const greeting = await getHello();
    const token = "VALID_KNOWLEDGE_TOKEN";

    expect(greeting.message).toBe("Hello World: The Agency is Operational.");
    expect(PrivacyFilter.verifyKnowledge(token)).toBe(true);
  });

  it('should reject access without valid knowledge token', () => {
    const token = "INVALID";
    expect(PrivacyFilter.verifyKnowledge(token)).toBe(false);
  });
});
