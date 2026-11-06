package ec.edu.uleam.navigator.data

import android.content.Context
import androidx.room.*

@Database(entities = [NodoEntity::class, AristaEntity::class, LugarEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): CampusDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "campus.db")
                .build().also { inst = it }
        }
    }
}
