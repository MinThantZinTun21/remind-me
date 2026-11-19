package app.remindme.wording

/** A file size as the user reads it: "1.44 GB" from a gigabyte up, "343 MB" below. */
fun gigabytes(bytes: Long): String =
    if (bytes >= 1_000_000_000) "%.2f GB".format(bytes / 1e9) else "%.0f MB".format(bytes / 1e6)
