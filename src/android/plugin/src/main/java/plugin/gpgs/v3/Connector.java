package plugin.gpgs.v3;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;

import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;

import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaActivity.OnActivityResultHandler;
import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaRuntime;
import com.ansca.corona.CoronaRuntimeTask;
import com.ansca.corona.CoronaRuntimeTaskDispatcher;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.AuthenticationResult;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesClient;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.Player;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.naef.jnlua.LuaState;

class Connector implements OnActivityResultHandler {
	interface SignInListener {
		enum SignInResult {
			SUCCESS,
			FAILED,
			CANCELED
		}
		void onSignIn(SignInResult result);
		void onSignIn(SignInResult result, Integer errorCode, String errorMessage);
		void onSignOut();
	}

	private SignInListener signInListener;
	private GoogleSignInOptions.Builder _signInBuilder;
	private GoogleSignInOptions _signInOptions;
	private GoogleSignInClient _signInClient;
	public static boolean shouldUseDrive = true; // should add Drive scope to login
	private GoogleSignInClient getSignInClient() {
		if(_signInOptions==null) {
			_signInBuilder = new GoogleSignInOptions.
					Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
			if(shouldUseDrive == true){
				_signInBuilder.requestScopes(Drive.SCOPE_APPFOLDER);
			}
			_signInOptions = _signInBuilder.build();
		}
		if(_signInClient==null) {
			_signInClient = GoogleSignIn.getClient(getContext(), _signInOptions);
		}
		return _signInClient;
	}

	private int expectedRequestCode; // Request code we use when invoking other Activities to complete the sign-in flow
	private boolean isConnecting = false;
	public static boolean lastConnectionCheck = false;
	private static final Connector instance = new Connector(); // Singleton
	private Connector() {}
	static Connector getInstance() {
		return instance;
	}

	static void isConnected(int listener, CoronaRuntimeTaskDispatcher dis) {
		final CoronaRuntimeTaskDispatcher myDis = dis;
		final int myLis = listener;
		PlayGames.getGamesSignInClient(getActivity()).isAuthenticated().addOnCompleteListener(new OnCompleteListener<AuthenticationResult>() {
			@Override
			public void onComplete(@NonNull final Task<AuthenticationResult> task) {
				lastConnectionCheck = task.isSuccessful() && task.getResult().isAuthenticated() ;
				//-1 means that we need to just get lastConnectionCheck
				if(myLis == -1){ return;}
				myDis.send(new CoronaRuntimeTask() {
					@Override
					public void executeUsing(CoronaRuntime runtime) {
						LuaState L = runtime.getLuaState();
						CoronaLua.newEvent(L, "gpgs");
						L.pushBoolean(lastConnectionCheck);
						L.setField(-2, "isConnected");
						L.pushString("isConnected");
						L.setField(-2, "name");
						try {
							CoronaLua.dispatchEvent(L, myLis, 0);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});

			}
		});

	}

	static boolean isAuthenticated() {
		return getContext() != null && getSignInAccount() != null;
	}

	Connector connector = this;
    static Context getContext() {
        return CoronaEnvironment.getApplicationContext();
    }
	static Activity getActivity() {
		return CoronaEnvironment.getCoronaActivity();
	}

    static GoogleSignInAccount getSignInAccount() {

        return GoogleSignIn.getLastSignedInAccount(getContext());
    }

    void setContext (final CoronaActivity activity, SignInListener listener) {
        signInListener = listener;
		expectedRequestCode = activity.registerActivityResultHandler(this);

//		// This view has to be added, otherwise GoogleApiClient won't find a view to show popups
//		final View popupView = new View(activity);
//		activity.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                if( activity.getRuntimeTaskDispatcher().isRuntimeAvailable() ) {
//                    RelativeLayout relativeLayout = new RelativeLayout(activity);
//                    relativeLayout.addView(popupView);
//                    activity.getOverlayView().addView(relativeLayout);
//                }
//            }
//        });
    }


	 String getServerAuthCode(String serverId) {
    	_signInOptions = new GoogleSignInOptions.
				Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN).
				requestServerAuthCode(serverId).
				requestScopes(Drive.SCOPE_APPFOLDER).

				requestEmail().
				build();
		_signInClient = null;

		return isAuthenticated() ? getSignInAccount().getServerAuthCode() : "";
	}

	void signIn(boolean userInitiated) {
		if (isAuthenticated()) {
			signInListener.onSignIn(SignInListener.SignInResult.SUCCESS);
		} else if (!isConnecting) { // We don't have a pending connection resolution, so start a new one
			connect(userInitiated);
		}
	}

	void signOut() {
		if (isAuthenticated()) {
			getSignInClient().signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
				@Override
				public void onComplete(@NonNull Task<Void> task) {
					signInListener.onSignOut();
				}
			});
		} else {
			signInListener.onSignOut();
		}
	}

	private void connect(boolean userInitiated) {
		isConnecting = true;
		if(userInitiated) {
			Activity activity = CoronaEnvironment.getCoronaActivity();
            if(activity!=null) {
                activity.startActivityForResult(getSignInClient().getSignInIntent(), expectedRequestCode);
            }
		} else {
			getSignInClient().silentSignIn().addOnCompleteListener(new OnCompleteListener<GoogleSignInAccount>() {
				@Override
				public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
					isConnecting = false;
					if (task.isSuccessful()) {
						lastConnectionCheck = true;
						signInListener.onSignIn(SignInListener.SignInResult.SUCCESS);
					} else {
						signInListener.onSignIn(SignInListener.SignInResult.FAILED);
					}
				}
			});
		}
	}

	private void disconnect() {
		isConnecting = false;
		if (isAuthenticated()) {
			getSignInClient().signOut();
		}
	}


	@Override
	public void onHandleActivityResult(CoronaActivity activity, int requestCode, int resultCode, Intent intent) {
		if (requestCode != expectedRequestCode) { // Request code was not meant for us
			return;
		}
		isConnecting = false;

		// We're coming back from an activity that was launched to resolve a connection problem. For example, a sign-in UI.
		switch (resultCode) {
			case Activity.RESULT_OK:
				signInListener.onSignIn(SignInListener.SignInResult.SUCCESS);
				break;
			case GamesActivityResultCodes.RESULT_RECONNECT_REQUIRED:
				signInListener.onSignIn(SignInListener.SignInResult.FAILED, null, "Reconnect Required");
				break;
			case Activity.RESULT_CANCELED:
				disconnect();
				signInListener.onSignIn(SignInListener.SignInResult.CANCELED);
				break;
			case GamesActivityResultCodes.RESULT_APP_MISCONFIGURED:
				disconnect();
				signInListener.onSignIn(SignInListener.SignInResult.FAILED, null, "Bad Configuration");
				Utils.errorLog("This application is misconfigured!");
				break;
			default:
				disconnect();
				signInListener.onSignIn(SignInListener.SignInResult.FAILED, resultCode, Utils.resultCodeToString(resultCode));
				break;
		}
	}
	//endregion
}
