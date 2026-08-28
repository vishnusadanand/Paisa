package com.visa.paisa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.visa.paisa.data.Txn
import com.visa.paisa.parse.Categories
import com.visa.paisa.ui.theme.CategoryColors
import com.visa.paisa.util.Dates
import com.visa.paisa.util.Money

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionHeader(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CategoryPill(
    category: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val accent = CategoryColors[category]
    val base = if (selected) accent else accent.copy(alpha = 0.14f)
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(base)
            .border(
                width = 1.dp,
                color = if (selected) accent else accent.copy(alpha = 0.35f),
                shape = RoundedCornerShape(50),
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Coloured initial disc — cheaper to read down a list than a row of similar icons. */
@Composable
fun MerchantAvatar(merchant: String?, category: String, modifier: Modifier = Modifier) {
    val letter = merchant?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(CategoryColors[category].copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium,
            color = CategoryColors[category],
        )
    }
}

@Composable
fun TxnRow(txn: Txn, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isCredit = txn.direction == "CREDIT"
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MerchantAvatar(txn.merchant, txn.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = txn.merchant ?: "Unknown payee",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append(Dates.timeOfDay(txn.timestamp))
                        if (txn.category == Categories.UNCATEGORIZED) append(" · needs a category")
                        else append(" · ${txn.category}")
                        txn.bank?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (isCredit) "+" else "") + Money.rupees(txn.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    txn.excluded -> MaterialTheme.colorScheme.onSurfaceVariant
                    isCredit -> CategoryColors["Groceries"]
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (txn.excluded) {
                Text(
                    "excluded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
