# Searching the connections

Date: 2026-10-19. Builds on [the original design](2026-07-22-linkedin-archive-explorer-design.md);
a record of the decisions, not a spec to keep in sync (the code is the truth).

## Intent

The export lists the user's connections (`Connections.csv`: first name, last name, profile
URL, e-mail, company, position, connection date). The search now covers them, so "who do I
know at Shodo" or "who is a staff engineer" is answered by the same box as everything else.

## Decisions

- **A connection is searched field by field**, never as one concatenated text: full name
  ("First Last", so the whole name is found), company, position. A match cannot straddle two
  fields. E-mail is not searched (17 of 756 have one; nothing to browse).
- **The result says where it matched**: each excerpt is labelled with its field. Groups are
  `CONNECTIONS`, last, newest connection first, each linking to the profile.
- **Sealed types carry it** (this needed Java 21 for pattern matching in `switch`, hence the
  floor raised from 17): `Searchable` (`Content`, `Message`, `Connection`) knows how to find a
  term in itself and returns a `SearchHit` (`ContentHit`, `MessageHit`, `ConnectionHit`). The
  engine is a loop over `find`; the renderers `switch` over the hit. A private message is its
  own type with a non-optional `Correspondent`, instead of a `Content` that sometimes has one.
- **Two enums, two jobs**: `ContentType` (article, post, comment) only discriminates inside
  `Content`; `Section` (in `application`) keys, orders and titles the groups of a result and is
  derived from the hit by an exhaustive `switch`. They had been one enum serving both, which
  is why `Content` had to refuse `MESSAGE` at runtime.
- **No new port**: `ConnectionsContentSource` implements `ContentSource`, whose `load()` now
  returns `List<? extends Searchable>`; each adapter narrows it. The composition root only
  gained one line.
- **`Connections.csv` opens with a notes paragraph** before its header row: the table is cut
  at `First Name,`. Dates are `18 Jul 2026` (English month names), unlike the other files.
- **Members who hide their profile** come with no name and no link (4 of 756): skipped, there
  is nothing to show or click.

## Not done

- No filter to search connections only (or to leave them out); they are always searched.
- `Invitations.csv` and `Member_Follows` are not read. The export has no status for an
  invitation, so refused ones cannot be told from pending ones (see the conversation notes).
