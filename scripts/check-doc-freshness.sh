#!/bin/bash
# scripts/check-doc-freshness.sh
# Validates that class names referenced in docs/ still exist in source code.
# Catches stale documentation that refers to deleted/renamed classes.

set -euo pipefail

ROOT=$(git rev-parse --show-toplevel 2>/dev/null || echo ".")
DOCS_DIR="$ROOT/docs"
ERRORS=0
WARNINGS=0

echo "=== Checking documentation freshness ==="

if [ ! -d "$DOCS_DIR" ]; then
  echo "No docs/ directory found. Skipping."
  exit 0
fi

# Collect all Java class names in the project
ALL_CLASSES=$(find "$ROOT" -name "*.java" -not -path "*/test/*" | xargs -I{} basename {} .java | sort -u)

# Extract class-like references from docs (PascalCase words that look like Java classes)
# Pattern: words starting with uppercase, containing at least one lowercase, 3+ chars
DOC_REFS=$(grep -rohE '\b[A-Z][a-zA-Z]{2,}\b' "$DOCS_DIR"/*.md "$DOCS_DIR"/**/*.md 2>/dev/null | sort -u || true)

# Common words to skip (not class names)
SKIP_WORDS="The This That These Those When What Where How Why All Any Each Every Some None Most \
Build Maven Spring Java Module Package Class Interface Method Rule Guide Status Context Decision \
Accepted Positive Negative Migration Steps Option Reference Rules Dependencies Responsibility \
Conventions Architecture Legacy Default Custom Session Layer REST WebSocket"

echo ""
echo "--- Stale class references ---"
for ref in $DOC_REFS; do
  # Skip common English words
  if echo "$SKIP_WORDS" | grep -qw "$ref"; then continue; fi
  
  # Skip short words (likely not class names)
  if [ ${#ref} -lt 5 ]; then continue; fi
  
  # Check if it looks like a Java class (PascalCase with mixed case)
  if ! echo "$ref" | grep -qE '^[A-Z][a-z].*[A-Z]'; then continue; fi
  
  # Check if this class exists in source
  if ! echo "$ALL_CLASSES" | grep -qx "$ref"; then
    # Double check: maybe it's a partial match
    if ! echo "$ALL_CLASSES" | grep -q "$ref"; then
      echo "  WARNING: '$ref' referenced in docs but not found in source code"
      WARNINGS=$((WARNINGS + 1))
    fi
  fi
done

echo ""
echo "--- Cross-reference check ---"
# Check that docs/modules/ has entries for each actual module
for module_dir in "$ROOT"/*/pom.xml; do
  module=$(basename "$(dirname "$module_dir")")
  if [ "$module" = "runner" ]; then continue; fi
  
  # Check if module has a doc entry
  MODULE_DOC=$(find "$DOCS_DIR/modules" -name "*.md" -exec grep -l "$module" {} \; 2>/dev/null || true)
  if [ -z "$MODULE_DOC" ]; then
    echo "  WARNING: Module '$module' has no entry in docs/modules/"
    WARNINGS=$((WARNINGS + 1))
  fi
done

echo ""
if [ $ERRORS -gt 0 ]; then
  echo "=== FAILED: $ERRORS error(s), $WARNINGS warning(s) ==="
  exit 1
elif [ $WARNINGS -gt 0 ]; then
  echo "=== PASSED with $WARNINGS warning(s) — consider updating docs ==="
  exit 0
else
  echo "=== PASSED: All docs are fresh ==="
  exit 0
fi
