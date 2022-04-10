
package at.jku.anttracks.gui.dialog.preference;

import at.jku.anttracks.gui.model.ClientInfo;
import at.jku.anttracks.gui.utils.Consts;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.io.MetaDataWriterConfig;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Settings {

    private static final Logger LOGGER = Logger.getLogger(Settings.class.getSimpleName());

    private static final Settings INSTANCE = new Settings();

    public static Settings getInstance() {
        return INSTANCE;
    }

    private Property<?>[] settings;

    public void setup() {
        settings = availableSettings();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(Consts.SETTINGS_FILE)))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                int splitter = line.indexOf('=');
                String key = line.substring(0, splitter);
                String value = line.substring(splitter + 1);
                trySet(key, value);
            }
        } catch (FileNotFoundException e) {
            // nothing to do
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "error occured", e);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::dump, "Settings Dumper"));
    }

    public static final class Property<T> {
        public final String name;
        public final String description;
        public final boolean configurable;
        private final Supplier<T> getter;
        private final Consumer<T> setter;

        private Property(String name, String description, boolean configurable, Supplier<T> getter, Consumer<T> setter) {
            this.name = name;
            this.description = description;
            this.configurable = configurable;
            this.getter = getter;
            this.setter = setter;
        }

        @SuppressWarnings("unchecked")
        public Class<T> getValueClass() {
            return (Class<T>) get().getClass();
        }

        public T get() {
            return getter.get();
        }

        @SuppressWarnings("unchecked")
        public void set(Object value) throws Exception {
            setter.accept((T) value);
        }
    }

    private Property<?>[] availableSettings() {
        return new Property<?>[]{
                new Property<>("meta_data_dump_count",
                               "# of heap dumps to be created during parsing for later fast acces",
                               true,
                               () -> MetaDataWriterConfig.DEFAULT_DUMP_COUNT,
                               value -> MetaDataWriterConfig.DEFAULT_DUMP_COUNT = value),
                new Property<>("meta_data_major_gcs_only",
                               "Whether to create dumps only at major GCs",
                               true,
                               () -> MetaDataWriterConfig.DEFAULT_AT_MAJOR_GCS_ONLY,
                               value -> MetaDataWriterConfig.DEFAULT_AT_MAJOR_GCS_ONLY = value),
                new Property<>("hprofDirectory",
                               "Last HPROF accesses directory",
                               false,
                               () -> {
                                   if (!new File(ClientInfo.hprofDirectory).exists()) {
                                       ClientInfo.hprofDirectory = System.getProperty("user.home");
                                   }
                                   return ClientInfo.hprofDirectory;
                               },
                               value -> ClientInfo.hprofDirectory = new File(value).exists() ? value : System.getProperty("user.home")),
                new Property<>("traceDirectory",
                               "Last trace file accesses directory",
                               false,
                               () -> {
                                   if (!new File(ClientInfo.traceDirectory).exists()) {
                                       ClientInfo.traceDirectory = System.getProperty("user.home");
                                   }
                                   return ClientInfo.traceDirectory;
                               },
                               value -> ClientInfo.traceDirectory = new File(value).exists() ? value : System.getProperty("user.home")),
                new Property<>("featureDirectory",
                               "Last feature file accesses directory",
                               false,
                               () -> {
                                   if (!new File(ClientInfo.featureDirectory).exists()) {
                                       ClientInfo.featureDirectory = System.getProperty("user.home");
                                   }
                                   return ClientInfo.featureDirectory;
                               },
                               value -> ClientInfo.featureDirectory = new File(value).exists() ? value : System.getProperty("user.home")),
                new Property<>("opening_x_location",
                               "Last X location",
                               false,
                               () -> ClientInfo.stage.getX(),
                               value -> ClientInfo.stage.setX(value)),
                new Property<>("json_store_location",
                               "Path for storing JSON exports",
                               true,
                               () -> ClientInfo.jsonExportDirectory,
                               value -> ClientInfo.jsonExportDirectory = value)
        };
    }

    public int getSize() {
        return settings.length;
    }

    public Property<?>[] getAll() {
        return settings.clone();
    }

    public void set(String key, String value) {
        boolean success = trySet(key, value);
        if (success) {
            dump();
        }
    }

    private synchronized void dump() {
        new File(Consts.SETTINGS_FILE).getParentFile().mkdirs();
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Consts.SETTINGS_FILE)))) {
            for (Property<?> property : settings) {
                out.write(property.name + "=" + property.get().toString() + "\n");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "error occured writing settings file", e);
        }
    }

    private boolean trySet(String key, String value) {
        Property<?> property = get(key);
        if (property != null) {
            try {
                Class<?> clazz = property.getValueClass();
                Constructor<?> ctor = clazz.getConstructor(String.class);
                Object obj = ctor.newInstance(value);
                property.set(obj);
                return true;
            } catch (NoSuchMethodException e) {
                LOGGER.log(Level.WARNING, "no suitable constructor", e);
            } catch (SecurityException e) {
                LOGGER.log(Level.WARNING, "could not access constructor", e);
            } catch (InstantiationException e) {
                LOGGER.log(Level.WARNING, "could not create object", e);
            } catch (IllegalAccessException e) {
                LOGGER.log(Level.WARNING, "internal error occured", e);
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.WARNING, "internal error occured", e);
            } catch (InvocationTargetException e) {
                LOGGER.log(Level.WARNING, "internal error occured", e);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "internal error occured setting property '" + key + "'", e);
            }
        } else {
            LOGGER.log(Level.WARNING, "could not find (and set) property '" + key + "'");
        }
        return false;
    }

    private Property<?> get(String key) {
        for (Property<?> property : settings) {
            if (property.name.equals(key)) {
                return property;
            }
        }
        return null;
    }

}
