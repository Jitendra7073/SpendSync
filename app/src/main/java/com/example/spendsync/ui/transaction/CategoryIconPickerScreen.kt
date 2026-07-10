package com.example.spendsync.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.spendsync.data.remote.IconifyApiClient
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.IconifyRepository
import kotlinx.coroutines.delay

/**
 * Full-screen "Add Category" flow: a name field (what gets saved) and a
 * separate icon-search field, kept independent so typing a search query
 * (e.g. "pizza") never overwrites a name the user already typed (e.g.
 * "Friday Takeout"). Results render as a tappable icon grid; the confirm
 * button stays pinned at the bottom regardless of result count.
 */
@Composable
fun CategoryIconPickerScreen(
    iconifyRepository: IconifyRepository,
    accentColor: Color,
    onDismiss: () -> Unit,
    onCategoryCreated: (name: String, iconId: String) -> Unit,
) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant

    var name by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchTouched by remember { mutableStateOf(false) }
    var selectedIcon by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // Convenience: search follows the name field until the user edits search
    // directly, so typing "Pizza" once already surfaces pizza icons — but
    // editing search independently (e.g. to try "food" instead) decouples it.
    LaunchedEffect(name) {
        if (!searchTouched) searchQuery = name
    }

    LaunchedEffect(searchQuery) {
        selectedIcon = null
        val trimmed = searchQuery.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        delay(400) // debounce — don't hit the API on every keystroke
        isSearching = true
        when (val res = iconifyRepository.searchIcons(trimmed)) {
            is AuthResult.Success -> {
                results = res.data
                searchError = null
            }
            is AuthResult.Error -> {
                results = emptyList()
                searchError = res.message
            }
        }
        isSearching = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NeutralOffWhite),
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeutralBlack)
                }
                Text(
                    text = "Add Category",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack,
                )
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(text = "Category Name", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeutralMid)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Friday Takeout", color = NeutralMid, fontSize = 14.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NeutralWhite,
                        unfocusedContainerColor = NeutralWhite,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                Text(text = "Search Icons", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeutralMid)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchTouched = true; searchQuery = it },
                    placeholder = { Text("e.g. pizza, rent, gift...", color = NeutralMid, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeutralMid) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchTouched = true; searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = NeutralMid)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NeutralWhite,
                        unfocusedContainerColor = NeutralWhite,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Icon results — weighted so it only takes leftover space and
            // never pushes the confirm button below the visible screen ───────
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    searchQuery.trim().length < 2 -> CenteredHint("Keep typing to search icons", NeutralMid)
                    isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                    searchError != null -> CenteredHint(searchError!!, NeutralMid)
                    results.isEmpty() -> CenteredHint("No icons found for \"$searchQuery\"", NeutralMid)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(results) { iconId ->
                            val isSelected = iconId == selectedIcon
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) accentColor.copy(alpha = 0.15f) else NeutralWhite)
                                    .clickable { selectedIcon = iconId },
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = IconifyApiClient.iconUrl(iconId, colorHex = "#111827"),
                                    contentDescription = iconId,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.padding(10.dp),
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(accentColor),
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.padding(2.dp).height(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Confirm button — always visible, fixed at the bottom ──────────
            val canConfirm = name.trim().isNotBlank() && selectedIcon != null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (canConfirm) accentColor else NeutralLight)
                    .clickable(enabled = canConfirm) {
                        onCategoryCreated(name.trim(), selectedIcon!!)
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Add Category",
                    color = if (canConfirm) Color.White else NeutralMid,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CenteredHint(text: String, color: Color) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
        Text(text = text, fontSize = 13.sp, color = color)
    }
}
