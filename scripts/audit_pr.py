#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PR Boundary Guard (scripts/audit_pr.py)
Author: Jules (BARCAN-TAG-00 Code Guardian)
Description:
Statically audits modified files in the pull request against the role's designated directories
mapped in docs/DIRECTORY_MAP.md. Prevents directory scope crossing to preserve MVC architecture boundaries.
"""

import sys
import os
import subprocess


def get_changed_files():
    """Retrieves the list of changed files compared to origin/main or HEAD~1."""
    try:
        # Try diffing against origin/main first
        result = subprocess.run(
            ["git", "diff", "--name-only", "origin/main...HEAD"],
            capture_output=True,
            text=True,
            check=True
        )
        files = result.stdout.strip().split("\n")
        return [f.strip() for f in files if f.strip()]
    except Exception:
        try:
            # Fallback to HEAD~1 if origin/main is not available
            result = subprocess.run(
                ["git", "diff", "--name-only", "HEAD~1"],
                capture_output=True,
                text=True,
                check=True
            )
            files = result.stdout.strip().split("\n")
            return [f.strip() for f in files if f.strip()]
        except Exception as e:
            print(f"Warning: Could not retrieve changed files via git diff: {e}")
            return []


def is_relevant_for_role(role_tag, file_path):
    """
    Verifies if a specific file_path is permitted for the given role tag
    based on directories mapped in docs/DIRECTORY_MAP.md.
    """
    lower = file_path.lower()

    # Always allow common lock, config, ignore, and documentation files to be edited by anyone
    if any(lower.endswith(ext) for ext in [
        "-lock.json", "package-lock.json", "pnpm-lock.yaml", ".gitignore",
        ".env.example", ".npmrc", ".md"
    ]):
        return True

    # ACC-01: Code Guardian / Tech Lead (TAG-00) and Technical Product Manager (TAG-09)
    # have global integration and review rights across all layers
    if role_tag in ["BARCAN-TAG-00", "BARCAN-TAG-09"]:
        return True

    # ACC-02: Solution Architect (TAG-01) & Backend Engineer (TAG-02)
    if role_tag == "BARCAN-TAG-01":
        # Models: domain entities & blueprints
        if "src/main/java/com/eneik/production/models/domain/" in file_path or "docs/" in lower:
            return True
        return False

    if role_tag == "BARCAN-TAG-02":
        # REST controllers & GreetingController (exclude policy & auth subfolders)
        if "src/main/java/com/eneik/production/controllers/" in file_path:
            if any(term in lower for term in ["/policy/", "/auth/"]):
                return False
            return True
        # Services & general backend logic
        if "src/main/java/com/eneik/production/services/" in file_path:
            return True
        return False

    # ACC-04: UI/UX Designer (TAG-03) & Frontend Engineer (TAG-11)
    if role_tag == "BARCAN-TAG-03":
        # Atomic design system components & styles
        if "src/views/components/" in lower or "design/" in lower or lower.endswith(".css"):
            return True
        return False

    if role_tag == "BARCAN-TAG-11":
        # Frontend pages & application state
        if any(term in lower for term in ["src/views/pages/", "src/views/states/", "frontend/"]):
            return True
        if any(lower.endswith(ext) for ext in [".svelte", ".tsx", ".jsx", ".ts", ".js"]):
            # Prevent frontend modifying java or python backend folders
            if not any(term in lower for term in ["src/main/", "src/models/", "tests/"]):
                return True
        return False

    # ACC-03: ML Engineer (TAG-04) & Data Engineer / DBA (TAG-08)
    if role_tag == "BARCAN-TAG-04":
        # Java ML models & ML FastAPI service files
        if "src/main/java/com/eneik/production/models/ml/" in file_path or "src/models/ml/" in lower:
            return True
        return False

    if role_tag == "BARCAN-TAG-08":
        # Database entities & flyway migrations
        if "src/main/java/com/eneik/production/models/persistence/" in file_path:
            return True
        if "src/main/resources/db/migration/" in lower:
            return True
        return False

    # ACC-05: DevOps / SRE (TAG-05)
    if role_tag == "BARCAN-TAG-05":
        # workflows, dockerfiles, environment configs, build/sync scripts
        if ".github/" in lower or "dockerfile" in lower or "scripts/" in lower or "config/env/" in lower:
            return True
        if any(lower.endswith(ext) for ext in [".yml", ".yaml", ".sh", ".cmd"]):
            return True
        return False

    # ACC-06: QA Automation (TAG-06) & AppSec (TAG-07)
    if role_tag == "BARCAN-TAG-06":
        # Tests (unit, integration, e2e) except security
        if "tests/security/" in lower:
            return False
        if "tests/" in lower or "/test/" in lower:
            return True
        if any(lower.endswith(ext) for ext in [".test.ts", ".test.js", ".spec.ts"]):
            return True
        if lower.endswith("test.java"):
            return True
        return False

    if role_tag == "BARCAN-TAG-07":
        # Auth controllers & security tests
        if "src/main/java/com/eneik/production/controllers/auth/" in file_path:
            return True
        if "tests/security/" in lower:
            return True
        return False

    # ACC-07: Compliance Officer (TAG-10)
    if role_tag == "BARCAN-TAG-10":
        # Regulatory policy controllers
        if "src/main/java/com/eneik/production/controllers/policy/" in file_path:
            return True
        return False

    # Default to False to enforce strict zero-defect boundaries (no unmapped leakage!)
    return False


def main():
    # Attempt to detect role tag from environment or branch name
    role_tag = os.getenv("ROLE_TAG")
    if not role_tag:
        try:
            # Try to get branch name
            branch_result = subprocess.run(
                ["git", "rev-parse", "--abbrev-ref", "HEAD"],
                capture_output=True,
                text=True,
                check=True
            )
            branch_name = branch_result.stdout.strip().upper()
            # Search for BARCAN-TAG-XX pattern in the branch name
            import re
            match = re.search(r"BARCAN-TAG-\d{2}", branch_name)
            if match:
                role_tag = match.group(0)
        except Exception:
            pass

    if not role_tag:
        print("PR Boundary Guard: No active role tag found in environment or branch name. Skipping validation.")
        sys.exit(0)

    print(f"PR Boundary Guard: Auditing changed files for active role: {role_tag}")

    changed_files = get_changed_files()
    if not changed_files:
        print("PR Boundary Guard: No modified files detected in this commit/PR.")
        sys.exit(0)

    violations = []
    for f in changed_files:
        if not is_relevant_for_role(role_tag, f):
            violations.append(f)

    if violations:
        print("\n❌ PR BOUNDARY GUARD VIOLATION DETECTED!")
        print(f"Role {role_tag} is not authorized to modify the following files outside its directory boundaries:")
        for v in violations:
            print(f" - {v}")
        print("\nPlease revert changes to these files or escalate the task to BARCAN-TAG-00 (Integration).")
        sys.exit(1)

    print("✅ PR Boundary Guard: All modified files are compliant with role directories. Quality Gate Passed!")
    sys.exit(0)


if __name__ == "__main__":
    main()
