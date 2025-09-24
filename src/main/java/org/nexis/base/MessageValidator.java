/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.base;

import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public interface MessageValidator {

    boolean supports(NexusProtocol.NexusMessage message);

    void validate(NexusProtocol.NexusEnvelop envelop) throws SecurityException;
}
