package io.github.jmusacchio.kogito.generator.gradle.plugin.tasks;

import org.drools.codegen.common.GeneratedFile;
import org.drools.codegen.common.GeneratedFileType;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.kie.kogito.codegen.core.ApplicationGenerator;
import org.kie.kogito.codegen.core.utils.ApplicationGeneratorDiscovery;
import io.github.jmusacchio.kogito.generator.gradle.plugin.extensions.KogitoExtension;
import io.github.jmusacchio.kogito.generator.gradle.plugin.extensions.GenerateModelExtension;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.jmusacchio.kogito.generator.gradle.plugin.util.Util.projectSourceDirectory;
import static org.drools.codegen.common.GeneratedFileType.COMPILED_CLASS;
import static org.kie.efesto.common.api.constants.Constants.INDEXFILE_DIRECTORY_PROPERTY;

@CacheableTask
public class GenerateModelTask extends AbstractKieTask {
    public static final PathMatcher drlFileMatcher = FileSystems.getDefault().getPathMatcher("glob:**.drl");

    @org.gradle.api.tasks.Optional
    @Input
    private Boolean generatePartial;

    @org.gradle.api.tasks.Optional
    @Input
    private Boolean onDemand;

    @org.gradle.api.tasks.Optional
    @Input
    private Boolean keepSources;

    @org.gradle.api.tasks.Optional
    @Input
    private String schemaVersion;

    @Input
    private String compilerSourceJavaVersion;

    @Input
    private String compilerTargetJavaVersion;

    @Inject
    public GenerateModelTask(KogitoExtension extension, GenerateModelExtension modelExtension) {
        super(extension);
        this.generatePartial = modelExtension.isGeneratePartial();
        this.onDemand = modelExtension.isOnDemand();
        this.keepSources = modelExtension.isKeepSources();
        this.schemaVersion = modelExtension.getSchemaVersion();
        this.compilerSourceJavaVersion = modelExtension.getCompilerSourceJavaVersion();
        this.compilerTargetJavaVersion = modelExtension.getCompilerTargetJavaVersion();
    }

    @TaskAction
    public void execute() {
        getLogger().debug("Compiler Target Java Version:" + compilerTargetJavaVersion);
        getLogger().debug("Compiler Source Java Version:" + compilerSourceJavaVersion);
        getLogger().debug("Compiler Source Encoding:" + projectSourceEncoding);
        getLogger().debug("Targeting directory: " + getOutputDirectory());

        boolean indexFileDirectorySet = false;
        if (getOutputDirectory() == null) {
            throw new RuntimeException("${project.buildDir} is null");
        } else {
            if (System.getProperty(INDEXFILE_DIRECTORY_PROPERTY) == null) {
                System.setProperty(INDEXFILE_DIRECTORY_PROPERTY, getOutputDirectory().toString());
                indexFileDirectorySet = true;
            }

            this.addCompileSourceRoots();

            if (getOnDemand()) {
                this.getLogger().info("On-Demand Mode is On. Use gradle build :scaffold");
            } else {
                this.generateModel();
            }

            if (indexFileDirectorySet) {
                System.clearProperty(INDEXFILE_DIRECTORY_PROPERTY);
            }
        }
    }

    protected void addCompileSourceRoots() {
        projectSourceDirectory(this.getProject()).srcDirs(getGeneratedFileWriter().getScaffoldedSourcesDir());
    }

    protected void generateModel() {
        this.setSystemProperties(getProperties());
        ApplicationGenerator appGen = ApplicationGeneratorDiscovery.discover(this.discoverKogitoRuntimeContext(this.projectClassLoader()));
        Collection<GeneratedFile> generatedFiles;
        if (getGeneratePartial()) {
            generatedFiles = appGen.generateComponents();
        } else {
            generatedFiles = appGen.generate();
        }

        Map<GeneratedFileType, List<GeneratedFile>> mappedGeneratedFiles = generatedFiles.stream()
                .collect(Collectors.groupingBy(GeneratedFile::type));
        mappedGeneratedFiles.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(COMPILED_CLASS))
                .forEach(entry -> writeGeneratedFiles(entry.getValue()));

        List<GeneratedFile> generatedCompiledFiles = mappedGeneratedFiles.getOrDefault(COMPILED_CLASS,
                        Collections.emptyList())
                .stream().map(originalGeneratedFile -> new GeneratedFile(COMPILED_CLASS, convertPath(originalGeneratedFile.path().toString()), originalGeneratedFile.contents()))
                .collect(Collectors.toList());

        writeGeneratedFiles(generatedCompiledFiles);

        if (!getKeepSources()) {
            this.deleteDrlFiles();
        }
    }

    private String convertPath(String toConvert) {
        return toConvert.replace('.', File.separatorChar) + ".class";
    }

    private void deleteDrlFiles() {
        // Remove drl files
        try (final Stream<Path> drlFiles = Files.find(getOutputDirectory().toPath(), Integer.MAX_VALUE,
                (p, f) -> drlFileMatcher.matches(p))) {
            drlFiles.forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Unable to find .drl files");
        }
    }


    public Boolean getGeneratePartial() {
        return generatePartial;
    }

    public Boolean getOnDemand() {
        return onDemand;
    }

    public void setOnDemand(Boolean onDemand) {
        this.onDemand = onDemand;
    }

    public Boolean getKeepSources() {
        return keepSources;
    }


    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getCompilerSourceJavaVersion() {
        return compilerSourceJavaVersion;
    }

    public String getCompilerTargetJavaVersion() {
        return compilerTargetJavaVersion;
    }
}
