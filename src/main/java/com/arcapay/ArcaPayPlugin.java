package com.arcapay;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class ArcaPayPlugin extends JavaPlugin {

    private String token;
    private String apiUrl;
    private int pollInterval;
    private String identifierType;
    private boolean debug;
    private final Gson gson = new Gson();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        // Polling async
        new BukkitRunnable() {
            @Override
            public void run() {
                pollCommands();
            }
        }.runTaskTimerAsynchronously(this, 20L * 5, 20L * pollInterval);

        getLogger().info("ArcaPay iniciado! Polling a cada " + pollInterval + "s");
    }

    private void loadSettings() {
        reloadConfig();
        token = getConfig().getString("token", "");
        apiUrl = getConfig().getString("api-url", "https://arcapay.org/api/v1/fivem");
        pollInterval = getConfig().getInt("poll-interval", 10);
        identifierType = getConfig().getString("identifier-type", "name");
        debug = getConfig().getBoolean("debug", false);
    }

    private void pollCommands() {
        try {
            String response = httpGet(apiUrl + "/pending-commands");
            if (response == null || response.isEmpty()) return;

            JsonArray commands = JsonParser.parseString(response).getAsJsonArray();
            if (commands.size() == 0) return;

            getLogger().info("[ArcaPay] " + commands.size() + " comando(s) pendente(s)");

            for (JsonElement el : commands) {
                JsonObject cmd = el.getAsJsonObject();
                String id = cmd.get("id").getAsString();
                String command = cmd.get("command").getAsString();

                // Executa no main thread
                boolean[] result = {false};
                String[] message = {"Erro desconhecido"};

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            result[0] = executeCommand(command);
                            message[0] = result[0] ? "Comando executado" : "Falha ao executar";
                        } catch (Exception e) {
                            message[0] = e.getMessage();
                        }

                        // Report async
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                reportCommand(id, result[0], message[0]);
                            }
                        }.runTaskAsynchronously(ArcaPayPlugin.this);
                    }
                }.runTask(this);
            }
        } catch (Exception e) {
            if (debug) getLogger().log(Level.WARNING, "[ArcaPay] Erro no polling", e);
        }
    }

    private boolean executeCommand(String command) {
        // Substitui {player} pelo nome do jogador online se encontrar
        // Formato esperado: <comando> <identifier> [args...]
        String[] parts = command.split("\\s+");
        if (parts.length >= 2) {
            String identifier = parts[1];
            Player player = findPlayer(identifier);
            if (player != null) {
                command = command.replace("{player}", player.getName());
                command = command.replace("{uuid}", player.getUniqueId().toString());
                // Substitui o identifier pelo nome real se necessario
                if (identifierType.equals("name")) {
                    // O identifier JA e o nome, nao precisa trocar
                } else if (identifierType.equals("uuid")) {
                    command = command.replace(identifier, player.getName());
                }
            }
        }

        if (debug) getLogger().info("[ArcaPay] Executando: " + command);

        // Executa como comando de console do servidor
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private Player findPlayer(String identifier) {
        if (identifierType.equals("uuid")) {
            try {
                return Bukkit.getPlayer(java.util.UUID.fromString(identifier));
            } catch (Exception ignored) {}
        }
        // Por nome (case insensitive)
        return Bukkit.getPlayerExact(identifier);
    }

    private void reportCommand(String id, boolean success, String message) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("id", id);
            body.addProperty("success", success);
            if (!success) body.addProperty("error", message);
            JsonObject resp = new JsonObject();
            resp.addProperty("message", message);
            body.add("response", resp);

            httpPost(apiUrl + "/report-command", body.toString());
        } catch (Exception e) {
            if (debug) getLogger().log(Level.WARNING, "[ArcaPay] Erro ao reportar", e);
        }
    }

    // ── HTTP Helpers ─────────────────────────────────────────────────────────

    private String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        if (conn.getResponseCode() != 200) {
            if (debug) getLogger().warning("[ArcaPay] HTTP " + conn.getResponseCode());
            return null;
        }

        return readStream(conn.getInputStream());
    }

    private void httpPost(String urlStr, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        conn.getResponseCode(); // Trigger request
    }

    private String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("arcapay")) return false;
        if (!sender.hasPermission("arcapay.admin")) {
            sender.sendMessage("Sem permissao.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§a[ArcaPay] §fComandos: reload, status, poll");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                loadSettings();
                sender.sendMessage("§a[ArcaPay] §fConfiguracoes recarregadas.");
                break;
            case "status":
                sender.sendMessage("§a[ArcaPay] §fAPI: " + apiUrl);
                sender.sendMessage("§a[ArcaPay] §fToken: " + token.substring(0, Math.min(8, token.length())) + "...");
                sender.sendMessage("§a[ArcaPay] §fPolling: " + pollInterval + "s");
                sender.sendMessage("§a[ArcaPay] §fIdentifier: " + identifierType);
                break;
            case "poll":
                sender.sendMessage("§a[ArcaPay] §fPolling manual...");
                new BukkitRunnable() {
                    @Override
                    public void run() { pollCommands(); }
                }.runTaskAsynchronously(this);
                break;
            default:
                sender.sendMessage("§a[ArcaPay] §fComando desconhecido.");
        }
        return true;
    }
}
