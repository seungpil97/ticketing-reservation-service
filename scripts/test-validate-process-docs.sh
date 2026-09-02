#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VALIDATOR="$SCRIPT_DIR/validate-process-docs.sh"
SOURCE_ROOT="${PROCESS_DOCS_FIXTURE_SOURCE:-$(cd "$SCRIPT_DIR/.." && pwd)}"

SELF_ERRORS=0
WORK_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/process-docs-self-test.XXXXXX")"

cleanup() {
  rm -rf "$WORK_ROOT"
}
trap cleanup EXIT HUP INT TERM

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

authoritative_reference_files() {
  cat <<'EOF'
docs/db/README.md
EOF
}

authoritative_reference_dirs() {
  cat <<'EOF'
docs/architecture
docs/api
EOF
}

record_pass() {
  printf '%s\n' "PASS: $1"
}

record_fail() {
  printf '%s\n' "FAIL: $1"
  SELF_ERRORS=$((SELF_ERRORS + 1))
}

copy_source_tree() {
  dest="$1"

  while IFS= read -r rel
  do
    src="$SOURCE_ROOT/$rel"

    if [ ! -f "$src" ] || [ -L "$src" ]; then
      return 1
    fi

    mkdir -p "$dest/$(dirname "$rel")"
    cp "$src" "$dest/$rel"
  done <<EOF
$(required_files)
EOF

  while IFS= read -r rel
  do
    src="$SOURCE_ROOT/$rel"

    if [ ! -f "$src" ] || [ -L "$src" ]; then
      return 1
    fi

    mkdir -p "$dest/$(dirname "$rel")"
    cp "$src" "$dest/$rel"
  done <<EOF
$(authoritative_reference_files)
EOF

  while IFS= read -r rel
  do
    src="$SOURCE_ROOT/$rel"

    if [ ! -d "$src" ] || [ -L "$src" ]; then
      return 1
    fi

    mkdir -p "$dest/$rel"
  done <<EOF
$(authoritative_reference_dirs)
EOF

  return 0
}

make_fixture() {
  name="$1"
  fixture="$WORK_ROOT/$name"
  mkdir -p "$fixture"
  if ! copy_source_tree "$fixture"; then
    return 1
  fi
  printf '%s\n' "$fixture"
}

run_validator() {
  fixture="$1"
  output="$2"
  tracked="${3:-0}"

  set +e
  PROCESS_DOCS_ROOT="$fixture" \
  PROCESS_DOCS_REQUIRE_GIT_TRACKED="$tracked" \
  /bin/bash "$VALIDATOR" >"$output" 2>&1
  rc=$?
  set -e
  return "$rc"
}

expect_pass() {
  id="$1"
  description="$2"
  fixture="$3"
  tracked="${4:-0}"
  output="$WORK_ROOT/$id.out"

  if run_validator "$fixture" "$output" "$tracked"; then
    if grep -Fq "PROCESS_DOCS_VALIDATION=PASS" "$output"; then
      record_pass "$id $description"
    else
      record_fail "$id $description (PASS marker missing)"
    fi
  else
    record_fail "$id $description (validator rejected valid fixture)"
  fi
}

expect_fail() {
  id="$1"
  description="$2"
  fixture="$3"
  tracked="${4:-0}"
  required_message="${5:-}"
  output="$WORK_ROOT/$id.out"

  if run_validator "$fixture" "$output" "$tracked"; then
    record_fail "$id $description (validator incorrectly accepted fixture)"
    return
  fi

  if ! grep -Fq "PROCESS_DOCS_VALIDATION=FAIL" "$output"; then
    record_fail "$id $description (validator failed without FAIL marker)"
    return
  fi

  if [ -n "$required_message" ] && ! grep -Fq "$required_message" "$output"; then
    record_fail "$id $description (required failure category missing)"
    return
  fi

  record_pass "$id $description"
}

expect_fail_redacted() {
  id="$1"
  description="$2"
  fixture="$3"
  sensitive_value="$4"
  tracked="${5:-0}"
  required_message="${6:-}"
  output="$WORK_ROOT/$id.out"

  if run_validator "$fixture" "$output" "$tracked"; then
    record_fail "$id $description (validator incorrectly accepted fixture)"
    return
  fi

  if ! grep -Fq "PROCESS_DOCS_VALIDATION=FAIL" "$output"; then
    record_fail "$id $description (validator failed without FAIL marker)"
    return
  fi

  if [ -n "$required_message" ] && ! grep -Fq "$required_message" "$output"; then
    record_fail "$id $description (required failure category missing)"
    return
  fi

  if grep -Fq "$sensitive_value" "$output"; then
    record_fail "$id $description (sensitive value leaked)"
    return
  fi

  record_pass "$id $description"
}

rewrite_with_awk() {
  file="$1"
  program="$2"

  dir="${file%/*}"
  if [ "$dir" = "$file" ]; then
    dir='.'
  fi

  tmp="$(mktemp "$dir/.process-docs-rewrite.XXXXXX")" ||
    return 1

  if ! awk "$program" "$file" > "$tmp"; then
    rm -f "$tmp"
    return 1
  fi

  if ! mv "$tmp" "$file"; then
    rm -f "$tmp"
    return 1
  fi

  return 0
}

if [ ! -f "$VALIDATOR" ] || [ -L "$VALIDATOR" ]; then
  printf '%s\n' "VALIDATOR_SELF_TEST=FAIL"
  printf '%s\n' "FAIL: validator candidate missing or not regular"
  exit 1
fi

# Verify the source fixture inputs before emitting individual case results.
while IFS= read -r rel
do
  if [ ! -f "$SOURCE_ROOT/$rel" ] || [ -L "$SOURCE_ROOT/$rel" ]; then
    printf '%s\n' "VALIDATOR_SELF_TEST=FAIL"
    printf '%s\n' "FAIL: fixture source missing or not regular: $rel"
    exit 1
  fi
done <<EOF
$(required_files)
EOF

while IFS= read -r rel
do
  if [ ! -f "$SOURCE_ROOT/$rel" ] || [ -L "$SOURCE_ROOT/$rel" ]; then
    printf '%s\n' "VALIDATOR_SELF_TEST=FAIL"
    printf '%s\n'       "FAIL: fixture authoritative file missing or not regular: $rel"
    exit 1
  fi
done <<EOF
$(authoritative_reference_files)
EOF

while IFS= read -r rel
do
  if [ ! -d "$SOURCE_ROOT/$rel" ] || [ -L "$SOURCE_ROOT/$rel" ]; then
    printf '%s\n' "VALIDATOR_SELF_TEST=FAIL"
    printf '%s\n'       "FAIL: fixture authoritative directory missing or invalid: $rel"
    exit 1
  fi
done <<EOF
$(authoritative_reference_dirs)
EOF

z18_append_unclosed_markdown_fence() {
  file="$1"
  opener="$2"

  [ -f "$file" ] &&
    [ ! -L "$file" ] ||
    return 1

  before_bytes="$(
    wc -c < "$file" |
      awk '{print $1+0}'
  )" ||
    return 1

  before_opener_count="$(
    awk -v expected="$opener" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    ' "$file"
  )" ||
    return 1

  expected_tail_file="$(
    mktemp "$WORK_ROOT/z18-unclosed-expected.XXXXXX"
  )" ||
    return 1

  actual_tail_file="$(
    mktemp "$WORK_ROOT/z18-unclosed-actual.XXXXXX"
  )" || {
    rm -f "$expected_tail_file"
    return 1
  }

  if ! printf '\n%s\n' "$opener" > "$expected_tail_file"; then
    rm -f "$expected_tail_file" "$actual_tail_file"
    return 1
  fi

  if ! printf '\n%s\n' "$opener" >> "$file"; then
    rm -f "$expected_tail_file" "$actual_tail_file"
    return 1
  fi

  after_bytes="$(
    wc -c < "$file" |
      awk '{print $1+0}'
  )" ||
    return 1

  after_opener_count="$(
    awk -v expected="$opener" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    ' "$file"
  )" ||
    return 1

  [ "$after_bytes" -gt "$before_bytes" ] ||
    return 1

  [ "$after_opener_count" -eq $((before_opener_count + 1)) ] ||
    return 1

  last_nonempty="$(
    awk 'NF{last=$0} END{print last}' "$file"
  )" ||
    return 1

  [ "$last_nonempty" = "$opener" ] ||
    return 1

  appended_bytes=$((after_bytes - before_bytes))

  if ! tail -c "$appended_bytes" "$file" > "$actual_tail_file"; then
    rm -f "$expected_tail_file" "$actual_tail_file"
    return 1
  fi

  if ! cmp -s "$expected_tail_file" "$actual_tail_file"; then
    rm -f "$expected_tail_file" "$actual_tail_file"
    return 1
  fi

  rm -f "$expected_tail_file" "$actual_tail_file"
  return 0
}

z18_append_closed_markdown_fence() {
  file="$1"
  opener="$2"
  payload="$3"
  closer="$4"

  [ -f "$file" ] &&
    [ ! -L "$file" ] ||
    return 1

  before_bytes="$(
    wc -c < "$file" |
      awk '{print $1+0}'
  )" ||
    return 1

  before_opener_count="$(
    awk -v expected="$opener" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    ' "$file"
  )" ||
    return 1

  before_payload_count="$(
    awk -v expected="$payload" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    ' "$file"
  )" ||
    return 1

  before_closer_count="$(
    awk -v expected="$closer" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    ' "$file"
  )" ||
    return 1

  expected_tail_file="$(
    mktemp "$WORK_ROOT/z18-closed-expected.XXXXXX"
  )" ||
    return 1

  actual_tail_file="$(
    mktemp "$WORK_ROOT/z18-closed-actual.XXXXXX"
  )" || {
    rm -f "$expected_tail_file"
    return 1
  }

  if ! printf '\n%s\n%s\n%s\n' \
    "$opener" \
    "$payload" \
    "$closer" \
    > "$expected_tail_file"
  then
    rm -f "$expected_tail_file" "$actual_tail_file"
    return 1
  fi

  if ! printf '\n%s\n%s\n%s\n' \
    "$opener" \
    "$payload" \
    "$closer" \
    >> "$file"
  then
    rm -f "$expected_tail_file" "$actual_tail_file"
    return 1
  fi

  after_bytes="$(
    wc -c < "$file" |
      awk '{print $1+0}'
  )" ||
    return 1

  after_opener_count="$(
    awk -v expected="$opener" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    ' "$file"
  )" ||
    return 1

  after_payload_count="$(
    awk -v expected="$payload" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    ' "$file"
  )" ||
    return 1

  after_closer_count="$(
    awk -v expected="$closer" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    ' "$file"
  )" ||
    return 1

  [ "$after_bytes" -gt "$before_bytes" ] ||
    return 1

  [ "$after_opener_count" -eq $((before_opener_count + 1)) ] ||
    return 1

  [ "$after_payload_count" -eq $((before_payload_count + 1)) ] ||
    return 1

  [ "$after_closer_count" -eq $((before_closer_count + 1)) ] ||
    return 1

  last_nonempty="$(
    awk 'NF{last=$0} END{print last}' "$file"
  )" ||
    return 1

  [ "$last_nonempty" = "$closer" ] ||
    return 1

  appended_bytes=$((after_bytes - before_bytes))

  if ! tail -c "$appended_bytes" "$file" > "$actual_tail_file"; then
    rm -f "$expected_tail_file" "$actual_tail_file"
    return 1
  fi

  if ! cmp -s "$expected_tail_file" "$actual_tail_file"; then
    rm -f "$expected_tail_file" "$actual_tail_file"
    return 1
  fi

  tail_lines="$(
    tail -n 3 "$file"
  )" || {
    rm -f "$expected_tail_file" "$actual_tail_file"
    return 1
  }

  expected_lines="$(
    printf '%s\n%s\n%s\n' \
      "$opener" \
      "$payload" \
      "$closer"
  )" || {
    rm -f "$expected_tail_file" "$actual_tail_file"
    return 1
  }

  if [ "$tail_lines" != "$expected_lines" ]; then
    rm -f "$expected_tail_file" "$actual_tail_file"
    return 1
  fi

  rm -f "$expected_tail_file" "$actual_tail_file"
  return 0
}

z25_mutate_structural_fixture() {
  file="$1"
  mode="$2"

  python3 - \
    "$file" \
    "$mode" <<'PY_Z25_FIXTURE'
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit(1)

path = Path(sys.argv[1])
mode = sys.argv[2]
text = path.read_text(encoding="utf-8")


def replace_once(value, old, new):
    count = value.count(old)
    if count != 1:
        raise SystemExit(1)
    return value.replace(old, new, 1)


def wrap_section(value, start_heading, next_heading, opener, closer):
    start_marker = start_heading + "\n"
    next_marker = next_heading + "\n"

    if value.count(start_marker) != 1:
        raise SystemExit(1)

    start = value.index(start_marker)
    end = value.find(next_marker, start + len(start_marker))

    if end < 0:
        raise SystemExit(1)

    segment = value[start:end]
    wrapped = (
        opener
        + "\n"
        + segment.rstrip("\n")
        + "\n"
        + closer
        + "\n\n"
    )
    return value[:start] + wrapped + value[end:]


ROADMAP_ANCHOR = (
    "사용자 승인 없이 다음 TASK를 자동으로 시작하지 않는다."
)
ROADMAP_PAYLOAD = """TASK-032
→ TASK-033
→ TASK-033-1
→ TASK-028
→ TASK-043"""
ROADMAP_CANONICAL = (
    ROADMAP_ANCHOR
    + "\n\n```text\n"
    + ROADMAP_PAYLOAD
    + "\n```\n"
)

EVIDENCE_ROW = (
    "| Evidence sufficiency | "
    "근거 충분 / 불충분과 부족한 근거 |"
)

SCHEMA_TABLE = """| 필드 | 의미 |
| --- | --- |
| Severity | `Critical / High / Medium / Low` |
| Category | 위험 또는 결함 분류 |
| Location | 저장소 상대 경로, 문서 절 또는 패킷 위치 |
| Condition | 문제가 발생하는 조건 |
| Risk | 실제 영향 |
| Evidence | 직접 근거 |
| Required action | 진행 전에 필요한 조치 |
| Evidence sufficiency | 근거 충분 / 불충분과 부족한 근거 |
| TASK scope | 현재 TASK 범위 안 / 밖 |"""

BRANCH_CANONICAL = """형식:

```text
{prefix}/TASK-XXX-english-kebab-case
```"""

if mode == "PROJECT_BRANCH_INDENTED_BACKTICK":
    text = wrap_section(
        text,
        "### Branch",
        "### Commit / PR",
        " ````text",
        " ````",
    )

elif mode == "PROJECT_PREFIX_INDENTED_BACKTICK":
    text = wrap_section(
        text,
        "### 허용 prefix",
        "### Hotfix",
        "   ````text",
        "   ````",
    )

elif mode == "PROJECT_COMMIT_INDENTED_TILDE":
    text = wrap_section(
        text,
        "### Commit / PR",
        "### 허용 prefix",
        "  ~~~~text",
        "  ~~~~",
    )

elif mode == "PROJECT_POSITIVE_BALANCED_FENCE":
    text += (
        "\n  ~~~~text\n"
        "Z25 unrelated balanced indented fence\n"
        "  ~~~~\n"
    )

elif mode == "PROJECT_BRANCH_DUPLICATE_PAYLOAD":
    replacement = (
        BRANCH_CANONICAL
        + "\n\n```text\n"
        "{prefix}/TASK-XXX-english-kebab-case\n"
        "```"
    )
    text = replace_once(
        text,
        BRANCH_CANONICAL,
        replacement,
    )

elif mode == "ROADMAP_BACKTICK_OUTER_DECOY":
    replacement = (
        ROADMAP_ANCHOR
        + "\n\n````text\n"
        + "```text\n"
        + ROADMAP_PAYLOAD
        + "\n```\n"
        + "````\n\n"
        + "```text\n"
        + ROADMAP_PAYLOAD
        + "\n```\n"
    )
    text = replace_once(text, ROADMAP_CANONICAL, replacement)

elif mode == "ROADMAP_TILDE_OUTER_DECOY":
    replacement = (
        ROADMAP_ANCHOR
        + "\n\n~~~~text\n"
        + "```text\n"
        + ROADMAP_PAYLOAD
        + "\n```\n"
        + "~~~~\n\n"
        + "```text\n"
        + ROADMAP_PAYLOAD
        + "\n```\n"
    )
    text = replace_once(text, ROADMAP_CANONICAL, replacement)

elif mode == "ROADMAP_DUPLICATE_BLOCK":
    replacement = (
        ROADMAP_ANCHOR
        + "\n\n```text\n"
        + ROADMAP_PAYLOAD
        + "\n```\n\n"
        + "```text\n"
        + ROADMAP_PAYLOAD
        + "\n```\n"
    )
    text = replace_once(text, ROADMAP_CANONICAL, replacement)

elif mode == "GPT_SCHEMA_FENCED_DECOY":
    text = replace_once(
        text,
        EVIDENCE_ROW,
        "| Evidence sufficiency invalid | "
        "근거 충분 / 불충분과 부족한 근거 |",
    )
    schema_tail = (
        "| TASK scope | 현재 TASK 범위 안 / 밖 |\n\n"
        "이 schema가 GPT와 프로젝트 Risk Reviewer Agent의 "
        "공통 Finding 단일 기준이다."
    )
    replacement = (
        "| TASK scope | 현재 TASK 범위 안 / 밖 |\n\n"
        "```text\n"
        + EVIDENCE_ROW
        + "\n```\n\n"
        "이 schema가 GPT와 프로젝트 Risk Reviewer Agent의 "
        "공통 Finding 단일 기준이다."
    )
    text = replace_once(text, schema_tail, replacement)

elif mode == "GPT_SCHEMA_OFF_SECTION_DECOY":
    text = replace_once(
        text,
        EVIDENCE_ROW,
        "| Evidence sufficiency invalid | "
        "근거 충분 / 불충분과 부족한 근거 |",
    )
    text += (
        "\n## Z25 off-section schema decoy\n\n"
        "| 필드 | 의미 |\n"
        "| --- | --- |\n"
        + EVIDENCE_ROW
        + "\n"
    )

elif mode == "GPT_SCHEMA_DUPLICATE_TABLE":
    text = replace_once(
        text,
        SCHEMA_TABLE,
        SCHEMA_TABLE + "\n\n" + SCHEMA_TABLE,
    )

elif mode == "GPT_SEVERITY_FENCED_DECOY":
    text = replace_once(text, "### Low\n", "")
    marker = "근거 부족 자체를 Critical로 분류하지 않는다.\n"
    text = replace_once(
        text,
        marker,
        "```text\n### Low\n```\n\n" + marker,
    )

elif mode == "GPT_SEVERITY_OFF_SECTION_DECOY":
    text = replace_once(text, "### Low\n", "")
    marker = "## 6. GPT 전용 출력\n"
    text = replace_once(
        text,
        marker,
        "## Z25 Severity off-section decoy\n\n"
        "### Low\n\n"
        + marker,
    )

elif mode == "GPT_SEVERITY_DUPLICATE_SECTION":
    severity_start_marker = "## 5. Severity\n"
    severity_end_marker = "## 6. GPT 전용 출력\n"

    if text.count(severity_start_marker) != 1:
        raise SystemExit(1)

    start = text.index(severity_start_marker)
    end = text.index(severity_end_marker, start)
    severity_section = text[start:end]
    text = text[:end] + severity_section + text[end:]

elif mode == "GPT_UNMATCHED_FENCE":
    text += (
        "\n```text\n"
        "Z25 unmatched strict-snapshot regression\n"
    )

else:
    raise SystemExit(1)

path.write_text(text, encoding="utf-8")
PY_Z25_FIXTURE
}

# P1. Valid fixture.
fixture="$(make_fixture P1)"
expect_pass "P1" "valid fixture accepted" "$fixture"

# N1. Required file missing.
fixture="$(make_fixture N1)"
rm -f "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md"
expect_fail "N1" "missing required file rejected" "$fixture"

# N2. Required symlink.
fixture="$(make_fixture N2)"
rm -f "$fixture/CLAUDE.md"
ln -s "docs/process/PROJECT-RULES.md" "$fixture/CLAUDE.md"
expect_fail "N2" "symlink required file rejected" "$fixture"

# N3. Matrix row deleted.
fixture="$(make_fixture N3)"
rewrite_with_awk "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
  '{ if ($0 !~ /^\| 684 \|/) print }'
expect_fail "N3" "Matrix missing row rejected" "$fixture"

# N4. Matrix duplicate ID.
fixture="$(make_fixture N4)"
rewrite_with_awk "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
  '{ if ($0 ~ /^\| 684 \|/) sub(/^\| 684 \|/, "| 683 |"); print }'
expect_fail "N4" "Matrix duplicate ID rejected" "$fixture"

# N5. Matrix 12-column break.
fixture="$(make_fixture N5)"
rewrite_with_awk "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
  '{ if ($0 ~ /^\| 1 \|/) sub(/ \|$/, " | EXTRA |"); print }'
expect_fail "N5" "Matrix broken 12-column row rejected" "$fixture"

# N6. Broken related # reference.
fixture="$(make_fixture N6)"
rewrite_with_awk "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
  'BEGIN{done=0} { if (!done && $0 ~ /^\| [0-9]+ \|/ && $0 ~ /관련 #[0-9]+/) { sub(/관련 #[0-9]+/, "관련 #999"); done=1 } print } END{ if(!done) exit 2 }'
expect_fail "N6" "broken related # reference rejected" "$fixture"

# N7. CFL mapping changed.
fixture="$(make_fixture N7)"
rewrite_with_awk "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
  '{ if ($0 ~ /^\| CFL-01 \| 결정 5 \|/) sub(/\| CFL-01 \| 결정 5 \|/, "| CFL-01 | 결정 6 |"); print }'
expect_fail "N7" "incorrect CFL decision mapping rejected" "$fixture"

# N8. TASK-059 incorrectly marked 진행중.
fixture="$(make_fixture N8)"
rewrite_with_awk "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
  '{ if ($0 ~ /^\| TASK-059 \|/ && $0 ~ /\| 완료 \|/) sub(/\| 완료 \|/, "| 진행중 |"); print }'
expect_fail "N8" "Roadmap TASK-059 wrong status rejected" "$fixture"

# N9. TASK-032 prerequisite regressed to TASK-059.
fixture="$(make_fixture N9)"
rewrite_with_awk "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
  '{ gsub(/TASK-060 완료 \+ 사용자 승인/, "TASK-059 완료 + 사용자 승인"); print }'
expect_fail "N9" "Roadmap TASK-032 wrong prerequisite rejected" "$fixture"

# N10. Extra Git prefix added.
fixture="$(make_fixture N10)"
rewrite_with_awk "$fixture/docs/process/PROJECT-RULES.md" \
  '{ print; if ($0 == "- `build`") print "- `perf`" }'
expect_fail "N10" "unapproved Git prefix rejected" "$fixture"

# N11. Controller -> Repository prohibition removed.
fixture="$(make_fixture N11)"
rewrite_with_awk "$fixture/docs/process/PROJECT-RULES.md" \
  '{ if ($0 != "- Controller에서 repository를 직접 호출하지 않는다.") print }'
expect_fail "N11" "missing Controller-to-Repository prohibition rejected" "$fixture"

# N12. Full test command changed.
fixture="$(make_fixture N12)"
rewrite_with_awk "$fixture/docs/process/PROJECT-RULES.md" \
  '{ gsub(/\.\/gradlew clean test --no-daemon --stacktrace -Dspring\.profiles\.active=test/, "./gradlew test --no-daemon --stacktrace -Dspring.profiles.active=test"); print }'
expect_fail "N12" "changed full test command rejected" "$fixture"

# N13. Learning made pre-merge mandatory for all TASKs.
fixture="$(make_fixture N13)"
rewrite_with_awk "$fixture/docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md" \
  '{ if ($0 == "- 계획에서 merge 전 learning gate가 승인된 TASK는 merge 전에 학습 검증을 통과해야 한다.") print "- 모든 TASK는 merge 전에 학습 검증을 통과해야 한다."; else print }'
expect_fail "N13" "universal pre-merge learning gate rejected" "$fixture"

# N14. General TASK INTERVIEW-NOTES made mandatory.
fixture="$(make_fixture N14)"
rewrite_with_awk "$fixture/docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md" \
  '{ if (index($0, "일반 TASK의 `INTERVIEW-NOTES.md` 저장소 작성은 조건부") == 1) print "일반 TASK도 `INTERVIEW-NOTES.md` 저장소 작성을 필수로 한다."; else print }'
expect_fail "N14" "mandatory general-TASK INTERVIEW-NOTES rejected" "$fixture"

# N15. GPT Medium severity removed.
fixture="$(make_fixture N15)"
rewrite_with_awk "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
  '{ if ($0 != "### Medium") print }'
expect_fail "N15" "missing GPT Medium severity rejected" "$fixture"

# N16. Agent Bash tool added.
fixture="$(make_fixture N16)"
rewrite_with_awk "$fixture/.claude/agents/ticketing-risk-reviewer.md" \
  '{ if ($0 == "tools: Read, Grep, Glob") print "tools: Read, Grep, Glob, Bash"; else print }'
expect_fail "N16" "Agent Bash tool expansion rejected" "$fixture"

# N17. Broken process-document cross reference.
fixture="$(make_fixture N17)"
rewrite_with_awk "$fixture/CLAUDE.md" \
  '{ if (!done && index($0, "docs/process/GPT-REVIEW-CONTRACT.md") > 0) { sub(/docs\/process\/GPT-REVIEW-CONTRACT\.md/, "docs/process/DOES-NOT-EXIST.md"); done=1 } print }'
expect_fail "N17" "broken process-document cross-reference rejected" "$fixture"

# N18. Sensitive assignment candidate inserted from runtime fragments.
fixture="$(make_fixture N18)"
N18_VALUE='fixture-sensitive-value'
printf '%s%s%s\n' \
  'pass' \
  'word' \
  "=$N18_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N18" \
  "sensitive candidate rejected without value disclosure" \
  "$fixture" \
  "$N18_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N19. Sensitive scan I/O error must not be treated as clean.
fixture="$(make_fixture N19)"
rm -f "$fixture/CLAUDE.md"
mkdir "$fixture/CLAUDE.md"
expect_fail "N19" "sensitive scan error rejected rather than treated as clean" "$fixture" 0 "sensitive scan error in CLAUDE.md"

# N20. Git index mode 120000 while the worktree path is restored to a regular file.
fixture="$(make_fixture N20)"
git -C "$fixture" init -q
git -C "$fixture" config user.email "fixture@example.invalid"
git -C "$fixture" config user.name "process-docs-self-test"
git -C "$fixture" add .
rm -f "$fixture/CLAUDE.md"
ln -s "docs/process/PROJECT-RULES.md" "$fixture/CLAUDE.md"
git -C "$fixture" add CLAUDE.md
rm -f "$fixture/CLAUDE.md"
cp "$SOURCE_ROOT/CLAUDE.md" "$fixture/CLAUDE.md"
expect_fail "N20" "Git mode 120000 rejected even with regular worktree file" "$fixture" 1 "Git mode 120000 blocked: CLAUDE.md"


# N21. Second reference in a related group is broken while the first remains valid.
fixture="$(make_fixture N21)"
rewrite_with_awk "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
  '{ if ($0 ~ /^\| 1 \|/ && $0 ~ /\(관련 #479, #480, #481\)/) sub(/#480/, "#999"); print }'
expect_fail "N21" "broken second related reference rejected" "$fixture" 0 "Migration Matrix broken related # reference detected"

# N22. Second reference in a related group duplicates the first reference.
fixture="$(make_fixture N22)"
rewrite_with_awk "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
  '{ if ($0 ~ /^\| 1 \|/ && $0 ~ /\(관련 #479, #480, #481\)/) sub(/#480/, "#479"); print }'
expect_fail "N22" "duplicate second related reference rejected" "$fixture" 0 "Migration Matrix duplicate related # reference detected within a row"

# N23. Remove every TASK row from the Roadmap full-history section.
fixture="$(make_fixture N23)"
rewrite_with_awk "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
  'BEGIN{inside=0} /^## 전체 TASK 이력$/ {inside=1; print; next} /^## CS 주차 ↔ TASK ↔ interview 연결$/ {inside=0; print; next} { if (inside && $0 ~ /^\| TASK-[0-9]+(-[0-9]+)? \|/) next; print }'
expect_fail "N23" "zero Roadmap history rows rejected with FAIL marker" "$fixture" 0 "Roadmap full history TASK row count is not 76"

# N24. Remove every final CFL mapping row.
fixture="$(make_fixture N24)"
rewrite_with_awk "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
  '{ if ($0 ~ /^\| CFL-[0-9][0-9] \| 결정 [0-9]+ \|/) next; print }'
expect_fail "N24" "zero CFL mapping rows rejected with FAIL marker" "$fixture" 0 "CFL final mapping row count is not 20"

# N25. Insert an unexpected TASK inside the approved direct-order fenced block.
fixture="$(make_fixture N25)"
rewrite_with_awk "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
  'BEGIN{section=0; fence=0; done=0} /^## 다음 직접 진행 순서$/ {section=1; print; next} section && $0=="```text" {fence=1; print; next} section && fence && $0=="TASK-032" && !done {print; print "→ TASK-999"; done=1; next} section && fence && $0=="```" {fence=0} /^## 전체 TASK 이력$/ {section=0} {print} END{if(!done) exit 2}'
expect_fail "N25" "extra TASK in Roadmap direct-order fenced block rejected" "$fixture" 0 "Roadmap direct TASK order mismatch"

# N26. Change only current-status TASK-059 from 완료 to 진행중; full history stays unchanged.
fixture="$(make_fixture N26)"
rewrite_with_awk "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
  'BEGIN{inside=0; done=0} /^## 현재 확인 상태$/ {inside=1; print; next} /^## 다음 직접 진행 순서$/ {inside=0; print; next} inside && /^\| TASK-059 \|/ && !done {sub(/\| 완료 \|/, "| 진행중 |"); done=1} {print} END{if(!done) exit 2}'
expect_fail "N26" "current-status TASK-059 mismatch rejected" "$fixture" 0 "Roadmap current-status TASK-059/TASK-060 state consistency failed"

# N27. Change only current-status TASK-060 from 진행중 to 완료; full history stays unchanged.
fixture="$(make_fixture N27)"
rewrite_with_awk "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
  'BEGIN{inside=0;seen=0;changed=0}
   $0=="## 현재 확인 상태"{inside=1;print;next}
   inside && /^## /{inside=0}
   inside && /^\| TASK-060 \|/{seen++;changed+=sub(/\| 진행중 \|/, "| 완료 |")}
   {print}
   END{if(seen != 1 || changed != 1) exit 2}'

if ! awk -F'|' '
  function t(s){gsub(/^[[:space:]]+|[[:space:]]+$/, "", s);return s}
  BEGIN{inside=0;seen=0;complete=0;original=0}
  $0=="## 현재 확인 상태"{inside=1;next}
  inside && /^## /{inside=0}
  inside && /^\| TASK-060 \|/{
    seen++
    status=t($4)
    if(status=="완료") complete++
    if(status=="진행중") original++
  }
  END{if(seen != 1 || complete != 1 || original != 0) exit 1}
' "$fixture/docs/process/PORTFOLIO-ROADMAP.md"
then
  record_fail "N27 current-status TASK-060 mutation postcondition failed"
else
  expect_fail "N27" "current-status TASK-060 mismatch rejected" "$fixture" 0 "Roadmap current-status TASK-059/TASK-060 state consistency failed"
fi

# N28. Keep the approved full-test command and add a conflicting clean-test command.
fixture="$(make_fixture N28)"
rewrite_with_awk "$fixture/docs/process/PROJECT-RULES.md" \
  '{ print; if ($0=="./gradlew clean test --no-daemon --stacktrace -Dspring.profiles.active=test" && !done) { print "./gradlew clean test -Dspring.profiles.active=test"; done=1 } } END{if(!done) exit 2}'
expect_fail "N28" "conflicting additional full-test command rejected" "$fixture" 0 "PROJECT-RULES conflicting full test command detected"


# P2. Structured placeholders/provenance/key-name forms are accepted.
fixture="$(make_fixture P2)"

printf '%s%s\n' \
  '${JWT_' \
  'SECRET}' \
  >> "$fixture/CLAUDE.md"

printf '%s%s\n' \
  '<sec' \
  'ret>' \
  >> "$fixture/CLAUDE.md"

printf '%s%s%s\n' \
  'to' \
  'ken' \
  ':user:{fixtureProvenance}' \
  >> "$fixture/CLAUDE.md"

printf '%s%s\n' \
  'client_' \
  'secret' \
  >> "$fixture/CLAUDE.md"

printf '%s%s\n' \
  'jwt_' \
  'secret' \
  >> "$fixture/CLAUDE.md"

printf '%s\n' \
  'clientSecret' \
  'jwtSecret' \
  'dbPassword' \
  'serviceApiKey' \
  'serviceAccessKey' \
  'serviceAccessToken' \
  >> "$fixture/CLAUDE.md"

printf '%s%s%s%s\n' \
  'clientSec' \
  'ret' \
  ': ' \
  '${CLIENT_SECRET}' \
  >> "$fixture/CLAUDE.md"

printf '%s%s%s%s\n' \
  'jwtSec' \
  'ret' \
  '=' \
  '<JWT_SECRET>' \
  >> "$fixture/CLAUDE.md"

printf '%s\n' \
  '"clientSecret"' \
  "'clientSecret'" \
  '"client_secret"' \
  >> "$fixture/CLAUDE.md"

printf '%s%s%s%s%s%s\n' \
  '"' \
  'clientSec' \
  'ret' \
  '"' \
  ': ' \
  '"${CLIENT_SECRET}"' \
  >> "$fixture/CLAUDE.md"

printf '%s%s%s%s%s%s\n' \
  '"' \
  'clientSec' \
  'ret' \
  '"' \
  ': ' \
  "'\${CLIENT_SECRET}'" \
  >> "$fixture/CLAUDE.md"

printf '%s%s%s%s%s%s\n' \
  "'" \
  'clientSec' \
  'ret' \
  "'" \
  ': ' \
  "'\${CLIENT_SECRET}'" \
  >> "$fixture/CLAUDE.md"

printf '%s%s%s%s%s%s\n' \
  "'" \
  'clientSec' \
  'ret' \
  "'" \
  ': ' \
  "\"\${CLIENT_SECRET}\"" \
  >> "$fixture/CLAUDE.md"

printf '%s%s%s%s%s%s\n' \
  '"' \
  'clientSec' \
  'ret' \
  '"' \
  ': ' \
  '"<CLIENT_SECRET>"' \
  >> "$fixture/CLAUDE.md"

printf '%s%s%s%s%s%s\n' \
  "'" \
  'clientSec' \
  'ret' \
  "'" \
  ': ' \
  "'<CLIENT_SECRET>'" \
  >> "$fixture/CLAUDE.md"

P2_JSON_A_OPEN='{'
P2_JSON_A_BENIGN_KEY='"name"'
P2_JSON_A_BENIGN_DELIM=':'
P2_JSON_A_BENIGN_VALUE='"x"'
P2_JSON_A_COMMA=','
P2_JSON_A_KEY_QUOTE='"'
P2_JSON_A_KEY_A='clientSec'
P2_JSON_A_KEY_B='ret'
P2_JSON_A_DELIM=':'
P2_JSON_A_VALUE_QUOTE='"'
P2_JSON_A_VALUE='${CLIENT_SECRET}'
P2_JSON_A_CLOSE='}'

{
  printf '%s' "$P2_JSON_A_OPEN"
  printf '%s%s%s' \
    "$P2_JSON_A_BENIGN_KEY" \
    "$P2_JSON_A_BENIGN_DELIM" \
    "$P2_JSON_A_BENIGN_VALUE"
  printf '%s' "$P2_JSON_A_COMMA"
  printf '%s' "$P2_JSON_A_KEY_QUOTE"
  printf '%s%s' "$P2_JSON_A_KEY_A" "$P2_JSON_A_KEY_B"
  printf '%s' "$P2_JSON_A_KEY_QUOTE"
  printf '%s' "$P2_JSON_A_DELIM"
  printf '%s' "$P2_JSON_A_VALUE_QUOTE"
  printf '%s' "$P2_JSON_A_VALUE"
  printf '%s' "$P2_JSON_A_VALUE_QUOTE"
  printf '%s\n' "$P2_JSON_A_CLOSE"
} >> "$fixture/CLAUDE.md"

P2_JSON_B_OPEN='{'
P2_JSON_B_KEY_QUOTE='"'
P2_JSON_B_KEY_A='clientSec'
P2_JSON_B_KEY_B='ret'
P2_JSON_B_DELIM=':'
P2_JSON_B_VALUE_QUOTE='"'
P2_JSON_B_VALUE='<CLIENT_SECRET>'
P2_JSON_B_COMMA=','
P2_JSON_B_BENIGN_KEY='"name"'
P2_JSON_B_BENIGN_DELIM=':'
P2_JSON_B_BENIGN_VALUE='"x"'
P2_JSON_B_CLOSE='}'

{
  printf '%s' "$P2_JSON_B_OPEN"
  printf '%s' "$P2_JSON_B_KEY_QUOTE"
  printf '%s%s' "$P2_JSON_B_KEY_A" "$P2_JSON_B_KEY_B"
  printf '%s' "$P2_JSON_B_KEY_QUOTE"
  printf '%s' "$P2_JSON_B_DELIM"
  printf '%s' "$P2_JSON_B_VALUE_QUOTE"
  printf '%s' "$P2_JSON_B_VALUE"
  printf '%s' "$P2_JSON_B_VALUE_QUOTE"
  printf '%s' "$P2_JSON_B_COMMA"
  printf '%s%s%s' \
    "$P2_JSON_B_BENIGN_KEY" \
    "$P2_JSON_B_BENIGN_DELIM" \
    "$P2_JSON_B_BENIGN_VALUE"
  printf '%s\n' "$P2_JSON_B_CLOSE"
} >> "$fixture/CLAUDE.md"

expect_pass \
  "P2" \
  "structured-placeholder/provenance/key-name forms accepted" \
  "$fixture"

# N29. Environment-style sensitive suffix with equals.
fixture="$(make_fixture N29)"
N29_VALUE='fixture-sensitive-value'
printf '%s%s%s%s\n' \
  'JWT_' \
  'SEC' \
  'RET' \
  "=$N29_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N29" \
  "environment-style sensitive assignment rejected without value disclosure" \
  "$fixture" \
  "$N29_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N30. Environment-style token suffix with colon.
fixture="$(make_fixture N30)"
N30_VALUE='fixture-sensitive-value'
printf '%s%s%s%s\n' \
  'ACCESS_' \
  'TOK' \
  'EN' \
  ": $N30_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N30" \
  "environment-style token assignment rejected without value disclosure" \
  "$fixture" \
  "$N30_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N31. Lowercase standalone token-like key with colon.
fixture="$(make_fixture N31)"
N31_VALUE='fixture-sensitive-value'
printf '%s%s%s\n' \
  'to' \
  'ken' \
  ": $N31_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N31" \
  "lowercase standalone token assignment rejected without value disclosure" \
  "$fixture" \
  "$N31_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N32. Lowercase access token-like key with colon.
fixture="$(make_fixture N32)"
N32_VALUE='fixture-sensitive-value'
printf '%s%s%s%s\n' \
  'access_' \
  'to' \
  'ken' \
  ": $N32_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N32" \
  "lowercase access token assignment rejected without value disclosure" \
  "$fixture" \
  "$N32_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N33. PROJECT-RULES related-test-first obligation must not become optional.
fixture="$(make_fixture N33)"
rewrite_with_awk "$fixture/docs/process/PROJECT-RULES.md" \
  '{ if ($0 == "- 관련 테스트가 있으면 먼저 실행한다.") print "- 관련 테스트를 먼저 실행할 수 있다."; else print }'
expect_fail "N33" "PROJECT-RULES optional related-test wording rejected" "$fixture" 0 "PROJECT-RULES related-test-first obligation"

# N34. DEVELOPMENT-WORKFLOW related-test-first obligation must not become optional.
fixture="$(make_fixture N34)"
rewrite_with_awk "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md" \
  '{ if ($0 == "- 관련 테스트가 있으면 먼저 실행한다.") print "- 관련 테스트를 먼저 실행할 수 있다."; else print }'
expect_fail "N34" "Workflow optional related-test wording rejected" "$fixture" 0 "DEVELOPMENT-WORKFLOW related-test-first obligation"

# N35. Remove the conditional pre-merge Learning transition.
fixture="$(make_fixture N35)"
rewrite_with_awk "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md" \
  '{ if ($0 != "- merge 전 learning gate가 있고 아직 통과하지 않았다면 단계 13 Learning을 선행 수행한다.") print }'
expect_fail "N35" "pre-merge Learning transition removal rejected" "$fixture" 0 "DEVELOPMENT-WORKFLOW pre-merge Learning transition missing"

# N36. Quoted token-like key with quoted value.
fixture="$(make_fixture N36)"
N36_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s\n' \
  '"' \
  'to' \
  'ken' \
  '"' \
  ': "' \
  "$N36_VALUE"'"' \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N36" \
  "quoted token assignment rejected without value disclosure" \
  "$fixture" \
  "$N36_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N37. Quoted access token-like key with quoted value.
fixture="$(make_fixture N37)"
N37_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s%s\n' \
  '"' \
  'access_' \
  'to' \
  'ken' \
  '"' \
  ': "' \
  "$N37_VALUE"'"' \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N37" \
  "quoted access token assignment rejected without value disclosure" \
  "$fixture" \
  "$N37_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N38. Quoted password-like key with quoted value.
fixture="$(make_fixture N38)"
N38_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s\n' \
  '"' \
  'pass' \
  'word' \
  '"' \
  ': "' \
  "$N38_VALUE"'"' \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N38" \
  "quoted password assignment rejected without value disclosure" \
  "$fixture" \
  "$N38_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N39. Quoted secret-like key with quoted value.
fixture="$(make_fixture N39)"
N39_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s\n' \
  '"' \
  'sec' \
  'ret' \
  '"' \
  ': "' \
  "$N39_VALUE"'"' \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N39" \
  "quoted secret assignment rejected without value disclosure" \
  "$fixture" \
  "$N39_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N40. Compact token-like colon form with quoted value.
fixture="$(make_fixture N40)"
N40_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s\n' \
  'to' \
  'ken' \
  ':' \
  '"' \
  "$N40_VALUE"'"' \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N40" \
  "compact quoted token assignment rejected without value disclosure" \
  "$fixture" \
  "$N40_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N41. Compact access token-like colon form with quoted value.
fixture="$(make_fixture N41)"
N41_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s\n' \
  'access_' \
  'to' \
  'ken' \
  ':' \
  '"' \
  "$N41_VALUE"'"' \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N41" \
  "compact quoted access token assignment rejected without value disclosure" \
  "$fixture" \
  "$N41_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N42. Remove one required lifecycle schema field from stage 12.
fixture="$(make_fixture N42)"
rewrite_with_awk "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md" \
  'BEGIN{inside=0; removed=0} /^### 12\. 조건부 회고 \/ 장애 기록$/ {inside=1; print; next} /^### 13\. Learning \/ interview$/ {inside=0; print; next} inside && $0=="**중단 조건**" && !removed {removed=1; next} {print} END{if(!removed) exit 2}'
expect_fail "N42" "stage 12 missing required schema field rejected" "$fixture" 0 "DEVELOPMENT-WORKFLOW lifecycle schema mismatch: stage 12 missing 중단 조건"

# N43. Remove one required final-completion stop condition.
fixture="$(make_fixture N43)"
rewrite_with_awk "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md" \
  '{ if ($0 != "- 필요한 review 또는 검증 미완료") print }'
expect_fail "N43" "stage 14 missing review/validation stop condition rejected" "$fixture" 0 "DEVELOPMENT-WORKFLOW final review/validation stop condition missing"

# N44. Remove GlobalExceptionHandler positive responsibility.
fixture="$(make_fixture N44)"
rewrite_with_awk "$fixture/docs/process/PROJECT-RULES.md" \
  '{ if ($0 != "- 현재 `GlobalExceptionHandler`를 공통 오류 처리 구조의 일부로 사용한다.") print }'
expect_fail "N44" "GlobalExceptionHandler positive responsibility removal rejected" "$fixture" 0 "PROJECT-RULES GlobalExceptionHandler positive responsibility missing"

# N45. Remove framework-boundary response-contract exception.
fixture="$(make_fixture N45)"
rewrite_with_awk "$fixture/docs/process/PROJECT-RULES.md" \
  '{ if ($0 != "- Security/JWT Filter처럼 `GlobalExceptionHandler` 밖의 framework boundary에서도 필요한 경우 동일한 응답 계약을 구성할 수 있다.") print }'
expect_fail "N45" "framework-boundary response-contract exception removal rejected" "$fixture" 0 "PROJECT-RULES framework-boundary response-contract exception missing"

# N46. Keep approved README rules and add the old PROJECT-RULES narrowing as a contradiction.
fixture="$(make_fixture N46)"
printf '%s\n' \
  "- README는 실행 방법, 환경, 외부 사용 방식이 바뀔 때만 수정한다." \
  >> "$fixture/docs/process/PROJECT-RULES.md"
expect_fail \
  "N46" \
  "PROJECT-RULES README narrowing rejected independently" \
  "$fixture" \
  0 \
  "PROJECT-RULES README condition narrowed to execution/environment/external-use only"

# N47. Keep approved README rules and add the old Workflow narrowing as a contradiction.
fixture="$(make_fixture N47)"
printf '%s\n' \
  "- README는 실행·환경·외부 사용 방식이 바뀔 때만 수정한다." \
  >> "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md"
expect_fail \
  "N47" \
  "Workflow README narrowing rejected independently" \
  "$fixture" \
  0 \
  "DEVELOPMENT-WORKFLOW README condition narrowed to execution/environment/external-use only"

# N48. Compact unquoted token-like assignment.
fixture="$(make_fixture N48)"
N48_VALUE='fixture-sensitive-value'
printf '%s%s%s\n' \
  'to' \
  'ken' \
  ":$N48_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N48" \
  "compact unquoted token assignment rejected without value disclosure" \
  "$fixture" \
  "$N48_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N49. Compact unquoted access token-like assignment.
fixture="$(make_fixture N49)"
N49_VALUE='fixture-sensitive-value'
printf '%s%s%s%s\n' \
  'access_' \
  'to' \
  'ken' \
  ":$N49_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N49" \
  "compact unquoted access token assignment rejected without value disclosure" \
  "$fixture" \
  "$N49_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N50. Lowercase prefixed secret-like assignment.
fixture="$(make_fixture N50)"
N50_VALUE='fixture-sensitive-value'
printf '%s%s%s%s\n' \
  'client_' \
  'sec' \
  'ret' \
  ": $N50_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N50" \
  "lowercase prefixed secret assignment rejected without value disclosure" \
  "$fixture" \
  "$N50_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N51. Lowercase JWT-prefixed secret-like assignment.
fixture="$(make_fixture N51)"
N51_VALUE='fixture-sensitive-value'
printf '%s%s%s%s\n' \
  'jwt_' \
  'sec' \
  'ret' \
  "=$N51_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N51" \
  "lowercase prefixed JWT assignment rejected without value disclosure" \
  "$fixture" \
  "$N51_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N52. Keep decision number and change only CFL final responsibility document.
fixture="$(make_fixture N52)"
rewrite_with_awk \
  "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
  '{ if ($0 ~ /^\| CFL-01 \| 결정 5 \|/) sub(/`docs\/process\/GPT-REVIEW-CONTRACT\.md`/, "`docs/process/PROJECT-RULES.md`"); print }'
expect_fail \
  "N52" \
  "CFL owner-only mismatch rejected" \
  "$fixture" \
  0 \
  "CFL-01 final decision/owner mapping mismatch"

# N53. Same-group source ID mutation keeps 684 rows and A/B/C aggregates.
fixture="$(make_fixture N53)"
rewrite_with_awk \
  "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
  '{ if ($0 ~ /^\| 1 \| A-01 \|/) sub(/^\| 1 \| A-01 \|/, "| 1 | A-02 |"); print }'
expect_fail \
  "N53" \
  "same-group source provenance mismatch rejected" \
  "$fixture" \
  0 \
  "Migration Matrix source projection SHA-256 mismatch"

# N54. Authoritative DB reference missing.
fixture="$(make_fixture N54)"
rm -f "$fixture/docs/db/README.md"
expect_fail \
  "N54" \
  "missing authoritative DB reference rejected" \
  "$fixture" \
  0 \
  "authoritative reference missing: docs/db/README.md"

# N55. Authoritative DB reference wrong type.
fixture="$(make_fixture N55)"
rm -f "$fixture/docs/db/README.md"
mkdir -p "$fixture/docs/db/README.md"
expect_fail \
  "N55" \
  "non-file authoritative DB reference rejected" \
  "$fixture" \
  0 \
  "authoritative reference is not a regular file: docs/db/README.md"

# N56. Architecture reference wrong type.
fixture="$(make_fixture N56)"
rm -rf "$fixture/docs/architecture"
printf '%s\n' "fixture" > "$fixture/docs/architecture"
expect_fail \
  "N56" \
  "non-directory architecture reference rejected" \
  "$fixture" \
  0 \
  "authoritative reference is not a directory: docs/architecture"

# N57. API reference symlink.
fixture="$(make_fixture N57)"
rm -rf "$fixture/docs/api"
ln -s "architecture" "$fixture/docs/api"
expect_fail \
  "N57" \
  "symlink authoritative API reference rejected" \
  "$fixture" \
  0 \
  "authoritative reference symlink blocked: docs/api"

# N58. Preserve approved sentence and add contradictory universal pre-merge rule.
fixture="$(make_fixture N58)"
printf '%s\n' \
  "- 모든 TASK는 merge 전에 학습 검증을 통과해야 한다." \
  >> "$fixture/docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md"
expect_fail \
  "N58" \
  "universal pre-merge learning prohibition isolated" \
  "$fixture" \
  0 \
  "Learning incorrectly makes pre-merge learning mandatory for all TASKs"

# N59. Preserve conditional general-TASK rule and add mandatory INTERVIEW-NOTES.
fixture="$(make_fixture N59)"
printf '%s\n' \
  '일반 TASK도 `INTERVIEW-NOTES.md` 저장소 작성을 필수로 한다.' \
  >> "$fixture/docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md"
expect_fail \
  "N59" \
  "general-TASK INTERVIEW-NOTES prohibition isolated" \
  "$fixture" \
  0 \
  "Learning incorrectly makes INTERVIEW-NOTES mandatory for general TASKs"

# N60. Preserve PROJECT-RULES obligation and add only contradictory optional wording.
fixture="$(make_fixture N60)"
printf '%s\n' \
  "- 관련 테스트를 먼저 실행할 수 있다." \
  >> "$fixture/docs/process/PROJECT-RULES.md"
expect_fail \
  "N60" \
  "PROJECT-RULES optional related-test prohibition isolated" \
  "$fixture" \
  0 \
  "PROJECT-RULES related-test-first obligation weakened to optional"

# N61. Preserve Workflow obligation and add only contradictory optional wording.
fixture="$(make_fixture N61)"
printf '%s\n' \
  "- 관련 테스트를 먼저 실행할 수 있다." \
  >> "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md"
expect_fail \
  "N61" \
  "Workflow optional related-test prohibition isolated" \
  "$fixture" \
  0 \
  "DEVELOPMENT-WORKFLOW related-test-first obligation weakened to optional"


# N62. camelCase secret-suffix assignment with colon.
fixture="$(make_fixture N62)"
N62_VALUE='fixture-sensitive-value'
printf '%s%s%s%s\n' \
  'client' \
  'Sec' \
  'ret' \
  ": $N62_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N62" \
  "camelCase secret suffix assignment rejected without value disclosure" \
  "$fixture" \
  "$N62_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N63. camelCase secret-suffix assignment with equals.
fixture="$(make_fixture N63)"
N63_VALUE='fixture-sensitive-value'
printf '%s%s%s%s\n' \
  'jwt' \
  'Sec' \
  'ret' \
  "=$N63_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N63" \
  "camelCase equals assignment rejected without value disclosure" \
  "$fixture" \
  "$N63_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N64. camelCase password-suffix assignment with colon.
fixture="$(make_fixture N64)"
N64_VALUE='fixture-sensitive-value'
printf '%s%s%s%s\n' \
  'db' \
  'Pass' \
  'word' \
  ": $N64_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N64" \
  "camelCase password suffix assignment rejected without value disclosure" \
  "$fixture" \
  "$N64_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N65. Plan / Issue input removed; same line placed in another stage as decoy.
fixture="$(make_fixture N65)"
rewrite_with_awk \
  "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
  'BEGIN{inside=0;removed=0;decoy=0}
   $0=="### Plan / Issue"{inside=1;print;next}
   $0=="### Implementation"{
     inside=0
     print
     if(!decoy){print "- Definition of Done";decoy=1}
     next
   }
   inside && $0=="- Definition of Done" && !removed{
     removed=1
     next
   }
   {print}
   END{if(!removed || !decoy) exit 2}'
expect_fail \
  "N65" \
  "Plan / Issue stage-specific core input deletion rejected despite decoy" \
  "$fixture" \
  0 \
  "GPT Plan / Issue core input list mismatch"

# N66. Implementation input removed; same line placed in Plan / Issue as decoy.
fixture="$(make_fixture N66)"
rewrite_with_awk \
  "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
  'BEGIN{inside=0;removed=0;decoy=0}
   $0=="### Plan / Issue"{
     print
     if(!decoy){print "- 실제 변경 code 또는 diff";decoy=1}
     next
   }
   $0=="### Implementation"{inside=1;print;next}
   $0=="### Git / PR"{inside=0;print;next}
   inside && $0=="- 실제 변경 code 또는 diff" && !removed{
     removed=1
     next
   }
   {print}
   END{if(!removed || !decoy) exit 2}'
expect_fail \
  "N66" \
  "Implementation stage-specific core input deletion rejected despite decoy" \
  "$fixture" \
  0 \
  "GPT Implementation core input list mismatch"

# N67. Git / PR input removed; same line placed in Implementation as decoy.
fixture="$(make_fixture N67)"
rewrite_with_awk \
  "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
  'BEGIN{inside=0;removed=0;decoy=0}
   $0=="### Implementation"{
     print
     if(!decoy){print "- final diff";decoy=1}
     next
   }
   $0=="### Git / PR"{inside=1;print;next}
   $0=="### Fixed packet"{inside=0;print;next}
   inside && $0=="- final diff" && !removed{
     removed=1
     next
   }
   {print}
   END{if(!removed || !decoy) exit 2}'
expect_fail \
  "N67" \
  "Git / PR stage-specific core input deletion rejected despite decoy" \
  "$fixture" \
  0 \
  "GPT Git / PR core input list mismatch"

# N68. Fixed packet input removed; same sentence placed in Git / PR as decoy.
fixture="$(make_fixture N68)"
rewrite_with_awk \
  "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
  'BEGIN{inside=0;removed=0;decoy=0}
   $0=="### Git / PR"{
     print
     if(!decoy){
       print "핵심 입력은 사용자가 해당 검토에 대해 정의한 전체 고정 패킷이다."
       decoy=1
     }
     next
   }
   $0=="### Fixed packet"{inside=1;print;next}
   inside &&
   $0=="핵심 입력은 사용자가 해당 검토에 대해 정의한 전체 고정 패킷이다." &&
   !removed{
     removed=1
     next
   }
   /^## 4\./{inside=0;print;next}
   {print}
   END{if(!removed || !decoy) exit 2}'
expect_fail \
  "N68" \
  "Fixed packet stage-specific core input deletion rejected despite decoy" \
  "$fixture" \
  0 \
  "GPT Fixed packet core input missing: 전체 고정 패킷"

# N69. Actual CFL row owner is wrong; approved triplet exists only as non-row decoy.
fixture="$(make_fixture N69)"
rewrite_with_awk \
  "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
  'BEGIN{changed=0}
   {
     if($0 ~ /^\| CFL-01 \| 결정 5 \|/ && !changed){
       sub(/`docs\/process\/GPT-REVIEW-CONTRACT\.md`/, "`docs/process/PROJECT-RULES.md`")
       changed=1
     }
     print
   }
   END{if(!changed) exit 2}'
printf '%s\n' \
  'decoy: | CFL-01 | 결정 5 | `docs/process/GPT-REVIEW-CONTRACT.md` |' \
  >> "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md"
expect_fail \
  "N69" \
  "CFL actual-row owner mismatch rejected despite non-table triplet decoy" \
  "$fixture" \
  0 \
  "CFL-01 final decision/owner mapping mismatch"

# N70. Mutate only PROJECT-RULES primary DB reference line.
#      The separate approved DB index occurrence remains intact.
fixture="$(make_fixture N70)"
rewrite_with_awk \
  "$fixture/docs/process/PROJECT-RULES.md" \
  'BEGIN{changed=0}
   $0=="DB와 Flyway 상세 기준은 `docs/db/README.md`를 우선한다." && !changed{
     print "DB와 Flyway 상세 기준은 `docs/db/README-broken.md`를 우선한다."
     changed=1
     next
   }
   {print}
   END{if(!changed) exit 2}'
expect_fail \
  "N70" \
  "PROJECT-RULES primary DB reference mutation rejected while DB index reference remains" \
  "$fixture" \
  0 \
  "PROJECT-RULES authoritative reference contract failure: DB/Flyway primary line"

# N71. Mutate only PROJECT-RULES current-structure architecture reference line.
#      The separate approved architecture index occurrence remains intact.
fixture="$(make_fixture N71)"
rewrite_with_awk \
  "$fixture/docs/process/PROJECT-RULES.md" \
  'BEGIN{changed=0}
   $0=="상세 구조는 현재 `docs/architecture/`와 실제 package를 함께 확인한다." && !changed{
     print "상세 구조는 현재 `docs/architecture-broken/`와 실제 package를 함께 확인한다."
     changed=1
     next
   }
   {print}
   END{if(!changed) exit 2}'
expect_fail \
  "N71" \
  "PROJECT-RULES architecture current-structure reference mutation rejected while index reference remains" \
  "$fixture" \
  0 \
  "PROJECT-RULES authoritative reference contract failure: architecture current-structure line"

# N72. PROJECT-RULES API reference source string mutation.
fixture="$(make_fixture N72)"
rewrite_with_awk \
  "$fixture/docs/process/PROJECT-RULES.md" \
  'BEGIN{changed=0}
   {
     if(gsub(/docs\/api\//, "docs/api-broken/") > 0){
       changed=1
     }
     print
   }
   END{if(!changed) exit 2}'
expect_fail \
  "N72" \
  "PROJECT-RULES API source-side reference mutation rejected" \
  "$fixture" \
  0 \
  "PROJECT-RULES authoritative reference contract failure: API index line"

# N73. TASK-START-CHECKLIST DB reference source string mutation.
fixture="$(make_fixture N73)"
rewrite_with_awk \
  "$fixture/docs/process/TASK-START-CHECKLIST.md" \
  'BEGIN{changed=0}
   {
     if(gsub(/docs\/db\/README\.md/, "docs/db/README-broken.md") > 0){
       changed=1
     }
     print
   }
   END{if(!changed) exit 2}'
expect_fail \
  "N73" \
  "TASK-START-CHECKLIST DB source-side reference mutation rejected" \
  "$fixture" \
  0 \
  "TASK-START-CHECKLIST authoritative reference contract failure: DB reference line"

# N74. Duplicate Plan / Issue heading inside section 3 must be rejected.
#      Remove Definition of Done from the original Plan / Issue section,
#      then insert a second Plan / Issue heading immediately before
#      the original Implementation heading, still inside section 3.
fixture="$(make_fixture N74)"
rewrite_with_awk \
  "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
  'BEGIN{
     inside_plan=0
     seen_plan=0
     removed=0
     inserted=0
   }

   $0=="### Plan / Issue" && !seen_plan{
     seen_plan=1
     inside_plan=1
     print
     next
   }

   inside_plan && $0=="- Definition of Done" && !removed{
     removed=1
     next
   }

   $0=="### Implementation" && seen_plan && !inserted{
     inside_plan=0
     print "### Plan / Issue"
     print "- Definition of Done"
     print ""
     print $0
     inserted=1
     next
   }

   {
     print
   }

   END{
     if(!seen_plan || !removed || !inserted) exit 2
   }'
expect_fail \
  "N74" \
  "duplicate Plan / Issue heading inside section 3 rejected" \
  "$fixture" \
  0 \
  "GPT review stage structural contract failure: Plan / Issue heading count must be exactly 1 in section 3"


# N75. Double-quoted prefixed camelCase secret-like assignment.
fixture="$(make_fixture N75)"
N75_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s\n' \
  '"' \
  'clientSec' \
  'ret' \
  '"' \
  ': ' \
  "$N75_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N75" \
  "double-quoted camelCase assignment rejected without value disclosure" \
  "$fixture" \
  "$N75_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N76. Double-quoted prefixed snake-case secret-like assignment.
fixture="$(make_fixture N76)"
N76_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s%s\n' \
  '"' \
  'client_' \
  'sec' \
  'ret' \
  '"' \
  ': ' \
  "$N76_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N76" \
  "double-quoted snake-case assignment rejected without value disclosure" \
  "$fixture" \
  "$N76_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N77. Double-quoted prefixed JWT secret-like assignment.
fixture="$(make_fixture N77)"
N77_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s\n' \
  '"' \
  'jwtSec' \
  'ret' \
  '"' \
  ': ' \
  "$N77_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N77" \
  "double-quoted JWT assignment rejected without value disclosure" \
  "$fixture" \
  "$N77_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N78. Double-quoted prefixed DB password-like assignment.
fixture="$(make_fixture N78)"
N78_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s\n' \
  '"' \
  'dbPass' \
  'word' \
  '"' \
  ': ' \
  "$N78_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N78" \
  "double-quoted DB password assignment rejected without value disclosure" \
  "$fixture" \
  "$N78_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N79. Single-quoted prefixed camelCase secret-like assignment.
fixture="$(make_fixture N79)"
N79_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s\n' \
  "'" \
  'clientSec' \
  'ret' \
  "'" \
  ': ' \
  "$N79_VALUE" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N79" \
  "single-quoted camelCase assignment rejected without value disclosure" \
  "$fixture" \
  "$N79_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N80. Plan / Issue raw structure moved inside a Markdown fence.
fixture="$(make_fixture N80)"
rewrite_with_awk \
  "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
  'BEGIN{
     opened=0
     closed=0
     inside_plan=0
   }

   $0=="### Plan / Issue" && !opened{
     print "```text"
     print
     opened=1
     inside_plan=1
     next
   }

   $0=="### Implementation" && inside_plan && !closed{
     print "```"
     print
     closed=1
     inside_plan=0
     next
   }

   {
     print
   }

   END{
     if(!opened || !closed) exit 2
   }'
expect_fail \
  "N80" \
  "fenced Plan / Issue structural decoy rejected" \
  "$fixture" \
  0 \
  "GPT review stage structural contract failure: Plan / Issue heading count must be exactly 1 in section 3"

# N81. Actual CFL-01 row removed; exact raw row survives only inside a fence.
fixture="$(make_fixture N81)"
N81_ROW="$(
  awk '
    /^\| CFL-01 \| 결정 5 \|/ {
      print
      exit
    }
  ' "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md"
)"

if [ -z "$N81_ROW" ]; then
  record_fail \
    "N81 fenced CFL decoy fixture setup failed"
else
  rewrite_with_awk \
    "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
    '{
       if($0 !~ /^\| CFL-01 \| 결정 5 \|/) print
     }'

  {
    printf '%s\n' ''
    printf '%s\n' '```text'
    printf '%s\n' "$N81_ROW"
    printf '%s\n' '```'
  } >> "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md"

  expect_fail \
    "N81" \
    "fenced CFL raw-row decoy rejected" \
    "$fixture" \
    0 \
    "CFL final mapping row count is not 20"
fi

# N82. Approved DB/Flyway primary line survives only as fenced decoy.
fixture="$(make_fixture N82)"
rewrite_with_awk \
  "$fixture/docs/process/PROJECT-RULES.md" \
  'BEGIN{
     changed=0
   }

   $0=="DB와 Flyway 상세 기준은 `docs/db/README.md`를 우선한다." &&
   !changed{
     print "DB와 Flyway 상세 기준은 `docs/db/README-broken.md`를 우선한다."
     changed=1
     next
   }

   {
     print
   }

   END{
     if(!changed) exit 2
   }'

{
  printf '%s\n' ''
  printf '%s\n' '```text'
  printf '%s\n' \
    'DB와 Flyway 상세 기준은 `docs/db/README.md`를 우선한다.'
  printf '%s\n' '```'
} >> "$fixture/docs/process/PROJECT-RULES.md"

expect_fail \
  "N82" \
  "fenced authoritative DB reference decoy rejected" \
  "$fixture" \
  0 \
  "PROJECT-RULES authoritative reference contract failure: DB/Flyway primary line"

# N83. Revert only the new TASK-START core-input gate to the obsolete broad gate.
fixture="$(make_fixture N83)"
rewrite_with_awk \
  "$fixture/docs/process/TASK-START-CHECKLIST.md" \
  'BEGIN{
     changed=0
   }

   $0=="8. 현재 Phase를 차단하는 핵심 입력이 부족하면 해당 단계 작업을 시작하지 않고 부족한 항목을 명시한다." &&
   !changed{
     print "8. 입력이 부족하면 구현이나 리뷰를 시작하지 않고 부족한 항목을 명시한다."
     changed=1
     next
   }

   {
     print
   }

   END{
     if(!changed) exit 2
   }'
expect_fail \
  "N83" \
  "obsolete broad TASK-START input gate rejected while GPT delegation remains" \
  "$fixture" \
  0 \
  "TASK-START-CHECKLIST obsolete broad input gate detected"

# N84. Double-quoted key with double-quoted actual-looking value.
fixture="$(make_fixture N84)"
N84_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s%s\n' \
  '"' \
  'clientSec' \
  'ret' \
  '"' \
  ': ' \
  '"' \
  "$N84_VALUE"'"' \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N84" \
  "double-quoted key and double-quoted value rejected without value disclosure" \
  "$fixture" \
  "$N84_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N85. Double-quoted key with single-quoted actual-looking value.
fixture="$(make_fixture N85)"
N85_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s%s\n' \
  '"' \
  'clientSec' \
  'ret' \
  '"' \
  ': ' \
  "'" \
  "$N85_VALUE""'" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N85" \
  "double-quoted key and single-quoted value rejected without value disclosure" \
  "$fixture" \
  "$N85_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N86. Single-quoted key with double-quoted actual-looking value.
fixture="$(make_fixture N86)"
N86_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s%s\n' \
  "'" \
  'clientSec' \
  'ret' \
  "'" \
  ': ' \
  '"' \
  "$N86_VALUE"'"' \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N86" \
  "single-quoted key and double-quoted value rejected without value disclosure" \
  "$fixture" \
  "$N86_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N87. Single-quoted key with single-quoted actual-looking value.
fixture="$(make_fixture N87)"
N87_VALUE='fixture-sensitive-value'
printf '%s%s%s%s%s%s%s\n' \
  "'" \
  'clientSec' \
  'ret' \
  "'" \
  ': ' \
  "'" \
  "$N87_VALUE""'" \
  >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N87" \
  "single-quoted key and single-quoted value rejected without value disclosure" \
  "$fixture" \
  "$N87_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N88. Actual CFL-01 row removed; exact row survives only outside approved table context.
fixture="$(make_fixture N88)"
N88_ROW="$(
  awk '
    /^\| CFL-01 \| 결정 5 \|/ {
      print
      exit
    }
  ' "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md"
)"

if [ -z "$N88_ROW" ]; then
  record_fail \
    "N88 off-table CFL decoy fixture setup failed"
else
  rewrite_with_awk \
    "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
    '{
       if($0 !~ /^\| CFL-01 \| 결정 5 \|/) print
     }'

  {
    printf '%s\n' ''
    printf '%s\n' "$N88_ROW"
  } >> "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md"

  expect_fail \
    "N88" \
    "visible CFL row outside approved CFL table rejected" \
    "$fixture" \
    0 \
    "CFL final mapping row count is not 20"
fi

# N89. PROJECT-RULES DB primary reference survives only in another visible section.
fixture="$(make_fixture N89)"
rewrite_with_awk \
  "$fixture/docs/process/PROJECT-RULES.md" \
  'BEGIN{
     changed=0
     decoy=0
   }

   $0=="DB와 Flyway 상세 기준은 `docs/db/README.md`를 우선한다." &&
   !changed{
     print "DB와 Flyway 상세 기준은 `docs/db/README-broken.md`를 우선한다."
     changed=1
     next
   }

   $0=="## 6. 코드와 파일 제공" && !decoy{
     print
     print "DB와 Flyway 상세 기준은 `docs/db/README.md`를 우선한다."
     decoy=1
     next
   }

   {
     print
   }

   END{
     if(!changed || !decoy) exit 2
   }'
expect_fail \
  "N89" \
  "PROJECT-RULES primary authoritative off-section decoy rejected" \
  "$fixture" \
  0 \
  "PROJECT-RULES authoritative reference contract failure: DB/Flyway primary line"

# N90. TASK-START DB reference survives only outside its approved subsection.
fixture="$(make_fixture N90)"
rewrite_with_awk \
  "$fixture/docs/process/TASK-START-CHECKLIST.md" \
  'BEGIN{
     changed=0
     decoy=0
   }

   $0=="- `docs/db/README.md`와 현재 migration 경로를 먼저 확인한다." &&
   !changed{
     print "- `docs/db/README-broken.md`와 현재 migration 경로를 먼저 확인한다."
     changed=1
     next
   }

   $0=="## 5. 민감정보 요청 금지" && !decoy{
     print
     print "- `docs/db/README.md`와 현재 migration 경로를 먼저 확인한다."
     decoy=1
     next
   }

   {
     print
   }

   END{
     if(!changed || !decoy) exit 2
   }'
expect_fail \
  "N90" \
  "TASK-START authoritative DB off-section decoy rejected" \
  "$fixture" \
  0 \
  "TASK-START-CHECKLIST authoritative reference contract failure: DB reference line"

# N91. PROJECT-RULES reference-index line survives only in another visible section.
fixture="$(make_fixture N91)"
rewrite_with_awk \
  "$fixture/docs/process/PROJECT-RULES.md" \
  'BEGIN{
     changed=0
     decoy=0
   }

   $0=="## 6. 코드와 파일 제공" && !decoy{
     print
     print "- API 문서: `docs/api/`"
     decoy=1
     next
   }

   $0=="- API 문서: `docs/api/`" && !changed{
     print "- API 문서: `docs/api-broken/`"
     changed=1
     next
   }

   {
     print
   }

   END{
     if(!changed || !decoy) exit 2
   }'
expect_fail \
  "N91" \
  "PROJECT-RULES reference-index off-section decoy rejected" \
  "$fixture" \
  0 \
  "PROJECT-RULES authoritative reference contract failure: API index line"


# N92. TASK-START blocker evidence must come from the canonical table.
fixture="$(make_fixture N92)"
rewrite_with_awk \
  "$fixture/docs/process/TASK-START-CHECKLIST.md" \
  'BEGIN{
     changed=0
     decoy=0
   }

   $0=="| blockers | 핵심 입력 누락, 상태 불일치, Critical 등 중단 조건 |" &&
   !changed{
     print "| blockers | 입력 누락, 상태 불일치, Critical 등 중단 조건 |"
     changed=1
     next
   }

   $0=="## 3. 자료 요청 원칙" && !decoy{
     print "| blockers | 핵심 입력 누락, 상태 불일치, Critical 등 중단 조건 |"
     print ""
     decoy=1
   }

   {
     print
   }

   END{
     if(!changed || !decoy) exit 2
   }'
expect_fail \
  "N92" \
  "structured blocker off-table decoy rejected" \
  "$fixture" \
  0 \
  "TASK-START-CHECKLIST structured blockers row missing or non-unique in approved table"

# N93. Inline JSON second-field punctuation-leading quoted RHS.
fixture="$(make_fixture N93)"
N93_OBJECT_OPEN='{'
N93_BENIGN_KEY='"name"'
N93_BENIGN_DELIM=':'
N93_BENIGN_VALUE='"x"'
N93_COMMA=','
N93_KEY_QUOTE='"'
N93_KEY_A='clientSec'
N93_KEY_B='ret'
N93_DELIM=':'
N93_VALUE_QUOTE='"'
N93_VALUE='!fixture-sensitive-value'
N93_OBJECT_CLOSE='}'
{
  printf '%s' "$N93_OBJECT_OPEN"
  printf '%s%s%s' \
    "$N93_BENIGN_KEY" \
    "$N93_BENIGN_DELIM" \
    "$N93_BENIGN_VALUE"
  printf '%s' "$N93_COMMA"
  printf '%s' "$N93_KEY_QUOTE"
  printf '%s%s' "$N93_KEY_A" "$N93_KEY_B"
  printf '%s' "$N93_KEY_QUOTE"
  printf '%s' "$N93_DELIM"
  printf '%s' "$N93_VALUE_QUOTE"
  printf '%s' "$N93_VALUE"
  printf '%s' "$N93_VALUE_QUOTE"
  printf '%s\n' "$N93_OBJECT_CLOSE"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N93" \
  "inline second-field punctuation-leading RHS rejected without value disclosure" \
  "$fixture" \
  "$N93_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N94. Punctuation-leading RHS with cross-quote value form.
fixture="$(make_fixture N94)"
N94_KEY_A='clientSec'
N94_KEY_B='ret'
N94_VALUE='!fixture-sensitive-value'
{
  printf '%s' '"'
  printf '%s%s' "$N94_KEY_A" "$N94_KEY_B"
  printf '%s' '"'
  printf '%s' ': '
  printf '%s' "'"
  printf '%s' "$N94_VALUE"
  printf '%s\n' "'"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N94" \
  "punctuation-leading cross-quoted RHS rejected without value disclosure" \
  "$fixture" \
  "$N94_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N95. Punctuation-leading RHS for a single-quoted key.
fixture="$(make_fixture N95)"
N95_KEY_A='clientSec'
N95_KEY_B='ret'
N95_VALUE='_fixture-sensitive-value'
{
  printf '%s' "'"
  printf '%s%s' "$N95_KEY_A" "$N95_KEY_B"
  printf '%s' "'"
  printf '%s' ': '
  printf '%s' '"'
  printf '%s' "$N95_VALUE"
  printf '%s\n' '"'
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N95" \
  "punctuation-leading RHS for single-quoted key rejected without value disclosure" \
  "$fixture" \
  "$N95_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N96. Unquoted snake_case comma-leading RHS must not collapse to clean empty.
fixture="$(make_fixture N96)"
N96_KEY_A='client_'
N96_KEY_B='sec'
N96_KEY_C='ret'
N96_VALUE=',fixture-sensitive-value'
{
  printf '%s%s%s' "$N96_KEY_A" "$N96_KEY_B" "$N96_KEY_C"
  printf '%s' '='
  printf '%s\n' "$N96_VALUE"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N96" \
  "comma-leading unquoted snake-case RHS rejected without value disclosure" \
  "$fixture" \
  "$N96_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N97. Only the approved GPT section title is mutated.
fixture="$(make_fixture N97)"
rewrite_with_awk \
  "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
  'BEGIN{changed=0}
   $0=="## 3. Review stage별 핵심 입력" && !changed{
     print "## 3. 다른 제목"
     changed=1
     next
   }
   {print}
   END{if(!changed) exit 2}'
expect_fail \
  "N97" \
  "non-approved GPT section title rejected" \
  "$fixture" \
  0 \
  "GPT review stage section heading count must be exactly 1"

rewrite_with_awk_decoy() {
  file="$1"
  decoy="$2"
  program="$3"

  tmp="$(mktemp "$WORK_ROOT/rewrite-decoy.XXXXXX")" ||
    return 1

  if ! awk -v decoy="$decoy" "$program" "$file" > "$tmp"
  then
    rm -f "$tmp"
    return 1
  fi

  cat "$tmp" > "$file" ||
    return 1

  rm -f "$tmp"
}

# N98. History row survives only as a fenced decoy.
fixture="$(make_fixture N98)"
N98_ROW="$(
  awk '
    /^## 전체 TASK 이력$/{inside=1;next}
    inside && /^## /{inside=0}
    inside && /^\| TASK-059 \|/{print;exit}
  ' "$fixture/docs/process/PORTFOLIO-ROADMAP.md"
)"
if [ -z "$N98_ROW" ]; then
  record_fail "N98 history fixture setup failed"
else
  rewrite_with_awk_decoy \
    "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
    "$N98_ROW" \
    'BEGIN{inside=0;removed=0;inserted=0}
     $0=="## 전체 TASK 이력"{
       inside=1
       print
       next
     }
     inside && /^## /{
       if(!inserted){
         print ""
         print "```text"
         print decoy
         print "```"
         inserted=1
       }
       inside=0
       print
       next
     }
     inside && $0==decoy && !removed{
       removed=1
       next
     }
     {print}
     END{if(!removed || !inserted) exit 2}' ||
    record_fail "N98 history fixture rewrite failed"

  expect_fail \
    "N98" \
    "Roadmap history fenced-row decoy rejected" \
    "$fixture" \
    0 \
    "Roadmap full history TASK row count is not 76"
fi

# N99. Current-status row survives only as a fenced decoy.
fixture="$(make_fixture N99)"
N99_ROW="$(
  awk '
    /^## 현재 확인 상태$/{inside=1;next}
    inside && /^## /{inside=0}
    inside && /^\| TASK-059 \|/{print;exit}
  ' "$fixture/docs/process/PORTFOLIO-ROADMAP.md"
)"
if [ -z "$N99_ROW" ]; then
  record_fail "N99 current-status fixture setup failed"
else
  rewrite_with_awk_decoy \
    "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
    "$N99_ROW" \
    'BEGIN{inside=0;removed=0;inserted=0}
     $0=="## 현재 확인 상태"{
       inside=1
       print
       next
     }
     inside && /^## /{
       if(!inserted){
         print ""
         print "```text"
         print decoy
         print "```"
         inserted=1
       }
       inside=0
       print
       next
     }
     inside && $0==decoy && !removed{
       removed=1
       next
     }
     {print}
     END{if(!removed || !inserted) exit 2}' ||
    record_fail "N99 current-status fixture rewrite failed"

  expect_fail \
    "N99" \
    "Roadmap current-status fenced-row decoy rejected" \
    "$fixture" \
    0 \
    "Roadmap current-status TASK-059/TASK-060 state consistency failed"
fi

# N100. History row survives only as a visible off-table decoy.
fixture="$(make_fixture N100)"
N100_ROW="$(
  awk '
    /^## 전체 TASK 이력$/{inside=1;next}
    inside && /^## /{inside=0}
    inside && /^\| TASK-059 \|/{print;exit}
  ' "$fixture/docs/process/PORTFOLIO-ROADMAP.md"
)"
if [ -z "$N100_ROW" ]; then
  record_fail "N100 history fixture setup failed"
else
  rewrite_with_awk_decoy \
    "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
    "$N100_ROW" \
    'BEGIN{inside=0;removed=0;inserted=0}
     $0=="## 전체 TASK 이력"{
       inside=1
       print
       next
     }
     inside && /^## /{
       if(!inserted){
         print ""
         print decoy
         inserted=1
       }
       inside=0
       print
       next
     }
     inside && $0==decoy && !removed{
       removed=1
       next
     }
     {print}
     END{if(!removed || !inserted) exit 2}' ||
    record_fail "N100 history fixture rewrite failed"

  expect_fail \
    "N100" \
    "Roadmap history visible off-table decoy rejected" \
    "$fixture" \
    0 \
    "Roadmap full history TASK row count is not 76"
fi

# N101. Current-status row survives only as a visible off-table decoy.
fixture="$(make_fixture N101)"
N101_ROW="$(
  awk '
    /^## 현재 확인 상태$/{inside=1;next}
    inside && /^## /{inside=0}
    inside && /^\| TASK-059 \|/{print;exit}
  ' "$fixture/docs/process/PORTFOLIO-ROADMAP.md"
)"
if [ -z "$N101_ROW" ]; then
  record_fail "N101 current-status fixture setup failed"
else
  rewrite_with_awk_decoy \
    "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
    "$N101_ROW" \
    'BEGIN{inside=0;removed=0;inserted=0}
     $0=="## 현재 확인 상태"{
       inside=1
       print
       next
     }
     inside && /^## /{
       if(!inserted){
         print ""
         print decoy
         inserted=1
       }
       inside=0
       print
       next
     }
     inside && $0==decoy && !removed{
       removed=1
       next
     }
     {print}
     END{if(!removed || !inserted) exit 2}' ||
    record_fail "N101 current-status fixture rewrite failed"

  expect_fail \
    "N101" \
    "Roadmap current-status visible off-table decoy rejected" \
    "$fixture" \
    0 \
    "Roadmap current-status TASK-059/TASK-060 state consistency failed"
fi

# N102. Generic non-empty literal RHS remains a conservative candidate.
fixture="$(make_fixture N102)"
N102_KEY_A='clientSec'
N102_KEY_B='ret'
N102_VALUE='placeholder'
{
  printf '%s%s' "$N102_KEY_A" "$N102_KEY_B"
  printf '%s' ': '
  printf '%s\n' "$N102_VALUE"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N102" \
  "generic literal RHS rejected without value disclosure" \
  "$fixture" \
  "$N102_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N103. PROJECT-RULES structured-placeholder policy is mandatory.
fixture="$(make_fixture N103)"
rewrite_with_awk \
  "$fixture/docs/process/PROJECT-RULES.md" \
  'BEGIN{changed=0}
   $0=="- 민감 키 assignment의 false-positive 예외는 normalized 전체 RHS가 `${NAME}` 또는 `<NAME>`인 structured placeholder인 경우로만 제한한다." && !changed{
     print "- structured placeholder 정책이 제거된 fixture"
     changed=1
     next
   }
   {print}
   END{if(!changed) exit 2}'
expect_fail \
  "N103" \
  "PROJECT-RULES structured-placeholder policy mutation rejected" \
  "$fixture" \
  0 \
  "PROJECT-RULES structured placeholder policy missing or duplicated"

rewrite_task_start_duplicate_table() {
  file="$1"
  header="$2"
  separator="$3"
  old_row="$4"
  new_row="$5"

  tmp="$(mktemp "$WORK_ROOT/task-start-duplicate.XXXXXX")" ||
    return 1

  if ! awk \
    -v header="$header" \
    -v separator="$separator" \
    -v old_row="$old_row" \
    -v new_row="$new_row" '
    BEGIN{
      inside=0
      changed=0
      inserted=0
    }

    $0=="## 2. 시작 상태"{
      inside=1
      print
      next
    }

    inside && /^## /{
      if(!inserted){
        print ""
        print header
        print separator
        print new_row
        print ""
        inserted=1
      }

      inside=0
      print
      next
    }

    inside && $0==new_row && !changed{
      print old_row
      changed=1
      next
    }

    {
      print
    }

    END{
      if(!changed || !inserted) exit 2
    }
  ' "$file" > "$tmp"
  then
    rm -f "$tmp"
    return 1
  fi

  cat "$tmp" > "$file" ||
    return 1

  rm -f "$tmp"
}

# N104. Duplicate canonical TASK-START table must not satisfy blocker contract.
fixture="$(make_fixture N104)"

if rewrite_task_start_duplicate_table \
  "$fixture/docs/process/TASK-START-CHECKLIST.md" \
  '| 항목 | 확인 내용 |' \
  '| --- | --- |' \
  '| blockers | 입력 누락, 상태 불일치, Critical 등 중단 조건 |' \
  '| blockers | 핵심 입력 누락, 상태 불일치, Critical 등 중단 조건 |'
then
  expect_fail \
    "N104" \
    "duplicate canonical TASK-START table rejected" \
    "$fixture" \
    0 \
    "TASK-START-CHECKLIST structured table topology mismatch"
else
  record_fail \
    "N104 duplicate TASK-START table fixture setup failed"
fi

# N105. Structured-placeholder-looking prefix/suffix is not a safe exact value.
fixture="$(make_fixture N105)"
N105_KEY_A='clientSec'
N105_KEY_B='ret'
N105_DELIM='='
N105_VALUE_PREFIX='${CLIENT_SECRET}'
N105_VALUE_SUFFIX='suffix'
N105_VALUE="$N105_VALUE_PREFIX$N105_VALUE_SUFFIX"
{
  printf '%s%s' "$N105_KEY_A" "$N105_KEY_B"
  printf '%s' "$N105_DELIM"
  printf '%s\n' "$N105_VALUE"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N105" \
  "structured-placeholder prefix with suffix rejected without value disclosure" \
  "$fixture" \
  "$N105_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N106. Hyphen-prefixed sensitive key with equals must be rejected.
fixture="$(make_fixture N106)"
N106_KEY_A='client-'
N106_KEY_B='sec'
N106_KEY_C='ret'
N106_VALUE='fixture-sensitive-value'
{
  printf '%s%s%s' \
    "$N106_KEY_A" \
    "$N106_KEY_B" \
    "$N106_KEY_C"
  printf '%s' '='
  printf '%s\n' "$N106_VALUE"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N106" \
  "hyphen-prefixed sensitive equals assignment rejected without value disclosure" \
  "$fixture" \
  "$N106_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N107. Quoted hyphen-prefixed sensitive key with whitespace-colon must fail.
fixture="$(make_fixture N107)"
N107_KEY_A='db-'
N107_KEY_B='pass'
N107_KEY_C='word'
N107_VALUE='fixture-sensitive-value'
{
  printf '%s' '"'
  printf '%s%s%s' \
    "$N107_KEY_A" \
    "$N107_KEY_B" \
    "$N107_KEY_C"
  printf '%s' '"'
  printf '%s' ': '
  printf '%s\n' "$N107_VALUE"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N107" \
  "quoted hyphen-prefixed sensitive colon assignment rejected without value disclosure" \
  "$fixture" \
  "$N107_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N108. required input row survives only as an off-table visible decoy.
fixture="$(make_fixture N108)"
N108_ROW="$(
  awk -F'|' '
    function t(s) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
      return s
    }

    t($2) == "required input" {
      print
      exit
    }
  ' "$fixture/docs/process/TASK-START-CHECKLIST.md"
)"

if [ -z "$N108_ROW" ]; then
  record_fail "N108 TASK-START required-input row fixture setup failed"
else
  rewrite_with_awk_decoy \
    "$fixture/docs/process/TASK-START-CHECKLIST.md" \
    "$N108_ROW" \
    'BEGIN {
       inside=0
       changed=0
       inserted=0
     }

     $0=="## 2. 시작 상태" {
       inside=1
       print
       next
     }

     inside && /^## / {
       if (!inserted) {
         print ""
         print decoy
         print ""
         inserted=1
       }

       inside=0
       print
       next
     }

     inside && $0==decoy && !changed {
       print "| required input | fixture-mutated canonical value |"
       changed=1
       next
     }

     {
       print
     }

     END {
       if (!changed || !inserted) exit 2
     }' ||
    record_fail "N108 TASK-START fixture rewrite failed"

  expect_fail \
    "N108" \
    "TASK-START canonical seven-row contract rejects off-table required-input decoy" \
    "$fixture" \
    0 \
    "TASK-START-CHECKLIST canonical 7-row structured-state contract mismatch"
fi

# N109. Plan / Issue required core input survives only off-core in same stage.
fixture="$(make_fixture N109)"
rewrite_with_awk \
  "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
  'BEGIN {
     inside=0
     removed=0
     decoy=0
   }

   $0=="### Plan / Issue" {
     inside=1
     print
     next
   }

   $0=="### Implementation" {
     inside=0
     print
     next
   }

   inside && $0=="- Scope" && !removed {
     removed=1
     next
   }

   inside &&
   /^검토 목적은 / &&
   !decoy {
     print
     print ""
     print "- Scope"
     decoy=1
     next
   }

   {
     print
   }

   END {
     if (!removed || !decoy) exit 2
   }' ||
  record_fail "N109 GPT same-stage off-core fixture rewrite failed"

expect_fail \
  "N109" \
  "GPT same-stage off-core core-input decoy rejected" \
  "$fixture" \
  0 \
  "GPT Plan / Issue core input list mismatch"

# N110. Roadmap history membership changes while row count/status stay intact.
fixture="$(make_fixture N110)"
N110_FILE="$fixture/docs/process/PORTFOLIO-ROADMAP.md"

if ! N110_PRESTATE="$(
  awk -F'|' '
    function t(s) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
      return s
    }

    BEGIN {
      inside=0
      rows=0
      fake=0
      task059=0
      task060=0
      selected_count=0
      selected_id=""
      selected_row=""
      row059=""
      row060=""
    }

    $0=="## 전체 TASK 이력" {
      inside=1
      next
    }

    inside && /^## / {
      inside=0
    }

    inside && /^\| TASK-[0-9]+(-[0-9]+)? \|/ {
      rows++
      id=t($2)

      if (id=="TASK-9999") {
        fake++
      }

      if (id=="TASK-059") {
        task059++
        row059=$0
        next
      }

      if (id=="TASK-060") {
        task060++
        row060=$0
        next
      }

      if (selected_id=="") {
        selected_id=id
        selected_row=$0
      }

      if (id==selected_id) {
        selected_count++
      }
    }

    END {
      if (rows <= 0 ||
          fake != 0 ||
          selected_id == "" ||
          selected_count != 1 ||
          task059 != 1 ||
          task060 != 1) {
        exit 2
      }

      print rows
      print selected_id
      print selected_row
      print row059
      print row060
    }
  ' "$N110_FILE"
)"
then
  record_fail "N110 Roadmap history fixture setup failed"
else
  N110_HISTORY_ROWS_BEFORE="$(
    printf '%s\n' "$N110_PRESTATE" |
      awk 'NR==1 {print; exit}'
  )"

  N110_ORIGINAL_ID="$(
    printf '%s\n' "$N110_PRESTATE" |
      awk 'NR==2 {print; exit}'
  )"

  N110_ORIGINAL_ROW="$(
    printf '%s\n' "$N110_PRESTATE" |
      awk 'NR==3 {print; exit}'
  )"

  N110_TASK059_ROW_BEFORE="$(
    printf '%s\n' "$N110_PRESTATE" |
      awk 'NR==4 {print; exit}'
  )"

  N110_TASK060_ROW_BEFORE="$(
    printf '%s\n' "$N110_PRESTATE" |
      awk 'NR==5 {print; exit}'
  )"

  if ! N110_EXPECTED_ROW="$(
    printf '%s\n' "$N110_ORIGINAL_ROW" |
      awk '
        {
          changed=sub(/^\| TASK-[0-9]+(-[0-9]+)? \|/, "| TASK-9999 |")

          if (changed != 1) {
            exit 2
          }

          print
        }
      '
  )"
  then
    record_fail "N110 expected mutated row construction failed"
  else
    N110_MUTATION_PROGRAM='BEGIN {
       inside=0
       seen=0
       changed=0
     }

     $0=="## 전체 TASK 이력" {
       inside=1
       print
       next
     }

     inside && /^## / {
       inside=0
       print
       next
     }

     inside && /^\| TASK-[0-9]+(-[0-9]+)? \|/ && $0 !~ /^\| TASK-059 \|/ && $0 !~ /^\| TASK-060 \|/ && seen==0 {
       seen++
       changed+=sub(/^\| TASK-[0-9]+(-[0-9]+)? \|/, "| TASK-9999 |")
     }

     {
       print
     }

     END {
       if (seen != 1 || changed != 1) exit 2
     }'

    if ! rewrite_with_awk \
      "$N110_FILE" \
      "$N110_MUTATION_PROGRAM"
    then
      record_fail "N110 Roadmap history membership fixture rewrite failed"
    elif ! N110_POSTSTATE="$(
      awk -F'|' \
        -v original="$N110_ORIGINAL_ID" '
        function t(s) {
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
          return s
        }

        BEGIN {
          inside=0
          rows=0
          fake=0
          original_count=0
          task059=0
          task060=0
          fake_row=""
          row059=""
          row060=""
        }

        $0=="## 전체 TASK 이력" {
          inside=1
          next
        }

        inside && /^## / {
          inside=0
        }

        inside && /^\| TASK-[0-9]+(-[0-9]+)? \|/ {
          rows++
          id=t($2)

          if (id=="TASK-9999") {
            fake++
            fake_row=$0
          }

          if (id==original) {
            original_count++
          }

          if (id=="TASK-059") {
            task059++
            row059=$0
          }

          if (id=="TASK-060") {
            task060++
            row060=$0
          }
        }

        END {
          print rows
          print fake
          print fake_row
          print original_count
          print task059
          print row059
          print task060
          print row060
        }
      ' "$N110_FILE"
    )"
    then
      record_fail "N110 Roadmap history mutation postcondition failed"
    else
      N110_HISTORY_ROWS_AFTER="$(
        printf '%s\n' "$N110_POSTSTATE" |
          awk 'NR==1 {print; exit}'
      )"

      N110_FAKE_COUNT="$(
        printf '%s\n' "$N110_POSTSTATE" |
          awk 'NR==2 {print; exit}'
      )"

      N110_FAKE_ROW="$(
        printf '%s\n' "$N110_POSTSTATE" |
          awk 'NR==3 {print; exit}'
      )"

      N110_ORIGINAL_COUNT_AFTER="$(
        printf '%s\n' "$N110_POSTSTATE" |
          awk 'NR==4 {print; exit}'
      )"

      N110_TASK059_COUNT_AFTER="$(
        printf '%s\n' "$N110_POSTSTATE" |
          awk 'NR==5 {print; exit}'
      )"

      N110_TASK059_ROW_AFTER="$(
        printf '%s\n' "$N110_POSTSTATE" |
          awk 'NR==6 {print; exit}'
      )"

      N110_TASK060_COUNT_AFTER="$(
        printf '%s\n' "$N110_POSTSTATE" |
          awk 'NR==7 {print; exit}'
      )"

      N110_TASK060_ROW_AFTER="$(
        printf '%s\n' "$N110_POSTSTATE" |
          awk 'NR==8 {print; exit}'
      )"

      if [ "$N110_HISTORY_ROWS_AFTER" -ne "$N110_HISTORY_ROWS_BEFORE" ] ||
         [ "$N110_FAKE_COUNT" -ne 1 ] ||
         [ "$N110_ORIGINAL_COUNT_AFTER" -ne 0 ] ||
         [ "$N110_TASK059_COUNT_AFTER" -ne 1 ] ||
         [ "$N110_TASK060_COUNT_AFTER" -ne 1 ] ||
         [ "$N110_FAKE_ROW" != "$N110_EXPECTED_ROW" ] ||
         [ "$N110_TASK059_ROW_AFTER" != "$N110_TASK059_ROW_BEFORE" ] ||
         [ "$N110_TASK060_ROW_AFTER" != "$N110_TASK060_ROW_BEFORE" ]
      then
        record_fail "N110 Roadmap history mutation postcondition failed"
      else
        expect_fail \
          "N110" \
          "Roadmap exact history TASK membership mutation rejected" \
          "$fixture" \
          0 \
          "Roadmap history TASK ID membership mismatch"
      fi
    fi
  fi
fi

# N111. Current-status row count remains three but TASK-031 membership changes.
fixture="$(make_fixture N111)"
if grep -Fq \
  '| TASK-9999 |' \
  "$fixture/docs/process/PORTFOLIO-ROADMAP.md"
then
  record_fail "N111 reserved fake TASK ID already exists"
else
  rewrite_with_awk \
    "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
    'BEGIN {
       inside=0
       changed=0
     }

     $0=="## 현재 확인 상태" {
       inside=1
       print
       next
     }

     inside && /^## / {
       inside=0
       print
       next
     }

     inside &&
     /^\| TASK-031 \|/ &&
     !changed {
       sub(/^\| TASK-031 \|/, "| TASK-9999 |")
       changed=1
     }

     {
       print
     }

     END {
       if (!changed) exit 2
     }' ||
    record_fail "N111 Roadmap current membership fixture rewrite failed"

  expect_fail \
    "N111" \
    "Roadmap exact current-status TASK membership mutation rejected" \
    "$fixture" \
    0 \
    "Roadmap current-status TASK ID membership mismatch"
fi

# N112. TASK-032 authoritative table row is mutated; approved row is off-table.
fixture="$(make_fixture N112)"
N112_FILE="$fixture/docs/process/PORTFOLIO-ROADMAP.md"
N112_TMP="$fixture/docs/process/.N112-roadmap.tmp"

N112_SECTION='## 다음 직접 진행 순서'
N112_HEADER='| TASK | 내용 | 상태 | 선행 조건 |'
N112_SEPARATOR='| --- | --- | --- | --- |'

N112_ROW='| TASK-032 | 이벤트 기반 예약 처리 (Spring Events) | 계획 | TASK-060 완료 + 사용자 승인 |'
N112_MUTATED_ROW='| TASK-032 | 이벤트 기반 예약 처리 (Spring Events) | 계획 | TASK-060 완료 + fixture 승인 |'

N112_DIRECT_ORDER_EXPECTED='```text
TASK-032
→ TASK-033
→ TASK-033-1
→ TASK-028
→ TASK-043
```'

N112_REWRITE_OK=1
N112_POST_OK=1

N112_INITIAL_CANONICAL_COUNT="$(
  awk -v expected="$N112_ROW" '
    $0 == expected {
      count++
    }

    END {
      print count + 0
    }
  ' "$N112_FILE"
)"

N112_INITIAL_MUTATED_COUNT="$(
  awk -v expected="$N112_MUTATED_ROW" '
    $0 == expected {
      count++
    }

    END {
      print count + 0
    }
  ' "$N112_FILE"
)"

N112_DIRECT_ORDER_BEFORE="$(
  awk -v section="$N112_SECTION" '
    $0 == section {
      inside=1
      next
    }

    inside && /^## / {
      inside=0
    }

    !inside {
      next
    }

    !capture && $0 == "```text" {
      capture=1
      print
      next
    }

    capture {
      print

      if ($0 == "```") {
        exit
      }
    }
  ' "$N112_FILE"
)"

if [ "$N112_INITIAL_CANONICAL_COUNT" -ne 1 ] ||
   [ "$N112_INITIAL_MUTATED_COUNT" -ne 0 ] ||
   [ "$N112_DIRECT_ORDER_BEFORE" != "$N112_DIRECT_ORDER_EXPECTED" ]; then
  N112_REWRITE_OK=0
fi

if [ "$N112_REWRITE_OK" -eq 1 ]; then
  if awk \
    -v section="$N112_SECTION" \
    -v header="$N112_HEADER" \
    -v separator="$N112_SEPARATOR" \
    -v canonical="$N112_ROW" \
    -v mutated="$N112_MUTATED_ROW" '
    BEGIN {
      inside=0
      section_count=0
      header_count=0
      separator_count=0
      expect_separator=0
      in_table=0
      table_done=0
      target_count=0
      changed=0
      inserted=0
      bad=0
    }

    $0 == section {
      section_count++
      inside=1
      print
      next
    }

    inside && /^## / {
      if (in_table && !table_done) {
        in_table=0
        table_done=1

        if (!inserted) {
          print ""
          print canonical
          print ""
          inserted=1
        }
      }

      inside=0
      print
      next
    }

    !inside {
      print
      next
    }

    $0 == header {
      header_count++

      if (header_count != 1 || in_table) {
        bad=1
      }

      expect_separator=1
      print
      next
    }

    expect_separator {
      expect_separator=0

      if ($0 != separator) {
        bad=1
        print
        next
      }

      separator_count++
      in_table=1
      print
      next
    }

    in_table {
      if ($0 ~ /^\| TASK-[0-9]+(-[0-9]+)? \|/) {
        if ($0 == canonical) {
          target_count++
          print mutated
          changed++
          next
        }

        print
        next
      }

      in_table=0
      table_done=1

      if (!inserted) {
        print ""
        print canonical
        print ""
        inserted=1
      }

      print
      next
    }

    {
      print
    }

    END {
      if (section_count != 1 ||
          header_count != 1 ||
          separator_count != 1 ||
          expect_separator ||
          target_count != 1 ||
          changed != 1 ||
          inserted != 1 ||
          table_done != 1 ||
          bad) {
        exit 2
      }
    }
  ' "$N112_FILE" > "$N112_TMP"
  then
    if mv "$N112_TMP" "$N112_FILE"; then
      :
    else
      rm -f "$N112_TMP"
      N112_REWRITE_OK=0
    fi
  else
    rm -f "$N112_TMP"
    N112_REWRITE_OK=0
  fi
fi

if [ "$N112_REWRITE_OK" -eq 1 ]; then
  N112_CANONICAL_COUNT="$(
    awk -v expected="$N112_ROW" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    ' "$N112_FILE"
  )"

  N112_MUTATED_COUNT="$(
    awk -v expected="$N112_MUTATED_ROW" '
      $0 == expected {
        count++
      }

      END {
        print count + 0
      }
    ' "$N112_FILE"
  )"

  if [ "$N112_CANONICAL_COUNT" -ne 1 ] ||
     [ "$N112_MUTATED_COUNT" -ne 1 ]; then
    N112_POST_OK=0
  fi

  if ! awk \
    -v section="$N112_SECTION" \
    -v header="$N112_HEADER" \
    -v separator="$N112_SEPARATOR" \
    -v canonical="$N112_ROW" \
    -v mutated="$N112_MUTATED_ROW" '
    BEGIN {
      inside=0
      section_count=0
      header_count=0
      separator_count=0
      expect_separator=0
      in_table=0
      table_done=0
      canonical_count=0
      mutated_count=0
      bad=0
    }

    $0 == section {
      section_count++
      inside=1
      next
    }

    inside && /^## / {
      if (in_table) {
        in_table=0
        table_done++
      }

      inside=0
      next
    }

    !inside {
      next
    }

    $0 == header {
      header_count++

      if (header_count != 1 || in_table) {
        bad=1
      }

      expect_separator=1
      next
    }

    expect_separator {
      expect_separator=0

      if ($0 != separator) {
        bad=1
        next
      }

      separator_count++
      in_table=1
      next
    }

    in_table {
      if ($0 ~ /^\| TASK-[0-9]+(-[0-9]+)? \|/) {
        if ($0 == canonical) {
          canonical_count++
        }

        if ($0 == mutated) {
          mutated_count++
        }

        next
      }

      in_table=0
      table_done++
    }

    END {
      if (section_count != 1 ||
          header_count != 1 ||
          separator_count != 1 ||
          expect_separator ||
          table_done != 1 ||
          canonical_count != 0 ||
          mutated_count != 1 ||
          bad) {
        exit 2
      }
    }
  ' "$N112_FILE"
  then
    N112_POST_OK=0
  fi

  if ! awk \
    -v section="$N112_SECTION" \
    -v header="$N112_HEADER" \
    -v separator="$N112_SEPARATOR" \
    -v canonical="$N112_ROW" '
    BEGIN {
      inside=0
      fence=0
      expect_separator=0
      in_table=0
      visible_off_table=0
      section_count=0
    }

    $0 == section {
      section_count++
      inside=1
      next
    }

    inside && !fence && /^## / {
      inside=0
      in_table=0
      next
    }

    !inside {
      next
    }

    /^```/ {
      if (fence) {
        fence=0
      } else {
        fence=1
      }

      next
    }

    fence {
      next
    }

    $0 == header {
      expect_separator=1
      next
    }

    expect_separator {
      expect_separator=0

      if ($0 == separator) {
        in_table=1
      }

      next
    }

    in_table {
      if ($0 ~ /^\| TASK-[0-9]+(-[0-9]+)? \|/) {
        next
      }

      in_table=0
    }

    !in_table && $0 == canonical {
      visible_off_table++
    }

    END {
      if (section_count != 1 ||
          visible_off_table != 1 ||
          fence) {
        exit 2
      }
    }
  ' "$N112_FILE"
  then
    N112_POST_OK=0
  fi

  N112_DIRECT_ORDER_AFTER="$(
    awk -v section="$N112_SECTION" '
      $0 == section {
        inside=1
        next
      }

      inside && /^## / {
        inside=0
      }

      !inside {
        next
      }

      !capture && $0 == "```text" {
        capture=1
        print
        next
      }

      capture {
        print

        if ($0 == "```") {
          exit
        }
      }
    ' "$N112_FILE"
  )"

  if [ "$N112_DIRECT_ORDER_AFTER" != "$N112_DIRECT_ORDER_EXPECTED" ] ||
     [ "$N112_DIRECT_ORDER_AFTER" != "$N112_DIRECT_ORDER_BEFORE" ]; then
    N112_POST_OK=0
  fi
fi

if [ "$N112_REWRITE_OK" -eq 1 ] &&
   [ "$N112_POST_OK" -eq 1 ]; then
  expect_fail \
    "N112" \
    "Roadmap TASK-032 off-table prerequisite decoy rejected" \
    "$fixture" \
    0 \
    "Roadmap TASK-032 prerequisite mismatch"
else
  if [ "$N112_REWRITE_OK" -ne 1 ]; then
    record_fail \
      "N112 TASK-032 prerequisite fixture rewrite/setup failed"
  else
    record_fail \
      "N112 TASK-032 prerequisite fixture postcondition failed"
  fi
fi

# N113. Migration Matrix row moved from authoritative table into fence.
fixture="$(make_fixture N113)"
N113_ROW="$(
  awk '
    /^## 4\. 684-row Migration Matrix$/ {
      inside=1
      next
    }

    inside && /^## / {
      inside=0
    }

    inside && /^\| 1 \|/ {
      print
      exit
    }
  ' "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md"
)"

if [ -z "$N113_ROW" ]; then
  record_fail "N113 Migration Matrix fixture setup failed"
else
  rewrite_with_awk_decoy \
    "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
    "$N113_ROW" \
    'BEGIN {
       inside=0
       removed=0
     }

     $0=="## 4. 684-row Migration Matrix" {
       inside=1
       print
       next
     }

     inside && /^## / {
       inside=0
       print
       next
     }

     inside && $0==decoy && !removed {
       removed=1
       next
     }

     {
       print
     }

     END {
       if (!removed) exit 2
     }' ||
    record_fail "N113 Migration Matrix fixture rewrite failed"

  {
    printf '%s\n' ''
    printf '%s\n' '```text'
    printf '%s\n' "$N113_ROW"
    printf '%s\n' '```'
  } >> "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md"

  expect_fail \
    "N113" \
    "Migration Matrix fenced moved-row decoy rejected" \
    "$fixture" \
    0 \
    "Migration Matrix data row count is not 684"
fi

# N114. Migration Matrix row moved to visible location outside table.
fixture="$(make_fixture N114)"
N114_ROW="$(
  awk '
    /^## 4\. 684-row Migration Matrix$/ {
      inside=1
      next
    }

    inside && /^## / {
      inside=0
    }

    inside && /^\| 1 \|/ {
      print
      exit
    }
  ' "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md"
)"

if [ -z "$N114_ROW" ]; then
  record_fail "N114 Migration Matrix fixture setup failed"
else
  rewrite_with_awk_decoy \
    "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
    "$N114_ROW" \
    'BEGIN {
       inside=0
       removed=0
     }

     $0=="## 4. 684-row Migration Matrix" {
       inside=1
       print
       next
     }

     inside && /^## / {
       inside=0
       print
       next
     }

     inside && $0==decoy && !removed {
       removed=1
       next
     }

     {
       print
     }

     END {
       if (!removed) exit 2
     }' ||
    record_fail "N114 Migration Matrix fixture rewrite failed"

  {
    printf '%s\n' ''
    printf '%s\n' "$N114_ROW"
  } >> "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md"

  expect_fail \
    "N114" \
    "Migration Matrix visible off-table moved-row decoy rejected" \
    "$fixture" \
    0 \
    "Migration Matrix data row count is not 684"
fi


# ---------------------------------------------------------------------------
# X-2A Finding 1-4 regression helpers.
# ---------------------------------------------------------------------------

x2a_exact_line_count() {
  file="$1"
  expected="$2"

  awk \
    -v expected="$expected" '
    $0 == expected {
      count++
    }

    END {
      print count + 0
    }
  ' "$file"
}

x2a_replace_exact_line() {
  file="$1"
  old="$2"
  new="$3"

  tmp="$(mktemp "$WORK_ROOT/x2a-line.XXXXXX")" ||
    return 1

  if ! awk \
    -v old="$old" \
    -v new="$new" '
    BEGIN {
      changed=0
    }

    $0 == old {
      changed++
      print new
      next
    }

    {
      print
    }

    END {
      if (changed != 1) {
        exit 2
      }
    }
  ' "$file" > "$tmp"
  then
    rm -f "$tmp"
    return 1
  fi

  if ! cat "$tmp" > "$file"; then
    rm -f "$tmp"
    return 1
  fi

  rm -f "$tmp"
}

x2a_append_project_fence_decoy() {
  file="$1"
  subsection="$2"
  label="$3"
  fence_open="$4"
  payload="$5"

  case "$fence_open" in
    '```'*)
      fence_close='```'
      ;;
    '~~~'*)
      fence_close='~~~'
      ;;
    *)
      return 1
      ;;
  esac

  {
    printf '\n%s\n' \
      '## X2A fixture decoy'
    printf '%s\n' \
      "$subsection"
    printf '%s\n\n' \
      "$label"
    printf '%s\n' \
      "$fence_open"
    printf '%s\n' \
      "$payload"
    printf '%s\n' \
      "$fence_close"
  } >> "$file"
}

x2a_project_line_decoy_postcondition() {
  file="$1"
  canonical="$2"
  mutated="$3"

  canonical_count="$(
    x2a_exact_line_count \
      "$file" \
      "$canonical"
  )"

  mutated_count="$(
    x2a_exact_line_count \
      "$file" \
      "$mutated"
  )"

  [ "$canonical_count" -eq 1 ] &&
    [ "$mutated_count" -eq 1 ]
}

x2a_insert_gpt_nested_heading() {
  file="$1"
  stage="$2"

  tmp="$(mktemp "$WORK_ROOT/x2a-gpt-nested.XXXXXX")" ||
    return 1

  if ! awk \
    -v target="### $stage" '
    function marker_of(line) {
      if (substr(line, 1, 3) == "```") {
        return "```"
      }

      if (substr(line, 1, 3) == "~~~") {
        return "~~~"
      }

      return ""
    }

    BEGIN {
      heading_count=0
      marker_count=0
      inside=0
      inserted=0
      fence=""
    }

    {
      line=$0

      if (fence != "") {
        print

        if (line == fence) {
          fence=""
        }

        next
      }

      marker=marker_of(line)

      if (marker != "") {
        fence=marker
        print
        next
      }

      if (line == target) {
        heading_count++
        inside=1
        print
        next
      }

      if (inside &&
          line ~ /^### /) {
        inside=0
      }

      if (inside &&
          line == "핵심 입력:") {
        marker_count++

        if (!inserted) {
          print "#### fixture subsection"
          inserted=1
        }
      }

      print
    }

    END {
      if (fence != "" ||
          heading_count != 1 ||
          marker_count != 1 ||
          inserted != 1) {
        exit 2
      }
    }
  ' "$file" > "$tmp"
  then
    rm -f "$tmp"
    return 1
  fi

  if ! cat "$tmp" > "$file"; then
    rm -f "$tmp"
    return 1
  fi

  rm -f "$tmp"
}

x2a_gpt_nested_postcondition() {
  file="$1"
  stage="$2"

  awk \
    -v target="### $stage" '
    function marker_of(line) {
      if (substr(line, 1, 3) == "```") {
        return "```"
      }

      if (substr(line, 1, 3) == "~~~") {
        return "~~~"
      }

      return ""
    }

    BEGIN {
      heading_count=0
      nested_marker_count=0
      inside=0
      fence=""
      previous=""
    }

    {
      line=$0

      if (fence != "") {
        if (line == fence) {
          fence=""
        }

        next
      }

      marker=marker_of(line)

      if (marker != "") {
        fence=marker
        next
      }

      if (line == target) {
        heading_count++
        inside=1
        previous=line
        next
      }

      if (inside &&
          line ~ /^### /) {
        inside=0
      }

      if (inside &&
          previous == "#### fixture subsection" &&
          line == "핵심 입력:") {
        nested_marker_count++
      }

      previous=line
    }

    END {
      if (fence != "" ||
          heading_count != 1 ||
          nested_marker_count != 1) {
        exit 2
      }
    }
  ' "$file"
}

# N115. Mismatched double-opening/single-closing sensitive key.
fixture="$(make_fixture N115)"
N115_KEY_A='client'
N115_KEY_B='Secret'
N115_VALUE_A='fixture'
N115_VALUE_B='-quote-'
N115_VALUE_C='115-value'
N115_VALUE="$N115_VALUE_A$N115_VALUE_B$N115_VALUE_C"
{
  printf '%s' '"'
  printf '%s%s' \
    "$N115_KEY_A" \
    "$N115_KEY_B"
  printf '%s' "'"
  printf '%s' '='
  printf '%s\n' \
    "$N115_VALUE"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N115" \
  "mismatched double-to-single sensitive key rejected without value disclosure" \
  "$fixture" \
  "$N115_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N116. Mismatched single-opening/double-closing snake sensitive key.
fixture="$(make_fixture N116)"
N116_KEY_A='client_'
N116_KEY_B='secret'
N116_VALUE_A='fixture'
N116_VALUE_B='-quote-'
N116_VALUE_C='116-value'
N116_VALUE="$N116_VALUE_A$N116_VALUE_B$N116_VALUE_C"
{
  printf '%s' "'"
  printf '%s%s' \
    "$N116_KEY_A" \
    "$N116_KEY_B"
  printf '%s' '"'
  printf '%s' '='
  printf '%s\n' \
    "$N116_VALUE"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N116" \
  "mismatched single-to-double sensitive key rejected without value disclosure" \
  "$fixture" \
  "$N116_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N117. Unmatched closing double quote after camel sensitive key.
fixture="$(make_fixture N117)"
N117_KEY_A='client'
N117_KEY_B='Secret'
N117_VALUE_A='fixture'
N117_VALUE_B='-quote-'
N117_VALUE_C='117-value'
N117_VALUE="$N117_VALUE_A$N117_VALUE_B$N117_VALUE_C"
{
  printf '%s%s' \
    "$N117_KEY_A" \
    "$N117_KEY_B"
  printf '%s' '"'
  printf '%s' '='
  printf '%s\n' \
    "$N117_VALUE"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N117" \
  "unmatched closing double quote sensitive key rejected without value disclosure" \
  "$fixture" \
  "$N117_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N118. Unmatched closing single quote after hyphen sensitive key.
fixture="$(make_fixture N118)"
N118_KEY_A='db-'
N118_KEY_B='password'
N118_VALUE_A='fixture'
N118_VALUE_B='-quote-'
N118_VALUE_C='118-value'
N118_VALUE="$N118_VALUE_A$N118_VALUE_B$N118_VALUE_C"
{
  printf '%s%s' \
    "$N118_KEY_A" \
    "$N118_KEY_B"
  printf '%s' "'"
  printf '%s' '='
  printf '%s\n' \
    "$N118_VALUE"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N118" \
  "unmatched closing single quote hyphen sensitive key rejected without value disclosure" \
  "$fixture" \
  "$N118_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N119. Opening-only double quote plus compact colon.
fixture="$(make_fixture N119)"
N119_KEY_A='client'
N119_KEY_B='Secret'
N119_VALUE_A='fixture'
N119_VALUE_B='-quote-'
N119_VALUE_C='119-value'
N119_VALUE="$N119_VALUE_A$N119_VALUE_B$N119_VALUE_C"
{
  printf '%s' '"'
  printf '%s%s' \
    "$N119_KEY_A" \
    "$N119_KEY_B"
  printf '%s' ':'
  printf '%s\n' \
    "$N119_VALUE"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N119" \
  "opening-only double quote compact-colon sensitive key rejected without value disclosure" \
  "$fixture" \
  "$N119_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N120. Opening-only single quote plus compact colon and hyphen key.
fixture="$(make_fixture N120)"
N120_KEY_A='db-'
N120_KEY_B='password'
N120_VALUE_A='fixture'
N120_VALUE_B='-quote-'
N120_VALUE_C='120-value'
N120_VALUE="$N120_VALUE_A$N120_VALUE_B$N120_VALUE_C"
{
  printf '%s' "'"
  printf '%s%s' \
    "$N120_KEY_A" \
    "$N120_KEY_B"
  printf '%s' ':'
  printf '%s\n' \
    "$N120_VALUE"
} >> "$fixture/CLAUDE.md"
expect_fail_redacted \
  "N120" \
  "opening-only single quote compact-colon hyphen sensitive key rejected without value disclosure" \
  "$fixture" \
  "$N120_VALUE" \
  0 \
  "sensitive pattern detected in CLAUDE.md"

# N121. Branch canonical payload removed from authoritative subsection.
fixture="$(make_fixture N121)"
N121_FILE="$fixture/docs/process/PROJECT-RULES.md"
N121_OLD='{prefix}/TASK-XXX-english-kebab-case'
N121_NEW='{prefix}/TASK-XXX-fixture-branch'

if [ "$(
  x2a_exact_line_count \
    "$N121_FILE" \
    "$N121_OLD"
)" -ne 1 ]; then
  record_fail \
    "N121 PROJECT-RULES Branch fixture setup failed"
elif ! x2a_replace_exact_line \
  "$N121_FILE" \
  "$N121_OLD" \
  "$N121_NEW"
then
  record_fail \
    "N121 PROJECT-RULES Branch fixture rewrite failed"
elif ! x2a_append_project_fence_decoy \
  "$N121_FILE" \
  "### Branch" \
  "형식:" \
  '```text' \
  "$N121_OLD"
then
  record_fail \
    "N121 PROJECT-RULES Branch fixture decoy setup failed"
elif ! x2a_project_line_decoy_postcondition \
  "$N121_FILE" \
  "$N121_OLD" \
  "$N121_NEW"
then
  record_fail \
    "N121 PROJECT-RULES Branch fixture postcondition failed"
else
  expect_fail \
    "N121" \
    "PROJECT-RULES Branch wrong-parent fenced decoy rejected" \
    "$fixture" \
    0 \
    "PROJECT-RULES branch naming pattern missing"
fi

# N122. Issue canonical payload survives only in wrong-parent fence.
fixture="$(make_fixture N122)"
N122_FILE="$fixture/docs/process/PROJECT-RULES.md"
N122_OLD='TASK-XXX 작업 내용 요약'
N122_NEW='TASK-XXX fixture 작업 내용 요약'

if [ "$(
  x2a_exact_line_count \
    "$N122_FILE" \
    "$N122_OLD"
)" -ne 1 ]; then
  record_fail \
    "N122 PROJECT-RULES Issue fixture setup failed"
elif ! x2a_replace_exact_line \
  "$N122_FILE" \
  "$N122_OLD" \
  "$N122_NEW"
then
  record_fail \
    "N122 PROJECT-RULES Issue fixture rewrite failed"
elif ! x2a_append_project_fence_decoy \
  "$N122_FILE" \
  "### Issue" \
  "제목:" \
  '```text' \
  "$N122_OLD"
then
  record_fail \
    "N122 PROJECT-RULES Issue fixture decoy setup failed"
elif ! x2a_project_line_decoy_postcondition \
  "$N122_FILE" \
  "$N122_OLD" \
  "$N122_NEW"
then
  record_fail \
    "N122 PROJECT-RULES Issue fixture postcondition failed"
else
  expect_fail \
    "N122" \
    "PROJECT-RULES Issue wrong-parent fenced decoy rejected" \
    "$fixture" \
    0 \
    "PROJECT-RULES Issue title pattern missing"
fi

# N123. Commit/PR canonical payload survives only in wrong-parent fence.
fixture="$(make_fixture N123)"
N123_FILE="$fixture/docs/process/PROJECT-RULES.md"
N123_OLD='{prefix}(TASK-XXX): 변경 대상과 구체적인 결과'
N123_NEW='{prefix}(TASK-XXX): fixture 변경 결과'

if [ "$(
  x2a_exact_line_count \
    "$N123_FILE" \
    "$N123_OLD"
)" -ne 1 ]; then
  record_fail \
    "N123 PROJECT-RULES Commit/PR fixture setup failed"
elif ! x2a_replace_exact_line \
  "$N123_FILE" \
  "$N123_OLD" \
  "$N123_NEW"
then
  record_fail \
    "N123 PROJECT-RULES Commit/PR fixture rewrite failed"
elif ! x2a_append_project_fence_decoy \
  "$N123_FILE" \
  "### Commit / PR" \
  "형식:" \
  '```text' \
  "$N123_OLD"
then
  record_fail \
    "N123 PROJECT-RULES Commit/PR fixture decoy setup failed"
elif ! x2a_project_line_decoy_postcondition \
  "$N123_FILE" \
  "$N123_OLD" \
  "$N123_NEW"
then
  record_fail \
    "N123 PROJECT-RULES Commit/PR fixture postcondition failed"
else
  expect_fail \
    "N123" \
    "PROJECT-RULES Commit/PR wrong-parent fenced decoy rejected" \
    "$fixture" \
    0 \
    "PROJECT-RULES Commit/PR pattern missing"
fi

# N124. Allowed-prefix canonical subsection is mutated; exact set is a decoy.
fixture="$(make_fixture N124)"
N124_FILE="$fixture/docs/process/PROJECT-RULES.md"
N124_OLD='- `build`'
N124_NEW='- `fixture-build`'

if [ "$(
  x2a_exact_line_count \
    "$N124_FILE" \
    "$N124_OLD"
)" -ne 1 ]; then
  record_fail \
    "N124 PROJECT-RULES prefix fixture setup failed"
elif ! x2a_replace_exact_line \
  "$N124_FILE" \
  "$N124_OLD" \
  "$N124_NEW"
then
  record_fail \
    "N124 PROJECT-RULES prefix fixture rewrite failed"
else
  {
    printf '\n%s\n' \
      '## X2A prefix fixture decoy'
    printf '%s\n' \
      '### 허용 prefix'
    printf '%s\n' \
      '- `feat`' \
      '- `fix`' \
      '- `refactor`' \
      '- `test`' \
      '- `docs`' \
      '- `chore`' \
      '- `build`'
  } >> "$N124_FILE"

  if ! x2a_project_line_decoy_postcondition \
    "$N124_FILE" \
    "$N124_OLD" \
    "$N124_NEW"
  then
    record_fail \
      "N124 PROJECT-RULES prefix fixture postcondition failed"
  else
    expect_fail \
      "N124" \
      "PROJECT-RULES allowed-prefix wrong-parent decoy rejected" \
      "$fixture" \
      0 \
      "PROJECT-RULES allowed prefix set is not exactly the approved 7"
  fi
fi

# N125. Full-test command survives only in a wrong-parent fence.
fixture="$(make_fixture N125)"
N125_FILE="$fixture/docs/process/PROJECT-RULES.md"
N125_OLD='./gradlew clean test --no-daemon --stacktrace -Dspring.profiles.active=test'
N125_NEW='./gradlew test --no-daemon --stacktrace -Dspring.profiles.active=test'

if [ "$(
  x2a_exact_line_count \
    "$N125_FILE" \
    "$N125_OLD"
)" -ne 1 ]; then
  record_fail \
    "N125 PROJECT-RULES full-test fixture setup failed"
elif ! x2a_replace_exact_line \
  "$N125_FILE" \
  "$N125_OLD" \
  "$N125_NEW"
then
  record_fail \
    "N125 PROJECT-RULES full-test fixture rewrite failed"
elif ! x2a_append_project_fence_decoy \
  "$N125_FILE" \
  "### 최종 전체 테스트 명령" \
  "최종 전체 테스트의 단일 명령은 다음과 같다." \
  '```bash' \
  "$N125_OLD"
then
  record_fail \
    "N125 PROJECT-RULES full-test decoy setup failed"
elif ! x2a_project_line_decoy_postcondition \
  "$N125_FILE" \
  "$N125_OLD" \
  "$N125_NEW"
then
  record_fail \
    "N125 PROJECT-RULES full-test fixture postcondition failed"
else
  expect_fail \
    "N125" \
    "PROJECT-RULES full-test wrong-parent fenced decoy rejected" \
    "$fixture" \
    0 \
    "PROJECT-RULES approved full test command authoritative context mismatch"
fi

# N126. Plan / Issue core marker and bullets are moved under a nested subsection.
fixture="$(make_fixture N126)"
N126_FILE="$fixture/docs/process/GPT-REVIEW-CONTRACT.md"

if ! x2a_insert_gpt_nested_heading \
  "$N126_FILE" \
  "Plan / Issue"
then
  record_fail \
    "N126 GPT Plan / Issue nested fixture rewrite failed"
elif ! x2a_gpt_nested_postcondition \
  "$N126_FILE" \
  "Plan / Issue"
then
  record_fail \
    "N126 GPT Plan / Issue nested fixture postcondition failed"
else
  expect_fail \
    "N126" \
    "GPT Plan / Issue nested subsection core decoy rejected" \
    "$fixture" \
    0 \
    "GPT Plan / Issue core input list mismatch"
fi

# N127. Implementation core marker and bullets are nested.
fixture="$(make_fixture N127)"
N127_FILE="$fixture/docs/process/GPT-REVIEW-CONTRACT.md"

if ! x2a_insert_gpt_nested_heading \
  "$N127_FILE" \
  "Implementation"
then
  record_fail \
    "N127 GPT Implementation nested fixture rewrite failed"
elif ! x2a_gpt_nested_postcondition \
  "$N127_FILE" \
  "Implementation"
then
  record_fail \
    "N127 GPT Implementation nested fixture postcondition failed"
else
  expect_fail \
    "N127" \
    "GPT Implementation nested subsection core decoy rejected" \
    "$fixture" \
    0 \
    "GPT Implementation core input list mismatch"
fi

# N128. Git / PR core marker and bullets are nested.
fixture="$(make_fixture N128)"
N128_FILE="$fixture/docs/process/GPT-REVIEW-CONTRACT.md"

if ! x2a_insert_gpt_nested_heading \
  "$N128_FILE" \
  "Git / PR"
then
  record_fail \
    "N128 GPT Git / PR nested fixture rewrite failed"
elif ! x2a_gpt_nested_postcondition \
  "$N128_FILE" \
  "Git / PR"
then
  record_fail \
    "N128 GPT Git / PR nested fixture postcondition failed"
else
  expect_fail \
    "N128" \
    "GPT Git / PR nested subsection core decoy rejected" \
    "$fixture" \
    0 \
    "GPT Git / PR core input list mismatch"
fi

# N129. TASK-033 prerequisite mutation.
fixture="$(make_fixture N129)"
N129_FILE="$fixture/docs/process/PORTFOLIO-ROADMAP.md"
N129_OLD='| TASK-033 | Outbox Pattern 구현 | 계획 | TASK-032 완료 + 사용자 승인 |'
N129_NEW='| TASK-033 | Outbox Pattern 구현 | 계획 | TASK-032 완료 + fixture 승인 |'

if [ "$(
  x2a_exact_line_count \
    "$N129_FILE" \
    "$N129_OLD"
)" -ne 1 ]; then
  record_fail \
    "N129 Roadmap prerequisite fixture setup failed"
elif ! x2a_replace_exact_line \
  "$N129_FILE" \
  "$N129_OLD" \
  "$N129_NEW"
then
  record_fail \
    "N129 Roadmap prerequisite fixture rewrite failed"
elif [ "$(
  x2a_exact_line_count \
    "$N129_FILE" \
    "$N129_OLD"
)" -ne 0 ] ||
     [ "$(
  x2a_exact_line_count \
    "$N129_FILE" \
    "$N129_NEW"
)" -ne 1 ]
then
  record_fail \
    "N129 Roadmap prerequisite fixture postcondition failed"
else
  expect_fail \
    "N129" \
    "Roadmap TASK-033 prerequisite mutation rejected" \
    "$fixture" \
    0 \
    "Roadmap next-task prerequisite contract mismatch"
fi

# N130. TASK-033-1 prerequisite mutation.
fixture="$(make_fixture N130)"
N130_FILE="$fixture/docs/process/PORTFOLIO-ROADMAP.md"
N130_OLD='| TASK-033-1 | Outbox 재처리 실패 시나리오 | 계획 | TASK-033 완료 + 사용자 승인 |'
N130_NEW='| TASK-033-1 | Outbox 재처리 실패 시나리오 | 계획 | TASK-033 완료 + fixture 승인 |'

if [ "$(
  x2a_exact_line_count \
    "$N130_FILE" \
    "$N130_OLD"
)" -ne 1 ]; then
  record_fail \
    "N130 Roadmap prerequisite fixture setup failed"
elif ! x2a_replace_exact_line \
  "$N130_FILE" \
  "$N130_OLD" \
  "$N130_NEW"
then
  record_fail \
    "N130 Roadmap prerequisite fixture rewrite failed"
elif [ "$(
  x2a_exact_line_count \
    "$N130_FILE" \
    "$N130_OLD"
)" -ne 0 ] ||
     [ "$(
  x2a_exact_line_count \
    "$N130_FILE" \
    "$N130_NEW"
)" -ne 1 ]
then
  record_fail \
    "N130 Roadmap prerequisite fixture postcondition failed"
else
  expect_fail \
    "N130" \
    "Roadmap TASK-033-1 prerequisite mutation rejected" \
    "$fixture" \
    0 \
    "Roadmap next-task prerequisite contract mismatch"
fi

# N131. TASK-028 prerequisite mutation.
fixture="$(make_fixture N131)"
N131_FILE="$fixture/docs/process/PORTFOLIO-ROADMAP.md"
N131_OLD='| TASK-028 | Redis/DB 장애 시나리오 구현 및 문서화 | 계획 | TASK-033-1 완료 + 사용자 승인 |'
N131_NEW='| TASK-028 | Redis/DB 장애 시나리오 구현 및 문서화 | 계획 | TASK-033-1 완료 + fixture 승인 |'

if [ "$(
  x2a_exact_line_count \
    "$N131_FILE" \
    "$N131_OLD"
)" -ne 1 ]; then
  record_fail \
    "N131 Roadmap prerequisite fixture setup failed"
elif ! x2a_replace_exact_line \
  "$N131_FILE" \
  "$N131_OLD" \
  "$N131_NEW"
then
  record_fail \
    "N131 Roadmap prerequisite fixture rewrite failed"
elif [ "$(
  x2a_exact_line_count \
    "$N131_FILE" \
    "$N131_OLD"
)" -ne 0 ] ||
     [ "$(
  x2a_exact_line_count \
    "$N131_FILE" \
    "$N131_NEW"
)" -ne 1 ]
then
  record_fail \
    "N131 Roadmap prerequisite fixture postcondition failed"
else
  expect_fail \
    "N131" \
    "Roadmap TASK-028 prerequisite mutation rejected" \
    "$fixture" \
    0 \
    "Roadmap next-task prerequisite contract mismatch"
fi

# N132. TASK-043 prerequisite mutation.
fixture="$(make_fixture N132)"
N132_FILE="$fixture/docs/process/PORTFOLIO-ROADMAP.md"
N132_OLD='| TASK-043 | 장애 복구 전략 설계 + 코드 구현 | 계획 | TASK-028 완료 + 사용자 승인 |'
N132_NEW='| TASK-043 | 장애 복구 전략 설계 + 코드 구현 | 계획 | TASK-028 완료 + fixture 승인 |'

if [ "$(
  x2a_exact_line_count \
    "$N132_FILE" \
    "$N132_OLD"
)" -ne 1 ]; then
  record_fail \
    "N132 Roadmap prerequisite fixture setup failed"
elif ! x2a_replace_exact_line \
  "$N132_FILE" \
  "$N132_OLD" \
  "$N132_NEW"
then
  record_fail \
    "N132 Roadmap prerequisite fixture rewrite failed"
elif [ "$(
  x2a_exact_line_count \
    "$N132_FILE" \
    "$N132_OLD"
)" -ne 0 ] ||
     [ "$(
  x2a_exact_line_count \
    "$N132_FILE" \
    "$N132_NEW"
)" -ne 1 ]
then
  record_fail \
    "N132 Roadmap prerequisite fixture postcondition failed"
else
  expect_fail \
    "N132" \
    "Roadmap TASK-043 prerequisite mutation rejected" \
    "$fixture" \
    0 \
    "Roadmap next-task prerequisite contract mismatch"
fi


# ---------------------------------------------------------------------------
# Z-2A Finding 1-4 regression helpers.
# ---------------------------------------------------------------------------

z2a_move_exact_line_to_fenced_decoy() {
  file="$1"
  exact="$2"

  tmp="$(mktemp "$WORK_ROOT/z2a-structural-decoy.XXXXXX")" ||
    return 1

  if ! awk \
    -v expected="$exact" '
    BEGIN {
      removed=0
    }

    $0 == expected &&
    removed == 0 {
      removed++
      next
    }

    {
      print
    }

    END {
      if (removed != 1) {
        exit 2
      }
    }
  ' "$file" > "$tmp"
  then
    rm -f "$tmp"
    return 1
  fi

  if ! cat "$tmp" > "$file"; then
    rm -f "$tmp"
    return 1
  fi

  rm -f "$tmp"

  {
    printf '\n%s\n\n' \
      '## Z2A fixture wrong-context decoy'
    printf '%s\n' \
      '```text'
    printf '%s\n' \
      "$exact"
    printf '%s\n' \
      '```'
  } >> "$file"

  [ "$(
    x2a_exact_line_count \
      "$file" \
      "$exact"
  )" -eq 1 ]
}

z2a_mutate_gpt_stage_list() {
  file="$1"
  stage="$2"
  mode="$3"

  tmp="$(mktemp "$WORK_ROOT/z2a-stage-list.XXXXXX")" ||
    return 1

  if ! awk \
    -v target="### $stage" \
    -v mode="$mode" '
    BEGIN {
      heading_count=0
      marker_count=0
      inside=0
      after_marker=0
      bullet_count=0
      changed=0
      first=""
    }

    $0 == target {
      heading_count++
      inside=1
      after_marker=0
      bullet_count=0
      print
      next
    }

    inside &&
    /^### / {
      inside=0
      after_marker=0
    }

    inside &&
    $0 == "핵심 입력:" {
      marker_count++
      after_marker=1
      print
      next
    }

    inside &&
    after_marker &&
    $0 ~ /^- / {
      bullet_count++

      if (mode == "extra" &&
          bullet_count == 1) {
        print
        print "- fixture extra core input"
        changed++
        next
      }

      if (mode == "duplicate" &&
          bullet_count == 1) {
        print
        print
        changed++
        next
      }

      if (mode == "reorder" &&
          bullet_count == 1) {
        first=$0
        next
      }

      if (mode == "reorder" &&
          bullet_count == 2) {
        print
        print first
        changed++
        next
      }

      print
      next
    }

    inside &&
    after_marker &&
    bullet_count > 0 &&
    $0 !~ /^- / {
      after_marker=0
    }

    {
      print
    }

    END {
      if (heading_count != 1 ||
          marker_count != 1 ||
          changed != 1) {
        exit 2
      }
    }
  ' "$file" > "$tmp"
  then
    rm -f "$tmp"
    return 1
  fi

  if ! cat "$tmp" > "$file"; then
    rm -f "$tmp"
    return 1
  fi

  rm -f "$tmp"
}

# N133. genuinely-unquoted clientSecret compact-colon candidate.
if fixture="$(make_fixture N133)"; then
  N133_KEY_A='client'
  N133_KEY_B='Secret'
  N133_VALUE_A='z2a'
  N133_VALUE_B='Runtime'
  N133_VALUE_C='133Candidate'
  N133_VALUE="$N133_VALUE_A$N133_VALUE_B$N133_VALUE_C"

  printf '%s%s:%s\n' \
    "$N133_KEY_A" \
    "$N133_KEY_B" \
    "$N133_VALUE" \
    >> "$fixture/CLAUDE.md"

  if ! grep -Fq "$N133_VALUE" "$fixture/CLAUDE.md"; then
    record_fail "N133 compact clientSecret fixture postcondition failed"
  else
    expect_fail_redacted \
      "N133" \
      "genuinely-unquoted clientSecret compact-colon candidate rejected without value disclosure" \
      "$fixture" \
      "$N133_VALUE" \
      0 \
      "sensitive pattern detected in CLAUDE.md"
  fi
else
  record_fail "N133 fixture construction failed"
fi

# N134. genuinely-unquoted jwtSecret compact-colon candidate.
if fixture="$(make_fixture N134)"; then
  N134_KEY_A='jwt'
  N134_KEY_B='Secret'
  N134_VALUE_A='z2a'
  N134_VALUE_B='Runtime'
  N134_VALUE_C='134Candidate'
  N134_VALUE="$N134_VALUE_A$N134_VALUE_B$N134_VALUE_C"

  printf '%s%s:%s\n' \
    "$N134_KEY_A" \
    "$N134_KEY_B" \
    "$N134_VALUE" \
    >> "$fixture/CLAUDE.md"

  if ! grep -Fq "$N134_VALUE" "$fixture/CLAUDE.md"; then
    record_fail "N134 compact jwtSecret fixture postcondition failed"
  else
    expect_fail_redacted \
      "N134" \
      "genuinely-unquoted jwtSecret compact-colon candidate rejected without value disclosure" \
      "$fixture" \
      "$N134_VALUE" \
      0 \
      "sensitive pattern detected in CLAUDE.md"
  fi
else
  record_fail "N134 fixture construction failed"
fi

# N135. genuinely-unquoted dbPassword compact-colon candidate.
if fixture="$(make_fixture N135)"; then
  N135_KEY_A='db'
  N135_KEY_B='Password'
  N135_VALUE_A='z2a'
  N135_VALUE_B='Runtime'
  N135_VALUE_C='135Candidate'
  N135_VALUE="$N135_VALUE_A$N135_VALUE_B$N135_VALUE_C"

  printf '%s%s:%s\n' \
    "$N135_KEY_A" \
    "$N135_KEY_B" \
    "$N135_VALUE" \
    >> "$fixture/CLAUDE.md"

  if ! grep -Fq "$N135_VALUE" "$fixture/CLAUDE.md"; then
    record_fail "N135 compact dbPassword fixture postcondition failed"
  else
    expect_fail_redacted \
      "N135" \
      "genuinely-unquoted dbPassword compact-colon candidate rejected without value disclosure" \
      "$fixture" \
      "$N135_VALUE" \
      0 \
      "sensitive pattern detected in CLAUDE.md"
  fi
else
  record_fail "N135 fixture construction failed"
fi

# N136. genuinely-unquoted serviceApiKey compact-colon candidate.
if fixture="$(make_fixture N136)"; then
  N136_KEY_A='service'
  N136_KEY_B='Api'
  N136_KEY_C='Key'
  N136_VALUE_A='z2a'
  N136_VALUE_B='Runtime'
  N136_VALUE_C='136Candidate'
  N136_VALUE="$N136_VALUE_A$N136_VALUE_B$N136_VALUE_C"

  printf '%s%s%s:%s\n' \
    "$N136_KEY_A" \
    "$N136_KEY_B" \
    "$N136_KEY_C" \
    "$N136_VALUE" \
    >> "$fixture/CLAUDE.md"

  if ! grep -Fq "$N136_VALUE" "$fixture/CLAUDE.md"; then
    record_fail "N136 compact serviceApiKey fixture postcondition failed"
  else
    expect_fail_redacted \
      "N136" \
      "genuinely-unquoted serviceApiKey compact-colon candidate rejected without value disclosure" \
      "$fixture" \
      "$N136_VALUE" \
      0 \
      "sensitive pattern detected in CLAUDE.md"
  fi
else
  record_fail "N136 fixture construction failed"
fi

# N137. Exact approved sensitive provenance plus non-sensitive hierarchical keys.
if fixture="$(make_fixture N137)"; then
  N137_TOKEN_A='to'
  N137_TOKEN_B='ken'
  N137_ACCESS_A='access_'
  N137_ACCESS_B='token'
  N137_USER='user'
  N137_MEMBER='{memberId}'
  N137_RATE='rate'
  N137_LIMIT='limit'
  N137_API='{api}'
  N137_QUEUE='queue'
  N137_SEQ='seq'
  N137_EVENT='{eventId}'

  {
    printf '%s%s:%s:%s\n' \
      "$N137_TOKEN_A" \
      "$N137_TOKEN_B" \
      "$N137_USER" \
      "$N137_MEMBER"

    printf '%s%s:%s:%s\n' \
      "$N137_ACCESS_A" \
      "$N137_ACCESS_B" \
      "$N137_USER" \
      "$N137_MEMBER"

    printf '%s:%s:%s:%s\n' \
      "$N137_RATE" \
      "$N137_LIMIT" \
      "$N137_MEMBER" \
      "$N137_API"

    printf '%s:%s:%s\n' \
      "$N137_QUEUE" \
      "$N137_SEQ" \
      "$N137_EVENT"
  } >> "$fixture/CLAUDE.md"

  expect_pass \
    "N137" \
    "approved sensitive provenance and non-sensitive hierarchical forms accepted" \
    "$fixture"
else
  record_fail "N137 fixture construction failed"
fi

# N138. Workflow policy survives only in a wrong-section fence.
if fixture="$(make_fixture N138)"; then
  N138_FILE="$fixture/docs/process/DEVELOPMENT-WORKFLOW.md"
  N138_LINE='- 관련 테스트가 있으면 먼저 실행한다.'

  if ! z2a_move_exact_line_to_fenced_decoy \
    "$N138_FILE" \
    "$N138_LINE"
  then
    record_fail "N138 Workflow structural-decoy fixture construction failed"
  else
    expect_fail \
      "N138" \
      "Workflow authoritative policy wrong-section fenced decoy rejected" \
      "$fixture" \
      0 \
      "DEVELOPMENT-WORKFLOW related-test-first obligation missing"
  fi
else
  record_fail "N138 fixture construction failed"
fi

# N139. Learning policy survives only in a wrong-section fence.
if fixture="$(make_fixture N139)"; then
  N139_FILE="$fixture/docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md"
  N139_LINE='질문은 한 번에 하나씩 진행한다.'

  if ! z2a_move_exact_line_to_fenced_decoy \
    "$N139_FILE" \
    "$N139_LINE"
  then
    record_fail "N139 Learning structural-decoy fixture construction failed"
  else
    expect_fail \
      "N139" \
      "Learning authoritative policy wrong-section fenced decoy rejected" \
      "$fixture" \
      0 \
      "Learning one-question-at-a-time rule missing"
  fi
else
  record_fail "N139 fixture construction failed"
fi

# N140. GPT role policy survives only in a wrong-section fence.
if fixture="$(make_fixture N140)"; then
  N140_FILE="$fixture/docs/process/GPT-REVIEW-CONTRACT.md"
  N140_LINE='GPT는 이 프로젝트의 독립 검토자다.'

  if ! z2a_move_exact_line_to_fenced_decoy \
    "$N140_FILE" \
    "$N140_LINE"
  then
    record_fail "N140 GPT structural-decoy fixture construction failed"
  else
    expect_fail \
      "N140" \
      "GPT authoritative role wrong-section fenced decoy rejected" \
      "$fixture" \
      0 \
      "GPT independent reviewer role missing"
  fi
else
  record_fail "N140 fixture construction failed"
fi

# N141. Agent read-only policy survives only in a wrong-section fence.
if fixture="$(make_fixture N141)"; then
  N141_FILE="$fixture/.claude/agents/ticketing-risk-reviewer.md"
  N141_LINE='이 Agent는 읽기 전용 검토자다.'

  if ! z2a_move_exact_line_to_fenced_decoy \
    "$N141_FILE" \
    "$N141_LINE"
  then
    record_fail "N141 Agent structural-decoy fixture construction failed"
  else
    expect_fail \
      "N141" \
      "Agent authoritative read-only policy wrong-section fenced decoy rejected" \
      "$fixture" \
      0 \
      "Agent read-only rule missing"
  fi
else
  record_fail "N141 fixture construction failed"
fi

# N142. PROJECT-RULES residual technical policy survives only as decoy.
if fixture="$(make_fixture N142)"; then
  N142_FILE="$fixture/docs/process/PROJECT-RULES.md"
  N142_LINE='- Controller에서 repository를 직접 호출하지 않는다.'

  if ! z2a_move_exact_line_to_fenced_decoy \
    "$N142_FILE" \
    "$N142_LINE"
  then
    record_fail "N142 PROJECT-RULES structural-decoy fixture construction failed"
  else
    expect_fail \
      "N142" \
      "PROJECT-RULES residual load-bearing policy wrong-section fenced decoy rejected" \
      "$fixture" \
      0 \
      "PROJECT-RULES Controller-to-Repository prohibition missing"
  fi
else
  record_fail "N142 fixture construction failed"
fi

# N143. Plan / Issue extra core bullet.
if fixture="$(make_fixture N143)"; then
  if ! z2a_mutate_gpt_stage_list \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "Plan / Issue" \
    "extra"
  then
    record_fail "N143 Plan / Issue extra-bullet fixture construction failed"
  else
    expect_fail \
      "N143" \
      "Plan / Issue extra core bullet rejected" \
      "$fixture" \
      0 \
      "GPT Plan / Issue core input list mismatch"
  fi
else
  record_fail "N143 fixture construction failed"
fi

# N144. Plan / Issue duplicate core bullet.
if fixture="$(make_fixture N144)"; then
  if ! z2a_mutate_gpt_stage_list \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "Plan / Issue" \
    "duplicate"
  then
    record_fail "N144 Plan / Issue duplicate-bullet fixture construction failed"
  else
    expect_fail \
      "N144" \
      "Plan / Issue duplicate core bullet rejected" \
      "$fixture" \
      0 \
      "GPT Plan / Issue core input list mismatch"
  fi
else
  record_fail "N144 fixture construction failed"
fi

# N145. Plan / Issue core bullet order mutation.
if fixture="$(make_fixture N145)"; then
  if ! z2a_mutate_gpt_stage_list \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "Plan / Issue" \
    "reorder"
  then
    record_fail "N145 Plan / Issue reorder fixture construction failed"
  else
    expect_fail \
      "N145" \
      "Plan / Issue reordered core list rejected" \
      "$fixture" \
      0 \
      "GPT Plan / Issue core input list mismatch"
  fi
else
  record_fail "N145 fixture construction failed"
fi

# N146. Implementation extra core bullet.
if fixture="$(make_fixture N146)"; then
  if ! z2a_mutate_gpt_stage_list \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "Implementation" \
    "extra"
  then
    record_fail "N146 Implementation extra-bullet fixture construction failed"
  else
    expect_fail \
      "N146" \
      "Implementation extra core bullet rejected" \
      "$fixture" \
      0 \
      "GPT Implementation core input list mismatch"
  fi
else
  record_fail "N146 fixture construction failed"
fi

# N147. Implementation duplicate core bullet.
if fixture="$(make_fixture N147)"; then
  if ! z2a_mutate_gpt_stage_list \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "Implementation" \
    "duplicate"
  then
    record_fail "N147 Implementation duplicate-bullet fixture construction failed"
  else
    expect_fail \
      "N147" \
      "Implementation duplicate core bullet rejected" \
      "$fixture" \
      0 \
      "GPT Implementation core input list mismatch"
  fi
else
  record_fail "N147 fixture construction failed"
fi

# N148. Implementation core bullet order mutation.
if fixture="$(make_fixture N148)"; then
  if ! z2a_mutate_gpt_stage_list \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "Implementation" \
    "reorder"
  then
    record_fail "N148 Implementation reorder fixture construction failed"
  else
    expect_fail \
      "N148" \
      "Implementation reordered core list rejected" \
      "$fixture" \
      0 \
      "GPT Implementation core input list mismatch"
  fi
else
  record_fail "N148 fixture construction failed"
fi

# N149. Git / PR extra core bullet.
if fixture="$(make_fixture N149)"; then
  if ! z2a_mutate_gpt_stage_list \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "Git / PR" \
    "extra"
  then
    record_fail "N149 Git / PR extra-bullet fixture construction failed"
  else
    expect_fail \
      "N149" \
      "Git / PR extra core bullet rejected" \
      "$fixture" \
      0 \
      "GPT Git / PR core input list mismatch"
  fi
else
  record_fail "N149 fixture construction failed"
fi

# N150. Git / PR duplicate core bullet.
if fixture="$(make_fixture N150)"; then
  if ! z2a_mutate_gpt_stage_list \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "Git / PR" \
    "duplicate"
  then
    record_fail "N150 Git / PR duplicate-bullet fixture construction failed"
  else
    expect_fail \
      "N150" \
      "Git / PR duplicate core bullet rejected" \
      "$fixture" \
      0 \
      "GPT Git / PR core input list mismatch"
  fi
else
  record_fail "N150 fixture construction failed"
fi

# N151. Git / PR core bullet order mutation.
if fixture="$(make_fixture N151)"; then
  if ! z2a_mutate_gpt_stage_list \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "Git / PR" \
    "reorder"
  then
    record_fail "N151 Git / PR reorder fixture construction failed"
  else
    expect_fail \
      "N151" \
      "Git / PR reordered core list rejected" \
      "$fixture" \
      0 \
      "GPT Git / PR core input list mismatch"
  fi
else
  record_fail "N151 fixture construction failed"
fi

# N152. Mandatory top-three wording regresses to old optional wording.
if fixture="$(make_fixture N152)"; then
  N152_FILE="$fixture/docs/process/GPT-REVIEW-CONTRACT.md"
  N152_NEW='- 중요도가 높은 Finding 최대 3개를 앞부분에 요약한다.'
  N152_OLD='- 중요도가 높은 Finding 최대 3개를 앞부분에 요약할 수 있다.'

  if ! x2a_replace_exact_line \
    "$N152_FILE" \
    "$N152_NEW" \
    "$N152_OLD"
  then
    record_fail "N152 GPT output wording fixture rewrite failed"
  elif [ "$(
    x2a_exact_line_count \
      "$N152_FILE" \
      "$N152_NEW"
  )" -ne 0 ] ||
       [ "$(
    x2a_exact_line_count \
      "$N152_FILE" \
      "$N152_OLD"
  )" -ne 1 ]
  then
    record_fail "N152 GPT output wording fixture postcondition failed"
  else
    expect_fail \
      "N152" \
      "old optional top-three wording rejected" \
      "$fixture" \
      0 \
      "GPT output principles authoritative ordered block mismatch"
  fi
else
  record_fail "N152 fixture construction failed"
fi

# N153. Mandatory top-three line survives only as wrong-section fenced decoy.
if fixture="$(make_fixture N153)"; then
  N153_FILE="$fixture/docs/process/GPT-REVIEW-CONTRACT.md"
  N153_LINE='- 중요도가 높은 Finding 최대 3개를 앞부분에 요약한다.'

  if ! z2a_move_exact_line_to_fenced_decoy \
    "$N153_FILE" \
    "$N153_LINE"
  then
    record_fail "N153 GPT output-principles decoy fixture construction failed"
  else
    expect_fail \
      "N153" \
      "mandatory top-three wrong-section fenced decoy rejected" \
      "$fixture" \
      0 \
      "GPT output principles authoritative ordered block mismatch"
  fi
else
  record_fail "N153 fixture construction failed"
fi

# N154. Arbitrary sensitive root must not inherit token/access_token provenance.
if fixture="$(make_fixture N154)"; then
  N154_KEY_A='client'
  N154_KEY_B='Secret'
  N154_IDENT_A='member'
  N154_IDENT_B='N154'
  N154_IDENT_C='RuntimeCandidate'
  N154_IDENT="$N154_IDENT_A$N154_IDENT_B$N154_IDENT_C"

  printf '%s%s:user:{%s}\n' \
    "$N154_KEY_A" \
    "$N154_KEY_B" \
    "$N154_IDENT" \
    >> "$fixture/CLAUDE.md"

  if ! grep -Fq "$N154_IDENT" "$fixture/CLAUDE.md"; then
    record_fail "N154 hierarchical sensitive-root fixture postcondition failed"
  else
    expect_fail_redacted \
      "N154" \
      "arbitrary sensitive-root hierarchical provenance bypass rejected without value disclosure" \
      "$fixture" \
      "$N154_IDENT" \
      0 \
      "sensitive pattern detected in CLAUDE.md"
  fi
else
  record_fail "N154 fixture construction failed"
fi


# ---------------------------------------------------------------------------
# Phase 3-5Z-5 Post-AD5 Finding 1-4 regression helpers.
# ---------------------------------------------------------------------------

z5_remove_exact_line() {
  file="$1"
  exact="$2"
  tmp="$(mktemp "$WORK_ROOT/z5-remove-line.XXXXXX")" ||
    return 1

  if ! awk \
    -v expected="$exact" '
      BEGIN {
        removed=0
      }

      $0 == expected &&
      removed == 0 {
        removed++
        next
      }

      {
        print
      }

      END {
        if (removed != 1) {
          exit 2
        }
      }
    ' "$file" > "$tmp"
  then
    rm -f "$tmp"
    return 1
  fi

  if ! cat "$tmp" > "$file"; then
    rm -f "$tmp"
    return 1
  fi

  rm -f "$tmp"
}

z5_move_exact_line_to_off_section() {
  file="$1"
  exact="$2"

  if ! z5_remove_exact_line \
    "$file" \
    "$exact"
  then
    return 1
  fi

  {
    printf '\n%s\n\n' \
      '## Z5 fixture off-section decoy'

    printf '%s\n' \
      "$exact"
  } >> "$file"

  [ "$(
    x2a_exact_line_count \
      "$file" \
      "$exact"
  )" -eq 1 ]
}

z5_duplicate_exact_line() {
  file="$1"
  exact="$2"
  tmp="$(mktemp "$WORK_ROOT/z5-duplicate-line.XXXXXX")" ||
    return 1

  if ! awk \
    -v expected="$exact" '
      BEGIN {
        seen=0
      }

      $0 == expected {
        seen++
        print
        print
        next
      }

      {
        print
      }

      END {
        if (seen != 1) {
          exit 2
        }
      }
    ' "$file" > "$tmp"
  then
    rm -f "$tmp"
    return 1
  fi

  if ! cat "$tmp" > "$file"; then
    rm -f "$tmp"
    return 1
  fi

  rm -f "$tmp"
}

z5_mutate_stage12_stop_field() {
  file="$1"
  mode="$2"
  tmp="$(mktemp "$WORK_ROOT/z5-stage12-field.XXXXXX")" ||
    return 1

  if ! awk \
    -v mode="$mode" '
      BEGIN {
        inside=0
        changed=0
      }

      $0 == "### 12. 조건부 회고 / 장애 기록" {
        inside=1
        print
        next
      }

      inside &&
      /^### / {
        inside=0
      }

      inside &&
      $0 == "**중단 조건**" &&
      changed == 0 {
        changed++

        if (mode == "fence") {
          print "```text"
          print "**중단 조건**"
          print "```"
        } else if (mode == "nested") {
          print "#### Z5 nested fixture"
          print "**중단 조건**"
        } else if (mode != "off") {
          exit 3
        }

        next
      }

      {
        print
      }

      END {
        if (changed != 1) {
          exit 2
        }

        if (mode == "off") {
          print ""
          print "## Z5 lifecycle off-section fixture"
          print ""
          print "**중단 조건**"
        }
      }
    ' "$file" > "$tmp"
  then
    rm -f "$tmp"
    return 1
  fi

  if ! cat "$tmp" > "$file"; then
    rm -f "$tmp"
    return 1
  fi

  rm -f "$tmp"
}

z5_insert_unclosed_fence_after_stage12() {
  file="$1"
  tmp="$(mktemp "$WORK_ROOT/z5-unclosed-fence.XXXXXX")" ||
    return 1

  if ! awk '
      BEGIN {
        inserted=0
      }

      $0 == "### 12. 조건부 회고 / 장애 기록" &&
      inserted == 0 {
        print
        print "```text"
        inserted++
        next
      }

      {
        print
      }

      END {
        if (inserted != 1) {
          exit 2
        }
      }
    ' "$file" > "$tmp"
  then
    rm -f "$tmp"
    return 1
  fi

  if ! cat "$tmp" > "$file"; then
    rm -f "$tmp"
    return 1
  fi

  rm -f "$tmp"
}

z5_append_fenced_decoy() {
  file="$1"
  exact="$2"

  {
    printf '\n%s\n\n' \
      '## Z5 fixture fenced decoy'

    printf '%s\n' \
      '```text'

    printf '%s\n' \
      "$exact"

    printf '%s\n' \
      '```'
  } >> "$file"
}

z5_mutate_matrix_row459_target() {
  file="$1"
  tmp="$(mktemp "$WORK_ROOT/z5-matrix459.XXXXXX")" ||
    return 1

  if ! awk '
      BEGIN{seen=0;changed=0}

      /^\| 459 \|/ {
        seen++
        changed+=sub(/docs\/process\/PROJECT-RULES\.md/, "docs/process/DEVELOPMENT-WORKFLOW.md")
      }

      {print}

      END{if(seen != 1 || changed != 1) exit 2}
    ' "$file" > "$tmp"
  then
    rm -f "$tmp"
    return 1
  fi

  if ! cat "$tmp" > "$file"; then
    rm -f "$tmp"
    return 1
  fi

  rm -f "$tmp"
}


# N155. Stage 12 required field survives only inside a fenced decoy.
if fixture="$(make_fixture N155)"; then
  if ! z5_mutate_stage12_stop_field \
    "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md" \
    "fence"
  then
    record_fail \
      "N155 lifecycle fenced-field fixture construction failed"
  else
    expect_fail \
      "N155" \
      "lifecycle fenced field decoy rejected" \
      "$fixture" \
      0 \
      "DEVELOPMENT-WORKFLOW lifecycle schema mismatch: stage 12 missing 중단 조건"
  fi
else
  record_fail "N155 fixture construction failed"
fi

# N156. Stage 12 required field survives only outside authoritative lifecycle H2.
if fixture="$(make_fixture N156)"; then
  if ! z5_mutate_stage12_stop_field \
    "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md" \
    "off"
  then
    record_fail \
      "N156 lifecycle off-section fixture construction failed"
  else
    expect_fail \
      "N156" \
      "lifecycle off-section field decoy rejected" \
      "$fixture" \
      0 \
      "DEVELOPMENT-WORKFLOW lifecycle schema mismatch: stage 12 missing 중단 조건"
  fi
else
  record_fail "N156 fixture construction failed"
fi

# N157. Duplicate canonical lifecycle stage heading.
if fixture="$(make_fixture N157)"; then
  if ! z5_duplicate_exact_line \
    "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md" \
    "### 12. 조건부 회고 / 장애 기록"
  then
    record_fail \
      "N157 duplicate lifecycle-stage fixture construction failed"
  else
    expect_fail \
      "N157" \
      "duplicate lifecycle stage rejected" \
      "$fixture" \
      0 \
      "DEVELOPMENT-WORKFLOW lifecycle schema mismatch:"
  fi
else
  record_fail "N157 fixture construction failed"
fi

# N158. Stage 12 required field survives only under nested H4.
if fixture="$(make_fixture N158)"; then
  if ! z5_mutate_stage12_stop_field \
    "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md" \
    "nested"
  then
    record_fail \
      "N158 lifecycle nested-field fixture construction failed"
  else
    expect_fail \
      "N158" \
      "nested lifecycle field decoy rejected" \
      "$fixture" \
      0 \
      "DEVELOPMENT-WORKFLOW lifecycle schema mismatch: stage 12 missing 중단 조건"
  fi
else
  record_fail "N158 fixture construction failed"
fi

# N159. Unclosed Markdown fence inside authoritative lifecycle.
if fixture="$(make_fixture N159)"; then
  if ! z5_insert_unclosed_fence_after_stage12 \
    "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md"
  then
    record_fail \
      "N159 lifecycle unclosed-fence fixture construction failed"
  else
    expect_fail \
      "N159" \
      "lifecycle unclosed fence rejected fail-closed" \
      "$fixture" \
      0 \
      "DEVELOPMENT-WORKFLOW lifecycle strict Markdown/schema parser failed"
  fi
else
  record_fail "N159 fixture construction failed"
fi

# N160. Roadmap coverage marker survives only as fenced decoy.
if fixture="$(make_fixture N160)"; then
  N160_LINE='- A old roadmap 고유 TASK: 74'

  if ! z2a_move_exact_line_to_fenced_decoy \
    "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
    "$N160_LINE"
  then
    record_fail \
      "N160 Roadmap coverage fenced-decoy fixture construction failed"
  else
    expect_fail \
      "N160" \
      "Roadmap coverage fenced decoy rejected" \
      "$fixture" \
      0 \
      "Roadmap A-old coverage value 74 authoritative context mismatch"
  fi
else
  record_fail "N160 fixture construction failed"
fi

# N161. Roadmap coverage marker survives only as visible off-section decoy.
if fixture="$(make_fixture N161)"; then
  N161_LINE='- A old roadmap 고유 TASK: 74'

  if ! z5_move_exact_line_to_off_section \
    "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
    "$N161_LINE"
  then
    record_fail \
      "N161 Roadmap coverage off-section fixture construction failed"
  else
    expect_fail \
      "N161" \
      "Roadmap coverage off-section decoy rejected" \
      "$fixture" \
      0 \
      "Roadmap A-old coverage value 74 authoritative context mismatch"
  fi
else
  record_fail "N161 fixture construction failed"
fi

# N162. Duplicate authoritative Roadmap TASK coverage heading.
if fixture="$(make_fixture N162)"; then
  if ! z5_duplicate_exact_line \
    "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
    "## TASK coverage 기준"
  then
    record_fail \
      "N162 duplicate Roadmap coverage heading fixture construction failed"
  else
    expect_fail \
      "N162" \
      "duplicate Roadmap coverage heading rejected" \
      "$fixture" \
      0 \
      "Roadmap TASK coverage section heading count is not exactly 1"
  fi
else
  record_fail "N162 fixture construction failed"
fi

# N163. Wrong canonical coverage value cannot be repaired by correct fenced decoy.
if fixture="$(make_fixture N163)"; then
  N163_FILE="$fixture/docs/process/PORTFOLIO-ROADMAP.md"
  N163_CORRECT='- A old roadmap 고유 TASK: 74'
  N163_WRONG='- A old roadmap 고유 TASK: 75'

  if ! x2a_replace_exact_line \
    "$N163_FILE" \
    "$N163_CORRECT" \
    "$N163_WRONG"
  then
    record_fail \
      "N163 Roadmap wrong-value fixture rewrite failed"
  elif ! z5_append_fenced_decoy \
    "$N163_FILE" \
    "$N163_CORRECT"
  then
    record_fail \
      "N163 Roadmap correct fenced-decoy append failed"
  else
    expect_fail \
      "N163" \
      "wrong canonical Roadmap coverage value with fenced correct decoy rejected" \
      "$fixture" \
      0 \
      "Roadmap A-old coverage value 74 authoritative context mismatch"
  fi
else
  record_fail "N163 fixture construction failed"
fi

# N164. Current-status TASK-060 uses spaced non-approved enum.
if fixture="$(make_fixture N164)"; then
  N164_FILE="$fixture/docs/process/PORTFOLIO-ROADMAP.md"

  N164_OLD='| TASK-060 | 프로젝트 프로세스 문서 정합성 복구 및 학습 계약 추가 | 진행중 | Issue #100, branch `chore/TASK-060-process-doc-consistency-recovery` |'

  N164_NEW='| TASK-060 | 프로젝트 프로세스 문서 정합성 복구 및 학습 계약 추가 | 진행 중 | Issue #100, branch `chore/TASK-060-process-doc-consistency-recovery` |'

  if ! x2a_replace_exact_line \
    "$N164_FILE" \
    "$N164_OLD" \
    "$N164_NEW"
  then
    record_fail \
      "N164 current-status spaced-enum fixture rewrite failed"
  else
    expect_fail \
      "N164" \
      "current-status spaced 진행 중 enum rejected" \
      "$fixture" \
      0 \
      "Roadmap current-status contains unapproved status enum"
  fi
else
  record_fail "N164 fixture construction failed"
fi

# N165. Full-history row uses spaced non-approved 재확인 필요 enum.
if fixture="$(make_fixture N165)"; then
  N165_FILE="$fixture/docs/process/PORTFOLIO-ROADMAP.md"

  N165_OLD='| TASK-001 | 프로젝트 부트스트랩 + MariaDB 연결 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | — |'

  N165_NEW='| TASK-001 | 프로젝트 부트스트랩 + MariaDB 연결 | 재확인 필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | — |'

  if ! x2a_replace_exact_line \
    "$N165_FILE" \
    "$N165_OLD" \
    "$N165_NEW"
  then
    record_fail \
      "N165 history spaced-enum fixture rewrite failed"
  else
    expect_fail \
      "N165" \
      "history spaced 재확인 필요 enum rejected" \
      "$fixture" \
      0 \
      "Roadmap history contains unapproved status enum"
  fi
else
  record_fail "N165 fixture construction failed"
fi

# N166. Status definition regresses to spaced form while tables remain canonical.
if fixture="$(make_fixture N166)"; then
  N166_FILE="$fixture/docs/process/PORTFOLIO-ROADMAP.md"

  N166_OLD='- `진행중`: 사용자 승인 후 실제 진행 중인 TASK'
  N166_NEW='- `진행 중`: 사용자 승인 후 실제 진행 중인 TASK'

  if ! x2a_replace_exact_line \
    "$N166_FILE" \
    "$N166_OLD" \
    "$N166_NEW"
  then
    record_fail \
      "N166 Roadmap status-definition fixture rewrite failed"
  else
    expect_fail \
      "N166" \
      "spaced Roadmap status definition rejected" \
      "$fixture" \
      0 \
      "Roadmap 진행중 status definition mismatch"
  fi
else
  record_fail "N166 fixture construction failed"
fi

# N167. PROJECT-RULES authoritative exact-command contract removed.
if fixture="$(make_fixture N167)"; then
  N167_LINE='- 실제로 실행한 각 test는 실행 결과와 함께 실제로 실행한 exact full command를 기록한다.'

  if ! z5_remove_exact_line \
    "$fixture/docs/process/PROJECT-RULES.md" \
    "$N167_LINE"
  then
    record_fail \
      "N167 PROJECT-RULES contract removal fixture construction failed"
  else
    expect_fail \
      "N167" \
      "missing executed-test exact-command contract rejected" \
      "$fixture" \
      0 \
      "PROJECT-RULES executed-test exact-full-command evidence contract missing"
  fi
else
  record_fail "N167 fixture construction failed"
fi

# N168. PROJECT-RULES exact-command contract survives only as fenced decoy.
if fixture="$(make_fixture N168)"; then
  N168_LINE='- 실제로 실행한 각 test는 실행 결과와 함께 실제로 실행한 exact full command를 기록한다.'

  if ! z2a_move_exact_line_to_fenced_decoy \
    "$fixture/docs/process/PROJECT-RULES.md" \
    "$N168_LINE"
  then
    record_fail \
      "N168 PROJECT-RULES fenced-decoy fixture construction failed"
  else
    expect_fail \
      "N168" \
      "executed-test contract fenced decoy rejected" \
      "$fixture" \
      0 \
      "PROJECT-RULES executed-test exact-full-command evidence contract missing"
  fi
else
  record_fail "N168 fixture construction failed"
fi

# N169. PROJECT-RULES exact-command contract survives only off-section.
if fixture="$(make_fixture N169)"; then
  N169_LINE='- 실제로 실행한 각 test는 실행 결과와 함께 실제로 실행한 exact full command를 기록한다.'

  if ! z5_move_exact_line_to_off_section \
    "$fixture/docs/process/PROJECT-RULES.md" \
    "$N169_LINE"
  then
    record_fail \
      "N169 PROJECT-RULES off-section fixture construction failed"
  else
    expect_fail \
      "N169" \
      "executed-test contract off-section decoy rejected" \
      "$fixture" \
      0 \
      "PROJECT-RULES executed-test exact-full-command evidence contract missing"
  fi
else
  record_fail "N169 fixture construction failed"
fi

# N170. Workflow Stage 8 direct output loses PROJECT-RULES evidence delegation.
if fixture="$(make_fixture N170)"; then
  N170_LINE='- 실행한 test evidence는 `docs/process/PROJECT-RULES.md`의 공통 test evidence 원칙에 따라 기록한다.'

  if ! z5_remove_exact_line \
    "$fixture/docs/process/DEVELOPMENT-WORKFLOW.md" \
    "$N170_LINE"
  then
    record_fail \
      "N170 Workflow delegation removal fixture construction failed"
  else
    expect_fail \
      "N170" \
      "missing Workflow Stage 8 evidence delegation rejected" \
      "$fixture" \
      0 \
      "DEVELOPMENT-WORKFLOW Stage 8 test-evidence SSOT delegation missing"
  fi
else
  record_fail "N170 fixture construction failed"
fi

# N171. Migration Matrix authoritative row 459 target identity drifts.
if fixture="$(make_fixture N171)"; then
  if ! z5_mutate_matrix_row459_target \
    "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md"
  then
    record_fail \
      "N171 Matrix row 459 target fixture construction failed"
  elif ! awk -F'|' '
    function t(s){gsub(/^[[:space:]]+|[[:space:]]+$/, "", s);return s}
    BEGIN{seen=0;new_target=0;old_target=0}
    /^\| 459 \|/ {
      seen++
      target=t($9)
      if(target=="docs/process/DEVELOPMENT-WORKFLOW.md") new_target++
      if(target=="docs/process/PROJECT-RULES.md") old_target++
    }
    END{if(seen != 1 || new_target != 1 || old_target != 0) exit 1}
  ' "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md"
  then
    record_fail \
      "N171 Matrix row 459 mutation postcondition failed"
  else
    expect_fail \
      "N171" \
      "Migration Matrix row 459 mapping drift rejected" \
      "$fixture" \
      0 \
      "Migration Matrix row 459 test-evidence mapping mismatch"
  fi
else
  record_fail "N171 fixture construction failed"
fi

# N172. TASK-START trailing unclosed fence.
if fixture="$(make_fixture N172)"; then
  if ! z18_append_unclosed_markdown_fence \
    "$fixture/docs/process/TASK-START-CHECKLIST.md" \
    '```text'
  then
    record_fail \
      "N172 TASK-START unclosed-fence fixture construction failed"
  else
    expect_fail \
      "N172" \
      "TASK-START trailing unclosed fence rejected fail-closed" \
      "$fixture" \
      0 \
      "TASK-START-CHECKLIST Markdown visible-line parser failed"
  fi
else
  record_fail "N172 fixture construction failed"
fi

# N173. PROJECT-RULES trailing unclosed fence.
if fixture="$(make_fixture N173)"; then
  if ! z18_append_unclosed_markdown_fence \
    "$fixture/docs/process/PROJECT-RULES.md" \
    '```text'
  then
    record_fail \
      "N173 PROJECT-RULES unclosed-fence fixture construction failed"
  else
    expect_fail \
      "N173" \
      "PROJECT-RULES trailing unclosed fence rejected fail-closed" \
      "$fixture" \
      0 \
      "PROJECT-RULES Markdown section parser failed"
  fi
else
  record_fail "N173 fixture construction failed"
fi

# N174. Roadmap trailing unclosed fence.
if fixture="$(make_fixture N174)"; then
  if ! z18_append_unclosed_markdown_fence \
    "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
    '```text'
  then
    record_fail \
      "N174 Roadmap unclosed-fence fixture construction failed"
  else
    expect_fail \
      "N174" \
      "Roadmap trailing unclosed fence rejected fail-closed" \
      "$fixture" \
      0 \
      "Roadmap history approved table topology mismatch"
  fi
else
  record_fail "N174 fixture construction failed"
fi

# N175. GPT-REVIEW-CONTRACT trailing unclosed fence.
if fixture="$(make_fixture N175)"; then
  if ! z18_append_unclosed_markdown_fence \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    '```text'
  then
    record_fail \
      "N175 GPT contract unclosed-fence fixture construction failed"
  else
    expect_fail \
      "N175" \
      "GPT contract trailing unclosed fence rejected fail-closed" \
      "$fixture" \
      0 \
      "GPT review stage Markdown section parser failed: Plan / Issue"
  fi
else
  record_fail "N175 fixture construction failed"
fi

# N176. Positive control: a properly closed fence remains valid.
if fixture="$(make_fixture N176)"; then
  if ! z18_append_closed_markdown_fence \
    "$fixture/docs/process/TASK-START-CHECKLIST.md" \
    '~~~text' \
    'Z18 closed-fence positive control' \
    '~~~'
  then
    record_fail \
      "N176 closed-fence fixture construction failed"
  else
    expect_pass \
      "N176" \
      "properly closed Markdown fence remains accepted" \
      "$fixture"
  fi
else
  record_fail "N176 fixture construction failed"
fi

# N177. DOCUMENT-MIGRATION-MATRIX CFL trailing unclosed fence.
if fixture="$(make_fixture N177)"; then
  if ! z18_append_unclosed_markdown_fence \
    "$fixture/docs/process/DOCUMENT-MIGRATION-MATRIX.md" \
    '```text'
  then
    record_fail \
      "N177 Matrix CFL unclosed-fence fixture construction failed"
  else
    expect_fail \
      "N177" \
      "Matrix CFL trailing unclosed fence rejected through guarded CFL consumer chain" \
      "$fixture" \
      0 \
      "CFL Markdown section parser failed"
  fi
else
  record_fail "N177 fixture construction failed"
fi

# N178. PROJECT Branch only survives inside a 1-space-indented outer fence.
if fixture="$(make_fixture N178)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/PROJECT-RULES.md" \
    "PROJECT_BRANCH_INDENTED_BACKTICK"
  then
    record_fail \
      "N178 PROJECT Branch indented-fence fixture construction failed"
  else
    expect_fail \
      "N178" \
      "PROJECT Branch indented-fence structural decoy rejected" \
      "$fixture" \
      0 \
      "PROJECT-RULES branch naming pattern missing"
  fi
else
  record_fail "N178 fixture construction failed"
fi

# N179. PROJECT prefixes only survive inside a 3-space-indented outer fence.
if fixture="$(make_fixture N179)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/PROJECT-RULES.md" \
    "PROJECT_PREFIX_INDENTED_BACKTICK"
  then
    record_fail \
      "N179 PROJECT prefix indented-fence fixture construction failed"
  else
    expect_fail \
      "N179" \
      "PROJECT allowed-prefix indented-fence structural decoy rejected" \
      "$fixture" \
      0 \
      "PROJECT-RULES allowed prefix set is not exactly the approved 7"
  fi
else
  record_fail "N179 fixture construction failed"
fi

# N180. PROJECT Commit/PR only survives inside an indented tilde outer fence.
if fixture="$(make_fixture N180)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/PROJECT-RULES.md" \
    "PROJECT_COMMIT_INDENTED_TILDE"
  then
    record_fail \
      "N180 PROJECT Commit/PR tilde-fence fixture construction failed"
  else
    expect_fail \
      "N180" \
      "PROJECT Commit/PR indented-tilde structural decoy rejected" \
      "$fixture" \
      0 \
      "PROJECT-RULES Commit/PR pattern missing"
  fi
else
  record_fail "N180 fixture construction failed"
fi

# N181. PROJECT positive control with an unrelated balanced indented fence.
if fixture="$(make_fixture N181)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/PROJECT-RULES.md" \
    "PROJECT_POSITIVE_BALANCED_FENCE"
  then
    record_fail \
      "N181 PROJECT positive-control fixture construction failed"
  else
    expect_pass \
      "N181" \
      "PROJECT canonical Git metadata survives unrelated balanced fence" \
      "$fixture"
  fi
else
  record_fail "N181 fixture construction failed"
fi

# N182. Roadmap anchor-next contract rejects balanced outer backtick decoy.
if fixture="$(make_fixture N182)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
    "ROADMAP_BACKTICK_OUTER_DECOY"
  then
    record_fail \
      "N182 Roadmap backtick-decoy fixture construction failed"
  else
    expect_fail \
      "N182" \
      "Roadmap balanced backtick outer-fence decoy rejected" \
      "$fixture" \
      0 \
      "Roadmap direct TASK fenced block missing or malformed"
  fi
else
  record_fail "N182 fixture construction failed"
fi

# N183. Roadmap anchor-next contract rejects balanced outer tilde decoy.
if fixture="$(make_fixture N183)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
    "ROADMAP_TILDE_OUTER_DECOY"
  then
    record_fail \
      "N183 Roadmap tilde-decoy fixture construction failed"
  else
    expect_fail \
      "N183" \
      "Roadmap balanced tilde outer-fence decoy rejected" \
      "$fixture" \
      0 \
      "Roadmap direct TASK fenced block missing or malformed"
  fi
else
  record_fail "N183 fixture construction failed"
fi

# N184. Roadmap duplicate canonical direct-order block is rejected.
if fixture="$(make_fixture N184)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/PORTFOLIO-ROADMAP.md" \
    "ROADMAP_DUPLICATE_BLOCK"
  then
    record_fail \
      "N184 Roadmap duplicate-block fixture construction failed"
  else
    expect_fail \
      "N184" \
      "Roadmap duplicate canonical direct-order block rejected" \
      "$fixture" \
      0 \
      "Roadmap direct TASK fenced block missing or malformed"
  fi
else
  record_fail "N184 fixture construction failed"
fi

# N185. Roadmap positive control: exact one canonical direct-order block.
if fixture="$(make_fixture N185)"; then
  expect_pass \
    "N185" \
    "Roadmap exact-one canonical direct-order block accepted" \
    "$fixture"
else
  record_fail "N185 fixture construction failed"
fi

# N186. GPT Finding schema field survives only in a fenced decoy.
if fixture="$(make_fixture N186)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "GPT_SCHEMA_FENCED_DECOY"
  then
    record_fail \
      "N186 GPT schema fenced-decoy fixture construction failed"
  else
    expect_fail \
      "N186" \
      "GPT Finding schema fenced field decoy rejected" \
      "$fixture" \
      0 \
      "GPT common Finding field mismatch: Evidence sufficiency"
  fi
else
  record_fail "N186 fixture construction failed"
fi

# N187. GPT Finding schema field survives only in an off-section table.
if fixture="$(make_fixture N187)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "GPT_SCHEMA_OFF_SECTION_DECOY"
  then
    record_fail \
      "N187 GPT schema off-section fixture construction failed"
  else
    expect_fail \
      "N187" \
      "GPT Finding schema off-section field decoy rejected" \
      "$fixture" \
      0 \
      "GPT common Finding field mismatch: Evidence sufficiency"
  fi
else
  record_fail "N187 fixture construction failed"
fi

# N188. GPT Finding schema duplicate authoritative table is rejected.
if fixture="$(make_fixture N188)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "GPT_SCHEMA_DUPLICATE_TABLE"
  then
    record_fail \
      "N188 GPT duplicate-schema fixture construction failed"
  else
    expect_fail \
      "N188" \
      "GPT duplicate authoritative Finding schema rejected" \
      "$fixture" \
      0 \
      "GPT common Finding schema authoritative table mismatch"
  fi
else
  record_fail "N188 fixture construction failed"
fi

# N189. GPT Finding schema positive control.
if fixture="$(make_fixture N189)"; then
  expect_pass \
    "N189" \
    "GPT exact-one authoritative Finding schema accepted" \
    "$fixture"
else
  record_fail "N189 fixture construction failed"
fi

# N190. GPT Severity heading survives only in a fenced decoy.
if fixture="$(make_fixture N190)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "GPT_SEVERITY_FENCED_DECOY"
  then
    record_fail \
      "N190 GPT Severity fenced-decoy fixture construction failed"
  else
    expect_fail \
      "N190" \
      "GPT fenced Severity heading decoy rejected" \
      "$fixture" \
      0 \
      "GPT Severity headings are not exactly Critical/High/Medium/Low"
  fi
else
  record_fail "N190 fixture construction failed"
fi

# N191. GPT Severity heading survives only in an off-section H2.
if fixture="$(make_fixture N191)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "GPT_SEVERITY_OFF_SECTION_DECOY"
  then
    record_fail \
      "N191 GPT Severity off-section fixture construction failed"
  else
    expect_fail \
      "N191" \
      "GPT off-section Severity heading decoy rejected" \
      "$fixture" \
      0 \
      "GPT Severity headings are not exactly Critical/High/Medium/Low"
  fi
else
  record_fail "N191 fixture construction failed"
fi

# N192. GPT duplicate authoritative Severity section is rejected.
if fixture="$(make_fixture N192)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "GPT_SEVERITY_DUPLICATE_SECTION"
  then
    record_fail \
      "N192 GPT duplicate-Severity fixture construction failed"
  else
    expect_fail \
      "N192" \
      "GPT duplicate authoritative Severity section rejected" \
      "$fixture" \
      0 \
      "GPT Severity headings are not exactly Critical/High/Medium/Low"
  fi
else
  record_fail "N192 fixture construction failed"
fi

# N193. GPT Severity positive control.
if fixture="$(make_fixture N193)"; then
  expect_pass \
    "N193" \
    "GPT exact direct Critical/High/Medium/Low accepted" \
    "$fixture"
else
  record_fail "N193 fixture construction failed"
fi

# N194. GPT affected-consumer strict snapshot propagates parser failure.
if fixture="$(make_fixture N194)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/GPT-REVIEW-CONTRACT.md" \
    "GPT_UNMATCHED_FENCE"
  then
    record_fail \
      "N194 GPT unmatched-fence fixture construction failed"
  else
    expect_fail \
      "N194" \
      "GPT schema/severity strict snapshot parser failure is fail-closed" \
      "$fixture" \
      0 \
      "GPT schema/severity Markdown parser failed"
  fi
else
  record_fail "N194 fixture construction failed"
fi
# N195. PROJECT Branch rejects a duplicate anchor-bound canonical payload.
if fixture="$(make_fixture N195)"; then
  if ! z25_mutate_structural_fixture \
    "$fixture/docs/process/PROJECT-RULES.md" \
    "PROJECT_BRANCH_DUPLICATE_PAYLOAD"
  then
    record_fail \
      "N195 PROJECT Branch duplicate-payload fixture construction failed"
  else
    expect_fail \
      "N195" \
      "PROJECT Branch duplicate anchor-bound canonical payload rejected" \
      "$fixture" \
      0 \
      "PROJECT-RULES branch naming pattern missing"
  fi
else
  record_fail "N195 fixture construction failed"
fi

# P3. Positive tracked-mode acceptance path: exact-one, stage 0, regular mode.
if fixture="$(make_fixture P3)"; then
  tracked_setup_ok=1
  git -C "$fixture" init -q || tracked_setup_ok=0

  if [ "$tracked_setup_ok" -eq 1 ]; then
    while IFS= read -r rel
    do
      [ -n "$rel" ] || continue
      git -C "$fixture" add -- "$rel" || {
        tracked_setup_ok=0
        break
      }
    done <<EOF
$(required_files)
EOF
  fi

  if [ "$tracked_setup_ok" -eq 1 ]; then
    while IFS= read -r rel
    do
      [ -n "$rel" ] || continue
      entry="$(git -C "$fixture" ls-files -s -- "$rel")" || {
        tracked_setup_ok=0
        break
      }
      entry_count="$(printf '%s\n' "$entry" | awk 'NF { n++ } END { print n + 0 }')"
      mode="$(printf '%s\n' "$entry" | awk 'NF { print $1 }')"
      stage="$(printf '%s\n' "$entry" | awk 'NF { print $3 }')"

      if [ "$entry_count" -ne 1 ] || [ "$stage" != '0' ]; then
        tracked_setup_ok=0
        break
      fi

      case "$mode" in
        100644|100755) ;;
        *) tracked_setup_ok=0; break ;;
      esac
    done <<EOF
$(required_files)
EOF
  fi

  if [ "$tracked_setup_ok" -ne 1 ]; then
    record_fail "P3 tracked-mode valid fixture construction failed"
  else
    expect_pass \
      "P3" \
      "tracked-mode valid stage-0 fixture accepted" \
      "$fixture" \
      1
  fi
else
  record_fail "P3 fixture construction failed"
fi

# N196. Multiple non-empty index records are rejected even when first mode is valid.
if fixture="$(make_fixture N196)"; then
  tracked_setup_ok=1
  git -C "$fixture" init -q || tracked_setup_ok=0

  if [ "$tracked_setup_ok" -eq 1 ]; then
    while IFS= read -r rel
    do
      [ -n "$rel" ] || continue
      git -C "$fixture" add -- "$rel" || {
        tracked_setup_ok=0
        break
      }
    done <<EOF
$(required_files)
EOF
  fi

  if [ "$tracked_setup_ok" -eq 1 ]; then
    blob="$(git -C "$fixture" hash-object -- CLAUDE.md)" || tracked_setup_ok=0
  fi

  if [ "$tracked_setup_ok" -eq 1 ]; then
    git -C "$fixture" update-index --force-remove -- CLAUDE.md || tracked_setup_ok=0
  fi

  if [ "$tracked_setup_ok" -eq 1 ]; then
    printf '100644 %s 1\tCLAUDE.md\n100644 %s 2\tCLAUDE.md\n' \
      "$blob" "$blob" |
      git -C "$fixture" update-index --index-info || tracked_setup_ok=0
  fi

  if [ "$tracked_setup_ok" -eq 1 ]; then
    entry="$(git -C "$fixture" ls-files -s -- CLAUDE.md)" || tracked_setup_ok=0
    entry_count="$(printf '%s\n' "$entry" | awk 'NF { n++ } END { print n + 0 }')"
    first_mode="$(printf '%s\n' "$entry" | awk 'NF { print $1; exit }')"
    [ "$entry_count" -eq 2 ] || tracked_setup_ok=0
    [ "$first_mode" = '100644' ] || tracked_setup_ok=0
  fi

  if [ "$tracked_setup_ok" -ne 1 ]; then
    record_fail "N196 multiple-index-record fixture construction failed"
  else
    expect_fail \
      "N196" \
      "multiple Git index records rejected despite valid first mode" \
      "$fixture" \
      1 \
      "required Git index entry count is not exactly 1: CLAUDE.md"
  fi
else
  record_fail "N196 fixture construction failed"
fi

# N197. A single nonzero-stage index record is rejected distinctly.
if fixture="$(make_fixture N197)"; then
  tracked_setup_ok=1
  git -C "$fixture" init -q || tracked_setup_ok=0

  if [ "$tracked_setup_ok" -eq 1 ]; then
    while IFS= read -r rel
    do
      [ -n "$rel" ] || continue
      git -C "$fixture" add -- "$rel" || {
        tracked_setup_ok=0
        break
      }
    done <<EOF
$(required_files)
EOF
  fi

  if [ "$tracked_setup_ok" -eq 1 ]; then
    blob="$(git -C "$fixture" hash-object -- CLAUDE.md)" || tracked_setup_ok=0
  fi

  if [ "$tracked_setup_ok" -eq 1 ]; then
    git -C "$fixture" update-index --force-remove -- CLAUDE.md || tracked_setup_ok=0
  fi

  if [ "$tracked_setup_ok" -eq 1 ]; then
    printf '100644 %s 2\tCLAUDE.md\n' "$blob" |
      git -C "$fixture" update-index --index-info || tracked_setup_ok=0
  fi

  if [ "$tracked_setup_ok" -eq 1 ]; then
    entry="$(git -C "$fixture" ls-files -s -- CLAUDE.md)" || tracked_setup_ok=0
    entry_count="$(printf '%s\n' "$entry" | awk 'NF { n++ } END { print n + 0 }')"
    mode="$(printf '%s\n' "$entry" | awk 'NF { print $1 }')"
    stage="$(printf '%s\n' "$entry" | awk 'NF { print $3 }')"
    [ "$entry_count" -eq 1 ] || tracked_setup_ok=0
    [ "$mode" = '100644' ] || tracked_setup_ok=0
    [ "$stage" = '2' ] || tracked_setup_ok=0
  fi

  if [ "$tracked_setup_ok" -ne 1 ]; then
    record_fail "N197 nonzero-stage fixture construction failed"
  else
    expect_fail \
      "N197" \
      "single nonzero-stage Git index record rejected" \
      "$fixture" \
      1 \
      "required Git index stage is not 0: CLAUDE.md"
  fi
else
  record_fail "N197 fixture construction failed"
fi

if [ "$SELF_ERRORS" -ne 0 ]; then
  printf '%s\n' "VALIDATOR_SELF_TEST=FAIL"
  exit 1
fi

printf '%s\n' "VALIDATOR_SELF_TEST=PASS"
exit 0
