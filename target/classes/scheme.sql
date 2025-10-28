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
    dni INTEGER NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    mail VARCHAR(100) NOT NULL UNIQUE
);

-- Elimina la tabla 'professors' si ya existe para asegurar un inicio limpio
DROP TABLE IF EXISTS professors;

-- Crea la tabla 'professors' con los campos originales, adaptados para SQLite
CREATE TABLE professors (
    person_id INTEGER NOT NULL PRIMARY KEY FOREIGN KEY REFERENCES persons (id), -- Clave foránea que hace referencia a persona, de la cual hereda profesor
    legajo INTEGER UNIQUE,
    titulo VARCHAR(100),
    univ_grad VARCHAR(200),
    cargo VARCHAR(200)
);