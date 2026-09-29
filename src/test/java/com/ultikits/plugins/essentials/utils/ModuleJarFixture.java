package com.ultikits.plugins.essentials.utils;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Constructs a module's main class the way a server does, from a module jar, without packaging
 * the module first (UltiKits/UltiEssentials#21).
 * <p>
 * {@code UltiToolsPlugin}'s constructor reads {@code plugin.yml} from the jar its class was loaded
 * from, and in a Maven test run every class comes from {@code target/classes}, a directory, so a
 * plain {@code new UltiEssentials()} finds no {@code plugin.yml} and is refused. This fixture writes
 * a small jar holding {@code plugin.yml}, the language files and the main class itself, and loads
 * only the main class from it; every other class, the module's services included, still comes from
 * the test class path, so a test can hand the plugin the same service types it mocks.
 */
public final class ModuleJarFixture {

    private ModuleJarFixture() {
    }

    /**
     * Builds the jar under {@code folder} and constructs {@code mainClass} from it.
     *
     * @param mainClass the module's main class name
     * @param folder    a folder the jar may be written to
     * @return the constructed module
     * @throws Exception if the jar cannot be written or the class cannot be constructed
     */
    public static UltiToolsPlugin construct(String mainClass, Path folder) throws Exception {
        Path classes = Paths.get("target", "classes");
        File jar = folder.resolve("module.jar").toFile();
        String classPath = mainClass.replace('.', '/');
        String classFolder = classPath.substring(0, classPath.lastIndexOf('/') + 1);
        String simpleName = classPath.substring(classPath.lastIndexOf('/') + 1);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath()))) {
            add(out, classes, "plugin.yml");
            try (Stream<Path> lang = Files.list(classes.resolve("lang"))) {
                for (Path file : (Iterable<Path>) lang::iterator) {
                    add(out, classes, "lang/" + file.getFileName());
                }
            }
            try (Stream<Path> own = Files.list(classes.resolve(classFolder))) {
                for (Path file : (Iterable<Path>) own::iterator) {
                    String name = file.getFileName().toString();
                    if (name.equals(simpleName + ".class") || name.startsWith(simpleName + "$")) {
                        add(out, classes, classFolder + name);
                    }
                }
            }
        }
        ClassLoader parent = ModuleJarFixture.class.getClassLoader();
        URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(mainClass) || name.startsWith(mainClass + "$")) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded == null) {
                            loaded = findClass(name);
                        }
                        if (resolve) {
                            resolveClass(loaded);
                        }
                        return loaded;
                    }
                }
                return super.loadClass(name, resolve);
            }
        };
        return (UltiToolsPlugin) loader.loadClass(mainClass).getDeclaredConstructor().newInstance();
    }

    private static void add(JarOutputStream out, Path root, String entry) throws IOException {
        out.putNextEntry(new JarEntry(entry));
        try (OutputStream sink = new NonClosing(out)) {
            Files.copy(root.resolve(entry), sink);
        }
        out.closeEntry();
    }

    /** Lets {@link Files#copy(Path, OutputStream)} write into the jar without closing it. */
    private static final class NonClosing extends java.io.FilterOutputStream {
        NonClosing(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
