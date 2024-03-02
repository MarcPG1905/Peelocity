package com.marcpg.peelocity.features;

import com.marcpg.lang.Translation;
import com.marcpg.peelocity.Configuration;
import com.marcpg.peelocity.Peelocity;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatUtilities {
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    public static boolean signedVelocityInstalled;

    @Subscribe(order = PostOrder.FIRST)
    public void onPlayerChat(@NotNull PlayerChatEvent event) {
        if (!event.getResult().isAllowed()) return;

        Player player = event.getPlayer();
        String content = event.getMessage();

        if (canUse(player, "mentions")) {
            Matcher matcher = MENTION_PATTERN.matcher(content);
            while (matcher.find()) {
                String mentioned = matcher.group(1);

                Optional<ServerConnection> connection = player.getCurrentServer();
                if (mentioned.equals("everyone") && canUse(player, "mentions.everyone")) {
                    Peelocity.SERVER.getAllPlayers().forEach(p -> {
                        if (Configuration.globalChat || connection.equals(p.getCurrentServer())) {
                            Locale l = p.getEffectiveLocale();
                            p.showTitle(Title.title(Translation.component(l, "chat.mentions.title").color(NamedTextColor.BLUE), Translation.component(l, "chat.mentions.subtitle.everyone", player.getUsername()).color(NamedTextColor.DARK_GREEN)));
                            p.playSound(Sound.sound(Key.key("minecraft:entity.player.levelup"), Sound.Source.PLAYER, 1.0f, 1.0f));
                        }
                    });
                } else {
                    Peelocity.SERVER.getPlayer(mentioned).ifPresentOrElse(p -> {
                        if (Configuration.globalChat || connection.equals(p.getCurrentServer())) {
                            Locale l = p.getEffectiveLocale();
                            p.showTitle(Title.title(Translation.component(l, "chat.mentions.title").color(NamedTextColor.BLUE), Translation.component(l, "chat.mentions.subtitle", player.getUsername()).color(NamedTextColor.DARK_GREEN)));
                            p.playSound(Sound.sound(Key.key("minecraft:entity.player.levelup"), Sound.Source.PLAYER, 1.0f, 1.0f));
                        } else
                            player.sendMessage(Translation.component(player.getEffectiveLocale(), "cmd.player_not_found", mentioned).color(NamedTextColor.GRAY));
                    }, () -> player.sendMessage(Translation.component(player.getEffectiveLocale(), "cmd.player_not_found", mentioned).color(NamedTextColor.GRAY)));
                }
            }
        }

        if (signedVelocityInstalled) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
        } else {
            Peelocity.LOG.warn("SignedVelocity isn't installed, which means that colors and global chat won't work! Please install SignedVelocity or UnSignedVelocity on Velocity and the backend servers.");
            return;
        }

        Component finalMessage = canUse(player, "colors") ? colorize(content) : Component.text(content);
        if (Configuration.globalChat) {
            Peelocity.SERVER.sendMessage(Component.text("<" + player.getUsername() + "> ").append(finalMessage));
        } else {
            player.getCurrentServer().ifPresent(connection -> connection.getServer().sendMessage(Component.text("<" + player.getUsername() + "> ").append(finalMessage)));
        }
    }


    private static boolean canUse(Player player, String chatUtil) {
        return Configuration.chatUtilities.getBoolean(chatUtil + ".enabled") && !Configuration.chatUtilities.getBoolean(chatUtil + ".permission") || player.hasPermission("pee.chat." + chatUtil);
    }

    private static final TagResolver COLORS = TagResolver.resolver(StandardTags.reset(), StandardTags.color());
    private static final TagResolver STYLES = TagResolver.resolver(StandardTags.reset(), StandardTags.color(), StandardTags.decorations());

    private static @NotNull Component colorize(String original) {
        return MiniMessage.builder().tags(Configuration.chatUtilities.getBoolean("colors.styles") ? STYLES : COLORS).build().deserialize(original);
    }
}
