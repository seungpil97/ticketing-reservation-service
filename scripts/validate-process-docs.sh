#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${PROCESS_DOCS_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
REQUIRE_GIT_TRACKED="${PROCESS_DOCS_REQUIRE_GIT_TRACKED:-0}"

ERROR_FILE="$(mktemp "${TMPDIR:-/tmp}/process-docs-validation-errors.XXXXXX")"
REF_FILE="$(mktemp "${TMPDIR:-/tmp}/process-docs-validation-refs.XXXXXX")"
ROW_FILE="$(mktemp "${TMPDIR:-/tmp}/process-docs-validation-rows.XXXXXX")"
ROADMAP_HISTORY_FILE="$(mktemp "${TMPDIR:-/tmp}/process-docs-validation-roadmap.XXXXXX")"
PREFIX_FILE="$(mktemp "${TMPDIR:-/tmp}/process-docs-validation-prefix.XXXXXX")"
PROJECT_VISIBLE_FILE="$(mktemp "${TMPDIR:-/tmp}/process-docs-validation-project-visible.XXXXXX")"
ROADMAP_VISIBLE_FILE="$(mktemp "${TMPDIR:-/tmp}/process-docs-validation-roadmap-visible.XXXXXX")"
GPT_VISIBLE_FILE="$(mktemp "${TMPDIR:-/tmp}/process-docs-validation-gpt-visible.XXXXXX")"

cleanup() {
  rm -f \
    "$ERROR_FILE" \
    "$REF_FILE" \
    "$ROW_FILE" \
    "$ROADMAP_HISTORY_FILE" \
    "$PREFIX_FILE" \
    "$PROJECT_VISIBLE_FILE" \
    "$ROADMAP_VISIBLE_FILE" \
    "$GPT_VISIBLE_FILE"
}
trap cleanup EXIT HUP INT TERM

add_failure() {
  printf '%s\n' "FAIL: $1" >> "$ERROR_FILE"
}

required_files() {
  cat <<'EOF'
CLAUDE.md
.claude/agents/ticketing-risk-reviewer.md
docs/process/PROJECT-RULES.md
docs/process/PORTFOLIO-ROADMAP.md
docs/process/TASK-START-CHECKLIST.md
docs/process/GPT-REVIEW-CONTRACT.md
docs/process/DEVELOPMENT-WORKFLOW.md
docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md
docs/process/DOCUMENT-MIGRATION-MATRIX.md
scripts/validate-process-docs.sh
scripts/test-validate-process-docs.sh
EOF
}

check_fixed() {
  file="$1"
  text="$2"
  label="$3"
  if ! grep -Fq -- "$text" "$file" 2>/dev/null; then
    add_failure "$label"
  fi
}

# ---------------------------------------------------------------------------
# Strict Markdown context primitives for load-bearing policy contracts.
#
# Generic visible-context evidence never consumes fenced payload.
# Explicit canonical fenced payload is accepted only through the dedicated
# context-aware fenced-payload primitive.
# ---------------------------------------------------------------------------

markdown_visible_lines_strict() {
  file="$1"

  awk '
    function detect_fence(line, s, spaces, ch, i) {
      detected_char=""
      detected_len=0
      detected_rest=""

      s=line
      spaces=0

      while (substr(s, 1, 1) == " " &&
             spaces < 4) {
        spaces++
        s=substr(s, 2)
      }

      if (spaces > 3) {
        return 0
      }

      ch=substr(s, 1, 1)

      if (ch != "`" &&
          ch != "~") {
        return 0
      }

      i=1

      while (substr(s, i, 1) == ch) {
        i++
      }

      detected_len=i - 1

      if (detected_len < 3) {
        return 0
      }

      detected_char=ch
      detected_rest=substr(s, i)

      return 1
    }

    {
      if (!in_fence) {
        if (detect_fence($0)) {
          in_fence=1
          fence_char=detected_char
          fence_len=detected_len
          next
        }

        print
        next
      }

      if (detect_fence($0) &&
          detected_char == fence_char &&
          detected_len >= fence_len &&
          detected_rest ~ /^[[:space:]]*$/) {
        in_fence=0
        fence_char=""
        fence_len=0
      }
    }

    END {
      if (in_fence) {
        exit 2
      }
    }
  ' "$file"
}

check_context_fixed() {
  file="$1"
  kind="$2"
  expected_h2="$3"
  expected_h3="$4"
  expected_h4="$5"
  expected_h5="$6"
  expected_h6="$7"
  expected_field="$8"
  expected_line="$9"
  label="${10}"

  if ! markdown_visible_lines_strict "$file" |
    awk \
      -v kind="$kind" \
      -v eh2="$expected_h2" \
      -v eh3="$expected_h3" \
      -v eh4="$expected_h4" \
      -v eh5="$expected_h5" \
      -v eh6="$expected_h6" \
      -v efield="$expected_field" \
      -v expected="$expected_line" '
      function path_matches() {
        return h2 == eh2 &&
               h3 == eh3 &&
               h4 == eh4 &&
               h5 == eh5 &&
               h6 == eh6
      }

      function parent_h3_matches() {
        return h2 == eh2 &&
               h3 == eh3
      }

      function parent_h4_matches() {
        return h2 == eh2 &&
               h3 == eh3 &&
               h4 == eh4
      }

      function parent_h5_matches() {
        return h2 == eh2 &&
               h3 == eh3 &&
               h4 == eh4 &&
               h5 == eh5
      }

      BEGIN {
        h2=""
        h3=""
        h4=""
        h5=""
        h6=""
        field=""

        h2_count=0
        h3_count=0
        h4_count=0
        h5_count=0
        h6_count=0
        field_count=0
        expected_count=0
      }

      /^## / {
        h2=$0
        h3=""
        h4=""
        h5=""
        h6=""
        field=""

        if ($0 == eh2) {
          h2_count++
        }

        if (kind == "H2" &&
            $0 == expected &&
            $0 == eh2) {
          expected_count++
        }

        next
      }

      /^### / {
        if (h2 == eh2 &&
            $0 == eh3) {
          h3_count++
        }

        if (kind == "H3" &&
            $0 == expected &&
            h2 == eh2 &&
            $0 == eh3) {
          expected_count++
        }

        h3=$0
        h4=""
        h5=""
        h6=""
        field=""
        next
      }

      /^#### / {
        if (parent_h3_matches() &&
            $0 == eh4) {
          h4_count++
        }

        if (kind == "H4" &&
            $0 == expected &&
            parent_h3_matches() &&
            $0 == eh4) {
          expected_count++
        }

        h4=$0
        h5=""
        h6=""
        field=""
        next
      }

      /^##### / {
        if (parent_h4_matches() &&
            $0 == eh5) {
          h5_count++
        }

        if (kind == "H5" &&
            $0 == expected &&
            parent_h4_matches() &&
            $0 == eh5) {
          expected_count++
        }

        h5=$0
        h6=""
        field=""
        next
      }

      /^###### / {
        if (parent_h5_matches() &&
            $0 == eh6) {
          h6_count++
        }

        if (kind == "H6" &&
            $0 == expected &&
            parent_h5_matches() &&
            $0 == eh6) {
          expected_count++
        }

        h6=$0
        field=""
        next
      }

      /^\*\*[^*][^*]*\*\*$/ {
        if (path_matches() &&
            $0 == efield) {
          field_count++
        }

        field=$0

        if (kind == "FIELD" &&
            $0 == expected &&
            path_matches() &&
            $0 == efield) {
          expected_count++
        }

        next
      }

      {
        if (kind == "LINE" &&
            $0 == expected &&
            path_matches() &&
            field == efield) {
          expected_count++
        }
      }

      END {
        bad=0

        if (eh2 != "" &&
            h2_count != 1) {
          bad=1
        }

        if (eh3 != "" &&
            h3_count != 1) {
          bad=1
        }

        if (eh4 != "" &&
            h4_count != 1) {
          bad=1
        }

        if (eh5 != "" &&
            h5_count != 1) {
          bad=1
        }

        if (eh6 != "" &&
            h6_count != 1) {
          bad=1
        }

        if (efield != "" &&
            field_count != 1) {
          bad=1
        }

        if (expected_count != 1) {
          bad=1
        }

        if (bad) {
          exit 1
        }
      }
    '
  then
    add_failure "$label"
  fi
}

check_context_fenced_payload() {
  file="$1"
  expected_h2="$2"
  expected_h3="$3"
  expected_h4="$4"
  expected_h5="$5"
  expected_h6="$6"
  expected_field="$7"
  expected_fence_open="$8"
  expected_payload="$9"
  label="${10}"

  if ! awk \
    -v eh2="$expected_h2" \
    -v eh3="$expected_h3" \
    -v eh4="$expected_h4" \
    -v eh5="$expected_h5" \
    -v eh6="$expected_h6" \
    -v efield="$expected_field" \
    -v efence="$expected_fence_open" \
    -v expected="$expected_payload" '
    function detect_fence(line, s, spaces, ch, i) {
      detected_char=""
      detected_len=0
      detected_rest=""

      s=line
      spaces=0

      while (substr(s, 1, 1) == " " &&
             spaces < 4) {
        spaces++
        s=substr(s, 2)
      }

      if (spaces > 3) {
        return 0
      }

      ch=substr(s, 1, 1)

      if (ch != "`" &&
          ch != "~") {
        return 0
      }

      i=1

      while (substr(s, i, 1) == ch) {
        i++
      }

      detected_len=i - 1

      if (detected_len < 3) {
        return 0
      }

      detected_char=ch
      detected_rest=substr(s, i)

      return 1
    }

    function path_matches() {
      return h2 == eh2 &&
             h3 == eh3 &&
             h4 == eh4 &&
             h5 == eh5 &&
             h6 == eh6 &&
             field == efield
    }

    BEGIN {
      h2=""
      h3=""
      h4=""
      h5=""
      h6=""
      field=""

      in_fence=0
      target_fence=0
      target_count=0
      target_closed=0
      payload_count=0
      payload_exact_count=0

      h2_count=0
      h3_count=0
      h4_count=0
      h5_count=0
      h6_count=0
      field_count=0
    }

    {
      line=$0

      if (in_fence) {
        if (detect_fence(line) &&
            detected_char == fence_char &&
            detected_len >= fence_len &&
            detected_rest ~ /^[[:space:]]*$/) {
          if (target_fence) {
            target_closed++
          }

          in_fence=0
          target_fence=0
          fence_char=""
          fence_len=0
          next
        }

        if (target_fence) {
          payload_count++

          if (line == expected) {
            payload_exact_count++
          }
        }

        next
      }

      if (detect_fence(line)) {
        in_fence=1
        fence_char=detected_char
        fence_len=detected_len

        if (line == efence &&
            path_matches()) {
          target_fence=1
          target_count++
        } else {
          target_fence=0
        }

        next
      }

      if (line ~ /^## /) {
        h2=line
        h3=""
        h4=""
        h5=""
        h6=""
        field=""

        if (line == eh2) {
          h2_count++
        }

        next
      }

      if (line ~ /^### /) {
        if (h2 == eh2 &&
            line == eh3) {
          h3_count++
        }

        h3=line
        h4=""
        h5=""
        h6=""
        field=""
        next
      }

      if (line ~ /^#### /) {
        if (h2 == eh2 &&
            h3 == eh3 &&
            line == eh4) {
          h4_count++
        }

        h4=line
        h5=""
        h6=""
        field=""
        next
      }

      if (line ~ /^##### /) {
        if (h2 == eh2 &&
            h3 == eh3 &&
            h4 == eh4 &&
            line == eh5) {
          h5_count++
        }

        h5=line
        h6=""
        field=""
        next
      }

      if (line ~ /^###### /) {
        if (h2 == eh2 &&
            h3 == eh3 &&
            h4 == eh4 &&
            h5 == eh5 &&
            line == eh6) {
          h6_count++
        }

        h6=line
        field=""
        next
      }

      if (line ~ /^\*\*[^*][^*]*\*\*$/) {
        if (h2 == eh2 &&
            h3 == eh3 &&
            h4 == eh4 &&
            h5 == eh5 &&
            h6 == eh6 &&
            line == efield) {
          field_count++
        }

        field=line
      }
    }

    END {
      bad=0

      if (in_fence) {
        bad=1
      }

      if (eh2 != "" &&
          h2_count != 1) {
        bad=1
      }

      if (eh3 != "" &&
          h3_count != 1) {
        bad=1
      }

      if (eh4 != "" &&
          h4_count != 1) {
        bad=1
      }

      if (eh5 != "" &&
          h5_count != 1) {
        bad=1
      }

      if (eh6 != "" &&
          h6_count != 1) {
        bad=1
      }

      if (efield != "" &&
          field_count != 1) {
        bad=1
      }

      if (target_count != 1 ||
          target_closed != 1 ||
          payload_count != 1 ||
          payload_exact_count != 1) {
        bad=1
      }

      if (bad) {
        exit 1
      }
    }
  ' "$file"
  then
    add_failure "$label"
  fi
}

check_regex() {
  file="$1"
  pattern="$2"
  label="$3"
  if ! grep -Eq "$pattern" "$file" 2>/dev/null; then
    add_failure "$label"
  fi
}

sha256_stream() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{ print $1 }'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 | awk '{ print $1 }'
  else
    return 127
  fi
}

# ---------------------------------------------------------------------------
# A/B. Required files, regular-file and optional Git-index type validation.
# No stdout/stderr is emitted before the sensitive scan finishes.
# ---------------------------------------------------------------------------

while IFS= read -r rel
do
  abs="$ROOT/$rel"
  if [ -L "$abs" ]; then
    add_failure "symlink blocked: $rel"
  elif [ ! -e "$abs" ]; then
    add_failure "required file missing: $rel"
  elif [ ! -f "$abs" ]; then
    add_failure "required path is not a regular file: $rel"
  fi
done <<EOF
$(required_files)
EOF

if [ "$REQUIRE_GIT_TRACKED" = "1" ]; then
  if ! git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    add_failure "Git tracked validation requested outside a Git worktree"
  else
    while IFS= read -r rel
    do
      if ! entry="$(git -C "$ROOT" ls-files -s -- "$rel" 2>/dev/null)"; then
        add_failure "required Git index lookup failed: $rel"
        continue
      fi

      entry_count="$(
        printf '%s\n' "$entry" |
          awk 'NF { n++ } END { print n + 0 }'
      )"

      if [ "$entry_count" -eq 0 ]; then
        add_failure "required file is not Git tracked: $rel"
        continue
      fi

      if [ "$entry_count" -ne 1 ]; then
        add_failure "required Git index entry count is not exactly 1: $rel"
        continue
      fi

      mode="$(printf '%s\n' "$entry" | awk 'NF { print $1 }')"
      stage="$(printf '%s\n' "$entry" | awk 'NF { print $3 }')"

      if [ "$stage" != "0" ]; then
        add_failure "required Git index stage is not 0: $rel"
        continue
      fi

      case "$mode" in
        100644|100755)
          ;;
        120000)
          add_failure "Git mode 120000 blocked: $rel"
          ;;
        *)
          add_failure "non-regular Git mode blocked for $rel"
          ;;
      esac
    done <<EOF
$(required_files)
EOF
  fi
elif [ "$REQUIRE_GIT_TRACKED" != "0" ]; then
  add_failure "PROCESS_DOCS_REQUIRE_GIT_TRACKED must be 0 or 1"
fi

# ---------------------------------------------------------------------------
# B-2. Authoritative non-process references used by process documents.
# ---------------------------------------------------------------------------

for rel in docs/db/README.md
do
  abs="$ROOT/$rel"

  if [ -L "$abs" ]; then
    add_failure "authoritative reference symlink blocked: $rel"
  elif [ ! -e "$abs" ]; then
    add_failure "authoritative reference missing: $rel"
  elif [ ! -f "$abs" ]; then
    add_failure "authoritative reference is not a regular file: $rel"
  fi
done

for rel in docs/architecture docs/api
do
  abs="$ROOT/$rel"

  if [ -L "$abs" ]; then
    add_failure "authoritative reference symlink blocked: $rel"
  elif [ ! -e "$abs" ]; then
    add_failure "authoritative reference missing: $rel"
  elif [ ! -d "$abs" ]; then
    add_failure "authoritative reference is not a directory: $rel"
  fi
done

# ---------------------------------------------------------------------------
# B-2A. Shared Markdown visible-line/section filter for structural contracts.
#
# POSIX awk only.
#
# Opening fence:
# - 0~3 literal spaces
# - 3+ identical backticks or tildes
# - info string allowed
#
# Closing fence:
# - same character as opener
# - run length >= opener
# - trailing whitespace only
#
# Section extraction:
# - exact approved heading starts the context
# - next same-or-higher Markdown heading ends the context
#
# This filter is intentionally NOT used by the sensitive scanner.
# ---------------------------------------------------------------------------

markdown_visible_lines() {
  file="$1"

  markdown_visible_lines_strict "$file"
}

markdown_visible_snapshot_strict() {
  file="$1"
  destination="$2"
  partial="${destination}.partial.$$"

  rm -f "$partial" "$destination"

  if markdown_visible_lines_strict \
    "$file" \
    > "$partial"
  then
    if mv "$partial" "$destination"; then
      return 0
    fi
  fi

  rm -f "$partial" "$destination"
  return 1
}

markdown_direct_context_lines() {
  visible_file="$1"
  parent="$2"
  child="$3"

  awk \
    -v parent="$parent" \
    -v child="$child" '
    function heading_level(line,    i,ch,level) {
      level=0

      for (i=1; i<=length(line); i++) {
        ch=substr(line, i, 1)

        if (ch == "#") {
          level++
        } else {
          break
        }
      }

      if (level == 0 ||
          substr(line, level + 1, 1) != " ") {
        return 0
      }

      return level
    }

    BEGIN {
      parent_level=heading_level(parent)
      child_level=0

      if (child != "") {
        child_level=heading_level(child)
      }

      if (parent_level == 0 ||
          (child != "" &&
           child_level != parent_level + 1)) {
        exit 2
      }

      parent_count=0
      child_count=0
      in_parent=0
      in_child=0
      parent_direct_open=0
      child_direct_open=0
    }

    {
      line=$0

      if (line == parent) {
        parent_count++
        in_parent=1
        in_child=0
        parent_direct_open=(child == "")
        child_direct_open=0
        next
      }

      level=heading_level(line)

      if (!in_parent) {
        next
      }

      if (level > 0 &&
          level <= parent_level) {
        in_parent=0
        in_child=0
        parent_direct_open=0
        child_direct_open=0
        next
      }

      if (child == "") {
        if (level > parent_level) {
          parent_direct_open=0
          next
        }

        if (parent_direct_open) {
          print line
        }

        next
      }

      if (level == child_level) {
        if (line == child) {
          child_count++
          in_child=1
          child_direct_open=1
        } else {
          in_child=0
          child_direct_open=0
        }

        next
      }

      if (in_child &&
          level > child_level) {
        child_direct_open=0
        next
      }

      if (in_child &&
          child_direct_open) {
        print line
      }
    }

    END {
      if (parent_count != 1) {
        exit 1
      }

      if (child != "" &&
          child_count != 1) {
        exit 1
      }
    }
  ' "$visible_file"
}

markdown_fenced_payload_after_visible_anchor() {
  raw_file="$1"
  visible_file="$2"
  parent="$3"
  child="$4"
  anchor="$5"
  approved_opener="$6"
  approved_closer="$7"
  required_context_opener_count="$8"

  direct_context=''

  if direct_context="$(
    markdown_direct_context_lines \
      "$visible_file" \
      "$parent" \
      "$child"
  )"
  then
    :
  else
    return 1
  fi

  visible_anchor_count="$(
    printf '%s\n' "$direct_context" |
      awk \
        -v expected="$anchor" '
        $0 == expected {
          count++
        }

        END {
          print count + 0
        }
      '
  )"

  [ "$visible_anchor_count" -eq 1 ] ||
    return 1

  visible_parent_count="$(
    awk \
      -v expected="$parent" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    ' "$visible_file"
  )"

  raw_parent_count="$(
    awk \
      -v expected="$parent" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    ' "$raw_file"
  )"

  [ "$visible_parent_count" -eq 1 ] &&
    [ "$raw_parent_count" -eq 1 ] ||
    return 1

  if [ -n "$child" ]; then
    raw_child_count="$(
      awk \
        -v expected="$child" '
        $0 == expected {
          count++
        }

        END {
          print count + 0
        }
      ' "$raw_file"
    )"

    [ "$raw_child_count" -eq 1 ] ||
      return 1
  fi

  awk \
    -v parent="$parent" \
    -v child="$child" \
    -v anchor="$anchor" \
    -v approved_opener="$approved_opener" \
    -v approved_closer="$approved_closer" \
    -v required_context_opener_count="$required_context_opener_count" '
    BEGIN {
      in_parent=0
      in_context=0
      waiting_for_payload=0
      capture_payload=0
      anchor_count=0
      capture_count=0
      capture_closed=0
      after_capture=0
      context_opener_count=0
      bad=0
    }

    {
      line=$0

      if (capture_payload) {
        if (line == approved_closer) {
          capture_payload=0
          capture_closed++
          after_capture=1
          next
        }

        print line
        next
      }

      if (after_capture) {
        if (line ~ /^[[:space:]]*$/) {
          next
        }

        if (line == approved_opener) {
          bad=1
          next
        }

        after_capture=0
      }

      if (line == parent) {
        in_parent=1
        in_context=(child == "")
        waiting_for_payload=0
        next
      }

      if (in_parent &&
          line ~ /^## /) {
        in_parent=0
        in_context=0
        waiting_for_payload=0
        next
      }

      if (!in_parent) {
        next
      }

      if (child != "" &&
          line == child) {
        in_context=1
        waiting_for_payload=0
        next
      }

      if (child != "" &&
          line ~ /^### /) {
        in_context=0
        waiting_for_payload=0
        next
      }

      if (child != "" &&
          in_context &&
          line ~ /^####+ /) {
        in_context=0
        waiting_for_payload=0
        next
      }

      if (child == "" &&
          in_context &&
          line ~ /^### /) {
        in_context=0
        waiting_for_payload=0
        next
      }

      if (!in_context) {
        next
      }

      if (line == approved_opener) {
        context_opener_count++
      }

      if (line == anchor) {
        anchor_count++
        waiting_for_payload=1
        next
      }

      if (waiting_for_payload &&
          line ~ /^[[:space:]]*$/) {
        next
      }

      if (waiting_for_payload) {
        if (line == approved_opener) {
          capture_count++
          capture_payload=1
        } else {
          bad=1
        }

        waiting_for_payload=0
        next
      }
    }

    END {
      if (capture_payload ||
          waiting_for_payload) {
        bad=1
      }

      if (anchor_count != 1 ||
          capture_count != 1 ||
          capture_closed != 1 ||
          bad) {
        exit 1
      }

      if ((required_context_opener_count + 0) > 0 &&
          context_opener_count != (required_context_opener_count + 0)) {
        exit 1
      }
    }
  ' "$raw_file"
}

gpt_finding_schema_rows() {
  visible_file="$1"
  direct_lines=''

  if direct_lines="$(
    markdown_direct_context_lines \
      "$visible_file" \
      '## 4. 공통 Finding schema' \
      ''
  )"
  then
    :
  else
    return 1
  fi

  printf '%s\n' "$direct_lines" |
    awk '
      BEGIN {
        header="| 필드 | 의미 |"
        separator="| --- | --- |"
        header_count=0
        table_count=0
        row_count=0
        state=0
        bad=0
      }

      {
        line=$0

        if (line == header) {
          header_count++

          if (state == 0 ||
              state == 3) {
            state=1
          } else {
            bad=1
          }

          next
        }

        if (state == 1) {
          if (line == separator) {
            table_count++
            state=2
          } else {
            bad=1
            state=3
          }

          next
        }

        if (state == 2) {
          if (line ~ /^\| [^|]+ \| [^|]+ \|$/) {
            print line
            row_count++
            next
          }

          state=3
        }
      }

      END {
        if (header_count != 1 ||
            table_count != 1 ||
            row_count < 1 ||
            bad) {
          exit 1
        }
      }
    '
}

gpt_severity_direct_headings() {
  visible_file="$1"
  parent='## 5. Severity'

  awk \
    -v parent="$parent" '
    function heading_level(line,    i,ch,level) {
      level=0

      for (i=1; i<=length(line); i++) {
        ch=substr(line, i, 1)

        if (ch == "#") {
          level++
        } else {
          break
        }
      }

      if (level == 0 ||
          substr(line, level + 1, 1) != " ") {
        return 0
      }

      return level
    }

    BEGIN {
      parent_level=heading_level(parent)
      parent_count=0
      in_parent=0
    }

    {
      line=$0

      if (line == parent) {
        parent_count++
        in_parent=1
        next
      }

      level=heading_level(line)

      if (!in_parent) {
        next
      }

      if (level > 0 &&
          level <= parent_level) {
        in_parent=0
        next
      }

      if (level == parent_level + 1) {
        value=line
        sub(/^### /, "", value)
        print value
      }
    }

    END {
      if (parent_count != 1) {
        exit 1
      }
    }
  ' "$visible_file"
}

visible_exact_line_count() {
  file="$1"
  expected_line="$2"

  markdown_visible_lines "$file" |
    awk \
      -v expected="$expected_line" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    '
}

visible_fixed_count() {
  file="$1"
  expected_text="$2"

  markdown_visible_lines "$file" |
    awk \
      -v expected="$expected_text" '
      index($0, expected) > 0 {
        count++
      }

      END {
        print count + 0
      }
    '
}

markdown_section_lines() {
  file="$1"
  expected_heading="$2"

  markdown_visible_lines "$file" |
    awk \
      -v expected="$expected_heading" '
      function heading_level(line,    i, ch, level) {
        level=0

        for (i=1; i<=length(line); i++) {
          ch=substr(line, i, 1)

          if (ch == "#") {
            level++
          } else {
            break
          }
        }

        if (level == 0 ||
            substr(line, level + 1, 1) != " ") {
          return 0
        }

        return level
      }

      BEGIN {
        target_level=heading_level(expected)
        inside=0
        seen=0
      }

      $0 == expected {
        seen++

        if (seen == 1) {
          inside=1
        }

        next
      }

      inside {
        current_level=heading_level($0)

        if (current_level > 0 &&
            current_level <= target_level) {
          inside=0
          next
        }

        print
      }
    '
}

section_exact_line_count() {
  file="$1"
  expected_heading="$2"
  expected_line="$3"

  markdown_section_lines \
    "$file" \
    "$expected_heading" |
    awk \
      -v expected="$expected_line" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    '
}

check_visible_heading_once() {
  file="$1"
  expected_heading="$2"
  label="$3"

  if heading_count="$(
    visible_exact_line_count \
      "$file" \
      "$expected_heading"
  )"
  then
    if [ "$heading_count" -ne 1 ]; then
      add_failure "$label"
    fi
  else
    add_failure \
      "Markdown visible-line parser failed: $label"
  fi
}

validate_workflow_lifecycle_schema() {
  file="$1"

  if ! lifecycle_issues_file="$(
    mktemp "${TMPDIR:-/tmp}/process-docs-lifecycle-schema.XXXXXX"
  )"
  then
    add_failure \
      "DEVELOPMENT-WORKFLOW lifecycle schema parser temp file unavailable"
    return 0
  fi

  if ! markdown_visible_lines_strict "$file" |
    awk '
      function expected_stage(n) {
        if (n == 1) return "### 1. TASK 시작"
        if (n == 2) return "### 2. 입력자료 확인"
        if (n == 3) return "### 3. Issue / 계획"
        if (n == 4) return "### 4. 설계"
        if (n == 5) return "### 5. 구현"
        if (n == 6) return "### 6. Claude 1차 검토"
        if (n == 7) return "### 7. GPT 독립 검토"
        if (n == 8) return "### 8. 테스트"
        if (n == 9) return "### 9. Git 검증"
        if (n == 10) return "### 10. PR"
        if (n == 11) return "### 11. merge"
        if (n == 12) return "### 12. 조건부 회고 / 장애 기록"
        if (n == 13) return "### 13. Learning / interview"
        if (n == 14) return "### 14. TASK 최종 완료"
        return ""
      }

      function reset_fields() {
        purpose=0
        input=0
        execution=0
        output=0
        gate=0
        stop=0
        nested=0
      }

      function report_field(name, count) {
        if (count == 0) {
          print "stage " stage_count " missing " name
        } else if (count > 1) {
          print "stage " stage_count " duplicate " name
        }
      }

      function report_stage() {
        if (!stage_open) {
          return
        }

        report_field("목적", purpose)
        report_field("입력", input)
        report_field("실행", execution)
        report_field("산출물", output)
        report_field("승인 gate", gate)
        report_field("중단 조건", stop)
      }

      BEGIN {
        in_root=0
        root_count=0
        stage_count=0
        stage_open=0
        reset_fields()
      }

      /^## / {
        if (in_root) {
          report_stage()
        }

        stage_open=0
        reset_fields()

        if ($0 == "## 3. 단계별 lifecycle") {
          root_count++
          in_root=1
        } else {
          in_root=0
        }

        next
      }

      !in_root {
        next
      }

      /^### / {
        report_stage()

        stage_count++
        stage_open=1
        reset_fields()

        expected=expected_stage(stage_count)

        if (expected == "" ||
            $0 != expected) {
          print "stage heading/order mismatch at " stage_count
        }

        next
      }

      !stage_open {
        next
      }

      /^####+ / {
        nested=1
        next
      }

      nested {
        next
      }

      $0 == "**목적**" {
        purpose++
        next
      }

      $0 == "**입력**" {
        input++
        next
      }

      $0 == "**실행**" {
        execution++
        next
      }

      $0 == "**산출물**" {
        output++
        next
      }

      $0 == "**승인 gate**" {
        gate++
        next
      }

      $0 == "**중단 조건**" {
        stop++
        next
      }

      END {
        if (in_root) {
          report_stage()
        }

        if (root_count != 1) {
          print "lifecycle root count is not 1"
        }

        if (stage_count != 14) {
          print "stage count is not 14"
        }
      }
    ' > "$lifecycle_issues_file"
  then
    rm -f "$lifecycle_issues_file"

    add_failure \
      "DEVELOPMENT-WORKFLOW lifecycle strict Markdown/schema parser failed"

    return 0
  fi

  if [ -s "$lifecycle_issues_file" ]; then
    while IFS= read -r issue
    do
      if [ -n "$issue" ]; then
        add_failure \
          "DEVELOPMENT-WORKFLOW lifecycle schema mismatch: $issue"
      fi
    done < "$lifecycle_issues_file"
  fi

  rm -f "$lifecycle_issues_file"
}

# ---------------------------------------------------------------------------
# B-2B. Fence-aware exact-section / exact-table row extraction.
#
# An approved table begins only when its exact header is immediately followed
# by its exact separator. All matching tables are counted. Only contiguous
# matching data rows are emitted.
#
# expected_table_count is part of the structural contract.
# ---------------------------------------------------------------------------

markdown_table_rows_in_section() {
  file="$1"
  section_heading="$2"
  table_header="$3"
  table_separator="$4"
  row_regex="$5"
  expected_table_count="$6"

  markdown_section_lines \
    "$file" \
    "$section_heading" |
    awk \
      -v header="$table_header" \
      -v separator="$table_separator" \
      -v row_re="$row_regex" \
      -v expected_count="$expected_table_count" '
      BEGIN {
        state=0
        tables=0
        malformed=0
      }

      {
        if (state == 0) {
          if ($0 == header) {
            state=1
          }

          next
        }

        if (state == 1) {
          if ($0 == separator) {
            state=2
            tables++
          } else {
            malformed=1
            state=0

            if ($0 == header) {
              state=1
            }
          }

          next
        }

        if (state == 2) {
          if ($0 ~ row_re) {
            print
            next
          }

          state=0

          if ($0 == header) {
            state=1
          }

          next
        }
      }

      END {
        if (state == 1) {
          exit 2
        }

        if (malformed) {
          exit 2
        }

        if (tables != expected_count + 0) {
          exit 2
        }
      }
    '
}

# ---------------------------------------------------------------------------
# B-3. Authoritative non-process reference source-side integrity.
# ---------------------------------------------------------------------------

PROJECT_FILE="$ROOT/docs/process/PROJECT-RULES.md"
TASK_START_FILE="$ROOT/docs/process/TASK-START-CHECKLIST.md"

PROJECT_PACKAGE_SECTION='### 5.2 Package / Layer'
PROJECT_DB_SECTION='### 5.9 DB / Flyway'
PROJECT_REFERENCE_INDEX_SECTION='## 7. 상세 문서 인덱스'
TASK_START_DB_SECTION='### DB / Flyway 변경'

check_authoritative_reference_line_in_section() {
  file="$1"
  section_heading="$2"
  expected_line="$3"
  label="$4"

  if exact_count="$(
    section_exact_line_count \
      "$file" \
      "$section_heading" \
      "$expected_line"
  )"
  then
    if [ "$exact_count" -ne 1 ]; then
      add_failure "$label"
    fi
  else
    add_failure \
      "Markdown section parser failed: $label"
  fi
}

check_visible_heading_once \
  "$PROJECT_FILE" \
  "$PROJECT_PACKAGE_SECTION" \
  "PROJECT-RULES authoritative context failure: Package / Layer heading count"

check_visible_heading_once \
  "$PROJECT_FILE" \
  "$PROJECT_DB_SECTION" \
  "PROJECT-RULES authoritative context failure: DB / Flyway heading count"

check_visible_heading_once \
  "$PROJECT_FILE" \
  "$PROJECT_REFERENCE_INDEX_SECTION" \
  "PROJECT-RULES authoritative context failure: detailed document index heading count"

check_visible_heading_once \
  "$TASK_START_FILE" \
  "$TASK_START_DB_SECTION" \
  "TASK-START-CHECKLIST authoritative context failure: DB / Flyway change heading count"

check_authoritative_reference_line_in_section \
  "$PROJECT_FILE" \
  "$PROJECT_PACKAGE_SECTION" \
  '상세 구조는 현재 `docs/architecture/`와 실제 package를 함께 확인한다.' \
  "PROJECT-RULES authoritative reference contract failure: architecture current-structure line"

check_authoritative_reference_line_in_section \
  "$PROJECT_FILE" \
  "$PROJECT_DB_SECTION" \
  'DB와 Flyway 상세 기준은 `docs/db/README.md`를 우선한다.' \
  "PROJECT-RULES authoritative reference contract failure: DB/Flyway primary line"

check_authoritative_reference_line_in_section \
  "$PROJECT_FILE" \
  "$PROJECT_REFERENCE_INDEX_SECTION" \
  '- DB/Flyway 상세: `docs/db/README.md`' \
  "PROJECT-RULES authoritative reference contract failure: DB/Flyway index line"

check_authoritative_reference_line_in_section \
  "$PROJECT_FILE" \
  "$PROJECT_REFERENCE_INDEX_SECTION" \
  '- 아키텍처 기준: `docs/architecture/`' \
  "PROJECT-RULES authoritative reference contract failure: architecture index line"

check_authoritative_reference_line_in_section \
  "$PROJECT_FILE" \
  "$PROJECT_REFERENCE_INDEX_SECTION" \
  '- API 문서: `docs/api/`' \
  "PROJECT-RULES authoritative reference contract failure: API index line"

check_authoritative_reference_line_in_section \
  "$TASK_START_FILE" \
  "$TASK_START_DB_SECTION" \
  '- `docs/db/README.md`와 현재 migration 경로를 먼저 확인한다.' \
  "TASK-START-CHECKLIST authoritative reference contract failure: DB reference line"

# ---------------------------------------------------------------------------
# B-4. TASK-START input-gate delegation and structured-state integrity.
# ---------------------------------------------------------------------------

TASK_START_FILE="$ROOT/docs/process/TASK-START-CHECKLIST.md"
PROJECT_PLACEHOLDER_FILE="$ROOT/docs/process/PROJECT-RULES.md"

TASK_START_SECTION='## 2. 시작 상태'
TASK_START_TABLE_HEADER='| 항목 | 확인 내용 |'
TASK_START_TABLE_SEPARATOR='| --- | --- |'
TASK_START_TABLE_COUNT=1
TASK_START_BLOCKERS_ROW='| blockers | 핵심 입력 누락, 상태 불일치, Critical 등 중단 조건 |'
TASK_START_EXPECTED_KEYS="$(cat <<'EOF_TASK_START_EXPECTED_KEYS'
TASK
Phase
allowed work
prohibited work
baseline
required input
blockers
EOF_TASK_START_EXPECTED_KEYS
)"

TASK_START_EXPECTED_ROWS="$(cat <<'EOF_TASK_START_EXPECTED_ROWS'
| TASK | TASK ID와 목적 |
| Phase | 현재 승인된 단계 |
| allowed work | 이번 단계에서 허용된 파일·명령·작업 |
| prohibited work | 금지된 파일·명령·작업 |
| baseline | 기대 branch와 SHA |
| required input | 구현·검토에 필요한 핵심·보조 자료 |
| blockers | 핵심 입력 누락, 상태 불일치, Critical 등 중단 조건 |
EOF_TASK_START_EXPECTED_ROWS
)"


TASK_START_CORE_GATE='8. 현재 Phase를 차단하는 핵심 입력이 부족하면 해당 단계 작업을 시작하지 않고 부족한 항목을 명시한다.'

TASK_START_GPT_DELEGATION='GPT 리뷰의 핵심·보조 입력 분류와 누락 처리는 `docs/process/GPT-REVIEW-CONTRACT.md`를 단일 기준으로 따른다.'

TASK_START_OLD_BROAD_GATE='입력이 부족하면 구현이나 리뷰를 시작하지 않고 부족한 항목을 명시한다.'

PROJECT_PLACEHOLDER_SECTION='### 5.10 Profile / Environment'
PROJECT_PLACEHOLDER_POLICY_1='- 민감 키 assignment의 false-positive 예외는 normalized 전체 RHS가 `${NAME}` 또는 `<NAME>`인 structured placeholder인 경우로만 제한한다.'
PROJECT_PLACEHOLDER_POLICY_2='- `placeholder`, `dummy`, `example`, `changeme`, `sample`, `test`, `fake` 같은 일반 literal은 broad allowlist에 넣지 않으며 그 외 non-empty RHS는 보수적으로 민감 후보로 본다.'

check_visible_heading_once \
  "$TASK_START_FILE" \
  "$TASK_START_SECTION" \
  "TASK-START-CHECKLIST structured-state section heading count is not exactly 1"

if task_start_structured_rows="$(
  markdown_table_rows_in_section \
    "$TASK_START_FILE" \
    "$TASK_START_SECTION" \
    "$TASK_START_TABLE_HEADER" \
    "$TASK_START_TABLE_SEPARATOR" \
    '^\\|[^|]+\\|[^|]+\\|$' \
    "$TASK_START_TABLE_COUNT"
)"; then
  task_start_row_count="$(
    printf '%s\n' "$task_start_structured_rows" |
      awk 'NF { count++ } END { print count + 0 }'
  )"

  task_start_actual_keys="$(
    printf '%s\n' "$task_start_structured_rows" |
      awk -F'|' '
        function t(s) {
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
          return s
        }

        NF {
          print t($2)
        }
      '
  )"

  if [ "$task_start_row_count" -ne 7 ] ||
     [ "$task_start_actual_keys" != "$TASK_START_EXPECTED_KEYS" ] ||
     [ "$task_start_structured_rows" != "$TASK_START_EXPECTED_ROWS" ]; then
    add_failure \
      "TASK-START-CHECKLIST canonical 7-row structured-state contract mismatch"
  fi

  task_start_blockers_counts="$(
    printf '%s\n' "$task_start_structured_rows" |
      awk \
        -F'|' \
        -v expected="$TASK_START_BLOCKERS_ROW" '
        function t(s) {
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
          return s
        }

        {
          if (t($2) == "blockers") {
            blockers++
          }

          if ($0 == expected) {
            exact++
          }
        }

        END {
          print blockers + 0, exact + 0
        }
      '
  )"

  task_start_blockers_count="$(
    printf '%s\n' "$task_start_blockers_counts" |
      awk '{ print $1 }'
  )"

  task_start_exact_blockers_count="$(
    printf '%s\n' "$task_start_blockers_counts" |
      awk '{ print $2 }'
  )"

  if [ "$task_start_blockers_count" -ne 1 ] ||
     [ "$task_start_exact_blockers_count" -ne 1 ]; then
    add_failure \
      "TASK-START-CHECKLIST structured blockers row missing or non-unique in approved table"
  fi
else
  add_failure \
    "TASK-START-CHECKLIST structured table topology mismatch"
fi

if task_start_core_count="$(
  visible_exact_line_count \
    "$TASK_START_FILE" \
    "$TASK_START_CORE_GATE"
)"
then
  if task_start_delegation_count="$(
    visible_exact_line_count \
      "$TASK_START_FILE" \
      "$TASK_START_GPT_DELEGATION"
  )"
  then
    if task_start_old_broad_count="$(
      visible_fixed_count \
        "$TASK_START_FILE" \
        "$TASK_START_OLD_BROAD_GATE"
    )"
    then
      if [ "$task_start_core_count" -ne 1 ]; then
        add_failure \
          "TASK-START-CHECKLIST core-input blocker gate missing or duplicated"
      fi

      if [ "$task_start_delegation_count" -ne 1 ]; then
        add_failure \
          "TASK-START-CHECKLIST GPT input delegation missing or duplicated"
      fi

      if [ "$task_start_old_broad_count" -ne 0 ]; then
        add_failure \
          "TASK-START-CHECKLIST obsolete broad input gate detected"
      fi
    else
      add_failure \
        "TASK-START-CHECKLIST Markdown visible-line parser failed"
    fi
  else
    add_failure \
      "TASK-START-CHECKLIST Markdown visible-line parser failed"
  fi
else
  add_failure \
    "TASK-START-CHECKLIST Markdown visible-line parser failed"
fi

check_visible_heading_once \
  "$PROJECT_PLACEHOLDER_FILE" \
  "$PROJECT_PLACEHOLDER_SECTION" \
  "PROJECT-RULES Profile / Environment heading count is not exactly 1"

if project_policy_1_count="$(
  section_exact_line_count \
    "$PROJECT_PLACEHOLDER_FILE" \
    "$PROJECT_PLACEHOLDER_SECTION" \
    "$PROJECT_PLACEHOLDER_POLICY_1"
)"
then
  if project_policy_2_count="$(
    section_exact_line_count \
      "$PROJECT_PLACEHOLDER_FILE" \
      "$PROJECT_PLACEHOLDER_SECTION" \
      "$PROJECT_PLACEHOLDER_POLICY_2"
  )"
  then
    if [ "$project_policy_1_count" -ne 1 ] ||
       [ "$project_policy_2_count" -ne 1 ]; then
      add_failure \
        "PROJECT-RULES structured placeholder policy missing or duplicated"
    fi
  else
    add_failure \
      "PROJECT-RULES Markdown section parser failed"
  fi
else
  add_failure \
    "PROJECT-RULES Markdown section parser failed"
fi

# ---------------------------------------------------------------------------
# M. Sensitive candidate scan.
# Matches are never printed. grep errors are recorded as scan errors, not clean.
# ---------------------------------------------------------------------------

SENSITIVE_ASSIGNMENT_ERE='(^|[^[:alnum:]_])(password|passwd|pwd|secret|api[_-]?key|access[_-]?key)[[:space:]]*[:=][[:space:]]*["'\'']?[A-Za-z0-9][^[:space:]]{3,}'
SENSITIVE_TOKEN_ERE='(^|[^[:alnum:]_])(token|access[_-]?token)([[:space:]]*=[[:space:]]*|[[:space:]]*:[[:space:]]+)["'\'']?[A-Za-z0-9][^[:space:]]{3,}'
SENSITIVE_TOKEN_COMPACT_UNQUOTED_ERE='(^|[^[:alnum:]_])(token|access[_-]?token):[A-Za-z0-9][A-Za-z0-9._~+/-]{3,}([^A-Za-z0-9._~+/:=-]|$)'
SENSITIVE_LOWER_PREFIX_NON_TOKEN_ASSIGNMENT_ERE='(^|[^[:alnum:]_])([a-z0-9]+[_-])+(secret|password|passwd|pwd|api[_-]?key|access[_-]?key)[[:space:]]*[:=][[:space:]]*["'\'']?[A-Za-z0-9][^[:space:]]{3,}'
SENSITIVE_LOWER_PREFIX_TOKEN_EXPLICIT_ASSIGNMENT_ERE='(^|[^[:alnum:]_])([a-z0-9]+[_-])+token([[:space:]]*=[[:space:]]*["'\'']?[A-Za-z0-9][^[:space:]]{3,}|[[:space:]]*:[[:space:]]+["'\'']?[A-Za-z0-9][^[:space:]]{3,}|[[:space:]]*:[[:space:]]*["'\''][A-Za-z0-9][^[:space:]]{3,})'
SENSITIVE_SUFFIX_ASSIGNMENT_ERE='(^|[^[:alnum:]_])[A-Za-z0-9_-]*(secret|token|password|passwd|pwd|api[_-]?key|access[_-]?key|access[_-]?token)([[:space:]]*=[[:space:]]*["'\'']?[A-Za-z0-9][^[:space:]]{3,}|[[:space:]]*:[[:space:]]+["'\'']?[A-Za-z0-9][^[:space:]]{3,}|[[:space:]]*:[[:space:]]*["'\''][A-Za-z0-9][^"'\'']{3,}["'\''])'
SENSITIVE_DOUBLE_QUOTED_SUFFIX_ASSIGNMENT_ERE="(^|[^[:alnum:]_])\"[A-Za-z0-9_-]*(secret|token|password|passwd|pwd|api[_-]?key|access[_-]?key|access[_-]?token)\"[[:space:]]*[:=][[:space:]]*(\"[A-Za-z0-9][^\"]{3,}\"|'[A-Za-z0-9][^']{3,}'|[A-Za-z0-9][^[:space:]]{3,})"

SENSITIVE_SINGLE_QUOTED_SUFFIX_ASSIGNMENT_ERE="(^|[^[:alnum:]_])'[A-Za-z0-9_-]*(secret|token|password|passwd|pwd|api[_-]?key|access[_-]?key|access[_-]?token)'[[:space:]]*[:=][[:space:]]*('[A-Za-z0-9][^']{3,}'|\"[A-Za-z0-9][^\"]{3,}\"|[A-Za-z0-9][^[:space:]]{3,})"

SENSITIVE_QUOTED_ASSIGNMENT_ERE='["'\''](token|access[_-]?token|password|secret)["'\''][[:space:]]*:[[:space:]]*["'\''][A-Za-z0-9][^"'\'']{3,}["'\'']'
SENSITIVE_TOKEN_QUOTED_VALUE_ERE='(^|[^[:alnum:]_])(token|access[_-]?token)[[:space:]]*:[[:space:]]*["'\''][A-Za-z0-9][^"'\'']{3,}["'\'']'
SENSITIVE_ENV_ASSIGNMENT_ERE='(^|[^[:alnum:]_])([A-Z0-9_]+_)?(SECRET|TOKEN|PASSWORD|PASSWD|PWD|API_KEY|APIKEY|ACCESS_KEY)[[:space:]]*[:=][[:space:]]*["'\'']?[A-Za-z0-9][^[:space:]]{3,}'
SENSITIVE_AUTH_ERE='Authorization[[:space:]]*:[[:space:]]*(Bearer|Basic)[[:space:]]+[A-Za-z0-9._~+/-]{8,}'
SENSITIVE_PRIVATE_KEY_ERE='-----BEGIN [A-Z ]*PRIVATE KEY-----'

scan_one_pattern() {
  abs="$1"
  rel="$2"
  pattern="$3"

  if grep -Eiq -- "$pattern" "$abs" >/dev/null 2>&1; then
    return 10
  else
    rc=$?
    if [ "$rc" -gt 1 ]; then
      return 20
    fi
  fi
  return 0
}

scan_one_pattern_case_sensitive() {
  abs="$1"
  rel="$2"
  pattern="$3"

  if grep -Eq -- "$pattern" "$abs" >/dev/null 2>&1; then
    return 10
  else
    rc=$?
    if [ "$rc" -gt 1 ]; then
      return 20
    fi
  fi
  return 0
}

scan_sensitive_assignment_classifier() {
  abs="$1"

  if LC_ALL=C awk '
    function trim(s) {
      gsub(/^[[:space:]]+/, "", s)
      gsub(/[[:space:]]+$/, "", s)
      return s
    }

    function local_boundary_ok(line, start, before) {
      if (start <= 1) {
        return 1
      }

      before=substr(line, start - 1, 1)

      if (before ~ /[A-Za-z0-9_-]/) {
        return 0
      }

      return 1
    }

    function extract_local_key(line, delimiter_pos, i, key_end, ch, closing_quote, key_start, key, q, opening_char) {
      local_key_quote_state=0
      i=delimiter_pos - 1

      while (i >= 1 &&
             substr(line, i, 1) ~ /[[:space:]]/) {
        i--
      }

      if (i < 1) {
        return ""
      }

      ch=substr(line, i, 1)
      q=sprintf("%c", 39)

      if (ch == "\"" || ch == q) {
        closing_quote=ch
        i--
        key_end=i

        while (i >= 1 &&
               substr(line, i, 1) ~ /[A-Za-z0-9_-]/) {
          i--
        }

        key_start=i + 1

        if (key_start > key_end) {
          return ""
        }

        key=substr(line, key_start, key_end - key_start + 1)

        if (key !~ /^[A-Za-z0-9_-]+$/) {
          return ""
        }

        if (i >= 1) {
          opening_char=substr(line, i, 1)
        } else {
          opening_char=""
        }

        if (opening_char == "\"" ||
            opening_char == q) {
          if (!local_boundary_ok(line, i)) {
            return ""
          }

          if (opening_char == closing_quote) {
            local_key_quote_state=1
          } else {
            local_key_quote_state=2
          }
        } else {
          if (!local_boundary_ok(line, key_start)) {
            return ""
          }

          local_key_quote_state=2
        }

        return key
      }

      key_end=i

      while (i >= 1 &&
             substr(line, i, 1) ~ /[A-Za-z0-9_-]/) {
        i--
      }

      key_start=i + 1

      if (key_start > key_end) {
        return ""
      }

      key=substr(line, key_start, key_end - key_start + 1)

      if (key !~ /^[A-Za-z0-9_-]+$/) {
        return ""
      }

      if (i >= 1) {
        opening_char=substr(line, i, 1)
      } else {
        opening_char=""
      }

      if (opening_char == "\"" ||
          opening_char == q) {
        if (!local_boundary_ok(line, i)) {
          return ""
        }

        local_key_quote_state=2
      } else {
        if (!local_boundary_ok(line, key_start)) {
          return ""
        }

        local_key_quote_state=0
      }

      return key
    }

    function sensitive_key(key, lower) {
      lower=tolower(key)

      if (lower ~ /^[a-z0-9_-]*(secret|token|password|passwd|pwd|api[-_]?key|access[-_]?key|access[-_]?token)$/) {
        return 1
      }

      return 0
    }

    function structured_placeholder(value) {
      if (value ~ /^\$\{[A-Za-z_][A-Za-z0-9_]*\}$/) {
        return 1
      }

      if (value ~ /^<[A-Za-z_][A-Za-z0-9_]*>$/) {
        return 1
      }

      return 0
    }

    function approved_sensitive_provenance(line, delimiter_pos, key, i, n, ch) {
      if (key != "token" &&
          key != "access_token") {
        return 0
      }

      if (substr(line, delimiter_pos, 6) != ":user:") {
        return 0
      }

      i=delimiter_pos + 6
      n=length(line)

      if (i > n ||
          substr(line, i, 1) != "{") {
        return 0
      }

      i++

      if (i > n ||
          substr(line, i, 1) !~ /[A-Za-z_]/) {
        return 0
      }

      i++

      while (i <= n &&
             substr(line, i, 1) ~ /[A-Za-z0-9_]/) {
        i++
      }

      if (i > n ||
          substr(line, i, 1) != "}") {
        return 0
      }

      i++

      if (i > n) {
        return 1
      }

      ch=substr(line, i, 1)

      if (local_value_boundary_char(ch)) {
        return 1
      }

      return 0
    }

    function local_value_boundary_char(ch) {
      if (ch == "") {
        return 1
      }

      if (ch ~ /[[:space:]]/) {
        return 1
      }

      if (ch == "," || ch == "}" || ch == "]" || ch == ";") {
        return 1
      }

      return 0
    }

    function extract_local_rhs(line, delimiter_pos, i, n, first, q, close_pos, rhs, tail_pos, next_char, ch) {
      local_rhs_forced_candidate=0

      i=delimiter_pos + 1
      n=length(line)

      while (i <= n && substr(line, i, 1) ~ /[[:space:]]/) {
        i++
      }

      if (i > n) {
        return ""
      }

      first=substr(line, i, 1)
      q=sprintf("%c", 39)

      if (first == "\"" || first == q) {
        close_pos=i + 1

        while (close_pos <= n && substr(line, close_pos, 1) != first) {
          close_pos++
        }

        if (close_pos > n) {
          local_rhs_forced_candidate=1
          return substr(line, i)
        }

        rhs=substr(line, i, close_pos - i + 1)
        tail_pos=close_pos + 1

        while (tail_pos <= n && substr(line, tail_pos, 1) ~ /[[:space:]]/) {
          tail_pos++
        }

        if (tail_pos <= n) {
          next_char=substr(line, tail_pos, 1)

          if (!local_value_boundary_char(next_char)) {
            local_rhs_forced_candidate=1
          }
        }

        return rhs
      }

      if (substr(line, i, 2) == "${") {
        close_pos=i + 2

        while (close_pos <= n && substr(line, close_pos, 1) != "}") {
          close_pos++
        }

        if (close_pos > n) {
          local_rhs_forced_candidate=1
          return substr(line, i)
        }

        rhs=substr(line, i, close_pos - i + 1)
        tail_pos=close_pos + 1

        while (tail_pos <= n && substr(line, tail_pos, 1) ~ /[[:space:]]/) {
          tail_pos++
        }

        if (tail_pos <= n) {
          next_char=substr(line, tail_pos, 1)

          if (!local_value_boundary_char(next_char)) {
            local_rhs_forced_candidate=1
          }
        }

        return rhs
      }

      if (first == "<") {
        close_pos=i + 1

        while (close_pos <= n && substr(line, close_pos, 1) != ">") {
          close_pos++
        }

        if (close_pos > n) {
          local_rhs_forced_candidate=1
          return substr(line, i)
        }

        rhs=substr(line, i, close_pos - i + 1)
        tail_pos=close_pos + 1

        while (tail_pos <= n && substr(line, tail_pos, 1) ~ /[[:space:]]/) {
          tail_pos++
        }

        if (tail_pos <= n) {
          next_char=substr(line, tail_pos, 1)

          if (!local_value_boundary_char(next_char)) {
            local_rhs_forced_candidate=1
          }
        }

        return rhs
      }

      close_pos=i

      while (close_pos <= n) {
        ch=substr(line, close_pos, 1)

        if (local_value_boundary_char(ch)) {
          break
        }

        close_pos++
      }

      if (close_pos == i) {
        local_rhs_forced_candidate=1
        return substr(line, i, 1)
      }

      return substr(line, i, close_pos - i)
    }

    function rhs_is_candidate(rhs, n, first, last, q) {
      rhs=trim(rhs)
      n=length(rhs)

      if (n >= 2) {
        q=sprintf("%c", 39)
        first=substr(rhs, 1, 1)
        last=substr(rhs, n, 1)

        if ((first == "\"" && last == "\"") || (first == q && last == q)) {
          rhs=substr(rhs, 2, n - 2)
        }
      }

      rhs=trim(rhs)

      if (rhs == "") {
        return 0
      }

      if (structured_placeholder(rhs)) {
        return 0
      }

      return 1
    }

    {
      line=$0

      for (delimiter_pos=1; delimiter_pos<=length(line); delimiter_pos++) {
        delimiter=substr(line, delimiter_pos, 1)

        if (delimiter != ":" && delimiter != "=") {
          continue
        }

        key=extract_local_key(line, delimiter_pos)

        if (key == "") {
          continue
        }

        if (!sensitive_key(key)) {
          continue
        }

        if (delimiter == ":" && local_key_quote_state == 0) {
          next_char=substr(line, delimiter_pos + 1, 1)
          q=sprintf("%c", 39)

          if (next_char !~ /[[:space:]]/ &&
              next_char != "\"" &&
              next_char != q) {
            if (approved_sensitive_provenance(line, delimiter_pos, key)) {
              continue
            }
          }
        }

        rhs=extract_local_rhs(line, delimiter_pos)

        if (local_rhs_forced_candidate) {
          exit 10
        }

        if (rhs_is_candidate(rhs)) {
          exit 10
        }
      }
    }
  ' "$abs" >/dev/null 2>&1
  then
    return 0
  else
    rc=$?

    if [ "$rc" -eq 10 ]; then
      return 10
    fi

    return 20
  fi
}

while IFS= read -r rel
do
  abs="$ROOT/$rel"
  if [ -e "$abs" ] || [ -L "$abs" ]; then
    sensitive_found=0
    scan_error=0

    if scan_one_pattern "$abs" "$rel" "$SENSITIVE_ASSIGNMENT_ERE"; then
      :
    else
      rc=$?
      if [ "$rc" -eq 10 ]; then sensitive_found=1; else scan_error=1; fi
    fi

    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
      if scan_one_pattern "$abs" "$rel" "$SENSITIVE_TOKEN_ERE"; then
        :
      else
        rc=$?
        if [ "$rc" -eq 10 ]; then sensitive_found=1; else scan_error=1; fi
      fi
    fi

    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
      if scan_one_pattern "$abs" "$rel" "$SENSITIVE_QUOTED_ASSIGNMENT_ERE"; then
        :
      else
        rc=$?
        if [ "$rc" -eq 10 ]; then sensitive_found=1; else scan_error=1; fi
      fi
    fi

    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
      if scan_one_pattern "$abs" "$rel" "$SENSITIVE_TOKEN_QUOTED_VALUE_ERE"; then
        :
      else
        rc=$?
        if [ "$rc" -eq 10 ]; then sensitive_found=1; else scan_error=1; fi
      fi
    fi

      if scan_one_pattern_case_sensitive "$abs" "$rel" "$SENSITIVE_ENV_ASSIGNMENT_ERE"; then
        :
      else
        rc=$?
        if [ "$rc" -eq 10 ]; then sensitive_found=1; else scan_error=1; fi
      fi
    fi

    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
      if scan_one_pattern "$abs" "$rel" "$SENSITIVE_TOKEN_COMPACT_UNQUOTED_ERE"; then
        :
      else
        rc=$?
        if [ "$rc" -eq 10 ]; then
          sensitive_found=1
        else
          scan_error=1
        fi
      fi
    fi

    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
      if scan_one_pattern_case_sensitive "$abs" "$rel" "$SENSITIVE_LOWER_PREFIX_NON_TOKEN_ASSIGNMENT_ERE"; then
        :
      else
        rc=$?
        if [ "$rc" -eq 10 ]; then
          sensitive_found=1
        else
          scan_error=1
        fi
      fi
    fi

    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
      if scan_one_pattern_case_sensitive "$abs" "$rel" "$SENSITIVE_LOWER_PREFIX_TOKEN_EXPLICIT_ASSIGNMENT_ERE"; then
        :
      else
        rc=$?
        if [ "$rc" -eq 10 ]; then
          sensitive_found=1
        else
          scan_error=1
        fi
      fi
    fi

    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
      if scan_one_pattern "$abs" "$rel" "$SENSITIVE_SUFFIX_ASSIGNMENT_ERE"; then
        :
      else
        rc=$?
        if [ "$rc" -eq 10 ]; then
          sensitive_found=1
        else
          scan_error=1
        fi
      fi
    fi

    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
      if scan_one_pattern         "$abs"         "$rel"         "$SENSITIVE_DOUBLE_QUOTED_SUFFIX_ASSIGNMENT_ERE"
      then
        :
      else
        rc=$?

        if [ "$rc" -eq 10 ]; then
          sensitive_found=1
        else
          scan_error=1
        fi
      fi
    fi

    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
      if scan_one_pattern         "$abs"         "$rel"         "$SENSITIVE_SINGLE_QUOTED_SUFFIX_ASSIGNMENT_ERE"
      then
        :
      else
        rc=$?

        if [ "$rc" -eq 10 ]; then
          sensitive_found=1
        else
          scan_error=1
        fi
      fi
    fi

    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
      if scan_sensitive_assignment_classifier "$abs"; then
        :
      else
        rc=$?

        if [ "$rc" -eq 10 ]; then
          sensitive_found=1
        else
          scan_error=1
        fi
      fi
    fi

    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
      if scan_one_pattern "$abs" "$rel" "$SENSITIVE_AUTH_ERE"; then
        :
      else
        rc=$?
        if [ "$rc" -eq 10 ]; then sensitive_found=1; else scan_error=1; fi
      fi
    fi

    if [ "$sensitive_found" -eq 0 ] && [ "$scan_error" -eq 0 ]; then
      if scan_one_pattern "$abs" "$rel" "$SENSITIVE_PRIVATE_KEY_ERE"; then
        :
      else
        rc=$?
        if [ "$rc" -eq 10 ]; then sensitive_found=1; else scan_error=1; fi
      fi
    fi

    if [ "$sensitive_found" -eq 1 ]; then
      add_failure "sensitive pattern detected in $rel"
    fi
    if [ "$scan_error" -eq 1 ]; then
      add_failure "sensitive scan error in $rel"
    fi
  fi
done <<EOF
$(required_files)
EOF

# Content validations below never print matched source lines or values.
PROJECT="$ROOT/docs/process/PROJECT-RULES.md"
ROADMAP="$ROOT/docs/process/PORTFOLIO-ROADMAP.md"
CHECKLIST="$ROOT/docs/process/TASK-START-CHECKLIST.md"
GPT="$ROOT/docs/process/GPT-REVIEW-CONTRACT.md"
WORKFLOW="$ROOT/docs/process/DEVELOPMENT-WORKFLOW.md"
LEARNING="$ROOT/docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md"
MATRIX="$ROOT/docs/process/DOCUMENT-MIGRATION-MATRIX.md"
AGENT="$ROOT/.claude/agents/ticketing-risk-reviewer.md"

# ---------------------------------------------------------------------------
# C. Process-document cross references.
# ---------------------------------------------------------------------------

: > "$REF_FILE"
while IFS= read -r rel
do
  abs="$ROOT/$rel"
  if [ -f "$abs" ] && [ ! -L "$abs" ]; then
    grep -Eo 'docs/process/[A-Za-z0-9._/-]+\.md' "$abs" >> "$REF_FILE" 2>/dev/null || true
  fi
done <<'EOF'
CLAUDE.md
.claude/agents/ticketing-risk-reviewer.md
docs/process/PROJECT-RULES.md
docs/process/PORTFOLIO-ROADMAP.md
docs/process/TASK-START-CHECKLIST.md
docs/process/GPT-REVIEW-CONTRACT.md
docs/process/DEVELOPMENT-WORKFLOW.md
docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md
docs/process/DOCUMENT-MIGRATION-MATRIX.md
EOF

if [ -s "$REF_FILE" ]; then
  sort -u "$REF_FILE" -o "$REF_FILE"
  while IFS= read -r ref
  do
    if [ ! -f "$ROOT/$ref" ] || [ -L "$ROOT/$ref" ]; then
      add_failure "broken process document reference: $ref"
    fi
  done < "$REF_FILE"
fi

# ---------------------------------------------------------------------------
# D/E. Migration Matrix structure, aggregates and related # references.
# ---------------------------------------------------------------------------

if [ -f "$MATRIX" ] && [ ! -L "$MATRIX" ]; then
  MATRIX_TABLE_SECTION='## 4. 684-row Migration Matrix'
  MATRIX_TABLE_HEADER='| 번호 | 출처 ID | 원문 장·절 | 원문 핵심 내용 | 현재 B 대응 위치 | 대응 상태 | 제안 상태 | 목표 문서 | 제안 이유 | 잃을 수 있는 정보·위험 | 사용자 승인 필요 | 자동 검증 후보 |'
  MATRIX_TABLE_SEPARATOR='| ---: | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |'

  check_visible_heading_once \
    "$MATRIX" \
    "$MATRIX_TABLE_SECTION" \
    "Migration Matrix approved section heading count is not exactly 1"

  if markdown_table_rows_in_section \
    "$MATRIX" \
    "$MATRIX_TABLE_SECTION" \
    "$MATRIX_TABLE_HEADER" \
    "$MATRIX_TABLE_SEPARATOR" \
    '^\\| [0-9]+ \\|' \
    1 \
    > "$ROW_FILE"
  then
    :
  else
    : > "$ROW_FILE"
    add_failure \
      "Migration Matrix approved table topology mismatch"
  fi

  header_line="$MATRIX_TABLE_HEADER"

  header_columns="$(
    printf '%s\n' "$header_line" |
      awk -F'|' '{ print NF - 2 }'
  )"

  if [ "$header_columns" -ne 12 ]; then
    add_failure "Migration Matrix header is not 12 columns"
  fi

  matrix_rows="$(wc -l < "$ROW_FILE" | awk '{ print $1 }')"
  if [ "$matrix_rows" -ne 684 ]; then
    add_failure "Migration Matrix data row count is not 684"
  fi

  if ! awk -F'|' '
    function trim(s) { gsub(/^[[:space:]]+|[[:space:]]+$/, "", s); return s }
    {
      if (NF - 2 != 12) bad = 1
      id = trim($2) + 0
      n++
      if (id != n) sequence_bad = 1
      seen[id]++
      if (seen[id] > 1) duplicate = 1
    }
    END {
      if (bad || sequence_bad || duplicate || n != 684) exit 1
    }
  ' "$ROW_FILE"; then
    add_failure "Migration Matrix column/ID sequence/duplicate validation failed"
  fi

  count_a="$(awk -F'|' 'function t(s){gsub(/^[[:space:]]+|[[:space:]]+$/,"",s);return s} {v=t($3); if (substr(v,1,1)=="A") c++} END{print c+0}' "$ROW_FILE")"
  count_b="$(awk -F'|' 'function t(s){gsub(/^[[:space:]]+|[[:space:]]+$/,"",s);return s} {v=t($3); if (substr(v,1,1)=="B") c++} END{print c+0}' "$ROW_FILE")"
  count_c="$(awk -F'|' 'function t(s){gsub(/^[[:space:]]+|[[:space:]]+$/,"",s);return s} {v=t($3); if (substr(v,1,1)=="C") c++} END{print c+0}' "$ROW_FILE")"
  [ "$count_a" -eq 378 ] || add_failure "Migration Matrix A row count is not 378"
  [ "$count_b" -eq 227 ] || add_failure "Migration Matrix B row count is not 227"
  [ "$count_c" -eq 79 ] || add_failure "Migration Matrix C row count is not 79"

  if ! awk -F'|' '
    function t(s) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
      return s
    }

    {
      v=t($3)

      if (!(v=="A-01" ||
            v=="A-02" ||
            v=="A-03" ||
            v=="A-04" ||
            v=="A-05" ||
            v=="A-06" ||
            v=="B-01" ||
            v=="B-02" ||
            v=="B-03" ||
            v=="B-04" ||
            v=="B-05" ||
            v=="B-06" ||
            v=="B-07" ||
            v=="B-08" ||
            v=="C-01")) {
        bad=1
      }
    }

    END {
      if (bad) exit 1
    }
  ' "$ROW_FILE"; then
    add_failure "Migration Matrix contains unapproved source ID"
  fi

  matrix_source_count() {
    source_id="$1"

    awk -F'|' -v source_id="$source_id" '
      function t(s) {
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
        return s
      }

      {
        if (t($3) == source_id) {
          c++
        }
      }

      END {
        print c+0
      }
    ' "$ROW_FILE"
  }

  while IFS=' ' read -r source_id expected_count
  do
    actual_count="$(matrix_source_count "$source_id")"

    if [ "$actual_count" -ne "$expected_count" ]; then
      add_failure "Migration Matrix source count mismatch: $source_id"
    fi
  done <<'EOF_SOURCE_COUNTS'
A-01 17
A-02 117
A-03 106
A-04 55
A-05 69
A-06 14
B-01 31
B-02 1
B-03 24
B-04 44
B-05 12
B-06 62
B-07 25
B-08 28
C-01 79
EOF_SOURCE_COUNTS

  source_projection_sha="$(
    awk -F'|' '
      function t(s) {
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
        return s
      }

      {
        print t($2) "|" t($3)
      }
    ' "$ROW_FILE" |
      sha256_stream 2>/dev/null ||
      true
  )"

  if [ -z "$source_projection_sha" ]; then
    add_failure "Migration Matrix source projection SHA-256 unavailable"
  elif [ "$source_projection_sha" != "6bd2348e8bfbbcaebb422e8e191b98a3fa7b14167cb4587c2ddf6b363d08da19" ]; then
    add_failure "Migration Matrix source projection SHA-256 mismatch"
  fi

  matrix_exact_count() {
    col="$1"
    expected="$2"
    awk -F'|' -v col="$col" -v expected="$expected" '
      function t(s){gsub(/^[[:space:]]+|[[:space:]]+$/,"",s);return s}
      { if (t($col) == expected) c++ }
      END { print c+0 }
    ' "$ROW_FILE"
  }

  [ "$(matrix_exact_count 8 유지)" -eq 197 ] || add_failure "Migration Matrix proposal 유지 count mismatch"
  [ "$(matrix_exact_count 8 병합)" -eq 150 ] || add_failure "Migration Matrix proposal 병합 count mismatch"
  [ "$(matrix_exact_count 8 수정)" -eq 257 ] || add_failure "Migration Matrix proposal 수정 count mismatch"
  [ "$(matrix_exact_count 8 제외)" -eq 7 ] || add_failure "Migration Matrix proposal 제외 count mismatch"
  [ "$(matrix_exact_count 8 신규)" -eq 73 ] || add_failure "Migration Matrix proposal 신규 count mismatch"

  [ "$(matrix_exact_count 7 '완전 대응')" -eq 195 ] || add_failure "Migration Matrix mapping 완전 대응 count mismatch"
  [ "$(matrix_exact_count 7 '부분 대응')" -eq 307 ] || add_failure "Migration Matrix mapping 부분 대응 count mismatch"
  [ "$(matrix_exact_count 7 '대응 없음')" -eq 147 ] || add_failure "Migration Matrix mapping 대응 없음 count mismatch"
  [ "$(matrix_exact_count 7 충돌)" -eq 35 ] || add_failure "Migration Matrix mapping 충돌 count mismatch"

  if ! awk -F'|' '
    function t(s) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
      return s
    }

    {
      if (t($2) != "459") {
        next
      }

      seen++

      if (t($3) == "B-04" &&
          t($7) == "완전 대응" &&
          t($8) == "유지" &&
          t($9) == "docs/process/PROJECT-RULES.md") {
        exact++
      }
    }

    END {
      if (seen != 1 ||
          exact != 1) {
        exit 1
      }
    }
  ' "$ROW_FILE"
  then
    add_failure \
      "Migration Matrix row 459 test-evidence mapping mismatch"
  fi

  validation_auto="$(awk -F'|' 'function t(s){gsub(/^[[:space:]]+|[[:space:]]+$/,"",s);return s} {v=t($13); if (index(v,"자동 검증:")==1) c++} END{print c+0}' "$ROW_FILE")"
  validation_manual="$(matrix_exact_count 13 '수동 검토')"
  validation_none="$(matrix_exact_count 13 '검증 불필요')"
  [ "$validation_auto" -eq 495 ] || add_failure "Migration Matrix validation 자동 count mismatch"
  [ "$validation_manual" -eq 183 ] || add_failure "Migration Matrix validation 수동 count mismatch"
  [ "$validation_none" -eq 6 ] || add_failure "Migration Matrix validation 없음 count mismatch"

  if ! awk -F'|' '
    function t(s){gsub(/^[[:space:]]+|[[:space:]]+$/,"",s);return s}
    {
      v=t($9)
      if (!(v=="CLAUDE.md" ||
            v==".claude/agents/ticketing-risk-reviewer.md" ||
            v=="docs/process/PROJECT-RULES.md" ||
            v=="docs/process/PORTFOLIO-ROADMAP.md" ||
            v=="docs/process/TASK-START-CHECKLIST.md" ||
            v=="docs/process/GPT-REVIEW-CONTRACT.md" ||
            v=="docs/process/DEVELOPMENT-WORKFLOW.md" ||
            v=="docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md" ||
            v=="docs/process/DOCUMENT-MIGRATION-MATRIX.md" ||
            v=="scripts/validate-process-docs.sh" ||
            v=="scripts/test-validate-process-docs.sh")) bad=1
    }
    END { if (bad) exit 1 }
  ' "$ROW_FILE"; then
    add_failure "Migration Matrix contains target outside approved 11-file scope"
  fi

  if ! awk '
    {
      s=$0
      while (match(s, /\(관련 [^)]*\)/)) {
        group_start=RSTART
        group_length=RLENGTH
        group=substr(s, group_start, group_length)
        rest=group
        while (match(rest, /#[0-9]+/)) {
          reftext=substr(rest, RSTART, RLENGTH)
          gsub(/[^0-9]/, "", reftext)
          ref=reftext+0
          if (ref < 1 || ref > 684) bad=1
          rest=substr(rest, RSTART+RLENGTH)
        }
        s=substr(s, group_start+group_length)
      }
    }
    END { if (bad) exit 1 }
  ' "$ROW_FILE"; then
    add_failure "Migration Matrix broken related # reference detected"
  fi

  if ! awk '
    {
      s=$0
      while (match(s, /\(관련 [^)]*\)/)) {
        group_start=RSTART
        group_length=RLENGTH
        group=substr(s, group_start, group_length)
        rest=group
        while (match(rest, /#[0-9]+/)) {
          reftext=substr(rest, RSTART, RLENGTH)
          gsub(/[^0-9]/, "", reftext)
          key=NR SUBSEP reftext
          if (seen[key]++) bad=1
          rest=substr(rest, RSTART+RLENGTH)
        }
        s=substr(s, group_start+group_length)
      }
    }
    END { if (bad) exit 1 }
  ' "$ROW_FILE"; then
    add_failure "Migration Matrix duplicate related # reference detected within a row"
  fi
fi

# ---------------------------------------------------------------------------
# F. CFL-01~20 fixed mapping.
# ---------------------------------------------------------------------------

if [ -f "$MATRIX" ] && [ ! -L "$MATRIX" ]; then
  CFL_SECTION_HEADING='## 5. CFL-01~20 최종 결정 연결'
  CFL_TABLE_HEADER='| CFL | 연결 결정 | 최종 책임 문서 | 최종 해소 의미 |'
  CFL_TABLE_SEPARATOR='| --- | ---: | --- | --- |'

  check_visible_heading_once \
    "$MATRIX" \
    "$CFL_SECTION_HEADING" \
    "CFL approved section heading count is not exactly 1"

  if cfl_section_lines="$(
    markdown_section_lines \
      "$MATRIX" \
      "$CFL_SECTION_HEADING"
  )"
  then
  cfl_header_count="$(
    printf '%s\n' "$cfl_section_lines" |
      awk \
        -v expected="$CFL_TABLE_HEADER" '
        $0 == expected {
          count++
        }

        END {
          print count + 0
        }
      '
  )"

  if [ "$cfl_header_count" -ne 1 ]; then
    add_failure \
      "CFL approved table header count is not exactly 1"
  fi

  cfl_separator_after_header_count="$(
    printf '%s\n' "$cfl_section_lines" |
      awk \
        -v header="$CFL_TABLE_HEADER" \
        -v separator="$CFL_TABLE_SEPARATOR" '
        {
          if (awaiting_separator) {
            if ($0 == separator) {
              count++
            }

            awaiting_separator=0
          }

          if ($0 == header) {
            awaiting_separator=1
          }
        }

        END {
          print count + 0
        }
      '
  )"

  if [ "$cfl_separator_after_header_count" -ne 1 ]; then
    add_failure \
      "CFL approved table separator is not exactly anchored after header"
  fi

  cfl_table_rows() {
    printf '%s\n' "$cfl_section_lines" |
      awk \
        -v header="$CFL_TABLE_HEADER" \
        -v separator="$CFL_TABLE_SEPARATOR" '
        BEGIN {
          state=0
          done=0
        }

        done {
          next
        }

        state == 0 {
          if ($0 == header) {
            state=1
          }

          next
        }

        state == 1 {
          if ($0 == separator) {
            state=2
          } else {
            done=1
          }

          next
        }

        state == 2 {
          if ($0 ~ /^\| CFL-[0-9][0-9] \|/) {
            print
            next
          }

          done=1
          next
        }
      '
  }

  cfl_total="$(
    cfl_table_rows |
      awk '
        END {
          print NR + 0
        }
      '
  )"

  if [ "$cfl_total" -ne 20 ]; then
    add_failure "CFL final mapping row count is not 20"
  fi

  cfl_duplicate="$(
    cfl_table_rows |
      awk -F'|' '
        function t(s) {
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
          return s
        }

        {
          id=t($2)
          seen[id]++

          if (seen[id] > 1) {
            duplicate=1
          }
        }

        END {
          print duplicate + 0
        }
      '
  )"

  if [ "$cfl_duplicate" -ne 0 ]; then
    add_failure "CFL duplicate final mapping ID detected"
  fi

  while IFS='|' read -r cfl decision owner
  do
    cfl_exact="$(
      cfl_table_rows |
        awk \
          -F'|' \
          -v expected_id="$cfl" \
          -v expected_decision="$decision" \
          -v expected_owner="$owner" '
          function t(s) {
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
            return s
          }

          {
            actual_id=t($2)

            if (actual_id != expected_id) {
              next
            }

            seen++

            actual_decision=t($3)
            sub(/^결정[[:space:]]+/, "", actual_decision)

            actual_owner=t($4)

            if (actual_owner ~ /^`[^`]+`$/) {
              sub(/^`/, "", actual_owner)
              sub(/`$/, "", actual_owner)
            }

            if (actual_decision == expected_decision &&
                actual_owner == expected_owner) {
              exact++
            }
          }

          END {
            if (seen == 1 && exact == 1) {
              print 1
            } else {
              print 0
            }
          }
        '
    )"

    if [ "$cfl_exact" -ne 1 ]; then
      add_failure \
        "$cfl final decision/owner mapping mismatch"
    fi
  done <<'EOF_CFL_EXPECTED'
CFL-01|5|docs/process/GPT-REVIEW-CONTRACT.md
CFL-02|1|docs/process/PORTFOLIO-ROADMAP.md
CFL-03|18|docs/process/DEVELOPMENT-WORKFLOW.md
CFL-04|3|docs/process/PROJECT-RULES.md
CFL-05|9|docs/process/PROJECT-RULES.md
CFL-06|6|docs/process/TASK-START-CHECKLIST.md
CFL-07|7|docs/process/GPT-REVIEW-CONTRACT.md
CFL-08|15|docs/process/DEVELOPMENT-WORKFLOW.md
CFL-09|16|docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md
CFL-10|8|docs/process/GPT-REVIEW-CONTRACT.md
CFL-11|19|docs/process/PROJECT-RULES.md
CFL-12|10|docs/process/PROJECT-RULES.md
CFL-13|14|docs/process/GPT-REVIEW-CONTRACT.md
CFL-14|2|docs/process/PORTFOLIO-ROADMAP.md
CFL-15|11|docs/process/PROJECT-RULES.md
CFL-16|12|docs/process/DEVELOPMENT-WORKFLOW.md
CFL-17|20|docs/process/GPT-REVIEW-CONTRACT.md
CFL-18|17|docs/process/GPT-REVIEW-CONTRACT.md
CFL-19|21|docs/process/PROJECT-RULES.md
CFL-20|22|docs/process/GPT-REVIEW-CONTRACT.md
EOF_CFL_EXPECTED
  else
    add_failure "CFL Markdown section parser failed"
  fi
fi

# ---------------------------------------------------------------------------
# G. Roadmap structural state and coverage.
# ---------------------------------------------------------------------------

if [ -f "$ROADMAP" ] && [ ! -L "$ROADMAP" ]; then
  ROADMAP_HISTORY_SECTION='## 전체 TASK 이력'
  ROADMAP_CURRENT_SECTION='## 현재 확인 상태'

  ROADMAP_STATUS_SECTION='## 상태 기준'

  check_visible_heading_once \
    "$ROADMAP" \
    "$ROADMAP_STATUS_SECTION" \
    "Roadmap status-definition section heading count is not exactly 1"

  check_context_fixed \
    "$ROADMAP" \
    LINE \
    "$ROADMAP_STATUS_SECTION" \
    '' '' '' '' '' \
    '- `완료`: 실제 완료 근거가 확인된 TASK' \
    "Roadmap 완료 status definition mismatch"

  check_context_fixed \
    "$ROADMAP" \
    LINE \
    "$ROADMAP_STATUS_SECTION" \
    '' '' '' '' '' \
    '- `진행중`: 사용자 승인 후 실제 진행 중인 TASK' \
    "Roadmap 진행중 status definition mismatch"

  check_context_fixed \
    "$ROADMAP" \
    LINE \
    "$ROADMAP_STATUS_SECTION" \
    '' '' '' '' '' \
    '- `계획`: 사용자 승인된 후속 TASK' \
    "Roadmap 계획 status definition mismatch"

  check_context_fixed \
    "$ROADMAP" \
    LINE \
    "$ROADMAP_STATUS_SECTION" \
    '' '' '' '' '' \
    '- `재확인필요`: 과거 자료에는 있으나 현재 완료/진행 근거가 확인되지 않은 항목' \
    "Roadmap 재확인필요 status definition mismatch"


  ROADMAP_HISTORY_HEADER='| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |'
  ROADMAP_HISTORY_SEPARATOR='| --- | --- | --- | --- | --- |'
  ROADMAP_HISTORY_TABLE_COUNT=13

  ROADMAP_CURRENT_HEADER='| TASK | 내용 | 상태 | 확인 근거 |'
  ROADMAP_CURRENT_SEPARATOR='| --- | --- | --- | --- |'
  ROADMAP_CURRENT_TABLE_COUNT=1

  ROADMAP_HISTORY_ID_SHA256='a3265b7dfa868d1b83081e6db14dac3eca1f6bc5cfcaa287f90a598454f7b4e2'

  ROADMAP_CURRENT_EXPECTED_IDS="$(cat <<'EOF_ROADMAP_CURRENT_EXPECTED_IDS'
TASK-031
TASK-059
TASK-060
EOF_ROADMAP_CURRENT_EXPECTED_IDS
)"

  ROADMAP_PREREQUISITE_SECTION='## 다음 직접 진행 순서'
  ROADMAP_PREREQUISITE_HEADER='| TASK | 내용 | 상태 | 선행 조건 |'
  ROADMAP_PREREQUISITE_SEPARATOR='| --- | --- | --- | --- |'
  ROADMAP_TASK_032_ROW='| TASK-032 | 이벤트 기반 예약 처리 (Spring Events) | 계획 | TASK-060 완료 + 사용자 승인 |'

  ROADMAP_PREREQUISITE_EXPECTED_ROWS="$(cat <<'EOF_ROADMAP_PREREQUISITE_EXPECTED_ROWS'
| TASK-032 | 이벤트 기반 예약 처리 (Spring Events) | 계획 | TASK-060 완료 + 사용자 승인 |
| TASK-033 | Outbox Pattern 구현 | 계획 | TASK-032 완료 + 사용자 승인 |
| TASK-033-1 | Outbox 재처리 실패 시나리오 | 계획 | TASK-033 완료 + 사용자 승인 |
| TASK-028 | Redis/DB 장애 시나리오 구현 및 문서화 | 계획 | TASK-033-1 완료 + 사용자 승인 |
| TASK-043 | 장애 복구 전략 설계 + 코드 구현 | 계획 | TASK-028 완료 + 사용자 승인 |
EOF_ROADMAP_PREREQUISITE_EXPECTED_ROWS
)"

  check_visible_heading_once \
    "$ROADMAP" \
    "$ROADMAP_HISTORY_SECTION" \
    "Roadmap history section heading count is not exactly 1"

  check_visible_heading_once \
    "$ROADMAP" \
    "$ROADMAP_CURRENT_SECTION" \
    "Roadmap current-status section heading count is not exactly 1"

  if markdown_table_rows_in_section \
    "$ROADMAP" \
    "$ROADMAP_HISTORY_SECTION" \
    "$ROADMAP_HISTORY_HEADER" \
    "$ROADMAP_HISTORY_SEPARATOR" \
    '^\\| TASK-[0-9]+(-[0-9]+)? \\|' \
    "$ROADMAP_HISTORY_TABLE_COUNT" \
    > "$ROADMAP_HISTORY_FILE"
  then
    :
  else
    : > "$ROADMAP_HISTORY_FILE"
    add_failure \
      "Roadmap history approved table topology mismatch"
  fi

  roadmap_history_count="$(
    awk '
      END {
        print NR + 0
      }
    ' "$ROADMAP_HISTORY_FILE"
  )"

  if [ "$roadmap_history_count" -ne 76 ]; then
    add_failure "Roadmap full history TASK row count is not 76"
  fi

  if ! awk -F'|' '
    function t(s) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
      return s
    }

    {
      id=t($2)
      seen[id]++

      if (seen[id] > 1) {
        bad=1
      }
    }

    END {
      if (bad) {
        exit 1
      }
    }
  ' "$ROADMAP_HISTORY_FILE"
  then
    add_failure "Roadmap duplicate TASK ID detected in full history"
  fi

  if roadmap_history_id_sha="$(
    awk -F'|' '
      function t(s) {
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
        return s
      }

      {
        print t($2)
      }
    ' "$ROADMAP_HISTORY_FILE" |
      LC_ALL=C sort |
      sha256_stream
  )"; then
    if [ "$roadmap_history_id_sha" != "$ROADMAP_HISTORY_ID_SHA256" ]; then
      add_failure         "Roadmap history TASK ID membership mismatch"
    fi
  else
    add_failure       "Roadmap history TASK ID membership hash unavailable"
  fi

  roadmap_status_count() {
    status="$1"

    awk \
      -F'|' \
      -v status="$status" '
      function t(s) {
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
        return s
      }

      {
        if (t($4) == status) {
          count++
        }
      }

      END {
        print count + 0
      }
    ' "$ROADMAP_HISTORY_FILE"
  }

  if ! awk -F'|' '
    function t(s) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
      return s
    }

    {
      status=t($4)

      if (!(status == "완료" ||
            status == "진행중" ||
            status == "계획" ||
            status == "재확인필요")) {
        bad=1
      }
    }

    END {
      if (bad) {
        exit 1
      }
    }
  ' "$ROADMAP_HISTORY_FILE"
  then
    add_failure \
      "Roadmap history contains unapproved status enum"
  fi

  [ "$(roadmap_status_count 완료)" -eq 2 ] ||
    add_failure "Roadmap 완료 count is not 2"

  [ "$(roadmap_status_count '진행중')" -eq 1 ] ||
    add_failure "Roadmap 진행중 count is not 1"

  [ "$(roadmap_status_count 계획)" -eq 5 ] ||
    add_failure "Roadmap 계획 count is not 5"

  [ "$(roadmap_status_count '재확인필요')" -eq 68 ] ||
    add_failure "Roadmap 재확인필요 count is not 68"

  if ! awk -F'|' '
    function t(s) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
      return s
    }

    /^\| TASK-059 \|/ {
      if (t($4) == "완료" &&
          index($0, "122816d91533befcc7a63c06cf83a7150e1fd05d") > 0) {
        ok=1
      }
    }

    END {
      if (!ok) {
        exit 1
      }
    }
  ' "$ROADMAP_HISTORY_FILE"
  then
    add_failure "Roadmap TASK-059 completion status or merge SHA mismatch"
  fi

  if ! awk -F'|' '
    function t(s) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
      return s
    }

    /^\| TASK-060 \|/ {
      if (t($4) == "진행중") {
        ok=1
      }
    }

    END {
      if (!ok) {
        exit 1
      }
    }
  ' "$ROADMAP_HISTORY_FILE"
  then
    add_failure "Roadmap TASK-060 is not 진행중 in full history"
  fi

  if roadmap_prerequisite_rows="$(
    markdown_table_rows_in_section \
      "$ROADMAP" \
      "$ROADMAP_PREREQUISITE_SECTION" \
      "$ROADMAP_PREREQUISITE_HEADER" \
      "$ROADMAP_PREREQUISITE_SEPARATOR" \
      '^\\| TASK-[0-9]+(-[0-9]+)? \\|' \
      1
  )"; then
    roadmap_prerequisite_row_count="$(
      printf '%s\n' "$roadmap_prerequisite_rows" |
        awk '
          NF {
            count++
          }

          END {
            print count + 0
          }
        '
    )"

    if [ "$roadmap_prerequisite_row_count" -ne 5 ] ||
       [ "$roadmap_prerequisite_rows" != "$ROADMAP_PREREQUISITE_EXPECTED_ROWS" ]; then
      add_failure \
        "Roadmap next-task prerequisite contract mismatch"
    fi

    roadmap_task_032_exact_count="$(
      printf '%s\n' "$roadmap_prerequisite_rows" |
        awk \
          -v expected="$ROADMAP_TASK_032_ROW" '
          $0 == expected {
            count++
          }

          END {
            print count + 0
          }
        '
    )"

    if [ "$roadmap_task_032_exact_count" -ne 1 ]; then
      add_failure \
        "Roadmap TASK-032 prerequisite mismatch"
    fi
  else
    add_failure \
      "Roadmap next-task prerequisite contract mismatch"
    add_failure \
      "Roadmap TASK-032 prerequisite mismatch"
  fi

  roadmap_direct_context_ready=1

  if markdown_visible_snapshot_strict \
    "$ROADMAP" \
    "$ROADMAP_VISIBLE_FILE"
  then
    :
  else
    add_failure \
      "Roadmap direct TASK Markdown parser failed"
    roadmap_direct_context_ready=0
  fi

  if [ "$roadmap_direct_context_ready" -eq 1 ]; then
    if direct_order="$(
      markdown_fenced_payload_after_visible_anchor \
        "$ROADMAP" \
        "$ROADMAP_VISIBLE_FILE" \
        "## 다음 직접 진행 순서" \
        "" \
        "사용자 승인 없이 다음 TASK를 자동으로 시작하지 않는다." \
        '```text' \
        '```' \
        1
    )"
    then
      expected_order='TASK-032
→ TASK-033
→ TASK-033-1
→ TASK-028
→ TASK-043'

      if [ "$direct_order" != "$expected_order" ]; then
        add_failure "Roadmap direct TASK order mismatch"
      fi
    else
      add_failure \
        "Roadmap direct TASK fenced block missing or malformed"
    fi
  fi

  if roadmap_current_rows="$(
    markdown_table_rows_in_section \
      "$ROADMAP" \
      "$ROADMAP_CURRENT_SECTION" \
      "$ROADMAP_CURRENT_HEADER" \
      "$ROADMAP_CURRENT_SEPARATOR" \
      '^\\| TASK-[0-9]+(-[0-9]+)? \\|' \
      "$ROADMAP_CURRENT_TABLE_COUNT"
  )"
  then
    :
  else
    roadmap_current_rows=''
    add_failure \
      "Roadmap current-status approved table topology mismatch"
  fi

  roadmap_current_row_count="$(
    printf '%s\n' "$roadmap_current_rows" |
      awk 'NF { count++ } END { print count + 0 }'
  )"

  roadmap_current_actual_ids="$(
    printf '%s\n' "$roadmap_current_rows" |
      awk -F'|' '
        function t(s) {
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
          return s
        }

        NF {
          print t($2)
        }
      ' |
      LC_ALL=C sort
  )"

  if [ "$roadmap_current_row_count" -ne 3 ] ||
     [ "$roadmap_current_actual_ids" != "$ROADMAP_CURRENT_EXPECTED_IDS" ]; then
    add_failure       "Roadmap current-status TASK ID membership mismatch"
  fi

  if ! printf '%s\n' "$roadmap_current_rows" |
    awk -F'|' '
      function t(s) {
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
        return s
      }

      NF {
        status=t($4)

        if (!(status == "완료" ||
              status == "진행중" ||
              status == "계획" ||
              status == "재확인필요")) {
          bad=1
        }
      }

      END {
        if (bad) {
          exit 1
        }
      }
    '
  then
    add_failure \
      "Roadmap current-status contains unapproved status enum"
  fi

  if ! printf '%s\n' "$roadmap_current_rows" |
    awk -F'|' '
      function t(s) {
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
        return s
      }

      /^\| TASK-059 \|/ {
        count059++

        if (t($4) == "완료" &&
            index($0, "122816d91533befcc7a63c06cf83a7150e1fd05d") > 0) {
          ok059++
        }
      }

      /^\| TASK-060 \|/ {
        count060++

        if (t($4) == "진행중") {
          ok060++
        }
      }

      END {
        if (count059 != 1 ||
            ok059 != 1 ||
            count060 != 1 ||
            ok060 != 1) {
          exit 1
        }
      }
    '
  then
    add_failure \
      "Roadmap current-status TASK-059/TASK-060 state consistency failed"
  fi

  ROADMAP_COVERAGE_SECTION='## TASK coverage 기준'

  check_visible_heading_once \
    "$ROADMAP" \
    "$ROADMAP_COVERAGE_SECTION" \
    "Roadmap TASK coverage section heading count is not exactly 1"

  check_context_fixed \
    "$ROADMAP" \
    LINE \
    "$ROADMAP_COVERAGE_SECTION" \
    '' '' '' '' '' \
    '- A old roadmap 고유 TASK: 74' \
    "Roadmap A-old coverage value 74 authoritative context mismatch"

  check_context_fixed \
    "$ROADMAP" \
    LINE \
    "$ROADMAP_COVERAGE_SECTION" \
    '' '' '' '' '' \
    '- 이 후보의 전체 TASK 이력 고유 TASK: 76' \
    "Roadmap final coverage value 76 authoritative context mismatch"

  check_context_fixed \
    "$ROADMAP" \
    LINE \
    "$ROADMAP_COVERAGE_SECTION" \
    '' '' '' '' '' \
    '- A old roadmap에서 누락된 TASK: 없음' \
    "Roadmap A-to-final no-missing coverage authoritative context mismatch"
fi

# ---------------------------------------------------------------------------
# H/I. PROJECT-RULES Git metadata and core technical rules.
# ---------------------------------------------------------------------------

project_fenced_payload_ok() {
  raw_file="$1"
  visible_file="$2"
  parent="$3"
  subsection="$4"
  anchor="$5"
  approved_opener="$6"
  expected="$7"

  payload=''

  if payload="$(
    markdown_fenced_payload_after_visible_anchor \
      "$raw_file" \
      "$visible_file" \
      "$parent" \
      "$subsection" \
      "$anchor" \
      "$approved_opener" \
      '```' \
      0
  )"
  then
    :
  else
    return 1
  fi

  [ "$payload" = "$expected" ]
}

project_direct_prefixes() {
  visible_file="$1"
  parent="$2"
  subsection="$3"
  direct_lines=''

  if direct_lines="$(
    markdown_direct_context_lines \
      "$visible_file" \
      "$parent" \
      "$subsection"
  )"
  then
    :
  else
    return 1
  fi

  printf '%s\n' "$direct_lines" |
    awk '
      /^- `[A-Za-z0-9_-]+`$/ {
        value=$0
        sub(/^- `/, "", value)
        sub(/`$/, "", value)
        print value
      }
    '
}

if [ -f "$PROJECT" ] && [ ! -L "$PROJECT" ]; then
  project_git_metadata_ready=1

  if markdown_visible_snapshot_strict \
    "$PROJECT" \
    "$PROJECT_VISIBLE_FILE"
  then
    :
  else
    add_failure \
      "PROJECT-RULES Git metadata Markdown parser failed"
    project_git_metadata_ready=0
  fi

  if [ "$project_git_metadata_ready" -eq 1 ]; then
    project_prefix_context_ok=1
    project_prefix_lines=''

    if project_prefix_lines="$(
      project_direct_prefixes \
        "$PROJECT_VISIBLE_FILE" \
        "## 3. Git metadata 단일 기준" \
        "### 허용 prefix"
    )"
    then
      printf '%s\n' "$project_prefix_lines" |
        LC_ALL=C sort > "$PREFIX_FILE"
    else
      project_prefix_context_ok=0
      : > "$PREFIX_FILE"
    fi

    actual_prefixes="$(
      awk '{ printf "%s ", $0 }' "$PREFIX_FILE"
    )"
    expected_prefixes="build chore docs feat fix refactor test "

    if [ "$project_prefix_context_ok" -ne 1 ] ||
       [ "$actual_prefixes" != "$expected_prefixes" ]; then
      add_failure \
        "PROJECT-RULES allowed prefix set is not exactly the approved 7"
    fi

    if ! project_fenced_payload_ok \
      "$PROJECT" \
      "$PROJECT_VISIBLE_FILE" \
      "## 3. Git metadata 단일 기준" \
      "### Branch" \
      "형식:" \
      '```text' \
      "{prefix}/TASK-XXX-english-kebab-case"
    then
      add_failure \
        "PROJECT-RULES branch naming pattern missing"
    fi

    if ! project_fenced_payload_ok \
      "$PROJECT" \
      "$PROJECT_VISIBLE_FILE" \
      "## 3. Git metadata 단일 기준" \
      "### Issue" \
      "제목:" \
      '```text' \
      "TASK-XXX 작업 내용 요약"
    then
      add_failure \
        "PROJECT-RULES Issue title pattern missing"
    fi

    if ! project_fenced_payload_ok \
      "$PROJECT" \
      "$PROJECT_VISIBLE_FILE" \
      "## 3. Git metadata 단일 기준" \
      "### Commit / PR" \
      "형식:" \
      '```text' \
      "{prefix}(TASK-XXX): 변경 대상과 구체적인 결과"
    then
      add_failure \
        "PROJECT-RULES Commit/PR pattern missing"
    fi
  fi

  check_context_fixed "$PROJECT" LINE '## 5. 기술 최소 원칙' '### 5.2 Package / Layer' '' '' '' '' '- Controller에서 repository를 직접 호출하지 않는다.' 'PROJECT-RULES Controller-to-Repository prohibition missing'
  check_context_fixed "$PROJECT" LINE '## 5. 기술 최소 원칙' '' '' '' '' '' '기술 규칙은 과거 문서보다 현재 코드·설정·주제별 문서를 우선 확인한다.' 'PROJECT-RULES current evidence priority missing'
  check_context_fixed "$PROJECT" LINE '## 5. 기술 최소 원칙' '### 5.9 DB / Flyway' '' '' '' '' '- 기존 migration 파일을 수정하지 않는다.' 'PROJECT-RULES Flyway immutable migration rule missing'
  check_context_fixed "$PROJECT" LINE '## 5. 기술 최소 원칙' '### 5.7 Redis Key / TTL' '' '' '' '' '- 과거 Key 표나 TTL 값을 현재 사실로 추정하지 않는다.' 'PROJECT-RULES Redis Key/TTL no-guess rule missing'
  check_context_fixed "$PROJECT" LINE '## 5. 기술 최소 원칙' '### 5.10 Profile / Environment' '' '' '' '' '- `.env`, `.envrc`, `.direnv` 본문을 요청하거나 출력하지 않는다.' 'PROJECT-RULES sensitive environment output prohibition missing'
  check_context_fixed "$PROJECT" LINE '## 4. 테스트와 문서 영향' '' '' '' '' '' '- 실행하지 못한 테스트는 실행했다고 기록하지 않는다.' 'PROJECT-RULES test non-execution truthfulness rule missing'

  check_context_fixed "$PROJECT" LINE '## 4. 테스트와 문서 영향' '' '' '' '' '' '- 관련 테스트가 있으면 먼저 실행한다.' 'PROJECT-RULES related-test-first obligation missing'
  check_context_fixed "$PROJECT" LINE '## 4. 테스트와 문서 영향' '' '' '' '' '' '- 실제로 실행한 각 test는 실행 결과와 함께 실제로 실행한 exact full command를 기록한다.' 'PROJECT-RULES executed-test exact-full-command evidence contract missing'
  check_context_fixed "$PROJECT" LINE '## 4. 테스트와 문서 영향' '' '' '' '' '' '- 관련 테스트가 존재하지 않으면 해당 없음으로 기록할 수 있다.' 'PROJECT-RULES no-related-test N/A rule missing'
  check_context_fixed "$PROJECT" LINE '## 5. 기술 최소 원칙' '### 5.6 Test' '' '' '' '' '- 변경과 가장 관련된 테스트가 있으면 먼저 실행한다.' 'PROJECT-RULES technical test-first obligation missing'

  if grep -Eq '관련.*테스트.*실행할 수 있다' "$PROJECT" 2>/dev/null; then
    add_failure "PROJECT-RULES related-test-first obligation weakened to optional"
  fi

  check_context_fixed "$PROJECT" LINE '## 4. 테스트와 문서 영향' '' '' '' '' '' '- README는 TASK 승인 범위 안에서 실제 수정 필요가 있을 때만 수정한다.' 'PROJECT-RULES README TASK-scope/actual-need rule missing'

  check_context_fixed "$PROJECT" LINE '## 4. 테스트와 문서 영향' '' '' '' '' '' '- 실행 방법, 환경, 외부 사용 방식 변경은 대표적인 README 수정 필요 사례이며 유일한 조건이 아니다.' 'PROJECT-RULES README examples-not-exclusive rule missing'

  check_context_fixed "$PROJECT" LINE '## 4. 테스트와 문서 영향' '' '' '' '' '' '- README 영향이 없으면 불필요하게 수정하지 않는다.' 'PROJECT-RULES README no-unnecessary-change rule missing'

  if grep -Fq --     "- README는 실행 방법, 환경, 외부 사용 방식이 바뀔 때만 수정한다."     "$PROJECT" 2>/dev/null; then
    add_failure "PROJECT-RULES README condition narrowed to execution/environment/external-use only"
  fi

  check_context_fixed "$PROJECT" LINE '## 5. 기술 최소 원칙' '### 5.5 Error / Common Response' '' '' '' '' '- 현재 `GlobalExceptionHandler`를 공통 오류 처리 구조의 일부로 사용한다.' 'PROJECT-RULES GlobalExceptionHandler positive responsibility missing'
  check_context_fixed "$PROJECT" LINE '## 5. 기술 최소 원칙' '### 5.5 Error / Common Response' '' '' '' '' '- Security/JWT Filter처럼 `GlobalExceptionHandler` 밖의 framework boundary에서도 필요한 경우 동일한 응답 계약을 구성할 수 있다.' 'PROJECT-RULES framework-boundary response-contract exception missing'
  check_context_fixed "$PROJECT" LINE '## 5. 기술 최소 원칙' '### 5.5 Error / Common Response' '' '' '' '' '- 모든 오류가 반드시 `GlobalExceptionHandler` 하나만 거쳐야 한다는 절대 규칙을 두지 않는다.' 'PROJECT-RULES GlobalExceptionHandler non-exclusive rule missing'

  approved_full_test='./gradlew clean test --no-daemon --stacktrace -Dspring.profiles.active=test'

  if ! project_fenced_payload_ok \
    "$PROJECT" \
    "$PROJECT_VISIBLE_FILE" \
    "## 4. 테스트와 문서 영향" \
    "### 최종 전체 테스트 명령" \
    "최종 전체 테스트의 단일 명령은 다음과 같다." \
    '```bash' \
    "$approved_full_test"
  then
    add_failure \
      "PROJECT-RULES approved full test command authoritative context mismatch"
  fi

  full_test_candidate_count="$(awk 'index($0,"./gradlew clean test")>0 { count++ } END { print count + 0 }' "$PROJECT")"

  if [ "$full_test_candidate_count" -ne 1 ]; then
    add_failure "PROJECT-RULES conflicting full test command detected"
  fi
fi

# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------
# I-2. Development Workflow lifecycle schema and approved transitions.
# ---------------------------------------------------------------------------

if [ -f "$WORKFLOW" ] && [ ! -L "$WORKFLOW" ]; then
  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 8. 테스트' '' '' '' '**실행**' '- 관련 테스트가 있으면 먼저 실행한다.' 'DEVELOPMENT-WORKFLOW related-test-first obligation missing'
  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 8. 테스트' '' '' '' '**실행**' '- 관련 테스트가 존재하지 않으면 해당 없음으로 기록할 수 있다.' 'DEVELOPMENT-WORKFLOW no-related-test N/A rule missing'
  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 8. 테스트' '' '' '' '**실행**' '- 환경 또는 기술적 이유로 관련 테스트를 실행할 수 없으면 이유와 남은 위험을 기록한다.' 'DEVELOPMENT-WORKFLOW related-test unavailable-risk rule missing'
  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 8. 테스트' '' '' '' '**산출물**' '- 실행한 test evidence는 `docs/process/PROJECT-RULES.md`의 공통 test evidence 원칙에 따라 기록한다.' 'DEVELOPMENT-WORKFLOW Stage 8 test-evidence SSOT delegation missing'

  if grep -Eq '관련.*테스트.*실행할 수 있다' "$WORKFLOW" 2>/dev/null; then
    add_failure "DEVELOPMENT-WORKFLOW related-test-first obligation weakened to optional"
  fi

  check_context_fixed "$WORKFLOW" LINE '## 5. 문서와 테스트 영향' '' '' '' '' '' '- README는 TASK 승인 범위 안에서 실제 수정 필요가 있을 때만 수정한다.' 'DEVELOPMENT-WORKFLOW README TASK-scope/actual-need rule missing'

  check_context_fixed "$WORKFLOW" LINE '## 5. 문서와 테스트 영향' '' '' '' '' '' '- 실행 방법, 환경, 외부 사용 방식 변경은 대표적인 README 수정 필요 사례이며 유일한 조건이 아니다.' 'DEVELOPMENT-WORKFLOW README examples-not-exclusive rule missing'

  check_context_fixed "$WORKFLOW" LINE '## 5. 문서와 테스트 영향' '' '' '' '' '' '- README 영향이 없으면 불필요하게 수정하지 않는다.' 'DEVELOPMENT-WORKFLOW README no-unnecessary-change rule missing'

  if grep -Fq --     "- README는 실행·환경·외부 사용 방식이 바뀔 때만 수정한다."     "$WORKFLOW" 2>/dev/null; then
    add_failure "DEVELOPMENT-WORKFLOW README condition narrowed to execution/environment/external-use only"
  fi

  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 11. merge' '' '' '' '**실행**' '- merge 전 learning gate가 없으면 정상 merge 흐름을 진행하고, 일반 TASK learning은 merge 후 수행할 수 있다.' 'DEVELOPMENT-WORKFLOW no-premerge-learning-gate transition missing'
  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 11. merge' '' '' '' '**실행**' '- merge 전 learning gate가 있고 아직 통과하지 않았다면 단계 13 Learning을 선행 수행한다.' 'DEVELOPMENT-WORKFLOW pre-merge Learning transition missing'
  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 11. merge' '' '' '' '**실행**' '- 단계 13 Learning이 PASS하면 단계 11 merge gate로 복귀하며, merge는 사용자 승인 후에만 수행한다.' 'DEVELOPMENT-WORKFLOW Learning-to-merge return transition missing'
  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 11. merge' '' '' '' '**실행**' '- 이 조건부 transition은 사용자 승인 없는 자동 단계 진행을 의미하지 않는다.' 'DEVELOPMENT-WORKFLOW no-auto-transition rule missing'
  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 11. merge' '' '' '' '**실행**' '- 일반 TASK learning은 merge 후 수행할 수 있지만 TASK 최종 완료 전, 다음 핵심 구현 TASK 시작 전에는 통과해야 한다.' 'DEVELOPMENT-WORKFLOW general-TASK learning deadline missing'

  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 12. 조건부 회고 / 장애 기록' '' '' '' '**중단 조건**' '- 해당 기록이 필요하지 않다는 판단 자체는 TASK 진행을 차단하지 않는다.' 'DEVELOPMENT-WORKFLOW conditional retrospective non-blocking rule missing'

  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 14. TASK 최종 완료' '' '' '' '**중단 조건**' '- DoD 미충족' 'DEVELOPMENT-WORKFLOW final DoD stop condition missing'
  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 14. TASK 최종 완료' '' '' '' '**중단 조건**' '- 필요한 review 또는 검증 미완료' 'DEVELOPMENT-WORKFLOW final review/validation stop condition missing'
  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 14. TASK 최종 완료' '' '' '' '**중단 조건**' '- 해당 TASK의 learning 의무 미완료' 'DEVELOPMENT-WORKFLOW final learning stop condition missing'
  check_context_fixed "$WORKFLOW" LINE '## 3. 단계별 lifecycle' '### 14. TASK 최종 완료' '' '' '' '**중단 조건**' '- 필요한 사용자 최종 결정 없음' 'DEVELOPMENT-WORKFLOW final user-decision stop condition missing'

  validate_workflow_lifecycle_schema "$WORKFLOW"

fi

# J. Learning Contract deterministic minimum semantics.
# ---------------------------------------------------------------------------

if [ -f "$LEARNING" ] && [ ! -L "$LEARNING" ]; then
  check_context_fixed "$LEARNING" LINE '## 9. 면접 대비 단계' '### 9.3 압박·꼬리 질문' '' '' '' '' '질문은 한 번에 하나씩 진행한다.' 'Learning one-question-at-a-time rule missing'
  check_context_fixed "$LEARNING" LINE '## 1. 기본 원칙' '' '' '' '' '' '4. 사용자가 답하기 전에는 모범 답안을 먼저 공개하지 않는다.' 'Learning no-model-answer-before-user rule missing'
  check_context_fixed "$LEARNING" LINE '## 11. 통과 기준' '' '' '' '' '' '- 치명적인 오개념 0개' 'Learning zero-critical-misconception criterion missing'
  check_context_fixed "$LEARNING" LINE '## 11. 통과 기준' '' '' '' '' '' '- 핵심 질문 통과율 80% 이상' 'Learning 80-percent criterion missing'
  check_context_fixed "$LEARNING" LINE '## 2. 적용 시점' '' '' '' '' '' '대표·고위험 여부와 learning deadline은 TASK 계획 단계에서 승인한다.' 'Learning planning-time risk/deadline approval missing'
  check_context_fixed "$LEARNING" LINE '## 2. 적용 시점' '' '' '' '' '' '- 계획에서 merge 전 learning gate가 승인된 TASK는 merge 전에 학습 검증을 통과해야 한다.' 'Learning conditional pre-merge gate missing'
  check_context_fixed "$LEARNING" LINE '## 2. 적용 시점' '' '' '' '' '' '- 일반 TASK는 merge 후 학습을 진행할 수 있다.' 'Learning general-TASK post-merge option missing'
  check_context_fixed "$LEARNING" LINE '## 2. 적용 시점' '' '' '' '' '' '- 모든 TASK는 TASK 최종 완료 전, 그리고 다음 핵심 구현 TASK 시작 전까지 필요한 학습 검증을 완료한다.' 'Learning final-completion deadline missing'
  check_context_fixed "$LEARNING" LINE '## 10. 최종 산출물' '' '' '' '' '' '- pass 여부' 'Learning minimum pass tracking missing'
  check_context_fixed "$LEARNING" LINE '## 10. 최종 산출물' '' '' '' '' '' '- 핵심 오해' 'Learning misconception tracking missing'
  check_context_fixed "$LEARNING" LINE '## 10. 최종 산출물' '' '' '' '' '' '- 사용자 기여 / AI 기여 구분' 'Learning user/AI contribution tracking missing'
  check_context_fixed "$LEARNING" LINE '## 10. 최종 산출물' '' '' '' '' '' '대표·고위험 TASK는 다음 저장소 산출물을 필수로 작성한다.' 'Learning representative/high-risk notes requirement missing'
  check_context_fenced_payload "$LEARNING" '## 10. 최종 산출물' '' '' '' '' '' '```text' docs/learning/TASK-XXX/INTERVIEW-NOTES.md 'Learning INTERVIEW-NOTES path missing'
  check_context_fixed "$LEARNING" LINE '## 10. 최종 산출물' '' '' '' '' '' '일반 TASK의 `INTERVIEW-NOTES.md` 저장소 작성은 조건부이며, 필요 여부는 TASK 계획과 학습 중요도에 따라 판단한다.' 'Learning general-TASK notes conditional rule missing'

  if grep -Fq "모든 TASK는 merge 전에 학습 검증을 통과해야 한다." "$LEARNING" 2>/dev/null; then
    add_failure "Learning incorrectly makes pre-merge learning mandatory for all TASKs"
  fi
  if grep -Fq -- '일반 TASK도 `INTERVIEW-NOTES.md` 저장소 작성을 필수' "$LEARNING" 2>/dev/null; then
    add_failure "Learning incorrectly makes INTERVIEW-NOTES mandatory for general TASKs"
  fi
fi

# ---------------------------------------------------------------------------
# K. GPT review contract.
# ---------------------------------------------------------------------------

if [ -f "$GPT" ] && [ ! -L "$GPT" ]; then
  check_context_fixed "$GPT" LINE '## 1. 역할과 권한' '' '' '' '' '' 'GPT는 이 프로젝트의 독립 검토자다.' 'GPT independent reviewer role missing'
  check_context_fixed "$GPT" LINE '## 1. 역할과 권한' '' '' '' '' '' '- 구현' 'GPT no-direct-implementation rule missing'
  check_context_fixed "$GPT" LINE '## 1. 역할과 권한' '' '' '' '' '' '- 저장소 파일 수정' 'GPT no-repository-file-modification rule missing'
  check_context_fixed "$GPT" LINE '## 1. 역할과 권한' '' '' '' '' '' '- Git 작업' 'GPT no-Git-work rule missing'
  check_context_fixed "$GPT" LINE '## 1. 역할과 권한' '' '' '' '' '' '- 다음 단계 자동 진행' 'GPT no-auto-progression rule missing'
  check_context_fixed "$GPT" LINE '## 2. 입력 자료 원칙' '' '' '' '' '' '검토 입력을 핵심 자료와 보조 자료로 구분한다.' 'GPT core/aux input distinction missing'
  check_context_fixed "$GPT" LINE '## 2. 입력 자료 원칙' '' '' '' '' '' '- 핵심 자료가 하나라도 누락되면 해당 review를 시작하지 않는다.' 'GPT core-input stop rule missing'
  check_context_fixed "$GPT" LINE '## 2. 입력 자료 원칙' '' '' '' '' '' '- 보조 자료가 누락되면 가능한 범위만 검토하고 `Review limitations`에 한계를 기록한다.' 'GPT auxiliary-input limitation rule missing'

  GPT_STAGE_SECTION='## 3. Review stage별 핵심 입력'

  check_visible_heading_once \
    "$GPT" \
    "$GPT_STAGE_SECTION" \
    "GPT review stage section heading count must be exactly 1"

  check_gpt_stage_heading() {
    stage="$1"

    if stage_heading_count="$(
      markdown_section_lines \
        "$GPT" \
        "$GPT_STAGE_SECTION" |
        awk \
          -v expected="### $stage" '
          $0 == expected {
            count++
          }

          END {
            print count + 0
          }
        '
    )"
    then
      if [ "$stage_heading_count" -ne 1 ]; then
        add_failure \
          "GPT review stage structural contract failure: $stage heading count must be exactly 1 in section 3"
      fi
    else
      add_failure \
        "GPT review stage Markdown section parser failed: $stage"
    fi
  }

  check_gpt_stage_input() {
    stage="$1"
    expected_line="$2"
    label="$3"

    if ! markdown_section_lines \
      "$GPT" \
      "$GPT_STAGE_SECTION" |
      awk \
        -v heading="### $stage" \
        -v stage="$stage" \
        -v expected="$expected_line" '
        BEGIN {
          heading_count=0
          inside_stage=0
          direct_region=0
          marker_count=0
          core_state=0
          core_started=0
          fixed_first_seen=0
          found=0
        }

        $0 == heading {
          heading_count++

          if (heading_count == 1) {
            inside_stage=1
            direct_region=1
          } else {
            inside_stage=0
            direct_region=0
          }

          marker_count=0
          core_state=0
          core_started=0
          fixed_first_seen=0
          next
        }

        inside_stage && /^### / {
          inside_stage=0
          direct_region=0
          next
        }

        !inside_stage {
          next
        }

        stage == "Fixed packet" {
          if ($0 ~ /^[[:space:]]*$/) {
            next
          }

          if (!fixed_first_seen) {
            fixed_first_seen=1

            if ($0 == expected) {
              found=1
            }
          }

          next
        }

        direct_region &&
        $0 ~ /^####+ / {
          direct_region=0
          core_state=2
          next
        }

        !direct_region {
          next
        }

        $0 == "핵심 입력:" {
          marker_count++

          if (marker_count == 1) {
            core_state=1
            core_started=0
          } else {
            core_state=2
          }

          next
        }

        core_state == 1 {
          if (!core_started &&
              $0 ~ /^[[:space:]]*$/) {
            next
          }

          if ($0 ~ /^- /) {
            core_started=1

            if ($0 == expected) {
              found=1
            }

            next
          }

          core_state=2
          next
        }

        END {
          if (stage == "Fixed packet") {
            if (heading_count == 1 &&
                fixed_first_seen == 1 &&
                found == 1) {
              exit 0
            }

            exit 1
          }

          if (heading_count == 1 &&
              marker_count == 1 &&
              core_started == 1 &&
              found == 1) {
            exit 0
          }

          exit 1
        }
      '
    then
      add_failure "$label"
    fi
  }

  check_gpt_stage_list() {
    stage="$1"
    label="$2"
    expected_count="$3"
    e1="${4:-}"
    e2="${5:-}"
    e3="${6:-}"
    e4="${7:-}"
    e5="${8:-}"

    if ! markdown_section_lines \
      "$GPT" \
      "$GPT_STAGE_SECTION" |
      awk \
        -v heading="### $stage" \
        -v expected_count="$expected_count" \
        -v e1="$e1" \
        -v e2="$e2" \
        -v e3="$e3" \
        -v e4="$e4" \
        -v e5="$e5" '
        function expected_for(n) {
          if (n == 1) {
            return e1
          }

          if (n == 2) {
            return e2
          }

          if (n == 3) {
            return e3
          }

          if (n == 4) {
            return e4
          }

          if (n == 5) {
            return e5
          }

          return ""
        }

        BEGIN {
          heading_count=0
          inside_stage=0
          direct_region=0
          marker_count=0
          after_marker=0
          list_started=0
          list_closed=0
          bullet_count=0
          bad=0

          if (expected_count !~ /^[0-9]+$/ ||
              expected_count < 1 ||
              expected_count > 5) {
            bad=1
          }
        }

        $0 == heading {
          heading_count++

          if (heading_count == 1) {
            inside_stage=1
            direct_region=1
          } else {
            inside_stage=0
            direct_region=0
            bad=1
          }

          next
        }

        inside_stage && /^### / {
          inside_stage=0
          direct_region=0
          next
        }

        !inside_stage {
          next
        }

        direct_region &&
        $0 ~ /^####+ / {
          direct_region=0
          after_marker=0
          list_closed=1
          next
        }

        !direct_region {
          next
        }

        $0 == "핵심 입력:" {
          marker_count++

          if (marker_count == 1 &&
              !list_started &&
              !list_closed) {
            after_marker=1
          } else {
            bad=1
          }

          next
        }

        after_marker {
          if (!list_started &&
              $0 ~ /^[[:space:]]*$/) {
            next
          }

          if ($0 ~ /^- /) {
            if (list_closed) {
              bad=1
              next
            }

            bullet_count++

            if (bullet_count > expected_count) {
              bad=1
              next
            }

            expected_line=expected_for(bullet_count)

            if ($0 != expected_line) {
              bad=1
            }

            list_started=1
            next
          }

          if (list_started) {
            list_closed=1
            after_marker=0
            next
          }

          bad=1
          after_marker=0
          next
        }

        END {
          if (heading_count != 1 ||
              marker_count != 1 ||
              bullet_count != expected_count ||
              bad) {
            exit 1
          }
        }
      '
    then
      add_failure "$label"
    fi
  }

  for stage in "Plan / Issue" "Implementation" "Git / PR" "Fixed packet"
  do
    check_gpt_stage_heading "$stage"
  done

  check_gpt_stage_list \
    "Plan / Issue" \
    "GPT Plan / Issue core input list mismatch" \
    5 \
    "- TASK 목적" \
    "- Scope" \
    "- Definition of Done" \
    "- Test plan" \
    "- 승인된 결정"

  check_gpt_stage_list \
    "Implementation" \
    "GPT Implementation core input list mismatch" \
    4 \
    "- 승인된 Issue와 구현 계획" \
    "- 실제 변경 code 또는 diff" \
    "- 판단에 필요한 관련 code context" \
    "- test 결과 또는 미실행 사유와 위험"

  check_gpt_stage_list \
    "Git / PR" \
    "GPT Git / PR core input list mismatch" \
    4 \
    "- final diff" \
    "- changed files" \
    "- 전체 검증 결과" \
    "- unresolved Finding과 사용자 결정"

  check_gpt_stage_input \
    "Fixed packet" \
    "핵심 입력은 사용자가 해당 검토에 대해 정의한 전체 고정 패킷이다." \
    "GPT Fixed packet core input missing: 전체 고정 패킷"

  GPT_OUTPUT_SECTION='## 7. 출력 원칙'

  check_visible_heading_once \
    "$GPT" \
    "$GPT_OUTPUT_SECTION" \
    "GPT output-principles section heading count must be exactly 1"

  if ! markdown_section_lines \
    "$GPT" \
    "$GPT_OUTPUT_SECTION" |
    awk '
      BEGIN {
        direct_region=1
        item_count=0
        bad=0
      }

      /^### / {
        direct_region=0
        next
      }

      !direct_region {
        next
      }

      /^[[:space:]]*$/ {
        if (item_count == 0) {
          next
        }
      }

      /^- / {
        item_count++

        if (item_count == 1 &&
            $0 != "- 차단 문제를 가장 먼저 표시한다.") {
          bad=1
        }

        if (item_count == 2 &&
            $0 != "- 중요도가 높은 Finding 최대 3개를 앞부분에 요약한다.") {
          bad=1
        }

        if (item_count == 3 &&
            $0 != "- 확인된 전체 Finding을 생략하지 않는다.") {
          bad=1
        }

        next
      }

      item_count < 3 &&
      $0 !~ /^[[:space:]]*$/ {
        bad=1
      }

      END {
        if (item_count < 3 ||
            bad) {
          exit 1
        }
      }
    '
  then
    add_failure \
      "GPT output principles authoritative ordered block mismatch"
  fi


  gpt_schema_severity_ready=1

  if markdown_visible_snapshot_strict \
    "$GPT" \
    "$GPT_VISIBLE_FILE"
  then
    :
  else
    add_failure \
      "GPT schema/severity Markdown parser failed"
    gpt_schema_severity_ready=0
  fi

  if [ "$gpt_schema_severity_ready" -eq 1 ]; then
    gpt_schema_rows=''

    if gpt_schema_rows="$(
      gpt_finding_schema_rows \
        "$GPT_VISIBLE_FILE"
    )"
    then
      :
    else
      add_failure \
        "GPT common Finding schema authoritative table mismatch"
      gpt_schema_rows=''
    fi

    for field in \
      "Severity" \
      "Category" \
      "Location" \
      "Condition" \
      "Risk" \
      "Evidence" \
      "Required action" \
      "Evidence sufficiency" \
      "TASK scope"
    do
      field_count="$(
        printf '%s\n' "$gpt_schema_rows" |
          awk \
            -F '|' \
            -v expected="$field" '
            {
              value=$2
              sub(/^[[:space:]]+/, "", value)
              sub(/[[:space:]]+$/, "", value)

              if (value == expected) {
                count++
              }
            }

            END {
              print count + 0
            }
          '
      )"

      if [ "$field_count" -ne 1 ]; then
        add_failure \
          "GPT common Finding field mismatch: $field"
      fi
    done

    gpt_schema_field_order="$(
      printf '%s\n' "$gpt_schema_rows" |
        awk \
          -F '|' '
          {
            value=$2
            sub(/^[[:space:]]+/, "", value)
            sub(/[[:space:]]+$/, "", value)

            if (value != "") {
              print value
            }
          }
        '
    )"

    gpt_expected_field_order='Severity
Category
Location
Condition
Risk
Evidence
Required action
Evidence sufficiency
TASK scope'

    if [ -n "$gpt_schema_rows" ] &&
       [ "$gpt_schema_field_order" != "$gpt_expected_field_order" ]; then
      add_failure \
        "GPT common Finding schema authoritative table mismatch"
    fi

    if severity_lines="$(
      gpt_severity_direct_headings \
        "$GPT_VISIBLE_FILE"
    )"
    then
      severity_headings="$(
        printf '%s\n' "$severity_lines" |
          awk '{ printf "%s ", $0 }'
      )"

      if [ "$severity_headings" != "Critical High Medium Low " ]; then
        add_failure \
          "GPT Severity headings are not exactly Critical/High/Medium/Low"
      fi
    else
      add_failure \
        "GPT Severity headings are not exactly Critical/High/Medium/Low"
    fi
  fi

  check_context_fixed "$GPT" LINE '## 7. 출력 원칙' '' '' '' '' '' '- 중요도가 높은 Finding 최대 3개를 앞부분에 요약한다.' 'GPT top-three summary rule missing'
  check_context_fixed "$GPT" LINE '## 7. 출력 원칙' '' '' '' '' '' '- 확인된 전체 Finding을 생략하지 않는다.' 'GPT complete-Finding preservation rule missing'
  check_context_fixed "$GPT" LINE '## 8. Critical 처리' '' '' '' '' '' '2. 영향받는 Phase를 중단한다.' 'GPT Critical phase-stop rule missing'
  check_context_fixed "$GPT" LINE '## 8. Critical 처리' '' '' '' '' '' '5. 사용자의 승인 전 자동으로 해결하거나 무시하지 않는다.' 'GPT Critical user-approval rule missing'
  check_context_fixed "$GPT" LINE '## 8. Critical 처리' '' '' '' '' '' '6. 승인된 수정 후 재검증한다.' 'GPT Critical revalidation rule missing'
fi

# ---------------------------------------------------------------------------
# L. Risk Reviewer Agent.
# ---------------------------------------------------------------------------

if [ -f "$AGENT" ] && [ ! -L "$AGENT" ]; then
  tools_line="$(awk '
    NR==1 && $0=="---" { front=1; next }
    front && $0=="---" { exit }
    front && /^tools:/ { print; exit }
  ' "$AGENT")"
  if [ "$tools_line" != "tools: Read, Grep, Glob" ]; then
    add_failure "Agent frontmatter tools are not exactly Read, Grep, Glob"
  fi

  check_context_fixed "$AGENT" LINE '## 역할' '' '' '' '' '' '이 Agent는 읽기 전용 검토자다.' 'Agent read-only rule missing'
  check_context_fixed "$AGENT" LINE '## 역할' '' '' '' '' '' '- 코드를 작성하거나 수정하지 않는다.' 'Agent code-modification prohibition missing'
  check_context_fixed "$AGENT" LINE '## 역할' '' '' '' '' '' '- 저장소 파일을 변경하지 않는다.' 'Agent file-modification prohibition missing'
  check_context_fixed "$AGENT" LINE '## 역할' '' '' '' '' '' '- 명령을 실행하지 않는다.' 'Agent command-execution prohibition missing'
  check_context_fixed "$AGENT" LINE '## 역할' '' '' '' '' '' '- 테스트를 실행하지 않는다.' 'Agent test-execution prohibition missing'
  check_context_fixed "$AGENT" LINE '## 공통 Finding 계약' '' '' '' '' '' '공통 Finding schema와 Severity의 단일 기준은 `docs/process/GPT-REVIEW-CONTRACT.md`다.' 'Agent common Finding SSOT reference missing'
  check_context_fixed "$AGENT" H3 '## Agent 전용 출력' '### Insufficient evidence' '' '' '' '' '### Insufficient evidence' 'Agent insufficient-evidence section missing'
  check_context_fixed "$AGENT" H3 '## Agent 전용 출력' '### Review limitations' '' '' '' '' '### Review limitations' 'Agent review-limitations section missing'
  check_context_fixed "$AGENT" H3 '## Agent 전용 출력' '### Finding count summary' '' '' '' '' '### Finding count summary' 'Agent finding-count-summary section missing'

  if grep -Eq '^### (Review stage|Final recommendation|User decision required)$' "$AGENT" 2>/dev/null; then
    add_failure "Agent contains GPT-only output section"
  fi
fi

# ---------------------------------------------------------------------------
# Final result. Sensitive scan has already completed; no secret values are emitted.
# ---------------------------------------------------------------------------

if [ -s "$ERROR_FILE" ]; then
  printf '%s\n' "PROCESS_DOCS_VALIDATION=FAIL"
  cat "$ERROR_FILE"
  exit 1
fi

printf '%s\n' "PROCESS_DOCS_VALIDATION=PASS"
exit 0
