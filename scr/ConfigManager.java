import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigManager {
    private static ConfigManager instance;
    private Properties properties;
    private final String CONFIG_FILE = "config.properties";

    private ConfigManager() {
        properties = new Properties();
        loadProperties();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    private void loadProperties() {
        try {
            // First try to load from the current directory
            Path configPath = Paths.get(CONFIG_FILE);
            if (configPath.toFile().exists()) {
                try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
                    properties.load(fis);
                    return;
                }
            }

            // If not found, try to load from classpath
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                if (is != null) {
                    properties.load(is);
                    return;
                }
            }

            throw new IOException("Configuration file not found in current directory or classpath");
        } catch (IOException e) {
            System.err.println("Error loading configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getDatabaseUrl() {
        return String.format("jdbc:sqlserver://%s:%s;DatabaseName=%s;encrypt=false;trustServerCertificate=true",
            getProperty("db.server"),
            getProperty("db.port"),
            getProperty("db.name"));
    }

    public String getDatabaseUser() {
        return getProperty("db.user");
    }

    public String getDatabasePassword() {
        return getProperty("db.password");
    }

    public String getAppTitle() {
        return getProperty("app.title", "Birth Statistics Manager");
    }

    public int getWindowWidth() {
        return Integer.parseInt(getProperty("app.window.width", "800"));
    }

    public int getWindowHeight() {
        return Integer.parseInt(getProperty("app.window.height", "600"));
    }

    public int getCsvBatchSize() {
        return Integer.parseInt(getProperty("csv.batch.size", "1000"));
    }

    public String getErrorLogPath() {
        return getProperty("csv.error.log", "error.log");
    }

    private String getProperty(String key) {
        return getProperty(key, null);
    }

    private String getProperty(String key, String defaultValue) {
        String value = properties.getProperty(key, defaultValue);
        if (value == null) {
            throw new IllegalStateException("Required configuration property not found: " + key);
        }
        return value;
    }
}
