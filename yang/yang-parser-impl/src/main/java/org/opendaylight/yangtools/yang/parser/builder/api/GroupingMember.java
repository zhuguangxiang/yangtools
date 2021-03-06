/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.builder.api;

/**
 * Marker interface for nodes which can be defined in grouping statement.
 * [anyxml, choice, container, grouping, leaf, leaf-list, list, typedef, uses]
 *
 * @deprecated Pre-Beryllium implementation, scheduled for removal.
 */
@Deprecated
public interface GroupingMember extends Builder {

    /**
     *
     * @return true, if this node is added by uses statement, false otherwise
     */
    boolean isAddedByUses();

    /**
     * Set if this node is added by uses.
     *
     * @param addedByUses information about uses statement
     */
    void setAddedByUses(boolean addedByUses);

}
