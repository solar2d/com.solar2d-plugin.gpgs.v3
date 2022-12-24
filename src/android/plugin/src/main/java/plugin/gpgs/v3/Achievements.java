package plugin.gpgs.v3;

import android.content.Intent;
import androidx.annotation.NonNull;

import com.ansca.corona.CoronaLua;
import com.google.android.gms.games.AchievementsClient;
import com.google.android.gms.games.AnnotatedData;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.achievement.Achievement;
import com.google.android.gms.games.achievement.AchievementBuffer;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.LuaState;

import java.util.Hashtable;

import plugin.gpgs.v3.LuaUtils.Scheme;
import plugin.gpgs.v3.LuaUtils.Table;

class Achievements {

	Achievements(LuaState L) {
		L.newTable(); // plugin.gpgs.v2.achievements
		Utils.setJavaFunctionAsField(L, "load", new JavaFunction() {
			@Override
			public int invoke(LuaState L) {
				return load(L, false);
			}
		});
		Utils.setJavaFunctionAsField(L, "increment", new JavaFunction() {
			@Override
			public int invoke(LuaState L) {
				return increment(L);
			}
		});
		Utils.setJavaFunctionAsField(L, "reveal", new JavaFunction() {
			@Override
			public int invoke(LuaState L) {
				return reveal(L);
			}
		});
		Utils.setJavaFunctionAsField(L, "setSteps", new JavaFunction() {
			@Override
			public int invoke(LuaState L) {
				return setSteps(L);
			}
		});
		Utils.setJavaFunctionAsField(L, "unlock", new JavaFunction() {
			@Override
			public int invoke(LuaState L) {
				return unlock(L, false);
			}
		});
		Utils.setJavaFunctionAsField(L, "show", new JavaFunction() {
			@Override
			public int invoke(LuaState L) {
				return show(L);
			}
		});
		L.setField(-2, "achievements");
	}

	/**
	 * Returns AchievementsClient instance
	 */
	private AchievementsClient getClient(){

		return PlayGames.getAchievementsClient(Connector.getActivity());
	}

	//region Lua functions
	// plugin.gpgs.v2.achievements.load(params)
	// params.reload
	// params.listener
	private int load(LuaState L, boolean isLegacy) {
		Utils.debugLog("achievements.load()");
		final String name = !isLegacy ? "load" : "loadAchievements";
		if (!L.isTable(1)){
			Utils.errorLog(name + " must receive table parameter, got "+ L.typeName(1));
			return 0;
		}
		if (Utils.checkConnection()) {
			Scheme scheme = new Scheme()
				.bool("reload")
				.listener("listener", name);

			Table params = new Table(L, 1).parse(scheme);
			Boolean reload = params.getBoolean("reload", false);
			final Integer luaListener = params.getListener("listener");
			Task<AnnotatedData<AchievementBuffer>> request = getClient().load(reload);
			request.addOnCompleteListener(new OnCompleteListener<AnnotatedData<AchievementBuffer>>() {
				@Override
				public void onComplete(@NonNull Task<AnnotatedData<AchievementBuffer>> task) {
					Hashtable<Object, Object> event = Utils.newEvent(name);

					Hashtable<Object, Object> achievements = new Hashtable<>();
					int i = 1;
					if (task.isSuccessful()) {
						AnnotatedData<AchievementBuffer> achievementBuffer = task.getResult();//.getAchievements();
						if (achievementBuffer.get() != null) {
							for (Achievement a : achievementBuffer.get()) {
								Hashtable<Object, Object> achievement = new Hashtable<>();

								achievement.put("id", a.getAchievementId());
								achievement.put("description", a.getDescription());
								achievement.put("lastUpdatedTimestamp", String.valueOf(a.getLastUpdatedTimestamp()));
								achievement.put("name", a.getName());
								achievement.put("revealedImageUri", a.getRevealedImageUri().toString());
								achievement.put("unlockedImageUri", a.getUnlockedImageUri().toString());
								achievement.put("xp", a.getXpValue());

								String state = "";
								switch (a.getState()) {
									case Achievement.STATE_HIDDEN:
										state = "hidden";
										break;
									case Achievement.STATE_REVEALED:
										state = "revealed";
										break;
									case Achievement.STATE_UNLOCKED:
										state = "unlocked";
										break;
								}
								achievement.put("state", state);

								boolean isIncremental = a.getType() == Achievement.TYPE_INCREMENTAL;
								if (isIncremental) {
									achievement.put("currentSteps", a.getCurrentSteps());
									achievement.put("totalSteps", a.getTotalSteps());
									Utils.put(achievement, "formattedCurrentSteps", a.getFormattedCurrentSteps());
									achievement.put("formattedTotalSteps", a.getFormattedTotalSteps());
								}
								achievement.put("isIncremental", isIncremental);

								achievements.put(i++, achievement);
							}
							achievementBuffer.get().release();
							event.put("achievements", achievements);
							Utils.dispatchEvent(luaListener, event, true);
						}
					}
					else {
						event.put("isError", true);
						event.put("errorCode", Utils.getErrorCode(task.getException()));
						event.put("errorMessage", task.getException().getLocalizedMessage());
					}
				}
			});
		}
		return 0;
	}

	// plugin.gpgs.v2.achievements.increment(params)
	// params.achievementId *
	// params.steps *
	// params.listener
	private int increment(LuaState L) {
		Utils.debugLog("achievements.increment()");
		String name = "increment";
		if (!L.isTable(1)){
			Utils.errorLog(name + " must receive table parameter, got "+ L.typeName(1));
			return 0;
		}
		if (Utils.checkConnection()) {
			Scheme scheme = new Scheme()
				.string("achievementId")
				.number("steps")
				.listener("listener", name);

			Table params = new Table(L, 1).parse(scheme);
			String achievementId = params.getStringNotNull("achievementId");
			int steps = params.getLong("steps", 1).intValue();
			Integer luaListener = params.getListener("listener");
			if (luaListener != null) {
				getClient().incrementImmediate(achievementId, steps).addOnCompleteListener(new UpdateAchievementOnCompleteListener<Boolean>(name, luaListener, achievementId));
			} else {
				getClient().increment(achievementId, steps);
			}
		}
		return 0;
	}

	// plugin.gpgs.v2.achievements.reveal(params)
	// params.achievementId *
	// params.listener
	private int reveal(LuaState L) {
		Utils.debugLog("achievements.reveal()");
		String name = "reveal";
		if (!L.isTable(1)){
			Utils.errorLog(name + " must receive table parameter, got "+ L.typeName(1));
			return 0;
		}
		if (Utils.checkConnection()) {
			Scheme scheme = new Scheme()
				.string("achievementId")
				.listener("listener", name);

			Table params = new Table(L, 1).parse(scheme);
			String achievementId = params.getStringNotNull("achievementId");
			Integer luaListener = params.getListener("listener");
			if (luaListener != null) {
				getClient().revealImmediate(achievementId).addOnCompleteListener(new UpdateAchievementOnCompleteListener<Void>(name, luaListener, achievementId));
			} else {
				getClient().reveal(achievementId);
			}
		}
		return 0;
	}

	// plugin.gpgs.v2.achievements.setSteps(params)
	// params.achievementId *
	// params.steps *
	// params.listener
	private int setSteps(LuaState L) {
		Utils.debugLog("achievements.setSteps()");
		String name = "setSteps";
		if (!L.isTable(1)){
			Utils.errorLog(name + " must receive table parameter, got "+ L.typeName(1));
			return 0;
		}
		if (Utils.checkConnection()) {
			Scheme scheme = new Scheme()
				.string("achievementId")
				.number("steps")
				.listener("listener", name);

			Table params = new Table(L, 1).parse(scheme);
			String achievementId = params.getStringNotNull("achievementId");
			int steps = params.getLongNotNull("steps").intValue();
			Integer luaListener = params.getListener("listener");
			if (luaListener != null) {
				getClient().setStepsImmediate(achievementId, steps).addOnCompleteListener(new UpdateAchievementOnCompleteListener<Boolean>(name, luaListener, achievementId));
			} else {
				getClient().setSteps(achievementId, steps);
			}
		}
		return 0;
	}

	// plugin.gpgs.v2.achievements.unlock(params)
	// params.achievementId *
	// params.listener
	private int unlock(LuaState L, boolean isLegacy) {
		Utils.debugLog("achievements.unlock()");
		String name = "unlock";
		if (!L.isTable(1)){
			Utils.errorLog(name + " must receive table parameter, got "+ L.typeName(1));
			return 0;
		}
		if (Utils.checkConnection()) {
			Scheme scheme = new Scheme()
				.string("achievementId")
				.listener("listener", name);

			Table params = new Table(L, 1).parse(scheme);
			String achievementId = params.getStringNotNull("achievementId");
			Integer luaListener = params.getListener("listener");
			if (luaListener != null) {
				getClient().unlockImmediate(achievementId).addOnCompleteListener(new UpdateAchievementOnCompleteListener<Void>(name, luaListener, achievementId));
			} else {
				getClient().unlock(achievementId);
			}
		}
		return 0;
	}

	// plugin.gpgs.v2.achievements.show(listener)
	int show(final LuaState L) {
		Utils.debugLog("achievements.show()");
		final String name = "show";
		Integer luaListener = null;
		int listenerIndex = 1;
		if (CoronaLua.isListener(L, listenerIndex, name)) {
			luaListener = CoronaLua.newRef(L, listenerIndex);
		}
		else {
			if (L.isTable(1)) {
				Utils.errorLog(name + " must receive listener parameter or null, got " + L.typeName(1));
				return 0;
			}
		}
		final Integer listener = luaListener;
		if (Utils.checkConnection()) {
			getClient().getAchievementsIntent().addOnCompleteListener(new OnCompleteListener<Intent>() {
				@Override
				public void onComplete(@NonNull Task<Intent> task) {
					if (task.isSuccessful()) {
						Utils.startActivity(task.getResult(), name, listener);
					}
				}
			});
		}
		return 0;
	}
	//endregion

	private class UpdateAchievementOnCompleteListener<T> implements OnCompleteListener<T>{

		private String name;
		private int luaListener;
		private String achievemntId;

		UpdateAchievementOnCompleteListener(String name, int luaListener, String achievemntId) {
			this.name = name;
			this.luaListener = luaListener;
			this.achievemntId = achievemntId;
		}

		@Override
		public void onComplete(@NonNull Task<T> task) {
			Hashtable<Object, Object> event = Utils.newEvent(name);
			boolean isError = !task.isSuccessful();
			event.put("isError", isError);
			if (isError) {
				event.put("errorCode", Utils.getErrorCode(task.getException()));
				event.put("errorMessage", task.getException().getLocalizedMessage());
			}
			event.put("achievementId", achievemntId);
			Utils.dispatchEvent(luaListener, event, true);
		}
	}

	//region Legacy compatibility
	private String[] actions = new String[]{"loadAchievements", "loadAchievementDescriptions", "unlockAchievement"};

	boolean hasAction(String action) {
		for (String a : actions) {
			if (action.equals(a)) {
				return true;
			}
		}
		return false;
	}

	// gameNetwork.request(action, ...)
	int request(LuaState L, String action) {
		switch (action) {
			case "loadAchievements":
			case "loadAchievementDescriptions":
				L.getField(1, "listener");
				L.remove(1);

				// New params
				L.newTable(0, 2);

				L.pushBoolean(true);
				L.setField(-2, "reload");

				L.pushValue(1);
				L.setField(-2, "listener");

				L.remove(1); // remove original listener
				return load(L, true);
			case "unlockAchievement":
				L.getField(1, "achievement");

				L.getField(-1, "identifier");
				String identifier = L.toString(-1);
				L.pop(2);

				L.getField(1, "listener");
				L.remove(1);

				// New params
				L.newTable(0, 2);

				L.pushString(identifier);
				L.setField(-2, "achievementId");

				L.pushValue(1);
				L.setField(-2, "listener");

				L.remove(1); // remove original listener
				return unlock(L, true);
			default:
				return 0;
		}
	}
}
