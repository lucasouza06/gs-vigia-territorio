package br.com.vigiaterritorio.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.vigiaterritorio.VigiaApp
import br.com.vigiaterritorio.di.AppContainer

/** Fábrica de ViewModel com acesso ao AppContainer e ao SavedStateHandle (argumentos da rota). */
inline fun <reified VM : ViewModel> vigiaFactory(
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[APPLICATION_KEY] as VigiaApp
        create(app.container, createSavedStateHandle())
    }
}
