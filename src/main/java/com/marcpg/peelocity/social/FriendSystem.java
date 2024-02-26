package com.marcpg.peelocity.social;

import com.marcpg.lang.Translation;
import com.marcpg.peelocity.Config;
import com.marcpg.peelocity.Peelocity;
import com.marcpg.peelocity.PlayerCache;
import com.marcpg.peelocity.storage.Storage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.kyori.adventure.text.format.NamedTextColor.*;

public class FriendSystem {
    public static final Map<UUID, UUID> FRIEND_REQUESTS = new HashMap<>();
    public static final Storage<UUID> STORAGE = Config.STORAGE_TYPE.getStorage("friendships", "uuid");

    public static @NotNull BrigadierCommand createFriendBrigadier() {
        LiteralCommandNode<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal("friend")
                .requires(source -> source.hasPermission("pee.friends"))
                .then(LiteralArgumentBuilder.<CommandSource>literal("add")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    Peelocity.SERVER.getAllPlayers().stream()
                                            .filter(player -> player != context.getSource())
                                            .map(Player::getUsername)
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    Player player = (Player) context.getSource();
                                    Optional<Player> optionalTarget = Peelocity.SERVER.getPlayer(context.getArgument("player", String.class));
                                    if (optionalTarget.isPresent()) {
                                        Player target = optionalTarget.get();
                                        if (getFriendship(player, target) != null) {
                                            player.sendMessage(Translation.component(player.getEffectiveLocale(), "friend.already_friends", target.getUsername()).color(YELLOW));
                                        } else {
                                            if ((FRIEND_REQUESTS.containsKey(player.getUniqueId()) && FRIEND_REQUESTS.containsValue(target.getUniqueId())) ||
                                                    (FRIEND_REQUESTS.containsKey(target.getUniqueId()) && FRIEND_REQUESTS.containsValue(player.getUniqueId()))) {
                                                player.sendMessage(Translation.component(player.getEffectiveLocale(), "friend.add.already_requested", target.getUsername()).color(YELLOW));
                                            } else {
                                                FRIEND_REQUESTS.put(player.getUniqueId(), target.getUniqueId());
                                                player.sendMessage(Translation.component(player.getEffectiveLocale(), "friend.add.confirm", target.getUsername()).color(GREEN));
                                                target.sendMessage(Translation.component(target.getEffectiveLocale(), "friend.add.msg.1", player.getUsername()).color(GREEN)
                                                        .append(Translation.component(target.getEffectiveLocale(), "friend.add.msg.2").color(YELLOW)
                                                                .hoverEvent(HoverEvent.showText(Translation.component(target.getEffectiveLocale(), "friend.add.msg.2.tooltip")))
                                                                .clickEvent(ClickEvent.runCommand("/friend accept " + player.getUsername())))
                                                        .append(Translation.component(target.getEffectiveLocale(), "friend.add.msg.3")));
                                            }
                                        }
                                    } else {
                                        player.sendMessage(Translation.component(player.getEffectiveLocale(), "cmd.player_not_found", context.getArgument("player", String.class)).color(NamedTextColor.RED));
                                    }
                                    return 1;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSource>literal("accept")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    Peelocity.SERVER.getAllPlayers().stream()
                                            .filter(player -> player != context.getSource())
                                            .map(Player::getUsername)
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    Player player = (Player) context.getSource();
                                    Optional<Player> target = Peelocity.SERVER.getPlayer(context.getArgument("player", String.class));
                                    if (target.isPresent()) {
                                        if (getFriendship(player, target.get()) != null) {
                                            player.sendMessage(Translation.component(player.getEffectiveLocale(), "friend.already_friends", target.get().getUsername()).color(YELLOW));
                                        } else {
                                            if (FRIEND_REQUESTS.containsKey(target.get().getUniqueId())) {
                                                STORAGE.add(Map.of("uuid", UUID.randomUUID(), "player1_uuid", target.get().getUniqueId(), "player2_uuid", player.getUniqueId()));
                                                FRIEND_REQUESTS.remove(target.get().getUniqueId());
                                            } else {
                                                player.sendMessage(Translation.component(player.getEffectiveLocale(), "friend.accept.not_requested", target.get().getUsername()).color(RED));
                                            }
                                        }
                                    } else {
                                        player.sendMessage(Translation.component(player.getEffectiveLocale(), "cmd.player_not_found", context.getArgument("player", String.class)).color(NamedTextColor.RED));
                                    }
                                    return 1;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSource>literal("remove")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    Peelocity.SERVER.getAllPlayers().stream()
                                            .filter(player -> player != context.getSource())
                                            .map(Player::getUsername)
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    Player player = (Player) context.getSource();
                                    Optional<Player> target = Peelocity.SERVER.getPlayer(context.getArgument("player", String.class));
                                    if (target.isPresent()) {
                                        UUID uuid = getFriendship(player, target.get());
                                        if (uuid != null) {
                                            STORAGE.remove(uuid);
                                            player.sendMessage(Translation.component(player.getEffectiveLocale(), "friend.remove.confirm", target.get().getUsername()).color(YELLOW));
                                            target.get().sendMessage(Translation.component(target.get().getEffectiveLocale(), "friend.remove.confirm", player.getUsername()).color(YELLOW));
                                        } else {
                                            player.sendMessage(Translation.component(player.getEffectiveLocale(), "friend.not_friends", target.get().getUsername()).color(RED));
                                        }
                                    } else {
                                        player.sendMessage(Translation.component(player.getEffectiveLocale(), "cmd.player_not_found", context.getArgument("player", String.class)).color(NamedTextColor.RED));
                                    }
                                    return 1;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                        .executes(context -> {
                            if (context.getSource() instanceof Player player) {
                                STORAGE.get(m -> m.get("player1_uuid") == player.getUniqueId() || m.get("player2_uuid") == player.getUniqueId())
                                        .forEach((uuid, o) -> player.sendMessage(Component.text("- " + PlayerCache.CACHED_USERS.get(uuid), AQUA)));
                            }
                            return 1;
                        })
                )
                .then(LiteralArgumentBuilder.<CommandSource>literal("help")
                        .executes(context -> {
                            context.getSource().sendMessage(Component.text("""
                                    §l§nHelp:§r §l/friend§r
                                    The command /friend provides all kinds of utilities for managing your friendships.
                                    
                                    §l§nArguments:§r
                                    -§l add§r: To which audience the announcement should be sent.
                                    -§l accept§r: Accept someone's friend request, if you got one.
                                    -§l remove§r: Removes a specific player from your friend list, if you're friends.
                                    -§l list§r: Lists of all your current friends.
                                    """));
                            return 1;
                        })
                )
                .build();

        return new BrigadierCommand(node);
    }

    private static UUID getFriendship(@NotNull Player player1, @NotNull Player player2) {
        return STORAGE.get(m -> (m.get("player1_uuid") == player1.getUniqueId() && m.get("player2_uuid") == player2.getUniqueId()) || (m.get("player1_uuid") == player2.getUniqueId() && m.get("player2_uuid") == player1.getUniqueId()))
                .keySet().stream().findFirst().orElse(null);
    }
}
