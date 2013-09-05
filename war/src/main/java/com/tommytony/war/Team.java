package com.tommytony.war;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.getspout.spoutapi.SpoutManager;
import org.getspout.spoutapi.player.SpoutPlayer;


import com.tommytony.war.config.InventoryBag;
import com.tommytony.war.config.TeamConfig;
import com.tommytony.war.config.TeamConfigBag;
import com.tommytony.war.config.TeamKind;
import com.tommytony.war.config.TeamSpawnStyle;
import com.tommytony.war.structure.Bomb;
import com.tommytony.war.structure.Cake;
import com.tommytony.war.utility.Direction;
import com.tommytony.war.utility.SignHelper;
import com.tommytony.war.volume.BlockInfo;
import com.tommytony.war.volume.Volume;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import org.kitteh.tag.TagAPI;

/**
 *
 * @author tommytony
 *
 */
public class Team {
	private List<Player> players = new ArrayList<Player>();
	private List<Location> teamSpawns;
	private Location teamFlag = null;
	private String name;
	private int remainingLives;
	private int points = 0;
	private Map<Location, Volume> spawnVolumes;
	private Volume flagVolume;
	private final Warzone warzone;
	private TeamKind kind;

	private TeamConfigBag teamConfig;
	private InventoryBag inventories;

	public Team(String name, TeamKind kind, List<Location> teamSpawn, Warzone warzone) {
		this.warzone = warzone;
		this.teamConfig = new TeamConfigBag(warzone);
		this.inventories = new InventoryBag(warzone);	// important constructors for cascading configs
		this.setName(name);
		this.teamSpawns = new ArrayList<Location>(teamSpawn);
		this.spawnVolumes = new HashMap<Location, Volume>();
		for (Location spawn : teamSpawn) {
			this.setSpawnVolume(spawn, new Volume(name + teamSpawns.indexOf(spawn), warzone.getWorld()));
		}
		this.kind = kind;
		this.setFlagVolume(null); // no flag at the start
	}

	public static Team getTeamByPlayerName(String playerName) {
		for (Warzone warzone : War.war.getWarzones()) {
			Team team = warzone.getPlayerTeam(playerName);
			if (team != null) {
				return team;
			}
		}
		return null;
	}

	public TeamKind getKind() {
		return this.kind;
	}

	private void createSpawnVolume(Location teamSpawn) {
		Volume spawnVolume = this.spawnVolumes.get(teamSpawn);
		if (spawnVolume.isSaved()) {
			spawnVolume.resetBlocks();
		}
		int x = teamSpawn.getBlockX();
		int y = teamSpawn.getBlockY();
		int z = teamSpawn.getBlockZ();

		TeamSpawnStyle style = this.getTeamConfig().resolveSpawnStyle();
		if (style.equals(TeamSpawnStyle.INVISIBLE)) {
			spawnVolume.setCornerOne(this.warzone.getWorld().getBlockAt(x, y - 1, z));
			spawnVolume.setCornerTwo(this.warzone.getWorld().getBlockAt(x, y + 3, z));
		} else if (style.equals(TeamSpawnStyle.SMALL)) {
			spawnVolume.setCornerOne(this.warzone.getWorld().getBlockAt(x - 1, y - 1, z - 1));
			spawnVolume.setCornerTwo(this.warzone.getWorld().getBlockAt(x + 1, y + 3, z + 1));
		} else {
			// flat or big
			spawnVolume.setCornerOne(this.warzone.getWorld().getBlockAt(x - 2, y - 1, z - 2));
			spawnVolume.setCornerTwo(this.warzone.getWorld().getBlockAt(x + 2, y + 3, z + 2));
		}
	}

	public void initializeTeamSpawns() {
		for (Location teamSpawn : this.spawnVolumes.keySet()) {
			initializeTeamSpawn(teamSpawn);
		}
	}
	public void initializeTeamSpawn(Location teamSpawn) {
		// Set the spawn
		int x = teamSpawn.getBlockX();
		int y = teamSpawn.getBlockY();
		int z = teamSpawn.getBlockZ();
		
		Material light = Material.getMaterial(this.warzone.getWarzoneMaterials().getLightId());
		byte lightData = this.warzone.getWarzoneMaterials().getLightData();

		TeamSpawnStyle style = this.getTeamConfig().resolveSpawnStyle();
		if (!style.equals(TeamSpawnStyle.INVISIBLE)) {
			// first ring
			this.setBlock(x + 1, y - 1, z + 1, this.kind);
			this.setBlock(x + 1, y - 1, z, this.kind);
			this.setBlock(x + 1, y - 1, z - 1, this.kind);
			this.setBlock(x, y - 1, z + 1, this.kind);
			Block lightBlock = this.warzone.getWorld().getBlockAt(x, y - 1, z);
			lightBlock.setType(light);
			lightBlock.setData(lightData);
			this.setBlock(x, y - 1, z - 1, this.kind);
			this.setBlock(x - 1, y - 1, z + 1, this.kind);
			this.setBlock(x - 1, y - 1, z, this.kind);
			this.setBlock(x - 1, y - 1, z - 1, this.kind);
		}

		// Orientation
		int yaw = 0;
		if (teamSpawn.getYaw() >= 0) {
			yaw = (int) (teamSpawn.getYaw() % 360);
		} else {
			yaw = (int) (360 + (teamSpawn.getYaw() % 360));
		}
		Block signBlock = null;
		int signData = 0;

		if (style.equals(TeamSpawnStyle.SMALL)) {
			// SMALL style
			if (yaw >= 0 && yaw < 90) {
				signData = 10;
				signBlock = this.warzone.getWorld().getBlockAt(x, y, z).getRelative(Direction.NORTH()).getRelative(Direction.WEST());
			} else if (yaw >= 90 && yaw <= 180) {
				signData = 14;
				signBlock = this.warzone.getWorld().getBlockAt(x, y, z).getRelative(Direction.NORTH()).getRelative(Direction.EAST());
			} else if (yaw >= 180 && yaw < 270) {
				signData = 2;
				signBlock = this.warzone.getWorld().getBlockAt(x, y, z).getRelative(Direction.SOUTH()).getRelative(Direction.EAST());
			} else if (yaw >= 270 && yaw <= 360) {
				signData = 6;
				signBlock = this.warzone.getWorld().getBlockAt(x, y, z).getRelative(Direction.SOUTH()).getRelative(Direction.WEST());
			}
		} else if (!style.equals(TeamSpawnStyle.INVISIBLE)) {
			// outer ring (FLAT or BIG)
			this.setBlock(x + 2, y - 1, z + 2, this.kind);
			this.setBlock(x + 2, y - 1, z + 1, this.kind);
			this.setBlock(x + 2, y - 1, z, this.kind);
			this.setBlock(x + 2, y - 1, z - 1, this.kind);
			this.setBlock(x + 2, y - 1, z - 2, this.kind);

			this.setBlock(x - 1, y - 1, z + 2, this.kind);
			this.setBlock(x - 1, y - 1, z - 2, this.kind);

			this.setBlock(x, y - 1, z + 2, this.kind);
			this.setBlock(x, y - 1, z - 2, this.kind);

			this.setBlock(x + 1, y - 1, z + 2, this.kind);
			this.setBlock(x + 1, y - 1, z - 2, this.kind);

			this.setBlock(x - 2, y - 1, z + 2, this.kind);
			this.setBlock(x - 2, y - 1, z + 1, this.kind);
			this.setBlock(x - 2, y - 1, z, this.kind);
			this.setBlock(x - 2, y - 1, z - 1, this.kind);
			this.setBlock(x - 2, y - 1, z - 2, this.kind);

			if (yaw >= 0 && yaw < 90) {
				signData = 10;
				signBlock = this.warzone.getWorld().getBlockAt(x, y, z).getRelative(Direction.NORTH(), 2).getRelative(Direction.WEST(), 2);

				if (style.equals(TeamSpawnStyle.BIG)) {
					// rim
					this.setBlock(x - 2, y, z - 1, this.kind);
					this.setBlock(x - 2, y, z - 2, this.kind);
					this.setBlock(x - 1, y, z - 2, this.kind);
					this.setBlock(x, y, z - 2, this.kind);
					this.setBlock(x + 1, y, z - 2, this.kind);
					this.setBlock(x + 2, y, z - 2, this.kind);
					this.setBlock(x + 2, y, z - 1, this.kind);
					this.setBlock(x + 2, y, z, this.kind);
					this.setBlock(x + 2, y, z + 1, this.kind);
					this.setBlock(x + 2, y, z + 2, this.kind);
					this.setBlock(x + 1, y, z + 2, this.kind);

					// tower
					this.setBlock(x, y + 1, z - 2, this.kind);
					this.setBlock(x + 1, y + 1, z - 2, this.kind);
					this.setBlock(x + 2, y + 1, z - 2, this.kind);
					this.setBlock(x + 2, y + 1, z - 1, this.kind);
					this.setBlock(x + 2, y + 1, z, this.kind);

					this.setBlock(x + 1, y + 2, z - 2, this.kind);
					this.setBlock(x + 2, y + 2, z - 2, this.kind);
					this.setBlock(x + 2, y + 2, z - 1, this.kind);

					this.setBlock(x + 2, y + 3, z - 2, this.kind);
				}
			} else if (yaw >= 90 && yaw <= 180) {
				signData = 14;
				signBlock = this.warzone.getWorld().getBlockAt(x, y, z).getRelative(Direction.NORTH(), 2).getRelative(Direction.EAST(), 2);
				if (style.equals(TeamSpawnStyle.BIG)) {
					// rim
					this.setBlock(x + 1, y, z - 2, this.kind);
					this.setBlock(x + 2, y, z - 2, this.kind);
					this.setBlock(x + 2, y, z - 1, this.kind);
					this.setBlock(x + 2, y, z, this.kind);
					this.setBlock(x + 2, y, z + 1, this.kind);
					this.setBlock(x + 2, y, z + 2, this.kind);
					this.setBlock(x + 1, y, z + 2, this.kind);
					this.setBlock(x, y, z + 2, this.kind);
					this.setBlock(x - 1, y, z + 2, this.kind);
					this.setBlock(x - 2, y, z + 2, this.kind);
					this.setBlock(x - 2, y, z + 1, this.kind);

					// tower
					this.setBlock(x + 2, y + 1, z, this.kind);
					this.setBlock(x + 2, y + 1, z + 1, this.kind);
					this.setBlock(x + 2, y + 1, z + 2, this.kind);
					this.setBlock(x + 1, y + 1, z + 2, this.kind);
					this.setBlock(x, y + 1, z + 2, this.kind);

					this.setBlock(x + 2, y + 2, z + 1, this.kind);
					this.setBlock(x + 2, y + 2, z + 2, this.kind);
					this.setBlock(x + 1, y + 2, z + 2, this.kind);

					this.setBlock(x + 2, y + 3, z + 2, this.kind);
				}
			} else if (yaw >= 180 && yaw < 270) {
				signData = 2;
				signBlock = this.warzone.getWorld().getBlockAt(x, y, z).getRelative(Direction.SOUTH(), 2).getRelative(Direction.EAST(), 2);
				if (style.equals(TeamSpawnStyle.BIG)) {
					// rim
					this.setBlock(x + 2, y, z + 1, this.kind);
					this.setBlock(x + 2, y, z + 2, this.kind);
					this.setBlock(x + 1, y, z + 2, this.kind);
					this.setBlock(x, y, z + 2, this.kind);
					this.setBlock(x - 1, y, z + 2, this.kind);
					this.setBlock(x - 2, y, z + 2, this.kind);
					this.setBlock(x - 2, y, z + 1, this.kind);
					this.setBlock(x - 2, y, z, this.kind);
					this.setBlock(x - 2, y, z - 1, this.kind);
					this.setBlock(x - 2, y, z - 2, this.kind);
					this.setBlock(x - 1, y, z - 2, this.kind);

					// tower
					this.setBlock(x, y + 1, z + 2, this.kind);
					this.setBlock(x - 1, y + 1, z + 2, this.kind);
					this.setBlock(x - 2, y + 1, z + 2, this.kind);
					this.setBlock(x - 2, y + 1, z + 1, this.kind);
					this.setBlock(x - 2, y + 1, z, this.kind);

					this.setBlock(x - 1, y + 2, z + 2, this.kind);
					this.setBlock(x - 2, y + 2, z + 2, this.kind);
					this.setBlock(x - 2, y + 2, z + 1, this.kind);

					this.setBlock(x - 2, y + 3, z + 2, this.kind);
				}
			} else if (yaw >= 270 && yaw <= 360) {
				signData = 6;
				signBlock = this.warzone.getWorld().getBlockAt(x, y, z).getRelative(Direction.SOUTH(), 2).getRelative(Direction.WEST(), 2);
				if (style.equals(TeamSpawnStyle.BIG)) {
					// rim
					this.setBlock(x - 1, y, z + 2, this.kind);
					this.setBlock(x - 2, y, z + 2, this.kind);
					this.setBlock(x - 2, y, z + 1, this.kind);
					this.setBlock(x - 2, y, z, this.kind);
					this.setBlock(x - 2, y, z - 1, this.kind);
					this.setBlock(x - 2, y, z - 2, this.kind);
					this.setBlock(x - 1, y, z - 2, this.kind);
					this.setBlock(x, y, z - 2, this.kind);
					this.setBlock(x + 1, y, z - 2, this.kind);
					this.setBlock(x + 2, y, z - 2, this.kind);
					this.setBlock(x + 2, y, z - 1, this.kind);

					// tower
					this.setBlock(x - 2, y + 1, z, this.kind);
					this.setBlock(x - 2, y + 1, z - 1, this.kind);
					this.setBlock(x - 2, y + 1, z - 2, this.kind);
					this.setBlock(x - 1, y + 1, z - 2, this.kind);
					this.setBlock(x, y + 1, z - 2, this.kind);

					this.setBlock(x - 2, y + 2, z - 1, this.kind);
					this.setBlock(x - 2, y + 2, z - 2, this.kind);
					this.setBlock(x - 1, y + 2, z - 2, this.kind);

					this.setBlock(x - 2, y + 3, z - 2, this.kind);
				}
			}
		}

		if (signBlock != null) {
			String[] lines = new String[4];
			lines[0] = "Team " + this.name;
			lines[1] = this.players.size() + "/" + this.getTeamConfig().resolveInt(TeamConfig.TEAMSIZE) + " players";
			lines[2] = this.points + "/" + this.getTeamConfig().resolveInt(TeamConfig.MAXSCORE)+ " pts";
			if (this.getTeamConfig().resolveInt(TeamConfig.LIFEPOOL) == -1) {
				lines[3] = "unlimited lives";
			} else {
				lines[3] = this.remainingLives + "/" + this.getTeamConfig().resolveInt(TeamConfig.LIFEPOOL) + " lives left";
			}

			SignHelper.setToSign(War.war, signBlock, (byte) signData, lines);
		}
		
		if (War.war.isSpoutServer()) {
			War.war.getSpoutDisplayer().updateStats(this.warzone);
		}
	}

	private void setBlock(int x, int y, int z, TeamKind kind) {
		Block block = this.warzone.getWorld().getBlockAt(x, y, z);
		block.setType(kind.getMaterial());
		block.setData(kind.getData());
	}

	public void addTeamSpawn(Location teamSpawn) {
		if (!this.teamSpawns.contains(teamSpawn)) {
			this.teamSpawns.add(teamSpawn);
		}
		// this resets the block to old state
		this.setSpawnVolume(teamSpawn, new Volume(name + teamSpawns.indexOf(teamSpawn), warzone.getWorld()));
		this.createSpawnVolume(teamSpawn);
		this.spawnVolumes.get(teamSpawn).saveBlocks();

		this.initializeTeamSpawn(teamSpawn);
	}

	public List<Location> getTeamSpawns() {
		return this.teamSpawns;
	}

	Random teamSpawnRandomizer = new Random();
	public Location getRandomSpawn() {
		return this.teamSpawns.get(teamSpawnRandomizer.nextInt(this.teamSpawns.size()));
	}

	public void addPlayer(Player player) {
		this.players.add(player);
		if (War.war.isTagServer()) {
			TagAPI.refreshPlayer(player);
		}
	}

	public List<Player> getPlayers() {
		return this.players;
	}
	
	public void teamcast(String message) {
		// by default a teamcast is a notification
		teamcast(message, true);
	}

	public void teamcast(String message, boolean isNotification) {
		for (Player player : this.players) {
			if (War.war.isSpoutServer()) {
				SpoutPlayer sp = SpoutManager.getPlayer(player);
				if (sp.isSpoutCraftEnabled() && isNotification) {
					// team notifications go to the top left for Spout players to lessen War spam in chat box
					War.war.getSpoutDisplayer().msg(sp, message);
				} else {
					War.war.msg(player, message);
				}
			} else {
				War.war.msg(player, message);
			}
		}
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public boolean removePlayer(String name) {
		Player thePlayer = null;
		for (Player player : this.players) {
			if (player.getName().equals(name)) {
				thePlayer = player;
			}
		}
		if (thePlayer != null) {
			this.players.remove(thePlayer);
			
			if (this.warzone.isFlagThief(thePlayer.getName())) {
				Team victim = this.warzone.getVictimTeamForFlagThief(thePlayer.getName());
				victim.getFlagVolume().resetBlocks();
				victim.initializeTeamFlag();
				this.warzone.removeFlagThief(thePlayer.getName());
				for (Team t : this.warzone.getTeams()) {
					t.teamcast("Team " + ChatColor.GREEN + victim.getName() + ChatColor.WHITE + " flag was returned.");
				}
			}
			
			if (this.warzone.isBombThief(thePlayer.getName())) {
				Bomb bomb = this.warzone.getBombForThief(thePlayer.getName());
				bomb.getVolume().resetBlocks();
				bomb.addBombBlocks();
				this.warzone.removeBombThief(thePlayer.getName());
				for (Team t : this.warzone.getTeams()) {
					t.teamcast("Bomb " + ChatColor.GREEN + bomb.getName() + ChatColor.WHITE  + " was returned.");
				}
			}
			
			if (this.warzone.isCakeThief(thePlayer.getName())) {
				Cake cake = this.warzone.getCakeForThief(thePlayer.getName());
				cake.getVolume().resetBlocks();
				cake.addCakeBlocks();
				this.warzone.removeCakeThief(thePlayer.getName());
				for (Team t : this.warzone.getTeams()) {
					t.teamcast("Cake " + ChatColor.GREEN + cake.getName() + ChatColor.WHITE  + " was returned.");
				}
			}
			if (War.war.isTagServer()) {
				TagAPI.refreshPlayer(thePlayer);
			}
			return true;
		}	
		
		return false;
	}

	public void setRemainingLives(int remainingLives) {
		this.remainingLives = remainingLives;
	}

	public int getRemainingLifes() {
		return this.remainingLives;
	}

	public void addPoint() {
		boolean atLeastOnePlayerOnTeam = this.players.size() != 0;
		boolean atLeastOnePlayerOnOtherTeam = false;
		for (Team team : this.warzone.getTeams()) {
			if (!team.getName().equals(this.getName()) && team.getPlayers().size() > 0) {
				atLeastOnePlayerOnOtherTeam = true;
			}
		}
		if (atLeastOnePlayerOnTeam && atLeastOnePlayerOnOtherTeam) {
			this.points++;
		} else if (!atLeastOnePlayerOnOtherTeam) {
			this.teamcast("Can't score until at least one player joins another team.");
		}
	}

	public int getPoints() {
		return this.points;
	}

	public Map<Location, Volume> getSpawnVolumes() {

		return this.spawnVolumes;
	}

	public void resetSign() {
		for (Entry<Location, Volume> spawnEntry : this.getSpawnVolumes().entrySet()) {
			spawnEntry.getValue().resetBlocks();
			this.initializeTeamSpawn(spawnEntry.getKey()); // reset everything instead of just sign
		}

		if (this.warzone.getLobby() != null) {
			this.warzone.getLobby().resetTeamGateSign(this);
		}
	}

	public void setSpawnVolume(Location spawnLocation, Volume volume) {
		this.spawnVolumes.put(spawnLocation, volume);
	}

	public void resetPoints() {
		this.points = 0;
	}

	public void setFlagVolume(Volume flagVolume) {
		this.flagVolume = flagVolume;
	}

	public Volume getFlagVolume() {
		return this.flagVolume;
	}

	private void setFlagVolume() {
		if (this.flagVolume == null) {
			this.flagVolume = new Volume(this.getName() + "flag", this.warzone.getWorld());
		}
		if (this.flagVolume.isSaved()) {
			this.flagVolume.resetBlocks();
		}
		int x = this.teamFlag.getBlockX();
		int y = this.teamFlag.getBlockY();
		int z = this.teamFlag.getBlockZ();
		this.flagVolume.setCornerOne(this.warzone.getWorld().getBlockAt(x - 1, y - 1, z - 1));
		this.flagVolume.setCornerTwo(this.warzone.getWorld().getBlockAt(x + 1, y + 3, z + 1));
	}

	@SuppressWarnings("unused")
	public void initializeTeamFlag() {
		// make air (old two-high above floor)
		Volume airGap = new Volume("airgap", this.warzone.getWorld());
		airGap.setCornerOne(new BlockInfo(
				this.flagVolume.getCornerOne().getX(), 
				this.flagVolume.getCornerOne().getY() + 1, 
				this.flagVolume.getCornerOne().getZ(),
				0,
				(byte)0));
		airGap.setCornerTwo(new BlockInfo(
				this.flagVolume.getCornerTwo().getX(), 
				this.flagVolume.getCornerOne().getY() + 2, 
				this.flagVolume.getCornerTwo().getZ(),
				0,
				(byte)0));
		airGap.setToMaterial(Material.AIR);

		// Set the flag blocks
		int x = this.teamFlag.getBlockX();
		int y = this.teamFlag.getBlockY();
		int z = this.teamFlag.getBlockZ();
		
		Material main = Material.getMaterial(this.warzone.getWarzoneMaterials().getMainId());
		byte mainData = this.warzone.getWarzoneMaterials().getMainData();
		Material stand = Material.getMaterial(this.warzone.getWarzoneMaterials().getStandId());
		byte standData = this.warzone.getWarzoneMaterials().getStandData();
		Material light = Material.getMaterial(this.warzone.getWarzoneMaterials().getLightId());
		byte lightData = this.warzone.getWarzoneMaterials().getLightData();

		// first ring
		Block current = this.warzone.getWorld().getBlockAt(x + 1, y - 1, z + 1);
		current.setType(main);
		current.setData(mainData);
		current = this.warzone.getWorld().getBlockAt(x + 1, y - 1, z);
		current.setType(main);
		current.setData(mainData);
		current = this.warzone.getWorld().getBlockAt(x + 1, y - 1, z - 1);
		current.setType(main);
		current.setData(mainData);
		current = this.warzone.getWorld().getBlockAt(x, y - 1, z + 1);
		current.setType(main);
		current.setData(mainData);
		current = this.warzone.getWorld().getBlockAt(x, y - 1, z);
		current.setType(light);
		current.setData(lightData);
		current = this.warzone.getWorld().getBlockAt(x, y - 1, z - 1);
		current.setType(main);
		current.setData(mainData);
		current = this.warzone.getWorld().getBlockAt(x - 1, y - 1, z + 1);
		current.setType(main);
		current.setData(mainData);
		current = this.warzone.getWorld().getBlockAt(x - 1, y - 1, z);
		current.setType(main);
		current.setData(mainData);
		current = this.warzone.getWorld().getBlockAt(x - 1, y - 1, z - 1);
		current.setType(main);
		current.setData(mainData);

		// flag
		this.warzone.getWorld().getBlockAt(x, y + 1, z).setType(this.kind.getMaterial());
		this.warzone.getWorld().getBlockAt(x, y + 1, z).setData(this.kind.getData());

		// Flag post using Orientation
		int yaw = 0;
		if (this.teamFlag.getYaw() >= 0) {
			yaw = (int) (this.teamFlag.getYaw() % 360);
		} else {
			yaw = (int) (360 + (this.teamFlag.getYaw() % 360));
		}
		BlockFace facing = null;
		BlockFace opposite = null;
		if ((yaw >= 0 && yaw < 45) || (yaw >= 315 && yaw <= 360)) {
			facing = Direction.WEST();
			opposite = Direction.EAST();
			current = this.warzone.getWorld().getBlockAt(x, y, z - 1);
			current.setType(stand);
			current.setData(standData);
			current = this.warzone.getWorld().getBlockAt(x, y + 1, z - 1);
			current.setType(stand);
			current.setData(standData);
		} else if (yaw >= 45 && yaw < 135) {
			facing = Direction.NORTH();
			opposite = Direction.SOUTH();
			current = this.warzone.getWorld().getBlockAt(x + 1, y, z);
			current.setType(stand);
			current.setData(standData);
			current = this.warzone.getWorld().getBlockAt(x + 1, y + 1, z);
			current.setType(stand);
			current.setData(standData);
		} else if (yaw >= 135 && yaw < 225) {
			facing = Direction.EAST();
			opposite = Direction.WEST();
			current = this.warzone.getWorld().getBlockAt(x, y, z + 1);
			current.setType(stand);
			current.setData(standData);
			current = this.warzone.getWorld().getBlockAt(x, y + 1, z + 1);
			current.setType(stand);
			current.setData(standData);;
		} else if (yaw >= 225 && yaw < 315) {
			facing = Direction.SOUTH();
			opposite = Direction.NORTH();
			current = this.warzone.getWorld().getBlockAt(x - 1, y, z);
			current.setType(stand);
			current.setData(standData);
			current = this.warzone.getWorld().getBlockAt(x - 1, y + 1, z);
			current.setType(stand);
			current.setData(standData);
		}
	}

	public void setTeamFlag(Location teamFlag) {
		this.teamFlag = teamFlag;

		// this resets the block to old state
		this.setFlagVolume();
		this.getFlagVolume().saveBlocks();

		this.initializeTeamFlag();
	}
	
	public boolean isTeamFlagBlock(Block block) {
		if (this.teamFlag != null) {
			int flagX = this.teamFlag.getBlockX();
			int flagY = this.teamFlag.getBlockY() + 1;
			int flagZ = this.teamFlag.getBlockZ();
			if (block.getX() == flagX && block.getY() == flagY && block.getZ() == flagZ) {
				return true;
			}
		}
		return false;
	}

	public Location getTeamFlag() {
		return this.teamFlag;
	}
	
	public void deleteTeamFlag() {
		this.getFlagVolume().resetBlocks();
		this.setFlagVolume(null);
		this.teamFlag = null;
		
		// remove volume file
		String filePath = War.war.getDataFolder().getPath() + "/dat/warzone-" + this.warzone.getName() + "/volume-" + this.getName() + "flag.dat";
		if (!new File(filePath).delete()) {			
			War.war.log("Failed to delete file " + filePath, Level.WARNING);
		}
	}

	public InventoryBag getInventories() {
		return this.inventories ;
	}

	public TeamConfigBag getTeamConfig() {
		return this.teamConfig;
	}

	/**
	 * Check if any team spawns contain a certain location.
	 *
	 * @param loc Location to check if contained by a spawn.
	 * @return true if loc is part of a spawn volume, false otherwise.
	 */
	public boolean isSpawnLocation(Location loc) {
		for (Volume spawnVolume : this.spawnVolumes.values()) {
			if (spawnVolume.contains(loc)) {
				return true;
			}
		}
		return false;
	}
}
