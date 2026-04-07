import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.example.yap.data.model.UserItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Расширение для контекста (обычно вверху файла или в отдельном Singleton)
private val Context.dataStore by preferencesDataStore(name = "user_settings")

class UserPreferences(private val context: Context) {
    private val gson = Gson()
    private object Keys {
        val CURRENT_STARS = intPreferencesKey("current_stars")
        val LAST_EXIT_TIME = longPreferencesKey("last_exit_time")
        val SAVED_USERS = stringPreferencesKey("saved_users")
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

    val usersData: Flow<List<UserItem>?> = context.dataStore.data.map { prefs ->
        val json = prefs[Keys.SAVED_USERS]
        if (json.isNullOrEmpty()) return@map null

        // Превращаем JSON строку обратно в список объектов
        val type = object : TypeToken<List<UserItem>>() {}.type
        gson.fromJson(json, type)
    }
    suspend fun saveEnergy(stars: Int, timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CURRENT_STARS] = stars
            prefs[Keys.LAST_EXIT_TIME] = timestamp
        }
    }

    suspend fun saveUsers(users: List<UserItem>) {
        context.dataStore.edit { prefs ->
            val json = gson.toJson(users)
            prefs[Keys.SAVED_USERS] = json
        }
    }
}