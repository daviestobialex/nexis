/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

/**
 * this represents the various API error response codes that, this class will
 * expand to carter for more types of errors and scenarios
 *
 * @author daviestobialex
 */
public class InterfaceErrorCodes {

    public static final int TIME_OUT = 0x00; // timeout error code
    public static final int SERVER_ERROR = 0x01; // server error code, server threw an exception
    public static final int SYSTEM_ERROR = 0x02; // system error code
}
