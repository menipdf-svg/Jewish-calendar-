package de.jeanlucmakiola.calendula.data.di

import javax.inject.Qualifier

/** The process-wide `CoroutineScope` that app-lifetime singletons share their flows in. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
