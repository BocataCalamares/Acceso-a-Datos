import java.io.File
import java.sql.ResultSet
import java.sql.Statement

/** Representa una fila de la tabla 'vegetacion'. */
data class Vegetacion(
    val idVegetacion: Int = 0,
    val nombre: String,
    val tipo: String,
    val alturaMedia: Int,
    val coberturaMedia: Double
)

class VegetacionDAO {

    private val conexion = Conexion.conexion

    /** Al crear el DAO, se asegura de que la tabla 'vegetacion' exista. */
    init {
        conexion.createStatement().use {
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS vegetacion (
                    id_vegetacion INT PRIMARY KEY AUTO_INCREMENT,
                    nombre VARCHAR(100) NOT NULL,
                    tipo VARCHAR(100),
                    altura_media INT,
                    cobertura_media DOUBLE
                )
                """.trimIndent()
            )
        }
    }

    // ====================== OPERACIONES DE MENÚ ======================

    /**
     * Lee 'datos/vegetacion.csv', valida cada línea (5 columnas y números correctos)
     * e inserta las válidas en la BD. Muestra un resumen con insertados, repetidos e inválidas.
     */
    fun importarCsv() {
        val fichero = File("datos/vegetacion.csv")
        if (!fichero.exists()) {
            println("No se encuentra el fichero ${fichero.path}")
            return
        }

        val lista = mutableListOf<Vegetacion>()
        var erroneas = 0

        fichero.readLines(Charsets.UTF_8)
            .drop(1) // cabecera
            .filter { it.isNotBlank() }
            .forEach { linea ->
                val c = linea.removePrefix("\uFEFF").split(",").map { it.trim() }
                val id = c.getOrNull(0)?.toIntOrNull()
                val altura = c.getOrNull(3)?.toIntOrNull()
                val cobertura = c.getOrNull(4)?.toDoubleOrNull()

                if (c.size != 5 || id == null || altura == null || cobertura == null) {
                    erroneas++
                    println("Línea inválida: $linea")
                } else {
                    lista.add(Vegetacion(id, c[1], c[2], altura, cobertura))
                }
            }

        val insertados = insertarVarios(lista)
        println(
            "Importación terminada: $insertados insertados, " +
                    "${lista.size - insertados} ya existían, $erroneas líneas inválidas."
        )
    }

    /** Muestra por pantalla toda la vegetación en formato de tabla. */
    fun leerInformacion() {
        val lista = obtenerTodos()
        if (lista.isEmpty()) {
            println("No hay registros.")
            return
        }
        println("%-5s %-24s %-12s %-12s %s".format("ID", "NOMBRE", "TIPO", "ALTURA (m)", "COBERTURA (%)"))
        lista.forEach {
            println(
                "%-5d %-24s %-12s %-12d %.1f".format(
                    it.idVegetacion, it.nombre, it.tipo, it.alturaMedia, it.coberturaMedia
                )
            )
        }
    }

    /** Pide los datos por teclado y crea una vegetación nueva (el ID lo genera MySQL). */
    fun anadirRegistro() {
        val nombre = pedirTexto("Nombre: ")
        val tipo = pedirTexto("Tipo: ")
        val altura = pedirEntero("Altura media (m): ")
        val cobertura = pedirDecimal("Cobertura media (%): ")

        val nuevoId = insertar(
            Vegetacion(nombre = nombre, tipo = tipo, alturaMedia = altura, coberturaMedia = cobertura)
        )
        if (nuevoId != null) println("Registro añadido con ID $nuevoId.")
    }

    /**
     * Modifica una vegetación existente por ID. Si el usuario deja un campo vacío
     * (o escribe un número no válido), se conserva el valor actual.
     */
    fun modificarRegistro() {
        val id = pedirEntero("ID de la vegetación a modificar: ")
        val actual = obtenerPorId(id)
        if (actual == null) {
            println("No existe ninguna vegetación con el ID $id.")
            return
        }

        println("Deja el campo vacío para mantener el valor actual.")
        val nombre = pedirOpcional("Nombre [${actual.nombre}]: ") ?: actual.nombre
        val tipo = pedirOpcional("Tipo [${actual.tipo}]: ") ?: actual.tipo
        val altura = pedirOpcional("Altura media [${actual.alturaMedia}]: ")
            ?.toIntOrNull() ?: actual.alturaMedia
        val cobertura = pedirOpcional("Cobertura media [${actual.coberturaMedia}]: ")
            ?.replace(",", ".")?.toDoubleOrNull() ?: actual.coberturaMedia

        if (actualizar(Vegetacion(id, nombre, tipo, altura, cobertura))) {
            println("Registro modificado.")
        }
    }

    /**
     * Elimina una vegetación por ID pidiendo confirmación. Si está asociada a hábitats
     * en 'habitat_vegetacion', avisa y borra también esas relaciones (los hábitats no se borran).
     */
    fun eliminarRegistro() {
        val id = pedirEntero("ID de la vegetación a eliminar: ")
        val actual = obtenerPorId(id)
        if (actual == null) {
            println("No existe ninguna vegetación con el ID $id.")
            return
        }

        val numRelaciones = contarRelaciones(id)
        if (numRelaciones > 0) {
            println("AVISO: Existen $numRelaciones registro(s) asociado(s) a esta vegetación en la tabla 'habitat_vegetacion'.")
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

    // ====================== ACCESO A DATOS ======================

    /** Inserta una vegetación nueva (el ID lo genera MySQL). Devuelve el ID generado o null si falla. */
    fun insertar(v: Vegetacion): Int? {
        val sql = "INSERT INTO vegetacion (nombre, tipo, altura_media, cobertura_media) VALUES (?, ?, ?, ?)"
        return try {
            conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                ps.setString(1, v.nombre)
                ps.setString(2, v.tipo)
                ps.setInt(3, v.alturaMedia)
                ps.setDouble(4, v.coberturaMedia)
                ps.executeUpdate()
                ps.generatedKeys.use { rs -> if (rs.next()) rs.getInt(1) else null }
            }
        } catch (e: Exception) {
            println("Error al insertar: ${e.message}")
            null
        }
    }

    /**
     * Inserta una lista de vegetaciones en una sola transacción conservando los IDs del CSV.
     * Ignora los IDs que ya existen. Si algo falla, hace rollback. Devuelve los insertados.
     */
    fun insertarVarios(lista: List<Vegetacion>): Int {
        val sql = "INSERT IGNORE INTO vegetacion (id_vegetacion, nombre, tipo, altura_media, cobertura_media) VALUES (?, ?, ?, ?, ?)"
        var insertados = 0
        try {
            conexion.autoCommit = false
            conexion.prepareStatement(sql).use { ps ->
                for (v in lista) {
                    ps.setInt(1, v.idVegetacion)
                    ps.setString(2, v.nombre)
                    ps.setString(3, v.tipo)
                    ps.setInt(4, v.alturaMedia)
                    ps.setDouble(5, v.coberturaMedia)
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

    /** Devuelve toda la vegetación ordenada por ID. */
    fun obtenerTodos(): List<Vegetacion> {
        val lista = mutableListOf<Vegetacion>()
        conexion.createStatement().use { st ->
            st.executeQuery("SELECT * FROM vegetacion ORDER BY id_vegetacion").use { rs ->
                while (rs.next()) lista.add(rs.aVegetacion())
            }
        }
        return lista
    }

    /** Busca una vegetación por su ID. Devuelve null si no existe. */
    fun obtenerPorId(id: Int): Vegetacion? {
        conexion.prepareStatement("SELECT * FROM vegetacion WHERE id_vegetacion = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs ->
                return if (rs.next()) rs.aVegetacion() else null
            }
        }
    }

    /** Actualiza todos los campos de una vegetación existente. Devuelve true si se modificó alguna fila. */
    fun actualizar(v: Vegetacion): Boolean {
        val sql = "UPDATE vegetacion SET nombre = ?, tipo = ?, altura_media = ?, cobertura_media = ? WHERE id_vegetacion = ?"
        return try {
            conexion.prepareStatement(sql).use { ps ->
                ps.setString(1, v.nombre)
                ps.setString(2, v.tipo)
                ps.setInt(3, v.alturaMedia)
                ps.setDouble(4, v.coberturaMedia)
                ps.setInt(5, v.idVegetacion)
                ps.executeUpdate() > 0
            }
        } catch (e: Exception) {
            println("Error al actualizar: ${e.message}")
            false
        }
    }

    /**
     * Elimina una vegetación y sus relaciones en 'habitat_vegetacion' dentro de una transacción
     * (primero la tabla intermedia, luego la principal). Los hábitats NO se borran.
     */
    fun eliminar(id: Int): Boolean {
        return try {
            conexion.autoCommit = false

            // 1. Borrar primero los registros asociados en la tabla intermedia
            conexion.prepareStatement("DELETE FROM habitat_vegetacion WHERE id_vegetacion = ?").use { ps ->
                ps.setInt(1, id)
                ps.executeUpdate()
            }

            // 2. Borrar el registro de la tabla principal
            val eliminado = conexion.prepareStatement("DELETE FROM vegetacion WHERE id_vegetacion = ?").use { ps ->
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

    /** Convierte la fila actual de un ResultSet en un objeto [Vegetacion]. */
    private fun ResultSet.aVegetacion() = Vegetacion(
        idVegetacion = getInt("id_vegetacion"),
        nombre = getString("nombre"),
        tipo = getString("tipo") ?: "",
        alturaMedia = getInt("altura_media"),
        coberturaMedia = getDouble("cobertura_media")
    )

    /** Cuenta cuántas filas de 'habitat_vegetacion' están asociadas a una vegetación. Devuelve 0 si hay error. */
    fun contarRelaciones(idVegetacion: Int): Int {
        val sql = "SELECT COUNT(*) FROM habitat_vegetacion WHERE id_vegetacion = ?"
        return try {
            conexion.prepareStatement(sql).use { ps ->
                ps.setInt(1, idVegetacion)
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