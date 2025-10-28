package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("persons") // Esta anotación asocia explícitamente el modelo 'Person' con la tabla 'persons' en la DB.

public class Person extends Model {
    
    // ActiveJDBC mapea automáticamente las columnas de la tabla 'persons' a los atributos de esta clase.
    // No es necesario declarar los campos (dni, nombre, apellido, correo) aquí como variables de instancia,
    // ya que la clase Model base se encarga de la interacción con la base de datos y los gestiona.
    // Se añaden getters/setters tipados por conveniencia.

    public Integer getId() {
        return getInteger("id");
    }
    
    public Integer getDni() {
        return getInteger("dni");
    }

    public void setDni(Integer dni) {
        set("dni", dni);
    }

    public String getNombre() {
        return getString("nombre");
    }

    public void setNombre(String nombre) {
        set("nombre", nombre);
    }

    public String getApellido() {
        return getString("apellido");
    }

    public void setApellido(String apellido) {
        set("apellido", apellido);
    }

    public String getMail() {
        return getString("mail");
    }

    public void setMail(String mail) {
        set("mail", mail);
    }

    // Relación uno a uno con Professor (la clave foránea está en professors)
    //public Professor getProfessor() {
    //    return this.one(Professor.class);
    //}
}
