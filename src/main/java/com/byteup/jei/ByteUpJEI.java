package com.byteup.jei;

import com.byteup.jei.index.RecipeIndex;
import com.byteup.jei.pack.PackHttpServer;
import com.byteup.jei.pack.ResourcePackBuilder;
import com.byteup.jei.ui.JeiDialogRenderer;
import com.byteup.jei.ui.JeiViewState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ByteUpJEI extends JavaPlugin implements TabExecutor, Listener {
    private static final UUID ID_DO_PACK = UUID.nameUUIDFromBytes("byteup-jei".getBytes());
    private RecipeIndex recipeIndex;
    private ResourcePackBuilder.PackResult pack;
    private PackHttpServer packHttpServer;
    private JeiDialogRenderer renderer;
    private final Map<UUID, JeiViewState> states = new ConcurrentHashMap<>();
    private final Set<UUID> packLoaded = ConcurrentHashMap.newKeySet();
    private final Set<UUID> openWhenPackReady = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        recipeIndex = RecipeIndex.build();
        try {
            buildPack();
            packHttpServer = new PackHttpServer(this);
            packHttpServer.start();
        } catch (Exception ex) {
            getLogger().severe("Falha ao gerar/servir resource pack: " + ex.getMessage());
            ex.printStackTrace();
        }
        renderer = new JeiDialogRenderer(this);
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("jei"), "Command 'jei' missing from plugin.yml").setExecutor(this);
        Objects.requireNonNull(getCommand("jei")).setTabCompleter(this);
        getLogger().info("ByteUP JEI 1.2.2 iniciado: " + recipeIndex.itemCount() + " itens, "
                + recipeIndex.recipeCount() + " receitas.");
    }

    @Override
    public void onDisable() {
        if (packHttpServer != null) packHttpServer.stop();
        states.clear();
        packLoaded.clear();
        openWhenPackReady.clear();
    }

    private void buildPack() throws Exception {
        List<Material> items = recipeIndex.searchItems("");
        pack = new ResourcePackBuilder(this, items).build();
        getLogger().info("Resource pack ByteUP JEI gerado: " + pack.size() + " bytes; SHA1 " + pack.sha1Hex());
    }

    public RecipeIndex index() { return recipeIndex; }
    public ResourcePackBuilder.PackResult pack() { return pack; }

    private JeiViewState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new JeiViewState());
    }

    /** pack.url-externa (host externo) > resource-pack.public-address > endereco que o jogador usou para entrar. */
    private String resolveUrl(Player player) {
        String externa = getConfig().getString("resource-pack.url-externa", "").trim();
        if (!externa.isEmpty()) return externa;
        String addr = getConfig().getString("resource-pack.public-address", "auto").trim();
        if (addr.isEmpty() || addr.equalsIgnoreCase("auto")) {
            var vhost = player.getVirtualHost();
            addr = vhost != null ? vhost.getHostString() : "127.0.0.1";
        }
        return "http://" + addr + ":" + getConfig().getInt("resource-pack.port", 8767) + "/byteup-jei.zip";
    }

    private void sendPack(Player player, boolean openAfter) {
        String url = resolveUrl(player);
        if (pack == null) {
            player.sendMessage(ChatColor.RED + "ByteUP JEI: o resource pack não foi gerado. Veja o console.");
            return;
        }
        if (openAfter) openWhenPackReady.add(player.getUniqueId());
        try {
            boolean externa = !getConfig().getString("resource-pack.url-externa", "").isBlank();
            byte[] hash = externa && !getConfig().getBoolean("resource-pack.url-externa-conferir-hash", false) ? null : pack.sha1();
            // addResourcePack soma ao(s) pack(s) que o jogador ja tem (ex.: o do ByteUP HUD) sem remover nenhum
            player.addResourcePack(ID_DO_PACK, url, hash,
                    getConfig().getString("resource-pack.prompt", "Este servidor usa um pack para mostrar o JEI."),
                    getConfig().getBoolean("resource-pack.required", false));
        } catch (RuntimeException ex) {
            openWhenPackReady.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "ByteUP JEI: não foi possível enviar o resource pack: " + ex.getMessage());
            getLogger().warning("Falha ao enviar resource pack para " + player.getName() + ": " + ex.getMessage());
        }
    }

    private void open(Player player) {
        if (!packLoaded.contains(player.getUniqueId())) {
            sendPack(player, true);
            return;
        }
        renderer.open(player, state(player));
    }

    /** The server can not see the E key, but it does open the crafting table: open JEI there (sneak = vanilla table). */
    @EventHandler(ignoreCancelled = true)
    public void onWorkbench(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.WORKBENCH) return;
        if (!getConfig().getBoolean("open-on-crafting-table", true)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!player.hasPermission("byteupjei.use") || player.isSneaking()) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(this, () -> { if (player.isOnline()) open(player); });
    }

    /**
     * A tecla F (trocar de mao) o servidor ve como acao, independente da tecla que o jogador configurou.
     * Agachado + trocar de mao abre o JEI.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!getConfig().getBoolean("open-on-sneak-swap-hand", true)) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.hasPermission("byteupjei.use")) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(this, () -> { if (player.isOnline()) open(player); });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> sendPack(event.getPlayer(), false), 40L);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!ID_DO_PACK.equals(event.getID())) return;
        UUID playerId = event.getPlayer().getUniqueId();
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            packLoaded.add(playerId);
            if (openWhenPackReady.remove(playerId)) {
                Player player = event.getPlayer();
                Bukkit.getScheduler().runTask(this, () -> {
                    if (player.isOnline()) renderer.open(player, state(player));
                });
            }
        } else if (event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            packLoaded.remove(playerId);
            openWhenPackReady.remove(playerId);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
        packLoaded.remove(event.getPlayer().getUniqueId());
        openWhenPackReady.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("byteupjei.admin")) {
                sender.sendMessage(ChatColor.RED + "Você não tem permissão.");
                return true;
            }
            recipeIndex = RecipeIndex.build();
            try {
                buildPack();
                sender.sendMessage(ChatColor.GREEN + "ByteUP JEI atualizado: " + recipeIndex.itemCount()
                        + " itens e " + recipeIndex.recipeCount() + " receitas.");
            } catch (Exception ex) {
                sender.sendMessage(ChatColor.RED + "Falha ao reconstruir o resource pack: " + ex.getMessage());
                getLogger().severe("Falha ao reconstruir pack: " + ex);
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por um jogador.");
            return true;
        }
        if (!player.hasPermission("byteupjei.use")) {
            player.sendMessage(ChatColor.RED + "Você não tem permissão para usar o ByteUP JEI.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("ui")) {
            handleUi(player, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        JeiViewState state = state(player);
        if (args.length >= 2 && args[0].equalsIgnoreCase("item")) {
            select(state, args[1], false);
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("uses")) {
            select(state, args[1], true);
        } else if (args.length > 0) {
            state.query = String.join(" ", args);
            state.page = 0;
            state.selected = null;
        } else {
            state.query = "";
            state.page = 0;
            state.selected = null;
            state.uses = false;
            state.recipeOffset = 0;
            state.recipeFilter = "all";
        }
        open(player);
        return true;
    }

    private void handleUi(Player player, String[] args) {
        if (args.length == 0) { open(player); return; }
        JeiViewState state = state(player);
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "item" -> { if (args.length > 1) select(state, args[1], false); }
            case "ingredient" -> { if (args.length > 1) select(state, args[1], true); }
            case "prev" -> state.page = Math.max(0, state.page - 1);
            case "next" -> {
                int count = recipeIndex.searchItems(state.query).size();
                int max = Math.max(0, (count - 1) / JeiDialogRenderer.PAGE_SIZE);
                state.page = Math.min(max, state.page + 1);
            }
            case "mode" -> {
                if (args.length > 1) state.uses = args[1].equalsIgnoreCase("uses");
                state.recipeOffset = 0;
            }
            case "filter" -> {
                if (args.length > 1) state.recipeFilter = args[1].toLowerCase(Locale.ROOT);
                state.recipeOffset = 0;
            }
            case "recipe-prev" -> state.recipeOffset = Math.max(0, state.recipeOffset - 2);
            case "recipe-next" -> {
                int pages = Math.max(1, (recipesFor(state).size() + 1) / 2);
                int maxOffset = Math.max(0, (pages - 1) * 2);
                state.recipeOffset = Math.min(maxOffset, state.recipeOffset + 2);
            }
            case "search" -> {
                state.selected = null;
                state.uses = false;
                state.query = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
                state.page = 0;
                state.recipeOffset = 0;
            }
            default -> { }
        }
        renderer.open(player, state);
    }


    private List<com.byteup.jei.index.RecipeEntry> recipesFor(JeiViewState state) {
        List<com.byteup.jei.index.RecipeEntry> all = state.selected == null ? recipeIndex.allRecipes()
                : state.uses ? recipeIndex.recipesUsing(state.selected) : recipeIndex.recipesFor(state.selected);
        String filter = state.recipeFilter == null ? "all" : state.recipeFilter;
        if (filter.equalsIgnoreCase("all")) return all;
        String f = filter.toLowerCase(Locale.ROOT);
        return all.stream().filter(r -> {
            String type = r.type().toLowerCase(Locale.ROOT);
            return switch (f) {
                case "crafting" -> type.contains("shaped") || type.contains("shapeless") || type.contains("craft");
                case "furnace" -> type.contains("furnace") || type.contains("smelt") || type.contains("blasting");
                case "smoker" -> type.contains("smok");
                case "brewing" -> type.contains("brew");
                case "smithing" -> type.contains("smith");
                default -> true;
            };
        }).toList();
    }

    private void select(JeiViewState state, String rawMaterial, boolean uses) {
        Material material;
        try { material = Material.valueOf(rawMaterial.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return; }
        if (!material.isItem() || material.isAir()) return;
        state.selected = material;
        state.uses = uses;
        state.recipeOffset = 0;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("reload", "item", "uses", "search").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("item") || args[0].equalsIgnoreCase("uses"))) {
            String prefix = args[1].toUpperCase(Locale.ROOT);
            return recipeIndex.searchItems("").stream().map(Material::name).filter(s -> s.startsWith(prefix)).limit(80).toList();
        }
        return List.of();
    }
}
