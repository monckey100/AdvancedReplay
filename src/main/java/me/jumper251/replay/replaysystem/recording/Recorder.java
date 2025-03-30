package me.jumper251.replay.replaysystem.recording;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONObject;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.google.common.collect.Multimap;

import me.jumper251.replay.ReplaySystem;
import me.jumper251.replay.api.IReplayHook;
import me.jumper251.replay.api.ReplayAPI;
import me.jumper251.replay.filesystem.ConfigManager;
import me.jumper251.replay.filesystem.saving.ReplaySaver;
import me.jumper251.replay.replaysystem.Replay;
import me.jumper251.replay.replaysystem.data.ActionData;
import me.jumper251.replay.replaysystem.data.ActionType;
import me.jumper251.replay.replaysystem.data.ReplayData;
import me.jumper251.replay.replaysystem.data.ReplayInfo;
import me.jumper251.replay.replaysystem.data.types.BlockChangeData;
import me.jumper251.replay.replaysystem.data.types.ChatData;
import me.jumper251.replay.replaysystem.data.types.EntityAnimationData;
import me.jumper251.replay.replaysystem.data.types.EntityData;
import me.jumper251.replay.replaysystem.data.types.EntityItemData;
import me.jumper251.replay.replaysystem.data.types.EntityMovingData;
import me.jumper251.replay.replaysystem.data.types.LocationData;
import me.jumper251.replay.replaysystem.data.types.PacketData;
import me.jumper251.replay.replaysystem.data.types.SignatureData;
import me.jumper251.replay.replaysystem.data.types.SpawnData;
import me.jumper251.replay.replaysystem.utils.NPCManager;
import me.jumper251.replay.utils.MojangSkinGenerator;
import me.jumper251.replay.utils.ReplayManager;
import me.jumper251.replay.utils.fetcher.JsonData;
import me.jumper251.replay.utils.fetcher.PlayerInfo;
import me.jumper251.replay.utils.fetcher.SkinInfo;
import me.jumper251.replay.utils.fetcher.WebsiteFetcher;
public class Recorder {

	private List<String> players;
	private Map<String, String> playerAliases;
	
	private Replay replay;
	
	private ReplayData data;
	
	private BukkitRunnable run;
	private int currentTick;
	private PacketRecorder packetRecorder;
	
	private CommandSender sender;
	
	public Recorder(Replay replay, List<Player> players, CommandSender sender) {
		this.players = new ArrayList<String>();
		this.playerAliases = new HashMap<String, String>();
		this.data = new ReplayData();
		this.replay = replay;
		this.sender = sender;
		
		HashMap<String, PlayerWatcher> tmpWatchers = new HashMap<String, PlayerWatcher>();
		for (Player player: players) {
			this.players.add(player.getName());
			this.playerAliases.put(player.getName(), player.getName());
			tmpWatchers.put(player.getName(), new PlayerWatcher(player.getName(),player.getName()));
		}
		
		this.data.setWatchers(tmpWatchers);
	}
	
	public Recorder(Replay replay, List<Player> players, CommandSender sender, Map<Player, String> playerAliases) {
		this.players = new ArrayList<String>();
		this.playerAliases = new HashMap<String, String>();
		this.data = new ReplayData();
		this.replay = replay;
		this.sender = sender;
		
		HashMap<String, PlayerWatcher> tmpWatchers = new HashMap<String, PlayerWatcher>();
		for (Player player: players) {
			String alias = playerAliases.getOrDefault(player, player.getName());
			this.players.add(player.getName());
			this.playerAliases.put(player.getName(), alias);
			tmpWatchers.put(player.getName(), new PlayerWatcher(player.getName(),alias));
		}
		
		this.data.setWatchers(tmpWatchers);
	}
	
	public void start() {
		this.packetRecorder = new PacketRecorder(this);
		this.packetRecorder.register();
		
		for (String names : this.players) {
			if (Bukkit.getPlayer(names) != null) {
				Player player = Bukkit.getPlayer(names);
				createSpawnAction(player, player.getLocation(), true);
			}
		}
		
		this.run = new BukkitRunnable() {
			
			@Override
			public void run() {
				
				HashMap<String, List<PacketData>> tmpMap = new HashMap<>(packetRecorder.getPacketData());
				
				for (String name : tmpMap.keySet()) {
					List<PacketData> list = new ArrayList<>(tmpMap.get(name));
					for (Iterator<PacketData> it = list.iterator(); it.hasNext();) {
						PacketData packetData = it.next();
						
						if (packetData instanceof BlockChangeData && !ConfigManager.RECORD_BLOCKS) continue;
						if (packetData instanceof EntityItemData) {
							EntityItemData data = (EntityItemData) packetData;
							if (data.getAction() != 2 && !ConfigManager.RECORD_ITEMS) continue;
						}
						if ((packetData instanceof EntityData || packetData instanceof EntityMovingData || packetData instanceof EntityAnimationData) && !ConfigManager.RECORD_ENTITIES) continue;
						if (packetData instanceof ChatData && !ConfigManager.RECORD_CHAT) continue;


						ActionData actionData = new ActionData(currentTick, ActionType.PACKET, name, packetData, playerAliases.get(name));
						addData(currentTick, actionData);
						

					}
					
				}

				packetRecorder.getPacketData().keySet().removeAll(tmpMap.keySet());

			

				if (ReplayAPI.getInstance().getHookManager().isRegistered()) {
					
					for (IReplayHook hook : ReplayAPI.getInstance().getHookManager().getHooks()) {
						for (String names : players) {
							List<PacketData> customList = hook.onRecord(names);
							customList.stream().filter(Objects::nonNull).forEach(customData -> {
								ActionData customAction = new ActionData(currentTick, ActionType.CUSTOM, names, customData, playerAliases.get(names));
								addData(currentTick, customAction);
							});

						}
					}
				}
				
				
				Recorder.this.currentTick++;
				
				if ((Recorder.this.currentTick / 20) >= ConfigManager.MAX_LENGTH) stop(ConfigManager.SAVE_STOP);
			}
		};
		
		this.run.runTaskTimerAsynchronously(ReplaySystem.getInstance(), 1, 1);
	}
	
	public void addData(int tick, ActionData actionData) {
		List<ActionData> list = new ArrayList<>();
		if(this.data.getActions().containsKey(tick)) {
			list = this.data.getActions().get(tick);
		}
		
		list.add(actionData);
		this.data.getActions().put(tick, list);
	}
	
	public void stop(boolean save) {
		this.packetRecorder.unregister();
		this.run.cancel();

		if (save) {
			this.data.setDuration(this.currentTick);

			String creator = this.sender != null ? this.sender.getName() : "CONSOLE";
			this.data.setCreator(creator);
			this.data.setWatchers(new HashMap<>());
			this.replay.setData(this.data);
			this.replay.setReplayInfo(new ReplayInfo(this.replay.getId(), creator, System.currentTimeMillis(), this.currentTick));
			ReplaySaver.save(this.replay);
		} else {
			this.data.getActions().clear();
		}
		
		this.replay.setRecording(false);

		
		if (ReplayManager.activeReplays.containsKey(this.replay.getId())) {
			ReplayManager.activeReplays.remove(this.replay.getId());
		}
	}
    private boolean isInDirectory(File file, File directory) {
        Path filePath = Paths.get(file.toURI()).toAbsolutePath().normalize();
        Path directoryPath = Paths.get(directory.toURI()).toAbsolutePath().normalize();
        return filePath.startsWith(directoryPath);
    }
	public void createSpawnAction(Player player, Location loc, boolean first) {
		SignatureData[] signArr = new SignatureData[1];
		String alias = this.playerAliases.get(player.getName());
		   String skinFile = ConfigManager.cfg.getString("Skins." + alias + ".File");
		    String skinURL = ConfigManager.cfg.getString("Skins." + alias + ".URL");

		    if ((skinFile != null && !skinFile.isEmpty()) || (skinURL != null && !skinURL.isEmpty())) {
		        // Process the custom skin asynchronously.
		            try {
		                JSONObject data = null;
		                if (skinFile != null && !skinFile.isEmpty()) {
		                    // Assume skins are stored in a "skins" folder inside your plugin's data folder.
		                    File skinsFolder = new File(ReplaySystem.getInstance().getDataFolder(), "skins");
		                    File skin = new File(skinsFolder, skinFile);
		                    // Validate the file.
		                    if (!skin.exists() || !skin.isFile() || skin.isHidden() || !isInDirectory(skin, skinsFolder)) {
		                        // Optionally log or notify an error here.
		                        return;
		                    }
		                    byte[] skinBytes = java.nio.file.Files.readAllBytes(skin.toPath());
		                    // Generate skin data from the PNG bytes.
		                    data = MojangSkinGenerator.generateFromPNG(skinBytes, false);
		                } else {
		                    // Generate skin data from the URL.
		                    data = MojangSkinGenerator.generateFromURL(skinURL, false);
		                }
		                
		                // Extract texture info from the returned JSON.
		                JSONObject textureObj = (JSONObject) data.get("texture");
		                String textureEncoded = (String) textureObj.get("value");
		                String signature = (String) textureObj.get("signature");
		                
		                SignatureData customSkin = new SignatureData("textures", textureEncoded, signature);
		                // Schedule a synchronous task to add the spawn and inventory actions.

	                    ActionData spawnData = new ActionData(
	                        0, ActionType.SPAWN, player.getName(),
	                        new SpawnData(player.getUniqueId(), LocationData.fromLocation(loc), customSkin),
	                        playerAliases.get(player.getName())
	                    );
	                    addData(first ? 0 : currentTick, spawnData);
	                    
	                    ActionData invData = new ActionData(
	                        first ? 0 : currentTick, ActionType.PACKET, player.getName(),
	                        NPCManager.copyFromPlayer(player, true, true),
	                        playerAliases.get(player.getName())
	                    );
	                    addData(first ? 0 : currentTick, invData);

		            } catch (Exception e) {
		                e.printStackTrace();
		            }
		        // Return early since the custom skin is being processed.
		        return;
		    }
	    
		if ((!Bukkit.getOnlineMode() && ConfigManager.USE_OFFLINE_SKINS) || alias != player.getName()) {
			new BukkitRunnable() {
				
				@Override
				public void run() {
					PlayerInfo info = (PlayerInfo) WebsiteFetcher.getJson("https://api.mojang.com/users/profiles/minecraft/" + alias, true, new JsonData(true, new PlayerInfo()));

					if (info != null) {		
						SkinInfo skin = (SkinInfo) WebsiteFetcher.getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + info.getId() + "?unsigned=false", true, new JsonData(true, new SkinInfo()));
						
						Map<String, String> props = skin.getProperties().get(0);
						signArr[0] =  new SignatureData(props.get("name"), props.get("value"), props.get("signature"));
					}
					
					ActionData spawnData = new ActionData(0, ActionType.SPAWN, player.getName(), new SpawnData(player.getUniqueId(), LocationData.fromLocation(loc), signArr[0]),playerAliases.get(player.getName()));
					addData(first ? 0 : currentTick, spawnData);
				
					ActionData invData = new ActionData(0, ActionType.PACKET, player.getName(), NPCManager.copyFromPlayer(player, true, true),playerAliases.get(player.getName()));
					addData(first ? 0 : currentTick, invData);
				}
			}.runTaskAsynchronously(ReplaySystem.getInstance());
		}
		
		Multimap<String, WrappedSignedProperty> map = WrappedGameProfile.fromPlayer(player).getProperties();
		for (String prop : map.asMap().keySet()) {
			for (WrappedSignedProperty sp : map.get(prop)) {
				signArr[0] =  new SignatureData(sp.getName(), sp.getValue(), sp.getSignature());
			}
		}
		
		if (alias == player.getName() && (!ConfigManager.USE_OFFLINE_SKINS || Bukkit.getOnlineMode())) {
			ActionData spawnData = new ActionData(0, ActionType.SPAWN, player.getName(), new SpawnData(player.getUniqueId(), LocationData.fromLocation(loc), signArr[0]),playerAliases.get(player.getName()));
			addData(currentTick, spawnData);
		
			ActionData invData = new ActionData(currentTick, ActionType.PACKET, player.getName(), NPCManager.copyFromPlayer(player, true, true),playerAliases.get(player.getName()));
			addData(currentTick, invData);
		}
	}
	
	public List<String> getPlayers() {
		return players;
	}
	public Map<String,String> getPlayerAliases() {
		return playerAliases;
	}
	public ReplayData getData() {
		return data;
	}
	
	public int getCurrentTick() {
		return currentTick;
	}
}
