/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.validator;

import org.nexis.exceptions.ProtocolException;
import org.nexis.base.Validator;
import org.nexus.base.proto.NexusProtocol;

/**
 * validates get header and header message to ensure they do not over load other
 * peers with the number of headers sent
 *
 * @author daviestobialex
 */
public class HeaderSizeValidator implements Validator {

    @Override
    public boolean supports(NexusProtocol.NexusMessage message) {
        return message.hasHeader()
                || message.hasGetHeader();
    }

    @Override
    public void validate(NexusProtocol.NexusEnvelop envelop) throws ProtocolException {
        NexusProtocol.Header headers = envelop.getMessage().getGetHeader();
        if (headers.getHashList().size() > 500) {
            throw new ProtocolException("Number of locators cannot be > 500, received: " + headers.getHashList().size());
        }
    }

}
