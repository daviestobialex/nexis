/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.exceptions;

/**
 *
 * @author daviestobialex
 */
public class ProtocolException extends VerificationException {

    public ProtocolException() {
        super();
    }

    public ProtocolException(String msg) {
        super(msg);
    }

    public ProtocolException(Exception e) {
        super(e);
    }
}
