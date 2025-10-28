package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("professors") // Mapea este modedlo a la tabla 'professors'.

public class Professor extends Model {
    
    // La tabla 'profesores' tiene una columna 'person_id' que es tanto clave primaria de 'professors'
    // como clave foránea de 'persons'. Esto se maneja mejor en la capa de persistencia (SQL).
    // Aquí, solo se definen los atributos específicos del profesor.

    public Integer getLegajo() {
        return getInteger("legajo");
    }

    public void setLegajo(Integer legajo) {
        set("legajo", legajo);
    }

    public String getTitulo() {
        return getString("titulo");
    }

    public void setTitulo(String titulo) {
        set("titulo", titulo);
    }

    public String getUniversidadGraduado() {
        return getString("univ_grad");
    }

    public void setUniversidadGraduado(String univ_grad) {
        set("univ_grad", univ_grad);
    }

    public String getCargo() {
        return getString("cargo");
    }

    public void setCargo(String cargo) {
        set("cargo", cargo);
    }

    // Relación de vuelta para obtener la información de Persona.
    // 'parent' indica que el ID de 'Profesor' (id_profesor) coincide con el ID de 'Persona' (id_persona)
    // Si la relación es 1:1, donde la PK de Profesor es FK a Persona:
    //public Person getPerson() {
        // Asumiendo que professors tiene una columna 'person_id'
        //return parent(Persona.class, "person_id");
    //}
}
