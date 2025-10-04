/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.starter;

import org.nexis.core.NexisInstance;
import org.nexis.base.NexusNetwork;

/**
 *
 * @author daviestobialex
 */
public class NexusTestPoint {

    // for test purposes alone and will be removed
    public static void main(String[] args) throws Exception {

        NexisInstance businessInstance = new NexisInstance(NexusNetwork.LOCALHOSTTEST, true);

        businessInstance
                .start(9000)
                .connect(5);

        // Keep the JVM alive
        Thread.currentThread().join();
    }

}
