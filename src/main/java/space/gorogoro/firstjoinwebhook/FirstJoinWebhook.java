package space.gorogoro.firstjoinwebhook;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class FirstJoinWebhook extends JavaPlugin implements Listener{
  @Override
  public void onEnable() {
      Bukkit.getLogger().info("The Plugin Has Been Enabled!");

      // Create the configuration file if it does not exist.
      File configFile = new File(getDataFolder(), "config.yml");
      if (!configFile.exists()) {
          saveDefaultConfig();
      }

      // Register the event listener.
      getServer().getPluginManager().registerEvents(this, this);
      getLogger().info("[ServerListMod] It started up successfully.");
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (command.getName().equalsIgnoreCase("fjw")) {
          // Check if the sender has OP privileges.
          if (sender.isOp()) {
              // Verify the array index to prevent exceptions.
              if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                  reloadConfig();
                  sender.sendMessage("[FirstJoinWebhook] Successfully reloaded config.yml.");
                  return true;
              }
              sender.sendMessage("[FirstJoinWebhook] Usage: /fjw reload");
              return true;
          } else {
              sender.sendMessage("You do not have permission (OP) to execute this command.");
              return true;
          }
      }
      return false;
  }
  
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
      if (!event.getPlayer().hasPlayedBefore()) {
          // 初参加プレイヤーの場合、通信処理を非同期（別スレッド）で実行する
          Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
              try {
                  String urlStr = getConfig().getString("url");
                  if (urlStr == null || urlStr.isEmpty()) return;

                  URL url = new URI(urlStr).toURL();
                  HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                  connection.setRequestMethod("POST");
                  connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
                  connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                  connection.setDoOutput(true);
                  connection.setDoInput(true);
                  connection.setUseCaches(false);
                  
                  // プレイヤー名などを埋め込めるようにする場合はここで置換
                  String content = getConfig().getString("content", "Welcome!");
                  content = content.replace("%player%", event.getPlayer().getName());
                  
                  // JSONの構文を壊さないようにエスケープ処理
                  content = content.replace("\\", "\\\\")   // バックスラッシュ自体をエスケープ（最優先）
                                   .replace("\"", "\\\"")   // ダブルクォーテーションを \" に置換
                                   .replace("\n", "\\n")    // 改行コードを \n という文字列に置換
                                   .replace("\r", "");      // Windows特有の改行コード（CR）を除去

                  
                  String jsonMessage = "{\"content\": \"" + content + "\"}";

                  // コネクション、通信開始
                  connection.connect();
                  // try-with-resources を使うと自動で close されて安全です
                  try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"))) {
                      writer.write(jsonMessage);
                  }
                  
                  int responseCode = connection.getResponseCode();
                  if (responseCode >= 200 && responseCode < 300) { // Discord等は204などを返すことがあるため
                      getLogger().info("Webhook Sent Successfully!");
                  } else {
                      getLogger().warning("Failed. Response code: " + responseCode);
                  }

                  // 最後に必ず切断する
                  connection.disconnect();

              } catch (IOException | URISyntaxException e) {
                  getLogger().severe("Webhook error: " + e.getMessage());
              }
          });
      }
  }

  @Override
  public void onDisable(){
      getLogger().info("The Plugin Has Been Disabled!");
  }
}
