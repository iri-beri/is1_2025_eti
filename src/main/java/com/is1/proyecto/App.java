package com.is1.proyecto; // Define el paquete de la aplicación, debe coincidir con la estructura de carpetas.

// Importaciones necesarias para la aplicación Spark
import com.fasterxml.jackson.databind.ObjectMapper; // Utilidad para serializar/deserializar objetos Java a/desde JSON.
import static spark.Spark.*; // Importa los métodos estáticos principales de Spark (get, post, before, after, etc.).

// Importaciones específicas para ActiveJDBC (ORM para la base de datos)
import org.javalite.activejdbc.Base; // Clase central de ActiveJDBC para gestionar la conexión a la base de datos.
import org.mindrot.jbcrypt.BCrypt; // Utilidad para hashear y verificar contraseñas de forma segura.

// Importaciones de Spark para renderizado de plantillas
import spark.ModelAndView; // Representa un modelo de datos y el nombre de la vista a renderizar.
import spark.template.mustache.MustacheTemplateEngine; // Motor de plantillas Mustache para Spark.

// Importaciones estándar de Java
import java.util.HashMap; // Para crear mapas de datos (modelos para las plantillas).
import java.util.Map; // Interfaz Map, utilizada para Map.of() o HashMap.
import java.io.BufferedReader;
// Importaciones para manejo de archivos SQL
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;

// Importaciones para la codificación de URL
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

// Importaciones de clases del proyecto
import com.is1.proyecto.config.DBConfigSingleton; // Clase Singleton para la configuración de la base de datos.
import com.is1.proyecto.models.User; // Modelo de ActiveJDBC que representa la tabla 'users'.
import com.is1.proyecto.models.Person; // Modelo de ActiveJDBC que representa la tabla 'persons'.
import com.is1.proyecto.models.Professor; // Modelo de ActiveJDBC que representa la tabla 'professors'.


/**
 * Clase principal de la aplicación Spark.
 * Configura las rutas, filtros y el inicio del servidor web.
 */
public class App {

    // Instancia estática y final de ObjectMapper para la serialización/deserialización JSON.
    // Se inicializa una sola vez para ser reutilizada en toda la aplicación.
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Helper que codifica un String para ser usado de forma segura como valor en un query parameter de URL.
     * Esto implementa una solución en las redirecciones del lado del servidor.
    */
    private static String urlEncode(String value) {
        try {
            // Se usa URLEncoder para codificar el valor con UTF-8
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // Este error nunca debería ocurrir ya que UTF-8 es una codificación estándar en Java.
            System.err.println("FATAL: Codificación UTF-8 no soportada!");
            throw new RuntimeException("UTF-8 encoding not supported!", e);
        }
    }
    
    /**
     * Inicializa la base de datos ejecutando el script de esquema SQL (scheme.sql).
     * Esto asegura que todas las tablas existan antes de que la aplicación reciba peticiones.
     * * NOTA: Esto abre y cierra una conexión temporalmente para propósitos de inicialización.
     */
    private static void initializeDatabase(DBConfigSingleton dbConfig) {
        System.out.println("DEBUG: Iniciando verificación y carga de esquema de la base de datos...");

        try {
            // Abrir una conexión temporal para la inicialización, usando las credenciales del Singleton.
            Base.open(dbConfig.getDriver(), dbConfig.getDbUrl(), dbConfig.getUser(), dbConfig.getPass());

            // 1. Leer el contenido del archivo scheme.sql desde el CLASSPATH
            InputStream inputStream = App.class.getClassLoader().getResourceAsStream("scheme.sql");

            if (inputStream == null) {
                // Si el archivo no se encuentra en el classpath, se lanza una excepción.
                throw new IOException("FATAL: Archivo 'scheme.sql' no encontrado en el classpath (ruta src/main/resources).");
            }
            
            // Leer el contenido completo del InputStream en un String
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            String sqlScheme = sb.toString();

            
            // 2. Ejecutar el script SQL (incluirá DROP TABLE y CREATE TABLE).
            Base.exec(sqlScheme);

            System.out.println("DEBUG: Esquema de base de datos cargado exitosamente (Tablas: users, persons, professors).");

        } catch (IOException e) {
            System.err.println("FATAL: No se pudo leer el archivo scheme.sql. La aplicación no puede iniciar. Error: " + e.getMessage());
            // Detiene la ejecución si el archivo clave no se encuentra.
            System.exit(1);
        } catch (Exception e) {
            System.err.println("FATAL: Error durante la inicialización de la base de datos. La aplicación no puede iniciar. Error: " + e.getMessage());
            // Detiene la ejecución si hay un error de BD (ej. conexión).
            System.exit(1);
        } finally {
            // Cierra la conexión temporarl de inicialización.
            Base.close();
        }
    }

    /**
     * Método principal que se ejecuta al iniciar la aplicación.
     * Aquí se configuran todas las rutas y filtros de Spark.
     */
    public static void main(String[] args) {
        port(8080); // Configura el puerto en el que la aplicación Spark escuchará las peticiones (por defecto es 4567).

        // Obtener la instancia única del singleton de configuración de la base de datos.
        DBConfigSingleton dbConfig = DBConfigSingleton.getInstance();
        
        // ********************************************************************
        // LLAMADA CLAVE: Inicializa las tablas antes de cualquier solicitud.
        initializeDatabase(dbConfig);
        // ********************************************************************

        // --- Filtro 'before' para gestionar la conexión a la base de datos ---
        // Este filtro se ejecuta antes de cada solicitud HTTP.
        before((req, res) -> {
            try {
                // Abre una conexión a la base de datos utilizando las credenciales del singleton.
                Base.open(dbConfig.getDriver(), dbConfig.getDbUrl(), dbConfig.getUser(), dbConfig.getPass());
                System.out.println(req.url());

            } catch (Exception e) {
                // Si ocurre un error al abrir la conexión, se registra y se detiene la solicitud
                // con un código de estado 500 (Internal Server Error) y un mensaje JSON.
                System.err.println("Error al abrir conexión con ActiveJDBC: " + e.getMessage());
                halt(500, "{\"error\": \"Error interno del servidor: Fallo al conectar a la base de datos.\"}" + e.getMessage());
            }
        });

        // --- Filtro 'after' para cerrar la conexión a la base de datos ---
        // Este filtro se ejecuta después de que cada solicitud HTTP ha sido procesada.
        after((req, res) -> {
            try {
                // Cierra la conexión a la base de datos para liberar recursos.
                Base.close();
            } catch (Exception e) {
                // Si ocurre un error al cerrar la conexión, se registra.
                System.err.println("Error al cerrar conexión con ActiveJDBC: " + e.getMessage());
            }
        });

        // --- Rutas GET para renderizar formularios y páginas HTML ---

        // GET: Muestra el formulario de creación de cuenta.
        // Soporta la visualización de mensajes de éxito o error pasados como query parameters.
        get("/user/create", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Crea un mapa para pasar datos a la plantilla.

            // Obtener y añadir mensaje de éxito de los query parameters (ej. ?message=Cuenta creada!)
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }

            // Obtener y añadir mensaje de error de los query parameters (ej. ?error=Campos vacíos)
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }

            // Renderiza la plantilla 'user_form.mustache' con los datos del modelo.
            return new ModelAndView(model, "user_form.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Ruta para mostrar el dashboard (panel de control) del usuario.
        // Requiere que el usuario esté autenticado.
        get("/dashboard", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Modelo para la plantilla del dashboard.

            // Intenta obtener el nombre de usuario y la bandera de login de la sesión.
            String currentUsername = req.session().attribute("currentUserUsername");
            Boolean loggedIn = req.session().attribute("loggedIn");

            // 1. Verificar si el usuario ha iniciado sesión.
            // Si no hay un nombre de usuario en la sesión, la bandera es nula o falsa,
            // significa que el usuario no está logueado o su sesión expiró.
            if (currentUsername == null || loggedIn == null || !loggedIn) {
                System.out.println("DEBUG: Acceso no autorizado a /dashboard. Redirigiendo a /login.");
                // Redirige al login con un mensaje de error.
                // Se codifica el mensaje de error:
                res.redirect("/login?error=" + urlEncode("Debes iniciar sesión para acceder a esta página."));
                return null; // Importante retornar null después de una redirección.
            }

            // 2. Si el usuario está logueado, añade el nombre de usuario al modelo para la plantilla.
            model.put("username", currentUsername);

            // 3. Renderiza la plantilla del dashboard con el nombre de usuario.
            return new ModelAndView(model, "dashboard.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Ruta para cerrar la sesión del usuario.
        get("/logout", (req, res) -> {
            // Invalida completamente la sesión del usuario.
            // Esto elimina todos los atributos guardados en la sesión y la marca como inválida.
            // La cookie JSESSIONID en el navegador también será gestionada para invalidarse.
            req.session().invalidate();

            System.out.println("DEBUG: Sesión cerrada. Redirigiendo a /login.");

            // Redirige al usuario a la página de login con un mensaje de éxito.
            res.redirect("/");

            return null; // Importante retornar null después de una redirección.
        });

        // GET: Muestra el formulario de inicio de sesión (login).
        // Nota: Esta ruta debería ser capaz de leer también mensajes de error/éxito de los query params
        // si se la usa como destino de redirecciones. (Tu código de /user/create ya lo hace, aplicar similar).
        get("/", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }
            return new ModelAndView(model, "login.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Ruta de alias para el formulario de creación de cuenta.
        // En una aplicación real, probablemente querrías unificar con '/user/create' para evitar duplicidad.
        get("/user/new", (req, res) -> {
            return new ModelAndView(new HashMap<>(), "user_form.mustache"); // No pasa un modelo específico, solo el formulario.
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Muestra el formulario de creación de profesor.
        get("/professor/create", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            //Obtener y añadir mensajes de query parameters
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }

            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }

            // Renderiza la plantilla 'professor_form.mustache' con los datos del modelo.
            // **Nota:** ¡Necesitás crear este archivo de plantilla!
            return new ModelAndView(model, "professor_form.mustache");
        }, new MustacheTemplateEngine());

        // --- Rutas POST para manejar envíos de formularios y APIs ---

        // POST: Maneja el envío del formulario de creación de nueva cuenta.
        post("/user/new", (req, res) -> {
            String name = req.queryParams("name");
            String password = req.queryParams("password");

            // Validaciones básicas: campos no pueden ser nulos o vacíos.
            if (name == null || name.isEmpty() || password == null || password.isEmpty()) {
                res.status(400); // Código de estado HTTP 400 (Bad Request).
                // Redirige al formulario de creación con un mensaje de error.
                res.redirect("/user/create?error=" + urlEncode("Nombre y contraseña son requeridos."));
                return ""; // Retorna una cadena vacía ya que la respuesta ya fue redirigida.
            }

            try {
                // Intenta crear y guardar la nueva cuenta en la base de datos.
                User ac = new User(); // Crea una nueva instancia del modelo User.
                // Hashea la contraseña de forma segura antes de guardarla.
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

                ac.set("name", name); // Asigna el nombre de usuario.
                ac.set("password", hashedPassword); // Asigna la contraseña hasheada.
                ac.saveIt(); // Guarda el nuevo usuario en la tabla 'users'.

                res.status(201); // Código de estado HTTP 201 (Created) para una creación exitosa.
                // Redirige al formulario de creación con un mensaje de éxito.
                res.redirect("/user/create?message=" + urlEncode("Cuenta creada exitosamente para " + name + "!"));
                return ""; // Retorna una cadena vacía.

            } catch (Exception e) {
                // Si ocurre cualquier error durante la operación de DB (ej. nombre de usuario duplicado),
                // se captura aquí y se redirige con un mensaje de error.
                System.err.println("Error al registrar la cuenta: " + e.getMessage());
                e.printStackTrace(); // Imprime el stack trace para depuración.
                res.status(500); // Código de estado HTTP 500 (Internal Server Error).
                // Se codifica el mensaje de error:
                res.redirect("/user/create?error=" + urlEncode("Error interno al crear la cuenta. Intente de nuevo."));
                return ""; // Retorna una cadena vacía.
            }
        });


        // POST: Maneja el envío del formulario de inicio de sesión.
        post("/login", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Modelo para la plantilla de login o dashboard.

            String username = req.queryParams("username");
            String plainTextPassword = req.queryParams("password");

            // Validaciones básicas: campos de usuario y contraseña no pueden ser nulos o vacíos.
            if (username == null || username.isEmpty() || plainTextPassword == null || plainTextPassword.isEmpty()) {
                res.status(400); // Bad Request.
                model.put("errorMessage", "El nombre de usuario y la contraseña son requeridos.");
                return new ModelAndView(model, "login.mustache"); // Renderiza la plantilla de login con error.
            }

            // Busca la cuenta en la base de datos por el nombre de usuario.
            User ac = User.findFirst("name = ?", username);

            // Si no se encuentra ninguna cuenta con ese nombre de usuario.
            if (ac == null) {
                res.status(401); // Unauthorized.
                model.put("errorMessage", "Usuario o contraseña incorrectos."); // Mensaje genérico por seguridad.
                // Retorna la plantilla renderizada a String.
                return new ModelAndView(model, "login.mustache"); // Renderiza la plantilla de login con error.
            }

            // Obtiene la contraseña hasheada almacenada en la base de datos.
            String storedHashedPassword = ac.getString("password");

            // Compara la contraseña en texto plano ingresada con la contraseña hasheada almacenada.
            // BCrypt.checkpw hashea la plainTextPassword con el salt de storedHashedPassword y compara.
            if (BCrypt.checkpw(plainTextPassword, storedHashedPassword)) {

                // Autenticación exitosa.
                res.status(200); // OK.

                // --- Gestión de Sesión ---
                req.session(true).attribute("currentUserUsername", username); // Guarda el nombre de usuario en la sesión.
                req.session().attribute("userId", ac.getId()); // Guarda el ID de la cuenta en la sesión (útil).
                req.session().attribute("loggedIn", true); // Establece una bandera para indicar que el usuario está logueado.
                
                System.out.println("DEBUG: Login exitoso para la cuenta: " + username);
                System.out.println("DEBUG: ID de Sesión: " + req.session().id());
                
                
                model.put("username", username); // Añade el nombre de usuario al modelo para el dashboard.
                // Renderiza la plantilla del dashboard tras un login exitoso.
                return new ModelAndView(model, "dashboard.mustache");
            } else {
                // Contraseña incorrecta.
                res.status(401); // Unauthorized.
                System.out.println("DEBUG: Intento de login fallido para: " + username);
                model.put("errorMessage", "Usuario o contraseña incorrectos."); // Mensaje genérico por seguridad.
                return new ModelAndView(model, "login.mustache"); // Renderiza la plantilla de login con error.
            }
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta POST.

        // POST: Endpoint para añadir usuarios (API que devuelve JSON, no HTML).
        // Advertencia: Esta ruta tiene un propósito diferente a las de formulario HTML.
        post("/add_users", (req, res) -> {
            res.type("application/json"); // Establece el tipo de contenido de la respuesta a JSON.

            // Obtiene los parámetros 'name' y 'password' de la solicitud.
            String name = req.queryParams("name");
            String password = req.queryParams("password");

            // --- Validaciones básicas ---
            if (name == null || name.isEmpty() || password == null || password.isEmpty()) {
                res.status(400); // Bad Request.
                return objectMapper.writeValueAsString(Map.of("error", "Nombre y contraseña son requeridos."));
            }

            try {
                // --- Creación y guardado del usuario usando el modelo ActiveJDBC ---
                User newUser = new User(); // Crea una nueva instancia de tu modelo User.

                // Hashea la contraseña de forma segura (como en la ruta /user/new)
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

                newUser.set("name", name); // Asigna el nombre al campo 'name'.
                newUser.set("password", hashedPassword); // Asigna la contraseña hasheada.
                newUser.saveIt(); // Guarda el nuevo usuario en la tabla 'users'.

                res.status(201); // Created.
                // Devuelve una respuesta JSON con el mensaje y el ID del nuevo usuario.
                return objectMapper.writeValueAsString(Map.of("message", "Usuario '" + name + "' registrado con éxito.", "id", newUser.getId()));

            } catch (Exception e) {
                // Si ocurre cualquier error durante la operación de DB, se captura aquí.
                System.err.println("Error al registrar usuario: " + e.getMessage());
                e.printStackTrace(); // Imprime el stack trace para depuración.
                res.status(500); // Internal Server Error.
                return objectMapper.writeValueAsString(Map.of("error", "Error interno al registrar usuario: " + e.getMessage()));
            }
        });

        // POST: Maneja el envío del formulario de creación de nuevo profesor.
        post("professor/new", (req, res) -> {
            // 1. Obtener datos del formulario
            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String mail = req.queryParams("mail");
            String dniStr = req.queryParams("dni");
            String legajoStr = req.queryParams("legajo"); // Atributo opcional de professor.
            String titulo = req.queryParams("titulo"); // Atributo opcional de professor.
            String universidad = req.queryParams("universidad"); // Atributo opcional de professor.
            String cargo = req.queryParams("cargo"); // Atributo opcional de professor.

            // Variable para la redirección de error
            String errorRedirectBase = "/professor/create?error=";

            // 2. Validaciones de datos
            // 2.1. Faltan campos obligatorios (nombre, apellido, mail, dni)
            if (nombre == null || nombre.isEmpty() || apellido == null || apellido.isEmpty() || mail == null || mail.isEmpty() || dniStr == null
                || dniStr.isEmpty()) {
                    res.status(400); // Bad Request
                    res.redirect(errorRedirectBase + urlEncode("Todos los campos obligatorios (nombre, apellido, mail, DNI) son requeridos."));
                    return "";
                }
            
            Integer dni;
            try {
                dni = Integer.parseInt(dniStr);
            } catch (NumberFormatException e) {
                res.status(400); // Bad Request
                // Se codifica el mensaje de error:
                res.redirect(errorRedirectBase + urlEncode("El DNI debe ser un número válido."));
                return "";
            }

            // 2.2. Formato del mail no válido (Validación básica)
            // Regex simple para email: local-part@domain.tld
            String emailRegex = "^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}$";
            if (!mail.matches(emailRegex)) {
                res.status(400); // Bad Request
                // Se codifica el mensaje de error:
                res.redirect(errorRedirectBase + urlEncode("El formato del email no es válido."));
                return "";
            }

            // 2.3. Email o DNI ya existen en la base de datos (Validación de unicidad)
            // Se asume que las tablas 'persons' ya existen y tienen la unicidad configurada.
            if (Person.findFirst("mail = ?", mail) != null) {
                res.status(409); // Conflict
                // Se codifica el mensaje de error:
                res.redirect(errorRedirectBase + urlEncode("El email ya está registrado en el sistema."));
                return "";
            }
            // En la BD, DNI es VARCHAR, pero lo validamos con el Integer parseado previamente.
            // Para la consulta, se puede usar el Integer o su representación String, ActiveJDBC lo gestiona.
            if (Person.findFirst("dni = ?", dni) != null) {
                res.status(409); // Conflict
                // Se codifica el mensaje de error:
                res.redirect(errorRedirectBase + urlEncode("El DNI ya está registrado en el sistema."));
                return "";
            }

            // 3. Flujo existoso: crear y guardar
            try {
                // 3.1. Crear y guardar el Person (padre)
                Person nuevoPerson = new Person();
                nuevoPerson.setNombre(nombre);
                nuevoPerson.setApellido(apellido);
                nuevoPerson.setMail(mail);
                nuevoPerson.setDni(dni);
                nuevoPerson.saveIt(); // Guarda la persona y obtendrá su ID.

                // 3.2. Crear y guardar el Professor (hijo)
                Professor nuevoProfessor = new Professor();
                // **Relación 1:1/Composición**: Guardamos el ID de Person en Professor.
                nuevoProfessor.set("person_id", nuevoPerson.getId());

                // Asignar atributos específicos de Professor (incluye manejo de nulos/vacíos para los atributos opcionales)
                if (legajoStr != null && !legajoStr.isEmpty()) {
                    // Manejo del NumberFormatException si legajoStr no es un número (aunque ya lo hicimos para DNI, mejor ser cauteloso)
                    try {
                        nuevoProfessor.setLegajo(Integer.parseInt(legajoStr));
                    } catch (NumberFormatException e) {
                        // En caso de error de parseo, redirigir y manejar la transacción (rollback implícito por el filtro after)
                        res.status(400); // Bad Request
                        // Se codifica el mensaje de error:
                        res.redirect(errorRedirectBase + urlEncode("El número de Legajo debe ser un número válido."));
                        return "";
                    }
                    
                }
                if (titulo != null && !titulo.isEmpty()) {
                    nuevoProfessor.setTitulo(titulo);
                }
                if (universidad != null && !universidad.isEmpty()) {
                    nuevoProfessor.setUniversidadGraduado(universidad);
                }
                if (cargo != null && !cargo.isEmpty()) {
                    nuevoProfessor.setCargo(cargo);
                }

                nuevoProfessor.saveIt(); // Guarda el profesor.

                // 4. Redirección de éxito
                res.status(201); // Created
                // Se codifica el mensaje de éxito:
                res.redirect("/professor/create?message=" + urlEncode("Profesor " + nombre + " " + apellido + " registrado exitosamente."));
                return "";

            } catch (Exception e) {
                // Manejo de errores de base de datos o parseo (ej. legajo no numérico)
                System.err.println("Error al registrar el profesor: " + e.getMessage());
                e.printStackTrace();
                res.status(500); // Internal Server Error
                // Se codifica el mensaje de error (incluyendo el mensaje de la excepción):
                res.redirect(errorRedirectBase + urlEncode("Error interno al crear el profesor. Detalle: " + e.getMessage()));
                return "";
            }
        });

    } // Fin del método main
} // Fin de la clase App