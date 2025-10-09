/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.exceptions;

/**
 *
 * @author daviestobialex
 */
public class ManifestValidationException extends RuntimeException {

    public ManifestValidationException(String message) {
        super(message);
    }

    public ManifestValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ManifestValidationException() {
        super("Invalid manifest Schema");
    }

}
