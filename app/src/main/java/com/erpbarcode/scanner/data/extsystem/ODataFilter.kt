package com.erpbarcode.scanner.data.extsystem

/**
 * [value] as a string literal in an OData `$filter`: quoted, with every apostrophe inside doubled.
 *
 * Every value that goes into a filter goes through here. A code with an apostrophe in it (a
 * location called `O'BRIEN`, a document no. typed by hand) would otherwise end the literal early,
 * and NAV would refuse the whole query as malformed instead of finding nothing.
 */
internal fun odataLiteral(value: String): String = "'" + value.replace("'", "''") + "'"
