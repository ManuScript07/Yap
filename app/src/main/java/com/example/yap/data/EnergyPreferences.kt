import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Расширение для контекста (обычно вверху файла или в отдельном Singleton)
private val Context.dataStore by preferencesDataStore(name = "energy_settings")

class EnergyPreferences(private val context: Context) {

    private object Keys {
        val CURRENT_STARS = intPreferencesKey("current_stars")
        val LAST_EXIT_TIME = longPreferencesKey("last_exit_time")
    }

    // Поток данных (Flow) для наблюдения за звездами
//    val energyData: Flow<Pair<Int, Long>> = context.dataStore.data.map { prefs ->
//        val stars = prefs[Keys.CURRENT_STARS] ?: 10 // Дефолтное значение 10
//        val time = prefs[Keys.LAST_EXIT_TIME] ?: 0L
//        Pair(stars, time)
//    }
    val energyData: Flow<Pair<Int?, Long?>> = context.dataStore.data.map { prefs ->
        Pair(prefs[Keys.CURRENT_STARS], prefs[Keys.LAST_EXIT_TIME])
    }
    suspend fun saveEnergy(stars: Int, timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CURRENT_STARS] = stars
            prefs[Keys.LAST_EXIT_TIME] = timestamp
        }
    }
}