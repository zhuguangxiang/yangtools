/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.util;

import com.google.common.annotations.Beta;
import java.util.UUID;
import org.opendaylight.yangtools.concepts.Identifier;

/**
 * Utility {@link Identifier} backed by a {@link UUID}.
 *
 * @deprecated Treats instantiations as equal, not providing safety against mixing instances from different modules.
 *             Use a subclass of {@link AbstractUUIDIdentifier} instead.
 */
@Deprecated
@Beta
public final class UUIDIdentifier extends AbstractUUIDIdentifier<UUIDIdentifier> {
    private static final long serialVersionUID = 1L;

    public UUIDIdentifier(final UUID uuid) {
        super(uuid);
    }

    /**
     * @deprecated Use {@link #getValue()} instead.
     */
    @Deprecated
    public UUID getUuid() {
        return getValue();
    }
}
