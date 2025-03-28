package me.jumper251.replay.commands.replay;

import java.util.List;
import java.util.stream.Collectors;

import me.jumper251.replay.filesystem.Messages;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import me.jumper251.replay.ReplaySystem;
import me.jumper251.replay.commands.AbstractCommand;
import me.jumper251.replay.commands.SubCommand;
import me.jumper251.replay.filesystem.saving.ReplaySaver;
import me.jumper251.replay.replaysystem.Replay;
import me.jumper251.replay.replaysystem.replaying.ReplayHelper;
import me.jumper251.replay.utils.fetcher.Consumer;

public class ReplayWatchCommand extends SubCommand {

	public ReplayWatchCommand(AbstractCommand parent) {
		super(parent, "watch", "Starts a recorded replay for a player", "watch <Name> <Player>", false);
	}

	@Override
	public boolean execute(CommandSender cs, Command cmd, String label, String[] args) {
		if (args.length != 3)
			return false;

		String name = args[1];

		final Player p = (Player) Bukkit.getPlayer(args[2]);

		if (ReplaySaver.exists(name) && !ReplayHelper.replaySessions.containsKey(p.getName())) {
			Messages.REPLAY_PLAY_LOAD.arg("replay", name).send(cs);

			try {
				ReplaySaver.load(args[1], replay -> {
					Messages.REPLAY_PLAY.arg("duration", replay.getData().getDuration() / 20).send(cs);
					replay.play(p);
				});

			} catch (Exception e) {
				e.printStackTrace();

				Messages.REPLAY_PLAY_ERROR.arg("replay", name).send(cs);
			}
		} else {
			Messages.REPLAY_NOT_FOUND.send(cs);
		}

		return true;
	}

	@Override
	public List<String> onTab(CommandSender cs, Command cmd, String label, String[] args) {
		return ReplaySaver.getReplays().stream()
				.filter(name -> StringUtil.startsWithIgnoreCase(name, args.length > 1 ? args[1] : null))
				.collect(Collectors.toList());
	}

}
