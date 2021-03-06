/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.data.impl.schema.tree;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection.Builder;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;
import org.opendaylight.yangtools.yang.data.api.schema.tree.TreeType;
import org.opendaylight.yangtools.yang.data.api.schema.tree.spi.TreeNode;
import org.opendaylight.yangtools.yang.model.api.ConstraintDefinition;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: would making this Serializable be useful (for Functions and similar?)
abstract class MandatoryLeafEnforcer implements Immutable {
    private static final class NoOp extends MandatoryLeafEnforcer {
        @Override
        protected void enforceOnTreeNode(final TreeNode tree) {
            // Intentional no-op
        }

        @Override
        protected void enforceOnTreeNode(final NormalizedNode<?, ?> normalizedNode) {
            // Intentional no-op
        }
    }

    private static final class Strict extends MandatoryLeafEnforcer {
        private final Collection<YangInstanceIdentifier> mandatoryNodes;

        private Strict(final Collection<YangInstanceIdentifier> mandatoryNodes) {
            this.mandatoryNodes = Preconditions.checkNotNull(mandatoryNodes);
        }

        @Override
        protected void enforceOnTreeNode(final TreeNode tree) {
            enforceOnTreeNode(tree.getData());
        }

        @Override
        protected void enforceOnTreeNode(final NormalizedNode<?, ?> data) {
            for (YangInstanceIdentifier id : mandatoryNodes) {
                final Optional<NormalizedNode<?, ?>> descandant = NormalizedNodes.findNode(data, id);
                Preconditions.checkArgument(descandant.isPresent(), "Node %s is missing mandatory descendant %s",
                        data.getIdentifier(), id);
            }
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(MandatoryLeafEnforcer.class);
    private static final MandatoryLeafEnforcer NOOP_ENFORCER = new NoOp();

    protected abstract void enforceOnTreeNode(final TreeNode tree);

    protected abstract void enforceOnTreeNode(final NormalizedNode<?, ?> normalizedNode);

    private static void findMandatoryNodes(final Builder<YangInstanceIdentifier> builder,
            final YangInstanceIdentifier id, final DataNodeContainer schema, final TreeType type) {
        for (DataSchemaNode child : schema.getChildNodes()) {
            if (SchemaAwareApplyOperation.belongsToTree(type, child)) {
                if (child instanceof ContainerSchemaNode) {
                    final ContainerSchemaNode container = (ContainerSchemaNode) child;
                    if (!container.isPresenceContainer()) {
                        findMandatoryNodes(builder, id.node(NodeIdentifier.create(child.getQName())), container, type);
                    }
                } else {
                    final ConstraintDefinition constraints = child.getConstraints();
                    final Integer minElements = constraints.getMinElements();
                    if (constraints.isMandatory() || (minElements != null && minElements > 0)) {
                        final YangInstanceIdentifier childId = id.node(NodeIdentifier.create(child.getQName()));
                        LOG.debug("Adding mandatory child {}", childId);
                        builder.add(childId.toOptimized());
                    }
                }
            }
        }
    }

    static MandatoryLeafEnforcer forContainer(final DataNodeContainer schema, final TreeType type) {
        switch (type) {
        case CONFIGURATION:
            final Builder<YangInstanceIdentifier> builder = ImmutableList.builder();
            findMandatoryNodes(builder, YangInstanceIdentifier.EMPTY, schema, type);
            final Collection<YangInstanceIdentifier> mandatoryNodes = builder.build();
            return mandatoryNodes.isEmpty() ? NOOP_ENFORCER : new Strict(mandatoryNodes);
        case OPERATIONAL:
            return NOOP_ENFORCER;
        default:
            throw new UnsupportedOperationException(String.format("Not supported tree type %s", type));
        }
    }
}
