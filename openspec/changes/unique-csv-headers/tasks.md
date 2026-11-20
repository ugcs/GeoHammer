## 1. Parser

- [x] 1.1 Add a constant for the `column_` prefix to `DataMapping` and use it in `DataMapping.getIndexedHeaders` and
  `Template.initIndexedHeaders`; verify `mvn -Dtest=CsvParserTest test` still passes
- [x] 1.2 Add a private method to `Parser` that makes header names unique by the rules of the `csv-loading` delta
  spec (two passes, see design.md) and call it in `parseFile` before `setHeaders`; verify with the tests of 2.1
- [x] 1.3 Add a parser warning that lists the new names of renamed columns and says they will be saved under these
  names; verify the file still parses and `mvn -Dtest=CsvParserTest test` passes

## 2. Tests

- [x] 2.1 Add tests to `CsvParserTest` that parse temp files with the headers of the "Column names are unique after
  load" scenarios and check column names and row values; verify they pass
- [x] 2.2 Add a test to `CsvParserTest` that a file with unique names has no warnings; verify it passes
- [x] 2.3 Add a test to `CsvFileTest` that opens a file with the header `Header,Header,,` plus position columns,
  saves it, and checks the saved header line, the saved values and the column names after opening it again; verify
  it passes

## 3. Verify

- [x] 3.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 3.2 Invoke the `code-review` skill (not the `code-review:code-review` plugin) on the uncommitted changes with
  this change's proposal and specs as the completeness reference, fix critical and major issues, and report the
  scores and any issues left open
