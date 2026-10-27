# Tasks

## 1. Save writes mark value 1

- [x] 1.1 Add a `GprFile` round-trip test on the SEG-Y fixture `TestData/Parsers/Segy/2020-07-29-14-37-42-gpr.sgy`:
  mark 300 traces, save with `save(File, IndexRange)` to a temp file, reopen and assert the same trace indices are
  marked and the mark byte of every saved trace is 1 or 0; also save a sub-range holding 300 marks and assert the
  same for it; verify the test fails before 1.2
- [x] 1.2 In `GprFile.save(File, IndexRange)`, replace the running `markNumber` with a named constant mark value 1
  for marked traces, keeping 0 for unmarked ones; verify by the tests from 1.1 passing and `mvn test` staying green

## 2. Verify

- [x] 2.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 2.2 If the `code-review` skill is available, invoke it (not the `code-review:code-review` plugin) on the
  uncommitted changes with this change's proposal and specs as the completeness reference, fix critical and major
  issues, and report the scores and any issues left open
