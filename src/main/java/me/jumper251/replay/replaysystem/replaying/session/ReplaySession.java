package me.jumper251.replay.replaysystem.replaying.session;

import java.util.Arrays;
import java.util.List;

import me.jumper251.replay.replaysystem.replaying.ReplayHelper;
import me.jumper251.replay.replaysystem.replaying.ReplayPacketListener;
import me.jumper251.replay.replaysystem.replaying.Replayer;
import org.bukkit.Bukkit;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import me.jumper251.replay.ReplaySystem;
import me.jumper251.replay.api.ReplaySessionFinishEvent;
import me.jumper251.replay.filesystem.ConfigManager;
import me.jumper251.replay.filesystem.ItemConfig;
import me.jumper251.replay.filesystem.ItemConfigOption;
import me.jumper251.replay.filesystem.ItemConfigType;

public class ReplaySession {

	private Replayer replayer;
	
	private Player player;
	
	private ItemStack content[];
	
	private int level;
	
	private float xp;
	
	private Location start;
	
	private ReplayPacketListener packetListener;
	
	public ReplaySession(Replayer replayer) {
		this.replayer = replayer;
		
		this.player = this.replayer.getWatchingPlayer();
		
		this.packetListener = new ReplayPacketListener(replayer);
	}
	
	public void startSession() {
		this.packetListener.register();

		this.content = this.player.getInventory().getContents();
		if (this.start == null) {
			this.start = this.player.getLocation();
		}
		this.level = this.player.getLevel();
		this.xp = this.player.getExp();

		// this.player.setHealth(this.player.getMaxHealth());
		// this.player.setFoodLevel(20);
		// this.player.getInventory().clear();
		
//		ItemConfigOption teleport = ItemConfig.getItem(ItemConfigType.TELEPORT);
//		ItemConfigOption time = ItemConfig.getItem(ItemConfigType.SPEED);
//		ItemConfigOption leave = ItemConfig.getItem(ItemConfigType.LEAVE);
//		ItemConfigOption backward = ItemConfig.getItem(ItemConfigType.BACKWARD);
//		ItemConfigOption forward = ItemConfig.getItem(ItemConfigType.FORWARD);
//		ItemConfigOption pauseResume = ItemConfig.getItem(ItemConfigType.PAUSE);
//
//		List<ItemConfigOption> configItems = Arrays.asList(teleport, time, leave, backward, forward, pauseResume);

//		configItems.stream()
//			.filter(ItemConfigOption::isEnabled)
//			.forEach(item -> {
//				this.player.getInventory().setItem(item.getSlot(), ReplayHelper.createItem(item));
//			});
//		
		
		// this.player.setAllowFlight(true);
		// this.player.setFlying(true);
		
		if (ConfigManager.HIDE_PLAYERS) {
			for (Player all : Bukkit.getOnlinePlayers()) {
				if (all == this.player) continue;
				
				this.player.hidePlayer(all);
			}
		}


	}
	
	public void stopSession() {
		if (ReplayHelper.replaySessions.containsKey(this.player.getName())) {
			ReplayHelper.replaySessions.remove(this.player.getName());
		}
		
		this.packetListener.unregister();

		
		new BukkitRunnable() {
			
			@Override
			public void run() {
				resetPlayer();
				// don't teleport player anymore
				// player.teleport(start);
				
				
				if (ConfigManager.HIDE_PLAYERS) {
					for (Player all : Bukkit.getOnlinePlayers()) {
						if (all == player) continue;
						
						player.showPlayer(all);
					}
				}

				ReplaySessionFinishEvent finishEvent = new ReplaySessionFinishEvent(replayer.getReplay(), player);
				Bukkit.getPluginManager().callEvent(finishEvent);
			}
		}.runTask(ReplaySystem.getInstance());
		

	}
	
	public void resetPlayer() {
//		player.getInventory().clear();
//		player.getInventory().setContents(content);
//		
		if (player.getGameMode() != GameMode.CREATIVE) {
			player.setFlying(false);
			player.setAllowFlight(false);
			packetListener.setCamera(player, player.getEntityId(), 0F);
		}

		if (ConfigManager.PROGRESS_TYPE == ReplayProgressType.XP_BAR) {
			player.setLevel(level);
			player.setExp(xp);
		}
	}

	public void setStart(Location start) {
		this.start = start;
	}

	public ReplayPacketListener getPacketListener() {
		return packetListener;
	}
}
