# The application ID is `app.remindme.alarm`

The HiOS freezer config on the owner's Camon 40 (version 2.0.9) exempts package names matching `alarm` and `calendar` from its hold-back of scheduled alarms. The application ID ends in `.alarm` to land on that list. Whether the match works for a sideloaded app is untested; the name costs nothing, and an application ID cannot change later without a reinstall that loses the stored tasks, so it is chosen before the first install.
