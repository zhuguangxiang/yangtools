/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.stmt.reactor;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import java.util.Collection;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.meta.DeclaredStatement;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.AugmentStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ChoiceStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ConfigStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.KeyStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RefineStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;
import org.opendaylight.yangtools.yang.model.api.stmt.UsesStatement;
import org.opendaylight.yangtools.yang.parser.spi.meta.InferenceException;
import org.opendaylight.yangtools.yang.parser.spi.meta.NamespaceBehaviour.NamespaceStorageNode;
import org.opendaylight.yangtools.yang.parser.spi.meta.NamespaceBehaviour.Registry;
import org.opendaylight.yangtools.yang.parser.spi.meta.QNameCacheNamespace;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContextUtils;
import org.opendaylight.yangtools.yang.parser.spi.validation.ValidationBundlesNamespace;
import org.opendaylight.yangtools.yang.parser.spi.validation.ValidationBundlesNamespace.ValidationBundleType;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.GroupingUtils;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.Utils;

final class SubstatementContext<A, D extends DeclaredStatement<A>, E extends EffectiveStatement<A, D>>
        extends StatementContextBase<A, D, E> {

    private final StatementContextBase<?, ?, ?> parent;
    private final A argument;
    private volatile SchemaPath schemaPath;

    SubstatementContext(final StatementContextBase<?, ?, ?> parent, final ContextBuilder<A, D, E> builder) {
        super(builder);
        this.parent = Preconditions.checkNotNull(parent, "Parent must not be null");
        this.argument = builder.getDefinition().parseArgumentValue(this, builder.getRawArgument());
    }

    @SuppressWarnings("unchecked")
    SubstatementContext(final SubstatementContext<A, D, E> original,
            final QNameModule newQNameModule,
            final StatementContextBase<?, ?, ?> newParent, final TypeOfCopy typeOfCopy) {
        super(original);
        this.parent = newParent;

        if (newQNameModule != null) {
            if (original.argument instanceof QName) {
                QName originalQName = (QName) original.argument;
                this.argument = (A)
                        getFromNamespace(QNameCacheNamespace.class,
                            QName.create(newQNameModule, originalQName.getLocalName()));
            } else if (StmtContextUtils.producesDeclared(original, KeyStatement.class)) {
                this.argument = (A) StmtContextUtils.replaceModuleQNameForKey(
                                (StmtContext<Collection<SchemaNodeIdentifier>, KeyStatement, ?>) original,
                                newQNameModule);
            } else {
                this.argument = original.argument;
            }
        } else {
            this.argument = original.argument;
        }
    }


    private void copyDeclaredStmts(final SubstatementContext<A, D, E> original,
            final QNameModule newQNameModule, final TypeOfCopy typeOfCopy) {
        Collection<? extends StatementContextBase<?, ?, ?>> originalDeclaredSubstatements = original
                .declaredSubstatements();
        for (StatementContextBase<?, ?, ?> stmtContext : originalDeclaredSubstatements) {
            if (GroupingUtils.needToCopyByUses(stmtContext)) {
                StatementContextBase<?, ?, ?> copy = stmtContext.createCopy(
                        newQNameModule, this, typeOfCopy);
                this.addEffectiveSubstatement(copy);
            } else if (GroupingUtils.isReusedByUses(stmtContext)) {
                this.addEffectiveSubstatement(stmtContext);
            }
        }
    }

    private void copyEffectiveStmts(final SubstatementContext<A, D, E> original,
            final QNameModule newQNameModule, final TypeOfCopy typeOfCopy) {
        Collection<? extends StatementContextBase<?, ?, ?>> originalEffectiveSubstatements = original
                .effectiveSubstatements();
        for (StatementContextBase<?, ?, ?> stmtContext : originalEffectiveSubstatements) {
            if (GroupingUtils.needToCopyByUses(stmtContext)) {
                StatementContextBase<?, ?, ?> copy = stmtContext.createCopy(
                        newQNameModule, this, typeOfCopy);
                this.addEffectiveSubstatement(copy);
            } else if (GroupingUtils.isReusedByUses(stmtContext)) {
                this.addEffectiveSubstatement(stmtContext);
            }
        }
    }

    @Override
    public StatementContextBase<?, ?, ?> getParentContext() {
        return parent;
    }

    @Override
    public NamespaceStorageNode getParentNamespaceStorage() {
        return parent;
    }

    @Override
    public Registry getBehaviourRegistry() {
        return parent.getBehaviourRegistry();
    }

    @Override
    public RootStatementContext<?, ?, ?> getRoot() {
        return parent.getRoot();
    }

    @Override
    public A getStatementArgument() {
        return argument;
    }

    @Override
    public StatementContextBase<?, ?, ?> createCopy(
            final StatementContextBase<?, ?, ?> newParent, final TypeOfCopy typeOfCopy) {
        return createCopy(null, newParent, typeOfCopy);
    }

    @Override
    public StatementContextBase<A, D, E> createCopy(final QNameModule newQNameModule,
            final StatementContextBase<?, ?, ?> newParent, final TypeOfCopy typeOfCopy) {
        SubstatementContext<A, D, E> copy = new SubstatementContext<>(this, newQNameModule, newParent, typeOfCopy);

        copy.addAllToCopyHistory(this.getCopyHistory());
        copy.addToCopyHistory(typeOfCopy);

        if (this.getOriginalCtx() != null) {
            copy.setOriginalCtx(this.getOriginalCtx());
        } else {
            copy.setOriginalCtx(this);
        }

        definition().onStatementAdded(copy);

        copy.copyDeclaredStmts(this, newQNameModule, typeOfCopy);
        copy.copyEffectiveStmts(this, newQNameModule, typeOfCopy);
        return copy;
    }

    private boolean isSupportedAsShorthandCase() {
        final Collection<?> supportedCaseShorthands = getFromNamespace(ValidationBundlesNamespace.class,
                ValidationBundleType.SUPPORTED_CASE_SHORTHANDS);
        return supportedCaseShorthands == null || supportedCaseShorthands.contains(getPublicDefinition());
    }

    private SchemaPath createSchemaPath() {
        final Optional<SchemaPath> maybeParentPath = parent.getSchemaPath();
        Verify.verify(maybeParentPath.isPresent(), "Parent %s does not have a SchemaPath", parent);
        final SchemaPath parentPath = maybeParentPath.get();

        if (argument instanceof QName) {
            QName qname = (QName) argument;
            if (StmtContextUtils.producesDeclared(this, UsesStatement.class)) {
                return maybeParentPath.orNull();
            }

            final SchemaPath path;
            if (StmtContextUtils.producesDeclared(getParentContext(), ChoiceStatement.class)
                    && isSupportedAsShorthandCase()) {
                path = parentPath.createChild(qname);
            } else {
                path = parentPath;
            }
            return path.createChild(qname);
        }
        if (argument instanceof String) {
            // FIXME: This may yield illegal argument exceptions
            final StatementContextBase<?, ?, ?> originalCtx = getOriginalCtx();
            final QName qname = (originalCtx != null) ? Utils.qNameFromArgument(originalCtx, (String) argument)
                    : Utils.qNameFromArgument(this, (String) argument);
            return parentPath.createChild(qname);
        }
        if (argument instanceof SchemaNodeIdentifier &&
                (StmtContextUtils.producesDeclared(this, AugmentStatement.class)
                   || StmtContextUtils.producesDeclared(this, RefineStatement.class))) {

            return parentPath.createChild(((SchemaNodeIdentifier) argument).getPathFromRoot());
        }
        if (Utils.isUnknownNode(this)) {
            return parentPath.createChild(getPublicDefinition().getStatementName());
        }

        // FIXME: this does not look right
        return maybeParentPath.orNull();
    }

    @Override
    public Optional<SchemaPath> getSchemaPath() {
        SchemaPath local = schemaPath;
        if (local == null) {
            synchronized (this) {
                local = schemaPath;
                if (local == null) {
                    local = createSchemaPath();
                    schemaPath = local;
                }
            }

        }

        return Optional.fromNullable(local);
    }

    @Override
    public boolean isRootContext() {
        return false;
    }

    @Override
    public boolean isConfiguration() {
        StmtContext<Boolean, ?, ?> configStatement = StmtContextUtils
                .findFirstSubstatement(this, ConfigStatement.class);

        /*
         * If "config" statement is not specified, the default is the same as
         * the parent schema node's "config" value.
         */
        if (configStatement == null) {
            return parent.isConfiguration();
        }

        /*
         * If a parent node has "config" set to "true", the node underneath it can
         * have "config" set to "true" or "false".
         */
        if (parent.isConfiguration()) {
            return configStatement.getStatementArgument();
        }

        /*
         * If a parent node has "config" set to "false", no node underneath it
         * can have "config" set to "true", therefore only "false" is permitted.
         */
        if (!configStatement.getStatementArgument()) {
            return false;
        }

        throw new InferenceException(
                "Parent node has config statement set to false, therefore no node underneath it can have config set to true",
                getStatementSourceReference());
    }

    @Override
    public boolean isEnabledSemanticVersioning() {
        return parent.isEnabledSemanticVersioning();
    }
}
