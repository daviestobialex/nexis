/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.example;

import org.nexis.core.NexisInstance;
import org.nexis.base.NexusNetwork;

/**
 * This class serves as an example of how to start and use a nexus instance
 *
 * @author daviestobialex
 */
public class NexusStartInstanceExample {

    // for test purposes alone and will be removed
    public static void main(String[] args) throws Exception {

        NexisInstance businessInstance = new NexisInstance(NexusNetwork.TESTNET, false);

        businessInstance
                .start(9004)
                .connect(5)
                .startBlockChainSync();

        // Keep the JVM alive
        Thread.currentThread().join();
    }

}
