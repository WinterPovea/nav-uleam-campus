package ec.edu.uleam.navigator.data

import androidx.room.*

@Entity(tableName = "nodos")
data class NodoEntity(
    @PrimaryKey val id: Int,
    val latitud: Double,
    val longitud: Double,
    val tipo: String, // CONEXION | ENTRADA | DESTINO | ZONA_SEGURA
    @ColumnInfo(name = "descripcion_interna") val descripcionInterna: String?
)

@Entity(
    tableName = "aristas",
    foreignKeys = [
        ForeignKey(NodoEntity::class, ["id"], ["id_origen"]),
        ForeignKey(NodoEntity::class, ["id"], ["id_destino"])
    ],
    indices = [Index("id_origen"), Index("id_destino")]
)
data class AristaEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "id_origen") val idOrigen: Int,
    @ColumnInfo(name = "id_destino") val idDestino: Int,
    @ColumnInfo(name = "distancia_m") val distanciaM: Double,
    val bidireccional: Int = 1,
    @ColumnInfo(name = "tiene_escaleras") val tieneEscaleras: Int = 0,
    val nota: String?
)

@Entity(
    tableName = "lugares",
    foreignKeys = [ForeignKey(NodoEntity::class, ["id"], ["id_nodo"])],
    indices = [Index("id_nodo"), Index("categoria")]
)
data class LugarEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "id_nodo") val idNodo: Int,
    val codigo: String?,
    val nombre: String,
    val descripcion: String?,
    val categoria: String,
    val etiquetas: String?,
    val busqueda: String,
    val activo: Int = 1
)
