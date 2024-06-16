package io.luna.util.logging;

import io.luna.Luna;
import io.luna.util.logging.LoggingSettings.FileOutputType;
import io.luna.util.logging.LoggingSettings.OutputType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter.Result;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender.Target;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.FilterComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.plugins.Plugin;

import java.net.URI;
import java.util.EnumSet;

/**
 * The log4j2 {@link ConfigurationFactory} that will dynamically build logging configurations according to the {@link LoggingSettings}.
 *
 * @author lare96
 */
@Plugin(name = "LoggingConfigurationFactory", category = ConfigurationFactory.CONFIGURATION_FACTORY_PROPERTY)
@Order(Integer.MAX_VALUE)
public final class LoggingConfigurationFactory extends ConfigurationFactory {

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, ConfigurationSource source) {
        return getConfiguration(loggerContext, source.toString(), null);
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, String name, URI configLocation) {
        ConfigurationBuilder<BuiltConfiguration> builder = newConfigurationBuilder();
        return createConfiguration(builder);
    }

    @Override
    protected String[] getSupportedTypes() {
        return new String[] { "*" };
    }

    /**
     * Dynamically builds a {@link Configuration} instance.
     *
     * @param builder The main builder.
     * @return The configuration instance.
     */
    private Configuration createConfiguration(ConfigurationBuilder<BuiltConfiguration> builder) {
        // Set name and StatusLogger logging level.
        builder.setConfigurationName("LoggingConfigurationFactory");
        builder.setStatusLevel(Level.WARN);

        // Add custom logging levels.
        for (var type : FileOutputType.values()) {
            builder.add(builder.newCustomLevel(type.name(), 700));
        }

        // Build console appenders.
        var outputType = Luna.settings().logging().outputType();
        if (outputType == OutputType.OUT) {
            builder.add(builder.newAppender("console", "Console").addAttribute("target", Target.SYSTEM_OUT)
                    .add(getPatternLayout(builder)).add(getLevelRangeFilter(builder, "FATAL", "TRACE")));
        } else if (outputType == OutputType.ERR) {
            builder.add(builder.newAppender("console", "Console").addAttribute("target", Target.SYSTEM_ERR)
                    .add(getPatternLayout(builder)).add(getLevelRangeFilter(builder, "FATAL", "TRACE")));
        } else if (outputType == OutputType.MIXED) {
            builder.add(builder.newAppender("sout", "Console").addAttribute("target", Target.SYSTEM_OUT)
                    .add(getPatternLayout(builder)).add(getLevelRangeFilter(builder, "INFO", "TRACE")));
            builder.add(builder.newAppender("serr", "Console").addAttribute("target", Target.SYSTEM_ERR)
                    .add(getPatternLayout(builder)).add(getLevelRangeFilter(builder, "FATAL", "WARN")));
        }

        // Build file appenders.
        var newFileLogs = EnumSet.noneOf(FileOutputType.class);
        var activeFileLogs = Luna.settings().logging().activeFileLogs();
        for (var type : FileOutputType.values()) {
            if (type == FileOutputType.CONSOLE_OUT && outputType == OutputType.ERR) {
                continue;
            }
            if (type == FileOutputType.CONSOLE_ERR && outputType == OutputType.OUT) {
                continue;
            }
            if (activeFileLogs.contains(type)) {
                var fileName = type.getFileName();
                var fileAppender = builder.newAppender(fileName, "RandomAccessFile")
                        .addAttribute("fileName", "./data/logs/" + fileName + ".txt");
                if (outputType == OutputType.MIXED) {
                    if (type == FileOutputType.CONSOLE_OUT) {
                        fileAppender.add(getLevelRangeFilter(builder, "INFO", "TRACE"));
                    } else if (type == FileOutputType.CONSOLE_ERR) {
                        fileAppender.add(getLevelRangeFilter(builder, "FATAL", "WARN"));
                    }
                }
                if (type == FileOutputType.CONSOLE_OUT || type == FileOutputType.CONSOLE_ERR) {
                    fileAppender.add(getPatternLayout(builder));
                } else {
                    fileAppender.add(builder.newLayout("PatternLayout")
                            .addAttribute("pattern","[%d{EEEE | dd, MMM yyyy | h:mm:ss a}] %msg%n"));
                }
                builder.add(fileAppender);
                newFileLogs.add(type);
            }
        }

        // Create and configure root logger.
        var rootLogger = builder.newRootLogger(Luna.settings().logging().rootLevel());
        if (outputType == OutputType.OUT || outputType == OutputType.ERR) {
            rootLogger.add(builder.newAppenderRef("console"));
        } else if (outputType == OutputType.MIXED) {
            rootLogger.add(builder.newAppenderRef("sout"));
            rootLogger.add(builder.newAppenderRef("serr"));
        }
        if (newFileLogs.contains(FileOutputType.CONSOLE_OUT)) {
            rootLogger.add(builder.newAppenderRef(FileOutputType.CONSOLE_OUT.getFileName()));
            newFileLogs.remove(FileOutputType.CONSOLE_OUT);
        }
        if (newFileLogs.contains(FileOutputType.CONSOLE_ERR)) {
            rootLogger.add(builder.newAppenderRef(FileOutputType.CONSOLE_ERR.getFileName()));
            newFileLogs.remove(FileOutputType.CONSOLE_ERR);
        }
        builder.add(rootLogger);

        // Create and configure file loggers.
        for (var type : newFileLogs) {
            var logger = builder.newLogger(type.getLoggerName(), type.name());
            logger.add(builder.newAppenderRef(type.getFileName()));
            builder.add(logger);
        }
        return builder.build();
    }

    /**
     * Returns the pattern layout defined by {@link LoggingSettings}.
     *
     * @param builder The builder.
     * @return The pattern layout.
     */
    private LayoutComponentBuilder getPatternLayout(ConfigurationBuilder<BuiltConfiguration> builder) {
        var pattern = "";
        var formatType = Luna.settings().logging().formatType();
        switch (formatType) {
            case BASIC:
                pattern = "[%d{dd MMM yyyy, h:mm:ss a}] [%level] %msg%n";
                break;
            case VERBOSE:
                pattern = "%d{[dd MMM yyyy HH:mm:ss]} [%logger{36}] [%t]%n%level: %msg%n";
                break;
        }
        if (pattern.isEmpty()) {
            throw new IllegalStateException("No pattern layout for " + formatType + "!");
        }
        return builder.newLayout("PatternLayout").addAttribute("pattern", pattern);
    }

    /**
     * Returns the filter for an appender with {@code minLevel} and {@code maxLevel}.
     *
     * @param builder The builder.
     * @param minLevel The minimum logging level.
     * @param maxLevel The maximum logging level.
     * @return The filter.
     */
    private FilterComponentBuilder getLevelRangeFilter(ConfigurationBuilder<BuiltConfiguration> builder,
                                                       String minLevel, String maxLevel) {
        return builder.newFilter("LevelRangeFilter", Result.ACCEPT, Result.DENY).
                addAttribute("minLevel", minLevel).
                addAttribute("maxLevel", maxLevel);
    }
}
