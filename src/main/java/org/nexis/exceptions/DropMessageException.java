/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.exceptions;

/**
 *
 * @author daviestobialex
 */
public class DropMessageException extends RuntimeException {

    public DropMessageException() {
        super("Message Dropped Excpetion");
    }

    public DropMessageException(java.lang.String message) {
        super(message);
    }

}
