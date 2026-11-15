# libphext-kotlin

A Kotlin/JVM implementation of phext (11-dimensional plain hypertext),
tracking the canonical Rust implementation at
https://github.com/wbic16/libphext-rs — this port is at **0.6.1**,
feature-identical to libphext-rs 0.6.1.

Package: `io.phext.libphext`

## What is Phext?

Phext is hierarchical digital memory: scrolls of plain text addressed in a
9-dimensional hierarchy by ASCII control-code dimension breaks, of the form

```
library.shelf.series/collection.volume.book/chapter.section.scroll
```

For example: `5.5.5/4.6.7/9.5.9`. Coordinate components are unbounded
(as of libphext 0.4.0); in this port they are held in `Long`s.

> **Dense graphs:** phext is sparse by design. A fully-populated dense
> graph contains the product of its dimensions' extents in scrolls.
> Materializing, exploding, or indexing such a graph costs memory
> proportional to its populated scroll count. Keep graphs sparse.

## Surface (mirrors libphext-rs 0.6.1)

- **Core** (`Phext.kt`): `fetch`, `insert`, `replace`, `remove`,
  `rangeReplace`, `phokenize`, `nextScroll`, `explode`, `implode`,
  `merge`, `subtract`, `normalize`, `expand`, `contract`, `index`,
  `offset`, `checksum` (XXH3-128, implemented in pure Kotlin in
  `Xxh3.kt`), `manifest`, `soundexV1`, `createSummary`, `navmap`,
  `textmap`, `checkForCowbell`, `getSubspaceCoordinates`.
- **Coordinates** (`Coordinate.kt`): `Coordinate` (with `ZCoordinate` /
  `YCoordinate` / `XCoordinate` arms), `toCoordinate` (the lenient
  parser), `Coordinate.tryParse` / `Coordinate.parse` (the strict
  `TryFrom` equivalent — parse failures return null / throw
  `PhextParseException`, never panic), `PositionedScroll`, `Range`.
- **Binary scrolls** (`Binary.kt`): the canonical
  `phext-binary:1:base64` scroll format — `Binary.encodeScroll`,
  `decodeScroll`, `isBinaryScroll`, `insertBinary`, `replaceBinary`,
  `fetchBinary`, plus the base64-verbatim ingestion rule in
  `insertBytes`, `replaceBytes`, `ingest`, and `phokenizeBytes`.
  Base64 is `java.util.Base64` with an explicit strictness check
  matching Rust's `base64` STANDARD engine.
- **The xz seal** (`Seal.kt`): `Seal.seal` / `sealBytes` / `unseal` /
  `unsealString` — a standard xz stream at preset 6 with a CRC64 check,
  via XZ for Java (`org.tukaani:xz`, a pure-Java XZ implementation).
  Seals cross-verify with libphext-rs both directions.
- **Chains + multiverse addressing** (`Chain.kt`, new in 0.6.0):
  chain break 0x02 / chain close 0x03, `Chain`, `parseChains`,
  `serializeChains`, and `$N:<address>` multiverse fetch
  (`Chain.fetch`, `parseUniverseAddress`). A bare address means
  universe 1; fetch misses return "".
- **Line/column addressing** (`Addressing.kt`, new in 0.6.1):
  `ScrollPosition`, `PointAddress`, `RangeAddress`,
  `parsePointAddress`, `parseRangeAddress`, and the chain operations
  `Chain.resolvePoint` / `fetchPoint` / `resolveRange` / `fetchRange`.
  A point is `[$N:]<coordinate>@<line>,<column>`, optionally with
  `+<offsetChars>`; a range is two points joined by one hyphen, stored
  canonicalized (endpoints ascending, `$1:` never written). Positions
  are 1-based caret boundaries (half-open); columns and offsets count
  Unicode scalar values (code points) — never UTF-16 code units or
  bytes. Resolution errors fail (null / ""), never clamp.

## Build & test

Prerequisites: a JDK 21 and Gradle 8.10.2 (or use the Gradle wrapper).

```sh
scripts/build.sh   # compile + jar
scripts/test.sh    # full JUnit 5 suite, incl. the shared chain fixtures
```

The test suite ports the libphext-rs suites (phext, phokenize, binary,
seal, chain) and runs the shared 0.6 chain conformance fixtures in
`tests/fixtures/chains/` (10 cases, 42 fetches — structure, fetches with
found flags, and byte-identical round-trips, verified against
manifest.json) plus the shared 0.6.1 range conformance fixtures in
`tests/fixtures/ranges/` (6 cases, 69 ops — point/range parsing and
canonical form, resolution offsets, and point/range fetches, consumed
from `expected.tsv`).

## Publish

There is no registry publish for this port: distribution is a git tag,
as with libphext-cpp.

```sh
scripts/publish.sh                    # dry run: test + build + summary
PUBLISH_CONFIRM=1 scripts/publish.sh  # tag v<version> on exo and push it
```

## License

MIT (as declared by the libphext family; libphext-rs `Cargo.toml`
carries `license = "MIT"`).
