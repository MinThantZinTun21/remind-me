package app.remindme.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.remindme.wording.short
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** The seven days in the order the phone's region starts its week. */
fun weekInPhoneOrder(): List<DayOfWeek> {
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return List(7) { first.plus(it.toLong()) }
}

/**
 * Seven joined day buttons, lit for the days in [selected]. Used on the action card and in the task form,
 * so a day looks and works the same in both.
 */
@Composable
fun DayButtonRow(selected: Set<DayOfWeek>, onTap: (DayOfWeek) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val days = weekInPhoneOrder()
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        days.forEachIndexed { index, day ->
            val on = day in selected
            val shape = when (index) {
                0 -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 2.dp, bottomEnd = 2.dp)
                days.lastIndex -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp, topStart = 2.dp, bottomStart = 2.dp)
                else -> RoundedCornerShape(2.dp)
            }
            Surface(
                shape = shape,
                color = if (on) colors.secondary else colors.surfaceContainerHighest,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
                    .toggleable(value = on, enabled = enabled, role = Role.Checkbox, onValueChange = { onTap(day) }),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        day.short(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) colors.onSecondary else colors.onSurface,
                    )
                }
            }
        }
    }
}
