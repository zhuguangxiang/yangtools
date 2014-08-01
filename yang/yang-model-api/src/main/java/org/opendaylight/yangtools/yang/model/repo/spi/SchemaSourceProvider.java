/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/eplv10.html
 */
package org.opendaylight.yangtools.yang.model.repo.spi;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

/**
 * Schema source provider implementations take care of resolving a {@link SourceIdentifier}
 * into a particular representation of the schema source. Examples of resolution include
 * fetching the source from an external source, opening a classpath resource, or similar.
 *
 * @param <T> Schema source representation type provided by this implementation
 */
@Beta
public interface SchemaSourceProvider<T extends SchemaSourceRepresentation> {
    /**
     * Returns a representation a for supplied YANG source identifier. The resolution
     * criteria are as follows:
     *
     * <ul>
     * <li> If the source identifier specifies a revision, this method returns either
     * a representation of that particular revision, or report the identifier as absent
     * by returning {@link Optional#absent()}.
     * <li> If the source identifier does not specify a revision, this method returns
     * the newest available revision, or {@link Optional#absent()}.
     *
     * In either case the returned representation is required to report a non-null
     * revision in the {@link SourceIdentifier} returned from
     * {@link SchemaSourceRepresentation#getIdentifier()}.
     *
     * Implementations are not required to provide constant behavior in time, notably
     * this different invocation of this method may produce different results.
     *
     * @param sourceIdentifier source identifier
     * @return source representation if supplied YANG module is available
     *         {@link Optional#absent()} otherwise.
     */
    ListenableFuture<Optional<T>> getSource(SourceIdentifier sourceIdentifier);
}