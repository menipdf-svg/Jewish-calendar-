package de.jeanlucmakiola.calendula

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.jeanlucmakiola.calendula.data.calendar.CalendarDataSource
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepositoryImpl
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Path

/** A Preferences DataStore in [dir]; [scope] pins its writes, e.g. to a test dispatcher. */
fun testDataStore(dir: Path, name: String, scope: CoroutineScope? = null): DataStore<Preferences> {
    val file = { dir.resolve("$name.preferences_pb").toFile() }
    return if (scope == null) {
        PreferenceDataStoreFactory.create(produceFile = file)
    } else {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = file)
    }
}

fun testPrefs(dir: Path, scope: CoroutineScope? = null): CalendarPrefs =
    CalendarPrefs(testDataStore(dir, "test_prefs", scope))

fun testSettings(dir: Path, scope: CoroutineScope? = null): SettingsPrefs =
    SettingsPrefs(testDataStore(dir, "test_settings", scope))

/** A [CalendarRepositoryImpl] over [source], with fresh prefs in [dir] unless given. */
fun testRepository(
    source: CalendarDataSource,
    dir: Path,
    io: CoroutineDispatcher,
    prefs: CalendarPrefs = testPrefs(dir),
    settings: SettingsPrefs = testSettings(dir),
): CalendarRepositoryImpl = CalendarRepositoryImpl(source, prefs, settings, io)

/** Installs [dispatcher] as `Dispatchers.Main` around each test. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) = Dispatchers.setMain(dispatcher)
    override fun afterEach(context: ExtensionContext) = Dispatchers.resetMain()
}
