package com.erpbarcode.scanner.ui.theme

// Each language is named in itself rather than translated per locale — that's the convention
// for language pickers, and it means you can still find your own language when the UI is
// currently in one you don't read.
enum class Language(val tag: String, val label: String) {
    ENGLISH   ("en", "English"),
    CROATIAN  ("hr", "Hrvatski"),
    SLOVENIAN ("sl", "Slovenščina"),
    MACEDONIAN("mk", "Македонски"),
}
