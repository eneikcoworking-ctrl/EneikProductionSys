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

    # Code Guardian / Tech Lead (TAG-00) and Technical Product Manager (TAG-09)
    # have global integration rights
    if role_tag in ["BARCAN-TAG-00", "BARCAN-TAG-09"]:
        return True

    if role_tag == "BARCAN-TAG-02":  # Backend Engineer
        # Spring Boot Java or next.js api / prisma / fastapi models
        if "src/main/java/" in lower and not "/test/" in lower and lower.endswith(".java"):
            return True
        if "src/app/api/" in lower or "app/api" in lower or lower.endswith(".prisma") or lower.endswith(".py"):
            return True
        return False

    elif role_tag in ["BARCAN-TAG-03", "BARCAN-TAG-11"]:  # UI/UX Designer & Frontend Engineer
        # frontend, src/views, src/components, src/app (excluding API / prisma / tests)
        if "test" in lower or "api/" in lower or lower.endswith(".prisma") or lower.endswith(".py"):
            return False
        if any(term in lower for term in ["frontend/", "src/views/", "src/components/", "src/app/", "design/"]):
            return True
        if any(lower.endswith(ext) for ext in [".svelte", ".tsx", ".jsx", ".css", ".html", ".js", ".ts"]):
            return True
        return False

    elif role_tag == "BARCAN-TAG-06":  # QA Automation
        # Tests only
        return "test" in lower or "/tests/" in lower or lower.endswith(".test.ts") or lower.endswith(".test.js")

    elif role_tag == "BARCAN-TAG-05":  # DevOps / SRE
        # github actions, dockerfiles, scripts, configurations
        if ".github/" in lower or "dockerfile" in lower or "scripts/" in lower or lower.endswith(".yml") or lower.endswith(".yaml"):
            return True
        return False

    # Default to True for roles with unmapped folders to be safe
    return True


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
