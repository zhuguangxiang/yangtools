/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opendaylight.yangtools.yang.parser.builder.impl.BuilderUtils.fillAugmentTarget;
import static org.opendaylight.yangtools.yang.parser.builder.impl.BuilderUtils.findBaseIdentity;
import static org.opendaylight.yangtools.yang.parser.builder.impl.BuilderUtils.findModuleFromBuilders;
import static org.opendaylight.yangtools.yang.parser.builder.impl.BuilderUtils.findModuleFromContext;
import static org.opendaylight.yangtools.yang.parser.builder.impl.BuilderUtils.findSchemaNode;
import static org.opendaylight.yangtools.yang.parser.builder.impl.BuilderUtils.findSchemaNodeInModule;
import static org.opendaylight.yangtools.yang.parser.builder.impl.BuilderUtils.processAugmentation;
import static org.opendaylight.yangtools.yang.parser.builder.impl.TypeUtils.resolveType;
import static org.opendaylight.yangtools.yang.parser.builder.impl.TypeUtils.resolveTypeUnion;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBiMap;
import com.google.common.io.ByteSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.concurrent.Immutable;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.opendaylight.yangtools.antlrv4.code.gen.YangLexer;
import org.opendaylight.yangtools.antlrv4.code.gen.YangParser;
import org.opendaylight.yangtools.antlrv4.code.gen.YangParser.YangContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.ExtensionDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleIdentifier;
import org.opendaylight.yangtools.yang.model.api.ModuleImport;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.parser.api.YangContextParser;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.parser.builder.api.AugmentationSchemaBuilder;
import org.opendaylight.yangtools.yang.parser.builder.api.AugmentationTargetBuilder;
import org.opendaylight.yangtools.yang.parser.builder.api.Builder;
import org.opendaylight.yangtools.yang.parser.builder.api.DataNodeContainerBuilder;
import org.opendaylight.yangtools.yang.parser.builder.api.DataSchemaNodeBuilder;
import org.opendaylight.yangtools.yang.parser.builder.api.ExtensionBuilder;
import org.opendaylight.yangtools.yang.parser.builder.api.GroupingBuilder;
import org.opendaylight.yangtools.yang.parser.builder.api.SchemaNodeBuilder;
import org.opendaylight.yangtools.yang.parser.builder.api.TypeAwareBuilder;
import org.opendaylight.yangtools.yang.parser.builder.api.UnknownSchemaNodeBuilder;
import org.opendaylight.yangtools.yang.parser.builder.api.UsesNodeBuilder;
import org.opendaylight.yangtools.yang.parser.builder.impl.BuilderUtils;
import org.opendaylight.yangtools.yang.parser.builder.impl.ChoiceBuilder;
import org.opendaylight.yangtools.yang.parser.builder.impl.ChoiceCaseBuilder;
import org.opendaylight.yangtools.yang.parser.builder.impl.DeviationBuilder;
import org.opendaylight.yangtools.yang.parser.builder.impl.GroupingUtils;
import org.opendaylight.yangtools.yang.parser.builder.impl.IdentitySchemaNodeBuilder;
import org.opendaylight.yangtools.yang.parser.builder.impl.IdentityrefTypeBuilder;
import org.opendaylight.yangtools.yang.parser.builder.impl.ModuleBuilder;
import org.opendaylight.yangtools.yang.parser.builder.impl.ModuleImpl;
import org.opendaylight.yangtools.yang.parser.builder.impl.UnionTypeBuilder;
import org.opendaylight.yangtools.yang.parser.builder.util.Comparators;
import org.opendaylight.yangtools.yang.parser.util.ModuleDependencySort;
import org.opendaylight.yangtools.yang.parser.util.NamedByteArrayInputStream;
import org.opendaylight.yangtools.yang.parser.util.NamedFileInputStream;
import org.opendaylight.yangtools.yang.parser.util.NamedInputStream;
import org.opendaylight.yangtools.yang.parser.util.YangParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Immutable
public final class YangParserImpl implements YangContextParser {
    private static final Logger LOG = LoggerFactory.getLogger(YangParserImpl.class);
    private static final String FAIL_DEVIATION_TARGET = "Failed to find deviation target.";
    private static final Splitter COLON_SPLITTER = Splitter.on(':');
    private static final YangParserImpl INSTANCE = new YangParserImpl();

    public static YangParserImpl getInstance() {
        return INSTANCE;
    }

    @Override
    @Deprecated
    public Set<Module> parseYangModels(final File yangFile, final File directory) {
        try {
            return parseFile(yangFile, directory).getModules();
        } catch (IOException | YangSyntaxErrorException e) {
            throw new YangParseException("Failed to parse yang data", e);
        }
    }

    @Override
    public SchemaContext parseFile(final File yangFile, final File directory) throws IOException,
    YangSyntaxErrorException {
        Preconditions.checkState(yangFile.exists(), yangFile + " does not exists");
        Preconditions.checkState(directory.exists(), directory + " does not exists");
        Preconditions.checkState(directory.isDirectory(), directory + " is not a directory");

        final String yangFileName = yangFile.getName();
        final String[] fileList = checkNotNull(directory.list(), directory + " not found or is not a directory");

        Map<ByteSource, File> sourceToFile = new LinkedHashMap<>();
        ByteSource mainFileSource = BuilderUtils.fileToByteSource(yangFile);
        sourceToFile.put(mainFileSource, yangFile);

        for (String fileName : fileList) {
            if (fileName.equals(yangFileName)) {
                continue;
            }
            File dependency = new File(directory, fileName);
            if (dependency.isFile()) {
                sourceToFile.put(BuilderUtils.fileToByteSource(dependency), dependency);
            }
        }

        Map<ByteSource, ModuleBuilder> sourceToBuilder = parseSourcesToBuilders(sourceToFile.keySet());
        ModuleBuilder main = sourceToBuilder.get(mainFileSource);

        List<ModuleBuilder> moduleBuilders = new ArrayList<>();
        moduleBuilders.add(main);
        filterImports(main, new ArrayList<>(sourceToBuilder.values()), moduleBuilders);
        Collection<ModuleBuilder> resolved = resolveSubmodules(moduleBuilders);

        // module builders sorted by dependencies
        List<ModuleBuilder> sortedBuilders = ModuleDependencySort.sort(resolved);
        LinkedHashMap<String, TreeMap<Date, ModuleBuilder>> modules = resolveModulesWithImports(sortedBuilders, null);
        Collection<Module> unsorted = build(modules).values();
        Set<Module> result = new LinkedHashSet<>(
                ModuleDependencySort.sort(unsorted.toArray(new Module[unsorted.size()])));
        return resolveSchemaContext(result);
    }

    @Override
    @Deprecated
    public Set<Module> parseYangModels(final List<File> yangFiles) {
        return parseFiles(yangFiles).getModules();
    }

    @Override
    public SchemaContext parseFiles(final Collection<File> yangFiles) {
        Collection<Module> unsorted = parseYangModelsMapped(yangFiles).values();
        Set<Module> sorted = new LinkedHashSet<>(
                ModuleDependencySort.sort(unsorted.toArray(new Module[unsorted.size()])));
        return resolveSchemaContext(sorted);
    }

    @Override
    @Deprecated
    public Set<Module> parseYangModels(final List<File> yangFiles, final SchemaContext context) {
        try {
            return parseFiles(yangFiles, context).getModules();
        } catch (IOException | YangSyntaxErrorException e) {
            throw new YangParseException("Failed to parse yang data", e);
        }
    }

    @Override
    public SchemaContext parseFiles(final Collection<File> yangFiles, final SchemaContext context) throws IOException,
    YangSyntaxErrorException {
        if (yangFiles == null) {
            return resolveSchemaContext(Collections.<Module> emptySet());
        }

        Collection<ByteSource> sources = BuilderUtils.filesToByteSources(yangFiles);
        return parseSources(sources, context);
    }

    @Override
    @Deprecated
    public Set<Module> parseYangModelsFromStreams(final List<InputStream> yangModelStreams) {
        try {
            Collection<ByteSource> sources = BuilderUtils.streamsToByteSources(yangModelStreams);
            return parseSources(sources).getModules();
        } catch (IOException | YangSyntaxErrorException e) {
            throw new YangParseException("Failed to parse yang data", e);
        }
    }

    @Override
    public SchemaContext parseSources(final Collection<ByteSource> sources) throws IOException,
    YangSyntaxErrorException {
        Collection<Module> unsorted = parseYangModelSources(sources).values();
        Set<Module> sorted = new LinkedHashSet<>(
                ModuleDependencySort.sort(unsorted.toArray(new Module[unsorted.size()])));
        return resolveSchemaContext(sorted);
    }

    @Override
    @Deprecated
    public Set<Module> parseYangModelsFromStreams(final List<InputStream> yangModelStreams, final SchemaContext context) {
        try {
            Collection<ByteSource> sources = BuilderUtils.streamsToByteSources(yangModelStreams);
            return parseSources(sources, context).getModules();
        } catch (IOException | YangSyntaxErrorException e) {
            throw new YangParseException("Failed to parse yang data", e);
        }
    }

    @Override
    public SchemaContext parseSources(final Collection<ByteSource> sources, final SchemaContext context)
            throws IOException, YangSyntaxErrorException {
        if (sources == null) {
            return resolveSchemaContext(Collections.<Module> emptySet());
        }

        final List<ModuleBuilder> sorted = resolveModuleBuilders(sources, context);
        final Map<String, TreeMap<Date, ModuleBuilder>> modules = resolveModulesWithImports(sorted, context);

        final Set<Module> unsorted = new LinkedHashSet<>(build(modules).values());
        if (context != null) {
            for (Module m : context.getModules()) {
                if (!unsorted.contains(m)) {
                    unsorted.add(m);
                }
            }
        }
        Set<Module> result = new LinkedHashSet<>(
                ModuleDependencySort.sort(unsorted.toArray(new Module[unsorted.size()])));
        return resolveSchemaContext(result);
    }

    private LinkedHashMap<String, TreeMap<Date, ModuleBuilder>> resolveModulesWithImports(final List<ModuleBuilder> sorted,
            final SchemaContext context) {
        final LinkedHashMap<String, TreeMap<Date, ModuleBuilder>> modules = orderModules(sorted);
        for (ModuleBuilder module : sorted) {
            if (module != null) {
                for (ModuleImport imp : module.getImports().values()) {
                    String prefix = imp.getPrefix();
                    ModuleBuilder targetModule = findModuleFromBuilders(modules, module, prefix, 0);
                    if (targetModule == null) {
                        Module result = findModuleFromContext(context, module, prefix, 0);
                        targetModule = new ModuleBuilder(result);
                        TreeMap<Date, ModuleBuilder> map = modules.get(prefix);
                        if (map == null) {
                            map = new TreeMap<>();
                            map.put(targetModule.getRevision(), targetModule);
                            modules.put(targetModule.getName(), map);
                        } else {
                            map.put(targetModule.getRevision(), targetModule);
                        }
                    }
                    module.addImportedModule(prefix, targetModule);
                }
            }
        }
        return modules;
    }

    @Override
    public Map<File, Module> parseYangModelsMapped(final Collection<File> yangFiles) {
        if (yangFiles == null || yangFiles.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<ByteSource, File> byteSourceToFile = new HashMap<>();
        for (final File file : yangFiles) {
            ByteSource source = new ByteSource() {
                @Override
                public InputStream openStream() throws IOException {
                    return new NamedFileInputStream(file, file.getPath());
                }
            };
            byteSourceToFile.put(source, file);
        }

        Map<ByteSource, Module> byteSourceToModule;
        try {
            byteSourceToModule = parseYangModelSources(byteSourceToFile.keySet());
        } catch (IOException | YangSyntaxErrorException e) {
            throw new YangParseException("Failed to parse yang data", e);
        }
        Map<File, Module> result = new LinkedHashMap<>();
        for (Map.Entry<ByteSource, Module> entry : byteSourceToModule.entrySet()) {
            result.put(byteSourceToFile.get(entry.getKey()), entry.getValue());
        }
        return result;
    }

    @Override
    public Map<InputStream, Module> parseYangModelsFromStreamsMapped(final Collection<InputStream> yangModelStreams) {
        if (yangModelStreams == null || yangModelStreams.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<ByteSource, InputStream> sourceToStream = new HashMap<>();
        for (final InputStream stream : yangModelStreams) {
            ByteSource source = new ByteSource() {
                @Override
                public InputStream openStream() throws IOException {
                    return NamedByteArrayInputStream.create(stream);
                }
            };
            sourceToStream.put(source, stream);
        }

        Map<ByteSource, Module> sourceToModule;
        try {
            sourceToModule = parseYangModelSources(sourceToStream.keySet());
        } catch (IOException | YangSyntaxErrorException e) {
            throw new YangParseException("Failed to parse yang data", e);
        }
        Map<InputStream, Module> result = new LinkedHashMap<>();
        for (Map.Entry<ByteSource, Module> entry : sourceToModule.entrySet()) {
            result.put(sourceToStream.get(entry.getKey()), entry.getValue());
        }
        return result;
    }

    @Override
    public SchemaContext resolveSchemaContext(final Set<Module> modules) {
        // after merging parse method with this one, add support for getting
        // submodule sources.
        Map<ModuleIdentifier, String> identifiersToSources = new HashMap<>();
        for (Module module : modules) {
            ModuleImpl moduleImpl = (ModuleImpl) module;
            identifiersToSources.put(module, moduleImpl.getSource());
        }
        return new SchemaContextImpl(modules, identifiersToSources);
    }

    private Map<ByteSource, Module> parseYangModelSources(final Collection<ByteSource> sources) throws IOException,
    YangSyntaxErrorException {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<ByteSource, ModuleBuilder> sourceToBuilder = resolveSources(sources);
        // sort and check for duplicates
        List<ModuleBuilder> sorted = ModuleDependencySort.sort(sourceToBuilder.values());
        Map<String, TreeMap<Date, ModuleBuilder>> modules = resolveModulesWithImports(sorted, null);
        Map<ModuleBuilder, Module> builderToModule = build(modules);
        Map<ModuleBuilder, ByteSource> builderToSource = HashBiMap.create(sourceToBuilder).inverse();
        sorted = ModuleDependencySort.sort(builderToModule.keySet());

        Map<ByteSource, Module> result = new LinkedHashMap<>();
        for (ModuleBuilder moduleBuilder : sorted) {
            Module value = checkNotNull(builderToModule.get(moduleBuilder), "Cannot get module for %s", moduleBuilder);
            result.put(builderToSource.get(moduleBuilder), value);
        }

        return result;
    }

    /**
     * Parse streams and resolve submodules.
     *
     * @param streams
     *            collection of streams to parse
     * @return map, where key is source stream and value is module builder
     *         parsed from stream
     * @throws YangSyntaxErrorException
     */
    // TODO: remove ByteSource result after removing YangModelParser
    private Map<ByteSource, ModuleBuilder> resolveSources(final Collection<ByteSource> streams) throws IOException,
    YangSyntaxErrorException {
        Map<ByteSource, ModuleBuilder> builders = parseSourcesToBuilders(streams);
        return resolveSubmodules(builders);
    }

    private Map<ByteSource, ModuleBuilder> parseSourcesToBuilders(final Collection<ByteSource> sources)
            throws IOException, YangSyntaxErrorException {
        final ParseTreeWalker walker = new ParseTreeWalker();
        final Map<ByteSource, ParseTree> sourceToTree = parseYangSources(sources);
        final Map<ByteSource, ModuleBuilder> sourceToBuilder = new LinkedHashMap<>();

        // validate yang
        new YangModelBasicValidator(walker).validate(sourceToTree.values());

        YangParserListenerImpl yangModelParser;
        for (Map.Entry<ByteSource, ParseTree> entry : sourceToTree.entrySet()) {
            ByteSource source = entry.getKey();
            String path = null; // TODO refactor to Optional
            // TODO refactor so that path can be retrieved without opening
            // stream: NamedInputStream -> NamedByteSource ?
            try (InputStream stream = source.openStream()) {
                if (stream instanceof NamedInputStream) {
                    path = stream.toString();
                }
            }
            yangModelParser = new YangParserListenerImpl(path);
            walker.walk(yangModelParser, entry.getValue());
            ModuleBuilder moduleBuilder = yangModelParser.getModuleBuilder();
            moduleBuilder.setSource(source);
            sourceToBuilder.put(source, moduleBuilder);
        }
        return sourceToBuilder;
    }

    private Map<ByteSource, ModuleBuilder> resolveSubmodules(final Map<ByteSource, ModuleBuilder> builders) {
        Map<ByteSource, ModuleBuilder> modules = new HashMap<>();
        Set<ModuleBuilder> submodules = new HashSet<>();
        for (Map.Entry<ByteSource, ModuleBuilder> entry : builders.entrySet()) {
            ModuleBuilder moduleBuilder = entry.getValue();
            if (moduleBuilder.isSubmodule()) {
                submodules.add(moduleBuilder);
            } else {
                modules.put(entry.getKey(), moduleBuilder);
            }
        }

        Collection<ModuleBuilder> values = modules.values();
        for (ModuleBuilder submodule : submodules) {
            for (ModuleBuilder module : values) {
                if (module.getName().equals(submodule.getBelongsTo())) {
                    addSubmoduleToModule(submodule, module);
                }
            }
        }
        return modules;
    }

    /**
     * Traverse collection of builders, find builders representing submodule and
     * add this submodule to its parent module.
     *
     * @param builders
     *            collection of builders containing modules and submodules
     * @return collection of module builders
     */
    private Collection<ModuleBuilder> resolveSubmodules(final Collection<ModuleBuilder> builders) {
        Collection<ModuleBuilder> modules = new HashSet<>();
        Set<ModuleBuilder> submodules = new HashSet<>();
        for (ModuleBuilder moduleBuilder : builders) {
            if (moduleBuilder.isSubmodule()) {
                submodules.add(moduleBuilder);
            } else {
                modules.add(moduleBuilder);
            }
        }

        for (ModuleBuilder submodule : submodules) {
            for (ModuleBuilder module : modules) {
                if (module.getName().equals(submodule.getBelongsTo())) {
                    addSubmoduleToModule(submodule, module);
                }
            }
        }
        return modules;
    }

    private void addSubmoduleToModule(final ModuleBuilder submodule, final ModuleBuilder module) {
        submodule.setParent(module);
        module.getDirtyNodes().addAll(submodule.getDirtyNodes());
        module.getImports().putAll(submodule.getImports());
        module.getAugments().addAll(submodule.getAugments());
        module.getAugmentBuilders().addAll(submodule.getAugmentBuilders());
        module.getAllAugments().addAll(submodule.getAllAugments());
        module.getChildNodeBuilders().addAll(submodule.getChildNodeBuilders());
        module.getChildNodes().putAll(submodule.getChildNodes());
        module.getGroupings().addAll(submodule.getGroupings());
        module.getGroupingBuilders().addAll(submodule.getGroupingBuilders());
        module.getTypeDefinitions().addAll(submodule.getTypeDefinitions());
        module.getTypeDefinitionBuilders().addAll(submodule.getTypeDefinitionBuilders());
        module.getUsesNodes().addAll(submodule.getUsesNodes());
        module.getUsesNodeBuilders().addAll(submodule.getUsesNodeBuilders());
        module.getAllGroupings().addAll(submodule.getAllGroupings());
        module.getAllUsesNodes().addAll(submodule.getAllUsesNodes());
        module.getRpcs().addAll(submodule.getRpcs());
        module.getAddedRpcs().addAll(submodule.getAddedRpcs());
        module.getNotifications().addAll(submodule.getNotifications());
        module.getAddedNotifications().addAll(submodule.getAddedNotifications());
        module.getIdentities().addAll(submodule.getIdentities());
        module.getAddedIdentities().addAll(submodule.getAddedIdentities());
        module.getFeatures().addAll(submodule.getFeatures());
        module.getAddedFeatures().addAll(submodule.getAddedFeatures());
        module.getDeviations().addAll(submodule.getDeviations());
        module.getDeviationBuilders().addAll(submodule.getDeviationBuilders());
        module.getExtensions().addAll(submodule.getExtensions());
        module.getAddedExtensions().addAll(submodule.getAddedExtensions());
        module.getUnknownNodes().addAll(submodule.getUnknownNodes());
        module.getAllUnknownNodes().addAll(submodule.getAllUnknownNodes());
    }

    private List<ModuleBuilder> resolveModuleBuilders(final Collection<ByteSource> yangFileStreams,
            final SchemaContext context) throws IOException, YangSyntaxErrorException {
        Map<ByteSource, ModuleBuilder> parsedBuilders = resolveSources(yangFileStreams);
        ModuleBuilder[] builders = new ModuleBuilder[parsedBuilders.size()];
        parsedBuilders.values().toArray(builders);

        // module dependency graph sorted
        List<ModuleBuilder> sorted;
        if (context == null) {
            sorted = ModuleDependencySort.sort(builders);
        } else {
            sorted = ModuleDependencySort.sortWithContext(context, builders);
        }
        return sorted;
    }

    /**
     * Order modules by name and revision.
     *
     * @param modules
     *            topologically sorted modules
     * @return modules ordered by name and revision
     */
    private LinkedHashMap<String, TreeMap<Date, ModuleBuilder>> orderModules(final List<ModuleBuilder> modules) {
        final LinkedHashMap<String, TreeMap<Date, ModuleBuilder>> result = new LinkedHashMap<>();
        for (final ModuleBuilder builder : modules) {
            if (builder == null) {
                continue;
            }
            final String builderName = builder.getName();
            Date builderRevision = builder.getRevision();
            if (builderRevision == null) {
                builderRevision = new Date(0L);
            }
            TreeMap<Date, ModuleBuilder> builderByRevision = result.get(builderName);
            if (builderByRevision == null) {
                builderByRevision = new TreeMap<>();
                builderByRevision.put(builderRevision, builder);
                result.put(builderName, builderByRevision);
            } else {
                builderByRevision.put(builderRevision, builder);
            }
        }
        return result;
    }

    /**
     * Find {@code main} dependencies from {@code other} and add them to
     * {@code filtered}.
     *
     * @param main
     *            main yang module
     * @param other
     *            all loaded modules
     * @param filtered
     *            collection to fill up
     */
    private void filterImports(final ModuleBuilder main, final Collection<ModuleBuilder> other,
            final Collection<ModuleBuilder> filtered) {
        Map<String, ModuleImport> imports = main.getImports();

        // if this is submodule, add parent to filtered and pick its imports
        if (main.isSubmodule()) {
            TreeMap<Date, ModuleBuilder> dependencies = new TreeMap<>();
            for (ModuleBuilder mb : other) {
                if (mb.getName().equals(main.getBelongsTo())) {
                    dependencies.put(mb.getRevision(), mb);
                }
            }
            ModuleBuilder parent = dependencies.get(dependencies.firstKey());
            filtered.add(parent);
            imports.putAll(parent.getImports());
        }

        for (ModuleImport mi : imports.values()) {
            for (ModuleBuilder builder : other) {
                if (mi.getModuleName().equals(builder.getModuleName())) {
                    if (mi.getRevision() == null) {
                        if (!filtered.contains(builder)) {
                            filtered.add(builder);
                            filterImports(builder, other, filtered);
                        }
                    } else {
                        if (mi.getRevision().equals(builder.getRevision())) {
                            if (!filtered.contains(builder)) {
                                filtered.add(builder);
                                filterImports(builder, other, filtered);
                            }
                        }
                    }
                }
            }
        }
    }

    private Map<ByteSource, ParseTree> parseYangSources(final Collection<ByteSource> sources) throws IOException,
    YangSyntaxErrorException {
        final Map<ByteSource, ParseTree> trees = new HashMap<>();
        for (ByteSource source : sources) {
            trees.put(source, parseYangSource(source));
        }
        return trees;
    }

    private YangContext parseYangSource(final ByteSource source) throws IOException, YangSyntaxErrorException {
        try (InputStream stream = source.openStream()) {
            final ANTLRInputStream input = new ANTLRInputStream(stream);
            final YangLexer lexer = new YangLexer(input);
            final CommonTokenStream tokens = new CommonTokenStream(lexer);
            final YangParser parser = new YangParser(tokens);
            parser.removeErrorListeners();

            final YangErrorListener errorListener = new YangErrorListener();
            parser.addErrorListener(errorListener);

            final YangContext result = parser.yang();
            errorListener.validate();

            return result;
        }
    }

    /**
     * Mini parser: This parsing context does not validate full YANG module,
     * only parses header up to the revisions and imports.
     *
     * @see org.opendaylight.yangtools.yang.parser.impl.util.YangModelDependencyInfo
     */
    public static YangContext parseStreamWithoutErrorListeners(final InputStream yangStream) {
        YangContext result = null;
        try {
            final ANTLRInputStream input = new ANTLRInputStream(yangStream);
            final YangLexer lexer = new YangLexer(input);
            final CommonTokenStream tokens = new CommonTokenStream(lexer);
            final YangParser parser = new YangParser(tokens);
            parser.removeErrorListeners();
            result = parser.yang();
        } catch (IOException e) {
            LOG.warn("Exception while reading yang file: " + yangStream, e);
        }
        return result;
    }

    /**
     * Creates builder-to-module map based on given modules. Method first
     * resolve unresolved type references, instantiate groupings through uses
     * statements and perform augmentation.
     *
     * Node resolving must be performed in following order:
     * <ol>
     * <li>
     * unresolved type references</li>
     * <li>
     * uses in groupings</li>
     * <li>
     * uses in other nodes</li>
     * <li>
     * augments</li>
     * </ol>
     *
     * @param modules
     *            all loaded modules
     * @return modules mapped on their builders
     */
    private Map<ModuleBuilder, Module> build(final Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        resolveDirtyNodes(modules);
        resolveAugmentsTargetPath(modules);
        resolveUsesTargetGrouping(modules);
        resolveUsesForGroupings(modules);
        resolveUsesForNodes(modules);
        resolveAugments(modules);
        resolveIdentities(modules);
        resolveDeviations(modules);

        // build
        final Map<ModuleBuilder, Module> result = new LinkedHashMap<>();
        for (Map.Entry<String, TreeMap<Date, ModuleBuilder>> entry : modules.entrySet()) {
            for (Map.Entry<Date, ModuleBuilder> childEntry : entry.getValue().entrySet()) {
                final ModuleBuilder moduleBuilder = childEntry.getValue();
                final Module module = moduleBuilder.build();
                result.put(moduleBuilder, module);
            }
        }
        return result;
    }

    /**
     * Resolve all unresolved type references.
     *
     * @param modules
     *            all loaded modules
     */
    private void resolveDirtyNodes(final Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        for (Map.Entry<String, TreeMap<Date, ModuleBuilder>> entry : modules.entrySet()) {
            for (Map.Entry<Date, ModuleBuilder> childEntry : entry.getValue().entrySet()) {
                final ModuleBuilder module = childEntry.getValue();
                resolveUnknownNodes(modules, module);
                resolveDirtyNodes(modules, module);
            }
        }
    }

    /**
     * Search for dirty nodes (node which contains UnknownType) and resolve
     * unknown types.
     *
     * @param modules
     *            all available modules
     * @param module
     *            current module
     */
    private void resolveDirtyNodes(final Map<String, TreeMap<Date, ModuleBuilder>> modules, final ModuleBuilder module) {
        final Set<TypeAwareBuilder> dirtyNodes = module.getDirtyNodes();
        if (!dirtyNodes.isEmpty()) {
            for (TypeAwareBuilder nodeToResolve : dirtyNodes) {
                if (nodeToResolve instanceof UnionTypeBuilder) {
                    // special handling for union types
                    resolveTypeUnion((UnionTypeBuilder) nodeToResolve, modules, module);
                } else if (nodeToResolve.getTypedef() instanceof IdentityrefTypeBuilder) {
                    // special handling for identityref types
                    IdentityrefTypeBuilder idref = (IdentityrefTypeBuilder) nodeToResolve.getTypedef();
                    IdentitySchemaNodeBuilder identity = findBaseIdentity(modules, module, idref.getBaseString(),
                            idref.getLine());
                    if (identity == null) {
                        throw new YangParseException(module.getName(), idref.getLine(), "Failed to find base identity");
                    }
                    idref.setBaseIdentity(identity);
                    nodeToResolve.setType(idref.build());
                } else {
                    resolveType(nodeToResolve, modules, module);
                }
            }
        }
    }

    /**
     * Traverse through augmentations of modules and fix their child nodes
     * schema path.
     *
     * @param modules
     *            all loaded modules
     */
    private void resolveAugmentsTargetPath(final Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        // collect augments from all loaded modules
        final List<AugmentationSchemaBuilder> allAugments = new ArrayList<>();
        for (Map.Entry<String, TreeMap<Date, ModuleBuilder>> entry : modules.entrySet()) {
            for (Map.Entry<Date, ModuleBuilder> inner : entry.getValue().entrySet()) {
                allAugments.addAll(inner.getValue().getAllAugments());
            }
        }

        for (AugmentationSchemaBuilder augment : allAugments) {
            setCorrectAugmentTargetPath(modules, augment);
        }
    }

    /**
     * Find augment target and set correct schema path for all its child nodes.
     *
     * @param modules
     *            all loaded modules
     * @param augment
     *            augment to resolve
     */
    private void setCorrectAugmentTargetPath(final Map<String, TreeMap<Date, ModuleBuilder>> modules,
            final AugmentationSchemaBuilder augment) {
        ModuleBuilder module = BuilderUtils.getParentModule(augment);
        final SchemaPath newSchemaPath;

        Builder parent = augment.getParent();
        if (parent instanceof UsesNodeBuilder) {
            DataNodeContainerBuilder usesParent = ((UsesNodeBuilder) parent).getParent();

            QName baseQName = usesParent.getQName();
            final QNameModule qnm;
            String prefix;
            if (baseQName == null) {
                ModuleBuilder m = BuilderUtils.getParentModule(usesParent);
                qnm = m.getQNameModule();
                prefix = m.getPrefix();
            } else {
                qnm = baseQName.getModule();
                prefix = baseQName.getPrefix();
            }

            SchemaPath s = usesParent.getPath();
            for (QName qn : augment.getTargetPath().getPathFromRoot()) {
                s = s.createChild(QName.create(qnm, prefix, qn.getLocalName()));
            }

            newSchemaPath = s;
        } else {
            final List<QName> newPath = new ArrayList<>();

            for (QName qn : augment.getTargetPath().getPathFromRoot()) {
                QNameModule qnm = module.getQNameModule();
                String localPrefix = qn.getPrefix();
                if (localPrefix != null && !localPrefix.isEmpty()) {
                    ModuleBuilder currentModule = BuilderUtils.getModuleByPrefix(module, localPrefix);
                    if (currentModule == null) {
                        throw new YangParseException(module.getName(), augment.getLine(), "Module with prefix "
                                + localPrefix + " not found.");
                    }
                    qnm = currentModule.getQNameModule();
                }
                newPath.add(QName.create(qnm, localPrefix, qn.getLocalName()));
            }

            /*
             * FIXME: this method of SchemaPath construction is highly ineffective.
             *        It would be great if we could actually dive into the context,
             *        find the actual target node and reuse its SchemaPath. Can we
             *        do that?
             */
            newSchemaPath = SchemaPath.create(newPath, true);
        }
        augment.setTargetNodeSchemaPath(newSchemaPath);

        for (DataSchemaNodeBuilder childNode : augment.getChildNodeBuilders()) {
            correctPathForAugmentNodes(childNode, augment.getTargetNodeSchemaPath());
        }
    }

    /**
     * Set new schema path to node and all its child nodes based on given parent
     * path. This method do not change the namespace.
     *
     * @param node
     *            node which schema path should be updated
     * @param parentPath
     *            schema path of parent node
     */
    private void correctPathForAugmentNodes(final DataSchemaNodeBuilder node, final SchemaPath parentPath) {
        SchemaPath newPath = parentPath.createChild(node.getQName());
        node.setPath(newPath);
        if (node instanceof DataNodeContainerBuilder) {
            for (DataSchemaNodeBuilder child : ((DataNodeContainerBuilder) node).getChildNodeBuilders()) {
                correctPathForAugmentNodes(child, node.getPath());
            }
        }
        if (node instanceof ChoiceBuilder) {
            for (ChoiceCaseBuilder child : ((ChoiceBuilder) node).getCases()) {
                correctPathForAugmentNodes(child, node.getPath());
            }
        }
    }

    /**
     * Check augments for mandatory nodes. If the target node is in another
     * module, then nodes added by the augmentation MUST NOT be mandatory nodes.
     * If mandatory node is found, throw an exception.
     *
     * @param augments
     *            augments to check
     */
    private void checkAugmentMandatoryNodes(final Collection<AugmentationSchemaBuilder> augments) {
        for (AugmentationSchemaBuilder augment : augments) {
            String augmentPrefix = augment.getTargetPath().getPathFromRoot().iterator().next().getPrefix();
            ModuleBuilder module = BuilderUtils.getParentModule(augment);
            String modulePrefix = module.getPrefix();

            if (augmentPrefix == null || augmentPrefix.isEmpty() || augmentPrefix.equals(modulePrefix)) {
                continue;
            }

            for (DataSchemaNodeBuilder childNode : augment.getChildNodeBuilders()) {
                if (childNode.getConstraints().isMandatory()) {
                    throw new YangParseException(augment.getModuleName(), augment.getLine(),
                            "Error in augment parsing: cannot augment mandatory node "
                                    + childNode.getQName().getLocalName());
                }
            }
        }
    }

    /**
     * Go through all augment definitions and resolve them.
     *
     * @param modules
     *            all loaded modules topologically sorted (based on dependencies
     *            between each other)
     */
    private void resolveAugments(final Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        List<ModuleBuilder> all = new ArrayList<>();
        for (Map.Entry<String, TreeMap<Date, ModuleBuilder>> entry : modules.entrySet()) {
            for (Map.Entry<Date, ModuleBuilder> inner : entry.getValue().entrySet()) {
                all.add(inner.getValue());
            }
        }

        for (ModuleBuilder mb : all) {
            if (mb != null) {
                List<AugmentationSchemaBuilder> augments = mb.getAllAugments();
                checkAugmentMandatoryNodes(augments);
                Collections.sort(augments, Comparators.AUGMENT_BUILDER_COMP);
                for (AugmentationSchemaBuilder augment : augments) {
                    if (!(augment.isResolved())) {
                        boolean resolved = resolveAugment(augment, mb, modules);
                        if (!resolved) {
                            throw new YangParseException(augment.getModuleName(), augment.getLine(),
                                    "Error in augment parsing: failed to find augment target: " + augment);
                        }
                    }
                }
            }
        }
    }

    /**
     * Perform augmentation defined under uses statement.
     *
     * @param augment
     *            augment to resolve
     * @param module
     *            current module
     * @param modules
     *            all loaded modules
     * @return true if augment process succeed
     */
    private boolean resolveUsesAugment(final AugmentationSchemaBuilder augment, final ModuleBuilder module,
            final Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        if (augment.isResolved()) {
            return true;
        }

        UsesNodeBuilder usesNode = (UsesNodeBuilder) augment.getParent();
        DataNodeContainerBuilder parentNode = usesNode.getParent();
        Optional<SchemaNodeBuilder> potentialTargetNode;
        SchemaPath resolvedTargetPath = augment.getTargetNodeSchemaPath();
        if (parentNode instanceof ModuleBuilder && resolvedTargetPath.isAbsolute()) {
            // Uses is directly used in module body, we lookup
            // We lookup in data namespace to find correct augmentation target
            potentialTargetNode = findSchemaNodeInModule(resolvedTargetPath, (ModuleBuilder) parentNode);
        } else {
            // Uses is used in local context (be it data namespace or grouping
            // namespace,
            // since all nodes via uses are imported to localName, it is safe to
            // to proceed only with local names.
            //
            // Conflicting elements in other namespaces are still not present
            // since resolveUsesAugment occurs before augmenting from external
            // modules.
            potentialTargetNode = Optional.<SchemaNodeBuilder> fromNullable(findSchemaNode(augment.getTargetPath()
                    .getPath(), (SchemaNodeBuilder) parentNode));
        }

        if (potentialTargetNode.isPresent()) {
            SchemaNodeBuilder targetNode = potentialTargetNode.get();
            if (targetNode instanceof AugmentationTargetBuilder) {
                fillAugmentTarget(augment, targetNode);
                ((AugmentationTargetBuilder) targetNode).addAugmentation(augment);
                augment.setResolved(true);
                return true;
            } else {
                throw new YangParseException(module.getName(), augment.getLine(), String.format(
                        "Failed to resolve augment in uses. Invalid augment target: %s", potentialTargetNode));
            }
        } else {
            throw new YangParseException(module.getName(), augment.getLine(), String.format(
                    "Failed to resolve augment in uses. Invalid augment target path: %s", augment.getTargetPath()));
        }

    }

    /**
     * Find augment target module and perform augmentation.
     *
     * @param augment
     *            augment to resolve
     * @param module
     *            current module
     * @param modules
     *            all loaded modules
     * @return true if augment process succeed
     */
    private boolean resolveAugment(final AugmentationSchemaBuilder augment, final ModuleBuilder module,
            final Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        if (augment.isResolved()) {
            return true;
        }

        QName targetModuleName = augment.getTargetPath().getPathFromRoot().iterator().next();
        ModuleBuilder targetModule = BuilderUtils.getModuleByPrefix(module, targetModuleName.getPrefix());
        if (targetModule == null) {
            throw new YangParseException(module.getModuleName(), augment.getLine(), "Failed to resolve augment "
                    + augment);
        }

        return processAugmentation(augment, targetModule);
    }

    /**
     * Go through identity statements defined in current module and resolve
     * their 'base' statement.
     *
     * @param modules
     *            all loaded modules
     */
    private void resolveIdentities(final Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        for (Map.Entry<String, TreeMap<Date, ModuleBuilder>> entry : modules.entrySet()) {
            for (Map.Entry<Date, ModuleBuilder> inner : entry.getValue().entrySet()) {
                ModuleBuilder module = inner.getValue();
                final Set<IdentitySchemaNodeBuilder> identities = module.getAddedIdentities();
                for (IdentitySchemaNodeBuilder identity : identities) {
                    resolveIdentity(modules, module, identity);
                }
            }
        }
    }

    private void resolveIdentity(final Map<String, TreeMap<Date, ModuleBuilder>> modules, final ModuleBuilder module,
            final IdentitySchemaNodeBuilder identity) {
        final String baseIdentityName = identity.getBaseIdentityName();
        if (baseIdentityName != null) {
            IdentitySchemaNodeBuilder result = null;
            if (baseIdentityName.contains(":")) {
                final int line = identity.getLine();
                String[] splittedBase = baseIdentityName.split(":");
                if (splittedBase.length > 2) {
                    throw new YangParseException(module.getName(), line, "Failed to parse identityref base: "
                            + baseIdentityName);
                }
                String prefix = splittedBase[0];
                String name = splittedBase[1];
                ModuleBuilder dependentModule = BuilderUtils.getModuleByPrefix(module, prefix);
                result = BuilderUtils.findIdentity(dependentModule.getAddedIdentities(), name);
            } else {
                result = BuilderUtils.findIdentity(module.getAddedIdentities(), baseIdentityName);
            }
            identity.setBaseIdentity(result);
        }
    }

    /**
     * Find and add reference of uses target grouping.
     *
     * @param modules
     *            all loaded modules
     */
    private void resolveUsesTargetGrouping(final Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        final List<UsesNodeBuilder> allUses = new ArrayList<>();
        for (Map.Entry<String, TreeMap<Date, ModuleBuilder>> entry : modules.entrySet()) {
            for (Map.Entry<Date, ModuleBuilder> inner : entry.getValue().entrySet()) {
                allUses.addAll(inner.getValue().getAllUsesNodes());
            }
        }
        for (UsesNodeBuilder usesNode : allUses) {
            ModuleBuilder module = BuilderUtils.getParentModule(usesNode);
            final GroupingBuilder targetGroupingBuilder = GroupingUtils.getTargetGroupingFromModules(usesNode, modules,
                    module);
            if (targetGroupingBuilder == null) {
                throw new YangParseException(module.getName(), usesNode.getLine(), "Referenced grouping '"
                        + usesNode.getGroupingPathAsString() + "' not found.");
            }
            usesNode.setGrouping(targetGroupingBuilder);
        }
    }

    /**
     * Resolve uses statements defined in groupings.
     *
     * @param modules
     *            all loaded modules
     */
    private void resolveUsesForGroupings(final Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        final Set<GroupingBuilder> allGroupings = new HashSet<>();
        for (Map.Entry<String, TreeMap<Date, ModuleBuilder>> entry : modules.entrySet()) {
            for (Map.Entry<Date, ModuleBuilder> inner : entry.getValue().entrySet()) {
                ModuleBuilder module = inner.getValue();
                allGroupings.addAll(module.getAllGroupings());
            }
        }
        final List<GroupingBuilder> sorted = GroupingSort.sort(allGroupings);
        for (GroupingBuilder gb : sorted) {
            List<UsesNodeBuilder> usesNodes = new ArrayList<>(GroupingSort.getAllUsesNodes(gb));
            Collections.sort(usesNodes, new GroupingUtils.UsesComparator());
            for (UsesNodeBuilder usesNode : usesNodes) {
                resolveUses(usesNode, modules);
            }
        }
    }

    /**
     * Resolve uses statements.
     *
     * @param modules
     *            all loaded modules
     */
    private void resolveUsesForNodes(final Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        for (Map.Entry<String, TreeMap<Date, ModuleBuilder>> entry : modules.entrySet()) {
            for (Map.Entry<Date, ModuleBuilder> inner : entry.getValue().entrySet()) {
                ModuleBuilder module = inner.getValue();
                List<UsesNodeBuilder> usesNodes = module.getAllUsesNodes();
                Collections.sort(usesNodes, new GroupingUtils.UsesComparator());
                for (UsesNodeBuilder usesNode : usesNodes) {
                    resolveUses(usesNode, modules);
                }
            }
        }
    }

    /**
     * Find target grouping and copy its child nodes to current location with
     * new namespace.
     *
     * @param usesNode
     *            uses node to resolve
     * @param modules
     *            all loaded modules
     */
    private void resolveUses(final UsesNodeBuilder usesNode, final Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        if (!usesNode.isResolved()) {
            DataNodeContainerBuilder parent = usesNode.getParent();
            ModuleBuilder module = BuilderUtils.getParentModule(parent);
            GroupingBuilder target = GroupingUtils.getTargetGroupingFromModules(usesNode, modules, module);

            int index = nodeAfterUsesIndex(usesNode);
            List<DataSchemaNodeBuilder> targetNodes = target.instantiateChildNodes(parent);
            for (DataSchemaNodeBuilder targetNode : targetNodes) {
                parent.addChildNode(index++, targetNode);
            }
            parent.getTypeDefinitionBuilders().addAll(target.instantiateTypedefs(parent));
            parent.getGroupingBuilders().addAll(target.instantiateGroupings(parent));
            parent.getUnknownNodes().addAll(target.instantiateUnknownNodes(parent));
            usesNode.setResolved(true);
            for (AugmentationSchemaBuilder augment : usesNode.getAugmentations()) {
                resolveUsesAugment(augment, module, modules);
            }

            GroupingUtils.performRefine(usesNode);
        }
    }

    private int nodeAfterUsesIndex(final UsesNodeBuilder usesNode) {
        DataNodeContainerBuilder parent = usesNode.getParent();
        int usesLine = usesNode.getLine();

        List<DataSchemaNodeBuilder> childNodes = parent.getChildNodeBuilders();
        if (childNodes.isEmpty()) {
            return 0;
        }

        DataSchemaNodeBuilder nextNodeAfterUses = null;
        for (DataSchemaNodeBuilder childNode : childNodes) {
            if (!(childNode.isAddedByUses()) && !(childNode.isAugmenting())) {
                if (childNode.getLine() > usesLine) {
                    nextNodeAfterUses = childNode;
                    break;
                }
            }
        }

        // uses is declared after child nodes
        if (nextNodeAfterUses == null) {
            return childNodes.size();
        }

        return parent.getChildNodeBuilders().indexOf(nextNodeAfterUses);
    }

    /**
     * Try to find extension describing this unknown node and assign it to
     * unknown node builder.
     *
     * @param modules
     *            all loaded modules
     * @param module
     *            current module
     */
    private void resolveUnknownNodes(final Map<String, TreeMap<Date, ModuleBuilder>> modules, final ModuleBuilder module) {
        for (UnknownSchemaNodeBuilder usnb : module.getAllUnknownNodes()) {
            QName nodeType = usnb.getNodeType();
            ModuleBuilder dependentModuleBuilder = BuilderUtils.getModuleByPrefix(module, nodeType.getPrefix());
            ExtensionBuilder extBuilder = findExtBuilder(nodeType.getLocalName(),
                    dependentModuleBuilder.getAddedExtensions());
            if (extBuilder == null) {
                ExtensionDefinition extDef = findExtDef(nodeType.getLocalName(), dependentModuleBuilder.getExtensions());
                if (extDef == null) {
                    LOG.warn(
                            "Error in module {} at line {}: Failed to resolve node {}: no such extension definition found.",
                            module.getName(), usnb.getLine(), usnb);
                } else {
                    usnb.setNodeType(new QName(extDef.getQName().getNamespace(), extDef.getQName().getRevision(),
                            nodeType.getPrefix(), extDef.getQName().getLocalName()));
                    usnb.setExtensionDefinition(extDef);
                }
            } else {
                usnb.setNodeType(QName.create(extBuilder.getQName().getModule(),
                        nodeType.getPrefix(), extBuilder.getQName().getLocalName()));
                usnb.setExtensionBuilder(extBuilder);
            }
        }
    }

    private ExtensionBuilder findExtBuilder(final String name, final Collection<ExtensionBuilder> extensions) {
        for (ExtensionBuilder extension : extensions) {
            if (extension.getQName().getLocalName().equals(name)) {
                return extension;
            }
        }
        return null;
    }

    private ExtensionDefinition findExtDef(final String name, final Collection<ExtensionDefinition> extensions) {
        for (ExtensionDefinition extension : extensions) {
            if (extension.getQName().getLocalName().equals(name)) {
                return extension;
            }
        }
        return null;
    }

    /**
     * Traverse through modules and resolve their deviation statements.
     *
     * @param modules
     *            all loaded modules
     */
    private void resolveDeviations(final Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        for (Map.Entry<String, TreeMap<Date, ModuleBuilder>> entry : modules.entrySet()) {
            for (Map.Entry<Date, ModuleBuilder> inner : entry.getValue().entrySet()) {
                ModuleBuilder b = inner.getValue();
                resolveDeviation(modules, b);
            }
        }
    }

    /**
     * Traverse through module and resolve its deviation statements.
     *
     * @param modules
     *            all loaded modules
     * @param module
     *            module in which resolve deviations
     */
    private void resolveDeviation(final Map<String, TreeMap<Date, ModuleBuilder>> modules, final ModuleBuilder module) {
        for (DeviationBuilder dev : module.getDeviationBuilders()) {
            SchemaPath targetPath = dev.getTargetPath();
            Iterable<QName> path = targetPath.getPathFromRoot();
            QName q0 = path.iterator().next();
            String prefix = q0.getPrefix();
            if (prefix == null) {
                prefix = module.getPrefix();
            }

            ModuleBuilder dependentModuleBuilder = BuilderUtils.getModuleByPrefix(module, prefix);
            processDeviation(dev, dependentModuleBuilder, path, module);
        }
    }

    /**
     * Correct deviation target path in deviation builder.
     *
     * @param dev
     *            deviation
     * @param dependentModuleBuilder
     *            module containing deviation target
     * @param path
     *            current deviation target path
     * @param module
     *            current module
     */
    private void processDeviation(final DeviationBuilder dev, final ModuleBuilder dependentModuleBuilder,
            final Iterable<QName> path, final ModuleBuilder module) {
        final int line = dev.getLine();
        Builder currentParent = dependentModuleBuilder;

        for (QName q : path) {
            if (currentParent == null) {
                throw new YangParseException(module.getName(), line, FAIL_DEVIATION_TARGET);
            }
            String name = q.getLocalName();
            if (currentParent instanceof DataNodeContainerBuilder) {
                currentParent = ((DataNodeContainerBuilder) currentParent).getDataChildByName(name);
            }
        }

        if (!(currentParent instanceof SchemaNodeBuilder)) {
            throw new YangParseException(module.getName(), line, FAIL_DEVIATION_TARGET);
        }
        dev.setTargetPath(((SchemaNodeBuilder) currentParent).getPath());
    }

}
