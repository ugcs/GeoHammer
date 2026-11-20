## Context

See proposal.md for the problem. The relevant code:

- `Parser.setHeaders` builds a `header -> cell index` map and takes the index from the current map size. A repeated
  name overwrites its entry without growing the map, so every later name gets an index one too low.
- Everything after the parser addresses a column by its name: `ColumnSchema`, `GeoData.getValue(header)`, template
  mappings, series settings, `CsvWriter`/`FixedWidthWriter`. Two columns with one name cannot be carried through the
  model, so names have to be unique before the schema is built.
- Header names come from `Parser.readHeaders`: the header line (`CsvParser`), or `DataMapping.getIndexedHeaders()` for
  files without a header line (`CsvParser`, `FixedWidthParser`). The second already names unmapped columns
  `column_<index>`.
- `CsvWriter` writes the names of the loaded `ColumnSchema`; `FixedWidthWriter` reads values by `Parser.getHeaders()`.
  Both work with whatever unique names the parser produced.

## Goals / Non-Goals

**Goals:**

- One place that makes header names unique, used for every source of names.
- No change in the writers, the model or the template editor.

**Non-Goals:**

- Remembering the original names to restore them on save.
- A shared column naming utility for the template editor.

## Decisions

### Rename in `Parser.parseFile`, between `readHeaders` and `setHeaders`

A private method of `Parser` takes the header tokens and returns the unique names; `parseFile` passes the result to
`setHeaders`. This covers the header line and the names taken from the template with one call, and the rest of the
parser (`buildValueSchema`, `buildFileSchema`, date and time lookup by header) keeps working on names.

Alternative: rename inside `setHeaders`. Rejected: `CsvWriter` calls `setHeaders` after a save with the saved names,
and a setter that renames and reports warnings has a hidden side effect.

### Two passes over the names

1. Collect the non-empty tokens as names written in the file.
2. Go left to right. A non-empty token not yet assigned keeps its name. An empty token starts from
   `column_<index>`. If the candidate is already assigned, or is generated and equals a name written in the file,
   append `_1`, `_2`, … to the base name until the result is neither assigned nor written in the file.

A single pass would let a generated name take the name of a column further right (`Header,Header,Header_1`), and a
template or setting that refers to `Header_1` would then read the wrong column.

### `setHeaders` is not changed

It takes the index of a name from the current map size, which is correct for unique names. Both callers pass unique
names: `parseFile` after the renaming, and `CsvWriter` with the names of a `ColumnSchema`.

### One constant for the `column_` prefix

`DataMapping` gets a constant for the prefix, used by `DataMapping.getIndexedHeaders`, `Template.initIndexedHeaders`
and the parser. `template.model.Defaults.INDEX_HEADER_PREFIX` of the template editor is left as is: the editor is
out of scope, and `format` must not depend on the editor package.

### Warning through the existing `Warnings`

The parser adds one warning with the new names of the renamed columns, next to the existing warning about
non-printable characters. `Loader` shows parser warnings in a dialog after an interactive open and the MCP
`OpenFile` tool returns them; neither changes.

### Load only: no undo, no unsaved flag

Renaming happens while the file is parsed on the loader's background thread, before the file reaches the model.
No snapshot, undo frame, event or cache invalidation is involved. The file opens as not modified, as it does after
missing values are filled on load; the new names reach the disk on the next save.

### Tests go through the parser and `CsvFile`

The renaming method is private. Tests write small files to a temp directory and check column names, row values and
warnings through `CsvParser.parse`, and the saved header line through `CsvFile.save`, as `CsvFileTest` does.

## Risks / Trade-offs

- [The header line of the source file changes on the first save] → The load warning says so. Accepted to keep the
  writers unchanged.
- [A template whose `match-regex` matches the repeated names literally stops matching the file after it is saved]
  → Accepted as rare; the user adjusts the regex or the template.
- [A file with trailing empty header cells, such as `A,B,C,,`, shows the warning on every open until it is saved]
  → Accepted; saving once writes the names.
- [A file with repeated names that opened before shows other data under the repeated name: the first column, not
  the last] → This is the intended fix; the template editor preview already shows the first one.
- [Series settings saved for an empty column name no longer apply] → Negligible: such a column had no visible name.
- [A template that maps two positions to one name gets the second renamed to `<name>_1`, and the template settings
  of that name go to the first column] → Out of scope; such a template is invalid.
