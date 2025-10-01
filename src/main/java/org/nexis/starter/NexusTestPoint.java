/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.starter;

import org.nexis.core.NexusNode;
import org.nexis.base.NexusNetwork;

/**
 *
 * @author daviestobialex
 */
public class NexusTestPoint {

    // for test purposes alone and will be removed
    public static void main(String[] args) throws Exception {

        NexusNode peerManager = new NexusNode(NexusNetwork.LOCALHOSTTEST);

        peerManager.start(9000, 5, true);

        // Keep the JVM alive
        Thread.currentThread().join();
    }

}
