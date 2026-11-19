# Gestor de Hábitats y Vegetación

Aplicación de consola en **Kotlin** que gestiona una base de datos **MySQL** de hábitats naturales y la vegetación que vive en ellos. Usa **JDBC** con el patrón **DAO** y permite importar datos desde CSV, consultarlos, modificarlos y eliminarlos desde un menú interactivo.

## Funcionalidades

- **CRUD completo** de dos entidades: `Habitat` y `Vegetacion`.
- **Importación desde CSV** con validación línea a línea (las líneas inválidas se informan y se omiten).
- **Importación transaccional**: si algo falla, se deshacen todos los cambios (rollback).
- **Conserva los IDs del CSV** e ignora los que ya existen (`INSERT IGNORE`).
- **Modificación parcial**: al editar, si dejas un campo vacío se mantiene el valor actual.
- **Aviso antes de eliminar**: si el registro tiene relaciones en `habitat_vegetacion`, se muestra cuántas son y se pide confirmación.
- **Eliminación en transacción**: se borran primero las relaciones y después el registro principal.
- **Eliminar hábitat con su vegetación**: borra el hábitat y la vegetación que no esté asociada a ningún otro hábitat.
- **Validación de entradas**: se repite la pregunta hasta que el dato es válido (los decimales aceptan coma o punto).

## Estructura del proyecto

| Fichero | Contenido |
|---|---|
| `Main.kt` | `main`, la conexión (`Conexion`), las utilidades de entrada por teclado y los menús (`CRUD`). |
| `HabitatsDAO.kt` | Data class `Habitat` y acceso a datos de la tabla `habitats`. |
| `VegetacionDAO.kt` | Data class `Vegetacion` y acceso a datos de la tabla `vegetacion`. |
| `datos/habitats.csv` | Datos de ejemplo para importar hábitats. |
| `datos/vegetacion.csv` | Datos de ejemplo para importar vegetación. |

## Requisitos

- JDK 17 o superior (o el que use tu proyecto).
- Kotlin.
- MySQL en funcionamiento.
- Conector JDBC de MySQL (`mysql-connector-j`) añadido como dependencia. Ejemplo con Gradle:

```kotlin
dependencies {
    implementation("com.mysql:mysql-connector-j:8.4.0")
}
```

## Configuración

1. Crea la base de datos:

```sql
CREATE DATABASE habitats;
```

2. Revisa las constantes de conexión al principio de `Main.kt`:

```kotlin
const val URL_BD  = "jdbc:mysql://localhost/habitats"
const val USER_BD = "root"
const val PASS_BD = "tu_contraseña"
```

> **Aviso:** no subas tu contraseña real a un repositorio. Lo ideal es leerla de una variable de entorno.

3. Las tablas `habitats` y `vegetacion` se crean solas al arrancar la aplicación. La tabla intermedia **`habitat_vegetacion` no se crea desde el código**, así que debe existir antes de usar las opciones de eliminación. Un ejemplo de esquema:

```sql
CREATE TABLE IF NOT EXISTS habitat_vegetacion (
    id_habitat INT NOT NULL,
    id_vegetacion INT NOT NULL,
    PRIMARY KEY (id_habitat, id_vegetacion),
    FOREIGN KEY (id_habitat) REFERENCES habitats(id_habitat),
    FOREIGN KEY (id_vegetacion) REFERENCES vegetacion(id_vegetacion)
);
```

Ajústalo si tu tabla tiene columnas adicionales.

## Formato de los CSV

Los ficheros deben estar en la carpeta `datos/`, tener **cabecera** (se ignora la primera línea), codificación **UTF-8** y **5 columnas** separadas por comas.

**`datos/habitats.csv`**

```csv
id_habitat,nombre,clima,altitud_media,temperatura_media
1,Bosque mediterráneo,Mediterráneo,450,16.5
2,Alta montaña,Alpino,2300,4.2
```

**`datos/vegetacion.csv`**

```csv
id_vegetacion,nombre,tipo,altura_media,cobertura_media
1,Encina,Árbol,12,65.0
2,Romero,Arbusto,1,30.5
```

Una línea se considera inválida si no tiene exactamente 5 columnas o si el ID, la altitud/altura o el valor decimal no son numéricos.

## Guía de uso

Ejecuta la función `main` de `Main.kt`. Se abrirá el menú principal:

```
----------- MENÚ PRINCIPAL -----------
1. CRUD (Habitats)
2. CRUD (Vegetación)
3. Eliminar hábitat con su vegetación (transacción)
0. Salir
```

### Submenús CRUD (opciones 1 y 2)

| Opción | Acción |
|---|---|
| 1 | Importar datos desde el fichero CSV correspondiente. |
| 2 | Leer información: muestra todos los registros en una tabla. |
| 3 | Añadir un registro nuevo (el ID lo genera MySQL). |
| 4 | Modificar un registro por ID (campo vacío = mantener valor actual). |
| 5 | Eliminar un registro por ID, con confirmación. |
| 0 | Volver al menú principal. |

### Eliminar hábitat con su vegetación (opción 3)

1. Introduce el ID del hábitat.
2. La aplicación muestra cuántas vegetaciones tiene asociadas.
3. Confirma con `s`.
4. En una sola transacción se borran las relaciones, la vegetación que ya no pertenezca a ningún otro hábitat y, por último, el hábitat.

Si una especie también vive en otro hábitat, **no se elimina**: solo se borra su relación con el hábitat eliminado.

### Flujo recomendado la primera vez

1. Crea la base de datos y la tabla `habitat_vegetacion`.
2. Coloca los CSV en `datos/`.
3. Arranca la aplicación.
4. Importa primero los **hábitats** (menú 1 → opción 1) y después la **vegetación** (menú 2 → opción 1).
5. Comprueba los datos con la opción 2 de cada menú.

## Detalles técnicos

- Una única conexión compartida (`Conexion`), abierta de forma perezosa y cerrada al salir.
- Todas las consultas usan `PreparedStatement`, lo que evita la inyección SQL.
- Los recursos JDBC se cierran automáticamente con `use`.
- Las operaciones de varios pasos (importar y eliminar) usan transacciones con `commit` y `rollback`.
