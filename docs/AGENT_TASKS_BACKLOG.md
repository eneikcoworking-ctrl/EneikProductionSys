# AGENT BACKLOG: 7 CRITICAL PRODUCTION TASKS

These tasks are designed for AI Agents to execute independently within their respective domain boundaries. All tasks target the "Automation Gap" identified in the diagnosis.

---

## TASK 1: IMPLEMENT THE "JULES" AUDIT ENGINE (MVP)
- **Agent**: `BARCAN-TAG-00` (Code Guardian)
- **Domain**: `scripts/audit_pr.py`
- **Description**: Develop the Python script that implements the logic defined in `docs/JULES_AUDIT_MANIFEST.md`.
- **Constraint**: Must use `git diff` to identify modified files and cross-reference them with `docs/DIRECTORY_MAP.md`.
- **DOD**: Script runs locally and correctly fails a PR if a `Model` file imports a `View` component.

## TASK 2: LINEAR-TO-GITHUB AGENTIC BRIDGE
- **Agent**: `BARCAN-TAG-09` (Mediator)
- **Domain**: `scripts/linear_sync.py`
- **Description**: Create a script that uses the Linear API to fetch tasks tagged with `BARCAN-TAG-XX`.
- **Constraint**: The script should output a JSON file containing the current "Active Context" (Task ID, Description, DOD).
- **DOD**: Successfully fetches at least one open task from the repository's Linear project.

## TASK 3: PROVISION BASE INFRASTRUCTURE (IaC)
- **Agent**: `BARCAN-TAG-05` (Necessary Identity)
- **Domain**: `infrastructure/terraform/`
- **Description**: Write Terraform scripts for the base environment (S3 bucket for artifacts, VPC, and IAM roles for agents).
- **Constraint**: Must follow the "Account Mapping" in `docs/DIRECTORY_MAP.md`.
- **DOD**: `terraform plan` executes without errors.

## TASK 4: DEONTIC PRIVACY MIDDLEWARE (JAVA)
- **Agent**: `BARCAN-TAG-10` (Deontic Prohibition)
- **Domain**: `src/main/java/com/eneik/production/controllers/policy/`
- **Description**: Convert the `PrivacyFilter` from a utility class into a Spring Boot `@ControllerAdvice` or `Filter` bean.
- **Constraint**: Must automatically mask any field annotated with a custom `@PII` annotation in the domain entities.
- **DOD**: Unit test confirms `@PII` fields are masked in JSON response.

## TASK 5: CORE WEB VITALS AUTOMATED AUDIT
- **Agent**: `BARCAN-TAG-11` (Client Perception)
- **Domain**: `.github/workflows/performance_audit.yml`
- **Description**: Configure a GitHub Action that runs `lighthouse-ci` on every PR affecting the `src/views/` directory.
- **Constraint**: Must block the PR if LCP (Largest Contentful Paint) is > 2.5s.
- **DOD**: Workflow file is valid and triggers on PRs to `main`.

## TASK 6: DATA LINEAGE & SCHEMA CATALOG
- **Agent**: `BARCAN-TAG-08` (Substitutivity Salva Veritate)
- **Domain**: `docs/data/DATA_CATALOG.md`
- **Description**: Create a comprehensive data catalog for the "Hello World" project.
- **Constraint**: Map `src/models/persistence/LandingSchema.sql` fields to their corresponding domain types in `Greeting.java`.
- **DOD**: Catalog contains 100% of the persistence fields with data lineage descriptions.

## TASK 7: SECURE KNOWLEDGE VAULT INTEGRATION
- **Agent**: `BARCAN-TAG-07` (Second-Order Knowledge)
- **Domain**: `src/main/java/com/eneik/production/config/SecurityConfig.java`
- **Description**: Implement Spring Security to protect the `/api/` routes using JWT.
- **Constraint**: Use a placeholder for the secret but ensure the logic is prepared for HashiCorp Vault integration.
- **DOD**: `/api/hello` returns 401 Unauthorized when no token is provided in the header.
