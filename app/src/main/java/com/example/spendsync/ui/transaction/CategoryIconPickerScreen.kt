package com.example.spendsync.ui.transaction

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.example.spendsync.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.example.spendsync.ui.components.Text
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.spendsync.data.remote.IconifyApiClient
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppIconButton
import com.example.spendsync.ui.components.ButtonSize
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
    // The user's current categories for this transaction type — checked
    // against, never guessed at, so "already exists" is always a real match.
    existingCategoryNames: Set<String> = emptySet(),
    existingIconIds: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onCategoryCreated: (name: String, iconId: String) -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val errorColor = MaterialTheme.colorScheme.error
    // Icons are recolored server-side by Iconify — needs a real theme-matching
    // hex, not a fixed dark tint that would vanish against a dark card.
    val iconColorHex = remember(NeutralBlack) { "#%06X".format(0xFFFFFF and NeutralBlack.toArgb()) }

    var name by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchTouched by remember { mutableStateOf(false) }
    var selectedIcon by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // Contextual duplicate check — a real match against this user's existing
    // categories, not a heuristic guess.
    val nameAlreadyExists = remember(name, existingCategoryNames) {
        val trimmed = name.trim()
        trimmed.isNotEmpty() && existingCategoryNames.any { it.equals(trimmed, ignoreCase = true) }
    }

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
                AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back), onClick = onDismiss)
                Text(
                    text = tr(R.string.add_category),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack,
                )
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(text = tr(R.string.category_name), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeutralMid)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(tr(R.string.e_g_friday_takeout), color = NeutralMid, fontSize = 14.sp) },
                    singleLine = true,
                    isError = nameAlreadyExists,
                    supportingText = if (nameAlreadyExists) {
                        { Text(tr(R.string.s_1_already_exists, name.trim()), color = errorColor, fontSize = 12.sp) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NeutralWhite,
                        unfocusedContainerColor = NeutralWhite,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = NeutralLight,
                        errorContainerColor = NeutralWhite,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                Text(text = tr(R.string.search_icons), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeutralMid)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchTouched = true; searchQuery = it },
                    placeholder = { Text(tr(R.string.e_g_pizza_rent_gift), color = NeutralMid, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeutralMid) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            AppIconButton(Icons.Default.Close, tr(R.string.clear_search), onClick = { searchTouched = true; searchQuery = "" }, tint = NeutralMid)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NeutralWhite,
                        unfocusedContainerColor = NeutralWhite,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = NeutralLight,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Icon results — weighted so it only takes leftover space and
            // never pushes the confirm button below the visible screen ───────
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    searchQuery.trim().length < 2 -> CenteredHint(tr(R.string.keep_typing_to_search_icons), NeutralMid)
                    isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                    searchError != null -> CenteredHint(searchError!!, NeutralMid)
                    results.isEmpty() -> CenteredHint(tr(R.string.no_icons_found_for_1, searchQuery), NeutralMid)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(results) { iconId ->
                            val isSelected = iconId == selectedIcon
                            // Contextual, not guessed: this icon is flagged only when
                            // it's the exact icon a category the user already has uses.
                            val alreadyUsed = iconId in existingIconIds
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) accentColor.copy(alpha = 0.15f) else NeutralWhite)
                                    .then(
                                        if (alreadyUsed && !isSelected) {
                                            Modifier.border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                        } else Modifier
                                    )
                                    .clickable { selectedIcon = iconId },
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = IconifyApiClient.iconUrl(iconId, colorHex = iconColorHex),
                                    contentDescription = iconId,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.padding(10.dp),
                                )
                                if (alreadyUsed && !isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                                            .background(NeutralBlack.copy(alpha = 0.55f))
                                            .padding(vertical = 2.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = tr(R.string.in_use),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
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
                                            contentDescription = tr(R.string.selected),
                                            tint = MaterialTheme.colorScheme.background,
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
            val canConfirm = name.trim().isNotBlank() && selectedIcon != null && !nameAlreadyExists
            AppButton(
                text = tr(R.string.add_category_2),
                onClick = { onCategoryCreated(name.trim(), selectedIcon!!) },
                modifier = Modifier.navigationBarsPadding().padding(20.dp),
                size = ButtonSize.Large,
                enabled = canConfirm,
                fullWidth = true,
            )
        }
    }
}

@Composable
private fun CenteredHint(text: String, color: Color) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
        Text(text = text, fontSize = 12.sp, color = color)
    }
}
