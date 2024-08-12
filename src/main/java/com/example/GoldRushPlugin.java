package com.example;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.FireworkEffect;

import java.util.*;

public class GoldRushPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private Map<Player, Integer> playerScores = new HashMap<>();
    private boolean gameRunning = false;
    private static final int DEFAULT_GAME_DURATION = 60; // デフォルトのゲームの長さ（秒）
    private Scoreboard scoreboard;
    private Objective objective;
    private Set<Player> frozenPlayers = new HashSet<>(); // 動きを制限されているプレイヤー
    private Location startLocation; // ゲーム開始地点

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("gold-rush").setExecutor(this);
        setupScoreboard();
    }

    @Override
    public void onDisable() {
        playerScores.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("gold-rush")) {
            if (gameRunning) {
                sender.sendMessage("ゲームは既に進行中です！");
                return true;
            }
            int duration = DEFAULT_GAME_DURATION;
            if (args.length > 0) {
                try {
                    duration = Integer.parseInt(args[0]);
                    if (duration <= 0) {
                        sender.sendMessage("秒数は正の整数である必要があります。");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("秒数は整数で指定してください。");
                    return true;
                }
            }
            Player player = (Player) sender;
            teleportAllToPlayer(player);
            startCountdown(player, duration);
            return true;
        }
        return false;
    }

    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();
        objective = scoreboard.registerNewObjective("goldRush", "dummy", "鉱石スコア");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    private void teleportAllToPlayer(Player player) {
        startLocation = player.getLocation(); // スタート地点を保存
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.teleport(startLocation);
            frozenPlayers.add(onlinePlayer); // 動きを制限
        }
    }

    private void startCountdown(Player player, int duration) {
        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (countdown <= 0) {
                    playSoundToAllPlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
                    startGame(player, duration);
                    cancel();
                } else {
                    showTitleToAllPlayers("開始まで: " + countdown, "", 10, 20, 10);
                    playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_HAT);
                    countdown--;
                }
            }
        }.runTaskTimer(this, 0, 20);
    }

    private void startGame(Player player, int duration) {
        gameRunning = true;
        playerScores.clear();
        frozenPlayers.clear(); // 動きを制限解除

        // 全プレイヤーのインベントリをクリアし、アイテムを付与
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.getInventory().clear();  // インベントリをクリア

            // ダイヤモンドピッケルを作成し、効率エンチャントを付与
            ItemStack diamondPickaxe = new ItemStack(Material.DIAMOND_PICKAXE, 1);
            ItemMeta pickaxeMeta = diamondPickaxe.getItemMeta();
            pickaxeMeta.addEnchant(Enchantment.EFFICIENCY, 5, true); // 効率V
            diamondPickaxe.setItemMeta(pickaxeMeta);

            onlinePlayer.getInventory().addItem(diamondPickaxe);
            onlinePlayer.getInventory().addItem(new ItemStack(Material.WATER_BUCKET, 3));
            onlinePlayer.getInventory().addItem(new ItemStack(Material.TORCH, 64 * 5));
            onlinePlayer.getInventory().addItem(new ItemStack(Material.STONE, 64 * 5));
            
            onlinePlayer.setGameMode(GameMode.SURVIVAL);  // ゲームモードをサバイバルに
            onlinePlayer.setScoreboard(scoreboard);
            objective.getScore(onlinePlayer.getName()).setScore(0); // 初期スコアを設定
            
            // 空腹度を最大にし、減らないように設定
            onlinePlayer.setFoodLevel(20);
            onlinePlayer.setSaturation(20f);
            onlinePlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0); // 最大体力を設定
        }

        showTitleToAllPlayers("ゴールドラッシュ開始！", duration + "秒間で鉱石を掘ろう！", 10, 70, 20);

        new BukkitRunnable() {
            int timeLeft = duration;

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    endGame();
                    cancel();
                } else {
                    if (timeLeft == 60) {
                        // スコアボードを隠して全画面メッセージを表示
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            onlinePlayer.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                        }
                        showTitleToAllPlayers("残り60秒です！", "スコアボードを隠します！\nみんながんばれ！", 10, 70, 20);
                        playSoundToAllPlayers(Sound.ENTITY_ENDER_DRAGON_GROWL);
                    }
                    if (timeLeft == 30 || (timeLeft <= 10 && timeLeft > 0)) {
                        showTitleToAllPlayers("残り " + timeLeft + " 秒", "", 10, 20, 10);
                        playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_PLING);
                    }
                    timeLeft--;
                }
            }
        }.runTaskTimer(this, 0, 20);
    }

    private void endGame() {
        gameRunning = false;

        // スコアを降順でソート
        List<Map.Entry<Player, Integer>> sortedScores = new ArrayList<>(playerScores.entrySet());
        sortedScores.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        // チャットをクリア
        for (int i = 0; i < 100; i++) {
            Bukkit.broadcastMessage(""); // チャットをクリアするために空行を送信
        }

        // 結果発表のメッセージ
        Bukkit.broadcastMessage(ChatColor.GOLD + "ゴールドラッシュ！結果発表！");

        // 結果を全員に表示
        Player winner = null;
        int highestScore = -1;
        boolean tie = false;
        List<Player> tiedPlayers = new ArrayList<>();

        int rank = 1;
        for (Map.Entry<Player, Integer> entry : sortedScores) {
            Player player = entry.getKey();
            int score = entry.getValue();

            // 順位を表示
            Bukkit.broadcastMessage(rank + "位: " + player.getName() + " - スコア " + score + " 点");

            if (score > highestScore) {
                winner = player;
                highestScore = score;
                tie = false;
                tiedPlayers.clear();
                tiedPlayers.add(player);
            } else if (score == highestScore) {
                tie = true;
                tiedPlayers.add(player);
            }

            rank++;
        }

        if (tie) {
            StringBuilder tieMessage = new StringBuilder(ChatColor.GREEN + "引き分けです！: ");
            for (Player tiedPlayer : tiedPlayers) {
                tieMessage.append(tiedPlayer.getName()).append(" ");
            }
            showTitleToAllPlayers("ゲーム終了！", tieMessage.toString(), 10, 70, 20);
        } else if (winner != null) {
            String winnerName = winner.getName();
            String victoryMessage = ChatColor.RED + winnerName + "の勝利！";
            showTitleToAllPlayers("ゲーム終了！", victoryMessage, 10, 70, 20);
            launchFireworks(winner, 5); // 勝者を追跡して花火を打ち上げる
        }

        playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP);

        // 全員をスタート地点にテレポートし、クリエイティブモードに戻す
        if (startLocation != null) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.teleport(startLocation);
                onlinePlayer.getInventory().clear(); // インベントリをクリア
                onlinePlayer.setGameMode(GameMode.CREATIVE); // ゲームモードをクリエイティブに戻す
                onlinePlayer.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard()); // スコアボードをクリア
            }
        }
    }

    private void launchFireworks(Player player, int seconds) {
        new BukkitRunnable() {
            int timeLeft = seconds * 5; // 秒数をティックに変換

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    cancel();
                } else {
                    Location location = player.getLocation(); // プレイヤーの現在位置を取得
                    Firework firework = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK_ROCKET);
                    FireworkMeta meta = firework.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .withColor(org.bukkit.Color.ORANGE)
                            .withFade(org.bukkit.Color.YELLOW)
                            .with(FireworkEffect.Type.BALL)
                            .trail(true)
                            .flicker(true)
                            .build());
                    meta.setPower(1);
                    firework.setFireworkMeta(meta);
                    timeLeft--;
                }
            }
        }.runTaskTimer(this, 0, 4); // 4ティックごとに1発打ち上げ（0.2秒ごと）
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!gameRunning) return;

        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();

        // 各鉱石のスコア
        int points = 0;
        Sound soundEffect = Sound.BLOCK_NOTE_BLOCK_BELL;
        switch (blockType) {
            case COAL_ORE:
            case DEEPSLATE_COAL_ORE:
                points = 1;
                soundEffect = Sound.BLOCK_NOTE_BLOCK_BASS;
                break;
            case IRON_ORE:
            case DEEPSLATE_IRON_ORE:
                points = 1;
                soundEffect = Sound.BLOCK_NOTE_BLOCK_BASS;
                break;
            case COPPER_ORE:
            case DEEPSLATE_COPPER_ORE:
                points = 1;
                soundEffect = Sound.BLOCK_NOTE_BLOCK_BASS;
                break;
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
                points = 3;
                soundEffect = Sound.BLOCK_NOTE_BLOCK_HARP;
                break;
            case REDSTONE_ORE:
            case DEEPSLATE_REDSTONE_ORE:
                points = 1;
                soundEffect = Sound.BLOCK_NOTE_BLOCK_BASS;
                break;
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
                points = 3;
                soundEffect = Sound.BLOCK_NOTE_BLOCK_HARP;
                break;
            case LAPIS_ORE:
            case DEEPSLATE_LAPIS_ORE:
                points = 2;
                soundEffect = Sound.BLOCK_NOTE_BLOCK_FLUTE;
                break;
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
                points = 5;
                soundEffect = Sound.BLOCK_NOTE_BLOCK_CHIME;
                break;
            default:
                break;
        }

        if (points > 0) {
            int newScore = playerScores.getOrDefault(player, 0) + points;
            playerScores.put(player, newScore);
            player.sendMessage(blockType.name() + "を掘りました！現在のスコア: " + newScore);

            // スコアボードを更新
            objective.getScore(player.getName()).setScore(newScore);
            
            // サウンドエフェクトを再生
            player.playSound(player.getLocation(), soundEffect, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!gameRunning) return;
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true); // ダメージをキャンセル
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true); // プレイヤーの動きをキャンセル
        }
    }

    @EventHandler
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        if (gameRunning) {
            Player player = event.getPlayer();
            player.setFoodLevel(20); // スプリントしても空腹度が減らないように設定
        }
    }

    private void playSoundToAllPlayers(Sound sound) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    private void showTitleToAllPlayers(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }
}
