/*
 * PermissionsEx - Permissions plugin for Bukkit
 * Copyright (C) 2011 t3hk0d3 http://www.tehkode.ru
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package ru.tehkode.permissions.backends;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import ru.tehkode.permissions.PermissionBackend;
import ru.tehkode.permissions.PermissionGroup;
import ru.tehkode.permissions.PermissionManager;
import ru.tehkode.permissions.PermissionUser;
import ru.tehkode.permissions.backends.sql.SQLConnection;
import ru.tehkode.permissions.backends.sql.SQLEntity;
import ru.tehkode.permissions.backends.sql.SQLGroup;
import ru.tehkode.permissions.backends.sql.SQLUser;
import ru.tehkode.permissions.bukkit.PermissionsEx;
import ru.tehkode.permissions.exceptions.PermissionBackendException;
import ru.tehkode.utils.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.logging.Logger;

/**
 * @author code
 */
public class SQLBackend extends PermissionBackend {

	protected Map<String, String[]> worldInheritanceCache = new HashMap<String, String[]>();
	private Map<String, Object> tableNames;
	private ThreadLocal<SQLConnection> conn;

	public SQLBackend(PermissionManager manager, Configuration config) {
		super(manager, config);
	}

	@Override
	public void initialize() throws PermissionBackendException {
		final String dbUri = config.getString("permissions.backends.sql.uri", "");
		final String dbUser = config.getString("permissions.backends.sql.user", "");
		final String dbPassword = config.getString("permissions.backends.sql.password", "");

		if (dbUri == null || dbUri.isEmpty()) {
			config.set("permissions.backends.sql.uri", "mysql://localhost/exampledb");
			config.set("permissions.backends.sql.user", "databaseuser");
			config.set("permissions.backends.sql.password", "databasepassword");

			throw new PermissionBackendException("SQL connection is not configured, see config.yml");
		}

		conn = new ThreadLocal<SQLConnection>() {
			@Override
			public SQLConnection initialValue() {
				return new SQLConnection(dbUri, dbUser, dbPassword, SQLBackend.this);
			}
		};

		try {
			getSQL(); // Test connection
		} catch (Exception e) {
			if (e.getCause() != null && e.getCause() instanceof Exception) {
				e = (Exception) e.getCause();
			}
			throw new PermissionBackendException(e);
		}


		Logger.getLogger("PermissionsEx").info("[PermissionsEx-SQL] Successfully connected to database");

		this.setupAliases(config);
		this.deployTables();
	}

	public SQLConnection getSQL() {
		return conn.get();
	}

	public String getTableName(String identifier) {
		Map<String, Object> tableNames = this.tableNames;
		if (tableNames == null) {
			return identifier;
		}

		Object ret = tableNames.get(identifier);
		if (ret == null) {
			return identifier;
		}
		return ret.toString();
	}

	@Override
	public PermissionUser getUser(String name) {
		return new SQLUser(name, manager, this);
	}

	@Override
	public PermissionGroup getGroup(String name) {
		return new SQLGroup(name, manager, this);
	}

	@Override
	public PermissionGroup getDefaultGroup(String worldName) {
		try {
			ResultSet result;

			if (worldName == null) {
				result = getSQL().prepAndBind("SELECT `name` FROM `{permissions_entity}` WHERE `type` = ? AND `default` = 1 LIMIT 1", SQLEntity.Type.GROUP.ordinal()).executeQuery();

				if (!result.next()) {
					throw new RuntimeException("There is no default group set, this is a serious issue");
				}
			} else {
				result = this.getSQL().prepAndBind("SELECT `name` FROM `{permissions}` WHERE `permission` = 'default' AND `value` = 'true' AND `type` = ? AND `world` = ?",
						SQLEntity.Type.GROUP.ordinal(), worldName).executeQuery();

				if (!result.next()) {
					return null;
				}
			}

			return this.manager.getGroup(result.getString("name"));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void setDefaultGroup(PermissionGroup group, String worldName) {
		try {
			if (worldName == null) {
				// Reset default flag
				this.getSQL().prepAndBind("UPDATE `{permissions_entity}` SET `default` = 0 WHERE `type` = ? AND `default` = 1 LIMIT 1", SQLEntity.Type.GROUP.ordinal()).execute();
				// Set default flag
				this.getSQL().prepAndBind("UPDATE `{permissions_entity}` SET `default` = 1 WHERE `type` = ? AND `name` = ? LIMIT 1", SQLEntity.Type.GROUP.ordinal(), group.getName()).execute();
			} else {
				this.getSQL().prepAndBind("DELETE FROM `{permissions}` WHERE `permission` = 'default' AND `world` = ? AND `type` = ?", worldName, SQLEntity.Type.GROUP.ordinal()).execute();
				this.getSQL().prepAndBind("INSERT INTO `{permissions}` (`name`, `permission`, `type`, `world`, `value`) VALUES (?, 'default', ?, ?, 'true')",
						group.getName(), SQLEntity.Type.GROUP.ordinal(), worldName).execute();
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to set default group", e);
		}
	}

	@Override
	public PermissionGroup[] getGroups() {
		Set<String> groupNames = SQLEntity.getEntitiesNames(getSQL(), SQLEntity.Type.GROUP, false);
		List<PermissionGroup> groups = new LinkedList<PermissionGroup>();

		for (String groupName : groupNames) {
			groups.add(this.manager.getGroup(groupName));
		}

		Collections.sort(groups);

		return groups.toArray(new PermissionGroup[0]);
	}

	@Override
	public PermissionUser[] getRegisteredUsers() {
		Set<String> userNames = SQLEntity.getEntitiesNames(getSQL(), SQLEntity.Type.USER, false);
		PermissionUser[] users = new PermissionUser[userNames.size()];

		int index = 0;
		for (String groupName : userNames) {
			users[index++] = this.manager.getUser(groupName);
		}

		return users;
	}


	@Override
	public Collection<String> getRegisteredGroupNames() {
		return SQLEntity.getEntitiesNames(getSQL(), SQLEntity.Type.GROUP, false);
	}

	@Override
	public Collection<String> getRegisteredUserNames() {
		return SQLEntity.getEntitiesNames(getSQL(), SQLEntity.Type.USER, false);
	}

	protected final void setupAliases(Configuration config) {
		ConfigurationSection aliases = config.getConfigurationSection("permissions.backends.sql.aliases");

		if (aliases == null) {
			return;
		}

		tableNames = aliases.getValues(false);
	}

	private void executeStream(SQLConnection conn, InputStream str) throws SQLException, IOException {
		String deploySQL = StringUtils.readStream(str);


		Statement s = conn.getStatement();

		for (String sqlQuery : deploySQL.trim().split(";")) {
			sqlQuery = sqlQuery.trim();
			if (sqlQuery.isEmpty()) {
				continue;
			}

			sqlQuery = conn.expandQuery(sqlQuery + ";");

			s.addBatch(sqlQuery);
		}
		s.executeBatch();
	}

	protected final void deployTables() throws PermissionBackendException {
		try {
			if (this.getSQL().hasTable("permissions")) {
				return;
			}
			InputStream databaseDumpStream = getClass().getResourceAsStream("/sql/" + getSQL().getDriver() + ".sql");

			if (databaseDumpStream == null) {
				throw new Exception("Can't find appropriate database dump for used database (" + getSQL().getDriver() + "). Is it bundled?");
			}

			Logger.getLogger("PermissionsEx").info("Deploying default database scheme");

			executeStream(getSQL(), databaseDumpStream);

			PermissionGroup defGroup = getGroup("default");
			String deployFile = config.getString("permissions.backends.sql.deploy", "");
			if (deployFile.length() > 0) {
				final File deploy = new File(PermissionsEx.getPlugin().getDataFolder(), deployFile);
				if (!deploy.exists()) {
					throw new Exception("Permissions deploy file for SQL does not exist!");
				}
				executeStream(getSQL(), new FileInputStream(deploy));
				config.set("permissions.backends.sql.deploy", null);

			} else {
				defGroup.addPermission("modifyworld.*");
				setDefaultGroup(defGroup, null);
			}

			Logger.getLogger("PermissionsEx").info("Database scheme deploying complete.");

		} catch (Exception e) {
			throw new PermissionBackendException("Deploying of default data failed. Please initialize database manually using " + getSQL().getDriver() + ".sql", e);
		}
	}

	private static String blankIfNull(String in) {
		return in == null ? "" : in;
	}

	@Override
	public void dumpData(OutputStreamWriter writer) throws IOException {
		// Users
		for (PermissionUser user : this.manager.getUsers()) {
			writer.append("/* User ").append(user.getName()).append(" */\n");
			// Basic info (Prefix/Suffix)
			writer.append("INSERT INTO `{permissions_entity}` ( `name`, `type`, `prefix`, `suffix` ) VALUES ( '")
					.append(user.getName())
					.append("', 1, '")
					.append(blankIfNull(user.getOwnPrefix()))
					.append("','")
					.append(blankIfNull(user.getOwnSuffix()))
					.append("' );\n");

			// Inheritance
			for (String group : user.getGroupsNames()) {
				writer.append("INSERT INTO `{permissions_inheritance}` ( `child`, `parent`, `type` ) VALUES ( '")
						.append(user.getName())
						.append("', '")
						.append(group)
						.append("',  1);\n");
			}

			// Permissions
			for (Map.Entry<String, String[]> entry : user.getAllPermissions().entrySet()) {
				for (String permission : entry.getValue()) {
					String world = entry.getKey();

					if (world == null) {
						world = "";
					}

					writer.append("INSERT INTO `{permissions}` ( `name`, `type`, `permission`, `world`, `value` ) VALUES ('")
							.append(user.getName())
							.append("', 1, '")
							.append(permission)
							.append("', '")
							.append(world)
							.append("', ''); \n");
				}
			}

			for (String world : user.getWorlds()) {
				if (world == null) continue;

				final String worldPrefix = user.getOwnPrefix(world);
				final String worldSuffix = user.getOwnSuffix(world);

				if (worldPrefix != null && !worldPrefix.isEmpty()) {
					writer.append("INSERT INTO `{permissions}` (`name`, `type`, `permission`, `world`, `value`) VALUES ('")
							.append(user.getName())
							.append("', 1, 'prefix', '")
							.append(world).append("', '")
							.append(worldPrefix)
							.append("');\n");
				}
				if (worldSuffix != null && !worldSuffix.isEmpty()) {
					writer.append("INSERT INTO `{permissions}` (`name`, `type`, `permission`, `world`, `value`) VALUES ('")
							.append(user.getName())
							.append("', 1, 'suffix', '")
							.append(world)
							.append("', '")
							.append(worldSuffix)
							.append("');\n");
				}
			}

			// Options
			for (Map.Entry<String, Map<String, String>> entry : user.getAllOptions().entrySet()) {
				for (Map.Entry<String, String> option : entry.getValue().entrySet()) {
					String value = option.getValue().replace("'", "\\'");
					String world = entry.getKey();

					if (world == null) {
						world = "";
					}

					writer.append("INSERT INTO `{permissions}` ( `name`, `type`, `permission`, `world`, `value` ) VALUES ('")
							.append(user.getName())
							.append("', 1, '")
							.append(option.getKey())
							.append("', '")
							.append(world)
							.append("', '")
							.append(value)
							.append("' );\n");
				}
			}
		}

		PermissionGroup defaultGroup = manager.getDefaultGroup();

		// Groups
		for (PermissionGroup group : this.manager.getGroups()) {
			writer.append("/* Group ").append(group.getName()).append(" */\n");
			// Basic info (Prefix/Suffix)
			writer.append("INSERT INTO `{permissions_entity}` ( `name`, `type`, `prefix`, `suffix`, `default` ) VALUES ( '")
					.append(group.getName())
					.append("', 0, '")
					.append(blankIfNull(group.getOwnPrefix()))
					.append("','")
					.append(blankIfNull(group.getOwnSuffix()))
					.append("', ")
					.append(group.equals(defaultGroup) ? "1" : "0")
					.append(" );\n");

			for (String world : group.getWorlds()) {
				if (world == null) continue;

				final String worldPrefix = group.getOwnPrefix(world);
				final String worldSuffix = group.getOwnSuffix(world);

				if (worldPrefix != null && !worldPrefix.isEmpty()) {
					writer.append("INSERT INTO `{permissions}` (`name`, `type`, `permission`, `world`, `value`) VALUES ('")
							.append(group.getName())
							.append("', 0, 'prefix', '")
							.append(world).append("', '")
							.append(worldPrefix)
							.append("');\n");
				}
				if (worldSuffix != null && !worldSuffix.isEmpty()) {
					writer.append("INSERT INTO `{permissions}` (`name`, `type`, `permission`, `world`, `value`) VALUES ('")
							.append(group.getName())
							.append("', 0, 'suffix', '")
							.append(world)
							.append("', '")
							.append(worldSuffix)
							.append("');\n");
				}
			}

			// Inheritance
			for (String parent : group.getParentGroupsNames()) {
				writer.append("INSERT INTO `{permissions_inheritance}` ( `child`, `parent`, `type` ) VALUES ( '")
						.append(group.getName())
						.append("', '")
						.append(parent)
						.append("',  0);\n");
			}

			// Permissions
			for (Map.Entry<String, String[]> entry : group.getAllPermissions().entrySet()) {
				for (String permission : entry.getValue()) {
					String world = entry.getKey();

					if (world == null) {
						world = "";
					}

					writer.append("INSERT INTO `{permissions}` ( `name`, `type`, `permission`, `world`, `value`) VALUES ('")
							.append(group.getName())
							.append("', 0, '")
							.append(permission)
							.append("', '")
							.append(world)
							.append("', '');\n");
				}
			}

			// Options
			for (Map.Entry<String, Map<String, String>> entry : group.getAllOptions().entrySet()) {
				for (Map.Entry<String, String> option : entry.getValue().entrySet()) {
					String value = option.getValue().replace("'", "\\'");
					String world = entry.getKey();

					if (world == null) {
						world = "";
					}

					writer.append("INSERT INTO `{permissions}` ( `name`, `type`, `permission`, `world`, `value` ) VALUES ('")
							.append(group.getName())
							.append("', 0, '")
							.append(option.getKey())
							.append("', '")
							.append(world)
							.append("', '")
							.append(value)
							.append("' );\n");
				}
			}
		}

		// World-inheritance
		writer.append("/* World Inheritance */\n");
		for (World world : Bukkit.getServer().getWorlds()) {
			String[] parentWorlds = manager.getWorldInheritance(world.getName());
			if (parentWorlds.length == 0) {
				continue;
			}

			for (String parentWorld : parentWorlds) {
				writer.append("INSERT INTO `{permissions_inheritance}` ( `child`, `parent`, `type` ) VALUES ( '")
						.append(world.getName())
						.append("', '")
						.append(parentWorld)
						.append("',  2);\n");
			}
		}


		writer.flush();
	}

	@Override
	public String[] getWorldInheritance(String world) {
		if (world == null || world.isEmpty()) {
			return new String[0];
		}

		if (!worldInheritanceCache.containsKey(world)) {
			try {
				ResultSet result = this.getSQL().prepAndBind("SELECT `parent` FROM `{permissions_inheritance}` WHERE `child` = ? AND `type` = 2;", world).executeQuery();
				LinkedList<String> worldParents = new LinkedList<String>();

				while (result.next()) {
					worldParents.add(result.getString("parent"));
				}

				this.worldInheritanceCache.put(world, worldParents.toArray(new String[0]));
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		return worldInheritanceCache.get(world);
	}

	@Override
	public void setWorldInheritance(String worldName, String[] parentWorlds) {
		if (worldName == null || worldName.isEmpty()) {
			return;
		}

		try {
			this.getSQL().prepAndBind("DELETE FROM `{permissions_inheritance}` WHERE `child` = ? AND `type` = 2", worldName).execute();

			PreparedStatement statement = this.getSQL().prepAndBind("INSERT INTO `{permissions_inheritance}` (`child`, `parent`, `type`) VALUES (?, ?, 2)", worldName, "toset");
			for (String parentWorld : parentWorlds) {
				statement.setString(2, parentWorld);
				statement.addBatch();
			}
			statement.executeBatch();

			this.worldInheritanceCache.put(worldName, parentWorlds);

		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void reload() {
		worldInheritanceCache.clear();
	}
}
