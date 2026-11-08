package ec.edu.uleam.navigator.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile CampusDao _campusDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `nodos` (`id` INTEGER NOT NULL, `latitud` REAL NOT NULL, `longitud` REAL NOT NULL, `tipo` TEXT NOT NULL, `descripcion_interna` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `aristas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `id_origen` INTEGER NOT NULL, `id_destino` INTEGER NOT NULL, `distancia_m` REAL NOT NULL, `bidireccional` INTEGER NOT NULL, `tiene_escaleras` INTEGER NOT NULL, `nota` TEXT, FOREIGN KEY(`id_origen`) REFERENCES `nodos`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`id_destino`) REFERENCES `nodos`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_aristas_id_origen` ON `aristas` (`id_origen`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_aristas_id_destino` ON `aristas` (`id_destino`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `lugares` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `id_nodo` INTEGER NOT NULL, `codigo` TEXT, `nombre` TEXT NOT NULL, `descripcion` TEXT, `categoria` TEXT NOT NULL, `etiquetas` TEXT, `busqueda` TEXT NOT NULL, `activo` INTEGER NOT NULL, FOREIGN KEY(`id_nodo`) REFERENCES `nodos`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lugares_id_nodo` ON `lugares` (`id_nodo`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lugares_categoria` ON `lugares` (`categoria`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '1cb8a91620fbe2eb83f71c22cc822b48')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `nodos`");
        db.execSQL("DROP TABLE IF EXISTS `aristas`");
        db.execSQL("DROP TABLE IF EXISTS `lugares`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsNodos = new HashMap<String, TableInfo.Column>(5);
        _columnsNodos.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNodos.put("latitud", new TableInfo.Column("latitud", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNodos.put("longitud", new TableInfo.Column("longitud", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNodos.put("tipo", new TableInfo.Column("tipo", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNodos.put("descripcion_interna", new TableInfo.Column("descripcion_interna", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysNodos = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesNodos = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoNodos = new TableInfo("nodos", _columnsNodos, _foreignKeysNodos, _indicesNodos);
        final TableInfo _existingNodos = TableInfo.read(db, "nodos");
        if (!_infoNodos.equals(_existingNodos)) {
          return new RoomOpenHelper.ValidationResult(false, "nodos(ec.edu.uleam.navigator.data.NodoEntity).\n"
                  + " Expected:\n" + _infoNodos + "\n"
                  + " Found:\n" + _existingNodos);
        }
        final HashMap<String, TableInfo.Column> _columnsAristas = new HashMap<String, TableInfo.Column>(7);
        _columnsAristas.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAristas.put("id_origen", new TableInfo.Column("id_origen", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAristas.put("id_destino", new TableInfo.Column("id_destino", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAristas.put("distancia_m", new TableInfo.Column("distancia_m", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAristas.put("bidireccional", new TableInfo.Column("bidireccional", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAristas.put("tiene_escaleras", new TableInfo.Column("tiene_escaleras", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAristas.put("nota", new TableInfo.Column("nota", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAristas = new HashSet<TableInfo.ForeignKey>(2);
        _foreignKeysAristas.add(new TableInfo.ForeignKey("nodos", "NO ACTION", "NO ACTION", Arrays.asList("id_origen"), Arrays.asList("id")));
        _foreignKeysAristas.add(new TableInfo.ForeignKey("nodos", "NO ACTION", "NO ACTION", Arrays.asList("id_destino"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesAristas = new HashSet<TableInfo.Index>(2);
        _indicesAristas.add(new TableInfo.Index("index_aristas_id_origen", false, Arrays.asList("id_origen"), Arrays.asList("ASC")));
        _indicesAristas.add(new TableInfo.Index("index_aristas_id_destino", false, Arrays.asList("id_destino"), Arrays.asList("ASC")));
        final TableInfo _infoAristas = new TableInfo("aristas", _columnsAristas, _foreignKeysAristas, _indicesAristas);
        final TableInfo _existingAristas = TableInfo.read(db, "aristas");
        if (!_infoAristas.equals(_existingAristas)) {
          return new RoomOpenHelper.ValidationResult(false, "aristas(ec.edu.uleam.navigator.data.AristaEntity).\n"
                  + " Expected:\n" + _infoAristas + "\n"
                  + " Found:\n" + _existingAristas);
        }
        final HashMap<String, TableInfo.Column> _columnsLugares = new HashMap<String, TableInfo.Column>(9);
        _columnsLugares.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLugares.put("id_nodo", new TableInfo.Column("id_nodo", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLugares.put("codigo", new TableInfo.Column("codigo", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLugares.put("nombre", new TableInfo.Column("nombre", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLugares.put("descripcion", new TableInfo.Column("descripcion", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLugares.put("categoria", new TableInfo.Column("categoria", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLugares.put("etiquetas", new TableInfo.Column("etiquetas", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLugares.put("busqueda", new TableInfo.Column("busqueda", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLugares.put("activo", new TableInfo.Column("activo", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLugares = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysLugares.add(new TableInfo.ForeignKey("nodos", "NO ACTION", "NO ACTION", Arrays.asList("id_nodo"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesLugares = new HashSet<TableInfo.Index>(2);
        _indicesLugares.add(new TableInfo.Index("index_lugares_id_nodo", false, Arrays.asList("id_nodo"), Arrays.asList("ASC")));
        _indicesLugares.add(new TableInfo.Index("index_lugares_categoria", false, Arrays.asList("categoria"), Arrays.asList("ASC")));
        final TableInfo _infoLugares = new TableInfo("lugares", _columnsLugares, _foreignKeysLugares, _indicesLugares);
        final TableInfo _existingLugares = TableInfo.read(db, "lugares");
        if (!_infoLugares.equals(_existingLugares)) {
          return new RoomOpenHelper.ValidationResult(false, "lugares(ec.edu.uleam.navigator.data.LugarEntity).\n"
                  + " Expected:\n" + _infoLugares + "\n"
                  + " Found:\n" + _existingLugares);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "1cb8a91620fbe2eb83f71c22cc822b48", "043d69ffec57582c19462df7ff7679b4");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "nodos","aristas","lugares");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `aristas`");
      _db.execSQL("DELETE FROM `lugares`");
      _db.execSQL("DELETE FROM `nodos`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(CampusDao.class, CampusDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public CampusDao dao() {
    if (_campusDao != null) {
      return _campusDao;
    } else {
      synchronized(this) {
        if(_campusDao == null) {
          _campusDao = new CampusDao_Impl(this);
        }
        return _campusDao;
      }
    }
  }
}
