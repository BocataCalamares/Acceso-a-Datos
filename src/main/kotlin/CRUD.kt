import java.sql.Connection
import java.sql.DriverManager
import kotlin.use
import java.sql.ResultSet

const val URL_BD =  "jdbc:mysql://localhost/habitats"
const val USER_BD = "root"
const val PASS_BD = "hola01"
/**
 * Creator Jonathan Rosa Aledón
 * Entidad/es: Habitat & Vegetación
 */
// ====================== CONEXIÓN ======================

/** Singleton que mantiene una única conexión a MySQL compartida por todos los DAO. */
object Conexion {
    /** Conexión creada de forma perezosa: solo se abre la primera vez que se usa. */
    val conexion: Connection by lazy {
        // MySQL (InnoDB) aplica las claves foráneas por defecto: ya no hace falta el PRAGMA
        DriverManager.getConnection(URL_BD, USER_BD, PASS_BD)
    }

    /** Cierra la conexión con la base de datos. */
    fun cerrar() = conexion.close()
}

// ====================== UTILIDADES DE ENTRADA ======================

/** Pide un número entero por teclado y repite la pregunta hasta que el valor sea válido. */
fun pedirEntero(msg: String): Int {
    while (true) {
        print(msg)
        readln().trim().toIntOrNull()?.let { return it }
        println("Introduce un número entero válido.")
    }
}

/** Pide un número decimal por teclado (acepta coma o punto) y repite hasta que sea válido. */
fun pedirDecimal(msg: String): Double {
    while (true) {
        print(msg)
        readln().trim().replace(",", ".").toDoubleOrNull()?.let { return it }
        println("Introduce un número válido.")
    }
}

/** Pide un texto por teclado y repite la pregunta mientras el usuario lo deje vacío. */
fun pedirTexto(msg: String): String {
    while (true) {
        print(msg)
        val t = readln().trim()
        if (t.isNotEmpty()) return t
        println("El campo no puede estar vacío.")
    }
}

/** Pide un texto opcional: devuelve null si el usuario pulsa Enter sin escribir nada. */
fun pedirOpcional(msg: String): String? {
    print(msg)
    return readln().trim().ifEmpty { null }
}

// ====================== MENÚS ======================

/** Clase que gestiona los menús de consola y delega cada opción en su DAO. */
class CRUD {
    private val habitatsDAO = HabitatsDAO()
    private val vegetacionDAO = VegetacionDAO()

    /**
     * Menú principal: permite entrar a los CRUD de hábitats y vegetación, o eliminar
     * un hábitat con su vegetación. Al salir cierra la conexión con la BD.
     */
    fun menuPrincipal() {
        var mostrarMenu = true
        while (mostrarMenu) {
            println(
                "--------------------------------------" +
                        "\n----------- MENÚ PRINCIPAL -----------" +
                        "\n--------------------------------------" +
                        "\n1. CRUD (Habitats)" +
                        "\n2. CRUD (Vegetación)" +
                        "\n3. Eliminar hábitat con su vegetación (transacción)" +
                        "\n0. Salir"
            )

            when (readln().toIntOrNull() ?: -1) {
                0 -> mostrarMenu = false
                1 -> menuHabitats()
                2 -> menuVegetacion()
                3 -> habitatsDAO.eliminarConVegetacion()
                else -> println("Opción inválida. Intenta con un número del menú.")
            }
        }
        Conexion.cerrar()
        println("¡Hasta pronto!")
    }

    /** Submenú CRUD de hábitats: importar CSV, listar, añadir, modificar y eliminar. */
    private fun menuHabitats() {
        var mostrarMenu = true
        while (mostrarMenu) {
            println(
                "--------------------------------------" +
                        "\n--------- MENÚ CRUD HABITATS ---------" +
                        "\n--------------------------------------" +
                        "\n1. Importar datos desde fichero CSV" +
                        "\n2. Leer Información" +
                        "\n3. Añadir un registro nuevo" +
                        "\n4. Modificar un registro existente (por ID)" +
                        "\n5. Eliminar un registro existente (por ID)" +
                        "\n0. Volver"
            )

            when (readln().toIntOrNull() ?: -1) {
                0 -> mostrarMenu = false
                1 -> habitatsDAO.importarCsv()
                2 -> habitatsDAO.leerInformacion()
                3 -> habitatsDAO.anadirRegistro()
                4 -> habitatsDAO.modificarRegistro()
                5 -> habitatsDAO.eliminarRegistro()
                else -> println("Opción inválida. Intenta con un número del menú.")
            }
        }
    }

    /** Submenú CRUD de vegetación: importar CSV, listar, añadir, modificar y eliminar. */
    private fun menuVegetacion() {
        var mostrarMenu = true
        while (mostrarMenu) {
            println(
                "--------------------------------------" +
                        "\n-------- MENÚ CRUD VEGETACION --------" +
                        "\n--------------------------------------" +
                        "\n1. Importar datos desde fichero CSV" +
                        "\n2. Leer Información" +
                        "\n3. Añadir un registro nuevo" +
                        "\n4. Modificar un registro existente (por ID)" +
                        "\n5. Eliminar un registro existente (por ID)" +
                        "\n0. Volver"
            )

            when (readln().toIntOrNull() ?: -1) {
                0 -> mostrarMenu = false
                1 -> vegetacionDAO.importarCsv()
                2 -> vegetacionDAO.leerInformacion()
                3 -> vegetacionDAO.anadirRegistro()
                4 -> vegetacionDAO.modificarRegistro()
                5 -> vegetacionDAO.eliminarRegistro()
                else -> println("Opción inválida. Intenta con un número del menú.")
            }
        }
    }
}

/** Punto de entrada del programa: arranca el menú principal. */
fun main() {
    CRUD().menuPrincipal()
}