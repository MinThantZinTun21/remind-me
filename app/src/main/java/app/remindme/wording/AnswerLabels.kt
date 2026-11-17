package app.remindme.wording

import app.remindme.core.SNOOZE_LENGTH

/** What the answer buttons say, on a notification and on the alarm screen alike. */
object AnswerLabels {
    const val DONE = "Done"
    val SNOOZE = "Snooze ${SNOOZE_LENGTH.toMinutes()} min"
    const val STOP = "Stop"
}
