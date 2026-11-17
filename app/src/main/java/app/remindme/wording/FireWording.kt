package app.remindme.wording

import app.remindme.core.FireKind

/** A kind of fire as the Fire log names it. */
val FireKind.label: String
    get() = when (this) {
        FireKind.WARNING_1, FireKind.WARNING_2 -> "Lead-time warning"
        FireKind.DUE -> "Due time"
        FireKind.SNOOZE -> "Snooze coming back"
    }
