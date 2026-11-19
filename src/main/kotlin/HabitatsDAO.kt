import java.io.File
import java.sql.ResultSet
import java.sql.Statement

/**
 * Creator Jonathan Rosa Aledón
 * Entidad/es: Habitat & Vegetación
 */

/** Representa una fila de la tabla 'habitats'. */
data class Habitat(
    val idHabitat: Int = 0,
    val nombre: String,
    val clima: String,
    val altitudMedia: Int,
    val temperaturaMedia: Double
)

class HabitatsDAO {

    private val conexion = Conexion.conexion

    /** Al crear el DAO, se asegura de que la tabla 'habitats' exista. */
    init {
        conexion.createStatement().use {
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS habitats (
                    id_habitat INT PRIMARY KEY AUTO_INCREMENT,
                    nombre VARCHAR(100) NOT NULL,
                    clima VARCHAR(100),
                    altitud_media INT,
                    temperatura_media DOUBLE
                )
                """.trimIndent()
            )
        }
    }

    // ====================== OPERACIONES DE MENÚ ======================

    /**
     * Lee 'datos/habitats.csv', valida cada línea (5 columnas y números correctos)
     * e inserta las válidas en la BD. Muestra un resumen con insertados, repetidos e inválidas.
     */
    fun importarCsv() {
        val fichero = File("datos/habitats.csv")
        if (!fichero.exists()) {
            println("No se encuentra el fichero ${fichero.path}")
            return
        }

        val habitats = mutableListOf<Habitat>()
        var erroneas = 0

        fichero.readLines(Charsets.UTF_8)
            .drop(1) // cabecera
            .filter { it.isNotBlank() }
            .forEach { linea ->
                val c = linea.removePrefix("\uFEFF").split(",").map { it.trim() }
                val id = c.getOrNull(0)?.toIntOrNull()
                val altitud = c.getOrNull(3)?.toIntOrNull()
                val temperatura = c.getOrNull(4)?.toDoubleOrNull()

                if (c.size != 5 || id == null || altitud == null || temperatura == null) {
                    erroneas++
                    println("Línea inválida: $linea")
                } else {
                    habitats.add(Habitat(id, c[1], c[2], altitud, temperatura))
                }
            }

        val insertados = insertarVarios(habitats)
        println(
            "Importación terminada: $insertados insertados, " +
                    "${habitats.size - insertados} ya existían, $erroneas líneas inválidas."
        )
    }

    /** Muestra por pantalla todos los hábitats en formato de tabla. */
    fun leerInformacion() {
        val lista = obtenerTodos()
        if (lista.isEmpty()) {
            println("No hay registros.")
            return
        }
        println("%-5s %-20s %-18s %-12s %s".format("ID", "NOMBRE", "CLIMA", "ALTITUD (m)", "TEMP (°C)"))
        lista.forEach {
            println(
                "%-5d %-20s %-18s %-12d %.1f".format(
                    it.idHabitat, it.nombre, it.clima, it.altitudMedia, it.temperaturaMedia
                )
            )
        }
    }

    /** Pide los datos por teclado y crea un hábitat nuevo (el ID lo genera MySQL). */
    fun anadirRegistro() {
        val nombre = pedirTexto("Nombre: ")
        val clima = pedirTexto("Clima: ")
        val altitud = pedirEntero("Altitud media (m): ")
        val temperatura = pedirDecimal("Temperatura media (°C): ")

        val nuevoId = insertar(
            Habitat(nombre = nombre, clima = clima, altitudMedia = altitud, temperaturaMedia = temperatura)
        )
        if (nuevoId != null) println("Registro añadido con ID $nuevoId.")
    }

    /**
     * Modifica un hábitat existente por ID. Si el usuario deja un campo vacío
     * (o escribe un número no válido), se conserva el valor actual.
     */
    fun modificarRegistro() {
        val id = pedirEntero("ID del hábitat a modificar: ")
        val actual = obtenerPorId(id)
        if (actual == null) {
            println("No existe ningún hábitat con el ID $id.")
            return
        }

        println("Deja el campo vacío para mantener el valor actual.")
        val nombre = pedirOpcional("Nombre [${actual.nombre}]: ") ?: actual.nombre
        val clima = pedirOpcional("Clima [${actual.clima}]: ") ?: actual.clima
        val altitud = pedirOpcional("Altitud media [${actual.altitudMedia}]: ")
            ?.toIntOrNull() ?: actual.altitudMedia
        val temperatura = pedirOpcional("Temperatura media [${actual.temperaturaMedia}]: ")
            ?.replace(",", ".")?.toDoubleOrNull() ?: actual.temperaturaMedia

        if (actualizar(Habitat(id, nombre, clima, altitud, temperatura))) {
            println("Registro modificado.")
        }
    }

    /**
     * Elimina un hábitat por ID pidiendo confirmación. Si tiene vegetación asociada
     * en 'habitat_vegetacion', avisa y borra solo esas relaciones (no la vegetación).
     */
    fun eliminarRegistro() {
        val id = pedirEntero("ID del hábitat a eliminar: ")
        val actual = obtenerPorId(id)
        if (actual == null) {
            println("No existe ningún hábitat con el ID $id.")
            return
        }

        val numRelaciones = contarRelaciones(id)
        if (numRelaciones > 0) {
            println("AVISO: Existen $numRelaciones registro(s) asociado(s) a este hábitat en la tabla 'habitat_vegetacion'.")
            print("¿Confirmas que deseas eliminar '${actual.nombre}' junto con sus registros asociados? (s/n): ")
        } else {
            print("¿Eliminar '${actual.nombre}'? (s/n): ")
        }

        if (readln().trim().lowercase() == "s") {
            if (eliminar(id)) println("Registro eliminado correctamente.")
        } else {
            println("Operación cancelada.")
        }
    }

    /**
     * Opción 3 del menú principal: elimina un hábitat junto con su vegetación.
     * Muestra cuántas relaciones y cuántas especies se borrarán, pide confirmación
     * y delega el trabajo en [eliminarHabitatConVegetacion].
     */
    fun eliminarConVegetacion() {
        val id = pedirEntero("ID del hábitat a eliminar junto con su vegetación: ")
        val actual = obtenerPorId(id)
        if (actual == null) {
            println("No existe ningún hábitat con el ID $id.")
            return
        }

        val numRelaciones = contarRelaciones(id)
        println("El hábitat '${actual.nombre}' tiene $numRelaciones vegetación(es) asociada(s).")
        println("Se borrará el hábitat y la vegetación que no esté asociada a ningún otro hábitat.")
        print("¿Confirmas la eliminación? (s/n): ")

        if (readln().trim().lowercase() == "s") {
            val borradas = eliminarHabitatConVegetacion(id)
            if (borradas != null) {
                println("Hábitat eliminado junto con $borradas vegetación(es).")
            }
        } else {
            println("Operación cancelada.")
        }
    }

    // ====================== ACCESO A DATOS ======================

    /** Inserta un hábitat nuevo (el ID lo genera MySQL). Devuelve el ID generado o null si falla. */
    fun insertar(h: Habitat): Int? {
        val sql = "INSERT INTO habitats (nombre, clima, altitud_media, temperatura_media) VALUES (?, ?, ?, ?)"
        return try {
            conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                ps.setString(1, h.nombre)
                ps.setString(2, h.clima)
                ps.setInt(3, h.altitudMedia)
                ps.setDouble(4, h.temperaturaMedia)
                ps.executeUpdate()
                ps.generatedKeys.use { rs -> if (rs.next()) rs.getInt(1) else null }
            }
        } catch (e: Exception) {
            println("Error al insertar: ${e.message}")
            null
        }
    }

    /**
     * Inserta una lista de hábitats en una sola transacción conservando los IDs del CSV.
     * Ignora los IDs que ya existen. Si algo falla, hace rollback. Devuelve los insertados.
     */
    fun insertarVarios(lista: List<Habitat>): Int {
        val sql =
            "INSERT IGNORE INTO habitats (id_habitat, nombre, clima, altitud_media, temperatura_media) VALUES (?, ?, ?, ?, ?)"
        var insertados = 0
        try {
            conexion.autoCommit = false
            conexion.prepareStatement(sql).use { ps ->
                for (h in lista) {
                    ps.setInt(1, h.idHabitat)
                    ps.setString(2, h.nombre)
                    ps.setString(3, h.clima)
                    ps.setInt(4, h.altitudMedia)
                    ps.setDouble(5, h.temperaturaMedia)
                    insertados += ps.executeUpdate()
                }
            }
            conexion.commit()
        } catch (e: Exception) {
            conexion.rollback()
            println("Error en la importación, se deshicieron los cambios: ${e.message}")
            insertados = 0
        } finally {
            conexion.autoCommit = true
        }
        return insertados
    }

    /** Devuelve todos los hábitats ordenados por ID. */
    fun obtenerTodos(): List<Habitat> {
        val lista = mutableListOf<Habitat>()
        conexion.createStatement().use { st ->
            st.executeQuery("SELECT * FROM habitats ORDER BY id_habitat").use { rs ->
                while (rs.next()) lista.add(rs.aHabitat())
            }
        }
        return lista
    }

    /** Busca un hábitat por su ID. Devuelve null si no existe. */
    fun obtenerPorId(id: Int): Habitat? {
        conexion.prepareStatement("SELECT * FROM habitats WHERE id_habitat = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs ->
                return if (rs.next()) rs.aHabitat() else null
            }
        }
    }

    /** Actualiza todos los campos de un hábitat existente. Devuelve true si se modificó alguna fila. */
    fun actualizar(h: Habitat): Boolean {
        val sql =
            "UPDATE habitats SET nombre = ?, clima = ?, altitud_media = ?, temperatura_media = ? WHERE id_habitat = ?"
        return try {
            conexion.prepareStatement(sql).use { ps ->
                ps.setString(1, h.nombre)
                ps.setString(2, h.clima)
                ps.setInt(3, h.altitudMedia)
                ps.setDouble(4, h.temperaturaMedia)
                ps.setInt(5, h.idHabitat)
                ps.executeUpdate() > 0
            }
        } catch (e: Exception) {
            println("Error al actualizar: ${e.message}")
            false
        }
    }

    /**
     * Elimina un hábitat y sus relaciones en 'habitat_vegetacion' dentro de una transacción
     * (primero la tabla intermedia, luego la principal). La vegetación NO se borra.
     */
    fun eliminar(id: Int): Boolean {
        return try {
            conexion.autoCommit = false

            // 1. Borrar primero los registros asociados en la tabla intermedia
            conexion.prepareStatement("DELETE FROM habitat_vegetacion WHERE id_habitat = ?").use { ps ->
                ps.setInt(1, id)
                ps.executeUpdate()
            }

            // 2. Borrar el registro de la tabla principal
            val eliminado = conexion.prepareStatement("DELETE FROM habitats WHERE id_habitat = ?").use { ps ->
                ps.setInt(1, id)
                ps.executeUpdate() > 0
            }

            conexion.commit()
            eliminado
        } catch (e: Exception) {
            conexion.rollback()
            println("Error al eliminar: ${e.message}")
            false
        } finally {
            conexion.autoCommit = true
        }
    }

    /**
     * Elimina un hábitat junto con su vegetación en UNA transacción:
     *  1. Guarda los IDs de la vegetación asociada al hábitat.
     *  2. Borra las relaciones del hábitat en 'habitat_vegetacion'.
     *  3. Borra cada vegetación que haya quedado sin ningún hábitat (huérfana).
     *  4. Borra el hábitat.
     * Si algo falla se hace rollback y no se modifica nada.
     * Devuelve cuántas vegetaciones se borraron, o null si hubo error o el hábitat no existía.
     */
    fun eliminarHabitatConVegetacion(idHabitat: Int): Int? {
        return try {
            conexion.autoCommit = false

            // 1. IDs de la vegetación asociada a este hábitat
            val idsVegetacion = mutableListOf<Int>()
            conexion.prepareStatement("SELECT id_vegetacion FROM habitat_vegetacion WHERE id_habitat = ?").use { ps ->
                ps.setInt(1, idHabitat)
                ps.executeQuery().use { rs ->
                    while (rs.next()) idsVegetacion.add(rs.getInt(1))
                }
            }

            // 2. Borrar las relaciones de este hábitat (tabla intermedia)
            conexion.prepareStatement("DELETE FROM habitat_vegetacion WHERE id_habitat = ?").use { ps ->
                ps.setInt(1, idHabitat)
                ps.executeUpdate()
            }

            // 3. Borrar solo la vegetación que ya no pertenece a ningún otro hábitat
            var vegetacionBorrada = 0
            val sqlHuerfana = """
                DELETE FROM vegetacion
                WHERE id_vegetacion = ?
                AND NOT EXISTS (SELECT 1 FROM habitat_vegetacion WHERE id_vegetacion = ?)
            """.trimIndent()
            conexion.prepareStatement(sqlHuerfana).use { ps ->
                for (idVeg in idsVegetacion) {
                    ps.setInt(1, idVeg)
                    ps.setInt(2, idVeg)
                    vegetacionBorrada += ps.executeUpdate()
                }
            }

            // 4. Borrar el hábitat
            val habitatBorrado = conexion.prepareStatement("DELETE FROM habitats WHERE id_habitat = ?").use { ps ->
                ps.setInt(1, idHabitat)
                ps.executeUpdate() > 0
            }

            if (!habitatBorrado) {
                conexion.rollback()
                println("No existe ningún hábitat con el ID $idHabitat.")
                null
            } else {
                conexion.commit()
                vegetacionBorrada
            }
        } catch (e: Exception) {
            conexion.rollback()
            println("Error al eliminar, se deshicieron los cambios: ${e.message}")
            null
        } finally {
            conexion.autoCommit = true
        }
    }

    /** Convierte la fila actual de un ResultSet en un objeto [Habitat]. */
    private fun ResultSet.aHabitat() = Habitat(
        idHabitat = getInt("id_habitat"),
        nombre = getString("nombre"),
        clima = getString("clima") ?: "",
        altitudMedia = getInt("altitud_media"),
        temperaturaMedia = getDouble("temperatura_media")
    )

    /** Cuenta cuántas filas de 'habitat_vegetacion' están asociadas a un hábitat. Devuelve 0 si hay error. */
    fun contarRelaciones(idHabitat: Int): Int {
        val sql = "SELECT COUNT(*) FROM habitat_vegetacion WHERE id_habitat = ?"
        return try {
            conexion.prepareStatement(sql).use { ps ->
                ps.setInt(1, idHabitat)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        } catch (e: Exception) {
            println("Error al comprobar relaciones: ${e.message}")
            0
        }
    }
}