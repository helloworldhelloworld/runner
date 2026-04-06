#!/bin/bash
# scripts/check-dependencies.sh
# Validates that module dependencies follow architectural rules.
# Run in CI to prevent dependency violations.

set -euo pipefail

ERRORS=0
ROOT=$(git rev-parse --show-toplevel 2>/dev/null || echo ".")

echo "=== Checking module dependency rules ==="

# Rule 1: agent-kernel must not depend on any internal module
echo -n "Rule 1: agent-kernel has no internal dependencies... "
KERNEL_DEPS=$(grep -A1 '<artifactId>agent-\|<artifactId>kernel-\|<artifactId>soul-' "$ROOT/agent-kernel/pom.xml" 2>/dev/null \
  | grep '<artifactId>' | grep -v 'agent-kernel' | grep -v '<!--' || true)
if [ -n "$KERNEL_DEPS" ]; then
  echo "FAIL"
  echo "  agent-kernel must not depend on internal modules. Found:"
  echo "  $KERNEL_DEPS"
  ERRORS=$((ERRORS + 1))
else
  echo "OK"
fi

# Rule 2: No module depends on agent-web (except agent-demo via transitive)
echo -n "Rule 2: No module depends on agent-web... "
for module in agent-kernel agent-tools agent-mcp kernel-memory agent-sdk soul-safety soul-assessment soul-user agent-plugin-example; do
  if [ -f "$ROOT/$module/pom.xml" ]; then
    WEB_DEP=$(grep '<artifactId>agent-web</artifactId>' "$ROOT/$module/pom.xml" || true)
    if [ -n "$WEB_DEP" ]; then
      echo "FAIL"
      echo "  $module depends on agent-web — this is forbidden."
      ERRORS=$((ERRORS + 1))
    fi
  fi
done
echo "OK"

# Rule 3: Business modules only depend on agent-kernel (and soul-user exception)
echo -n "Rule 3: Peer modules don't depend on each other... "
declare -A ALLOWED_DEPS
ALLOWED_DEPS[agent-tools]="agent-kernel"
ALLOWED_DEPS[agent-mcp]="agent-kernel"
ALLOWED_DEPS[kernel-memory]="agent-kernel"
ALLOWED_DEPS[agent-sdk]="agent-kernel"
ALLOWED_DEPS[soul-safety]="agent-kernel"
ALLOWED_DEPS[soul-assessment]="agent-kernel soul-user"
ALLOWED_DEPS[soul-user]=""

RULE3_FAIL=0
for module in agent-tools agent-mcp kernel-memory agent-sdk soul-safety soul-assessment soul-user; do
  if [ -f "$ROOT/$module/pom.xml" ]; then
    INTERNAL_DEPS=$(grep -A1 '<artifactId>agent-\|<artifactId>kernel-\|<artifactId>soul-' "$ROOT/$module/pom.xml" 2>/dev/null \
      | grep '<artifactId>' | grep -v "$module" | grep -v '<!--' | sed 's/.*<artifactId>//' | sed 's/<.*//' || true)
    ALLOWED="${ALLOWED_DEPS[$module]}"
    for dep in $INTERNAL_DEPS; do
      if [[ ! " $ALLOWED " =~ " $dep " ]]; then
        echo ""
        echo "  VIOLATION: $module depends on $dep (allowed: $ALLOWED)"
        RULE3_FAIL=1
        ERRORS=$((ERRORS + 1))
      fi
    done
  fi
done
if [ $RULE3_FAIL -eq 0 ]; then echo "OK"; fi

# Rule 4: No circular dependencies (simple check via build order)
echo -n "Rule 4: Maven build order is acyclic... "
cd "$ROOT"
if mvn validate -q 2>/dev/null; then
  echo "OK"
else
  echo "FAIL (Maven detected circular dependency)"
  ERRORS=$((ERRORS + 1))
fi

echo ""
if [ $ERRORS -gt 0 ]; then
  echo "=== FAILED: $ERRORS dependency violation(s) found ==="
  exit 1
else
  echo "=== PASSED: All dependency rules OK ==="
  exit 0
fi
