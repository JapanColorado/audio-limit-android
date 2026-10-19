package dev.russell.audiolimit.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.russell.audiolimit.AppGraph
import dev.russell.audiolimit.appGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppGraph) -> VM): VM {
    val graph = LocalContext.current.appGraph
    return viewModel(factory = viewModelFactory { initializer { create(graph) } })
}

fun ticker(periodMillis: Long): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMillis)
    }
}
