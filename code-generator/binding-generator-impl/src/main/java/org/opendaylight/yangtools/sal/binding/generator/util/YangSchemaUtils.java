package org.opendaylight.yangtools.sal.binding.generator.util;

import java.util.List;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.*;

import com.google.common.base.Preconditions;

public class YangSchemaUtils {

    public static final String AUGMENT_IDENTIFIER = "augment-identifier";


    public YangSchemaUtils() {
        throw new UnsupportedOperationException("Helper class. Instantiation is prohibited");
    }


    public static QName getAugmentationQName(AugmentationSchema augmentation) {
        Preconditions.checkNotNull(augmentation, "Augmentation must not be null.");
        QName identifier = getAugmentationIdentifier(augmentation);
        if(identifier != null) {
            return identifier;
        }
        for(DataSchemaNode child : augmentation.getChildNodes()) {
            // FIXME: Return true name
            return QName.create(child.getQName(), "foo_augment");
        }
        // FIXME: Allways return a qname with module namespace.
        return null;
    }

    public static QName getAugmentationIdentifier(AugmentationSchema augmentation) {
        for(UnknownSchemaNode extension : augmentation.getUnknownSchemaNodes()) {
            if(AUGMENT_IDENTIFIER.equals(extension.getNodeType().getLocalName())) {
                return extension.getQName();
            }
        }
        return null;
    }


    public static TypeDefinition<?> findTypeDefinition(SchemaContext context, SchemaPath path) {
        List<QName> arguments = path.getPath();
        QName first = arguments.get(0);
        QName typeQName = arguments.get(arguments.size() -1);
        DataNodeContainer previous = context.findModuleByNamespaceAndRevision(first.getNamespace(), first.getRevision());
        if(previous == null) {
            return null;
        }
        Preconditions.checkArgument(arguments.size() == 1);
        for (QName qName : arguments) {
            //previous.getDataChildByName(qName);
        }
        for(TypeDefinition<?> typedef : previous.getTypeDefinitions()) {
            if(typedef.getQName().equals(typeQName)) {
                return typedef;
            }
        }
        return null;
    }
}