# Google Play Games Services

> --------------------- ------------------------------------------------------------------------------------------
> __Type__              [Library][api.type.library]
> __Revision__          [REVISION_LABEL](REVISION_URL)
> __Keywords__          google, google play games services, achievements, leaderboards
> __Platforms__         Android
> __Sample__            [https://github.com/coronalabs/gpgs-sample](https://github.com/coronalabs/gpgs-sample)
> --------------------- ------------------------------------------------------------------------------------------


## Overview

This plugin enables access to Google Play Games Services API, such as achievements, leaderboards and snapshots(Saved Games).


## V2 vs V3
setPopupPosition
showSettings
loadGame

## Syntax

	local gpgs = require( "plugin.gpgs.v3" )

## Project Settings

To use this plugin, add an entry into the `plugins` table of `build.settings`. When added, the build server will integrate the plugin during the build phase.

	settings = {
		plugins = {
			["plugin.gpgs.v3"] = {
				publisherId = "com.solar2d",
			}
		}
	}

## Nodes

The plugin is divided into API nodes for better organization.

#### [gpgs.v3.achievements][plugin.gpgs2.achievements]

#### [gpgs.v3.leaderboards][plugin.gpgs2.leaderboards]

#### [gpgs.v3.players][plugin.gpgs2.players]

#### [gpgs.v3.events][plugin.gpgs2.events]

#### [gpgs.v3.snapshots][plugin.gpgs2.snapshots]



## gpgs.v3.*

## Overview

This is the base API node for the plugin. It manages connection to the Google's servers, authentication and general SDK tasks.

## Functions

#### [gpgs.v3.init()][plugin.gpgs3.init]

#### [gpgs.v3.enableDebug()][plugin.gpgs3.enableDebug]

#### [gpgs.v3.isConnected(callback)][plugin.gpgs3.isConnected]

#### [gpgs.v3.isAuthenticated()][plugin.gpgs3.isAuthenticated]

#### [gpgs.v3.login(params)][plugin.gpgs3.login]

#### [gpgs.v3.logout()][plugin.gpgs2.logout]

#### [gpgs.v3.getAccountName(listener)][plugin.gpgs2.getAccountName]

#### [gpgs.v3.getServerAuthCode(params)][plugin.gpgs2.getServerAuthCode]

#### [gpgs.v3.setPopupPosition(position)][plugin.gpgs2.setPopupPosition]

#### [gpgs.v3.loadGame(listener)][plugin.gpgs2.loadGame]

#### [gpgs.v3.clearNotifications(notificationTypes)][plugin.gpgs2.clearNotifications]

#### [gpgs.v3.loadImage(params)][plugin.gpgs2.loadImage]


## Events

#### [login][plugin.gpgs3.event.login]

#### [getAccountName][plugin.gpgs3.event.getAccountName]

#### [getServerAuthCode][plugin.gpgs3.event.getServerAuthCode]

#### [loadGame][plugin.gpgs3.event.loadGame]

#### [loadImage][plugin.gpgs3.event.loadImage]

## Types

#### [Game][plugin.gpgs3.type.Game]
