package ec.edu.uleam.navigator.data

import androidx.room.*

@Dao
interface CampusDao {
    @Insert suspend fun insertNodos(l: List<NodoEntity>)
    @Insert suspend fun insertAristas(l: List<AristaEntity>)
    @Insert suspend fun insertLugares(l: List<LugarEntity>)

    @Query("SELECT COUNT(*) FROM nodos") suspend fun countNodos(): Int
    @Query("SELECT * FROM nodos") suspend fun nodos(): List<NodoEntity>
    @Query("SELECT * FROM aristas") suspend fun aristas(): List<AristaEntity>
    @Query("SELECT * FROM lugares WHERE activo = 1 ORDER BY nombre") suspend fun lugares(): List<LugarEntity>
}
