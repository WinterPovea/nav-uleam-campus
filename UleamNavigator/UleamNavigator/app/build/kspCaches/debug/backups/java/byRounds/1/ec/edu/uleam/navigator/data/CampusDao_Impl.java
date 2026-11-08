package ec.edu.uleam.navigator.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class CampusDao_Impl implements CampusDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<NodoEntity> __insertionAdapterOfNodoEntity;

  private final EntityInsertionAdapter<AristaEntity> __insertionAdapterOfAristaEntity;

  private final EntityInsertionAdapter<LugarEntity> __insertionAdapterOfLugarEntity;

  public CampusDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfNodoEntity = new EntityInsertionAdapter<NodoEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `nodos` (`id`,`latitud`,`longitud`,`tipo`,`descripcion_interna`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final NodoEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindDouble(2, entity.getLatitud());
        statement.bindDouble(3, entity.getLongitud());
        statement.bindString(4, entity.getTipo());
        if (entity.getDescripcionInterna() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getDescripcionInterna());
        }
      }
    };
    this.__insertionAdapterOfAristaEntity = new EntityInsertionAdapter<AristaEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `aristas` (`id`,`id_origen`,`id_destino`,`distancia_m`,`bidireccional`,`tiene_escaleras`,`nota`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AristaEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getIdOrigen());
        statement.bindLong(3, entity.getIdDestino());
        statement.bindDouble(4, entity.getDistanciaM());
        statement.bindLong(5, entity.getBidireccional());
        statement.bindLong(6, entity.getTieneEscaleras());
        if (entity.getNota() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getNota());
        }
      }
    };
    this.__insertionAdapterOfLugarEntity = new EntityInsertionAdapter<LugarEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `lugares` (`id`,`id_nodo`,`codigo`,`nombre`,`descripcion`,`categoria`,`etiquetas`,`busqueda`,`activo`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LugarEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getIdNodo());
        if (entity.getCodigo() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCodigo());
        }
        statement.bindString(4, entity.getNombre());
        if (entity.getDescripcion() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getDescripcion());
        }
        statement.bindString(6, entity.getCategoria());
        if (entity.getEtiquetas() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getEtiquetas());
        }
        statement.bindString(8, entity.getBusqueda());
        statement.bindLong(9, entity.getActivo());
      }
    };
  }

  @Override
  public Object insertNodos(final List<NodoEntity> l,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfNodoEntity.insert(l);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAristas(final List<AristaEntity> l,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAristaEntity.insert(l);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertLugares(final List<LugarEntity> l,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLugarEntity.insert(l);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object countNodos(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM nodos";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object nodos(final Continuation<? super List<NodoEntity>> $completion) {
    final String _sql = "SELECT * FROM nodos";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<NodoEntity>>() {
      @Override
      @NonNull
      public List<NodoEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfLatitud = CursorUtil.getColumnIndexOrThrow(_cursor, "latitud");
          final int _cursorIndexOfLongitud = CursorUtil.getColumnIndexOrThrow(_cursor, "longitud");
          final int _cursorIndexOfTipo = CursorUtil.getColumnIndexOrThrow(_cursor, "tipo");
          final int _cursorIndexOfDescripcionInterna = CursorUtil.getColumnIndexOrThrow(_cursor, "descripcion_interna");
          final List<NodoEntity> _result = new ArrayList<NodoEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final NodoEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final double _tmpLatitud;
            _tmpLatitud = _cursor.getDouble(_cursorIndexOfLatitud);
            final double _tmpLongitud;
            _tmpLongitud = _cursor.getDouble(_cursorIndexOfLongitud);
            final String _tmpTipo;
            _tmpTipo = _cursor.getString(_cursorIndexOfTipo);
            final String _tmpDescripcionInterna;
            if (_cursor.isNull(_cursorIndexOfDescripcionInterna)) {
              _tmpDescripcionInterna = null;
            } else {
              _tmpDescripcionInterna = _cursor.getString(_cursorIndexOfDescripcionInterna);
            }
            _item = new NodoEntity(_tmpId,_tmpLatitud,_tmpLongitud,_tmpTipo,_tmpDescripcionInterna);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object aristas(final Continuation<? super List<AristaEntity>> $completion) {
    final String _sql = "SELECT * FROM aristas";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AristaEntity>>() {
      @Override
      @NonNull
      public List<AristaEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfIdOrigen = CursorUtil.getColumnIndexOrThrow(_cursor, "id_origen");
          final int _cursorIndexOfIdDestino = CursorUtil.getColumnIndexOrThrow(_cursor, "id_destino");
          final int _cursorIndexOfDistanciaM = CursorUtil.getColumnIndexOrThrow(_cursor, "distancia_m");
          final int _cursorIndexOfBidireccional = CursorUtil.getColumnIndexOrThrow(_cursor, "bidireccional");
          final int _cursorIndexOfTieneEscaleras = CursorUtil.getColumnIndexOrThrow(_cursor, "tiene_escaleras");
          final int _cursorIndexOfNota = CursorUtil.getColumnIndexOrThrow(_cursor, "nota");
          final List<AristaEntity> _result = new ArrayList<AristaEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AristaEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final int _tmpIdOrigen;
            _tmpIdOrigen = _cursor.getInt(_cursorIndexOfIdOrigen);
            final int _tmpIdDestino;
            _tmpIdDestino = _cursor.getInt(_cursorIndexOfIdDestino);
            final double _tmpDistanciaM;
            _tmpDistanciaM = _cursor.getDouble(_cursorIndexOfDistanciaM);
            final int _tmpBidireccional;
            _tmpBidireccional = _cursor.getInt(_cursorIndexOfBidireccional);
            final int _tmpTieneEscaleras;
            _tmpTieneEscaleras = _cursor.getInt(_cursorIndexOfTieneEscaleras);
            final String _tmpNota;
            if (_cursor.isNull(_cursorIndexOfNota)) {
              _tmpNota = null;
            } else {
              _tmpNota = _cursor.getString(_cursorIndexOfNota);
            }
            _item = new AristaEntity(_tmpId,_tmpIdOrigen,_tmpIdDestino,_tmpDistanciaM,_tmpBidireccional,_tmpTieneEscaleras,_tmpNota);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object lugares(final Continuation<? super List<LugarEntity>> $completion) {
    final String _sql = "SELECT * FROM lugares WHERE activo = 1 ORDER BY nombre";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LugarEntity>>() {
      @Override
      @NonNull
      public List<LugarEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfIdNodo = CursorUtil.getColumnIndexOrThrow(_cursor, "id_nodo");
          final int _cursorIndexOfCodigo = CursorUtil.getColumnIndexOrThrow(_cursor, "codigo");
          final int _cursorIndexOfNombre = CursorUtil.getColumnIndexOrThrow(_cursor, "nombre");
          final int _cursorIndexOfDescripcion = CursorUtil.getColumnIndexOrThrow(_cursor, "descripcion");
          final int _cursorIndexOfCategoria = CursorUtil.getColumnIndexOrThrow(_cursor, "categoria");
          final int _cursorIndexOfEtiquetas = CursorUtil.getColumnIndexOrThrow(_cursor, "etiquetas");
          final int _cursorIndexOfBusqueda = CursorUtil.getColumnIndexOrThrow(_cursor, "busqueda");
          final int _cursorIndexOfActivo = CursorUtil.getColumnIndexOrThrow(_cursor, "activo");
          final List<LugarEntity> _result = new ArrayList<LugarEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LugarEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final int _tmpIdNodo;
            _tmpIdNodo = _cursor.getInt(_cursorIndexOfIdNodo);
            final String _tmpCodigo;
            if (_cursor.isNull(_cursorIndexOfCodigo)) {
              _tmpCodigo = null;
            } else {
              _tmpCodigo = _cursor.getString(_cursorIndexOfCodigo);
            }
            final String _tmpNombre;
            _tmpNombre = _cursor.getString(_cursorIndexOfNombre);
            final String _tmpDescripcion;
            if (_cursor.isNull(_cursorIndexOfDescripcion)) {
              _tmpDescripcion = null;
            } else {
              _tmpDescripcion = _cursor.getString(_cursorIndexOfDescripcion);
            }
            final String _tmpCategoria;
            _tmpCategoria = _cursor.getString(_cursorIndexOfCategoria);
            final String _tmpEtiquetas;
            if (_cursor.isNull(_cursorIndexOfEtiquetas)) {
              _tmpEtiquetas = null;
            } else {
              _tmpEtiquetas = _cursor.getString(_cursorIndexOfEtiquetas);
            }
            final String _tmpBusqueda;
            _tmpBusqueda = _cursor.getString(_cursorIndexOfBusqueda);
            final int _tmpActivo;
            _tmpActivo = _cursor.getInt(_cursorIndexOfActivo);
            _item = new LugarEntity(_tmpId,_tmpIdNodo,_tmpCodigo,_tmpNombre,_tmpDescripcion,_tmpCategoria,_tmpEtiquetas,_tmpBusqueda,_tmpActivo);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
