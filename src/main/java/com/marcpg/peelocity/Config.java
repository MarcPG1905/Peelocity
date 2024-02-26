package com.marcpg.peelocity;

import com.marcpg.data.database.sql.SQLConnection;
import com.marcpg.data.database.sql.SQLConnection.DatabaseType;
import com.marcpg.lang.Translation;
import com.marcpg.peelocity.storage.Storage;
import com.marcpg.web.Downloads;
import com.marcpg.web.discord.Webhook;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.Favicon;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Config {
    public static final List<DatabaseType> ALLOWED_DATABASES = List.of(DatabaseType.POSTGRESQL, DatabaseType.ORACLE, DatabaseType.MYSQL, DatabaseType.MS_SQL_SERVER, DatabaseType.MARIADB);

    public static boolean downloadedTranslations;

    public static YamlDocument CONFIG;
    public static List<String> VALID_ROUTES;

    public static boolean MODERATOR_WEBHOOK_ENABLED = false;
    public static Webhook MODERATOR_WEBHOOK;
    public static Map<String, Integer> GAMEMODES;
    public static boolean GLOBAL_CHAT;

    public static Storage.StorageType STORAGE_TYPE;

    public static SQLConnection.DatabaseType DATABASE_TYPE;

    public static boolean WHITELIST_ENABLED;

    public static boolean SL_ENABLED;
    public static boolean SL_MOTD_ENABLED;
    public static Component[] SL_MOTDS;
    public static boolean SL_FAVICON_ENABLED;
    public static Favicon[] SL_FAVICONS;
    public static int SL_SHOW_MAX_PLAYERS;
    public static int SL_SHOW_CURRENT_PLAYERS;

    public static Section CHATUTILITY_BOOLEANS;

    public static void createDataDirectory() throws IOException {
        if (Peelocity.DATA_DIRECTORY.resolve("lang/").toFile().mkdirs()) Peelocity.LOG.info("Created plugins/peelocity/lang/, as it didn't exist before!");
        if (Peelocity.DATA_DIRECTORY.resolve("message-history/").toFile().mkdirs()) Peelocity.LOG.info("Created plugins/peelocity/message-history/, as it didn't exist before!");
        if (Peelocity.DATA_DIRECTORY.resolve("playercache").toFile().createNewFile()) Peelocity.LOG.info("Created plugins/peelocity/playercache/, as it didn't exist before!");
    }

    public static void load(InputStream peeYml) {
        try {
            CONFIG = YamlDocument.create(
                    new File(Peelocity.DATA_DIRECTORY.toFile(), "pee.yml"),
                    peeYml,
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS).build()
            );
            VALID_ROUTES = CONFIG.getRoutesAsStrings(true).stream().filter(r -> !CONFIG.isSection(r)).toList();

            try {
                MODERATOR_WEBHOOK = new Webhook(new URI(CONFIG.getString("moderator-webhook")).toURL());
                MODERATOR_WEBHOOK_ENABLED = true;
            } catch (URISyntaxException e) {
                Peelocity.LOG.warn("The `moderator-webhook` URL in the configuration is either invalid or not set! The moderator webhook will be disabled.");
                Peelocity.LOG.warn("In case you want the moderator webhook to be disabled, you can safely ignore this warning.");
            }

            GAMEMODES = CONFIG.getSection("gamemodes").getStringRouteMappedValues(false).entrySet().stream()
                    .filter(entry -> entry.getValue() instanceof Integer)
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> (Integer) entry.getValue()));

            GLOBAL_CHAT = CONFIG.getBoolean("global-chat");

            try {
                STORAGE_TYPE = Storage.StorageType.valueOf(CONFIG.getString("storage-method").toUpperCase());
            } catch (IllegalArgumentException e) {
                Peelocity.importantError("The specified database type is invalid! Using the default (yaml) now.");
            }

            if (STORAGE_TYPE == Storage.StorageType.DATABASE) {
                try {
                    DATABASE_TYPE = DatabaseType.valueOf(CONFIG.getString("database.type").toUpperCase());
                } catch (IllegalArgumentException e) {
                    Peelocity.importantError("The specified storage type is invalid! Using the default (postgresql) now.");
                    DATABASE_TYPE = DatabaseType.POSTGRESQL;
                }
            }

            WHITELIST_ENABLED = CONFIG.getBoolean("whitelist-enabled");

            SL_ENABLED = CONFIG.getBoolean("server-list.enabled");
            if (SL_ENABLED) {
                SL_MOTD_ENABLED = CONFIG.getBoolean("server-list.custom-motd");
                if (SL_MOTD_ENABLED) SL_MOTDS = CONFIG.getStringList("server-list.custom-motd-messages").stream().map(s -> MiniMessage.miniMessage().deserialize(s)).toArray(Component[]::new);
                SL_FAVICON_ENABLED = CONFIG.getBoolean("server-list.custom-favicon");
                if (SL_FAVICON_ENABLED) SL_FAVICONS = CONFIG.getStringList("server-list.custom-favicon-urls").stream()
                        .map(s -> {
                            try {
                                return Favicon.create(ImageIO.read(new URI(s).toURL()));
                            } catch (IOException | URISyntaxException e) {
                                throw new RuntimeException(e);
                            } catch (IllegalArgumentException e) {
                                Peelocity.importantError("One or more of the provided favicons is not 64x64 pixels!");
                                return null;
                            }
                        })
                        .toArray(Favicon[]::new);
                SL_SHOW_MAX_PLAYERS = CONFIG.getInt("server-list.show-max-players");
                SL_SHOW_CURRENT_PLAYERS = CONFIG.getInt("server-list.show-current-players");
            }

            CHATUTILITY_BOOLEANS = CONFIG.getSection("chatutility");

            if (isDatabaseInvalid(Objects.requireNonNull(CONFIG.getDefaults()))) {
                Peelocity.importantError("Please configure the database first, before running Peelocity!");
            } else if (STORAGE_TYPE == Storage.StorageType.DATABASE && !ALLOWED_DATABASES.contains(DATABASE_TYPE)) {
                Peelocity.importantError("The specified database type is invalid!");
            } else if (CONFIG.getBoolean("enable-translations")) {
                new Thread(new TranslationDownloadTask()).start();
            }
        } catch (IOException e) {
            Peelocity.importantError("Couldn't load the pee.yml configuration file!");
        } catch (NullPointerException e) {
            Peelocity.importantError("Please fully configure Peelocity in the pee.yml file first, before running it! : " + e.getMessage());
        }
    }

    public static boolean isDatabaseInvalid(@NotNull YamlDocument defaults) {
        return STORAGE_TYPE == Storage.StorageType.DATABASE &&
                CONFIG.getString("database.user").equals(defaults.getString("database.user")) ||
                CONFIG.getString("database.passwd").equals(defaults.getString("database.passwd"));
    }

    public static @NotNull BrigadierCommand createConfigBrigadier() {
        LiteralCommandNode<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal("config")
                .requires(source -> source.hasPermission("pee.admin"))
                .then(LiteralArgumentBuilder.<CommandSource>literal("get")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("entry", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    VALID_ROUTES.forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSource source = context.getSource();
                                    Locale locale = source instanceof Player player ? player.getEffectiveLocale() : new Locale("en", "US");
                                    String route = context.getArgument("entry", String.class);
                                    if (CONFIG.isList(route)) {
                                        source.sendMessage(Translation.component(locale, "cmd.config.get.list", route).color(NamedTextColor.YELLOW));
                                        CONFIG.getList(route).forEach(o -> source.sendMessage(Component.text("- " + o.toString())));
                                    } else if (CONFIG.contains(route)) {
                                        source.sendMessage(Translation.component(locale, "cmd.config.get.object", route, CONFIG.getString(route)).color(NamedTextColor.YELLOW));
                                    } else {
                                        source.sendMessage(Translation.component(locale, "cmd.config.key_not_existing", route).color(NamedTextColor.RED));
                                    }
                                    return 1;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSource>literal("set")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("entry", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    VALID_ROUTES.stream()
                                            .filter(r -> !CONFIG.isList(r))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("value", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> {
                                            if (CONFIG.isBoolean(context.getArgument("entry", String.class))) {
                                                builder.suggest("true");
                                                builder.suggest("false");
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            CommandSource source = context.getSource();
                                            Locale locale = source instanceof Player player ? player.getEffectiveLocale() : new Locale("en", "US");
                                            String route = context.getArgument("entry", String.class);
                                            if (CONFIG.contains(route)) {
                                                String stringValue = context.getArgument("value", String.class);

                                                if (CONFIG.isSection(route) || CONFIG.isList(route)) {
                                                    source.sendMessage(Translation.component(locale, "cmd.config.set.section_list").color(NamedTextColor.RED));
                                                    return 1;
                                                } else if (CONFIG.isBoolean(route))
                                                    CONFIG.set(route, Boolean.parseBoolean(stringValue));
                                                else if (CONFIG.isInt(route))
                                                    CONFIG.set(route, Integer.parseInt(stringValue));
                                                else
                                                    CONFIG.set(route, stringValue);

                                                try {
                                                    CONFIG.save();
                                                } catch (IOException e) {
                                                    source.sendMessage(Translation.component(locale, "cmd.config.error").color(NamedTextColor.RED));
                                                    return 1;
                                                }

                                                source.sendMessage(Translation.component(locale, "cmd.config.set.confirm", route, stringValue).color(NamedTextColor.YELLOW));
                                                source.sendMessage(Translation.component(locale, "cmd.config.reload_to_apply").color(NamedTextColor.GRAY));
                                            } else {
                                                source.sendMessage(Translation.component(locale, "cmd.config.key_not_existing", route).color(NamedTextColor.RED));
                                            }
                                            return 1;
                                        })
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSource>literal("add")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("entry", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    VALID_ROUTES.stream()
                                            .filter(r -> CONFIG.isList(r))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("value", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            CommandSource source = context.getSource();
                                            Locale locale = source instanceof Player player ? player.getEffectiveLocale() : new Locale("en", "US");
                                            String route = context.getArgument("entry", String.class);
                                            if (CONFIG.contains(route)) {
                                                String stringValue = context.getArgument("value", String.class);

                                                List<String> list = CONFIG.getStringList(route);
                                                list.add(stringValue);
                                                CONFIG.set(route, list);

                                                try {
                                                    CONFIG.save();
                                                } catch (IOException e) {
                                                    source.sendMessage(Translation.component(locale, "cmd.config.error").color(NamedTextColor.RED));
                                                    return 1;
                                                }

                                                source.sendMessage(Translation.component(locale, "cmd.config.add.confirm", route, stringValue).color(NamedTextColor.YELLOW));
                                                source.sendMessage(Translation.component(locale, "cmd.config.reload_to_apply").color(NamedTextColor.GRAY));
                                            } else {
                                                source.sendMessage(Translation.component(locale, "cmd.config.key_not_existing", route).color(NamedTextColor.RED));
                                            }
                                            return 1;
                                        })
                                )
                        )
                )
                .build();
        return new BrigadierCommand(node);
    }

    public static @NotNull BrigadierCommand createPeeloadBrigadier(Peelocity peelocity) {
        LiteralCommandNode<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal("peeload")
                .requires(source -> source.hasPermission("pee.admin"))
                .executes(context -> {
                    Locale locale = context.getSource() instanceof Player player ? player.getEffectiveLocale() : new Locale("en", "US");
                    try {
                        Peelocity.loadLogic(peelocity.getClass().getResourceAsStream("/pee.yml"));
                    } catch (IOException e) {
                        context.getSource().sendMessage(Translation.component(locale, "cmd.reload.error").color(NamedTextColor.RED));
                    } finally {
                        context.getSource().sendMessage(Translation.component(locale, "cmd.reload.confirm").color(NamedTextColor.YELLOW));
                    }
                    return 1;
                })
                .build();
        return new BrigadierCommand(node);
    }

    private static class TranslationDownloadTask implements Runnable {
        private final Path langFolder = Peelocity.DATA_DIRECTORY.resolve("lang");
        private static final int MAX_RETRIES = 3;

        @Override
        public void run() {
            int attempt = 1;
            while (attempt <= MAX_RETRIES) {
                try {
                    Downloads.simpleDownload(new URI("https://marcpg.com/peelocity/translations/available_locales").toURL(), langFolder.resolve("available_locales.temp").toFile());
                    for (String locale : Files.readAllLines(langFolder.resolve("available_locales.temp"))) {
                        Downloads.simpleDownload(new URI("https://marcpg.com/peelocity/translations/" + locale).toURL(), langFolder.resolve(locale).toFile());
                    }
                    Files.deleteIfExists(langFolder.resolve("available_locales.temp"));
                    return;
                } catch (Exception e) {
                    if (attempt == MAX_RETRIES) {
                        Peelocity.LOG.warn("Translation download failed. The maximum amount of retries (" + MAX_RETRIES + ") has been reached!");
                    } else {
                        Peelocity.LOG.warn("Translation download failed on attempt " + attempt + ", retrying in 3 seconds...");
                        try {
                            this.wait(3000); // Wait for 3s before retrying
                        } catch (InterruptedException ignored) {}
                    }
                    attempt++;
                }
            }
            Peelocity.LOG.info("Downloaded and loaded all recent translations!");

            try {
                Translation.loadProperties(langFolder.toFile());
            } catch (IOException e) {
                Peelocity.LOG.warn("The downloaded translations are corrupted or missing, so the translations couldn't be loaded!");
            }
        }
    }
}
