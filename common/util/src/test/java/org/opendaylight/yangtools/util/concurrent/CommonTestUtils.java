/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yangtools.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.Uninterruptibles;

/**
 * Some common test utilities.
 *
 * @author Thomas Pantelis
 */
public class CommonTestUtils {

    public interface Invoker {
        ListenableFuture<?> invokeExecutor( ListeningExecutorService executor,
                CountDownLatch blockingLatch );
    }

    public static final Invoker SUBMIT_CALLABLE = new Invoker() {
        @Override
        public ListenableFuture<?> invokeExecutor( ListeningExecutorService executor,
                final CountDownLatch blockingLatch ) {
            return executor.submit( new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    if (blockingLatch != null ) {
                        Uninterruptibles.awaitUninterruptibly( blockingLatch );
                    }
                    return null;
                }
            } );
        }
    };

    public static final Invoker SUBMIT_RUNNABLE =  new Invoker() {
        @Override
        public ListenableFuture<?> invokeExecutor( ListeningExecutorService executor,
                final CountDownLatch blockingLatch ) {
            return executor.submit( new Runnable() {
                @Override
                public void run() {
                    if (blockingLatch != null ) {
                        Uninterruptibles.awaitUninterruptibly( blockingLatch );
                    }
                }
            } );
        }
    };

    public static final Invoker SUBMIT_RUNNABLE_WITH_RESULT = new Invoker() {
        @Override
        public ListenableFuture<?> invokeExecutor( ListeningExecutorService executor,
                final CountDownLatch blockingLatch ) {
            return executor.submit( new Runnable() {
                @Override
                public void run() {
                    if (blockingLatch != null ) {
                        Uninterruptibles.awaitUninterruptibly( blockingLatch );
                    }
                }
            }, "foo" );
        }
    };
}
