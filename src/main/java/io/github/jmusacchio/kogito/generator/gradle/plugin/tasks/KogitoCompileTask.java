package io.github.jmusacchio.kogito.generator.gradle.plugin.tasks;

import io.github.jmusacchio.kogito.generator.gradle.plugin.extensions.KogitoExtension;
import io.github.jmusacchio.kogito.generator.gradle.plugin.util.Util;
import org.drools.codegen.common.GeneratedFileWriter;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.tasks.compile.incremental.IncrementalCompilerFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.util.internal.PatternSetFactory;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;

import static io.github.jmusacchio.kogito.generator.gradle.plugin.util.Util.projectSourceDirectory;

@CacheableTask
public class KogitoCompileTask extends JavaCompile {

    protected static final GeneratedFileWriter.Builder GENERATED_FILE_WRITER_BUILDER = GeneratedFileWriter.builder("kogito", "kogito.codegen.resources.directory", "kogito.codegen.sources.directory");

    @Internal
    private File baseDir;

    @Inject
    public KogitoCompileTask(KogitoExtension extension, AbstractCompile compile) {
        super();
        this.baseDir = extension.getBaseDir();
        source(
                getGeneratedFileWriter().getResourcePath(), getGeneratedFileWriter().getScaffoldedSourcesDir(), projectSourceDirectory(this.getProject())
                        .getSrcDirs()
                        .stream()
                        .findFirst()
                        .orElse(getGeneratedFileWriter().getScaffoldedSourcesDir().toFile()));
        setClasspath(compile.getClasspath());
        //setDestinationDir(compile.getDestinationDirectory());
    }

    @Override
    public FileCollection getClasspath() {
        return getServices().get(FileCollectionFactory.class).fixed(Util.classpathFiles(getProject()));
    }

    @Override
    protected ObjectFactory getObjectFactory() {
        return null;
    }

    @Override
    protected PropertyFactory getPropertyFactory() {
        return null;
    }

    @Override
    protected JavaToolchainService getJavaToolchainService() {
        return null;
    }

    @Override
    protected ProviderFactory getProviderFactory() {
        return null;
    }

    @Override
    protected IncrementalCompilerFactory getIncrementalCompilerFactory() {
        return null;
    }

    @Override
    protected JavaModuleDetector getJavaModuleDetector() {
        return null;
    }

    @Override
    protected Deleter getDeleter() {
        return null;
    }

    @Override
    protected ProjectLayout getProjectLayout() {
        return null;
    }

    @Internal
    protected GeneratedFileWriter getGeneratedFileWriter() {
        return GENERATED_FILE_WRITER_BUILDER.build(Path.of(baseDir.getAbsolutePath()));
    }

    public File getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(File baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    protected PatternSetFactory getPatternSetFactory() {
        return null;
    }
}