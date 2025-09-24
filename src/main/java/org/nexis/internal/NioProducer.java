/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package org.nexis.internal;

import io.netty.channel.Channel;

/**
 *
 * @author daviestobialex
 */
public interface NioProducer {

    public default void send(Channel channel, Object o) {
        channel.writeAndFlush(o);
    }
}
