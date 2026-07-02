package yupay.turismo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppSettings::class, Visit::class, Product::class, PendingOp::class, TtsPreference::class],
    version = 13
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun visitDao(): VisitDao
    abstract fun productDao(): ProductDao
    abstract fun pendingOpDao(): PendingOpDao
    // tts_preferences: modelo de voz activo por idioma (v12). Con fallbackToDestructiveMigration
    // no hace falta migración manual (las tablas se recrean si cambia el esquema).
    abstract fun ttsPreferenceDao(): TtsPreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v12 → v13: añade la columna `uuid` (identidad estable entre dispositivos para el merge P2P) a
         * `products` y `visits`, rellenando las filas existentes con un id único por fila
         * (`hex(randomblob(16))`). Es la primera migración manual del proyecto; se mantiene
         * `fallbackToDestructiveMigration` como red de seguridad para esquemas no contemplados.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE products SET uuid = lower(hex(randomblob(16)))")
                db.execSQL("ALTER TABLE visits ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE visits SET uuid = lower(hex(randomblob(16)))")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .addMigrations(MIGRATION_12_13)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
