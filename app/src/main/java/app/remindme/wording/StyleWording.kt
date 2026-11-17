package app.remindme.wording

import app.remindme.core.DeliveryStyle

/** A delivery style as the user reads it. */
val DeliveryStyle.label: String
    get() = when (this) {
        DeliveryStyle.NOTIFICATION -> "Notification"
        DeliveryStyle.ALARM -> "Alarm"
    }
