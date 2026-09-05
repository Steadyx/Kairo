package com.kairo.reader.ui.library

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Extra font scaling trades a second column for readable text and reachable actions. */
@Composable
internal fun libraryGridCells(): GridCells =
    GridCells.Adaptive(minSize = 320.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
