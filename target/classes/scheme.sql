-- Elimina la tabla 'users' si ya existe para asegurar un inicio limpio
DROP TABLE IF EXISTS users;

-- Crea la tabla 'users' con los campos originales, adaptados para SQLite
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- Clave primaria autoincremental para SQLite
    name TEXT NOT NULL UNIQUE,          -- Nombre de usuario (TEXT es el tipo de cadena recomendado para SQLite), con restricción UNIQUE
    password TEXT NOT NULL           -- Contraseña hasheada (TEXT es el tipo de cadena recomendado para SQLite)
);

-- Elimina la tabla 'persons' si ya existe para asegurar un inicio limpio
DROP TABLE IF EXISTS persons;

-- Crea la tabla 'persons' con los campos originales, adaptados para SQLite
CREATE TABLE persons (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- Clave primaria autoincremental para SQLite
    -- Campos de persona (obligatorios en el formulario)
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    dni VARCHAR(20) NOT NULL UNIQUE,
    mail VARCHAR(100) NOT NULL UNIQUE,

    -- Campos requeridos por ActiveJDBC para seguimiento
    created_at DATETIME,
    updated_at DATETIME
);

-- Elimina la tabla 'professors' si ya existe para asegurar un inicio limpio
DROP TABLE IF EXISTS professors;

-- Crea la tabla 'professors' con los campos originales, adaptados para SQLite
CREATE TABLE professors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    person_id INTEGER NOT NULL UNIQUE,
    legajo VARCHAR(100) UNIQUE,
    titulo VARCHAR(100),
    univ_grad VARCHAR(200),
    cargo VARCHAR(200),

    -- Campos requeridos por ActiveJDBC para seguimiento
    created_at DATETIME,
    updated_at DATETIME,

    FOREIGN KEY (person_id) REFERENCES persons (id) -- Clave foránea que hace referencia a persona, de la cual hereda profesor
);