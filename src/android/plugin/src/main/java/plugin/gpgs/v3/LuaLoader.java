package plugin.gpgs.v3;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.view.Gravity;

import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaRuntimeTaskDispatcher;
import com.ansca.corona.permissions.PermissionState;
import com.ansca.corona.permissions.PermissionsServices;
import com.ansca.corona.permissions.PermissionsSettings;
import com.google.android.gms.common.images.ImageManager;
import com.google.android.gms.games.AnnotatedData;
import com.google.android.gms.games.Game;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesClient;
import com.google.android.gms.games.GamesSignInClient;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.PlayGamesSdk;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.LuaState;
import com.naef.jnlua.LuaType;
import com.naef.jnlua.NamedJavaFunction;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Hashtable;

import plugin.gpgs.v3.LuaUtils.Scheme;
import plugin.gpgs.v3.LuaUtils.Table;

// For better readability: Android Studio -> Preferences -> Editor -> General -> Code Folding -> Check "Custom folding regions"
@SuppressWarnings("unused")
public class LuaLoader implements JavaFunction, Connector.SignInListener {
	private Connector connector;
	private Achievements achievements;
	private Leaderboards leaderboards;
	private Players players;
	private Multiplayer multiplayer;
	private int luaLoginListener = CoronaLua.REFNIL;
	private boolean isLoginLegacy = false;
	private ImageManager imageManager;

	//region Lua functions
	// require('plugin.gpgs.v2')
	@Override
	public int invoke(LuaState L) {
		NamedJavaFunction[] luaFunctions = new NamedJavaFunction[] {
			new EnableDebugWrapper(),
			new IsConnectedWrapper(),
			new IsAuthenticatedWrapper(),
			new LoginWrapper(),
			new LogoutWrapper(),
			new GetAccountNameWrapper(),
			new SetPopupPositionWrapper(),
			new GetServerAuthCodeNameWrapper(),
			new LoadGameWrapper(),
			new ClearNotificationsWrapper(),
			new LoadImageWrapper(),
			new ShowSettingsWrapper(),
			new ShowWrapper(),
			new RequestWrapper(),
			new InitWrapper(),
		};

		L.register(L.toString(1), luaFunctions);
		L.register("CoronaProvider.gameNetwork.google", luaFunctions); // For backward compatibility

		// API nodes
		connector = Connector.getInstance();
		achievements = new Achievements(L);
		leaderboards = new Leaderboards(L);
		players = new Players(L);
		new Events(L);
		new Snapshots(L);
		new Videos(L);
		multiplayer = new Multiplayer(L);
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity != null) {
			imageManager = ImageManager.create(activity);
		}


		Utils.getDirPointers(L);
		Utils.setTag("plugin.gpgs.v2");

		return 1;
	}

	// plugin.gpgs.v2.enableDebug()
	private int enableDebug(LuaState L) {
		Utils.enableDebug();
		return 0;
	}

	// plugin.gpgs.v3.isConnected()
	private int isConnected(LuaState L) {
		Utils.debugLog("isConnected()");
		if(CoronaLua.isListener(L,1, "isConnected")){
			Connector.isConnected(CoronaLua.newRef(L, 1), new CoronaRuntimeTaskDispatcher(L));
		}
		//Return our last check
		L.pushBoolean(Connector.lastConnectionCheck);
		return 1;
	}


	private int isAuthenticated(LuaState L) {
		Utils.debugLog("isAuthenticated()");
		L.pushBoolean(Connector.isAuthenticated());
		return 1;
	}

	// plugin.gpgs.v2.login(params)
	// params.userInitiated
	// params.listener
	private int login(LuaState L, final boolean isLegacy) {
		Utils.debugLog("login()");
		L.checkType(1, LuaType.TABLE);
		String name = "login";
		Scheme scheme = new Scheme()
			.bool("userInitiated")
			.listener("listener", name);

		Table params = new Table(L, 1).parse(scheme);
		final boolean userInitiated = params.getBoolean("userInitiated", false);
		Integer luaListener = params.getListener("listener");
		if (luaListener != null) {
			luaLoginListener = luaListener;
		}
		isLoginLegacy = isLegacy;
		//Disable Drive
		Connector.shouldUseDrive = params.getBoolean("useDrive", false);

		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity != null) {
			connector.setContext(activity, this);
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					connector.signIn(userInitiated);
				}
			});
		}

		return 0;
	}

	// plugin.gpgs.v2.logout()
	private int logout(LuaState L) {
		Utils.debugLog("logout()");
		connector.signOut();
		return 0;
	}

	// plugin.gpgs.v2.getAccountName(listener)
	private int getAccountName(LuaState L) {
		Utils.debugLog("getAccountName()");
		String name = "getAccountName";
		int listenerIndex = 1;
		int luaListener = CoronaLua.REFNIL;
		if (CoronaLua.isListener(L, listenerIndex, name)) {
			luaListener = CoronaLua.newRef(L, listenerIndex);
		}
		if (Utils.checkConnection()) {
			AccountsRequestPermissionsResultHandler resultHandler = new AccountsRequestPermissionsResultHandler();
			resultHandler.handleRun(name, luaListener);
		}
		return 0;
	}

	// plugin.gpgs.v2.getServerAuthCode(params)
	// params.serverId
	// params.listener
	private int getServerAuthCode(LuaState L) {
		Utils.debugLog("getServerAuthCode()");
		String name = "getServerAuthCode";
		if (!L.isTable(1)){
			Utils.errorLog(name + " must receive table parameter, got "+ L.typeName(1));
			return 0;
		}
		if (Utils.checkConnection()) {
			Scheme scheme = new Scheme()
					.string("serverId")
					.listener("listener", name);

			Table params = new Table(L, 1).parse(scheme);
			String serverId = params.getStringNotNull("serverId");
			Integer luaListener = params.getListener("listener");
			if (luaListener != null) {
				Hashtable<Object, Object> event = Utils.newEvent(name);
				String authCode = "";
				try {
					event.put("isError", false);
					authCode = Connector.getInstance().getServerAuthCode(serverId);
					event.put("code", authCode);
				} catch (Exception ex) {
					event.put("isError", true);
					event.put("errorCode", Utils.getErrorCode(ex));
					event.put("errorMessage", "Cannot retrieve Auth Code for serverId " + serverId);
				}
				Utils.put(event, "code", authCode);
				Utils.dispatchEvent(luaListener, event, true);
			}
		}
		return 0;
	}

	// plugin.gpgs.v3.setPopupPosition(position)
	private int setPopupPosition(LuaState L) {
		LuaUtils.errorLog("setPopupPosition() is no longer supported");
		return 0;
	}

	// plugin.gpgs.v3.loadGame(listener)
	private int loadGame(LuaState L) {
		LuaUtils.errorLog("loadGame() is no longer supported");
		return 0;
	}

	// plugin.gpgs.v2.clearNotifications(notificationTypes)
	private int clearNotifications(LuaState L) {
		LuaUtils.errorLog("clearNotifications() is no longer supported");
		return 0;
	}

	// plugin.gpgs.v2.loadImage(params)
	// params.uri *
	// params.filename *
	// params.baseDir
	// params.listener
	private int loadImage(LuaState L) {
		Utils.debugLog("loadImage()");
		String name = "loadImage";
		if (!L.isTable(1)){
			Utils.errorLog(name + " must receive table parameter, got "+ L.typeName(1));
			return 0;
		}
		Scheme scheme = new Scheme()
			.string("uri")
			.string("filename")
			.lightuserdata("baseDir")
			.listener("listener", name);

		Table params = new Table(L, 1).parse(scheme);
		final String uri = params.getStringNotNull("uri");
		String filename = params.getStringNotNull("filename");
		LuaUtils.LuaLightuserdata baseDir = params.getLightuserdata("baseDir", Utils.Dirs.cachesDirectoryPointer);
		Integer luaListener = params.getListener("listener", CoronaLua.REFNIL);
		final LoadImageCallback loadImageCallback = new LoadImageCallback(name, luaListener, filename, baseDir, Utils.pathForFile(L, filename, baseDir));

		final CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity != null) {
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					imageManager.loadImage(loadImageCallback, Uri.parse(uri));
				}
			});
		}
		return 0;
	}

	// plugin.gpgs.v3.showSettings(listener)
	private int showSettings(LuaState L) {
		Utils.debugLog("showSettings()");
		LuaUtils.errorLog("showSettings() is no longer supported");
		return 0;
	}

	// gameNetwork.show(name, ...)
	private int show(LuaState L) {
		Utils.debugLog("show()");
		if (!L.isString(1)){
			Utils.errorLog("Show must receive at least string parameter, got "+ L.typeName(1));
			return 0;
		}
		String name = L.checkString(1);
		L.remove(1);
		switch (name) {
			case "achievements":
				return achievements.show(L);
			case "leaderboards":
				return leaderboards.show(L);
			case "selectPlayers":
				LuaUtils.errorLog("Multiplayer is no longer supported");
				return 0;
			case "waitingRoom":
				LuaUtils.errorLog("Multiplayer is no longer supported");
				return 0;
			case "invitations":
				LuaUtils.errorLog("Multiplayer is no longer supported");
				return 0;
			default:
				return 0;
		}
	}

	// gameNetwork.request(action, ...)
	private int request(LuaState L) {
		Utils.debugLog("request()");
		String action = L.checkString(1);
		L.remove(1);
		switch (action) {
			case "isConnected":
				return isConnected(L);
			case "login":
				return login(L, true);
			case "logout":
				return logout(L);
			default:
				if (achievements.hasAction(action)) {
					return achievements.request(L, action);
				} else if (leaderboards.hasAction(action)) {
					return leaderboards.request(L, action);
				} else if (players.hasAction(action)) {
					return players.request(L, action);
				} else if (multiplayer.invitations.hasAction()) {
					return multiplayer.invitations.request(L);
				} else if (multiplayer.realtime.hasAction()) {
					return multiplayer.realtime.request(L);
				} else {
					return 0;
				}
		}
	}
	//endregion

	//region Callbacks
	// Connector.SignInListener
	public void onSignIn(SignInResult result) {
		this.onSignIn(result, null, null);
	}

	public void onSignIn(SignInResult result, Integer errorCode, String errorMessage) {
		Utils.debugLog("Entering onSingIn listener event");
		Hashtable<Object, Object> event = Utils.newEvent("login");
		if (!isLoginLegacy) {
			boolean isError = result != SignInResult.SUCCESS;
			event.put("isError", isError);
			Utils.put(event, "errorCode", errorCode);
			Utils.put(event, "errorMessage", errorMessage);
			event.put("phase", result == SignInResult.CANCELED ? "canceled" : "logged in");
		} else {
			event.put("type", "login");
			Hashtable<Object, Object> data = new Hashtable<>();
			data.put("isError", result != SignInResult.SUCCESS);
			event.put("data", data);
		}
		Utils.dispatchEvent(luaLoginListener, event);
	}

	public void onSignOut() {
		if (!isLoginLegacy) {
			Hashtable<Object, Object> event = Utils.newEvent("login");
			event.put("isError", false);
			event.put("phase", "logged out");
			Utils.dispatchEvent(luaLoginListener, event);
		}
	}

	private class AccountsRequestPermissionsResultHandler implements CoronaActivity.OnRequestPermissionsResultHandler {
		private String name;
		private Integer luaListener;

		void handleRun(String name, Integer luaListener) {
			this.name = name;
			this.luaListener = luaListener;
			PermissionsServices permissionsServices = new PermissionsServices(CoronaEnvironment.getApplicationContext());
			String permission = PermissionsServices.Permission.GET_ACCOUNTS;
			switch (permissionsServices.getPermissionStateFor(permission)) {
				case MISSING:
					// The Corona developer didn't add the permission to the AndroidManifest.xml
					// As it is required for our app to function, we'll error out here
					// If the permission were not critical, we could work around it here
					permissionsServices.showPermissionMissingFromManifestAlert(permission, "plugin.gpgs.v2.getAccountName() requires GET_ACCOUNTS permission.");
					break;
				case DENIED:
					if (!permissionsServices.shouldNeverAskAgain(permission)) {
						PermissionsSettings settings = new PermissionsSettings(permission);
						permissionsServices.requestPermissions(settings, this);
					}
					break;
				default:
					// Permission is granted!
					run();
			}
		}

		@Override
		public void onHandleRequestPermissionsResult(CoronaActivity activity, int requestCode, String[] permissions, int[] grantResults) {
			PermissionsSettings permissionsSettings = activity.unregisterRequestPermissionsResultHandler(this);
			if (permissionsSettings != null) {
				permissionsSettings.markAsServiced();
			}
			PermissionsServices permissionsServices = new PermissionsServices(activity);
			if (permissionsServices.getPermissionStateFor(PermissionsServices.Permission.GET_ACCOUNTS) == PermissionState.GRANTED) {
				run();
			} else {
				Hashtable<Object, Object> event = Utils.newEvent(name);
				event.put("isError", true);
				event.put("errorCode", 1);
				event.put("errorMessage", "Permission denied.");
				Utils.dispatchEvent(luaListener, event, true);
			}
			// Else, we were denied permission
		}

		void run() {
			/*
			Games.getGamesClient(Connector.getContext(), Connector.getSignInAccount()).
					getCurrentAccountName().addOnCompleteListener(new OnCompleteListener<String>() {
				@Override
				public void onComplete(@NonNull Task<String> task) {
					Hashtable<Object, Object> event = Utils.newEvent(name);
					event.put("isError", false);
					event.put("accountName", task.getResult());
					Utils.dispatchEvent(luaListener, event, true);
				}
			});

			 */
		}
	}

	private class LoadImageCallback implements ImageManager.OnImageLoadedListener {
		private String name;
		private Integer luaListener;
		private String filename;
		private LuaUtils.LuaLightuserdata baseDir;
		private String filepath;

		LoadImageCallback(String name, Integer luaListener, String filename, LuaUtils.LuaLightuserdata baseDir, String filepath) {
			this.name = name;
			this.luaListener = luaListener;
			this.filename = filename;
			this.baseDir = baseDir;
			this.filepath = filepath;
		}

		@Override
		public void onImageLoaded(Uri uri, Drawable drawable, boolean isRequestedDrawable) {
			int errorCode = 0;
			String errorMessage = "";
			boolean isError = false;

			Bitmap bitmap = null;
			if (drawable != null) {
				bitmap = ((BitmapDrawable) drawable).getBitmap();
				ByteArrayOutputStream bitmapStream = new ByteArrayOutputStream();
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, bitmapStream);

				try {
					OutputStream fileStream = new FileOutputStream(filepath);
					bitmapStream.writeTo(fileStream);
					bitmapStream.flush();
					fileStream.flush();
					bitmapStream.close();
					fileStream.close();
				} catch (FileNotFoundException e) {
					isError = true;
					errorCode = 2;
					errorMessage = "Can't create the file: " + e.toString();
				} catch (IOException e) {
					isError = true;
					errorCode = 3;
					errorMessage = "Can't write to the file: " + e.toString();
				}
			} else {
				isError = true;
				errorCode = 1;
				errorMessage = "Image not found: " + uri.toString();
			}

			Hashtable<Object, Object> event = Utils.newEvent(name);
			event.put("isError", isError);
			if (!isError) {
				event.put("filename", filename);
				event.put("baseDir", baseDir);
				event.put("width", bitmap.getWidth());
				event.put("height", bitmap.getHeight());
			} else {
				event.put("errorCode", errorCode);
				event.put("errorMessage", errorMessage);
			}
			Utils.dispatchEvent(luaListener, event, true);
		}
	}
	//endregion

	//region Lua wrappers
	private class EnableDebugWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "enableDebug";
		}

		@Override
		public int invoke(LuaState L) {
			return enableDebug(L);
		}
	}

	private class IsConnectedWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "isConnected";
		}

		@Override
		public int invoke(LuaState L) {
			return isConnected(L);
		}
	}

	private class IsAuthenticatedWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "isAuthenticated";
		}

		@Override
		public int invoke(LuaState L) {
			return isAuthenticated(L);
		}
	}

	private class LoginWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "login";
		}

		@Override
		public int invoke(LuaState L) {
			return login(L, false);
		}
	}

	private class LogoutWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "logout";
		}

		@Override
		public int invoke(LuaState L) {
			return logout(L);
		}
	}

	private class GetAccountNameWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "getAccountName";
		}

		@Override
		public int invoke(LuaState L) {
			return getAccountName(L);
		}
	}

	private class GetServerAuthCodeNameWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "getServerAuthCode";
		}

		@Override
		public int invoke(LuaState L) {
			return getServerAuthCode(L);
		}
	}

	private class SetPopupPositionWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "setPopupPosition";
		}

		@Override
		public int invoke(LuaState L) {
			return setPopupPosition(L);
		}
	}

	private class LoadGameWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "loadGame";
		}

		@Override
		public int invoke(LuaState L) {
			return loadGame(L);
		}
	}

	private class ClearNotificationsWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "clearNotifications";
		}

		@Override
		public int invoke(LuaState L) {
			return clearNotifications(L);
		}
	}

	private class LoadImageWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "loadImage";
		}

		@Override
		public int invoke(LuaState L) {
			return loadImage(L);
		}
	}

	private class ShowSettingsWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "showSettings";
		}

		@Override
		public int invoke(LuaState L) {
			return showSettings(L);
		}
	}

	private class ShowWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "show";
		}

		@Override
		public int invoke(LuaState L) {
			return show(L);
		}
	}

	private class RequestWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "request";
		}

		@Override
		public int invoke(LuaState L) {
			return request(L);
		}
	}

	protected class InitWrapper implements NamedJavaFunction
	{
		@Override
		public String getName() {
			return "init";
		}

		@Override
		public int invoke(final LuaState L) {
			PlayGamesSdk.initialize(Connector.getContext());
			//Get Sign In Result After Initializing use with lastConnectionCheck
			Connector.isConnected(-1, null);
			if(CoronaLua.isListener(L, 1, "init")) {
				int ref = CoronaLua.newRef(L, 1);
				LuaUtils.dispatchEvent(ref, LuaUtils.newEvent("init"), false);
			}
			return 0;
		}
	}

	//endregion
}
