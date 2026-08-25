package com.xovrix.quickvoicereminder.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xovrix.quickvoicereminder.R
import com.xovrix.quickvoicereminder.ui.theme.*
import com.xovrix.quickvoicereminder.ui.viewmodel.DashboardStats
import com.xovrix.quickvoicereminder.ui.viewmodel.TaskFilter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

@Composable
fun StatsHeader(
    stats: DashboardStats,
    selectedFilter: TaskFilter,
    onFilterSelected: (TaskFilter) -> Unit,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    selectedDate: String?,
    onDateSelected: (String?) -> Unit,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onClearCompleted: () -> Unit,
    isAdsFree: Boolean = false,
    onUpgradeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val openDatePicker = {
        val calendar = parseDateOrNow(selectedDate)
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                onDateSelected(picked.toString())
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Title & Tagline
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "QuickVoice Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = "QuickVoice",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Speak or type — scheduled in seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                Surface(
                    onClick = onUpgradeClick,
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAdsFree) EmeraldGreen.copy(alpha = 0.15f) else ElectricIndigo.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isAdsFree) EmeraldGreen.copy(alpha = 0.4f) else ElectricIndigo.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.testTag("upgrade_ad_free_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isAdsFree) Icons.Filled.CheckCircle else Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (isAdsFree) EmeraldGreen else AmberFlame,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isAdsFree) "Pro" else "Ad-Free $1/mo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isAdsFree) EmeraldGreen else ElectricIndigo
                        )
                    }
                }
            }
        }

        // Fast Metric Pills Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricBadge(
                label = "Pending",
                count = stats.activeCount,
                color = ElectricIndigo,
                isSelected = selectedFilter == TaskFilter.ALL,
                onClick = { onFilterSelected(TaskFilter.ALL) },
                modifier = Modifier.weight(1f)
            )
            MetricBadge(
                label = "Today",
                count = stats.todayCount,
                color = CyanAccent,
                isSelected = selectedFilter == TaskFilter.TODAY,
                onClick = { onFilterSelected(TaskFilter.TODAY) },
                modifier = Modifier.weight(1f)
            )
            MetricBadge(
                label = "Urgent Nag",
                count = stats.nagCount,
                color = AmberFlame,
                isSelected = selectedFilter == TaskFilter.NAG_MODE,
                onClick = { onFilterSelected(TaskFilter.NAG_MODE) },
                modifier = Modifier.weight(1f)
            )
            MetricBadge(
                label = "Done",
                count = stats.completedCount,
                color = EmeraldGreen,
                isSelected = selectedFilter == TaskFilter.COMPLETED,
                onClick = { onFilterSelected(TaskFilter.COMPLETED) },
                modifier = Modifier.weight(1f)
            )
        }

        // Search Bar & Clear Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChanged,
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_tasks_field"),
                placeholder = {
                    Text(
                        "Search reminders...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchChanged("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = ElectricIndigo,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            if (selectedFilter == TaskFilter.COMPLETED && stats.completedCount > 0) {
                TextButton(
                    onClick = onClearCompleted,
                    modifier = Modifier.testTag("clear_completed_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = "Clear Completed",
                        tint = CoralRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Clear Done",
                        color = CoralRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Category & Date Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date Filter Chip
            FilterChip(
                selected = selectedDate != null,
                onClick = openDatePicker,
                label = {
                    Text(
                        if (selectedDate != null) {
                            try {
                                val d = LocalDate.parse(selectedDate)
                                if (d == LocalDate.now()) "Today"
                                else if (d == LocalDate.now().plusDays(1)) "Tomorrow"
                                else d.format(DateTimeFormatter.ofPattern("MMM d"))
                            } catch (e: Exception) {
                                selectedDate
                            }
                        } else "Pick Date"
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "Date Filter",
                        tint = if (selectedDate != null) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (selectedDate != null) {
                        IconButton(
                            onClick = { onDateSelected(null) },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear Date",
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("date_filter_chip")
            )

            // Category Filter: All
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All Categories") },
                shape = RoundedCornerShape(12.dp)
            )

            CategoryUtils.ALL_CATEGORIES.forEach { category ->
                val isSelected = selectedCategory.equals(category, ignoreCase = true)
                val catColor = CategoryUtils.getCategoryColor(category)
                val catIcon = CategoryUtils.getCategoryIcon(category)
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelected(category) },
                    label = {
                        Text(
                            text = category,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = catIcon,
                            contentDescription = category,
                            tint = if (isSelected) catColor else catColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) catColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        selectedBorderColor = catColor,
                        borderWidth = if (isSelected) 1.5.dp else 1.dp
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = catColor.copy(alpha = 0.15f)
                    )
                )
            }
        }
    }
}

private fun parseDateOrNow(dateStr: String?): Calendar {
    val cal = Calendar.getInstance()
    if (dateStr != null) {
        try {
            val parsed = LocalDate.parse(dateStr)
            cal.set(parsed.year, parsed.monthValue - 1, parsed.dayOfMonth)
        } catch (_: Exception) {}
    }
    return cal
}

@Composable
private fun MetricBadge(
    label: String,
    count: Int,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}
