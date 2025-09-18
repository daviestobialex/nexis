/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.testers;

import org.nexus.base.NexusBootstrap;
import org.nexus.base.NexusNetwork;

/**
 *
 * @author daviestobialex
 */
public class NexusTestPoint {

    @Deprecated // for test purposes alone and will be removed
    public static void main(String[] args) throws Exception {

        NexusBootstrap peerManager = new NexusBootstrap();

//        peerManager.start(9001, NexusNetwork.LOCALHOSTTEST, 5);
        peerManager.start(9001, NexusNetwork.LOCALHOSTTEST, 5);
//        peerManager.start(9002, NexusNetwork.LOCALHOSTTEST, 5);

        // Keep the JVM alive
        Thread.currentThread().join();
    }

}
